package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.AnswerCommand;
import tech.cameia.entrevista.application.service.TurnEvaluator.EvaluationJob;
import tech.cameia.entrevista.domain.exception.AnswerAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.model.ValidatedAnswerRequest;
import tech.cameia.entrevista.domain.policy.AnswerRequestPolicy;
import tech.cameia.entrevista.domain.policy.EvaluationStaleness;
import tech.cameia.entrevista.domain.policy.PlanningClaim;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.Waiter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Caso de uso: registrar la respuesta escrita a la pregunta pendiente, evaluarla sin mostrar la evaluación y avanzar.
 *
 * <p>Tiene dos transacciones cortas y la llamada a la IA en medio, sin transacción abierta. La primera guarda la
 * clave de idempotencia (en 202), la respuesta, el turno en procesamiento y la ejecución de la IA; solo cuando
 * confirma se llama al modelo. La segunda la hace {@link TurnEvaluator}. Una repetición con la misma clave repite la
 * respuesta guardada o, si la primera petición aún evalúa, espera su resultado hasta un tiempo límite. Los registros
 * llevan códigos e identificadores, nunca el texto de la respuesta.</p>
 */
@Service
public class RegisterAnswerService {

    private static final Logger log = LoggerFactory.getLogger(RegisterAnswerService.class);

    private static final String OPERATION = "RESPONDER";
    private static final String PROVIDER = "gemini";
    private static final int IN_PROGRESS_STATUS = 202;
    private static final int OK_STATUS = 200;
    private static final int LLM_FAILED_STATUS = 503;
    private static final int SESSION_CLOSED_STATUS = 409;
    private static final String PENDING_BODY = "{}";
    private static final Duration POLL = Duration.ofMillis(250);
    private static final String LLM_PREFIX = "LLM_";

    private final AnswerRequestPolicy policy = new AnswerRequestPolicy();
    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final InterviewQuestionRepository questions;
    private final AnswerAttemptRepository answers;
    private final AiExecutionRepository executions;
    private final IdempotencyKeyRepository keys;
    private final TurnEvaluator evaluator;
    private final JsonMapper json;
    private final Waiter waiter;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String modelName;
    private final Duration llmTimeout;
    private final EvaluationStaleness staleness;

    @SuppressWarnings("java:S107")
    RegisterAnswerService(InterviewSessionRepository sessions, InterviewTurnRepository turns,
            InterviewQuestionRepository questions, AnswerAttemptRepository answers, AiExecutionRepository executions,
            IdempotencyKeyRepository keys, TurnEvaluator evaluator, JsonMapper json, Waiter waiter,
            TransactionTemplate transaction, Clock clock, @Value("${cameia.llm.model}") String modelName,
            @Value("${cameia.llm.timeout}") Duration llmTimeout) {
        this.sessions = sessions;
        this.turns = turns;
        this.questions = questions;
        this.answers = answers;
        this.executions = executions;
        this.keys = keys;
        this.evaluator = evaluator;
        this.json = json;
        this.waiter = waiter;
        this.transaction = transaction;
        this.clock = clock;
        this.modelName = modelName;
        this.llmTimeout = llmTimeout;
        this.staleness = new EvaluationStaleness(llmTimeout);
    }

