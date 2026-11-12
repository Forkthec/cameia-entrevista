package tech.cameia.entrevista.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Respuesta registrada de un turno (fila de {@code intento_respuesta}). Solo se inserta: es la evidencia de lo que
 * la persona respondió.
 *
 * @param id identificador del intento
 * @param turnId turno al que responde
 * @param origin forma en que llegó la respuesta: audio o texto
 * @param text texto de la respuesta (la transcripción, si llegó por audio), de 1 a {@value #MAX_TEXT_LENGTH}
 * @param shownQuestion texto de la pregunta tal como se mostró
 * @param detectedLanguage idioma detectado del audio; {@code null} si no se conoce o si es texto
 * @param definitive si es la respuesta definitiva del turno; un turno tiene a lo sumo una
 * @param createdAt fecha de registro
 */
public record AnswerAttempt(UUID id, UUID turnId, AnswerMode origin, String text, String shownQuestion,
        String detectedLanguage, boolean definitive, Instant createdAt) {

    /** Largo máximo de la respuesta, en caracteres; es el de la columna {@code respuesta_texto}. */
    public static final int MAX_TEXT_LENGTH = 4000;

    /** Largo mínimo del idioma detectado; es el de la restricción de {@code idioma_detectado}. */
    public static final int MIN_LANGUAGE_LENGTH = 2;

    /** Largo máximo del idioma detectado; es el de la columna {@code idioma_detectado}. */
    public static final int MAX_LANGUAGE_LENGTH = 10;

    /**
     * Exige los datos obligatorios.
     */
    public AnswerAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(shownQuestion, "shownQuestion");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Crea la respuesta definitiva de un turno a partir de la transcripción de su audio.
     *
     * @param turnId turno al que responde
     * @param transcription texto transcrito, ya validado
     * @param shownQuestion texto de la pregunta
     * @param clock reloj que da la fecha
     * @return la respuesta definitiva por audio
     */
    public static AnswerAttempt fromAudio(UUID turnId, Transcription transcription, String shownQuestion,
            Clock clock) {
        return new AnswerAttempt(UUID.randomUUID(), turnId, AnswerMode.AUDIO, transcription.text(), shownQuestion,
                usableLanguage(transcription.detectedLanguage()), true, clock.instant());
    }

    // La columna admite de 2 a 10 caracteres: un valor fuera de ese rango se descarta en vez de romper el guardado.
    private static String usableLanguage(String language) {
        if (language == null) {
            return null;
        }
        String trimmed = language.strip();
        return trimmed.length() >= MIN_LANGUAGE_LENGTH && trimmed.length() <= MAX_LANGUAGE_LENGTH ? trimmed : null;
    }

    @Override
    public String toString() {
        return "AnswerAttempt[id=" + id + ", turnId=" + turnId + ", origin=" + origin + "]";
    }
}
