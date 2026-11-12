package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Texto que el servicio de transcripción obtuvo de un audio.
 *
 * @param text texto transcrito, nunca nulo; en blanco si no se detectó voz
 * @param model modelo que transcribió
 * @param inputTokens tokens de entrada que informó el proveedor
 * @param outputTokens tokens de salida que informó el proveedor
 */
public record Transcription(String text, String model, long inputTokens, long outputTokens) {

    /**
     * Valida que el texto y el modelo no falten.
     */
    public Transcription {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(model, "model");
    }

    /** @return descripción sin el texto transcrito, que no debe llegar al log */
    @Override
    public String toString() {
        return "Transcription[chars=" + text.length() + ", model=" + model + "]";
    }
}
