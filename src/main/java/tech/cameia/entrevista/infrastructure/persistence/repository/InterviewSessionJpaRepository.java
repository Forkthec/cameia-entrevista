package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.infrastructure.persistence.entity.InterviewSessionEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code sesion}. Es una pieza interna: el resto del
 * servicio usa el puerto {@code InterviewSessionRepository}.
 */
interface InterviewSessionJpaRepository extends JpaRepository<InterviewSessionEntity, UUID> {

    /**
     * Borra la sesión solo si tiene el estado indicado, en una sola sentencia, para que un cambio
     * de estado concurrente no se pierda entre la lectura y el borrado.
     *
     * @param id identificador de la sesión
     * @param status estado que debe tener
     * @return filas borradas: 1 si se borró, 0 si no existe o cambió de estado
     */
    @Modifying
    @Query("delete from InterviewSessionEntity s where s.id = :id and s.status = :status")
    int deleteByIdAndStatus(@Param("id") UUID id, @Param("status") SessionStatus status);
}
