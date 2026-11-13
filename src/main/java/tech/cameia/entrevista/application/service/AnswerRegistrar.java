package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

/**
 * Registra la respuesta definitiva de la pregunta pendiente y avanza a la siguiente, sea cual sea su origen
 * (escrita o por voz): es la pieza común que pide CA-5.12.1, «procesarla igual que una respuesta escrita».
 *
 * <p>El ciclo de una respuesta es: {@link #requireAnswerable} (la pregunta se puede responder), {@link #claim}
 * (reserva el turno en {@code PROCESSING} con control de versión, en una transacción corta), el trabajo lento sin
 * transacción (transcribir), y {@link #register} (dentro de la transacción de quien llama) o {@link #release} si
 * algo falló. La reserva deja pasar un solo envío por turno: un doble envío recibe 409 {@code SESSION_CONFLICT}
 * (CA-5.8.6).</p>
 *
 * <p>Alcance hoy: sin evaluación por turno (HU-5.2 aún no la tiene en Backend), el turno pasa a {@code COMPLETED} al
 * registrar la respuesta. Tras la quinta respuesta la sesión sigue {@code EN_CURSO} y la respuesta informa
 * {@code interviewFinished = true}: es el comportamiento de CA-5.12.6 mientras HU-5.5 no esté disponible.</p>
 */
