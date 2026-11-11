package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.infrastructure.persistence.entity.AiExecutionEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code ejecucion_ia}. Es una pieza interna: el resto del servicio usa
 * el puerto {@code AiExecutionRepository}.
 */
interface AiExecutionJpaRepository extends JpaRepository<AiExecutionEntity, UUID> {

    /**
     * Busca la ejecución de un tipo de operación y estado de una sesión.
     *
     * @param sessionId sesión
     * @param operation operación guardada
     * @param status estado guardado
     * @return la ejecución, o vacío si no hay
     */
    Optional<AiExecutionEntity> findFirstBySessionIdAndOperationAndStatus(UUID sessionId, String operation,
            String status);

    /**
     * Indica si existe una ejecución de la sesión con la operación y el estado dados.
     *
     * @param sessionId sesión
     * @param operation operación guardada
     * @param status estado guardado
     * @return {@code true} si existe
     */
    boolean existsBySessionIdAndOperationAndStatus(UUID sessionId, String operation, String status);

    /**
     * Borra las ejecuciones de una sesión para una operación.
     *
     * @param sessionId sesión
     * @param operation operación guardada
     * @return filas borradas
     */
    @Modifying
    @Query("delete from AiExecutionEntity e where e.sessionId = :sessionId and e.operation = :operation")
    int deleteBySessionAndOperation(@Param("sessionId") UUID sessionId, @Param("operation") String operation);

    /**
     * Cierra como fallida la ejecución con ese identificador solo si sigue en el estado dado.
     *
     * @param id identificador de la ejecución
     * @param inProgress estado guardado de «en curso»
     * @param failed estado guardado de «fallida»
     * @param errorCode código de la causa
     * @param finishedAt instante de cierre
     * @return filas cambiadas (0 si la fila ya no existe o ya no estaba en curso)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiExecutionEntity e set e.status = :failed, e.errorCode = :errorCode, e.finishedAt = :finishedAt"
            + " where e.id = :id and e.status = :inProgress")
    int failIfInProgress(@Param("id") UUID id, @Param("inProgress") String inProgress,
            @Param("failed") String failed, @Param("errorCode") String errorCode,
            @Param("finishedAt") java.time.Instant finishedAt);
}
