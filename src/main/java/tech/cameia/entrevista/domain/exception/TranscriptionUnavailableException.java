package tech.cameia.entrevista.domain.exception;

/**
 * El servicio de transcripción no respondió a tiempo, falló o devolvió una respuesta inutilizable. La pregunta sigue
 * pendiente y la persona puede grabar de nuevo o responder por texto.
 *
 * <p>La causa técnica viaja como {@link #getCause()} y solo llega al log, nunca con el audio ni el texto.</p>
 */
public class TranscriptionUnavailableException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.";

    /**
     * Crea la excepción conservando la causa técnica.
     *
     * @param cause fallo del proveedor, para el log; {@code null} si la respuesta llegó pero no se puede usar
     */
    public TranscriptionUnavailableException(Throwable cause) {
        super(ErrorCode.TRANSCRIPTION_UNAVAILABLE, MESSAGE);
        initCause(cause);
    }
}
