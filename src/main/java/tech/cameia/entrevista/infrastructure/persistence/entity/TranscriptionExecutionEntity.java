package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code ejecucion_ia} para la operación {@code TRANSCRIBIR}: una llamada al servicio de
 * transcripción para un turno. Es otra vista de la misma tabla que {@link AiExecutionEntity}: esa no mapea
 * {@code turno_id} porque en una planificación siempre es nula, y aquí es obligatoria. La base exige
 * {@code version_prompt} nula en una transcripción, por eso no se mapea.
 */
@Entity
@Table(name = "ejecucion_ia")
public class TranscriptionExecutionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sessionId;

    @Column(name = "turno_id", nullable = false)
    private UUID turnId;

    @Column(name = "tipo_operacion", nullable = false, length = 20)
    private String operation;

    @Column(name = "numero_intento", nullable = false)
    private short attempt;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "estado", nullable = false, length = 20)
    private String status;

    @Column(name = "modelo", nullable = false, length = 120)
    private String model;

    @Column(name = "error_codigo", length = 80)
    private String errorCode;

    @Column(name = "proveedor", length = 60)
    private String provider;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant startedAt;

    @Column(name = "fecha_fin")
    private Instant finishedAt;

    /** Constructor que exige JPA. */
    protected TranscriptionExecutionEntity() {
    }

    /**
     * Crea la fila con todas sus columnas.
     *
     * @param id identificador
     * @param sessionId sesión
     * @param turnId turno
     * @param operation operación ({@code TRANSCRIBIR})
     * @param attempt intento de 1 a 3
     * @param idempotencyKey clave única de la ejecución
     * @param status estado guardado
     * @param model modelo
     * @param errorCode código del fallo
     * @param provider proveedor
     * @param startedAt inicio
     * @param finishedAt fin
     */
    public TranscriptionExecutionEntity(UUID id, UUID sessionId, UUID turnId, String operation, short attempt,
            UUID idempotencyKey, String status, String model, String errorCode, String provider, Instant startedAt,
            Instant finishedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.turnId = turnId;
        this.operation = operation;
        this.attempt = attempt;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.model = model;
        this.errorCode = errorCode;
        this.provider = provider;
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

    /** @return turno */
    public UUID getTurnId() {
        return turnId;
    }

    /** @return operación */
    public String getOperation() {
        return operation;
    }

    /** @return intento */
    public short getAttempt() {
        return attempt;
    }

    /** @return estado */
    public String getStatus() {
        return status;
    }

    /** @return modelo */
    public String getModel() {
        return model;
    }

    /** @return código del fallo */
    public String getErrorCode() {
        return errorCode;
    }

    /** @return proveedor */
    public String getProvider() {
        return provider;
    }
}
