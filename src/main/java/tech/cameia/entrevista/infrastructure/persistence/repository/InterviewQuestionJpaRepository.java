package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.cameia.entrevista.infrastructure.persistence.entity.InterviewQuestionEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code pregunta}. Es una pieza interna: el resto del servicio usa el
 * puerto {@code InterviewQuestionRepository}.
 */
interface InterviewQuestionJpaRepository extends JpaRepository<InterviewQuestionEntity, UUID> {

    /**
     * Lista las preguntas de una sesión.
     *
     * @param sessionId identificador de la sesión
     * @return las preguntas por posición ascendente
     */
    List<InterviewQuestionEntity> findBySessionIdOrderByPositionAsc(UUID sessionId);
}
