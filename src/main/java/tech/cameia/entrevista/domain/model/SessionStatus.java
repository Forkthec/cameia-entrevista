package tech.cameia.entrevista.domain.model;

/**
 * Estados de una sesión de entrevista y las transiciones que admite la máquina de estados.
 */
public enum SessionStatus {

    /** La sesión está preparada pero aún no empezó. */
    CONFIGURED("CONFIGURADA"),
    /** La persona está respondiendo la entrevista. */
    IN_PROGRESS("EN_CURSO"),
    /** Las respuestas ya se entregaron y se evalúan. */
    EVALUATING("EVALUANDO"),
    /** La entrevista terminó con su evaluación. */
    FINISHED("FINALIZADA"),
    /** La persona dejó la entrevista antes de terminarla. */
    ABANDONED("ABANDONADA");

    private final String code;

    SessionStatus(String code) {
        this.code = code;
    }

    /**
     * Valor que se guarda en {@code sesion.estado} y se publica en el contrato.
     *
     * @return el valor del backlog de este estado
     */
    public String code() {
        return code;
    }

    /**
     * Busca el estado por su valor guardado.
     *
     * @param code valor guardado en la base o recibido en el contrato
     * @return el estado cuyo valor es {@code code}
     * @throws IllegalArgumentException si ningún estado tiene ese valor
     */
    public static SessionStatus fromCode(String code) {
        for (SessionStatus candidate : values()) {
            if (candidate.code.equals(code)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Estado de sesión desconocido: " + code);
    }

    /**
     * Indica si la máquina admite pasar de este estado a {@code target}. Solo hay cuatro
     * transiciones; borrar una sesión configurada no es una transición (ver
     * {@link InterviewSession#requireDeletable()}).
     *
     * @param target estado al que se quiere pasar
     * @return {@code true} si la transición está permitida
     */
    public boolean canChangeTo(SessionStatus target) {
        return switch (this) {
            case CONFIGURED -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == ABANDONED || target == EVALUATING;
            case EVALUATING -> target == FINISHED;
            case FINISHED, ABANDONED -> false;
        };
    }
}
