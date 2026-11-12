package tech.cameia.entrevista.domain.exception;

/**
 * La entrevista está en evaluación o finalizada y no admite respuestas (CA-5.5.5, CA-5.8.28).
 */
public class SessionAlreadyClosedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta entrevista ya no admite esa acción.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SessionAlreadyClosedException() {
        super(ErrorCode.SESSION_ALREADY_CLOSED, MESSAGE);
    }
}
