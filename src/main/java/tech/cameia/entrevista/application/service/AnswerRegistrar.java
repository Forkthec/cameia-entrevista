package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tech.cameia.entrevista.domain.exception.AnswerAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.AiRetryPolicy;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

/**
 * Reserva y registro de la respuesta por voz sobre el flujo común de respuestas.
 *
 * <p>La respuesta por voz reserva el turno ({@code PENDING → PROCESSING}) antes de transcribir, para que un segundo
 * envío responda 409 {@code TURN_CONFLICT} (CA-5.13.8). Si la transcripción falla, el turno vuelve a
 * {@code PENDING} sin respuesta guardada: la persona puede grabar de nuevo o responder por texto (CA-5.13.5,
 * CA-5.13.6). Con el texto transcrito, {@link #startEvaluation} guarda la respuesta y la marca de evaluación en la
 * misma transacción, y la evaluación, el avance y el cierre de la sesión los hace {@link TurnEvaluator}, igual que
 * para la respuesta escrita. Los códigos coinciden con los de la respuesta escrita.
 */
@Component
public class AnswerRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AnswerRegistrar.class);
    private static final String PROVIDER = AiProvider.PRIMARY.code();

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewTurnRepository turns;
    private final AnswerAttemptRepository answers;
    private final AiExecutionRepository executions;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String modelName;
    private final Duration evaluationBudget;

    @SuppressWarnings("java:S107")
    AnswerRegistrar(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns, AnswerAttemptRepository answers, AiExecutionRepository executions,
            TransactionTemplate transaction, Clock clock, @Value("${cameia.llm.model}") String modelName,
            @Value("${cameia.llm.evaluation-timeout}") Duration evaluationTimeout,
            @Value("${cameia.llm.retry.first-wait}") Duration firstWait,
            @Value("${cameia.llm.retry.second-wait}") Duration secondWait) {
        this.sessions = sessions;
        this.questions = questions;
        this.turns = turns;
        this.answers = answers;
        this.executions = executions;
        this.transaction = transaction;
        this.clock = clock;
        this.modelName = modelName;
        this.evaluationBudget = AiRetryPolicy.cycleBudget(evaluationTimeout, firstWait, secondWait);
    }

    /** Turno pendiente y su pregunta planificada. */
    public record Pending(InterviewTurn turn, InterviewQuestion question) {
    }

    /** Tiempo máximo del ciclo de evaluación que sigue a la transcripción. */
    public Duration evaluationBudget() {
        return evaluationBudget;
    }

    /**
     * Comprueba, sin escribir, que el turno de la sesión admite una respuesta por voz.
     *
     * @throws TurnNotFoundException si el turno no es de la sesión
     * @throws AnswerAlreadyExistsException si la pregunta ya tiene respuesta guardada o no está pendiente
     * @throws TurnConflictException si otra respuesta del turno se está procesando
     */
    public Pending requireAnswerable(InterviewSession session, UUID turnId, Duration claimMaxAge) {
        InterviewTurn turn = turns.findByIdAndSession(turnId, session.id()).orElseThrow(TurnNotFoundException::new);
        InterviewQuestion question = questions.findBySession(session.id()).stream()
                .filter(candidate -> candidate.id().equals(turn.questionId()))
                .findFirst()
                .orElseThrow(TurnNotFoundException::new);
        requireClaimable(turn, claimMaxAge);
        return new Pending(turn, question);
    }

    /**
     * Reserva el turno para transcribir: {@code PENDING → PROCESSING}. Una reserva de voz abandonada (más vieja que
     * {@code claimMaxAge} y sin respuesta guardada) se libera y se toma de nuevo. Va dentro de una transacción.
     */
    public InterviewTurn claim(UUID turnId, Duration claimMaxAge) {
        InterviewTurn turn = turns.findById(turnId).orElseThrow(TurnNotFoundException::new);
        requireClaimable(turn, claimMaxAge);
        if (turn.status() == TurnStatus.PROCESSING) {
            turn.release(clock);
        }
        turn.startProcessing(clock);
        try {
            return turns.save(turn);
        } catch (SessionConflictException lostRace) {
            throw new TurnConflictException();
        }
    }

    /**
     * Devuelve a pendiente un turno reservado cuyo audio no llegó a guardarse como respuesta. En su propia
     * transacción; si el turno ya cambió, no hace nada.
     */
    public void release(InterviewTurn claimed) {
        try {
            transaction.executeWithoutResult(status -> turns.findById(claimed.id())
                    .filter(turn -> turn.status() == TurnStatus.PROCESSING
                            && Objects.equals(turn.version(), claimed.version()))
                    .ifPresent(turn -> {
                        turn.release(clock);
                        turns.save(turn);
                    }));
        } catch (RuntimeException releasing) {
            log.error("No se pudo liberar el turno tras un fallo de la respuesta por voz [turnId={}, causa={}]",
                    claimed.id(), releasing.getClass().getSimpleName());
        }
    }

    /**
     * Guarda la respuesta transcrita y la marca de evaluación del turno reservado. Va dentro de una transacción; la
     * evaluación se hace después, fuera de ella, con {@link TurnEvaluator#evaluate}.
     *
     * @throws TurnConflictException si el turno ya no es la reserva de esta petición
     */
    @SuppressWarnings("java:S107")
    public TurnEvaluator.EvaluationJob startEvaluation(UUID sessionId, FirebaseUid owner, InterviewTurn claimed,
            InterviewQuestion question, String text, String detectedLanguage, TurnEvaluator.KeyRecorder recorder) {
        InterviewSession session = sessions.findByIdAndOwner(sessionId, owner)
                .orElseThrow(SessionNotFoundException::new);
        session.requireAcceptsAnswers();
        InterviewTurn turn = turns.findById(claimed.id()).orElseThrow(TurnNotFoundException::new);
        if (turn.status() != TurnStatus.PROCESSING || !Objects.equals(turn.version(), claimed.version())) {
            throw new TurnConflictException();
        }
        answers.save(AnswerAttempt.spoken(turn.id(), text, question.originalText(), detectedLanguage, clock));
        AiExecution mark = executions.save(AiExecution.evaluationStarted(session.id(), turn.id(), modelName,
                EvaluationPromptBuilder.VERSION, PROVIDER, clock));
        session.registerActivity(clock);
        sessions.save(session);
        return new TurnEvaluator.EvaluationJob(session, turn, question, text, mark, recorder);
    }

    private void requireClaimable(InterviewTurn turn, Duration claimMaxAge) {
        switch (turn.status()) {
            case PENDING -> {
                // Admite la respuesta.
            }
            case PROCESSING -> {
                // Una reserva viva, o una con respuesta guardada (se está evaluando), no se toca.
                if (isLive(turn, claimMaxAge) || answers.findDefinitive(turn.id()).isPresent()) {
                    throw new TurnConflictException();
                }
            }
            case COMPLETED, RETRYING -> throw new AnswerAlreadyExistsException();
        }
    }

    private boolean isLive(InterviewTurn turn, Duration claimMaxAge) {
        return turn.updatedAt().plus(claimMaxAge).isAfter(clock.instant());
    }
}
