package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de la tabla {@code feedback_turno}. La columna {@code feedback_star} no se mapea: queda nula hasta que otra
 * tarea la use.
 */
@Entity
@Table(name = "feedback_turno")
public class TurnFeedbackEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "turno_id", nullable = false)
    private UUID turnId;

    @Column(name = "ejecucion_ia_id", nullable = false)
    private UUID executionId;

    /** Objeto JSON con la acción, la clasificación, el motivo y la retroalimentación. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback", nullable = false)
    private String feedback;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant createdAt;

    /** Constructor que exige JPA. */
    protected TurnFeedbackEntity() {
    }

    /**
     * Crea la fila con todas sus columnas.
     *
     * @param id identificador
     * @param turnId turno evaluado
     * @param executionId ejecución de la IA que la produjo
     * @param feedback objeto JSON de la evaluación
     * @param createdAt fecha de registro
     */
    public TurnFeedbackEntity(UUID id, UUID turnId, UUID executionId, String feedback, Instant createdAt) {
        this.id = id;
        this.turnId = turnId;
        this.executionId = executionId;
        this.feedback = feedback;
        this.createdAt = createdAt;
    }

    /** @return identificador */
    public UUID getId() {
        return id;
    }

    /** @return turno evaluado */
    public UUID getTurnId() {
        return turnId;
    }

    /** @return ejecución de la IA */
    public UUID getExecutionId() {
        return executionId;
    }

    /** @return objeto JSON de la evaluación */
    public String getFeedback() {
        return feedback;
    }

    /** @return fecha de registro */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
