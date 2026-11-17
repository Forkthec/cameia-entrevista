package tech.cameia.entrevista.domain.model;

import java.time.Duration;
import java.util.List;

/**
 * Lo que devolvió el servicio de transcripción para un audio: el texto tal como salió, el idioma que detectó y
 * cuánta voz reconoció.
 *
 * <p>El texto puede venir vacío: decidir si sirve como respuesta es de
 * {@link tech.cameia.entrevista.domain.policy.TranscriptionPolicy}, no del proveedor.</p>
 *
 * @param text texto transcrito, sin recortar; nunca {@code null} (vacío si no hubo voz)
 * @param detectedLanguage código ISO 639-1 del idioma detectado (por ejemplo {@code es} o {@code en}); puede ser
 *        {@code null} si el proveedor no lo informa
 * @param languageConfidence confianza de la detección, de 0.0 a 1.0; {@code null} si el proveedor no la informa
 * @param speechDuration voz reconocida: la suma de lo que dura cada palabra, sin los silencios entre ellas;
 *        {@code null} si el proveedor no informa las palabras
 * @param words palabras con su inicio y su fin, en el orden en que se dijeron; vacía si el proveedor no las informa
 */
public record Transcription(String text, String detectedLanguage, Double languageConfidence,
        Duration speechDuration, List<TimedWord> words) {

    /** Normaliza el texto ausente a vacío y las palabras ausentes a una lista vacía e inmutable. */
    public Transcription {
        text = text == null ? "" : text;
        words = words == null ? List.of() : List.copyOf(words);
    }

    /**
     * Crea una transcripción sin las palabras con tiempos.
     *
     * @param text texto transcrito
     * @param detectedLanguage idioma detectado
     * @param languageConfidence confianza de la detección
     * @param speechDuration voz reconocida
     */
    public Transcription(String text, String detectedLanguage, Double languageConfidence,
            Duration speechDuration) {
        this(text, detectedLanguage, languageConfidence, speechDuration, List.of());
    }

    /**
     * Crea una transcripción sin la duración de la voz, para un proveedor que no informa las palabras.
     *
     * @param text texto transcrito
     * @param detectedLanguage idioma detectado
     * @param languageConfidence confianza de la detección
     */
    public Transcription(String text, String detectedLanguage, Double languageConfidence) {
        this(text, detectedLanguage, languageConfidence, null);
    }

    /** No imprime el texto: una transcripción nunca llega a un registro. */
    @Override
    public String toString() {
        return "Transcription[length=" + text.length() + ", detectedLanguage=" + detectedLanguage
                + ", languageConfidence=" + languageConfidence + ", speechDuration=" + speechDuration + ", words="
                + words.size() + "]";
    }
}
