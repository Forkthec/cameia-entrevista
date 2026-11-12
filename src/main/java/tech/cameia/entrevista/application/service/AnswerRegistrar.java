package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
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
 * <p>Debe correr dentro de una transacción abierta por quien la llama. Comprueba de nuevo el estado dentro de la
 * transacción porque, mientras se transcribía el audio, la sesión pudo cambiar.</p>
 *
 * <p>Alcance hoy: sin evaluación por turno (HU-5.2 aún no la tiene en Backend), el turno pasa a
 * {@code COMPLETED} al registrar la respuesta. Tras la quinta respuesta la sesión sigue {@code EN_CURSO}
 * y la respuesta informa {@code interviewFinished = true}: es el comportamiento de CA-5.12.6 mientras HU-5.5 no esté
 * disponible.</p>
 */
@Component
public class AnswerRegistrar {

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewTurnRepository turns;
    private final AnswerAttemptRepository attempts;
    private final Clock clock;

    AnswerRegistrar(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns, AnswerAttemptRepository attempts, Clock clock) {
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
     * Comprueba que el turno pedido es el pendiente de la sesión.
     *
     * @param session sesión en curso
     * @param turnId turno al que se quiere responder
     * @return el turno pendiente y su pregunta
     * @throws TurnTransitionNotAllowedException si el turno no existe, no es de la sesión, ya se respondió o no es
     *         el pendiente
     */
    public Pending requirePending(InterviewSession session, UUID turnId) {
        InterviewTurn open = turns.findOpenBySession(session.id())
                .filter(candidate -> candidate.id().equals(turnId))
                .orElseThrow(TurnTransitionNotAllowedException::new);
        if (open.status() != TurnStatus.PENDING) {
            throw new TurnTransitionNotAllowedException();
        }
        InterviewQuestion question = questions.findBySession(session.id()).stream()
                .filter(candidate -> candidate.id().equals(open.questionId()))
                .findFirst()
                .orElseThrow(TurnTransitionNotAllowedException::new);
        return new Pending(open, question);
    }

    /**
     * Registra la respuesta y avanza. Vuelve a cargar la sesión y el turno dentro de la transacción.
     *
     * @param sessionId sesión
     * @param owner dueño
     * @param turnId turno que se responde
     * @param text texto definitivo de la respuesta, ya validado (de 1 a 4000 caracteres)
     * @param origin origen de la respuesta
     * @param detectedLanguage idioma detectado en el audio; {@code null} en una respuesta escrita
     * @return el turno respondido, el avance y la siguiente pregunta
     * @throws SessionNotFoundException si la sesión desapareció
     * @throws SessionTransitionNotAllowedException si la sesión ya no está en curso
     * @throws TurnTransitionNotAllowedException si el turno ya no es el pendiente
     */
    public AnswerResult register(UUID sessionId, FirebaseUid owner, UUID turnId, String text, AnswerMode origin,
            String detectedLanguage) {
        InterviewSession session = sessions.findByIdAndOwner(sessionId, owner)
                .orElseThrow(SessionNotFoundException::new);
        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new SessionTransitionNotAllowedException();
        }
        Pending pending = requirePending(session, turnId);
        InterviewTurn turn = pending.turn();
        attempts.saveDefinitive(new AnswerAttempt(UUID.randomUUID(), turn.id(), origin, text,
                pending.question().originalText(), detectedLanguage, clock.instant()));
        turn.startProcessing(clock);
        turn.complete(clock);
        InterviewTurn saved = turns.save(turn);

        int answered = pending.question().position();
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
}
