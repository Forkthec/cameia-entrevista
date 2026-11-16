package tech.cameia.entrevista.domain.exception;

/**
 * La pregunta pedida todavía no se ha mostrado: es posterior a la pendiente (CA-5.15.5).
 */
public class QuestionNotAvailableException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esa pregunta todavía no está disponible.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public QuestionNotAvailableException() {
        super(ErrorCode.QUESTION_NOT_AVAILABLE, MESSAGE);
    }
}
