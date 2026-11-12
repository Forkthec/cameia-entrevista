package tech.cameia.entrevista.domain.exception;

/**
 * El turno no está esperando un reintento de su evaluación.
 */
public class TurnNotRetryableException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu respuesta no está esperando un reintento.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TurnNotRetryableException() {
        super(ErrorCode.TURN_NOT_RETRYABLE, MESSAGE);
    }
}
