package tech.cameia.entrevista.domain.model;

/**
 * Formatos de audio que admite una respuesta por voz: los que graban los navegadores con {@code MediaRecorder}
 * (Chrome, Edge y Firefox en WebM con Opus; Safari en MP4 con AAC). Se deciden por el contenido real del archivo,
 * nunca por el tipo declarado ni por el nombre (CA-5.8.15, CA-5.8.16).
 */
public enum AudioFormat {

    /** Contenedor WebM con una sola pista de audio Opus. */
    WEBM_OPUS("audio/webm"),

    /** Contenedor MP4 con una sola pista de audio AAC. */
    MP4_AAC("audio/mp4");

    private final String mimeType;

    AudioFormat(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Tipo MIME con el que el audio se entrega al servicio de transcripción.
     *
     * @return el tipo MIME del contenedor
     */
    public String mimeType() {
        return mimeType;
    }
}
