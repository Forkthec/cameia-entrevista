package tech.cameia.entrevista.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import tech.cameia.entrevista.domain.model.AiExecution;

/**
 * Regla de caducidad de una evaluación en curso: si el proceso murió durante la llamada, la ejecución queda abierta y
 * el turno procesando. Pasado el tiempo de espera del modelo más un margen, la evaluación se considera caída.
 */
public final class EvaluationStaleness {

    private final Duration maxAge;

    /**
     * Crea la regla.
     *
     * @param llmTimeout tiempo máximo de la llamada al modelo
     */
    public EvaluationStaleness(Duration llmTimeout) {
        this.maxAge = llmTimeout.plus(PlanningClaim.MARGIN);
    }

    /**
     * Indica si la evaluación del turno ya no está viva.
     *
     * @param inProgress ejecución de evaluación en curso del turno, si hay
     * @param now instante actual
     * @return {@code true} si no hay ejecución en curso o ya pasó el plazo
     */
    public boolean isStale(Optional<AiExecution> inProgress, Instant now) {
        return inProgress.isEmpty() || !inProgress.get().isLive(now, maxAge);
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
