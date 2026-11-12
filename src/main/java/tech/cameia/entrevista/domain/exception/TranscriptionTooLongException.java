package tech.cameia.entrevista.domain.exception;

/**
 * La transcripción supera los 4000 caracteres que admite una respuesta: no se registra.
 */
public class TranscriptionTooLongException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu respuesta es demasiado larga. Graba una respuesta más corta o responde por texto.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TranscriptionTooLongException() {
        super(ErrorCode.TRANSCRIPTION_TOO_LONG, MESSAGE);
    }
}
