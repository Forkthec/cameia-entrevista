package tech.cameia.entrevista.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Todos los datos de un turno tal como los guarda la persistencia, para reconstruirlo.
 *
 * @param id identificador del turno
 * @param questionId pregunta a la que pertenece
 * @param status estado actual
 * @param createdAt fecha de creación
 * @param updatedAt fecha de la última modificación
 * @param version versión de control de edición simultánea; {@code null} si el turno es nuevo
 */
public record TurnSnapshot(
        UUID id,
        UUID questionId,
        TurnStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
