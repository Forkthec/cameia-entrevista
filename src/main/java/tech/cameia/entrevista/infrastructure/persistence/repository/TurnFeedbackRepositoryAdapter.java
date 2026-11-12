package tech.cameia.entrevista.infrastructure.persistence.repository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.model.TurnFeedback;
import tech.cameia.entrevista.domain.port.TurnFeedbackRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.TurnFeedbackEntity;

/**
 * Adaptador del puerto de retroalimentación sobre JPA. Guarda la evaluación como un objeto JSON de cuatro claves
 * y traduce la unicidad por turno a un conflicto de turno.
 */
@Repository
class TurnFeedbackRepositoryAdapter implements TurnFeedbackRepository {

    /** Restricción única: una sola retroalimentación por turno. */
    static final String UNIQUE_PER_TURN = "uq_feedback_turno_turno";

    private final TurnFeedbackJpaRepository jpa;
    private final JsonMapper json;

    TurnFeedbackRepositoryAdapter(TurnFeedbackJpaRepository jpa, JsonMapper json) {
        this.jpa = jpa;
        this.json = json;
    }

    @Override
    @Transactional
    public TurnFeedback save(TurnFeedback feedback) {
        try {
            jpa.saveAndFlush(toEntity(feedback));
            return feedback;
        } catch (DataIntegrityViolationException e) {
            if (ConstraintNames.violates(e, UNIQUE_PER_TURN)) {
                throw new TurnConflictException();
            }
            throw e;
        }
    }

    private TurnFeedbackEntity toEntity(TurnFeedback f) {
        ObjectNode body = json.createObjectNode();
        body.put("accion", f.result().action());
        body.put("clasificacion", f.result().classification());
        body.put("motivo", f.result().reason());
        body.put("feedback", f.result().feedback());
        return new TurnFeedbackEntity(f.id(), f.turnId(), f.executionId(), json.writeValueAsString(body),
                f.createdAt());
    }
}
