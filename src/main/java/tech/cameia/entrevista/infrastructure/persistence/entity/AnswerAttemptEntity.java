package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code intento_respuesta}. Solo se inserta: la base rechaza cualquier cambio posterior. Por
 * ahora todo intento es la respuesta inicial y definitiva del turno.
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
    private String shownQuestion;

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
     * @param turnId turno al que responde
     * @param interactionType tipo de interacción guardado
     * @param definitive si es la respuesta definitiva del turno
     * @param origin origen guardado de la respuesta
     * @param text texto de la respuesta
     * @param shownQuestion pregunta tal como se mostró
     * @param detectedLanguage idioma detectado; nulo si la respuesta fue escrita
     * @param createdAt fecha de registro
     */
    public AnswerAttemptEntity(UUID id, UUID turnId, String interactionType, boolean definitive, String origin,
            String text, String shownQuestion, String detectedLanguage, Instant createdAt) {
        this.id = id;
        this.turnId = turnId;
        this.interactionType = interactionType;
        this.definitive = definitive;
        this.origin = origin;
        this.text = text;
        this.shownQuestion = shownQuestion;
        this.detectedLanguage = detectedLanguage;
        this.createdAt = createdAt;
    }

    /** @return identificador */
    public UUID getId() {
        return id;
    }

    /** @return turno al que responde */
    public UUID getTurnId() {
        return turnId;
    }

    /** @return origen guardado */
    public String getOrigin() {
        return origin;
    }

    /** @return texto de la respuesta */
    public String getText() {
        return text;
    }

    /** @return pregunta mostrada */
    public String getShownQuestion() {
        return shownQuestion;
    }

    /** @return idioma detectado */
    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    /** @return fecha de registro */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
