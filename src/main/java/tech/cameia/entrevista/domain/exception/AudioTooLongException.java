package tech.cameia.entrevista.domain.exception;

/**
 * El audio dura más de 3 minutos (CA-5.8.8).
 */
public class AudioTooLongException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu respuesta no puede durar más de 3 minutos.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AudioTooLongException() {
        super(ErrorCode.AUDIO_TOO_LONG, MESSAGE);
    }
}
