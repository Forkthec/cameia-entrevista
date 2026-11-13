package tech.cameia.entrevista.domain.model;

/**
 * Por qué falló un intento de transcripción. Decide si se reintenta (HU-5.13, ver
 * {@code tech.cameia.entrevista.domain.policy.TranscriptionRetryPolicy}).
 */
public enum TranscriptionFailure {

    /** Error transitorio: el proveedor respondió 503 (no disponible) o rechazó la conexión. Se reintenta una vez. */
    TRANSIENT,
    /** El intento agotó su tiempo de espera. No se reintenta: la persona ya esperó el máximo (CA-5.13.3). */
    TIMEOUT,
    /** Cualquier otro fallo: el proveedor rechazó el audio, respondió otro error o algo ilegible. No se reintenta. */
    REJECTED
}
