package tech.cameia.entrevista.domain.exception;

/**
 * El audio dura menos de 1,5 segundos: no puede traer los 2 segundos de voz que exige la interfaz (CA-5.8.10).
 */
public class AudioTooShortException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No detectamos tu voz, intenta de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AudioTooShortException() {
        super(ErrorCode.AUDIO_TOO_SHORT, MESSAGE);
    }
}
