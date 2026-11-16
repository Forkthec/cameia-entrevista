package tech.cameia.entrevista.domain.exception;

/**
 * El texto de la pregunta supera los 500 puntos de código; no se envía a la voz (CA-5.15.13).
 */
public class SpeechTextTooLongException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No pudimos reproducir esta pregunta. Puedes leerla en pantalla.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SpeechTextTooLongException() {
        super(ErrorCode.SPEECH_TEXT_TOO_LONG, MESSAGE);
    }
}
