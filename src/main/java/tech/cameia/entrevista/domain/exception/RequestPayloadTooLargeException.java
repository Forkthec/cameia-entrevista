package tech.cameia.entrevista.domain.exception;

/**
 * Los datos enviados superan el tamaño que el servicio admite en una petición; el caso típico es
 * un cuerpo {@code multipart/form-data} de más de 10 MB.
 */
public class RequestPayloadTooLargeException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Los datos enviados superan el tamaño permitido.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public RequestPayloadTooLargeException() {
        super(ErrorCode.REQUEST_PAYLOAD_TOO_LARGE, MESSAGE);
    }
}
