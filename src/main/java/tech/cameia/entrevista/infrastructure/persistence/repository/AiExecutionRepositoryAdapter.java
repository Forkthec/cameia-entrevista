package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiExecutionStatus;
import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.AiExecutionEntity;

/**
 * Adaptador del puerto de ejecuciones de la IA sobre JPA. Traduce el índice único de la marca de planificación a
 * un conflicto de sesión: dos pedidos de inicio simultáneos no pueden abrir dos marcas.
 */
@Repository
class AiExecutionRepositoryAdapter implements AiExecutionRepository {

    /** Índice único parcial: una sola planificación en curso por sesión. */
    static final String UNIQUE_PLANNING_IN_PROGRESS = "uq_ejecucion_ia_planificacion_en_curso";

    /** Índice único parcial: una sola evaluación en curso por turno. */
    static final String UNIQUE_EVALUATION_IN_PROGRESS = "uq_ejecucion_ia_evaluacion_en_curso";

    private static final String PLAN = "PLANIFICAR";
    private static final String EVALUATE = "EVALUAR";

    private final AiExecutionJpaRepository jpa;

    AiExecutionRepositoryAdapter(AiExecutionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public AiExecution save(AiExecution execution) {
        try {
            return toDomain(jpa.saveAndFlush(toEntity(execution)));
        } catch (DataIntegrityViolationException e) {
            if (ConstraintNames.violates(e, UNIQUE_PLANNING_IN_PROGRESS)) {
                throw new SessionConflictException();
            }
            if (ConstraintNames.violates(e, UNIQUE_EVALUATION_IN_PROGRESS)) {
                throw new TurnConflictException();
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiExecution> findPlanningInProgress(UUID sessionId) {
        return jpa.findFirstBySessionIdAndOperationAndStatus(sessionId, PLAN, AiExecutionStatus.IN_PROGRESS.code())
                .map(AiExecutionRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiExecution> findEvaluationInProgress(UUID turnId) {
        return jpa.findFirstByTurnIdAndOperationAndStatus(turnId, EVALUATE, AiExecutionStatus.IN_PROGRESS.code())
                .map(AiExecutionRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFailedPlanning(UUID sessionId) {
        return jpa.existsBySessionIdAndOperationAndStatus(sessionId, PLAN, AiExecutionStatus.FAILED.code());
    }

    @Override
    @Transactional
    public void deletePlanningOf(UUID sessionId) {
        jpa.deleteBySessionAndOperation(sessionId, PLAN);
    }

    @Override
    @Transactional
    public boolean failIfInProgress(AiExecution failed) {
        return jpa.failIfInProgress(failed.id(), AiExecutionStatus.IN_PROGRESS.code(),
                AiExecutionStatus.FAILED.code(), failed.errorCode(), failed.finishedAt()) > 0;
    }

    private static AiExecutionEntity toEntity(AiExecution e) {
        return new AiExecutionEntity(e.id(), e.sessionId(), e.operation().code(), e.turnId(), (short) e.attempt(),
                e.idempotencyKey(),
                e.status().code(), e.model(), e.promptVersion(), e.errorCode(), e.provider(), e.inputTokens(),
                e.outputTokens(), e.startedAt(), e.finishedAt());
    }

    private static AiExecution toDomain(AiExecutionEntity e) {
        return new AiExecution(e.getId(), e.getSessionId(), AiOperation.fromCode(e.getOperation()), e.getTurnId(), e.getAttempt(), e.getIdempotencyKey(),
                AiExecutionStatus.fromCode(e.getStatus()), e.getModel(), e.getPromptVersion(), e.getErrorCode(),
                e.getProvider(), e.getInputTokens(), e.getOutputTokens(), e.getStartedAt(), e.getFinishedAt());
    }
}
