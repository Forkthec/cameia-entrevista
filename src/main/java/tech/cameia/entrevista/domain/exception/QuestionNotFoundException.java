package tech.cameia.entrevista.domain.exception;

/**
 * El número de pregunta no es un entero de 1 a 5 escrito tal cual (CA-5.15.12, CA-5.15.17).
 */
public class QuestionNotFoundException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No encontramos lo que buscabas.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public QuestionNotFoundException() {
        super(ErrorCode.QUESTION_NOT_FOUND, MESSAGE);
    }
}
