package tech.cameia.entrevista.support;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import tech.cameia.entrevista.domain.port.ProcessedMessageInbox;

/**
 * Inbox en memoria para probar los casos de uso sin base.
 */
public class InMemoryProcessedMessageInbox implements ProcessedMessageInbox {

    private final Set<UUID> registered = new HashSet<>();

    @Override
    public boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt) {
        return registered.add(messageId);
    }

    /**
     * @param messageId identificador del mensaje
     * @return {@code true} si el mensaje quedó anotado
     */
    public boolean contains(UUID messageId) {
        return registered.contains(messageId);
    }
}
