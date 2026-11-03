package tech.cameia.entrevista.infrastructure.persistence.mapper;

import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.TurnSnapshot;
import tech.cameia.entrevista.infrastructure.persistence.entity.InterviewTurnEntity;

/**
 * Traduce entre el turno del dominio y su fila. Es una clase de métodos estáticos porque no guarda
 * estado.
 */
public final class InterviewTurnMapper {

    private InterviewTurnMapper() {
    }

    /**
     * Copia el turno del dominio a una fila.
     *
     * @param turn turno del dominio
     * @return la fila equivalente
     */
    public static InterviewTurnEntity toEntity(InterviewTurn turn) {
        InterviewTurnEntity entity = new InterviewTurnEntity();
        entity.setId(turn.id());
        entity.setQuestionId(turn.questionId());
        entity.setStatus(turn.status());
        entity.setCreatedAt(turn.createdAt());
        entity.setUpdatedAt(turn.updatedAt());
        entity.setVersion(turn.version());
        return entity;
    }

    /**
     * Reconstruye el turno del dominio desde su fila.
     *
     * @param entity fila leída de la base
     * @return el turno del dominio
     */
    public static InterviewTurn toDomain(InterviewTurnEntity entity) {
        return InterviewTurn.reconstitute(new TurnSnapshot(entity.getId(), entity.getQuestionId(),
                entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion()));
    }
}
