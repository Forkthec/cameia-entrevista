package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code pregunta}. No es el modelo de dominio: la traducción la hace el mapeador. No lleva
 * {@code @Version}: la pregunta no se modifica (la base lo impide con un disparador).
 */
@Entity
@Table(name = "pregunta")
public class InterviewQuestionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sessionId;

    @Column(name = "orden", nullable = false)
    private short position;

    @Column(name = "tipo", nullable = false, length = 20)
    private String type;

    @Column(name = "texto_original", nullable = false, length = 500)
    private String originalText;

    @Column(name = "ejecucion_ia_id", nullable = false)
    private UUID executionId;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant createdAt;

    /** Constructor que exige JPA. */
    protected InterviewQuestionEntity() {
    }

    /**
     * Crea la fila con todas sus columnas.
     *
     * @param id identificador
     * @param sessionId sesión
     * @param position posición de 1 a 5
     * @param type tipo guardado
     * @param originalText texto
     * @param executionId ejecución de la IA
     * @param createdAt fecha de creación
     */
    public InterviewQuestionEntity(UUID id, UUID sessionId, short position, String type, String originalText,
            UUID executionId, Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.position = position;
        this.type = type;
        this.originalText = originalText;
        this.executionId = executionId;
        this.createdAt = createdAt;
    }

    /** @return identificador */
    public UUID getId() {
        return id;
    }

    /** @return sesión */
    public UUID getSessionId() {
        return sessionId;
    }

    /** @return posición */
    public short getPosition() {
        return position;
    }

    /** @return tipo guardado */
    public String getType() {
        return type;
    }

    /** @return texto original */
    public String getOriginalText() {
        return originalText;
    }

    /** @return ejecución de la IA */
    public UUID getExecutionId() {
        return executionId;
    }

    /** @return fecha de creación */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
