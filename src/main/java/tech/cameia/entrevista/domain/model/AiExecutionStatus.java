package tech.cameia.entrevista.domain.model;

/**
 * Estado de una llamada al modelo de lenguaje registrada en {@code ejecucion_ia}.
 */
public enum AiExecutionStatus {

    /** La llamada está en curso; en la planificación, es la marca que impide una segunda. */
    IN_PROGRESS("EN_CURSO"),
    /** La llamada terminó y su salida se usó. */
    SUCCEEDED("EXITOSA"),
    /** La llamada falló o su salida se descartó. */
    FAILED("FALLIDA");

    private final String code;

    AiExecutionStatus(String code) {
        this.code = code;
    }

    /**
     * Valor que se guarda en {@code ejecucion_ia.estado}.
     *
     * @return el valor de la base
     */
    public String code() {
        return code;
    }

    /**
     * Busca el estado por su valor guardado.
     *
     * @param code valor guardado
     * @return el estado
     * @throws IllegalArgumentException si ningún estado tiene ese valor
     */
    public static AiExecutionStatus fromCode(String code) {
        for (AiExecutionStatus candidate : values()) {
            if (candidate.code.equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Estado de ejecución desconocido");
    }
}
