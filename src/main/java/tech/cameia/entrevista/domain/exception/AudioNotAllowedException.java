package tech.cameia.entrevista.domain.exception;

/**
 * Llegó un audio para una entrevista configurada con respuesta por texto (CA-5.8.20).
 */
public class AudioNotAllowedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta entrevista es por texto: escribe tu respuesta.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AudioNotAllowedException() {
        super(ErrorCode.AUDIO_NOT_ALLOWED, MESSAGE);
    }
}
