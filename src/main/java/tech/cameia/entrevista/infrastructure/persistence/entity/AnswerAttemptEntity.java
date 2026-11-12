package tech.cameia.entrevista.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.AnswerModeConverter;

/**
 * Fila de la tabla {@code intento_respuesta}: la respuesta de un turno. Solo se inserta; el disparador
 * {@code trg_intento_append_only} rechaza cualquier cambio posterior.
 *
 * <p>No es el modelo de dominio: existe para hablar con JPA. El esquema lo fija {@code hibernate.default_schema}.</p>
 */
@Entity
@Table(name = "intento_respuesta")
public class AnswerAttemptEntity {

    /** Único valor admitido hoy en {@code tipo_interaccion}. */
    public static final String INITIAL_INTERACTION = "INICIAL";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "turno_id", nullable = false)
    private UUID turnId;

    @Column(name = "tipo_interaccion", nullable = false, length = 20)
    private String interactionType;

    @Column(name = "es_definitiva", nullable = false)
    private boolean definitive;

    @Convert(converter = AnswerModeConverter.class)
    @Column(name = "origen_respuesta", nullable = false, length = 10)
    private AnswerMode origin;

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
     * Crea la fila con todos sus valores.
     *
     * @param id identificador
     * @param turnId turno al que responde
     * @param definitive si es la respuesta definitiva
     * @param origin audio o texto
     * @param text texto de la respuesta
     * @param shownQuestion texto de la pregunta mostrada
     * @param detectedLanguage idioma detectado, o {@code null}
     * @param createdAt fecha de creación
     */
    @SuppressWarnings("java:S107")
    public AnswerAttemptEntity(UUID id, UUID turnId, boolean definitive, AnswerMode origin, String text,
            String shownQuestion, String detectedLanguage, Instant createdAt) {
        this.id = id;
        this.turnId = turnId;
        this.interactionType = INITIAL_INTERACTION;
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

    /** @return tipo de interacción */
    public String getInteractionType() {
        return interactionType;
    }

    /** @return si es la respuesta definitiva */
    public boolean isDefinitive() {
        return definitive;
    }

    /** @return audio o texto */
    public AnswerMode getOrigin() {
        return origin;
    }

    /** @return texto de la respuesta */
    public String getText() {
        return text;
    }

    /** @return texto de la pregunta mostrada */
    public String getShownQuestion() {
        return shownQuestion;
    }

    /** @return idioma detectado, o {@code null} */
    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    /** @return fecha de creación */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
