package tech.cameia.entrevista.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code evento_procesado}: un mensaje del broker ya aplicado (Inbox).
 *
 * <p>Existe para que Hibernate valide el esquema y para leer el registro; la escritura es una
 * sola sentencia nativa que no falla si el mensaje ya estaba.</p>
 */
@Entity
@Table(name = "evento_procesado")
public class ProcessedMessageEntity {

    /** Identificador del mensaje del broker. */
    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    /** Tipo del evento. */
    @Column(name = "tipo", nullable = false, length = 40)
    private String type;

    /** Instante en que se procesó. */
    @Column(name = "procesado_en", nullable = false)
    private Instant processedAt;

    /** Constructor que exige JPA. */
    protected ProcessedMessageEntity() {
    }

    /** @return identificador del mensaje */
    public UUID getMessageId() {
        return messageId;
    }

    /** @return tipo del evento */
    public String getType() {
        return type;
    }

    /** @return instante en que se procesó */
    public Instant getProcessedAt() {
        return processedAt;
    }
}
