package tech.cameia.entrevista.domain.model;

/**
 * Lo que devolvió el servicio de transcripción para un audio: el texto tal como salió y el idioma que detectó.
 *
 * <p>El texto puede venir vacío: decidir si sirve como respuesta es de
 * {@link tech.cameia.entrevista.domain.policy.TranscriptionPolicy}, no del proveedor.</p>
 *
 * @param text texto transcrito, sin recortar; nunca {@code null} (vacío si no hubo voz)
 * @param detectedLanguage código ISO 639-1 del idioma detectado (por ejemplo {@code es} o {@code en}); puede ser
 *        {@code null} si el proveedor no lo informa
 * @param languageConfidence confianza de la detección, de 0.0 a 1.0; {@code null} si el proveedor no la informa
 */
public record Transcription(String text, String detectedLanguage, Double languageConfidence) {

    /** Normaliza el texto ausente a vacío. */
    public Transcription {
        text = text == null ? "" : text;
    }

    /** No imprime el texto: una transcripción nunca llega a un registro. */
    @Override
    public String toString() {
        return "Transcription[length=" + text.length() + ", detectedLanguage=" + detectedLanguage
                + ", languageConfidence=" + languageConfidence + "]";
    }
}
