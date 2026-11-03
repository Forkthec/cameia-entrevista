package tech.cameia.entrevista.domain.exception;

/**
 * Otra escritura cambió la sesión antes que esta.
 */
public class SessionConflictException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "La entrevista cambió mientras se procesaba tu solicitud. Inténtalo de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SessionConflictException() {
        super(ErrorCode.SESSION_CONFLICT, MESSAGE);
    }
}
