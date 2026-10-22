package tech.cameia.entrevista.domain.exception;

/**
 * La petición no se pudo interpretar antes de llegar a una ruta del servicio; el caso típico es
 * una ruta con codificación porcentual inválida.
 */
public class RequestPathInvalidException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "La ruta solicitada no es válida.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public RequestPathInvalidException() {
        super(ErrorCode.REQUEST_PATH_INVALID_FORMAT, MESSAGE);
    }
}
