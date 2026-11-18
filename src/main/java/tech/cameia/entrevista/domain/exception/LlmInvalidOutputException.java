package tech.cameia.entrevista.domain.exception;

/**
 * El modelo de lenguaje respondió, pero su salida no cumple el formato que el caso de uso exige.
 *
 * <p>Es un fallo del modelo y no de la persona, así que el cliente recibe {@code 503}, como con
 * {@link LlmUnavailableException}. La razón concreta ({@link #getReason()}) solo llega al log: nunca
 * lleva texto del modelo ni de la persona.</p>
 */
public class LlmInvalidOutputException extends BusinessException {

    private static final String MESSAGE = "Ocurrió un error. Inténtalo de nuevo.";

    private final String reason;

    /**
     * Crea la excepción.
     *
     * @param reason regla del plan que se incumplió, sin texto del modelo; solo para el log
     */
    public LlmInvalidOutputException(String reason) {
        super(ErrorCode.LLM_INVALID_OUTPUT, MESSAGE);
        this.reason = reason;
    }

    /**
     * Indica qué regla del plan se incumplió.
     *
     * @return razón corta, sin datos de la persona
     */
    public String getReason() {
        return reason;
    }
}
