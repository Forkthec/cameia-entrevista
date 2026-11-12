package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code ejecucion_ia}, de planificar o de evaluar. La columna {@code turno_id} es nula al
 * planificar y obligatoria al evaluar, como exige la base.
 */
@Entity
@Table(name = "ejecucion_ia")
public class AiExecutionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tipo_operacion", nullable = false, length = 20)
    private String operation;

    @Column(name = "turno_id")
    private UUID turnId;

    @Column(name = "numero_intento", nullable = false)
    private short attempt;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "estado", nullable = false, length = 20)
    private String status;

    @Column(name = "modelo", nullable = false, length = 120)
    private String model;

    @Column(name = "version_prompt", length = 80)
    private String promptVersion;

    @Column(name = "error_codigo", length = 80)
    private String errorCode;

    @Column(name = "proveedor", length = 60)
    private String provider;

    @Column(name = "tokens_entrada")
    private Long inputTokens;

    @Column(name = "tokens_salida")
    private Long outputTokens;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant startedAt;

    @Column(name = "fecha_fin")
    private Instant finishedAt;

    /** Constructor que exige JPA. */
    protected AiExecutionEntity() {
    }

    /**
     * Crea la fila con todas sus columnas.
     *
     * @param id identificador
     * @param sessionId sesión
     * @param operation operación guardada
     * @param turnId turno que se evalúa; nulo al planificar
     * @param attempt intento de 1 a 3
     * @param idempotencyKey clave única
     * @param status estado guardado
     * @param model modelo
     * @param promptVersion versión del prompt
     * @param errorCode código del fallo
     * @param provider proveedor
     * @param inputTokens tokens de entrada
     * @param outputTokens tokens de salida
     * @param startedAt inicio
     * @param finishedAt cierre
     */
    public AiExecutionEntity(UUID id, UUID sessionId, String operation, UUID turnId, short attempt,
            UUID idempotencyKey,
            String status, String model, String promptVersion, String errorCode, String provider, Long inputTokens,
            Long outputTokens, Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.operation = operation;
        this.turnId = turnId;
        this.attempt = attempt;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.model = model;
        this.promptVersion = promptVersion;
        this.errorCode = errorCode;
        this.provider = provider;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /** @return identificador */
    public UUID getId() {
        return id;
    }

    /** @return sesión */
    public UUID getSessionId() {
        return sessionId;
    }

    /** @return operación guardada */
    public String getOperation() {
        return operation;
    }

    /** @return turno que se evalúa; nulo al planificar */
    public UUID getTurnId() {
        return turnId;
    }

    /** @return intento */
    public short getAttempt() {
        return attempt;
    }

    /** @return clave única */
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    /** @return estado guardado */
    public String getStatus() {
        return status;
    }

    /** @return modelo */
    public String getModel() {
        return model;
    }

    /** @return versión del prompt */
    public String getPromptVersion() {
        return promptVersion;
    }

    /** @return código del fallo */
    public String getErrorCode() {
        return errorCode;
    }

    /** @return proveedor */
    public String getProvider() {
        return provider;
    }

    /** @return tokens de entrada */
    public Long getInputTokens() {
        return inputTokens;
    }

    /** @return tokens de salida */
    public Long getOutputTokens() {
        return outputTokens;
    }

    /** @return inicio */
    public Instant getStartedAt() {
        return startedAt;
    }

    /** @return cierre */
    public Instant getFinishedAt() {
        return finishedAt;
    }
}
