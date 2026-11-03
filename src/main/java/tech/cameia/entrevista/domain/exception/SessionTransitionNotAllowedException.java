package tech.cameia.entrevista.domain.exception;

/**
 * Se pidió una transición de la sesión que su estado no admite.
 */
public class SessionTransitionNotAllowedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta entrevista ya no admite esa acción.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SessionTransitionNotAllowedException() {
        super(ErrorCode.SESSION_TRANSITION_NOT_ALLOWED, MESSAGE);
    }
}
