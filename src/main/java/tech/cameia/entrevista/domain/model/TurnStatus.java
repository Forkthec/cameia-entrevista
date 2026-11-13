package tech.cameia.entrevista.domain.model;

/**
 * Estados de un turno (una pregunta con su respuesta) y las transiciones que admite.
 */
public enum TurnStatus {

    /** El turno espera la respuesta. */
    PENDING,
    /** La respuesta se está procesando. */
    PROCESSING,
    /** El procesamiento terminó. */
    COMPLETED,
    /** El procesamiento falló y se volverá a intentar. */
    RETRYING;

    /**
     * Valor que se guarda en {@code turno.estado} y se publica en el contrato.
     *
     * @return el nombre del estado
     */
    public String code() {
        return name();
    }

    /**
     * Busca el estado por su valor guardado.
     *
     * @param code valor guardado en la base o recibido en el contrato
     * @return el estado cuyo valor es {@code code}
     * @throws IllegalArgumentException si ningún estado tiene ese valor
     */
    public static TurnStatus fromCode(String code) {
        for (TurnStatus candidate : values()) {
            if (candidate.name().equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Estado de turno desconocido: " + code);
    }

    /**
     * Indica si la máquina admite pasar de este estado a {@code target}.
     *
     * @param target estado al que se quiere pasar
     * @return {@code true} si la transición está permitida
     */
    public boolean canChangeTo(TurnStatus target) {
        return switch (this) {
            case PENDING -> target == PROCESSING;
            case PROCESSING -> target == COMPLETED || target == RETRYING;
            case RETRYING -> target == PROCESSING;
            case COMPLETED -> false;
        };
    }
}
