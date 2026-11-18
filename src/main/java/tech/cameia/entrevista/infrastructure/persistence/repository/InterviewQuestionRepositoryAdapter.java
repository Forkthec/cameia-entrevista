package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.InterviewQuestionEntity;

/**
 * Adaptador del puerto de preguntas sobre JPA. Incluye la traducción entre la pregunta del dominio y su fila.
 */
@Repository
class InterviewQuestionRepositoryAdapter implements InterviewQuestionRepository {

    private final InterviewQuestionJpaRepository jpa;

    InterviewQuestionRepositoryAdapter(InterviewQuestionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void saveAll(List<InterviewQuestion> questions) {
        jpa.saveAllAndFlush(questions.stream().map(InterviewQuestionRepositoryAdapter::toEntity).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewQuestion> findBySession(UUID sessionId) {
        return jpa.findBySessionIdOrderByPositionAsc(sessionId).stream()
                .map(InterviewQuestionRepositoryAdapter::toDomain).toList();
    }

    private static InterviewQuestionEntity toEntity(InterviewQuestion question) {
        return new InterviewQuestionEntity(question.id(), question.sessionId(), (short) question.position(),
                question.type().code(), question.originalText(), question.executionId(), question.createdAt());
    }

    private static InterviewQuestion toDomain(InterviewQuestionEntity entity) {
        return new InterviewQuestion(entity.getId(), entity.getSessionId(), entity.getPosition(),
                QuestionType.fromCode(entity.getType()), entity.getOriginalText(), entity.getExecutionId(),
                entity.getCreatedAt());
    }
}
