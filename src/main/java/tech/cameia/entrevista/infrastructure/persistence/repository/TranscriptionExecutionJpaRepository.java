package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.cameia.entrevista.infrastructure.persistence.entity.TranscriptionExecutionEntity;

/**
 * Repositorio de Spring Data sobre las filas {@code TRANSCRIBIR} de {@code ejecucion_ia}. Es una pieza interna: el
 * resto del servicio usa el puerto {@code TranscriptionExecutionRepository}.
 */
interface TranscriptionExecutionJpaRepository extends JpaRepository<TranscriptionExecutionEntity, UUID> {

    /**
     * Lista las ejecuciones de una operación para un turno.
     *
     * @param turnId turno
     * @param operation operación guardada
     * @return las ejecuciones, en cualquier orden
     */
    List<TranscriptionExecutionEntity> findByTurnIdAndOperation(UUID turnId, String operation);
}
