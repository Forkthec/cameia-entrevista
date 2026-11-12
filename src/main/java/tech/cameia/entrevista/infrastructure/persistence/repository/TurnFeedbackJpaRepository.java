package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.cameia.entrevista.infrastructure.persistence.entity.TurnFeedbackEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code feedback_turno}. Es una pieza interna: el resto del servicio usa
 * el puerto {@code TurnFeedbackRepository}.
 */
interface TurnFeedbackJpaRepository extends JpaRepository<TurnFeedbackEntity, UUID> {
}
