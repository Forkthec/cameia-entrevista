package tech.cameia.entrevista.domain.exception;

/**
 * El audio supera los 10 MiB (10 485 760 bytes) que admite una respuesta por voz (CA-5.8.13).
 */
public class AudioTooLargeException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu audio no puede superar los 10 MB.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public AudioTooLargeException() {
        super(ErrorCode.AUDIO_TOO_LARGE, MESSAGE);
    }
}
