package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.ProfileReplicaEntity;

/**
 * Adaptador del puerto de la copia del perfil sobre JPA. La lectura traduce la fila al modelo de
 * dominio; la escritura es la sentencia condicionada del repositorio.
 */
@Repository
class ProfileReplicaRepositoryAdapter implements ProfileReplicaRepository {

    private final ProfileReplicaJpaRepository jpa;

    ProfileReplicaRepositoryAdapter(ProfileReplicaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileReplica> findById(UUID profileId) {
        return jpa.findById(profileId).map(ProfileReplicaRepositoryAdapter::toDomain);
    }

    /**
     * Solo se llama dentro de la transacción del caso de uso: el Inbox y la copia se confirman juntos.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean upsertIfNotOlder(ProfileReplica replica, Instant replicatedAt) {
        int changed = jpa.upsertIfNotOlder(replica.profileId(), replica.owner().value(), replica.name(),
                replica.status().name(), replica.archivedAt(), replica.professionalContextJson(),
                replica.aspiredRolesJson(), replica.updatedAt(), replicatedAt);
        return changed > 0;
    }

    private static ProfileReplica toDomain(ProfileReplicaEntity entity) {
        return new ProfileReplica(entity.getProfileId(), new FirebaseUid(entity.getFirebaseUid()),
                entity.getName(), ReplicatedProfileStatus.valueOf(entity.getStatus()), entity.getArchivedAt(),
                entity.getProfessionalContext(), entity.getAspiredRoles(), entity.getUpdatedAt());
    }
}
