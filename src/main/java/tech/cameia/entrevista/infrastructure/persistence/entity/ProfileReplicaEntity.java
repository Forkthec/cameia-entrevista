package tech.cameia.entrevista.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code contexto_profesional_replica}.
 *
 * <p>No es el modelo de dominio: existe para leer la copia con JPA y para que Hibernate valide el
 * esquema. Las escrituras no pasan por aquí: el reemplazo condicionado a la fecha del evento es una
 * sola sentencia nativa del repositorio. El esquema no se declara porque lo fija
 * {@code hibernate.default_schema}.</p>
 */
@Entity
@Table(name = "contexto_profesional_replica")
public class ProfileReplicaEntity {

    /** Identificador del perfil en Perfil. */
    @Id
    @Column(name = "perfil_id", nullable = false)
    private UUID profileId;

    /** Dueño del perfil. */
    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    /** Nombre del perfil. */
    @Column(name = "nombre", nullable = false, length = 255)
    private String name;

    /** Estado publicado por Perfil. */
    @Column(name = "estado", nullable = false, length = 20)
    private String status;

    /** Instante de archivado, o {@code null}. */
    @Column(name = "fecha_archivado")
    private Instant archivedAt;

    /** Bloque {@code contextoProfesional} del evento. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto_profesional", nullable = false)
    private String professionalContext;

    /** Lista {@code rolesAspirados} del evento. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles_aspirados", nullable = false)
    private String aspiredRoles;

    /** {@code actualizadoEn} del último evento aplicado. */
    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;

    /** Instante en que el servicio aplicó el evento. */
    @Column(name = "fecha_replicacion", nullable = false)
    private Instant replicatedAt;

    /** Constructor que exige JPA. */
    protected ProfileReplicaEntity() {
    }

    /** @return identificador del perfil */
    public UUID getProfileId() {
        return profileId;
    }

    /** @return dueño del perfil */
    public String getFirebaseUid() {
        return firebaseUid;
    }

    /** @return nombre del perfil */
    public String getName() {
        return name;
    }

    /** @return estado publicado */
    public String getStatus() {
        return status;
    }

    /** @return instante de archivado, o {@code null} */
    public Instant getArchivedAt() {
        return archivedAt;
    }

    /** @return bloque {@code contextoProfesional} en JSON */
    public String getProfessionalContext() {
        return professionalContext;
    }

    /** @return lista {@code rolesAspirados} en JSON */
    public String getAspiredRoles() {
        return aspiredRoles;
    }

    /** @return {@code actualizadoEn} del último evento aplicado */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** @return instante en que se aplicó el evento */
    public Instant getReplicatedAt() {
        return replicatedAt;
    }
}
