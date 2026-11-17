package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.service.AiCallRunner.AiCall;
import tech.cameia.entrevista.application.service.AiCallRunner.AiSuccess;
import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.ProfileSnapshotInvalidException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.ContentScores;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.ReportQuestion;
import tech.cameia.entrevista.domain.model.StarResult;
import tech.cameia.entrevista.domain.model.VoiceMetrics;
import tech.cameia.entrevista.domain.model.VoiceSample;
import tech.cameia.entrevista.domain.policy.AiRetryPolicy;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.EvaluationReportRepository;

/**
 * Genera y guarda el Reporte de Evaluación de una sesión finalizada, dentro de la misma petición que lo consulta.
 *
 * <p>Las fases son: <b>reserva</b> (transacción corta que crea la marca de generación, una ejecución de la IA en
 * curso que el índice único de la base permite una sola vez por sesión), <b>llamada</b> a la IA sin transacción
 * (un ciclo de hasta tres intentos con {@link AiCallRunner}) y <b>cierre</b> (una transacción que guarda el reporte,
 * sus filas STAR y el estado final de la marca). Si la IA no entrega una calificación válida el reporte se emite
 * igual, con las métricas de contenido y el STAR «sin resultado» y las métricas de voz, que no dependen de la IA.</p>
 *
 * <p>Los registros llevan códigos e identificadores, nunca el texto de las preguntas ni de las respuestas.</p>
 */
