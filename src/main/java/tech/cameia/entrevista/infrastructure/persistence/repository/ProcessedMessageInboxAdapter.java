package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.port.ProcessedMessageInbox;

/**
 * Adaptador del Inbox sobre JPA: una inserción que no falla si el mensaje ya estaba.
 */
@Repository
class ProcessedMessageInboxAdapter implements ProcessedMessageInbox {

    private final ProcessedMessageJpaRepository jpa;

    ProcessedMessageInboxAdapter(ProcessedMessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Solo se llama dentro de la transacción del caso de uso: si luego algo falla, el mensaje
     * vuelve a quedar sin anotar y el reintento lo aplica completo.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt) {
        return jpa.insertIfAbsent(messageId, eventType, processedAt) > 0;
    }
}
