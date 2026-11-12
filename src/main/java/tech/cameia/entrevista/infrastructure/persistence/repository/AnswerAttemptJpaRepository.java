package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.cameia.entrevista.infrastructure.persistence.entity.AnswerAttemptEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code intento_respuesta}. Es una pieza interna: el resto del servicio
 * usa el puerto {@code AnswerAttemptRepository}.
 */
interface AnswerAttemptJpaRepository extends JpaRepository<AnswerAttemptEntity, UUID> {
}
