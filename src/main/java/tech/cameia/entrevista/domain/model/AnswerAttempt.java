package tech.cameia.entrevista.domain.model;

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
 * @param shownQuestion texto de la pregunta tal como se mostró
 * @param detectedLanguage idioma detectado en el audio, o {@code null} si no se detectó o la respuesta fue escrita
 * @param createdAt fecha de registro
 */
public record AnswerAttempt(UUID id, UUID turnId, AnswerOrigin origin, String text, String shownQuestion,
        String detectedLanguage, Instant createdAt) {

    /** Largo máximo de una respuesta, en caracteres; el mismo de la columna {@code respuesta_texto}. */
    public static final int MAX_TEXT_LENGTH = 4000;

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

    /** @return descripción sin el texto de la respuesta ni de la pregunta */
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