@Service
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    private final AiExecutionRepository executions;

    private final AnswerAttemptRepository attempts;

    private final EvaluationReportRepository reports;

    private final AiCallRunner runner;

    private final ReportPromptBuilder prompts;

    private final ReportOutputReader reader;

    private final TransactionTemplate transaction;

    private final JsonMapper json;

    private final Clock clock;

    private final String modelName;

    private final String fallbackModelName;

    private final Duration claimTtl;

    /**
     * Crea el generador.
     *
     * @param executions puerto de ejecuciones de la IA
     * @param attempts puerto de respuestas
     * @param reports puerto del reporte
     * @param runner ejecutor del ciclo de la IA
     * @param prompts armador del prompt
     * @param reader lector de la salida del modelo
     * @param transaction plantilla de transacciones
     * @param json lector de JSON de la copia del perfil
     * @param clock reloj
     * @param modelName modelo principal
     * @param fallbackModelName modelo de respaldo
     * @param llmTimeout tope de un intento de la IA
     * @param firstWait espera entre el primer y el segundo intento
     * @param secondWait espera entre el segundo y el tercer intento
     * @param claimTtl tiempo tras el cual una marca de generación se considera caducada
     * @throws IllegalStateException si {@code claimTtl} no supera el peor ciclo de la IA: una marca viva de un ciclo
     *         que aún corre se tomaría por caducada
     */
    @SuppressWarnings("java:S107")
    ReportGenerator(AiExecutionRepository executions, AnswerAttemptRepository attempts,
            EvaluationReportRepository reports, AiCallRunner runner, ReportPromptBuilder prompts,
            ReportOutputReader reader, TransactionTemplate transaction, JsonMapper json, Clock clock,
            @Value("${cameia.llm.model}") String modelName,
            @Value("${cameia.llm.fallback.model}") String fallbackModelName,
            @Value("${cameia.llm.timeout}") Duration llmTimeout,
            @Value("${cameia.llm.retry.first-wait}") Duration firstWait,
            @Value("${cameia.llm.retry.second-wait}") Duration secondWait,
            @Value("${cameia.report.generation-claim-ttl}") Duration claimTtl) {
        Duration cycle = AiRetryPolicy.cycleBudget(llmTimeout, firstWait, secondWait);
        if (claimTtl.compareTo(cycle) <= 0) {
            throw new IllegalStateException("cameia.report.generation-claim-ttl (REPORT_GENERATION_CLAIM_TTL) debe "
                    + "ser mayor que el peor ciclo de la IA (" + cycle.toMillis() + " ms)");
        }
        this.executions = executions;
        this.attempts = attempts;
        this.reports = reports;
        this.runner = runner;
        this.prompts = prompts;
        this.reader = reader;
        this.transaction = transaction;
        this.json = json;
        this.clock = clock;
        this.modelName = modelName;
        this.fallbackModelName = fallbackModelName;
        this.claimTtl = claimTtl;
    }

    /**
     * Genera el reporte de una sesión finalizada que aún no lo tiene.
     *
     * @param session sesión finalizada de la persona
     * @return el reporte emitido (o el que otra petición emitió primero), o vacío si otra petición lo está
     *         generando en este momento
     * @throws DatabaseUnavailableException si la base falla al reservar, leer, cambiar de intento o guardar
     * @throws ProfileSnapshotInvalidException si la copia del perfil de la sesión no trae el rol
     */
    public Optional<EvaluationReport> generate(InterviewSession session) {
        List<ReportQuestion> inputs = readInputs(session.id());
        if (inputs.isEmpty()) {
            return Optional.of(emit(session, null, inputs, Graded.none()));
        }
        // Antes de reservar: una copia de perfil ilegible falla de inmediato y no deja una marca abierta.
        String role = roleName(session);
        Reservation reservation = reserve(session.id());
        if (reservation.existing() != null || reservation.mark() == null) {
            return Optional.ofNullable(reservation.existing());
        }
        Graded graded = grade(session, role, reservation.mark(), inputs);
        if (graded.busy()) {
            return Optional.empty();
        }
        return Optional.of(emit(session, graded.mark(), inputs, graded));
    }

    // ---- Fase 1: lectura y reserva ----

    private List<ReportQuestion> readInputs(UUID sessionId) {
        try {
            return attempts.reportInputsOf(sessionId);
        } catch (DataAccessException | TransactionException failure) {
            throw unavailable(sessionId, failure);
        }
    }

    private Reservation reserve(UUID sessionId) {
        try {
            return transaction.execute(status -> reserveInTransaction(sessionId));
        } catch (SessionConflictException otherIsGenerating) {
            // El índice único rechazó la marca: otra petición la abrió entre la lectura y el guardado.
            return Reservation.busy();
        } catch (DataAccessException | TransactionException | DatabaseUnavailableException failure) {
            throw unavailable(sessionId, failure);
        }
    }

    private Reservation reserveInTransaction(UUID sessionId) {
        Optional<EvaluationReport> existing = reports.findBySession(sessionId);
        if (existing.isPresent()) {
            return Reservation.existing(existing.get());
        }
        Optional<AiExecution> open = executions.findReportInProgress(sessionId);
        if (open.isPresent() && open.get().isLive(clock.instant(), claimTtl)) {
            return Reservation.busy();
        }
        open.ifPresent(stale -> closeStale(sessionId, stale));
        return Reservation.reserved(executions.save(AiExecution.reportStarted(sessionId, 1, modelName,
                ReportPromptBuilder.VERSION, AiProvider.PRIMARY.code(), clock)));
    }

    private void closeStale(UUID sessionId, AiExecution stale) {
        executions.save(stale.failed(ErrorCode.LLM_UNAVAILABLE, clock));
        log.warn("Marca de generación del reporte caducada cerrada como fallida [sessionId={}, executionId={}]",
                sessionId, stale.id());
    }

    // ---- Fase 2: llamada a la IA ----

    private Graded grade(InterviewSession session, String role, AiExecution firstMark, List<ReportQuestion> inputs) {
        Set<Integer> starNumbers = numbers(inputs, true);
        Set<Integer> contentNumbers = numbers(inputs, false);
        var prompt = prompts.build(session.configuration().language(), role,
                session.configuration().jobOffer(), inputs);
        try {
            AiSuccess<ReportGrades> success = runner.run(new AiCall<>(prompt,
                    response -> reader.read(response, contentNumbers, starNumbers), firstMark,
                    switchReport(session.id())));
            return Graded.ok(success.value(), success.mark(), success.response().inputTokens(),
                    success.response().outputTokens());
        } catch (AiCycleFailedException exhausted) {
            if (exhausted.cause() instanceof SessionConflictException) {
                // Otra petición tomó la marca (caducó y la retomó): ya no hay nada que cerrar aquí.
                return Graded.otherOwnsTheMark();
            }
            if (exhausted.cause() instanceof DatabaseUnavailableException databaseDown) {
                // La IA no falló 3 veces: falló la base al cambiar de intento. No se emite un reporte inmutable.
                closeAsFailed(exhausted.lastMark(), ErrorCode.DATABASE_UNAVAILABLE);
                throw databaseDown;
            }
            if (Thread.currentThread().isInterrupted()) {
                // El servicio se apaga durante la espera: el ciclo no se agotó, así que tampoco se emite.
                closeAsFailed(exhausted.lastMark(), exhausted.cause().getErrorCode());
                throw exhausted.cause();
            }
            return Graded.failed(exhausted.lastMark(), exhausted.cause().getErrorCode());
        }
    }

    private static Set<Integer> numbers(List<ReportQuestion> inputs, boolean behavioral) {
        return inputs.stream().filter(input -> (input.type() == QuestionType.CONDUCTUAL) == behavioral)
                .map(ReportQuestion::number).collect(Collectors.toSet());
    }

    // Cambia de intento en una sola transacción: cierra la marca fallida y abre la siguiente, sin hueco entre ambas.
    private AttemptSwitch switchReport(UUID sessionId) {
        return (failed, attempt, provider) -> {
            try {
                return transaction.execute(status -> {
                    if (!executions.failIfInProgress(failed)) {
                        throw new SessionConflictException();
                    }
                    return executions.save(AiExecution.reportStarted(sessionId, attempt, modelOf(provider),
                            ReportPromptBuilder.VERSION, provider.code(), clock));
                });
            } catch (DataAccessException | TransactionException failure) {
                throw unavailable(sessionId, failure);
            }
        };
    }

    private String modelOf(AiProvider provider) {
        return provider == AiProvider.FALLBACK ? fallbackModelName : modelName;
    }

    // ---- Fase 3: cierre ----

    private EvaluationReport emit(InterviewSession session, AiExecution mark, List<ReportQuestion> inputs,
            Graded graded) {
        EvaluationReport report = buildReport(session, inputs, graded);
        if (graded.failureCode() != null) {
            log.warn("Reporte emitido sin calificación de la IA [sessionId={}, executionId={}, code={}]",
                    session.id(), mark.id(), graded.failureCode());
        }
        EvaluationReport saved = save(report, mark, graded);
        log.info("Reporte de Evaluación emitido [sessionId={}, executionId={}, intento={}, contenido={}, voz={}]",
                session.id(), mark == null ? null : mark.id(), mark == null ? 0 : mark.attempt(),
                report.content().precision() == null ? "sin resultado" : "calculado",
                report.voice().speechRate() == null ? "sin resultado" : "calculado");
        return saved;
    }

    private EvaluationReport buildReport(InterviewSession session, List<ReportQuestion> inputs, Graded graded) {
        List<VoiceSample> samples = inputs.stream().map(ReportQuestion::voice).filter(Objects::nonNull).toList();
        ContentScores content = graded.grades() == null ? ContentScores.NONE
                : ContentScores.average(graded.grades().content());
        return new EvaluationReport(session.id(), session.configuration().language(), content,
                VoiceMetrics.of(samples), starOf(inputs, graded.grades()), clock.instant());
    }

    private static List<StarResult> starOf(List<ReportQuestion> inputs, ReportGrades grades) {
        return inputs.stream().filter(input -> input.type() == QuestionType.CONDUCTUAL).map(input -> {
            return grades == null ? StarResult.unavailable(input.number(), input.questionText())
                    : grades.star().stream().filter(marks -> marks.number() == input.number()).findFirst()
                            .map(marks -> new StarResult(input.number(), input.questionText(), marks.situation(),
                                    marks.task(), marks.action(), marks.result()))
                            .orElseGet(() -> StarResult.unavailable(input.number(), input.questionText()));
        }).toList();
    }

    private EvaluationReport save(EvaluationReport report, AiExecution mark, Graded graded) {
        try {
            transaction.executeWithoutResult(status -> {
                reports.saveIssued(report);
                if (mark != null) {
                    executions.save(closed(mark, graded));
                }
            });
            return report;
        } catch (EvaluationReportRepository.AlreadyIssuedException lostTheRace) {
            return storedAfterRace(report.sessionId(), mark, graded);
        } catch (DataAccessException | TransactionException | DatabaseUnavailableException failure) {
            closeAsFailed(mark, ErrorCode.DATABASE_UNAVAILABLE);
            throw unavailable(report.sessionId(), failure);
        }
    }

    // Otro proceso emitió el reporte tras retomar una marca vencida: se cierra la marca propia y se devuelve el suyo.
    private EvaluationReport storedAfterRace(UUID sessionId, AiExecution mark, Graded graded) {
        try {
            if (mark != null) {
                transaction.executeWithoutResult(status -> executions.save(closed(mark, graded)));
            }
            return reports.findBySession(sessionId).orElseThrow(() -> new DatabaseUnavailableException(
                    new IllegalStateException("El reporte existente no se pudo leer")));
        } catch (DataAccessException | TransactionException failure) {
            throw unavailable(sessionId, failure);
        }
    }

    private AiExecution closed(AiExecution mark, Graded graded) {
        return graded.failureCode() == null ? mark.succeeded(graded.inputTokens(), graded.outputTokens(), clock)
                : mark.failed(graded.failureCode(), clock);
    }

    // En su propia transacción: nunca depende de la que falló. Si ni así se cierra, la marca caduca sola.
    private void closeAsFailed(AiExecution mark, ErrorCode code) {
        if (mark == null) {
            return;
        }
        try {
            transaction.executeWithoutResult(status -> executions.failIfInProgress(mark.failed(code, clock)));
        } catch (RuntimeException closing) {
            log.error("No se pudo cerrar la marca de generación del reporte [executionId={}, causa={}]", mark.id(),
                    closing.getClass().getSimpleName());
        }
    }

    private DatabaseUnavailableException unavailable(UUID sessionId, Exception failure) {
        log.error("Falla de base al generar el reporte [code={}, sessionId={}, causa={}]",
                ErrorCode.DATABASE_UNAVAILABLE, sessionId, failure.getClass().getSimpleName());
        return failure instanceof DatabaseUnavailableException known ? known : new DatabaseUnavailableException(failure);
    }

    // ---- Datos de la sesión ----

    private String roleName(InterviewSession session) {
        try {
            JsonNode snapshot = json.readTree(session.configuration().profileSnapshotJson());
            return PlanningPromptBuilder.roleName(snapshot, session.configuration().targetRoleId());
        } catch (JacksonException unreadable) {
            // Sin la causa original: su mensaje puede citar un fragmento del texto del perfil.
            throw new ProfileSnapshotInvalidException(
                    new IllegalStateException("La copia del perfil no es un JSON legible"));
        }
    }

    // ---- Tipos internos ----

    private record Reservation(EvaluationReport existing, AiExecution mark) {

        static Reservation existing(EvaluationReport report) {
            return new Reservation(report, null);
        }

        static Reservation reserved(AiExecution mark) {
            return new Reservation(null, mark);
        }

        static Reservation busy() {
            return new Reservation(null, null);
        }
    }

    private record Graded(ReportGrades grades, AiExecution mark, ErrorCode failureCode, long inputTokens,
            long outputTokens, boolean busy) {

        static Graded ok(ReportGrades grades, AiExecution mark, long inputTokens, long outputTokens) {
            return new Graded(grades, mark, null, inputTokens, outputTokens, false);
        }

        static Graded failed(AiExecution mark, ErrorCode code) {
            return new Graded(null, mark, code, 0, 0, false);
        }

        static Graded otherOwnsTheMark() {
            return new Graded(null, null, null, 0, 0, true);
        }

        /** Sin respuestas que calificar: no hay llamada a la IA ni marca; todo queda «sin resultado». */
        static Graded none() {
            return new Graded(null, null, null, 0, 0, false);
        }
    }
}
