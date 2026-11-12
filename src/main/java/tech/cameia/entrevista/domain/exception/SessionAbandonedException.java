package tech.cameia.entrevista.domain.exception;

/**
 * La entrevista quedó abandonada y no admite respuestas (CA-5.5.4, CA-5.8.28).
 */
public class SessionAbandonedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Sesión abandonada: no genera evaluación.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SessionAbandonedException() {
        super(ErrorCode.SESSION_ABANDONED, MESSAGE);
    }
}
