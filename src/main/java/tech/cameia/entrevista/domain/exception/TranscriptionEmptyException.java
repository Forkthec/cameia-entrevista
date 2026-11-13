package tech.cameia.entrevista.domain.exception;

/**
 * El audio se transcribió vacío o solo con espacios (CA-5.12.7): no se registra la respuesta, se descarta el audio
 * y la persona puede grabar de nuevo o responder por texto la misma pregunta.
 */
public class TranscriptionEmptyException extends BusinessException {

    /** Mensaje que ve la persona, literal de CA-5.12.7. */
    public static final String MESSAGE = "No detectamos tu voz, intenta de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TranscriptionEmptyException() {
        super(ErrorCode.TRANSCRIPTION_EMPTY, MESSAGE);
    }
}
