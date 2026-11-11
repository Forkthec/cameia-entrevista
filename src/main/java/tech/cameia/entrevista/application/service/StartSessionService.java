package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.PlanningRetriesExhaustedException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.PlannedQuestion;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.policy.PlanValidator;
import tech.cameia.entrevista.domain.policy.PlanningClaim;
import tech.cameia.entrevista.domain.policy.PlanningRetryPolicy;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.LanguageModelPort;

/**
 * Caso de uso: iniciar la entrevista. Pide a la IA las cinco preguntas, las valida y, solo si todo está bien, las
 * guarda junto con el primer turno, abandona la entrevista que la persona tuviera en curso y pasa esta a en curso.
 *
 * <p>La llamada a la IA nunca ocurre dentro de una transacción (puede tardar hasta el tiempo máximo del modelo y el
 * grupo de conexiones es pequeño). Las tres fases son:</p>
 * <ol>
 *   <li><b>Reserva</b> (transacción corta): crea la marca de planificación, una ejecución de la IA en curso que el
 *       índice único de la base permite una sola vez por sesión, sube el contador de reintentos si el pedido sigue
 *       a un intento fallido y rechaza el pedido si los intentos se agotaron.</li>
 *   <li><b>Llamada y validación</b>, sin transacción.</li>
 *   <li><b>Cierre</b> (transacción corta): guarda la ejecución exitosa, las preguntas y el turno 1; primero abandona
 *       la entrevista anterior y la aplica, y después inicia la nueva. Si algo falla, la marca se cierra como
 *       fallida en una transacción propia y la entrevista anterior no cambia.</li>
 * </ol>
 *
 * <p>Cada {@code start} hace como máximo una llamada a la IA. Los registros llevan códigos e identificadores,
 * nunca texto del perfil, la oferta ni las preguntas.</p>
 */
@Service
public class StartSessionService {

    private static final Logger log = LoggerFactory.getLogger(StartSessionService.class);

    private static final String PROVIDER = "gemini";

    private final InterviewSessionRepository sessions;
    private final AiExecutionRepository executions;
    private final InterviewQuestionRepository questions;
    private final InterviewTurnRepository turns;
    private final LanguageModelPort model;
    private final PlanningPromptBuilder prompts;
    private final PlanOutputReader reader;
    private final PlanValidator validator;
    private final PlanningRetryPolicy retryPolicy;
    private final PlanningClaim claim;
    private final GetSessionStateService stateService;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String modelName;

    @SuppressWarnings("java:S107")
    StartSessionService(InterviewSessionRepository sessions, AiExecutionRepository executions,
            InterviewQuestionRepository questions, InterviewTurnRepository turns, LanguageModelPort model,
            PlanningPromptBuilder prompts, PlanOutputReader reader, PlanValidator validator,
            PlanningRetryPolicy retryPolicy, GetSessionStateService stateService, TransactionTemplate transaction,
            Clock clock, @Value("${cameia.llm.model}") String modelName,
            @Value("${cameia.llm.timeout}") Duration llmTimeout) {
        this.sessions = sessions;
        this.executions = executions;
        this.questions = questions;
        this.turns = turns;
        this.model = model;
        this.prompts = prompts;
        this.reader = reader;
        this.validator = validator;
        this.retryPolicy = retryPolicy;
        this.claim = new PlanningClaim(llmTimeout);
        this.stateService = stateService;
        this.transaction = transaction;
        this.clock = clock;
        this.modelName = modelName;
    }

