package tech.cameia.entrevista.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Respuesta definitiva de la persona a un turno (fila de {@code intento_respuesta}). Solo se inserta.
 *
 * @param id identificador del intento
 * @param turnId turno al que responde
 * @param origin si la respuesta se escribió o se dictó
 * @param text texto de la respuesta (transcrito si llegó por voz), de 1 a {@link #MAX_TEXT_LENGTH} caracteres
 * @param shownQuestion texto de la pregunta tal como se mostró, de hasta {@link #MAX_QUESTION_LENGTH} caracteres
 * @param detectedLanguage idioma detectado en el audio, o {@code null} si no se detectó o la respuesta fue escrita
 * @param createdAt fecha de registro
 */
public record AnswerAttempt(UUID id, UUID turnId, AnswerOrigin origin, String text, String shownQuestion,
        String detectedLanguage, Instant createdAt) {

    /** Largo máximo de una respuesta, en caracteres; el mismo de la columna {@code respuesta_texto}. */
    public static final int MAX_TEXT_LENGTH = 4000;

    /** Largo máximo de la pregunta mostrada; el mismo de la columna {@code pregunta_mostrada}. */
    public static final int MAX_QUESTION_LENGTH = 1000;

    /** Largo máximo del idioma detectado que admite la base. */
    public static final int MAX_LANGUAGE_LENGTH = 10;

    /**
     * Valida los datos obligatorios.
     */
    public AnswerAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(shownQuestion, "shownQuestion");
        Objects.requireNonNull(createdAt, "createdAt");
        if (text.isBlank()) {
            throw new IllegalArgumentException("La respuesta no puede estar vacía");
        }
    }

    /**
     * Crea la respuesta escrita inicial y definitiva de un turno.
     *
     * @param turnId turno al que responde
     * @param text texto ya validado y normalizado
     * @param shownQuestion pregunta tal como se mostró
     * @param clock reloj que da la fecha de registro
     * @return la respuesta nueva
     */
    public static AnswerAttempt written(UUID turnId, String text, String shownQuestion, Clock clock) {
        return new AnswerAttempt(UUID.randomUUID(), turnId, AnswerOrigin.TEXTO, text, shownQuestion, null,
                clock.instant());
    }

    /** @return descripción sin el texto de la respuesta ni de la pregunta */
    /**
     * Respuesta por voz ya transcrita. El idioma detectado se guarda solo si tiene de 2 a 10 caracteres, como exige
     * la base; otro valor del proveedor se descarta en vez de romper el registro.
     *
     * @param turnId turno respondido
     * @param text texto transcrito
     * @param shownQuestion pregunta mostrada a la persona
     * @param detectedLanguage idioma que detectó el proveedor, o null
     * @param clock reloj que da la fecha
     * @return la respuesta lista para guardar
     */
    public static AnswerAttempt spoken(UUID turnId, String text, String shownQuestion, String detectedLanguage,
            Clock clock) {
        String language = detectedLanguage == null ? null : detectedLanguage.strip();
        if (language != null && (language.length() < 2 || language.length() > MAX_LANGUAGE_LENGTH)) {
            language = null;
        }
        return new AnswerAttempt(UUID.randomUUID(), turnId, AnswerOrigin.AUDIO, text, shownQuestion, language,
                clock.instant());
    }

    @Override
    public String toString() {
        return "AnswerAttempt[id=" + id + ", turnId=" + turnId + ", origin=" + origin + "]";
    }

    /** Si la respuesta se escribió o se dictó; el valor es el que guarda {@code origen_respuesta}. */
    public enum AnswerOrigin {
        /** Respuesta escrita. */
        TEXTO,
        /** Respuesta por voz, transcrita. */
        AUDIO
    }
}
