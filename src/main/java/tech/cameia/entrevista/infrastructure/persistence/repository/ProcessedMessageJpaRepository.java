package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.infrastructure.persistence.entity.ProcessedMessageEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code evento_procesado}. Es una pieza interna: el
 * resto del servicio usa el puerto {@code ProcessedMessageInbox}.
 */
interface ProcessedMessageJpaRepository extends JpaRepository<ProcessedMessageEntity, UUID> {

    /**
     * Anota el mensaje sin fallar si ya estaba: la clave primaria decide quién gana cuando dos
     * instancias reciben el mismo mensaje a la vez.
     *
     * @return 1 si se insertó, 0 si el mensaje ya estaba
     */
    @Modifying
    @Query(value = """
            INSERT INTO microentrevista.evento_procesado (message_id, tipo, procesado_en)
            VALUES (:messageId, :type, :processedAt)
            ON CONFLICT (message_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("messageId") UUID messageId, @Param("type") String type,
            @Param("processedAt") Instant processedAt);
}
