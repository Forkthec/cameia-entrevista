package tech.cameia.entrevista.domain.exception;

/**
 * Raíz de las excepciones de negocio del microservicio.
 *
 * <p>Separa un fallo previsto por las reglas del dominio, que el cliente puede corregir,
 * de un fallo técnico. La capa de presentación traduce cada subclase a su estado HTTP y a
 * un documento Problem Details; ninguna de ellas debe incluir datos sensibles en su
 * mensaje, porque ese texto llega al usuario.</p>
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Crea la excepción con su código estable y el mensaje que verá el usuario.
     *
     * @param errorCode código estable del error, que llega al cliente en el miembro {@code code}
     * @param mensaje texto en español, sin credenciales ni datos de la petición
     */
    protected BusinessException(ErrorCode errorCode, String mensaje) {
        super(mensaje);
        this.errorCode = errorCode;
    }

    /**
     * Indica el código estable del error.
     *
     * @return código que el cliente usa para decidir qué mostrar
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
