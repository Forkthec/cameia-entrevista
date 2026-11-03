package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.infrastructure.persistence.mapper.IdempotencyKeyMapper;

/**
 * Adaptador del puerto de claves de idempotencia sobre JPA. Traduce la clave repetida a su error
 * de negocio.
 */
@Repository
class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {

    /** Restricción única: una clave por persona y operación. */
    static final String UNIQUE_KEY = "uq_clave_idempotencia_usuario_operacion_clave";

    private final IdempotencyKeyJpaRepository jpa;

    IdempotencyKeyRepositoryAdapter(IdempotencyKeyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> find(FirebaseUid owner, String operation, String key) {
        return jpa.findByFirebaseUidAndOperationAndKey(owner.value(), operation, key)
                .map(IdempotencyKeyMapper::toDomain);
    }

    @Override
    @Transactional
    public IdempotencyKey save(IdempotencyKey key) {
        try {
            return IdempotencyKeyMapper.toDomain(jpa.saveAndFlush(IdempotencyKeyMapper.toEntity(key)));
        } catch (DataIntegrityViolationException e) {
            if (ConstraintNames.violates(e, UNIQUE_KEY)) {
                throw new IdempotencyKeyAlreadyExistsException();
            }
            throw e;
        }
    }
}
