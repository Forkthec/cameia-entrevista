package tech.cameia.entrevista.domain.exception;

/**
 * La respuesta del turno se está procesando o el turno cambió al mismo tiempo.
 */
public class TurnConflictException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu respuesta se está procesando. Espera un momento.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TurnConflictException() {
        super(ErrorCode.TURN_CONFLICT, MESSAGE);
    }
}
