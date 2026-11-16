package tech.cameia.entrevista.domain.exception;

/**
 * El servicio de voz no respondió dentro del tiempo máximo configurado (CA-5.15.8, CA-5.15.22).
 *
 * <p>Lleva la razón técnica para el registro, nunca el texto de la pregunta ni el audio.</p>
 */
public class SpeechTimeoutException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "La voz tardó demasiado. Inténtalo de nuevo o léela en pantalla.";

    private final String reason;

    /**
     * Crea la excepción.
     *
     * @param reason razón corta y sin datos de la persona (por ejemplo {@code status=429})
     * @param cause causa técnica, o {@code null}
     */
    public SpeechTimeoutException(String reason, Throwable cause) {
        super(ErrorCode.SPEECH_TIMEOUT, MESSAGE);
        this.reason = reason;
        if (cause != null) {
            initCause(cause);
        }
    }

    /** @return la razón técnica, para el registro */
    public String getReason() {
        return reason;
    }
}
