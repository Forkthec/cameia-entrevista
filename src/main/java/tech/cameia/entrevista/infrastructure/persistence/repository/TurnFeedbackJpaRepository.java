package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.infrastructure.persistence.entity.TurnFeedbackEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code feedback_turno}. Es una pieza interna: el resto del servicio usa
 * el puerto {@code TurnFeedbackRepository}.
 */
interface TurnFeedbackJpaRepository extends JpaRepository<TurnFeedbackEntity, UUID> {

    /** Cuenta las retroalimentaciones de los turnos de las preguntas de una sesión. */
    @Query(value = """
            SELECT count(*) FROM microentrevista.feedback_turno f
              JOIN microentrevista.turno t ON t.id = f.turno_id
              JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
             WHERE p.sesion_id = :sessionId
            """, nativeQuery = true)
    int countBySession(@Param("sessionId") UUID sessionId);
}
