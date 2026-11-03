package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de la tabla {@code clave_idempotencia}.
 *
 * <p>No es el modelo de dominio: existe para hablar con JPA y sus columnas siguen la convención
 * de la base de datos, en español. La traducción la hace el mapeador de {@code persistence.mapper}.
 * El esquema no se declara aquí porque lo fija {@code hibernate.default_schema}.</p>
 */
@Entity
@Table(name = "clave_idempotencia")
public class IdempotencyKeyEntity {

    /** Identificador del registro. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Persona que hizo la petición. */
    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    /** Operación protegida. */
    @Column(name = "operacion", nullable = false, length = 40)
    private String operation;

    /** Clave enviada por la persona. */
    @Column(name = "clave", nullable = false, length = 64)
    private String key;

    /** Huella hexadecimal de la petición original. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "huella_peticion", nullable = false, length = 64)
    private String requestFingerprint;

    /** Estado HTTP de la respuesta original. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "estado_respuesta", nullable = false)
    private int responseStatus;

    /** Cuerpo JSON de la respuesta original. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cuerpo_respuesta", nullable = false)
    private String responseBody;

    /** Fecha del registro. */
    @Column(name = "fecha_creacion", nullable = false)
    private Instant createdAt;

    /** Constructor que exige JPA y que usa el mapeador. */
    public IdempotencyKeyEntity() {
    }

    /**
     * @return identificador del registro
     */
    public UUID getId() {
        return id;
    }

    /**
     * @param id identificador del registro
     */
    public void setId(UUID id) {
        this.id = id;
    }
    /**
     * @return persona que hizo la petición
     */
    public String getFirebaseUid() {
        return firebaseUid;
    }

    /**
     * @param firebaseUid persona que hizo la petición
     */
    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }
    /**
     * @return operación protegida
     */
    public String getOperation() {
        return operation;
    }

    /**
     * @param operation operación protegida
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }
    /**
     * @return clave enviada por la persona
     */
    public String getKey() {
        return key;
    }

    /**
     * @param key clave enviada por la persona
     */
    public void setKey(String key) {
        this.key = key;
    }
    /**
     * @return huella hexadecimal de la petición original
     */
    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    /**
     * @param requestFingerprint huella hexadecimal de la petición original
     */
    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }
    /**
     * @return estado HTTP de la respuesta original
     */
    public int getResponseStatus() {
        return responseStatus;
    }

    /**
     * @param responseStatus estado HTTP de la respuesta original
     */
    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }
    /**
     * @return cuerpo JSON de la respuesta original
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * @param responseBody cuerpo JSON de la respuesta original
     */
    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
    /**
     * @return fecha del registro
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @param createdAt fecha del registro
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