    /**
     * Registra la respuesta, la evalúa y devuelve el avance de la entrevista.
     *
     * <p>Si la evaluación termina pero la base falla al guardar la clave con su resultado, se registra el error y se
     * devuelve igual el resultado: la respuesta y su evaluación ya quedaron guardadas, y una repetición con la misma
     * clave la resuelve el estado del turno.</p>
     *
     * @param command sesión, turno, texto y clave de idempotencia
     * @return el avance: el turno completado y la pregunta siguiente
     * @throws tech.cameia.entrevista.domain.exception.InvalidRequestException si algún campo es inválido
     * @throws tech.cameia.entrevista.domain.exception.SessionNotFoundException si la sesión no es de la persona
     * @throws SessionAbandonedException si la sesión se abandonó
     * @throws SessionAlreadyClosedException si la sesión ya se evalúa o terminó
     * @throws SessionTransitionNotAllowedException si la sesión aún no empezó
     * @throws TurnNotFoundException si el turno no es de la sesión
     * @throws AnswerAlreadyExistsException si el turno ya tiene su respuesta
     * @throws IdempotencyKeyConflictException si la clave ya se usó con otra respuesta
     * @throws TurnConflictException si la misma clave sigue procesándose al vencer la espera
     * @throws BusinessException con código {@code LLM_*} si la IA falló; la respuesta queda guardada y se puede
     *         reintentar
     * @throws DatabaseUnavailableException si la base no estuvo disponible
     */
    public AnswerResult register(AnswerCommand command) {
        ValidatedAnswerRequest request = policy.validate(command.turnId(), command.answerText(),
                command.idempotencyKey());
        InterviewSession session = GetSessionStateService.findOwned(sessions, command.session());
        FirebaseUid owner = session.owner();
        var replay = keys.find(owner, OPERATION, request.idempotencyKey());
        if (replay.isPresent()) {
            return fromKey(replay.get(), request, owner);
        }
        session.requireAcceptsAnswers();
        InterviewTurn turn = turns.findByIdAndSession(request.turnId(), session.id())
                .orElseThrow(TurnNotFoundException::new);
        if (turn.status() != TurnStatus.PENDING) {
            throw new AnswerAlreadyExistsException();
        }
        InterviewQuestion question = questions.findBySession(session.id()).stream()
                .filter(planned -> planned.id().equals(turn.questionId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Turno sin pregunta planificada"));
        Reservation reservation;
        try {
            reservation = transaction.execute(status -> reserve(session, turn, question, request));
        } catch (IdempotencyKeyAlreadyExistsException race) {
            // Otra petición con la misma clave reservó primero: se repite su resultado.
            return fromKey(keys.find(owner, OPERATION, request.idempotencyKey()).orElseThrow(() -> race), request,
                    owner);
        } catch (DataAccessException | TransactionException failure) {
            log.error("No se pudo registrar la respuesta [code={}, sessionId={}, turnId={}, causa={}]",
                    ErrorCode.DATABASE_UNAVAILABLE, session.id(), turn.id(), failure.getClass().getSimpleName());
            throw new DatabaseUnavailableException(failure);
        }
        log.info("Respuesta registrada [sessionId={}, turnId={}, numero={}]", session.id(), turn.id(),
                question.position());
        return evaluate(reservation, request, owner);
    }

    // ---- Transacción 1: reserva ----

    private Reservation reserve(InterviewSession requested, InterviewTurn requestedTurn, InterviewQuestion question,
            ValidatedAnswerRequest request) {
        FirebaseUid owner = requested.owner();
        UUID keyId = UUID.randomUUID();
        Instant now = clock.instant();
        keys.save(new IdempotencyKey(keyId, owner, OPERATION, request.idempotencyKey(), request.fingerprint(),
                IN_PROGRESS_STATUS, PENDING_BODY, now));
        InterviewSession session = sessions.findByIdAndOwner(requested.id(), owner)
                .orElseThrow(IllegalStateException::new);
        session.requireAcceptsAnswers();
        InterviewTurn turn = turns.findById(requestedTurn.id()).orElseThrow(TurnNotFoundException::new);
        if (turn.status() != TurnStatus.PENDING) {
            // Otra petición ya tomó el turno entre la lectura y la reserva.
            throw new AnswerAlreadyExistsException();
        }
        answers.save(AnswerAttempt.written(turn.id(), request.answerText(), question.originalText(), clock));
        turn.startProcessing(clock);
        InterviewTurn processing;
        try {
            processing = turns.save(turn);
        } catch (SessionConflictException lostRace) {
            // Otra petición tomó el turno entre la lectura y la escritura.
            throw new AnswerAlreadyExistsException();
        }
        AiExecution mark = executions.save(AiExecution.evaluationStarted(session.id(), processing.id(), modelName,
                EvaluationPromptBuilder.VERSION, PROVIDER, clock));
        session.registerActivity(clock);
        sessions.save(session);
        return new Reservation(keyId, now,
                new EvaluationJob(session, processing, question, request.answerText(), mark));
    }

    // ---- Evaluación y cierre de la clave ----

    private AnswerResult evaluate(Reservation reservation, ValidatedAnswerRequest request, FirebaseUid owner) {
        AnswerResult result;
        try {
            result = evaluator.evaluate(reservation.job());
        } catch (SessionAbandonedException | SessionAlreadyClosedException
                | SessionTransitionNotAllowedException closed) {
            saveKey(reservation, owner, request, SESSION_CLOSED_STATUS, errorBody(closed.getErrorCode()));
            throw closed;
        } catch (BusinessException failure) {
            if (failure.getErrorCode().name().startsWith(LLM_PREFIX)) {
                saveKey(reservation, owner, request, LLM_FAILED_STATUS, errorBody(failure.getErrorCode()));
            }
            throw failure;
        }
        saveKey(reservation, owner, request, OK_STATUS, json.writeValueAsString(StoredAnswer.of(result)));
        return result;
    }

    // Si no se puede guardar, la clave queda en 202 y su repetición la resuelve el estado del turno.
    private void saveKey(Reservation reservation, FirebaseUid owner, ValidatedAnswerRequest request, int status,
            String body) {
        try {
            keys.save(new IdempotencyKey(reservation.keyId(), owner, OPERATION, request.idempotencyKey(),
                    request.fingerprint(), status, body, reservation.createdAt()));
        } catch (RuntimeException failure) {
            log.error("No se pudo guardar el resultado de la clave de idempotencia [code={}, turnId={}, causa={}]",
                    ErrorCode.DATABASE_UNAVAILABLE, request.turnId(), failure.getClass().getSimpleName());
        }
    }

    private String errorBody(ErrorCode code) {
        return json.writeValueAsString(new StoredError(code.name()));
    }

    // ---- Repetición con la misma clave ----

    private AnswerResult fromKey(IdempotencyKey saved, ValidatedAnswerRequest request, FirebaseUid owner) {
        // La huella se compara antes que el estado: otra respuesta con la misma clave nunca espera.
        if (!saved.requestFingerprint().equals(request.fingerprint())) {
            throw new IdempotencyKeyConflictException();
        }
        if (saved.responseStatus() != IN_PROGRESS_STATUS) {
            return storedResult(saved);
        }
        return awaitResult(saved, request, owner);
    }

    private AnswerResult storedResult(IdempotencyKey saved) {
        return switch (saved.responseStatus()) {
            case OK_STATUS -> json.readValue(saved.responseBodyJson(), StoredAnswer.class).toResult();
            case LLM_FAILED_STATUS -> throw llmFailure(codeOf(saved));
            default -> throw closedFailure(codeOf(saved));
        };
    }

    // Sin transacción abierta: la primera petición sigue evaluando y esta solo mira su resultado.
    private AnswerResult awaitResult(IdempotencyKey saved, ValidatedAnswerRequest request, FirebaseUid owner) {
        Instant deadline = saved.createdAt().plus(llmTimeout).plus(PlanningClaim.MARGIN);
        while (true) {
            IdempotencyKey current = keys.find(owner, OPERATION, request.idempotencyKey()).orElse(saved);
            if (current.responseStatus() != IN_PROGRESS_STATUS) {
                return storedResult(current);
            }
            InterviewTurn turn = turns.findById(request.turnId()).orElseThrow(TurnNotFoundException::new);
            switch (turn.status()) {
                case COMPLETED -> throw new AnswerAlreadyExistsException();
                case RETRYING -> throw new LlmUnavailableException(null);
                case PROCESSING -> {
                    if (staleness.isStale(executions.findEvaluationInProgress(turn.id()), clock.instant())) {
                        throw new LlmUnavailableException(null);
                    }
                }
                default -> { }
            }
            if (!clock.instant().isBefore(deadline)) {
                throw new TurnConflictException();
            }
            waiter.pause(POLL);
            if (Thread.currentThread().isInterrupted()) {
                // El servicio se apaga: no se sigue girando; la persona puede repetir la petición.
                throw new TurnConflictException();
            }
        }
    }

    private String codeOf(IdempotencyKey saved) {
        return json.readValue(saved.responseBodyJson(), StoredError.class).code();
    }

    private static BusinessException llmFailure(String code) {
        return switch (code) {
            case "LLM_LIMIT_REACHED" -> new LlmLimitReachedException(null);
            case "LLM_INVALID_OUTPUT" -> new LlmInvalidOutputException("evaluación inválida guardada");
            default -> new LlmUnavailableException(null);
        };
    }

    private static BusinessException closedFailure(String code) {
        return switch (code) {
            case "SESSION_ABANDONED" -> new SessionAbandonedException();
            case "SESSION_ALREADY_CLOSED" -> new SessionAlreadyClosedException();
            default -> new SessionTransitionNotAllowedException();
        };
    }

    private record Reservation(UUID keyId, Instant createdAt, EvaluationJob job) {
    }

    /** Cuerpo guardado de una respuesta exitosa: un registro plano, sin tipos polimórficos. */
    private record StoredAnswer(UUID turnId, TurnStatus turnStatus, int currentQuestion, int totalQuestions,
            UUID nextTurnId, Integer nextNumber, String nextText, boolean interviewFinished) {

        static StoredAnswer of(AnswerResult result) {
            SessionState.PendingQuestion next = result.nextQuestion();
            return new StoredAnswer(result.turnId(), result.turnStatus(), result.currentQuestion(),
                    result.totalQuestions(), next == null ? null : next.turnId(),
                    next == null ? null : next.number(), next == null ? null : next.text(),
                    result.interviewFinished());
        }

        AnswerResult toResult() {
            SessionState.PendingQuestion next = nextTurnId == null ? null
                    : new SessionState.PendingQuestion(nextTurnId, nextNumber, nextText, TurnStatus.PENDING, 0);
            return new AnswerResult(turnId, turnStatus, currentQuestion, totalQuestions, next, interviewFinished);
        }
    }

    /** Cuerpo guardado de una respuesta fallida: solo el código estable. */
    private record StoredError(String code) {
    }
}
