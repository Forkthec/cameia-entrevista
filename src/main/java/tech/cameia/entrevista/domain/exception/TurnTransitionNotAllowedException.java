package tech.cameia.entrevista.domain.exception;

/**
 * Se pidió una transición del turno que su estado no admite.
 */
public class TurnTransitionNotAllowedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta pregunta ya no admite esa acción.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TurnTransitionNotAllowedException() {
        super(ErrorCode.TURN_TRANSITION_NOT_ALLOWED, MESSAGE);
    }
}
