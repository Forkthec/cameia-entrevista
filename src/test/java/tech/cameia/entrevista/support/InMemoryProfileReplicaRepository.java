package tech.cameia.entrevista.support;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;

/**
 * Copia del perfil en memoria para probar el caso de uso sin base: respeta la misma regla de la
 * sentencia real (no reemplaza lo más nuevo ni cambia el dueño).
 */
public class InMemoryProfileReplicaRepository implements ProfileReplicaRepository {

    private final Map<UUID, ProfileReplica> stored = new HashMap<>();

    @Override
    public Optional<ProfileReplica> findById(UUID profileId) {
        return Optional.ofNullable(stored.get(profileId));
    }

    @Override
    public boolean upsertIfNotOlder(ProfileReplica replica, Instant replicatedAt) {
        ProfileReplica current = stored.get(replica.profileId());
        if (current != null && (current.updatedAt().isAfter(replica.updatedAt())
                || !current.owner().equals(replica.owner()))) {
            return false;
        }
        stored.put(replica.profileId(), replica);
        return true;
    }
}
