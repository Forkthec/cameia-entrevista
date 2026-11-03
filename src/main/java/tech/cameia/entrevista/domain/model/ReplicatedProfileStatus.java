package tech.cameia.entrevista.domain.model;

/**
 * Estado del Perfil Profesional tal como lo publica Perfil. Solo {@link #COMPLETED} es un perfil
 * activo; el contrato admite los otros tres porque un perfil puede reactivarse en el futuro.
 */
public enum ReplicatedProfileStatus {

    /** El perfil se creó y no se ha empezado a llenar. */
    PENDING,

    /** La persona lo está llenando. */
    IN_PROGRESS,

    /** El perfil espera revisión. */
    IN_REVIEW,

    /** El perfil está completo y es el único estado que sirve para una entrevista. */
    COMPLETED
}
