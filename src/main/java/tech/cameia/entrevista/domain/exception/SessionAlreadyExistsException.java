package tech.cameia.entrevista.domain.exception;

/**
 * La persona ya tiene otra sesión configurada o en curso.
 */
public class SessionAlreadyExistsException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Ya tienes una entrevista en preparación o en curso. Inténtalo de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SessionAlreadyExistsException() {
        super(ErrorCode.SESSION_ALREADY_EXISTS, MESSAGE);
    }
}
