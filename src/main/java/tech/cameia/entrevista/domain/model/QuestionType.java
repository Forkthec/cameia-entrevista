package tech.cameia.entrevista.domain.model;

/**
 * Tipo de una pregunta de la entrevista. Los cuatro valores coinciden con el catálogo {@code tipo_pregunta}.
 */
public enum QuestionType {

    /** Pregunta de apertura. */
    APERTURA(false),
    /** Pregunta técnica del rol. */
    TECNICA(false),
    /** Pregunta de comportamiento: se evalúa con el método STAR. */
    CONDUCTUAL(true),
    /** Pregunta que plantea una situación hipotética. */
    SITUACIONAL(false);

    private final boolean starApplies;

    QuestionType(boolean starApplies) {
        this.starApplies = starApplies;
    }

    /**
     * Indica si la respuesta se evalúa con el método STAR.
     *
     * @return {@code true} solo para las preguntas conductuales
     */
    public boolean startApplies() {
        return starApplies;
    }

    /**
     * Valor que se guarda en {@code pregunta.tipo} y se publica en el contrato.
     *
     * @return el nombre del tipo
     */
    public String code() {
        return name();
    }

    /**
     * Busca el tipo por su valor guardado, exacto y en mayúsculas.
     *
     * @param code valor guardado
     * @return el tipo cuyo valor es {@code code}
     * @throws IllegalArgumentException si ningún tipo tiene ese valor
     */
    public static QuestionType fromCode(String code) {
        for (QuestionType candidate : values()) {
            if (candidate.name().equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Tipo de pregunta desconocido");
    }
}
