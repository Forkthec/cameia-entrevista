package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.service.TurnEvaluator.EvaluationJob;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.exception.TurnNotRetryableException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.AiRetryPolicy;
import tech.cameia.entrevista.domain.policy.EvaluationStaleness;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

/**
 * Caso de uso: reintentar la evaluación de una respuesta que ya está guardada, cuando la evaluación falló o quedó
 * colgada. Cada uso suma uno al contador del turno (máximo cuatro) y vuelve a evaluar con el texto guardado: la
 * persona no tiene que escribir la respuesta de nuevo.
 *
 * <p>La reserva es una transacción corta; la llamada a la IA la hace {@link TurnEvaluator} sin transacción. Los
 * registros llevan códigos e identificadores, nunca texto de la persona.</p>
 */
@Service
public class ReprocessTurnService {

    private static final Logger log = LoggerFactory.getLogger(ReprocessTurnService.class);

    private static final String PROVIDER = AiProvider.PRIMARY.code();

    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final InterviewQuestionRepository questions;
    private final AnswerAttemptRepository answers;
    private final AiExecutionRepository executions;
    private final TurnEvaluator evaluator;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String modelName;
    private final EvaluationStaleness staleness;

    @SuppressWarnings("java:S107")
    ReprocessTurnService(InterviewSessionRepository sessions, InterviewTurnRepository turns,
            InterviewQuestionRepository questions, AnswerAttemptRepository answers, AiExecutionRepository executions,
            TurnEvaluator evaluator, TransactionTemplate transaction, Clock clock,
            @Value("${cameia.llm.model}") String modelName,
            @Value("${cameia.llm.evaluation-timeout}") Duration evaluationTimeout,
            @Value("${cameia.llm.retry.first-wait}") Duration firstWait,
            @Value("${cameia.llm.retry.second-wait}") Duration secondWait) {
        this.sessions = sessions;
        this.turns = turns;
        this.questions = questions;
        this.answers = answers;
        this.executions = executions;
        this.evaluator = evaluator;
        this.transaction = transaction;
        this.clock = clock;
        this.modelName = modelName;
        this.staleness = new EvaluationStaleness(AiRetryPolicy.cycleBudget(evaluationTimeout, firstWait, secondWait));
    }

    /**
     * Reintenta la evaluación de un turno.
     *
     * @param command sesión y dueño
     * @param rawTurnId identificador del turno, tal como llegó en la ruta
     * @return el avance de la entrevista si la evaluación fue válida
     * @throws tech.cameia.entrevista.domain.exception.SessionNotFoundException si la sesión no es de la persona
     * @throws TurnNotFoundException si el turno no existe, no es de la sesión o el identificador está mal escrito
     * @throws TurnNotRetryableException si el turno no espera un reintento
     * @throws tech.cameia.entrevista.domain.exception.TurnRetryLimitReachedException si ya se usaron los cuatro
     * @throws DatabaseUnavailableException si la base no estuvo disponible
     * @throws tech.cameia.entrevista.domain.exception.BusinessException con código {@code LLM_*} si la IA volvió a
     *         fallar; el reintento queda contado
     */
    public AnswerResult reprocess(SessionCommand command, String rawTurnId) {
        InterviewSession session = GetSessionStateService.findOwned(sessions, command);
        session.requireAcceptsAnswers();
        var turnId = SessionCommand.of(rawTurnId, session.owner()).sessionId();
        if (turnId == null) {
            throw new TurnNotFoundException();
        }
        InterviewTurn turn = turns.findByIdAndSession(turnId, session.id()).orElseThrow(TurnNotFoundException::new);
        Reservation reservation = reserve(session, turn);
        AnswerAttempt answer = answers.findDefinitive(turn.id()).orElseThrow(
                () -> new IllegalStateException("Turno en reintento sin respuesta guardada"));
        // La actividad de la sesión no se registra aquí: la registra TurnEvaluator al completar la evaluación.
        return evaluator.evaluate(new EvaluationJob(session, reservation.turn(), reservation.question(),
                answer.text(), reservation.mark()));
    }

    private Reservation reserve(InterviewSession session, InterviewTurn requested) {
        try {
            return transaction.execute(status -> startRetry(session, requested));
        } catch (SessionConflictException | TurnConflictException race) {
            // Otra petición tomó el turno entre la lectura y la escritura.
            throw new TurnNotRetryableException();
        } catch (DataAccessException | TransactionException failure) {
            log.error("No se pudo reservar el reintento [code={}, sessionId={}, turnId={}, causa={}]",
                    ErrorCode.DATABASE_UNAVAILABLE, session.id(), requested.id(), failure.getClass().getSimpleName());
            throw new DatabaseUnavailableException(failure);
        }
    }

    private Reservation startRetry(InterviewSession session, InterviewTurn requested) {
        InterviewTurn turn = turns.findById(requested.id()).orElseThrow(TurnNotFoundException::new);
        Optional<AiExecution> inProgress = executions.findEvaluationInProgress(turn.id());
        boolean stale = turn.status() == TurnStatus.PROCESSING && staleness.isStale(inProgress, clock.instant());
        turn.startManualRetry(stale, clock);
        if (stale && inProgress.isPresent()) {
            executions.failIfInProgress(inProgress.get().failed(ErrorCode.LLM_UNAVAILABLE, clock));
        }
        InterviewTurn saved = turns.save(turn);
        InterviewQuestion question = questions.findBySession(session.id()).stream()
                .filter(planned -> planned.id().equals(saved.questionId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Turno sin pregunta planificada"));
        AiExecution mark = executions.save(AiExecution.evaluationStarted(session.id(), saved.id(), modelName,
                EvaluationPromptBuilder.VERSION, PROVIDER, clock));
        return new Reservation(saved, question, mark);
    }

    private record Reservation(InterviewTurn turn, InterviewQuestion question, AiExecution mark) {
    }
}
