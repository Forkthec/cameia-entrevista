package tech.cameia.entrevista.domain.exception;

/**
 * La pregunta ya gastó sus usos de audio (CA-5.15.6, CA-5.15.7).
 */
public class QuestionAudioLimitReachedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Ya escuchaste esta pregunta el máximo de veces. Puedes leerla en pantalla.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public QuestionAudioLimitReachedException() {
        super(ErrorCode.QUESTION_AUDIO_LIMIT_REACHED, MESSAGE);
    }
}
