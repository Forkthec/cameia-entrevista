package tech.cameia.entrevista.domain.exception;

/**
 * Falta la identidad del Gateway o no es válida.
 */
public class IdentityRequiredException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu sesión expiró. Inicia sesión de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public IdentityRequiredException() {
        super(ErrorCode.IDENTITY_REQUIRED, MESSAGE);
    }
}
