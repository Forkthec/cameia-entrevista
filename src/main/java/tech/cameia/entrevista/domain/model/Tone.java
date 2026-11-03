package tech.cameia.entrevista.domain.model;

/**
 * Tono con el que el entrevistador conduce la sesión.
 */
public enum Tone {

    /** Tono profesional y neutro */
    PROFESSIONAL_NEUTRAL("PROFESIONAL_NEUTRO"),
    /** Tono cálido */
    WARM("CALIDO"),
    /** Tono estricto */
    STRICT("ESTRICTO"),
    /** Tono motivador */
    MOTIVATING("MOTIVADOR");

    private final String code;

    Tone(String code) {
        this.code = code;
    }

    /**
     * Valor que se guarda en la base y se publica en el contrato.
     *
     * @return el valor del backlog de este elemento
     */
    public String code() {
        return code;
    }

    /**
     * Busca el elemento por su valor guardado.
     *
     * @param code valor guardado en la base o recibido en el contrato
     * @return el elemento cuyo valor es {@code code}
     * @throws IllegalArgumentException si ningún elemento tiene ese valor
     */
    public static Tone fromCode(String code) {
        for (Tone candidate : values()) {
            if (candidate.code.equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Valor desconocido: " + code);
    }
}
