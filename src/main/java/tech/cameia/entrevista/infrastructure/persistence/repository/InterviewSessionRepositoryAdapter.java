package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.infrastructure.persistence.mapper.InterviewSessionMapper;

/**
 * Adaptador del puerto de sesiones sobre JPA.
 *
 * <p>Traduce las dos fallas de concurrencia a sus errores de negocio: la versión vencida (otra
 * escritura ganó) y los índices únicos que impiden dos sesiones configuradas o dos en curso de
 * la misma persona.</p>
 */
@Repository
class InterviewSessionRepositoryAdapter implements InterviewSessionRepository {

    /** Índice único: una sola sesión configurada por persona. */
    static final String UNIQUE_CONFIGURED = "uq_sesion_configurada_por_usuario";

    /** Índice único: una sola sesión en curso por persona. */
    static final String UNIQUE_IN_PROGRESS = "uq_sesion_en_curso_por_usuario";

    private final InterviewSessionJpaRepository jpa;

    InterviewSessionRepositoryAdapter(InterviewSessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public InterviewSession save(InterviewSession session) {
        try {
            // Se escribe ya para que la violación de un índice se traduzca aquí y no al confirmar.
            return InterviewSessionMapper.toDomain(jpa.saveAndFlush(InterviewSessionMapper.toEntity(session)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new SessionConflictException();
        } catch (DataIntegrityViolationException e) {
            if (ConstraintNames.violates(e, UNIQUE_CONFIGURED) || ConstraintNames.violates(e, UNIQUE_IN_PROGRESS)) {
                throw new SessionAlreadyExistsException();
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewSession> findByIdAndOwner(UUID id, FirebaseUid owner) {
        return jpa.findByIdAndFirebaseUid(id, owner.value()).map(InterviewSessionMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteConfigured(UUID id, FirebaseUid owner) {
        if (jpa.deleteByIdAndOwnerAndStatus(id, owner.value(), SessionStatus.CONFIGURED) == 0) {
            throw new SessionTransitionNotAllowedException();
        }
    }
}
