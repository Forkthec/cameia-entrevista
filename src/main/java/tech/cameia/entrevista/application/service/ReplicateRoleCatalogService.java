package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.application.command.ReplicateRoleCatalogCommand;
import tech.cameia.entrevista.domain.model.ReplicationOutcome;
import tech.cameia.entrevista.domain.port.ProcessedMessageInbox;
import tech.cameia.entrevista.domain.port.RoleCatalogReplicaRepository;

/**
 * Aplica una instantánea del catálogo de roles a la copia local, una sola vez y sin retroceder en el
 * tiempo.
 *
 * <p>Orden dentro de una transacción: (1) anota el mensaje en el Inbox y, si ya estaba, no hay
 * efecto; (2) inserta o actualiza cada rol cuya fila guardada no sea más nueva que la instantánea.
 * Los roles que la instantánea no trae se conservan: Perfil desactiva los roles, no los borra. El
 * {@code ack} del broker ocurre después del commit. Ningún registro lleva los nombres de los roles.</p>
 */
@Service
public class ReplicateRoleCatalogService {

    /** Tipo de evento que se anota en el Inbox. */
    static final String EVENT_TYPE = "perfil.catalogo-roles-actualizado";

    private static final Logger log = LoggerFactory.getLogger(ReplicateRoleCatalogService.class);

    private final RoleCatalogReplicaRepository roles;
    private final ProcessedMessageInbox inbox;
    private final Clock clock;

    ReplicateRoleCatalogService(RoleCatalogReplicaRepository roles, ProcessedMessageInbox inbox, Clock clock) {
        this.roles = roles;
        this.inbox = inbox;
        this.clock = clock;
    }

    /**
     * Aplica la instantánea.
     *
     * @param command mensaje validado
     * @return {@code APPLIED} si cambió al menos una fila, {@code STALE_IGNORED} si ninguna y
     *         {@code DUPLICATE_IGNORED} si el mensaje ya se había aplicado
     */
    @Transactional
    public ReplicationOutcome apply(ReplicateRoleCatalogCommand command) {
        Instant now = clock.instant();
        if (!inbox.registerIfAbsent(command.messageId(), EVENT_TYPE, now)) {
            log.info("Mensaje del catálogo de roles ya aplicado; se ignora [messageId={}]", command.messageId());
            return ReplicationOutcome.DUPLICATE_IGNORED;
        }
        int changed = roles.upsertSnapshot(command.roles(), command.snapshotAt(), now);
        if (changed == 0) {
            log.info("Instantánea del catálogo de roles más vieja que la copia; se ignora [messageId={}, recibidos={}]",
                    command.messageId(), command.roles().size());
            return ReplicationOutcome.STALE_IGNORED;
        }
        log.info("Catálogo de roles actualizado [messageId={}, recibidos={}, aplicados={}]",
                command.messageId(), command.roles().size(), changed);
        return ReplicationOutcome.APPLIED;
    }
}
