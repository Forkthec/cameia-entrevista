package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.infrastructure.persistence.entity.InterviewTurnEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code turno}. Es una pieza interna: el resto del
 * servicio usa el puerto {@code InterviewTurnRepository}.
 */
interface InterviewTurnJpaRepository extends JpaRepository<InterviewTurnEntity, UUID> {

    /**
     * Busca el turno abierto de una sesión: el de la primera pregunta cuyo turno no está completado.
     *
     * @param sessionId identificador de la sesión
     * @return el turno, o vacío si no hay
     */
    @Query(value = """
            SELECT t.* FROM microentrevista.turno t
              JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
             WHERE p.sesion_id = :sessionId AND t.estado <> 'COMPLETED'
             ORDER BY p.orden LIMIT 1
            """, nativeQuery = true)
    Optional<InterviewTurnEntity> findOpenBySession(@Param("sessionId") UUID sessionId);

    /**
     * Busca un turno por su identificador solo si su pregunta es de la sesión.
     *
     * @param turnId identificador del turno
     * @param sessionId identificador de la sesión
     * @return el turno, o vacío si no existe o es de otra sesión
     */
    @Query(value = """
            SELECT t.* FROM microentrevista.turno t
              JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
             WHERE t.id = :turnId AND p.sesion_id = :sessionId
            """, nativeQuery = true)
    Optional<InterviewTurnEntity> findByIdAndSession(@Param("turnId") UUID turnId,
            @Param("sessionId") UUID sessionId);

    /**
     * Cuenta los turnos completados de una sesión.
     *
     * @param sessionId identificador de la sesión
     * @return cantidad de turnos completados
     */
    @Query(value = """
            SELECT count(*) FROM microentrevista.turno t
              JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
             WHERE p.sesion_id = :sessionId AND t.estado = 'COMPLETED'
            """, nativeQuery = true)
    int countCompleted(@Param("sessionId") UUID sessionId);
}
