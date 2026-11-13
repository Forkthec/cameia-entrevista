package tech.cameia.entrevista.domain.exception;

/**
 * La pregunta ya tiene una respuesta registrada o su turno ya no está pendiente.
 */
public class AnswerAlreadyExistsException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta pregunta ya tiene una respuesta registrada.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AnswerAlreadyExistsException() {
        super(ErrorCode.ANSWER_ALREADY_EXISTS, MESSAGE);
    }
}
