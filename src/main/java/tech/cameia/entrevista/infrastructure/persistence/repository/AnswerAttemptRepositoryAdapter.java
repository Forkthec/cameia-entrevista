package tech.cameia.entrevista.infrastructure.persistence.repository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.AnswerAttemptEntity;

/**
 * Adaptador del puerto de respuestas sobre JPA. Traduce el índice único de la respuesta definitiva a un rechazo
 * del turno: dos envíos simultáneos a la misma pregunta no pueden dejar dos respuestas definitivas.
 */
@Repository
class AnswerAttemptRepositoryAdapter implements AnswerAttemptRepository {

    /** Índice único parcial: una sola respuesta definitiva por turno. */
    static final String UNIQUE_DEFINITIVE = "uq_intento_definitivo_por_turno";

    /** Valor de {@code tipo_interaccion} de la respuesta a una pregunta principal. */
    static final String INITIAL = "INICIAL";

    private final AnswerAttemptJpaRepository jpa;

    AnswerAttemptRepositoryAdapter(AnswerAttemptJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void saveDefinitive(AnswerAttempt a) {
        try {
            jpa.saveAndFlush(new AnswerAttemptEntity(a.id(), a.turnId(), INITIAL, true, a.origin().code(),
                    a.text(), a.questionShown(), a.detectedLanguage(), a.createdAt()));
        } catch (DataIntegrityViolationException e) {
            if (ConstraintNames.violates(e, UNIQUE_DEFINITIVE)) {
                throw new TurnTransitionNotAllowedException();
            }
            throw e;
        }
    }
}
