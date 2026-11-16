package tech.cameia.entrevista.domain.exception;

/**
 * La función de escuchar la pregunta está apagada por configuración (CA-5.15.9).
 */
public class SpeechDisabledException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "La voz no está disponible por ahora. Puedes leer la pregunta.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SpeechDisabledException() {
        super(ErrorCode.SPEECH_DISABLED, MESSAGE);
    }
}
