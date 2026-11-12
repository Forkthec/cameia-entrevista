package tech.cameia.entrevista.domain.model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.ErrorCode;

/**
 * Una llamada al modelo de lenguaje de la planificación: qué modelo y qué versión del prompt se usaron, cuántos
 * tokens costó y cómo terminó. Es la evidencia de procedencia de las preguntas.
 *
 * <p>Es inmutable: cerrar la ejecución devuelve otra. Los textos de procedencia se recortan al largo de su
 * columna para que un valor largo nunca termine en un error de base de datos.</p>
 *
 * @param id identificador de la ejecución
 * @param sessionId sesión a la que pertenece la ejecución
 * @param operation operación de la IA: planificar o evaluar
 * @param turnId turno que se evalúa; solo es nulo al planificar
 * @param attempt número de intento, de 1 a 3
 * @param idempotencyKey clave única de la ejecución
 * @param status estado actual
 * @param model modelo configurado, de hasta {@value #MAX_MODEL} caracteres
 * @param promptVersion versión del prompt, de hasta {@value #MAX_PROMPT_VERSION}; puede ser nula
 * @param errorCode código del fallo; solo con {@code FAILED}
 * @param provider proveedor del modelo, de hasta {@value #MAX_PROVIDER}; puede ser nulo
 * @param inputTokens tokens del prompt, o {@code null} si no se conocen
 * @param outputTokens tokens de la respuesta, o {@code null} si no se conocen
 * @param startedAt fecha de inicio
 * @param finishedAt fecha de cierre; {@code null} mientras está en curso
 */
public record AiExecution(UUID id, UUID sessionId, AiOperation operation, UUID turnId, int attempt, UUID idempotencyKey, AiExecutionStatus status,
        String model, String promptVersion, String errorCode, String provider, Long inputTokens, Long outputTokens,
        Instant startedAt, Instant finishedAt) {

    /** Largo de la columna {@code modelo}. */
    public static final int MAX_MODEL = 120;
    /** Largo de la columna {@code version_prompt}. */
    public static final int MAX_PROMPT_VERSION = 80;
    /** Largo de la columna {@code error_codigo}. */
    public static final int MAX_ERROR_CODE = 80;
    /** Largo de la columna {@code proveedor}. */
    public static final int MAX_PROVIDER = 60;

    /**
     * Exige los datos obligatorios y recorta los textos al largo de su columna.
     *
     * @throws IllegalArgumentException si el intento no está entre 1 y 3
     */
    public AiExecution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(startedAt, "startedAt");
        if (attempt < 1 || attempt > 3) {
            throw new IllegalArgumentException("El intento debe estar entre 1 y 3");
        }
        if (operation == AiOperation.EVALUATE && turnId == null) {
            throw new IllegalArgumentException("El turno es obligatorio al evaluar");
        }
        model = cut(model, MAX_MODEL);
        promptVersion = promptVersion == null ? null : cut(promptVersion, MAX_PROMPT_VERSION);
        errorCode = errorCode == null ? null : cut(errorCode, MAX_ERROR_CODE);
        provider = provider == null ? null : cut(provider, MAX_PROVIDER);
    }

    /**
     * Abre la marca de una planificación: una ejecución {@code IN_PROGRESS}.
     *
     * @param sessionId sesión que se planifica
     * @param attempt número de intento, de 1 a 3
     * @param model modelo configurado
     * @param promptVersion versión del prompt que se enviará; la base la exige desde el inicio
     * @param provider proveedor del modelo
     * @param clock reloj que da la fecha de inicio
     * @return la ejecución en curso, con identificador y clave nuevos
     */
    public static AiExecution planningStarted(UUID sessionId, int attempt, String model, String promptVersion,
            String provider, Clock clock) {
        return new AiExecution(UUID.randomUUID(), sessionId, AiOperation.PLAN, null, attempt, UUID.randomUUID(), AiExecutionStatus.IN_PROGRESS,
                model, promptVersion, null, provider, null, null, clock.instant(), null);
    }

    /**
     * Abre la marca de una evaluación: una ejecución {@code IN_PROGRESS} de un turno.
     *
     * @param sessionId sesión del turno
     * @param turnId turno que se evalúa
     * @param model modelo configurado
     * @param promptVersion versión del prompt que se enviará
     * @param provider proveedor del modelo
     * @param clock reloj que da la fecha de inicio
     * @return la ejecución en curso, con identificador y clave nuevos
     */
    public static AiExecution evaluationStarted(UUID sessionId, UUID turnId, String model, String promptVersion,
            String provider, Clock clock) {
        return new AiExecution(UUID.randomUUID(), sessionId, AiOperation.EVALUATE, turnId, 1, UUID.randomUUID(),
                AiExecutionStatus.IN_PROGRESS, model, promptVersion, null, provider, null, null, clock.instant(),
                null);
    }

    /**
     * Cierra la ejecución como exitosa.
     *
     * @param inputTokens tokens del prompt
     * @param outputTokens tokens de la respuesta
     * @param clock reloj que da la fecha de cierre
     * @return la ejecución {@code SUCCEEDED}
     */
    public AiExecution succeeded(long inputTokens, long outputTokens, Clock clock) {
        return new AiExecution(id, sessionId, operation, turnId, attempt, idempotencyKey, AiExecutionStatus.SUCCEEDED, model,
                promptVersion, null, provider, inputTokens, outputTokens, startedAt, clock.instant());
    }

    /**
     * Cierra la ejecución como fallida.
     *
     * @param cause código estable de la causa
     * @param clock reloj que da la fecha de cierre
     * @return la ejecución {@code FAILED}
     */
    public AiExecution failed(ErrorCode cause, Clock clock) {
        return new AiExecution(id, sessionId, operation, turnId, attempt, idempotencyKey, AiExecutionStatus.FAILED, model,
                promptVersion, cause.name(), provider, inputTokens, outputTokens, startedAt, clock.instant());
    }

    /**
     * Indica si la marca sigue vigente.
     *
     * @param now instante actual
     * @param maxAge tiempo tras el cual se considera caducada
     * @return {@code true} si está en curso y no pasó {@code maxAge} desde su inicio
     */
    public boolean isLive(Instant now, Duration maxAge) {
        return status == AiExecutionStatus.IN_PROGRESS && startedAt.plus(maxAge).isAfter(now);
    }

    private static String cut(String value, int max) {
        return value.codePointCount(0, value.length()) <= max ? value
                : value.substring(0, value.offsetByCodePoints(0, max));
    }
}
