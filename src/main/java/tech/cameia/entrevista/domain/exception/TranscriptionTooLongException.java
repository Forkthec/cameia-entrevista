package tech.cameia.entrevista.domain.exception;

/**
 * La transcripción tiene 4001 caracteres o más (CA-5.12.9): no se registra la respuesta, se descarta el audio y
 * no se gasta un intento de respuesta.
 */
public class TranscriptionTooLongException extends BusinessException {

    /** Mensaje que ve la persona, literal de CA-5.12.9. */
    public static final String MESSAGE = "Tu respuesta es demasiado larga. Graba una más corta o responde por texto.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TranscriptionTooLongException() {
        super(ErrorCode.TRANSCRIPTION_TOO_LONG, MESSAGE);
    }
}
