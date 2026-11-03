package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.cameia.entrevista.infrastructure.persistence.entity.IdempotencyKeyEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code clave_idempotencia}. Es una pieza interna: el
 * resto del servicio usa el puerto {@code IdempotencyKeyRepository}.
 */
interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    /**
     * Busca el registro de una clave de una persona para una operación.
     *
     * @param firebaseUid persona que envió la clave
     * @param operation operación protegida
     * @param key clave enviada
     * @return el registro, o vacío si no existe
     */
    Optional<IdempotencyKeyEntity> findByFirebaseUidAndOperationAndKey(String firebaseUid, String operation,
            String key);
}
