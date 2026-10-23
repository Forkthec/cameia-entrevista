package tech.cameia.entrevista.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.ProfileReplica;

/**
 * Puerto de la copia local de los perfiles profesionales.
 */
public interface ProfileReplicaRepository {

    /**
     * Busca la copia de un perfil.
     *
     * @param profileId identificador del perfil en Perfil
     * @return la copia, o vacío si nunca llegó un evento de ese perfil
     */
    Optional<ProfileReplica> findById(UUID profileId);

    /**
     * Inserta la copia, o la reemplaza si la guardada no es más nueva y tiene el mismo dueño. Debe
     * llamarse dentro de una transacción.
     *
     * @param replica      copia que trae el mensaje
     * @param replicatedAt instante en que el servicio la aplica
     * @return {@code true} si la copia cambió; {@code false} si la guardada era más nueva o de otro dueño
     */
    boolean upsertIfNotOlder(ProfileReplica replica, Instant replicatedAt);
}