    /**
     * Inicia la entrevista.
     *
     * @param command sesión pedida y dueño
     * @return el estado de la sesión: en curso con la pregunta 1, o el estado actual si ya estaba en curso
     * @throws SessionNotFoundException si la sesión no existe, es de otra persona o el identificador no era un UUID
     * @throws SessionTransitionNotAllowedException si la sesión está evaluándose, finalizada o abandonada
     * @throws SessionConflictException si otra petición está preparando las preguntas o la sesión cambió
     * @throws PlanningRetriesExhaustedException si ya se gastaron los tres intentos
     * @throws BusinessException con el código de la causa si la IA falló o devolvió un plan inválido
     */
    public SessionState start(SessionCommand command) {
        InterviewSession session = GetSessionStateService.findOwned(sessions, command);
        if (session.status() == SessionStatus.IN_PROGRESS) {
            return stateService.stateOf(session);
        }
        if (session.status() != SessionStatus.CONFIGURED) {
            throw new SessionTransitionNotAllowedException();
        }
        PlanningPrompt prompt = prompts.build(session.configuration());
        Reservation reservation;
        try {
            reservation = transaction.execute(status -> reserve(session.id(), command.owner(), prompt.version()));
        } catch (DataAccessException | TransactionException failure) {
            // La base falló antes de llamar a la IA: no se gastó ningún intento ni ninguna llamada.
            log.error("No se pudo reservar la planificación [code={}, sessionId={}, causa={}]",
                    ErrorCode.DATABASE_UNAVAILABLE, session.id(), failure.getClass().getSimpleName());
            throw new DatabaseUnavailableException(failure);
        }
        return plan(session, reservation, prompt);
    }

    private SessionState plan(InterviewSession session, Reservation reservation, PlanningPrompt prompt) {
        AiExecution mark = reservation.mark();
        List<PlannedQuestion> plan;
        LanguageModelResponse response;
        try {
            response = model.generate(prompt.prompt());
            plan = validator.validate(session.configuration().language(), reader.read(response.text()));
        } catch (BusinessException failure) {
            throw failPlanning(session, mark, reservation, failure.getErrorCode(), failure);
        } catch (RuntimeException unexpected) {
            // Una falla de la llamada que el adaptador no clasificó nunca debe terminar en el error genérico.
            throw failPlanning(session, mark, reservation, ErrorCode.LLM_UNAVAILABLE,
                    new LlmUnavailableException(unexpected));
        }
        try {
            transaction.executeWithoutResult(status -> finish(session, mark, response, plan));
        } catch (RuntimeException failure) {
            throw failSaving(session, mark, reservation, failure);
        }
        log.info("Entrevista iniciada [sessionId={}, executionId={}, intento={}]", session.id(), mark.id(),
                mark.attempt());
        return stateService.stateOf(GetSessionStateService.findOwned(sessions,
                new SessionCommand(session.id(), session.owner())));
    }

    // ---- Fase 1: reserva ----

    private Reservation reserve(UUID sessionId, FirebaseUid owner, String promptVersion) {
        InterviewSession session = sessions.findByIdAndOwner(sessionId, owner)
                .orElseThrow(SessionNotFoundException::new);
        if (session.status() != SessionStatus.CONFIGURED) {
            // Otra petición la inició o la canceló entre la lectura y la reserva.
            throw new SessionConflictException();
        }
        closeStaleMark(sessionId);
        boolean hadFailedAttempt = executions.hasFailedPlanning(sessionId);
        if (retryPolicy.decide(session.planningRetries(), hadFailedAttempt) == PlanningRetryPolicy.Decision.EXHAUSTED) {
            throw new PlanningRetriesExhaustedException();
        }
        int retries = retryPolicy.retriesAfterRequest(session.planningRetries(), hadFailedAttempt);
        if (retries != session.planningRetries()) {
            session.registerPlanningRetry(clock);
            sessions.save(session);
        }
        AiExecution mark = executions.save(AiExecution.planningStarted(sessionId, retryPolicy.attemptNumber(retries),
                modelName, promptVersion, PROVIDER, clock));
        return new Reservation(mark, retries);
    }

    // Una marca vigente significa que otra petición está planificando; una caducada es de un proceso que murió.
    private void closeStaleMark(UUID sessionId) {
        Optional<AiExecution> open = executions.findPlanningInProgress(sessionId);
        if (open.isEmpty()) {
            return;
        }
        if (claim.isLive(open.get(), clock.instant())) {
            throw new SessionConflictException();
        }
        executions.save(open.get().failed(ErrorCode.LLM_UNAVAILABLE, clock));
        log.warn("Marca de planificación caducada cerrada como fallida [sessionId={}, executionId={}]", sessionId,
                open.get().id());
    }

    // ---- Fase 3: cierre ----

