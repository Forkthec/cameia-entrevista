package tech.cameia.entrevista.domain.exception;

/**
 * La petición no trae el audio de la respuesta, o llega vacío (HU-5.8).
 */
public class AudioRequiredException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Graba tu respuesta antes de enviarla.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AudioRequiredException() {
        super(ErrorCode.AUDIO_REQUIRED, MESSAGE);
    }
}
