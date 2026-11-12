package tech.cameia.entrevista.domain.exception;

/**
 * La entrevista se responde en otra forma: se envió audio a una entrevista configurada para responder por texto.
 */
public class AnswerFormatNotAllowedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Esta entrevista no admite respuestas en esa forma.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AnswerFormatNotAllowedException() {
        super(ErrorCode.ANSWER_FORMAT_NOT_ALLOWED, MESSAGE);
    }
}
