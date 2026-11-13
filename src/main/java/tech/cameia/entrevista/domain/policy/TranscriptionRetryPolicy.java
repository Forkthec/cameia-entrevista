package tech.cameia.entrevista.domain.policy;

import java.time.Duration;
import java.util.Objects;

import tech.cameia.entrevista.domain.model.TranscriptionFailure;

/**
 * Regla del reintento automático de la transcripción (HU-5.13).
 *
 * <ul>
 *   <li>Como máximo {@value #MAX_ATTEMPTS} intentos por envío: el inicial y un reintento sin que la persona haga nada
 *       (CA-5.13.1).</li>
 *   <li>Solo se reintenta un error transitorio: el proveedor no disponible (503) o la conexión rechazada.</li>
 *   <li>Un intento que agotó su tiempo de espera no se reintenta (CA-5.13.3), ni cualquier otro fallo.</li>
 *   <li>Tampoco se reintenta si un intento completo más la pausa ya no caben en el presupuesto total de la petición
 *       (por debajo de los 120 s del Gateway).</li>
 * </ul>
 */
public final class TranscriptionRetryPolicy {

    /** Intentos máximos por envío de audio. */
    public static final int MAX_ATTEMPTS = 2;

    private final Duration attemptTimeout;
    private final Duration pause;
    private final Duration budget;

    /**
     * Crea la política.
     *
     * @param attemptTimeout tiempo máximo de cada intento
     * @param pause pausa antes del reintento
     * @param budget tiempo total que puede durar la transcripción de un envío, con todos sus intentos
     */
    public TranscriptionRetryPolicy(Duration attemptTimeout, Duration pause, Duration budget) {
        this.attemptTimeout = Objects.requireNonNull(attemptTimeout, "attemptTimeout");
        this.pause = Objects.requireNonNull(pause, "pause");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    /**
     * Decide si se hace otro intento después de uno fallido.
     *
     * @param failedAttempt número del intento que acaba de fallar, desde 1
     * @param failure causa del fallo
     * @param elapsed tiempo transcurrido desde el primer intento
     * @return {@code true} si se debe intentar una vez más
     */
    public boolean shouldRetry(int failedAttempt, TranscriptionFailure failure, Duration elapsed) {
        return failure == TranscriptionFailure.TRANSIENT && failedAttempt < MAX_ATTEMPTS
                && elapsed.plus(pause).plus(attemptTimeout).compareTo(budget) <= 0;
    }

    /** @return pausa antes del reintento */
    public Duration pause() {
        return pause;
    }

    /**
     * Tiempo máximo que puede durar la transcripción de un envío; sobre él se calcula cuándo una reserva del turno es
     * de un proceso que murió.
     *
     * @return el máximo entre el presupuesto y todos los intentos con su pausa
     */
    public Duration maxDuration() {
        Duration allAttempts = attemptTimeout.multipliedBy(MAX_ATTEMPTS).plus(pause);
        return allAttempts.compareTo(budget) > 0 ? budget : allAttempts;
    }
}
