package tech.cameia.entrevista.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.ErrorCode;

/**
 * Registro de una llamada al servicio de transcripción para un turno: fila {@code TRANSCRIBIR} de
 * {@code ejecucion_ia} (CA-5.12.3). Se guarda cuando el servicio responde, con éxito o con fallo; nunca lleva el
 * audio ni el texto transcrito.
 *
 * @param id identificador de la ejecución
 * @param sessionId sesión del turno
 * @param turnId turno al que se respondió
 * @param attempt número de intento sobre esa grabación; hoy siempre 1 (los reintentos son de HU-5.13)
 * @param status {@code SUCCEEDED} o {@code FAILED}
 * @param model modelo de transcripción configurado
 * @param provider proveedor de transcripción
 * @param errorCode código de la causa; solo con {@code FAILED}
 * @param startedAt fecha en que se envió el audio
 * @param finishedAt fecha en que respondió el servicio
 */
public record TranscriptionExecution(UUID id, UUID sessionId, UUID turnId, int attempt, AiExecutionStatus status,
        String model, String provider, String errorCode, Instant startedAt, Instant finishedAt) {

    /** Intento que se registra hoy: el primero de cada grabación (CA-5.12.3). */
    public static final int FIRST_ATTEMPT = 1;

    /**
     * Exige los datos obligatorios y recorta los textos al largo de su columna.
     *
     * @throws IllegalArgumentException si está en curso, o si el código de error no corresponde al estado
     */
    public TranscriptionExecution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        if (status == AiExecutionStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Una transcripción se registra ya terminada");
        }
        if ((status == AiExecutionStatus.FAILED) != (errorCode != null)) {
            throw new IllegalArgumentException("Solo una transcripción fallida lleva código de error");
        }
        model = cut(model, AiExecution.MAX_MODEL);
        provider = provider == null ? null : cut(provider, AiExecution.MAX_PROVIDER);
    }

    /**
     * Primer intento exitoso.
     *
     * @param sessionId sesión
     * @param turnId turno
     * @param model modelo configurado
     * @param provider proveedor
     * @param startedAt envío del audio
     * @param finishedAt respuesta del servicio
     * @return la ejecución {@code SUCCEEDED}
     */
    public static TranscriptionExecution succeeded(UUID sessionId, UUID turnId, String model, String provider,
            Instant startedAt, Instant finishedAt) {
        return new TranscriptionExecution(UUID.randomUUID(), sessionId, turnId, FIRST_ATTEMPT,
                AiExecutionStatus.SUCCEEDED, model, provider, null, startedAt, finishedAt);
    }

    /**
     * Primer intento fallido.
     *
     * @param sessionId sesión
     * @param turnId turno
     * @param model modelo configurado
     * @param provider proveedor
     * @param cause código estable de la causa
     * @param startedAt envío del audio
     * @param finishedAt respuesta (o fallo) del servicio
     * @return la ejecución {@code FAILED} con su causa
     */
    public static TranscriptionExecution failed(UUID sessionId, UUID turnId, String model, String provider,
            ErrorCode cause, Instant startedAt, Instant finishedAt) {
        return new TranscriptionExecution(UUID.randomUUID(), sessionId, turnId, FIRST_ATTEMPT,
                AiExecutionStatus.FAILED, model, provider, cause.name(), startedAt, finishedAt);
    }

    private static String cut(String value, int max) {
        return value.codePointCount(0, value.length()) <= max ? value
                : value.substring(0, value.offsetByCodePoints(0, max));
    }
}
