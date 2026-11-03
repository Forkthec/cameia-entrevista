package tech.cameia.entrevista.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto del registro de mensajes ya aplicados (Inbox): el broker entrega "al menos una vez" y
 * este registro hace que una reentrega no tenga efecto.
 */
public interface ProcessedMessageInbox {

    /**
     * Anota el mensaje como procesado si no estaba. Debe llamarse dentro de la misma transacción
     * que aplica el mensaje, para que ambas cosas se confirmen o se deshagan juntas.
     *
     * @param messageId   identificador del mensaje del broker
     * @param eventType   tipo del evento
     * @param processedAt instante en que se procesa
     * @return {@code true} si no estaba y ahora sí; {@code false} si ya se había aplicado
     */
    boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt);
}
