package tech.cameia.entrevista.application.command;

import java.util.Objects;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.ProfileReplica;

/**
 * Entrada del caso de uso que aplica un mensaje del perfil a la copia local.
 *
 * @param messageId identificador del mensaje del broker
 * @param replica   copia que trae el mensaje, ya validada
 */
public record ReplicateProfileCommand(UUID messageId, ProfileReplica replica) {

    /**
     * @throws NullPointerException si falta alguno de los dos
     */
    public ReplicateProfileCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(replica, "replica");
    }
}
