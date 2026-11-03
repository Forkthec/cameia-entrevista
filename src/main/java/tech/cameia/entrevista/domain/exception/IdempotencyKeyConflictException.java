package tech.cameia.entrevista.domain.exception;

/**
 * La clave anti-duplicados ya se usó con otra configuración.
 */
public class IdempotencyKeyConflictException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta solicitud no coincide con la que enviaste antes. Inténtalo de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public IdempotencyKeyConflictException() {
        super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, MESSAGE);
    }
}
