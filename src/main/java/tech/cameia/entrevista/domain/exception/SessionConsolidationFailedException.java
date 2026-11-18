package tech.cameia.entrevista.domain.exception;

/**
 * La evaluación de la entrevista no se pudo reunir: la sesión sigue en evaluación con todas sus respuestas guardadas
 * y la persona puede pedir el cierre de nuevo.
 */
public class SessionConsolidationFailedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Ocurrió un error. Inténtalo de nuevo.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SessionConsolidationFailedException() {
        super(ErrorCode.SESSION_CONSOLIDATION_FAILED, MESSAGE);
    }
}
