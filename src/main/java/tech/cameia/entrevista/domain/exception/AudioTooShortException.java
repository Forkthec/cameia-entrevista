package tech.cameia.entrevista.domain.exception;

/**
 * La respuesta trae menos de 1,5 segundos de voz (CA-5.8.10): el archivo dura menos de eso, o el proveedor de la
 * transcripción reconoció menos de eso sumando lo que dura cada palabra. No se registra la respuesta.
 */
public class AudioTooShortException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No detectamos tu voz, intenta de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AudioTooShortException() {
        super(ErrorCode.AUDIO_TOO_SHORT, MESSAGE);
    }
}
