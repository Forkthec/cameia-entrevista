package tech.cameia.entrevista.domain.exception;

/**
 * Los caracteres enviados a la voz en el mes calendario (UTC) llegaron al tope configurado (CA-5.15.28).
 */
public class SpeechMonthlyLimitReachedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "La voz no está disponible por ahora. Puedes leer la pregunta.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SpeechMonthlyLimitReachedException() {
        super(ErrorCode.SPEECH_MONTHLY_LIMIT_REACHED, MESSAGE);
    }
}
