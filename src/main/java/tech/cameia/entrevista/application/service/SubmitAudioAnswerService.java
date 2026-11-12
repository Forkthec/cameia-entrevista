package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.SubmitAudioAnswerCommand;
import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TranscriptionEmptyException;
import tech.cameia.entrevista.domain.exception.TranscriptionTooLongException;
import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.exception.TurnAlreadyAnsweredException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.exception.TurnProcessingException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TranscriptionFailure;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.TranscriptionRetryPolicy;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.SpeechToTextPort;

/**
 * Caso de uso: responder por voz la pregunta pendiente, con el reintento automático de la transcripción (HU-5.13).
 *
 * <p>La llamada a Voz nunca ocurre dentro de una transacción (cada intento puede tardar hasta 60 s). Las fases son:</p>
 * <ol>
 *   <li><b>Comprobaciones</b>, en el orden del contrato: sesión propia (404), sesión en curso (409), turno de la
 *       sesión (404), turno sin respuesta y libre (409), audio presente (422).</li>
 *   <li><b>Reserva</b> (transacción corta): el turno pasa a {@code PROCESSING} con control de versión. Un segundo
 *       envío del mismo turno mientras tanto —o dos simultáneos— recibe {@code 409 TURN_PROCESSING} sin guardarse ni
 *       transcribirse (CA-5.13.8).</li>
 *   <li><b>Transcripción</b>, sin transacción: hasta dos intentos según {@link TranscriptionRetryPolicy}. Entre el
 *       primero y el reintento el turno queda en {@code RETRYING}.</li>
 *   <li><b>Registro</b> (transacción corta): una sola respuesta definitiva, el turno completado y el siguiente
 *       abierto; con la quinta, la sesión pasa a evaluación (CA-5.13.1, CA-5.13.2).</li>
 * </ol>
 *
 * <p>Si la transcripción falla del todo, el turno vuelve a {@code PENDING} sin respuesta y sube
 * {@code 503 TRANSCRIPTION_UNAVAILABLE} (CA-5.13.3): la persona puede grabar de nuevo o responder por texto sobre
 * la misma pregunta (CA-5.13.5 a CA-5.13.7). El audio solo existe en memoria durante la petición (CA-5.13.4).</p>
 *
 * <p>Los registros llevan identificadores, número de intento y causa; nunca el audio ni el texto transcrito.</p>
 */
@Service
public class SubmitAudioAnswerService {

    private static final Logger log = LoggerFactory.getLogger(SubmitAudioAnswerService.class);

    /** Margen sobre la duración máxima de una transcripción antes de dar por muerta una reserva. */
    static final Duration CLAIM_MARGIN = Duration.ofSeconds(30);

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewTurnRepository turns;
    private final AnswerAttemptRepository answers;
    private final SpeechToTextPort speech;
    private final TranscriptionRetryPolicy retryPolicy;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration retryPause;
    private final Duration claimMaxAge;
    private final Pause pause;

    /** Espera entre intentos; separada para que las pruebas no duerman. */
    @FunctionalInterface
    interface Pause {
        /**
         * Espera el tiempo indicado.
         *
         * @param duration tiempo de espera
         * @throws InterruptedException si el hilo se interrumpe
         */
        void sleep(Duration duration) throws InterruptedException;
    }

    @Autowired
    @SuppressWarnings("java:S107")
    SubmitAudioAnswerService(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns, AnswerAttemptRepository answers, SpeechToTextPort speech,
            TranscriptionRetryPolicy retryPolicy, TransactionTemplate transaction, Clock clock,
            @Value("${cameia.voz.timeout}") Duration attemptTimeout,
            @Value("${cameia.voz.retry-pause}") Duration retryPause) {
        this(sessions, questions, turns, answers, speech, retryPolicy, transaction, clock, attemptTimeout, retryPause,
                duration -> Thread.sleep(duration));
    }

    @SuppressWarnings("java:S107")
    SubmitAudioAnswerService(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns, AnswerAttemptRepository answers, SpeechToTextPort speech,
            TranscriptionRetryPolicy retryPolicy, TransactionTemplate transaction, Clock clock,
            Duration attemptTimeout, Duration retryPause, Pause pause) {
        this.sessions = sessions;
        this.questions = questions;
        this.turns = turns;
        this.answers = answers;
        this.speech = speech;
        this.retryPolicy = retryPolicy;
        this.transaction = transaction;
        this.clock = clock;
        this.retryPause = retryPause;
        // Una reserva más vieja que todos los intentos posibles es de un proceso que murió sin liberarla.
        this.claimMaxAge = attemptTimeout.multipliedBy(TranscriptionRetryPolicy.MAX_ATTEMPTS).plus(retryPause)
                .plus(CLAIM_MARGIN);
        this.pause = pause;
    }

