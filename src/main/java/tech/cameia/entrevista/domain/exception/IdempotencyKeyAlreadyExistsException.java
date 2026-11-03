package tech.cameia.entrevista.domain.exception;

/**
 * La clave de la petición ya se registró para esa persona y operación.
 */
public class IdempotencyKeyAlreadyExistsException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta solicitud ya se registró.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public IdempotencyKeyAlreadyExistsException() {
        super(ErrorCode.IDEMPOTENCY_KEY_ALREADY_EXISTS, MESSAGE);
    }
}
