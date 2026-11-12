package tech.cameia.entrevista.domain.exception;

/**
 * Voz transcribió el audio, pero el texto quedó vacío: no se registra ninguna respuesta.
 */
public class TranscriptionEmptyException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No detectamos tu voz, intenta de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TranscriptionEmptyException() {
        super(ErrorCode.TRANSCRIPTION_EMPTY, MESSAGE);
    }
}
