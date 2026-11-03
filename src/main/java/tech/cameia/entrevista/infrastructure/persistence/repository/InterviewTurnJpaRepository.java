package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.cameia.entrevista.infrastructure.persistence.entity.InterviewTurnEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code turno}. Es una pieza interna: el resto del
 * servicio usa el puerto {@code InterviewTurnRepository}.
 */
interface InterviewTurnJpaRepository extends JpaRepository<InterviewTurnEntity, UUID> {
}
