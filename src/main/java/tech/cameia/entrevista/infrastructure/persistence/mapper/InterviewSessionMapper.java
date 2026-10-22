package tech.cameia.entrevista.infrastructure.persistence.mapper;

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionSnapshot;
import tech.cameia.entrevista.infrastructure.persistence.entity.InterviewSessionEntity;

/**
 * Traduce entre la sesión del dominio y su fila. Es una clase de métodos estáticos porque no
 * guarda estado.
 */
public final class InterviewSessionMapper {

    private InterviewSessionMapper() {
    }

    /**
     * Copia la sesión del dominio a una fila. La versión de una sesión nueva es {@code null}, y así
     * JPA sabe que debe insertarla.
     *
     * @param session sesión del dominio
     * @return la fila equivalente
     */
    public static InterviewSessionEntity toEntity(InterviewSession session) {
        SessionConfiguration configuration = session.configuration();
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setId(session.id());
        entity.setFirebaseUid(session.owner().value());
        entity.setProfileId(configuration.profileId());
        entity.setTargetRoleId(configuration.targetRoleId());
        entity.setJobOffer(configuration.jobOffer());
        entity.setProfileSnapshot(configuration.profileSnapshotJson());
        entity.setModality(configuration.modality());
        entity.setTone(configuration.tone());
        entity.setAnswerMode(configuration.answerMode());
        entity.setLanguage(configuration.language());
        entity.setStatus(session.status());
        entity.setPlanningRetries(session.planningRetries());
        entity.setCreatedAt(session.createdAt());
        entity.setUpdatedAt(session.updatedAt());
        entity.setLastActivityAt(session.lastActivityAt());
        entity.setFinishedAt(session.finishedAt());
        entity.setVersion(session.version());
        return entity;
    }

    /**
     * Reconstruye la sesión del dominio desde su fila, sin validar transiciones.
     *
     * @param entity fila leída de la base
     * @return la sesión del dominio
     */
    public static InterviewSession toDomain(InterviewSessionEntity entity) {
        SessionConfiguration configuration = new SessionConfiguration(entity.getProfileId(),
                entity.getTargetRoleId(), entity.getJobOffer(), entity.getProfileSnapshot(), entity.getModality(),
                entity.getTone(), entity.getAnswerMode(), entity.getLanguage());
        return InterviewSession.reconstitute(new SessionSnapshot(entity.getId(),
                new FirebaseUid(entity.getFirebaseUid()), configuration, entity.getStatus(),
                entity.getPlanningRetries(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getLastActivityAt(), entity.getFinishedAt(), entity.getVersion()));
    }
}
