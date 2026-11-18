package tech.cameia.entrevista.domain.exception;

/**
 * La instancia ya atiende el máximo de síntesis simultáneas (CA-5.15.26).
 */
public class SpeechBusyException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Hay muchas personas escuchando. Inténtalo en unos segundos.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SpeechBusyException() {
        super(ErrorCode.SPEECH_BUSY, MESSAGE);
    }
}
