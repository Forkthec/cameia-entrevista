package tech.cameia.entrevista.domain.policy;

import java.time.Duration;
import java.time.Instant;

import tech.cameia.entrevista.domain.model.AiExecution;

/**
 * Regla de caducidad de la marca de planificación: la ejecución {@code EN_CURSO} que impide un segundo inicio.
 *
 * <p>Si el proceso muere durante la llamada, la marca queda abierta. Pasado el tiempo de espera del modelo más un
 * margen, un pedido nuevo la considera caducada, la cierra como fallida y sigue como reintento.</p>
 */
public final class PlanningClaim {

    /** Margen sobre el tiempo de espera del modelo para cerrar y guardar. */
    public static final Duration MARGIN = Duration.ofSeconds(15);

    private final Duration maxAge;

    /**
     * Crea la regla.
     *
     * @param llmTimeout tiempo máximo de la llamada al modelo
     */
    public PlanningClaim(Duration llmTimeout) {
        this.maxAge = llmTimeout.plus(MARGIN);
    }

    /**
     * Indica si la marca sigue vigente y por tanto impide otro inicio.
     *
     * @param mark ejecución en curso de la sesión
     * @param now instante actual
     * @return {@code true} si no caducó
     */
    public boolean isLive(AiExecution mark, Instant now) {
        return mark.isLive(now, maxAge);
    }

    /**
     * Plazo de caducidad.
     *
     * @return tiempo de espera del modelo más el margen
     */
    public Duration maxAge() {
        return maxAge;
    }
}
