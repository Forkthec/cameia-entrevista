package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Texto que devolvió el servicio de Voz para un audio.
 *
 * <p>{@link #toString()} no imprime el texto: una transcripción nunca llega al registro.</p>
 *
 * @param text texto transcrito; puede estar vacío si no se detectó voz
 * @param detectedLanguage idioma detectado por Voz, o {@code null} si no lo informó
 */
public record Transcription(String text, String detectedLanguage) {

    /**
     * Exige el texto.
     */
    public Transcription {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public String toString() {
        return "Transcription[chars=" + text.length() + ", detectedLanguage=" + detectedLanguage + "]";
    }
}
