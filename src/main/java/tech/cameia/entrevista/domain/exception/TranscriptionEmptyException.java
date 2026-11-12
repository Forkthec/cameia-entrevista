package tech.cameia.entrevista.domain.exception;

/**
 * La transcripción del audio no trae texto: no se detectó voz (CA-5.8.11).
 */
public class TranscriptionEmptyException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No detectamos tu voz, intenta de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TranscriptionEmptyException() {
        super(ErrorCode.TRANSCRIPTION_EMPTY, MESSAGE);
    }
}
