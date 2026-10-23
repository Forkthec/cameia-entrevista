package tech.cameia.entrevista.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Copia del Perfil Profesional que guarda Entrevista para crear sesiones sin llamar a Perfil.
 *
 * <p>El contenido ya viene validado contra el contrato del evento; el dominio solo exige que no
 * falte nada y sabe decir si el perfil sirve para una entrevista.</p>
 *
 * @param profileId               identificador del perfil en Perfil
 * @param owner                   dueño del perfil
 * @param name                    nombre del perfil, de 1 a {@value #MAX_NAME_LENGTH} puntos de código
 * @param status                  estado publicado
 * @param archivedAt              instante de archivado, o {@code null} si no está archivado
 * @param professionalContextJson bloque {@code contextoProfesional} del evento, en JSON
 * @param aspiredRolesJson        lista {@code rolesAspirados} del evento, en JSON
 * @param updatedAt               {@code actualizadoEn} del evento que produjo esta copia
 */
public record ProfileReplica(UUID profileId, FirebaseUid owner, String name, ReplicatedProfileStatus status,
        Instant archivedAt, String professionalContextJson, String aspiredRolesJson, Instant updatedAt) {

    /** Largo máximo del nombre, igual al de la columna y al del contrato del evento. */
    public static final int MAX_NAME_LENGTH = 255;

    /**
     * Exige todos los componentes salvo {@code archivedAt}.
     *
     * @throws NullPointerException si falta alguno de los obligatorios
     */
    public ProfileReplica {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(professionalContextJson, "professionalContextJson");
        Objects.requireNonNull(aspiredRolesJson, "aspiredRolesJson");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Indica si el perfil sirve para una entrevista.
     *
     * @return {@code true} solo si el perfil está completo y no archivado
     */
    public boolean isUsableForInterview() {
        return status == ReplicatedProfileStatus.COMPLETED && archivedAt == null;
    }
}
