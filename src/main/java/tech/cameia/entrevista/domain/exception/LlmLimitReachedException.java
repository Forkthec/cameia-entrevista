package tech.cameia.entrevista.domain.exception;

/**
 * Señala que el proveedor del modelo de lenguaje rechazó la llamada porque la cuota o el
 * límite de uso de la cuenta del servicio está agotado.
 *
 * <p>Quien usa el sistema no superó ningún límite propio, así que el cliente recibe
 * {@code 503}, como con {@link LlmUnavailableException}; el código distinto permite a Frontend
 * mostrar otro mensaje. La causa técnica viaja como {@link #getCause()} y solo llega al
 * log.</p>
 */
public class LlmLimitReachedException extends BusinessException {

    private static final String MENSAJE = "Ocurrió un error. Inténtalo de nuevo.";

    /**
     * Crea la excepción conservando la causa técnica.
     *
     * @param causa respuesta de cuota agotada del proveedor, para el log
     */
    public LlmLimitReachedException(Throwable causa) {
        super(ErrorCode.LLM_LIMIT_REACHED, MENSAJE);
        initCause(causa);
    }
}
