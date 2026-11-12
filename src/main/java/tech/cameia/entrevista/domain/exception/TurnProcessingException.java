package tech.cameia.entrevista.domain.exception;

/**
 * Ya se está procesando (o reintentando transcribir) una respuesta del mismo turno; el segundo envío se rechaza sin guardarlo ni transcribirlo (CA-5.13.8).
 */
public class TurnProcessingException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Ya estamos procesando tu respuesta a esta pregunta. Espera un momento.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TurnProcessingException() {
        super(ErrorCode.TURN_PROCESSING, MESSAGE);
    }
}
