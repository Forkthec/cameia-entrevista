package tech.cameia.entrevista.domain.exception;

/**
 * La sesión agotó sus intentos de preparar las preguntas: el inicial y los dos reintentos.
 */
public class PlanningRetriesExhaustedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No pudimos preparar tu entrevista en este momento. Inténtalo más tarde.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public PlanningRetriesExhaustedException() {
        super(ErrorCode.PLANNING_RETRIES_EXHAUSTED, MESSAGE);
    }
}
