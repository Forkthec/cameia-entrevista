package tech.cameia.entrevista.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Retroalimentación guardada de un turno (fila de {@code feedback_turno}). Se guarda y no se muestra a la persona.
 *
 * @param id identificador
 * @param turnId turno evaluado
 * @param executionId ejecución de la IA que la produjo
 * @param result evaluación del modelo
 * @param createdAt fecha de registro
 */
public record TurnFeedback(UUID id, UUID turnId, UUID executionId, EvaluationResult result, Instant createdAt) {

    /** Exige los datos obligatorios. */
    public TurnFeedback {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Crea la retroalimentación de un turno recién evaluado.
     *
     * @param turnId turno evaluado
     * @param executionId ejecución que la produjo
     * @param result evaluación validada
     * @param clock reloj que da la fecha
     * @return la retroalimentación nueva
     */
    public static TurnFeedback of(UUID turnId, UUID executionId, EvaluationResult result, Clock clock) {
        return new TurnFeedback(UUID.randomUUID(), turnId, executionId, result, clock.instant());
    }
}
