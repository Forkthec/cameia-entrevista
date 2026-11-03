package tech.cameia.entrevista.domain.model;

/**
 * Modalidad de una sesión: simulación completa o entrenamiento.
 */
public enum Modality {

    /** Entrevista completa de simulación */
    SIMULATION("SIMULACION"),
    /** Práctica con retroalimentación */
    TRAINING("ENTRENO");

    private final String code;

    Modality(String code) {
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
    public static Modality fromCode(String code) {
        for (Modality candidate : values()) {
            if (candidate.code.equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Valor desconocido: " + code);
    }
}
