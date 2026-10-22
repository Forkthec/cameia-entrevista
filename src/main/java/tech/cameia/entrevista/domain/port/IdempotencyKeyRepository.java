package tech.cameia.entrevista.domain.port;

import java.util.Optional;

import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;

/**
 * Puerto de persistencia de las claves que evitan atender dos veces la misma petición.
 */
public interface IdempotencyKeyRepository {

    /**
     * Busca el registro de una clave de una persona para una operación.
     *
     * @param owner persona que envió la clave
     * @param operation operación protegida
     * @param key clave enviada
     * @return el registro, o vacío si la clave no se ha usado
     */
    Optional<IdempotencyKey> find(FirebaseUid owner, String operation, String key);

    /**
     * Registra la clave junto con la respuesta original.
     *
     * @param key registro a guardar
     * @return el registro guardado
     * @throws IdempotencyKeyAlreadyExistsException si la persona ya usó esa clave en esa operación
     */
    IdempotencyKey save(IdempotencyKey key);
}
