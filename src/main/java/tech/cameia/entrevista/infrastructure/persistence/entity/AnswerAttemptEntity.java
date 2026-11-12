package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code intento_respuesta}: la respuesta de la persona a un turno. Solo se inserta; la base
 * rechaza cualquier cambio posterior (disparador {@code trg_intento_append_only}).
 */
@Entity
@Table(name = "intento_respuesta")
public class AnswerAttemptEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "turno_id", nullable = false)
    private UUID turnId;

    @Column(name = "tipo_interaccion", nullable = false, length = 20)
    private String interactionType;

    @Column(name = "es_definitiva", nullable = false)
    private boolean definitive;

    @Column(name = "origen_respuesta", nullable = false, length = 10)
    private String origin;

    @Column(name = "respuesta_texto", nullable = false, length = 4000)
    private String text;

    @Column(name = "pregunta_mostrada", nullable = false, length = 1000)
    private String questionShown;

    @Column(name = "idioma_detectado", length = 10)
    private String detectedLanguage;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant createdAt;

    /** Constructor que exige JPA. */
    protected AnswerAttemptEntity() {
    }

    /**
     * Crea la fila con todas sus columnas.
     *
     * @param id identificador
     * @param turnId turno
     * @param interactionType tipo de interacción ({@code INICIAL})
     * @param definitive si es la respuesta definitiva del turno
     * @param origin origen ({@code TEXTO} o {@code AUDIO})
     * @param text texto de la respuesta
     * @param questionShown pregunta tal como se mostró
     * @param detectedLanguage idioma detectado en el audio
     * @param createdAt fecha del registro
     */
    public AnswerAttemptEntity(UUID id, UUID turnId, String interactionType, boolean definitive, String origin,
            String text, String questionShown, String detectedLanguage, Instant createdAt) {
        this.id = id;
        this.turnId = turnId;
        this.interactionType = interactionType;
        this.definitive = definitive;
        this.origin = origin;
        this.text = text;
        this.questionShown = questionShown;
        this.detectedLanguage = detectedLanguage;
        this.createdAt = createdAt;
    }

    /** @return identificador */
    public UUID getId() {
        return id;
    }

    /** @return turno */
    public UUID getTurnId() {
        return turnId;
    }

    /** @return tipo de interacción */
    public String getInteractionType() {
        return interactionType;
    }

    /** @return si es la respuesta definitiva */
    public boolean isDefinitive() {
        return definitive;
    }

    /** @return origen */
    public String getOrigin() {
        return origin;
    }

    /** @return texto de la respuesta */
    public String getText() {
        return text;
    }

    /** @return pregunta mostrada */
    public String getQuestionShown() {
        return questionShown;
    }

    /** @return idioma detectado */
    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    /** @return fecha del registro */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