@Component
public class AnswerRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AnswerRegistrar.class);

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewTurnRepository turns;
    private final AnswerAttemptRepository attempts;
    private final TransactionTemplate transaction;
    private final Clock clock;

    AnswerRegistrar(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns, AnswerAttemptRepository attempts, TransactionTemplate transaction,
            Clock clock) {
        this.transaction = transaction;
        this.sessions = sessions;
        this.questions = questions;
        this.turns = turns;
        this.attempts = attempts;
        this.clock = clock;
    }

    /**
     * Pregunta pendiente de una sesión en curso: el turno abierto y su pregunta.
     *
     * @param turn turno abierto
     * @param question pregunta del turno
     */
    public record Pending(InterviewTurn turn, InterviewQuestion question) {
    }

    /**
     * Comprueba que la sesión admite respuestas (CA-5.8.28).
     *
     * @param status estado de la sesión
     * @throws SessionAbandonedException si la sesión se abandonó
     * @throws SessionAlreadyClosedException si la sesión está en evaluación o finalizada
     * @throws SessionTransitionNotAllowedException si la sesión todavía no empezó
     */
    public static void requireAcceptsAnswers(SessionStatus status) {
        switch (status) {
            case IN_PROGRESS -> {
                // Admite respuestas.
            }
            case ABANDONED -> throw new SessionAbandonedException();
            case EVALUATING, FINISHED -> throw new SessionAlreadyClosedException();
            case CONFIGURED -> throw new SessionTransitionNotAllowedException();
        }
    }

    /**
     * Comprueba que el turno pedido es el pendiente de la sesión y que nadie lo está procesando.
     *
     * @param session sesión en curso
     * @param turnId turno al que se quiere responder
     * @param claimMaxAge tiempo tras el que una reserva se considera de un proceso que murió
     * @return el turno pendiente y su pregunta
     * @throws TurnNotFoundException si el turno no existe o no es de la sesión (no se revela si existe)
     * @throws TurnTransitionNotAllowedException si es de la sesión pero ya se respondió o no es el pendiente
     * @throws SessionConflictException si otro envío del mismo turno se está procesando
     */
    public Pending requireAnswerable(InterviewSession session, UUID turnId, Duration claimMaxAge) {
        InterviewTurn turn = turns.findById(turnId).orElseThrow(TurnNotFoundException::new);
        InterviewQuestion question = questions.findBySession(session.id()).stream()
                .filter(candidate -> candidate.id().equals(turn.questionId()))
                .findFirst()
                .orElseThrow(TurnNotFoundException::new);
        boolean isOpen = turns.findOpenBySession(session.id()).map(open -> open.id().equals(turnId)).orElse(false);
        if (!isOpen || turn.status() == TurnStatus.COMPLETED) {
            throw new TurnTransitionNotAllowedException();
        }
        if (turn.status() == TurnStatus.PROCESSING && isLive(turn, claimMaxAge)) {
            throw new SessionConflictException();
        }
        return new Pending(turn, question);
    }

    /**
     * Reserva el turno ({@code PENDING} o {@code RETRYING} → {@code PROCESSING}). Debe correr en una transacción
     * corta propia: si otra petición reservó el mismo turno entre la lectura y la escritura, la versión ya no coincide
     * y el repositorio lanza {@link SessionConflictException}.
     *
     * @param turnId turno a reservar
     * @param claimMaxAge tiempo tras el que una reserva se considera de un proceso que murió
     * @return el turno reservado, con su versión
     * @throws SessionConflictException si otro envío ya lo reservó
     * @throws TurnTransitionNotAllowedException si ya se respondió
     */
    public InterviewTurn claim(UUID turnId, Duration claimMaxAge) {
        InterviewTurn turn = turns.findById(turnId).orElseThrow(TurnNotFoundException::new);
        switch (turn.status()) {
            case PENDING -> turn.startProcessing(clock);
            case RETRYING -> turn.resumeProcessing(clock);
            case PROCESSING -> {
                if (isLive(turn, claimMaxAge)) {
                    throw new SessionConflictException();
                }
                // Reserva de un proceso que murió a mitad del trabajo: se libera y se toma de nuevo.
                turn.retry(clock);
                turn.resumeProcessing(clock);
            }
            case COMPLETED -> throw new TurnTransitionNotAllowedException();
        }
        return turns.save(turn);
    }

    /**
     * Devuelve a {@code RETRYING} un turno reservado cuyo trabajo falló: la pregunta sigue pendiente y la persona
     * puede responder de nuevo. Corre en su propia transacción y solo toca el turno si sigue siendo la reserva de
     * quien llama; si ni así se puede, la reserva caduca sola.
     *
     * @param claimed turno tal como quedó al reservarlo
     */
    public void release(InterviewTurn claimed) {
        try {
            transaction.executeWithoutResult(status -> turns.findById(claimed.id())
                    .filter(turn -> turn.status() == TurnStatus.PROCESSING
                            && Objects.equals(turn.version(), claimed.version()))
                    .ifPresent(turn -> {
                        turn.retry(clock);
                        turns.save(turn);
                    }));
        } catch (RuntimeException releasing) {
            log.error("No se pudo liberar el turno tras un fallo de la respuesta [turnId={}, causa={}]", claimed.id(),
                    releasing.getClass().getSimpleName());
        }
    }

    /**
     * Registra la respuesta y avanza. Vuelve a cargar la sesión y el turno dentro de la transacción de quien llama:
     * mientras se transcribía, la sesión pudo cambiar o la reserva pudo caducar.
     *
     * @param sessionId sesión
     * @param owner dueño
     * @param claimed turno reservado por quien llama
     * @param question pregunta del turno
     * @param text texto definitivo de la respuesta, ya validado (de 1 a 4000 caracteres)
     * @param origin origen de la respuesta
     * @param detectedLanguage idioma detectado en el audio; {@code null} en una respuesta escrita
     * @return el turno respondido, el avance y la siguiente pregunta
     * @throws SessionNotFoundException si la sesión desapareció
     * @throws SessionConflictException si la reserva ya no es de quien llama
     */
    @SuppressWarnings("java:S107")
    public AnswerResult register(UUID sessionId, FirebaseUid owner, InterviewTurn claimed, InterviewQuestion question,
            String text, AnswerMode origin, String detectedLanguage) {
        InterviewSession session = sessions.findByIdAndOwner(sessionId, owner)
                .orElseThrow(SessionNotFoundException::new);
        requireAcceptsAnswers(session.status());
        InterviewTurn turn = turns.findById(claimed.id()).orElseThrow(TurnNotFoundException::new);
        if (turn.status() != TurnStatus.PROCESSING || !Objects.equals(turn.version(), claimed.version())) {
            throw new SessionConflictException();
        }
        attempts.saveDefinitive(new AnswerAttempt(UUID.randomUUID(), turn.id(), origin, text,
                question.originalText(), detectedLanguage, clock.instant()));
        turn.complete(clock);
        InterviewTurn saved = turns.save(turn);

        int answered = question.position();
        if (answered >= InterviewQuestion.TOTAL) {
            return new AnswerResult(saved.id(), saved.status(), InterviewQuestion.TOTAL, InterviewQuestion.TOTAL,
                    null, true);
        }
        List<InterviewQuestion> planned = questions.findBySession(session.id());
        InterviewQuestion next = planned.stream()
                .filter(candidate -> candidate.position() == answered + 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Falta la pregunta " + (answered + 1)));
        InterviewTurn nextTurn = turns.save(InterviewTurn.open(next.id(), clock));
        return new AnswerResult(saved.id(), saved.status(), next.position(), InterviewQuestion.TOTAL,
                new AnswerResult.NextQuestion(nextTurn.id(), next.position(), next.originalText()), false);
    }

    private boolean isLive(InterviewTurn turn, Duration claimMaxAge) {
        return turn.updatedAt().plus(claimMaxAge).isAfter(clock.instant());
    }
}
