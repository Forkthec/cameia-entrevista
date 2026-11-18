package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.infrastructure.persistence.mapper.InterviewTurnMapper;

/**
 * Adaptador del puerto de turnos sobre JPA. Traduce la versión vencida a un conflicto de sesión.
 */
@Repository
class InterviewTurnRepositoryAdapter implements InterviewTurnRepository {

    private final InterviewTurnJpaRepository jpa;

    InterviewTurnRepositoryAdapter(InterviewTurnJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public InterviewTurn save(InterviewTurn turn) {
        try {
            return InterviewTurnMapper.toDomain(jpa.saveAndFlush(InterviewTurnMapper.toEntity(turn)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new SessionConflictException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewTurn> findById(UUID id) {
        return jpa.findById(id).map(InterviewTurnMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewTurn> findOpenBySession(UUID sessionId) {
        return jpa.findOpenBySession(sessionId).map(InterviewTurnMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewTurn> findByIdAndSession(UUID turnId, UUID sessionId) {
        return jpa.findByIdAndSession(turnId, sessionId).map(InterviewTurnMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public int countCompleted(UUID sessionId) {
        return jpa.countCompleted(sessionId);
    }
}
