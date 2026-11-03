package tech.cameia.entrevista.application.command;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;

/**
 * Entrada del caso de uso que aplica una instantánea del catálogo de roles a la copia local.
 *
 * @param messageId  identificador del mensaje del broker
 * @param roles      roles de la instantánea, de 1 a {@value #MAX_ROLES}; se guarda una copia inmutable
 * @param snapshotAt {@code actualizadoEn} de la instantánea
 */
public record ReplicateRoleCatalogCommand(UUID messageId, List<ProfessionalRoleReplica> roles,
        Instant snapshotAt) {

    /** Máximo de roles por instantánea, igual al del contrato del evento. */
    public static final int MAX_ROLES = 200;

    /**
     * @throws NullPointerException     si falta algún componente
     * @throws IllegalArgumentException si la lista tiene menos de 1 o más de {@value #MAX_ROLES} roles
     */
    public ReplicateRoleCatalogCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(snapshotAt, "snapshotAt");
        if (roles.isEmpty() || roles.size() > MAX_ROLES) {
            throw new IllegalArgumentException("roles debe tener de 1 a " + MAX_ROLES + " elementos");
        }
        roles = List.copyOf(roles);
    }
}
