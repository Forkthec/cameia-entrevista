package tech.cameia.entrevista.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Respuesta definitiva de la persona a un turno: fila de {@code intento_respuesta}. Solo se inserta; es la
 * evidencia de lo que la persona respondió.
 *
 * @param id identificador del intento
 * @param turnId turno al que responde
 * @param origin forma en que llegó: escrita o por voz
 * @param text texto de la respuesta (el transcrito, si llegó por voz), de 1 a {@value #MAX_TEXT_LENGTH} caracteres
 * @param questionShown texto de la pregunta tal como se mostró
 * @param detectedLanguage idioma detectado en el audio; solo en una respuesta por voz, puede ser {@code null}
 * @param createdAt fecha del registro
 */
public record AnswerAttempt(UUID id, UUID turnId, AnswerMode origin, String text, String questionShown,
        String detectedLanguage, Instant createdAt) {

    /** Largo máximo de una respuesta, en puntos de código Unicode tras normalizar a NFC; es el de la columna. */
    public static final int MAX_TEXT_LENGTH = 4000;

    /** Largo de la columna {@code idioma_detectado}. */
    public static final int MAX_LANGUAGE_LENGTH = 10;

    /** Exige los datos obligatorios; el idioma detectado solo acompaña a una respuesta por voz. */
    public AnswerAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(questionShown, "questionShown");
        Objects.requireNonNull(createdAt, "createdAt");
        // La base exige idioma nulo en una respuesta escrita y de 2 a 10 caracteres en una por voz: un valor del
        // proveedor fuera de ese rango se descarta en vez de romper el registro de la respuesta.
        if (origin != AnswerMode.AUDIO || detectedLanguage == null) {
            detectedLanguage = null;
        } else {
            String trimmed = detectedLanguage.strip();
            detectedLanguage = trimmed.length() < 2 || trimmed.length() > MAX_LANGUAGE_LENGTH ? null : trimmed;
        }
    }
}
