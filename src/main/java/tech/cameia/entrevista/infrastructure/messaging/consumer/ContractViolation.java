package tech.cameia.entrevista.infrastructure.messaging.consumer;

/**
 * Un mensaje que incumple el contrato: lleva la razón estable del rechazo y, si aplica, la ruta del
 * campo que falló. No tiene mensaje de texto: nunca se escribe el valor recibido.
 */
final class ContractViolation extends RuntimeException {

    /** El {@code message_id} falta o no es un UUID canónico. */
    static final String MESSAGE_ID_INVALID = "MESSAGE_ID_INVALID";

    /** El {@code type} falta o no es el del evento. */
    static final String EVENT_TYPE_INVALID = "EVENT_TYPE_INVALID";

    /** El encabezado {@code x-event-version} falta o no es el entero esperado. */
    static final String EVENT_VERSION_UNSUPPORTED = "EVENT_VERSION_UNSUPPORTED";

    /** El cuerpo no se puede leer o no tiene la forma del contrato. */
    static final String PAYLOAD_INVALID_FORMAT = "PAYLOAD_INVALID_FORMAT";

    /** El cuerpo tiene la forma pero incumple una regla del contrato. */
    static final String PAYLOAD_CONTRACT_VIOLATION = "PAYLOAD_CONTRACT_VIOLATION";

    /** El catálogo de roles trae dos roles con el mismo {@code codigo}. */
    static final String CATALOG_ROLE_DUPLICATED = "CATALOG_ROLE_DUPLICATED";

    private static final long serialVersionUID = 1L;

    private final String reason;
    private final String field;

    /**
     * @param reason razón estable del rechazo
     * @param field  ruta del campo según el contrato, o {@code null} si no aplica
     */
    ContractViolation(String reason, String field) {
        super(reason, null, false, false);
        this.reason = reason;
        this.field = field;
    }

    /** @return razón estable del rechazo */
    String reason() {
        return reason;
    }

    /** @return ruta del campo según el contrato, o {@code null} si no aplica */
    String field() {
        return field;
    }
}
