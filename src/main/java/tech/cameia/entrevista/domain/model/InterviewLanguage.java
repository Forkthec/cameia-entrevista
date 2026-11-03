package tech.cameia.entrevista.domain.model;

/**
 * Idioma en que se conduce la entrevista.
 */
public enum InterviewLanguage {

    /** Español de Colombia */
    SPANISH_COLOMBIA("es-CO"),
    /** Inglés */
    ENGLISH("en");

    private final String code;

    InterviewLanguage(String code) {
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
    public static InterviewLanguage fromCode(String code) {
        for (InterviewLanguage candidate : values()) {
            if (candidate.code.equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Valor desconocido: " + code);
    }
}