    private void finish(InterviewSession requested, AiExecution mark, LanguageModelResponse response,
            List<PlannedQuestion> plan) {
        InterviewSession session = sessions.findByIdAndOwner(requested.id(), requested.owner())
                .orElseThrow(SessionNotFoundException::new);
        // La configuración con la que se armó el prompt debe seguir vigente: si cambió mientras la IA respondía,
        // las preguntas ya no corresponden a ella y no se guardan.
        if (session.status() != SessionStatus.CONFIGURED
                || !session.configuration().equals(requested.configuration())) {
            throw new SessionConflictException();
        }
        // Primero la anterior se abandona y se aplica; el índice único no admite dos sesiones en curso.
        sessions.findInProgressByOwner(session.owner()).ifPresent(previous -> {
            previous.abandon(clock);
            sessions.save(previous);
        });
        session.start(clock);
        sessions.save(session);
        executions.save(mark.succeeded(response.inputTokens(), response.outputTokens(), clock));
        List<InterviewQuestion> saved = new ArrayList<>();
        for (PlannedQuestion planned : plan) {
            saved.add(new InterviewQuestion(UUID.randomUUID(), session.id(), planned.position(),
                    QuestionType.fromCode(planned.type()), planned.text(), mark.id(), clock.instant()));
        }
        questions.saveAll(saved);
        turns.save(InterviewTurn.open(saved.get(0).id(), clock));
    }

    // ---- Fallos ----

    /**
     * Cierra la marca como fallida y devuelve la excepción que debe subir: la de la causa, o la de reintentos
     * agotados si este era el tercer intento. La sesión (y la entrevista anterior) no se tocan.
     */
    private BusinessException failPlanning(InterviewSession session, AiExecution mark, Reservation reservation,
            ErrorCode code, BusinessException cause) {
        closeAsFailed(mark, code);
        log.warn("Planificación fallida [code={}, sessionId={}, executionId={}, intento={}]", code, session.id(),
                mark.id(), mark.attempt());
        if (retryPolicy.isLastAttempt(reservation.retries())) {
            return new PlanningRetriesExhaustedException();
        }
        return cause;
    }

    /**
     * Cierra la marca cuando el guardado del plan falló y devuelve lo que debe subir. Un cambio de configuración o
     * una sesión que desapareció no son un intento fallido del modelo y suben tal cual; un fallo de la base sube como
     * {@code DATABASE_UNAVAILABLE} y, si era el tercer intento, como reintentos agotados.
     */
    private RuntimeException failSaving(InterviewSession session, AiExecution mark, Reservation reservation,
            RuntimeException failure) {
        if (failure instanceof SessionConflictException || failure instanceof SessionNotFoundException) {
            closeAsFailed(mark, ((BusinessException) failure).getErrorCode());
            log.warn("No se guardó el plan [code={}, sessionId={}, executionId={}]",
                    ((BusinessException) failure).getErrorCode(), session.id(), mark.id());
            return failure;
        }
        if (failure instanceof DataAccessException || failure instanceof TransactionException) {
            log.error("Falla de base al guardar el plan [code={}, sessionId={}, executionId={}, causa={}]",
                    ErrorCode.DATABASE_UNAVAILABLE, session.id(), mark.id(), failure.getClass().getSimpleName());
            return failPlanning(session, mark, reservation, ErrorCode.DATABASE_UNAVAILABLE,
                    new DatabaseUnavailableException(failure));
        }
        if (failure instanceof BusinessException business) {
            return failPlanning(session, mark, reservation, business.getErrorCode(), business);
        }
        closeAsFailed(mark, ErrorCode.INTERNAL_ERROR);
        log.warn("No se pudo guardar el plan [code={}, sessionId={}, executionId={}]", ErrorCode.INTERNAL_ERROR,
                session.id(), mark.id());
        return failure;
    }

    // En su propia transacción: nunca debe depender de la que falló. Si ni así se puede cerrar, la marca caduca sola.
    private void closeAsFailed(AiExecution mark, ErrorCode code) {
        try {
            // Solo cierra una fila que sigue en curso: si la persona reconfiguró o canceló mientras tanto, la marca ya
            // no existe y no debe reaparecer como un intento fallido de la configuración nueva.
            transaction.executeWithoutResult(status -> executions.failIfInProgress(mark.failed(code, clock)));
        } catch (RuntimeException closing) {
            log.error("No se pudo cerrar la marca de planificación [executionId={}, causa={}]", mark.id(),
                    closing.getClass().getSimpleName());
        }
    }

    private record Reservation(AiExecution mark, int retries) {
    }
}
