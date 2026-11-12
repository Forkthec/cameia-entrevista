package tech.cameia.entrevista.domain.model;

/**
 * Operación de la IA que registra una ejecución en {@code ejecucion_ia}.
 */
public enum AiOperation {

    /** Planificar las cinco preguntas de la sesión. */
    PLAN("PLANIFICAR"),
    /** Evaluar la respuesta de un turno. */
    EVALUATE("EVALUAR");

    private final String code;

    AiOperation(String code) {
        this.code = code;
    }

    /**
     * Valor que se guarda en {@code ejecucion_ia.tipo_operacion}.
     *
     * @return el valor de la base
     */
    public String code() {
        return code;
    }

    /**
     * Busca la operación por su valor guardado.
     *
     * @param code valor guardado
     * @return la operación
     * @throws IllegalArgumentException si ninguna operación tiene ese valor
     */
    public static AiOperation fromCode(String code) {
        for (AiOperation candidate : values()) {
            if (candidate.code.equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Operación de ejecución desconocida");
    }
}
