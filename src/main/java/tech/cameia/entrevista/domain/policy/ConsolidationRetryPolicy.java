package tech.cameia.entrevista.domain.policy;

import java.time.Duration;
import java.util.List;

/**
 * Ciclo de consolidación de una sesión: hasta tres intentos con espera creciente entre ellos. No accede a la base ni
 * al reloj.
 */
public final class ConsolidationRetryPolicy {

    /** Esperas antes del segundo y del tercer intento. */
    public static final List<Duration> WAITS = List.of(Duration.ofMillis(200), Duration.ofMillis(400));

    /** Intentos en total: el inicial y uno por cada espera. */
    public static final int MAX_ATTEMPTS = WAITS.size() + 1;

    /**
     * Espera que corresponde antes de un intento.
     *
     * @param attempt número del intento, 2 o 3
     * @return tiempo que se espera antes de ese intento
     */
    public Duration waitBefore(int attempt) {
        return WAITS.get(attempt - 2);
    }
}