    /**
     * Transcribe el audio y registra la respuesta de la pregunta pendiente.
     *
     * @param command sesión, dueño, turno y audio
     * @return el turno respondido, el avance y la pregunta siguiente
     * @throws SessionNotFoundException si la sesión no existe, es ajena o el identificador no es un UUID
     * @throws SessionTransitionNotAllowedException si la sesión no está en curso
     * @throws TurnNotFoundException si el turno no existe, no es de la sesión o no es un UUID
     * @throws TurnAlreadyAnsweredException si el turno ya tiene su respuesta
     * @throws TurnProcessingException si ya se está procesando otra respuesta del mismo turno
     * @throws InvalidRequestException si falta el turno o el audio
     * @throws TranscriptionUnavailableException si se agotaron los intentos de transcripción
     * @throws TranscriptionEmptyException si la transcripción quedó vacía
     * @throws TranscriptionTooLongException si la transcripción supera los 4000 caracteres
     */
    public AnswerResult submit(SubmitAudioAnswerCommand command) {
        InterviewSession session = GetSessionStateService.findOwned(sessions, command.session());
        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new SessionTransitionNotAllowedException();
        }
        UUID turnId = parseTurnId(command.rawTurnId());
        List<InterviewQuestion> plan = questions.findBySession(session.id());
        InterviewTurn turn = turns.findById(turnId).orElseThrow(TurnNotFoundException::new);
        InterviewQuestion question = questionOf(plan, turn).orElseThrow(TurnNotFoundException::new);
        requireAnswerable(turn);
        AudioClip audio = command.audio();
        if (audio == null || audio.isEmpty()) {
            throw new InvalidRequestException(List.of(new FieldRejection("audio", ErrorCode.AUDIO_REQUIRED,
                    "Graba tu respuesta antes de enviarla.")));
        }
        Claim claim = new Claim(reserve(turnId));
        Transcription transcription;
        try {
            transcription = transcribe(audio, session, claim);
            requireUsable(transcription);
        } catch (BusinessException failure) {
            release(claim);
            throw failure;
        } catch (RuntimeException unexpected) {
            // Una falla que el adaptador no clasificó nunca debe dejar el turno ocupado ni terminar en el genérico.
            release(claim);
            throw new TranscriptionUnavailableException(TranscriptionFailure.REJECTED, unexpected);
        }
        try {
            AnswerResult result = transaction.execute(status -> register(session, plan, question, claim,
                    transcription));
            log.info("Respuesta por voz registrada [sessionId={}, turnId={}, pregunta={}]", session.id(), turnId,
                    question.position());
            return result;
        } catch (RuntimeException failure) {
            release(claim);
            throw translate(failure);
        }
    }

    // ---- Comprobaciones ----

    private static UUID parseTurnId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException(List.of(new FieldRejection("turnId", ErrorCode.TURN_ID_REQUIRED,
                    "Falta la pregunta que respondes.")));
        }
        // Como con la sesión: un identificador mal escrito es un turno que no existe.
        if (raw.length() != 36) {
            throw new TurnNotFoundException();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException notAUuid) {
            throw new TurnNotFoundException();
        }
    }

    private static Optional<InterviewQuestion> questionOf(List<InterviewQuestion> plan, InterviewTurn turn) {
        return plan.stream().filter(question -> question.id().equals(turn.questionId())).findFirst();
    }

    private void requireAnswerable(InterviewTurn turn) {
        if (turn.status() == TurnStatus.COMPLETED) {
            throw new TurnAlreadyAnsweredException();
        }
        if (turn.isBusy(clock.instant(), claimMaxAge)) {
            throw new TurnProcessingException();
        }
    }

    private static void requireUsable(Transcription transcription) {
        String text = transcription.text();
        if (text.isBlank()) {
            throw new TranscriptionEmptyException();
        }
        if (text.codePointCount(0, text.length()) > AnswerAttempt.MAX_TEXT_LENGTH) {
            throw new TranscriptionTooLongException();
        }
    }

    // ---- Reserva ----

    private InterviewTurn reserve(UUID turnId) {
        try {
            return transaction.execute(status -> {
                InterviewTurn turn = turns.findById(turnId).orElseThrow(TurnNotFoundException::new);
                requireAnswerable(turn);
                if (turn.status() != TurnStatus.PENDING) {
                    // Reserva caducada de un proceso que murió: se libera antes de tomarla.
                    log.warn("Reserva caducada del turno liberada [turnId={}, estado={}]", turnId, turn.status());
                    turn.release(clock);
                }
                turn.startProcessing(clock);
                return turns.save(turn);
            });
        } catch (SessionConflictException concurrent) {
            // Otra petición tomó el turno entre la lectura y la escritura.
            throw new TurnProcessingException();
        } catch (DataAccessException | TransactionException failure) {
            throw new DatabaseUnavailableException(failure);
        }
    }

    // ---- Transcripción ----

    private Transcription transcribe(AudioClip audio, InterviewSession session, Claim claim) {
        int attempt = 1;
        while (true) {
            try {
                Transcription transcription = speech.transcribe(audio, session.configuration().language());
                log.info("Transcripción obtenida [sessionId={}, turnId={}, intento={}]", session.id(),
                        claim.turn.id(), attempt);
                return transcription;
            } catch (TranscriptionUnavailableException failure) {
                log.warn("Transcripción fallida [sessionId={}, turnId={}, intento={}, causa={}]", session.id(),
                        claim.turn.id(), attempt, failure.failure());
                if (!retryPolicy.shouldRetry(attempt, failure.failure())) {
                    log.warn("Intentos de transcripción concluidos sin éxito [sessionId={}, turnId={}, intentos={}]",
                            session.id(), claim.turn.id(), attempt);
                    throw failure;
                }
            }
            markRetrying(claim);
            waitBeforeRetry();
            attempt++;
        }
    }

    private void markRetrying(Claim claim) {
        try {
            claim.turn = transaction.execute(status -> {
                InterviewTurn turn = claim.turn;
                turn.retry(clock);
                return turns.save(turn);
            });
        } catch (SessionConflictException lost) {
            // Alguien tomó el turno (solo pasa si la reserva caducó): esta petición ya no puede registrar nada.
            throw new TurnProcessingException();
        } catch (DataAccessException | TransactionException failure) {
            throw new DatabaseUnavailableException(failure);
        }
    }

    private void waitBeforeRetry() {
        try {
            pause.sleep(retryPause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TranscriptionUnavailableException(TranscriptionFailure.REJECTED, interrupted);
        }
    }

    // ---- Registro ----

    private AnswerResult register(InterviewSession requested, List<InterviewQuestion> plan, InterviewQuestion question,
            Claim claim, Transcription transcription) {
        InterviewSession session = sessions.findByIdAndOwner(requested.id(), requested.owner())
                .orElseThrow(SessionNotFoundException::new);
        if (session.status() != SessionStatus.IN_PROGRESS) {
            // La persona abandonó o cerró la entrevista mientras se transcribía.
            throw new SessionTransitionNotAllowedException();
        }
        InterviewTurn turn = turns.findById(claim.turn.id()).orElseThrow(TurnNotFoundException::new);
        if (!Objects.equals(turn.version(), claim.turn.version())) {
            throw new SessionConflictException();
        }
        if (turn.status() == TurnStatus.RETRYING) {
            turn.resumeProcessing(clock);
        }
        answers.insert(AnswerAttempt.fromAudio(turn.id(), transcription, question.originalText(), clock));
        turn.complete(clock);
        // claim.turn no se actualiza: si la transacción se revierte, la liberación debe ver la versión anterior.
        InterviewTurn completed = turns.save(turn);
        session.recordActivity(clock);
        Optional<InterviewQuestion> next = plan.stream()
                .filter(candidate -> candidate.position() == question.position() + 1).findFirst();
        if (next.isPresent()) {
            InterviewTurn opened = turns.save(InterviewTurn.open(next.get().id(), clock));
            sessions.save(session);
            return new AnswerResult(completed.id(), completed.status(), next.get().position(),
                    InterviewQuestion.TOTAL, new AnswerResult.NextQuestion(opened.id(), next.get().position(),
                            next.get().originalText()), false);
        }
        session.startEvaluation(clock);
        sessions.save(session);
        return new AnswerResult(completed.id(), completed.status(), InterviewQuestion.TOTAL, InterviewQuestion.TOTAL,
                null, true);
    }

    private static RuntimeException translate(RuntimeException failure) {
        if (failure instanceof BusinessException) {
            return failure;
        }
        if (failure instanceof DataIntegrityViolationException) {
            // El índice único de la respuesta definitiva o del turno siguiente: otra petición ya respondió.
            return new TurnAlreadyAnsweredException();
        }
        if (failure instanceof DataAccessException || failure instanceof TransactionException) {
            return new DatabaseUnavailableException(failure);
        }
        return failure;
    }

    // ---- Liberación ----

    // En su propia transacción y solo si el turno sigue siendo el que reservó esta petición. Si ni así se puede, la
    // reserva caduca sola (claimMaxAge) y el siguiente envío la libera.
    private void release(Claim claim) {
        try {
            transaction.executeWithoutResult(status -> turns.findById(claim.turn.id()).ifPresent(turn -> {
                boolean ours = Objects.equals(turn.version(), claim.turn.version());
                if (ours && (turn.status() == TurnStatus.PROCESSING || turn.status() == TurnStatus.RETRYING)) {
                    turn.release(clock);
                    turns.save(turn);
                }
            }));
        } catch (RuntimeException releasing) {
            log.error("No se pudo liberar el turno [turnId={}, causa={}]", claim.turn.id(),
                    releasing.getClass().getSimpleName());
        }
    }

    /** Turno reservado por esta petición, con la última versión que esta misma petición escribió. */
    private static final class Claim {
        private InterviewTurn turn;

        private Claim(InterviewTurn turn) {
            this.turn = turn;
        }
    }
}
