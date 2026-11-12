package tech.cameia.entrevista.domain.exception;

/**
 * El turno ya tiene su respuesta definitiva; no se registra otra (CA-5.13.7).
 */
public class TurnAlreadyAnsweredException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta pregunta ya tiene una respuesta registrada.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TurnAlreadyAnsweredException() {
        super(ErrorCode.TURN_ALREADY_ANSWERED, MESSAGE);
    }
}
