package tech.cameia.entrevista.domain.model;

/**
 * Por qué falló un intento de transcripción. Decide si se reintenta (ver
 * {@code tech.cameia.entrevista.domain.policy.TranscriptionRetryPolicy}).
 */
public enum TranscriptionFailure {

    /** Error transitorio: Voz respondió 503 (servicio no disponible) o rechazó la conexión. Se reintenta una vez. */
    TRANSIENT,
    /** El intento agotó su tiempo de espera (60 s por defecto). No se reintenta (CA-5.13.3). */
    TIMEOUT,
    /** Cualquier otro fallo: Voz rechazó el audio, respondió otro error o una respuesta ilegible. No se reintenta. */
    REJECTED
}
