package tech.cameia.entrevista.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.AnswerAttemptEntity;

/**
 * Adaptador del puerto de respuestas sobre JPA. Incluye la traducción entre la respuesta del dominio y su fila.
 */
@Repository
class AnswerAttemptRepositoryAdapter implements AnswerAttemptRepository {

    private final AnswerAttemptJpaRepository jpa;

    AnswerAttemptRepositoryAdapter(AnswerAttemptJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void insert(AnswerAttempt attempt) {
        // El identificador es siempre nuevo, así que se inserta; el disparador de la base rechaza cualquier UPDATE.
        jpa.saveAndFlush(new AnswerAttemptEntity(attempt.id(), attempt.turnId(), attempt.definitive(),
                attempt.origin(), attempt.text(), attempt.shownQuestion(), attempt.detectedLanguage(),
                attempt.createdAt()));
    }
}
