package tech.cameia.entrevista.domain.exception;

/**
 * Los datos enviados superan el tamaño que el servicio admite en una petición; el caso típico es
 * un cuerpo {@code multipart/form-data} de más de 12 MiB (el audio de una respuesta admite 10 MiB y tiene su propio
 * código, {@code AUDIO_TOO_LARGE}).
 */
public class RequestPayloadTooLargeException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Los datos enviados superan el tamaño permitido.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public RequestPayloadTooLargeException() {
        super(ErrorCode.REQUEST_PAYLOAD_TOO_LARGE, MESSAGE);
    }
}
