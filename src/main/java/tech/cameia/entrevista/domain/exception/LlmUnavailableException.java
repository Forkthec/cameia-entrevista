package tech.cameia.entrevista.domain.exception;

/**
 * Señala que el modelo de lenguaje no respondió dentro del tiempo de espera, que el proveedor
 * falló de su lado o que la respuesta llegó vacía o inutilizable, así que la operación no se
 * pudo completar y la persona puede reintentar.
 *
 * <p>La causa está fuera del servicio y es pasajera: el cliente recibe {@code 503} en lugar de
 * {@code 500}. El mensaje es genérico a propósito; la causa técnica viaja como
 * {@link #getCause()} y solo llega al log, nunca con el texto enviado ni el recibido.</p>
 */
public class LlmUnavailableException extends BusinessException {

    private static final String MESSAGE = "Ocurrió un error. Inténtalo de nuevo.";

    /**
     * Crea la excepción conservando la causa técnica.
     *
     * @param causa error del cliente del modelo, para el log; {@code null} cuando la respuesta
     *              llegó pero no se puede usar
     */
    public LlmUnavailableException(Throwable cause) {
        super(ErrorCode.LLM_UNAVAILABLE, MESSAGE);
        initCause(cause);
    }
}
