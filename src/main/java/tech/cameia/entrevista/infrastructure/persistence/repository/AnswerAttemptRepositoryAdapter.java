package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.exception.AnswerAlreadyExistsException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;
import tech.cameia.entrevista.domain.model.AnsweredQuestion;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.AnswerAttemptEntity;

/**
 * Adaptador del puerto de respuestas sobre JPA. Traduce el índice único de la respuesta definitiva a un error de
 * negocio: un turno solo tiene una.
 */
@Repository
class AnswerAttemptRepositoryAdapter implements AnswerAttemptRepository {

    /** Índice único parcial: una sola respuesta definitiva por turno. */
    static final String UNIQUE_DEFINITIVE_PER_TURN = "uq_intento_definitivo_por_turno";

    /** Tipo de interacción de la primera respuesta de un turno. */
    private static final String INITIAL = "INICIAL";

    private final AnswerAttemptJpaRepository jpa;

    AnswerAttemptRepositoryAdapter(AnswerAttemptJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public AnswerAttempt save(AnswerAttempt attempt) {
        try {
            return toDomain(jpa.saveAndFlush(toEntity(attempt)));
        } catch (DataIntegrityViolationException e) {
            if (ConstraintNames.violates(e, UNIQUE_DEFINITIVE_PER_TURN)) {
                throw new AnswerAlreadyExistsException();
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnswerAttempt> findDefinitive(UUID turnId) {
        return jpa.findDefinitive(turnId).map(AnswerAttemptRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnsweredQuestion> historyOf(UUID sessionId) {
        return jpa.historyOf(sessionId).stream()
                .map(row -> new AnsweredQuestion(row.getNumber(), row.getQuestion(), row.getAnswer())).toList();
    }

    private static AnswerAttemptEntity toEntity(AnswerAttempt a) {
        return new AnswerAttemptEntity(a.id(), a.turnId(), INITIAL, true, a.origin().name(), a.text(),
                a.shownQuestion(), a.detectedLanguage(), a.createdAt());
    }

    private static AnswerAttempt toDomain(AnswerAttemptEntity e) {
        return new AnswerAttempt(e.getId(), e.getTurnId(), AnswerOrigin.valueOf(e.getOrigin()), e.getText(),
                e.getShownQuestion(), e.getDetectedLanguage(), e.getCreatedAt());
    }
}
