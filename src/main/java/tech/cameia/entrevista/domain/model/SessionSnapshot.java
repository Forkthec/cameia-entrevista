package tech.cameia.entrevista.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Todos los datos de una sesión tal como los guarda la persistencia, para reconstruirla.
 *
 * @param id identificador de la sesión
 * @param owner dueño de la sesión
 * @param configuration configuración con la que se preparó
 * @param status estado actual
 * @param planningRetries reintentos de planificación gastados
 * @param createdAt fecha de creación
 * @param updatedAt fecha de la última modificación
 * @param lastActivityAt fecha de la última actividad, o {@code null} si no empezó
 * @param finishedAt fecha de cierre, o {@code null} si no terminó
 * @param version versión de control de edición simultánea; {@code null} si la sesión es nueva
 */
public record SessionSnapshot(
        UUID id,
        FirebaseUid owner,
        SessionConfiguration configuration,
        SessionStatus status,
        int planningRetries,
        Instant createdAt,
        Instant updatedAt,
        Instant lastActivityAt,
        Instant finishedAt,
        Long version) {
}
