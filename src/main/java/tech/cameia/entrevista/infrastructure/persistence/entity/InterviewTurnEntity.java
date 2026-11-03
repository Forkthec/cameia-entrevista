package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.TurnStatusConverter;

/**
 * Fila de la tabla {@code turno}.
 *
 * <p>No es el modelo de dominio: existe para hablar con JPA y sus columnas siguen la convención
 * de la base de datos, en español. La traducción la hace el mapeador de {@code persistence.mapper}.
 * El esquema no se declara aquí porque lo fija {@code hibernate.default_schema}.</p>
 */
@Entity
@Table(name = "turno")
public class InterviewTurnEntity {

    /** Identificador del turno. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Pregunta a la que pertenece. */
    @Column(name = "pregunta_id", nullable = false)
    private UUID questionId;

    /** Estado del turno. */
    @Convert(converter = TurnStatusConverter.class)
    @Column(name = "estado", nullable = false, length = 20)
    private TurnStatus status;

    /** Fecha de creación. */
    @Column(name = "fecha_creacion", nullable = false)
    private Instant createdAt;

    /** Fecha de la última modificación. */
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant updatedAt;

    /** Versión de control de edición simultánea. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Constructor que exige JPA y que usa el mapeador. */
    public InterviewTurnEntity() {
    }

    /**
     * @return identificador del turno
     */
    public UUID getId() {
        return id;
    }

    /**
     * @param id identificador del turno
     */
    public void setId(UUID id) {
        this.id = id;
    }
    /**
     * @return pregunta a la que pertenece
     */
    public UUID getQuestionId() {
        return questionId;
    }

    /**
     * @param questionId pregunta a la que pertenece
     */
    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
    }
    /**
     * @return estado del turno
     */
    public TurnStatus getStatus() {
        return status;
    }

    /**
     * @param status estado del turno
     */
    public void setStatus(TurnStatus status) {
        this.status = status;
    }
    /**
     * @return fecha de creación
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @param createdAt fecha de creación
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    /**
     * @return fecha de la última modificación
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * @param updatedAt fecha de la última modificación
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    /**
     * @return versión de control de edición simultánea
     */
    public Long getVersion() {
        return version;
    }

    /**
     * @param version versión de control de edición simultánea
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
