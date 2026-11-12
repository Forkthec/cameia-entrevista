package tech.cameia.entrevista.domain.exception;

/**
 * El audio llegó en un tipo de contenido que el servicio no admite.
 */
public class AudioFormatNotAllowedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tipo de contenido no admitido.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AudioFormatNotAllowedException() {
        super(ErrorCode.AUDIO_FORMAT_NOT_ALLOWED, MESSAGE);
    }
}
