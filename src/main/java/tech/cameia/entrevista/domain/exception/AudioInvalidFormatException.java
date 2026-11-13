package tech.cameia.entrevista.domain.exception;

/**
 * El contenido real del audio no es WebM con Opus ni MP4 con AAC, trae una pista de video o no se puede leer (CA-5.8.14, CA-5.8.15). El mensaje no revela la causa.
 */
public class AudioInvalidFormatException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Este formato de audio no es compatible. Graba de nuevo o responde por texto.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AudioInvalidFormatException() {
        super(ErrorCode.AUDIO_INVALID_FORMAT, MESSAGE);
    }
}
