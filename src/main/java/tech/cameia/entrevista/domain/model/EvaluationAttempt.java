package tech.cameia.entrevista.domain.model;

import java.util.UUID;

/**
 * Intento de evaluación de un turno dentro de un ciclo de reintentos.
 *
 * @param sessionId sesión del turno
 * @param turnId turno que se evalúa
 * @param number número de intento, de 1 a 3
 */
public record EvaluationAttempt(UUID sessionId, UUID turnId, int number) {
}
