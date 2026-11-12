package tech.cameia.entrevista.application.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionConsolidationFailedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.EvaluationResult;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.TurnFeedback;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.TurnFeedbackRepository;

/**
 * Evalúa la respuesta ya guardada de un turno que está procesándose y deja el turno completo o en reintento. Es la
 * pieza que comparten responder y reintentar.
 *
 * <p>La llamada al modelo nunca ocurre dentro de una transacción. El resultado se guarda en una sola transacción
 * corta: la ejecución exitosa, la retroalimentación, el turno completado, el turno de la pregunta siguiente y la
 * actividad de la sesión. Si la sesión dejó de estar en curso mientras el modelo respondía, no se escribe nada de la
 * evaluación y la ejecución se cierra como fallida con el código de esa causa. Al completar la quinta pregunta, la
 * misma transacción pasa la sesión a evaluación y, ya confirmada, se pide su consolidación; si esta falla, la respuesta
 * sigue siendo válida y la sesión queda en evaluación hasta que la persona pida el cierre de nuevo. Los registros
 * llevan códigos e identificadores, nunca texto de la persona ni del modelo.</p>
 */
@Service
public class TurnEvaluator {

    private static final Logger log = LoggerFactory.getLogger(TurnEvaluator.class);

    private final LanguageModelPort model;
    private final EvaluationPromptBuilder prompts;
    private final EvaluationOutputReader reader;
    private final InterviewTurnRepository turns;
    private final InterviewQuestionRepository questions;
    private final InterviewSessionRepository sessions;
    private final AiExecutionRepository executions;
    private final TurnFeedbackRepository feedbacks;
    private final ConsolidateSessionService consolidation;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    TurnEvaluator(LanguageModelPort model, EvaluationPromptBuilder prompts, EvaluationOutputReader reader,
            InterviewTurnRepository turns, InterviewQuestionRepository questions, InterviewSessionRepository sessions,
            AiExecutionRepository executions, TurnFeedbackRepository feedbacks,
            ConsolidateSessionService consolidation, TransactionTemplate transaction, Clock clock) {
        this.model = model;
        this.prompts = prompts;
        this.reader = reader;
        this.turns = turns;
        this.questions = questions;
        this.sessions = sessions;
        this.executions = executions;
        this.feedbacks = feedbacks;
        this.consolidation = consolidation;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * Trabajo de evaluación: lo que hace falta para evaluar un turno.
     *
     * @param session sesión del turno, tal como estaba al reservar
     * @param turn turno que se evalúa, ya en procesamiento
     * @param question pregunta del turno
     * @param answer respuesta de la persona
     * @param mark ejecución de la IA abierta para esta evaluación
     * @param recorder quien guarda el resultado en la clave de idempotencia, dentro de la misma transacción que
     *        cierra la evaluación
     */
    public record EvaluationJob(InterviewSession session, InterviewTurn turn, InterviewQuestion question,
            String answer, AiExecution mark, KeyRecorder recorder) {

        /** Trabajo sin clave de idempotencia, como el de reintentar. */
        public EvaluationJob(InterviewSession session, InterviewTurn turn, InterviewQuestion question, String answer,
                AiExecution mark) {
            this(session, turn, question, answer, mark, KeyRecorder.NONE);
        }
    }

    /**
     * Guarda el resultado de la evaluación en la clave de idempotencia de la petición. Se llama dentro de la
     * transacción que cierra la evaluación, para que una repetición nunca vea el turno resuelto con la clave todavía
     * en proceso.
     */
    public interface KeyRecorder {

        /** Registrador de las evaluaciones que no tienen clave. */
        KeyRecorder NONE = new KeyRecorder() {
            @Override
            public void succeeded(AnswerResult result) {
                // Sin clave: nada que guardar.
            }

            @Override
            public void failed(ErrorCode code) {
                // Sin clave: nada que guardar.
            }
        };

        /**
         * Guarda el resultado exitoso.
         *
         * @param result avance que se devuelve a la persona
         */
        void succeeded(AnswerResult result);

        /**
         * Guarda el fallo previsto con el que termina la evaluación.
         *
         * @param code código del fallo
         */
        void failed(ErrorCode code);
    }

    /**
     * Evalúa la respuesta guardada de un turno ya marcado como procesando, fuera de toda transacción, y guarda el
     * resultado.
     *
     * @param job turno y respuesta que se evalúan
     * @return el avance de la entrevista si la evaluación fue válida
     * @throws BusinessException con código {@code LLM_*} si la IA falló o devolvió una salida inválida; el turno ya
     *         quedó en reintento y la ejecución como fallida
     * @throws SessionAbandonedException si la sesión se abandonó mientras se evaluaba
     * @throws SessionAlreadyClosedException si la sesión ya se evalúa o terminó
     * @throws SessionTransitionNotAllowedException si la sesión ya no está en curso por otra causa
     * @throws DatabaseUnavailableException si la base falló al guardar; el turno queda procesando y lo recupera la
     *         caducidad
     */
    public AnswerResult evaluate(EvaluationJob job) {
        EvaluationResult result;
        LanguageModelResponse response;
        try {
            response = model.generate(prompts.build(job.question().originalText(), job.answer()));
            result = reader.read(response);
        } catch (BusinessException failure) {
            fail(job, failure.getErrorCode());
            throw failure;
        } catch (RuntimeException unexpected) {
            // Una falla que el adaptador no clasificó nunca debe terminar en el error genérico.
            fail(job, ErrorCode.LLM_UNAVAILABLE);
            throw new LlmUnavailableException(unexpected);
        }
        try {
            Saved saved = transaction.execute(status -> save(job, result, response));
            log.info("Turno evaluado [sessionId={}, turnId={}, executionId={}, resultado={}]",
                    job.session().id(), job.turn().id(), job.mark().id(), "EXITOSA");
            if (saved.evaluating()) {
                consolidateAfterCommit(job);
            }
            return saved.result();
        } catch (SessionAbandonedException | SessionAlreadyClosedException | SessionTransitionNotAllowedException
                | SessionConflictException closed) {
            fail(job, closed.getErrorCode());
            log.info("Evaluación descartada: la sesión ya no está en curso [sessionId={}, turnId={}, code={}]",
                    job.session().id(), job.turn().id(), closed.getErrorCode());
            throw closed;
        } catch (DataAccessException | TransactionException failure) {
            throw databaseUnavailable(job, failure);
        }
    }

    /** Resultado guardado y si la sesión quedó en evaluación al completarse la quinta pregunta. */
    private record Saved(AnswerResult result, boolean evaluating) {
    }

    // Ya confirmada la evaluación: un fallo del cierre no deshace la respuesta; la sesión sigue en evaluación.
    private void consolidateAfterCommit(EvaluationJob job) {
        try {
            consolidation.consolidate(job.session().id(), job.session().owner());
        } catch (SessionConsolidationFailedException failed) {
            // Ya registrado con su código e identificador; la persona pide el cierre de nuevo.
            log.info("Sesión en evaluación tras la quinta respuesta [sessionId={}]", job.session().id());
        }
    }

    // Una sola transacción: o queda todo (evaluación, turno completado, turno siguiente) o no queda nada.
    private Saved save(EvaluationJob job, EvaluationResult result, LanguageModelResponse response) {
        InterviewSession session = sessions.findByIdAndOwner(job.session().id(), job.session().owner())
                .orElseThrow(SessionNotFoundException::new);
        // Antes de escribir nada: una sesión que ya no está en curso no recibe evaluaciones.
        session.requireAcceptsAnswers();
        executions.save(job.mark().succeeded(response.inputTokens(), response.outputTokens(), clock));
        feedbacks.save(TurnFeedback.of(job.turn().id(), job.mark().id(), result, clock));
        InterviewTurn turn = turns.findById(job.turn().id()).orElseThrow(IllegalStateException::new);
        turn.complete(clock);
        turns.save(turn);
        int position = job.question().position();
        SessionState.PendingQuestion next = null;
        if (position < InterviewQuestion.TOTAL) {
            InterviewQuestion nextQuestion = questions.findBySession(session.id()).stream()
                    .filter(question -> question.position() == position + 1).findFirst()
                    .orElseThrow(IllegalStateException::new);
            InterviewTurn opened = turns.save(InterviewTurn.open(nextQuestion.id(), clock));
            next = new SessionState.PendingQuestion(opened.id(), nextQuestion.position(), nextQuestion.originalText(),
                    TurnStatus.PENDING, 0);
        }
        session.registerActivity(clock);
        boolean evaluating = position == InterviewQuestion.TOTAL && closeIfComplete(session);
        sessions.save(session);
        boolean finished = next == null;
        AnswerResult outcome = new AnswerResult(turn.id(), TurnStatus.COMPLETED,
                finished ? InterviewQuestion.TOTAL : position + 1, InterviewQuestion.TOTAL, next, finished);
        job.recorder().succeeded(outcome);
        return new Saved(outcome, evaluating);
    }

    // Después de guardar la quinta respuesta, dentro de la misma transacción: pasa a evaluación si hay cinco completos.
    private boolean closeIfComplete(InterviewSession session) {
        int completed = turns.countCompleted(session.id());
        boolean closed = session.closeIfComplete(completed, clock);
        if (!closed) {
            log.warn("Pregunta 5 completada sin cinco turnos completos [sessionId={}, turnosCompletos={}]",
                    session.id(), completed);
        }
        return closed;
    }

    /**
     * Cierra la evaluación como fallida y deja el turno en reintento. No mira el estado de la sesión, así que sirve
     * también cuando la sesión ya no está en curso. Si ni así se puede cerrar, la marca y el turno los recupera la
     * caducidad.
     */
    private void fail(EvaluationJob job, ErrorCode code) {
        try {
            transaction.executeWithoutResult(status -> {
                executions.failIfInProgress(job.mark().failed(code, clock));
                job.recorder().failed(code);
                InterviewTurn turn = turns.findById(job.turn().id()).orElseThrow(IllegalStateException::new);
                if (turn.status() == TurnStatus.PROCESSING) {
                    turn.retry(clock);
                    turns.save(turn);
                }
            });
        } catch (RuntimeException closing) {
            log.error("No se pudo cerrar la evaluación fallida [sessionId={}, turnId={}, executionId={}, causa={}]",
                    job.session().id(), job.turn().id(), job.mark().id(), closing.getClass().getSimpleName());
        }
        log.info("Turno evaluado [sessionId={}, turnId={}, executionId={}, resultado={}]", job.session().id(),
                job.turn().id(), job.mark().id(), code);
    }

    private DatabaseUnavailableException databaseUnavailable(EvaluationJob job, RuntimeException failure) {
        log.error("No se pudo guardar la evaluación [code={}, sessionId={}, turnId={}, causa={}]",
                ErrorCode.DATABASE_UNAVAILABLE, job.session().id(), job.turn().id(),
                failure.getClass().getSimpleName());
        return new DatabaseUnavailableException(failure);
    }
}
