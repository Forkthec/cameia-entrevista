package tech.cameia.entrevista.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.AnswerAttemptEntity;

/**
 * Adaptador del puerto {@link AnswerAttemptRepository} sobre Spring Data. Toda respuesta que registra es la inicial
 * y definitiva del turno; el índice {@code uq_intento_definitivo_por_turno} impide una segunda.
 */
@Repository
class AnswerAttemptRepositoryAdapter implements AnswerAttemptRepository {

    /** Única interacción que admite hoy el esquema. */
    private static final String INITIAL = "INICIAL";

    private final AnswerAttemptJpaRepository jpa;

    AnswerAttemptRepositoryAdapter(AnswerAttemptJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public AnswerAttempt save(AnswerAttempt attempt) {
        jpa.saveAndFlush(new AnswerAttemptEntity(attempt.id(), attempt.turnId(), INITIAL, true,
                attempt.origin().name(), attempt.text(), attempt.shownQuestion(), attempt.detectedLanguage(),
                attempt.createdAt()));
        return attempt;
    }
}
