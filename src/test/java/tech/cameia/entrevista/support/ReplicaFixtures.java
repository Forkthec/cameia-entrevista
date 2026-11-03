package tech.cameia.entrevista.support;

import java.time.Instant;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;

/**
 * Copias del perfil de ejemplo para las pruebas, con los valores del ejemplo del contrato del evento.
 */
public final class ReplicaFixtures {

    /** Identificador del perfil del ejemplo del contrato. */
    public static final UUID PROFILE_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    /** Dueño del perfil del ejemplo del contrato. */
    public static final FirebaseUid OWNER = new FirebaseUid("6f1d2c3b4a5e4f60718293a4b5c6d7e8");

    /** Rol aspirado del ejemplo del contrato. */
    public static final String ASPIRED_ROLES_JSON =
            "[{\"codigo\":\"a0000001-0000-0000-0000-000000000002\",\"nombre\":\"Desarrollador Backend\"}]";

    /** Bloque {@code contextoProfesional} mínimo. */
    public static final String CONTEXT_JSON = "{\"resumen\":\"Analista con tres años de experiencia en SQL y Python.\"}";

    private ReplicaFixtures() {
    }

    /**
     * @param owner     dueño
     * @param name      nombre del perfil
     * @param updatedAt {@code actualizadoEn} del evento
     * @return una copia completada y sin archivar del perfil del ejemplo
     */
    public static ProfileReplica replica(FirebaseUid owner, String name, Instant updatedAt) {
        return new ProfileReplica(PROFILE_ID, owner, name, ReplicatedProfileStatus.COMPLETED, null, CONTEXT_JSON,
                ASPIRED_ROLES_JSON, updatedAt);
    }

    /**
     * @param updatedAt {@code actualizadoEn} del evento
     * @return una copia del perfil del ejemplo con su dueño y nombre
     */
    public static ProfileReplica replica(Instant updatedAt) {
        return replica(OWNER, "Analista de datos", updatedAt);
    }
}
