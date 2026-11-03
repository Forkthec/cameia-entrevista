package tech.cameia.entrevista.domain.model;

/**
 * Forma en que la persona responde: por voz o por escrito.
 */
public enum AnswerMode {

    /** Respuesta hablada */
    AUDIO("AUDIO"),
    /** Respuesta escrita */
    TEXT("TEXTO");

    private final String code;

    AnswerMode(String code) {
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
    public static AnswerMode fromCode(String code) {
        for (AnswerMode candidate : values()) {
            if (candidate.code.equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Valor desconocido: " + code);
    }
}
