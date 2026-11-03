package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.application.command.ReplicateProfileCommand;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicationOutcome;
import tech.cameia.entrevista.domain.port.ProcessedMessageInbox;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;

/**
 * Aplica un mensaje del perfil a la copia local, una sola vez y sin retroceder en el tiempo.
 *
 * <p>Orden dentro de una transacción: (1) si la copia existe con otro dueño, devuelve
 * {@link ReplicationOutcome#OWNER_MISMATCH} sin escribir nada, para que el mensaje se pueda
 * reprocesar si fue un error; (2) anota el mensaje en el Inbox y, si ya estaba, no hay efecto;
 * (3) inserta o reemplaza la copia si no es más vieja que la guardada. El {@code ack} del broker
 * ocurre después del commit.</p>
 *
 * <p>Un perfil tiene un solo dueño en Perfil, así que el cambio de dueño se detecta al leer; la
 * condición de dueño del reemplazo en la base es la segunda barrera.</p>
 */
@Service
public class ReplicateProfileService {

    /** Tipo de evento que se anota en el Inbox. */
    static final String EVENT_TYPE = "perfil.profesional-actualizado";

    private static final Logger log = LoggerFactory.getLogger(ReplicateProfileService.class);

    private final ProfileReplicaRepository replicas;
    private final ProcessedMessageInbox inbox;
    private final Clock clock;

    ReplicateProfileService(ProfileReplicaRepository replicas, ProcessedMessageInbox inbox, Clock clock) {
        this.replicas = replicas;
        this.inbox = inbox;
        this.clock = clock;
    }

    /**
     * Aplica el mensaje.
     *
     * @param command mensaje validado
     * @return qué pasó con el mensaje
     */
    @Transactional
    public ReplicationOutcome apply(ReplicateProfileCommand command) {
        ProfileReplica replica = command.replica();
        if (hasOtherOwner(replica)) {
            return ReplicationOutcome.OWNER_MISMATCH;
        }
        Instant now = clock.instant();
        if (!inbox.registerIfAbsent(command.messageId(), EVENT_TYPE, now)) {
            log.info("Mensaje del perfil ya aplicado; se ignora [messageId={}]", command.messageId());
            return ReplicationOutcome.DUPLICATE_IGNORED;
        }
        if (!replicas.upsertIfNotOlder(replica, now)) {
            log.info("Mensaje del perfil más viejo que la copia; se ignora [perfilId={}, messageId={}]",
                    replica.profileId(), command.messageId());
            return ReplicationOutcome.STALE_IGNORED;
        }
        log.info("Copia del perfil actualizada [perfilId={}, firebaseUid={}, messageId={}]",
                replica.profileId(), replica.owner().value(), command.messageId());
        return ReplicationOutcome.APPLIED;
    }

    private boolean hasOtherOwner(ProfileReplica incoming) {
        return replicas.findById(incoming.profileId())
                .map(stored -> !stored.owner().equals(incoming.owner()))
                .orElse(false);
    }
}
