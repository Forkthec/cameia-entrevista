package tech.cameia.entrevista.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code rol_profesional}.
 *
 * <p>No es el modelo de dominio: existe para leer la copia con JPA y para que Hibernate valide el
 * esquema. Las escrituras no pasan por aquí: la instantánea completa se aplica con una sola sentencia
 * nativa condicionada a la fecha de cada fila. El esquema no se declara porque lo fija
 * {@code hibernate.default_schema}.</p>
 */
@Entity
@Table(name = "rol_profesional")
public class ProfessionalRoleEntity {

    /** Identificador del rol en Perfil. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Nombre en español. */
    @Column(name = "nombre_es", nullable = false, length = 255)
    private String nameEs;

    /** Nombre en inglés. */
    @Column(name = "nombre_en", nullable = false, length = 255)
    private String nameEn;

    /** Estado publicado: {@code ACTIVE} o {@code INACTIVE}. */
    @Column(name = "estado", nullable = false, length = 8)
    private String status;

    /** {@code actualizadoEn} de la última instantánea aplicada a esta fila. */
    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;

    /** Instante en que el servicio aplicó esa instantánea. */
    @Column(name = "fecha_replicacion", nullable = false)
    private Instant replicatedAt;

    /** Constructor que exige JPA. */
    protected ProfessionalRoleEntity() {
    }

    /** @return identificador del rol */
    public UUID getId() {
        return id;
    }

    /** @return nombre en español */
    public String getNameEs() {
        return nameEs;
    }

    /** @return nombre en inglés */
    public String getNameEn() {
        return nameEn;
    }

    /** @return estado publicado */
    public String getStatus() {
        return status;
    }

    /** @return {@code actualizadoEn} de la última instantánea aplicada */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** @return instante en que se aplicó esa instantánea */
    public Instant getReplicatedAt() {
        return replicatedAt;
    }
}
