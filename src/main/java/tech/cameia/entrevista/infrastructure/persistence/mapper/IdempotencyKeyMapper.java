package tech.cameia.entrevista.infrastructure.persistence.mapper;

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.infrastructure.persistence.entity.IdempotencyKeyEntity;

/**
 * Traduce entre el registro de idempotencia del dominio y su fila. Es una clase de métodos
 * estáticos porque no guarda estado.
 */
public final class IdempotencyKeyMapper {

    private IdempotencyKeyMapper() {
    }

    /**
     * Copia el registro del dominio a una fila.
     *
     * @param key registro del dominio
     * @return la fila equivalente
     */
    public static IdempotencyKeyEntity toEntity(IdempotencyKey key) {
        IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
        entity.setId(key.id());
        entity.setFirebaseUid(key.owner().value());
        entity.setOperation(key.operation());
        entity.setKey(key.key());
        entity.setRequestFingerprint(key.requestFingerprint());
        entity.setResponseStatus(key.responseStatus());
        entity.setResponseBody(key.responseBodyJson());
        entity.setCreatedAt(key.createdAt());
        return entity;
    }

    /**
     * Reconstruye el registro del dominio desde su fila.
     *
     * @param entity fila leída de la base
     * @return el registro del dominio
     */
    public static IdempotencyKey toDomain(IdempotencyKeyEntity entity) {
        return new IdempotencyKey(entity.getId(), new FirebaseUid(entity.getFirebaseUid()), entity.getOperation(),
                entity.getKey(), entity.getRequestFingerprint(), entity.getResponseStatus(),
                entity.getResponseBody(), entity.getCreatedAt());
    }
}
