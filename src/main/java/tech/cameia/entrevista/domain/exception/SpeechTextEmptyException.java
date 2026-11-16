package tech.cameia.entrevista.domain.exception;

/**
 * El texto de la pregunta quedó vacío o solo con espacios; no se envía a la voz (CA-5.15.20).
 */
public class SpeechTextEmptyException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No pudimos reproducir esta pregunta. Puedes leerla en pantalla.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SpeechTextEmptyException() {
        super(ErrorCode.SPEECH_TEXT_EMPTY, MESSAGE);
    }
}
