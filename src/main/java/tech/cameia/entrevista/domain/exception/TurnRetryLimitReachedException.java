package tech.cameia.entrevista.domain.exception;

/**
 * La persona ya usó los cuatro reintentos manuales del turno.
 */
public class TurnRetryLimitReachedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No pudimos evaluar tu respuesta en este momento. Tu respuesta quedó guardada; inténtalo más tarde.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TurnRetryLimitReachedException() {
        super(ErrorCode.TURN_RETRY_LIMIT_REACHED, MESSAGE);
    }
}
