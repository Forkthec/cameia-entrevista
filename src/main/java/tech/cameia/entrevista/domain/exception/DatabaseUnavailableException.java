package tech.cameia.entrevista.domain.exception;

/**
 * La base de datos no respondió o rechazó la conexión; la persona puede reintentar.
 */
public class DatabaseUnavailableException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Ocurrió un error. Inténtalo de nuevo.";

    /**
     * Crea la excepción con su código y su mensaje fijos.
     *
     * @param cause fallo técnico original, que solo se registra
     */
    public DatabaseUnavailableException(Throwable cause) {
        super(ErrorCode.DATABASE_UNAVAILABLE, MESSAGE);
        initCause(cause);
    }
}
