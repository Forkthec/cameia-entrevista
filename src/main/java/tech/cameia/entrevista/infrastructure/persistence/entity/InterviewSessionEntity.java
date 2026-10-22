package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.AnswerModeConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.InterviewLanguageConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.ModalityConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.SessionStatusConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.ToneConverter;

/**
 * Fila de la tabla {@code sesion}.
 *
 * <p>No es el modelo de dominio: existe para hablar con JPA y sus columnas siguen la convención
 * de la base de datos, en español. La traducción la hace el mapeador de {@code persistence.mapper}.
 * El esquema no se declara aquí porque lo fija {@code hibernate.default_schema}.</p>
 */
@Entity
@Table(name = "sesion")
public class InterviewSessionEntity {

    /** Identificador de la sesión. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Dueño de la sesión. */
    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    /** Perfil profesional, de otro servicio. */
    @Column(name = "perfil_id", nullable = false)
    private UUID profileId;

    /** Rol objetivo, de otro servicio. */
    @Column(name = "rol_objetivo_id", nullable = false)
    private UUID targetRoleId;

    /** Texto libre de la oferta laboral. */
    @Column(name = "oferta_laboral", length = 3000)
    private String jobOffer;

    /** Copia JSON del contexto del perfil. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto_perfil_snapshot", nullable = false)
    private String profileSnapshot;

    /** Modalidad de la sesión. */
    @Convert(converter = ModalityConverter.class)
    @Column(name = "modalidad", nullable = false, length = 20)
    private Modality modality;

    /** Tono del entrevistador. */
    @Convert(converter = ToneConverter.class)
    @Column(name = "tono", nullable = false, length = 20)
    private Tone tone;

    /** Forma de respuesta. */
    @Convert(converter = AnswerModeConverter.class)
    @Column(name = "forma_respuesta", nullable = false, length = 10)
    private AnswerMode answerMode;

    /** Idioma de la entrevista. */
    @Convert(converter = InterviewLanguageConverter.class)
    @Column(name = "idioma", nullable = false, length = 5)
    private InterviewLanguage language;

    /** Estado de la sesión. */
    @Convert(converter = SessionStatusConverter.class)
    @Column(name = "estado", nullable = false, length = 20)
    private SessionStatus status;

    /** Reintentos de planificación gastados. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "reintentos_planificacion", nullable = false)
    private int planningRetries;

    /** Fecha de creación. */
    @Column(name = "fecha_creacion", nullable = false)
    private Instant createdAt;

    /** Fecha de la última modificación. */
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant updatedAt;

    /** Fecha de la última actividad. */
    @Column(name = "fecha_ultima_actividad")
    private Instant lastActivityAt;

    /** Fecha de cierre. */
    @Column(name = "fecha_fin")
    private Instant finishedAt;

    /** Versión de control de edición simultánea. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Constructor que exige JPA y que usa el mapeador. */
    public InterviewSessionEntity() {
    }

    /**
     * @return identificador de la sesión
     */
    public UUID getId() {
        return id;
    }

    /**
     * @param id identificador de la sesión
     */
    public void setId(UUID id) {
        this.id = id;
    }
    /**
     * @return dueño de la sesión
     */
    public String getFirebaseUid() {
        return firebaseUid;
    }

    /**
     * @param firebaseUid dueño de la sesión
     */
    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }
    /**
     * @return perfil profesional, de otro servicio
     */
    public UUID getProfileId() {
        return profileId;
    }

    /**
     * @param profileId perfil profesional, de otro servicio
     */
    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }
    /**
     * @return rol objetivo, de otro servicio
     */
    public UUID getTargetRoleId() {
        return targetRoleId;
    }

    /**
     * @param targetRoleId rol objetivo, de otro servicio
     */
    public void setTargetRoleId(UUID targetRoleId) {
        this.targetRoleId = targetRoleId;
    }
    /**
     * @return texto libre de la oferta laboral
     */
    public String getJobOffer() {
        return jobOffer;
    }

    /**
     * @param jobOffer texto libre de la oferta laboral
     */
    public void setJobOffer(String jobOffer) {
        this.jobOffer = jobOffer;
    }
    /**
     * @return copia JSON del contexto del perfil
     */
    public String getProfileSnapshot() {
        return profileSnapshot;
    }

    /**
     * @param profileSnapshot copia JSON del contexto del perfil
     */
    public void setProfileSnapshot(String profileSnapshot) {
        this.profileSnapshot = profileSnapshot;
    }
    /**
     * @return modalidad de la sesión
     */
    public Modality getModality() {
        return modality;
    }

    /**
     * @param modality modalidad de la sesión
     */
    public void setModality(Modality modality) {
        this.modality = modality;
    }
    /**
     * @return tono del entrevistador
     */
    public Tone getTone() {
        return tone;
    }

    /**
     * @param tone tono del entrevistador
     */
    public void setTone(Tone tone) {
        this.tone = tone;
    }
    /**
     * @return forma de respuesta
     */
    public AnswerMode getAnswerMode() {
        return answerMode;
    }

    /**
     * @param answerMode forma de respuesta
     */
    public void setAnswerMode(AnswerMode answerMode) {
        this.answerMode = answerMode;
    }
    /**
     * @return idioma de la entrevista
     */
    public InterviewLanguage getLanguage() {
        return language;
    }

    /**
     * @param language idioma de la entrevista
     */
    public void setLanguage(InterviewLanguage language) {
        this.language = language;
    }
    /**
     * @return estado de la sesión
     */
    public SessionStatus getStatus() {
        return status;
    }

    /**
     * @param status estado de la sesión
     */
    public void setStatus(SessionStatus status) {
        this.status = status;
    }
    /**
     * @return reintentos de planificación gastados
     */
    public int getPlanningRetries() {
        return planningRetries;
    }

    /**
     * @param planningRetries reintentos de planificación gastados
     */
    public void setPlanningRetries(int planningRetries) {
        this.planningRetries = planningRetries;
    }
    /**
     * @return fecha de creación
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @param createdAt fecha de creación
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    /**
     * @return fecha de la última modificación
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * @param updatedAt fecha de la última modificación
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    /**
     * @return fecha de la última actividad
     */
    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    /**
     * @param lastActivityAt fecha de la última actividad
     */
    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
    /**
     * @return fecha de cierre
     */
    public Instant getFinishedAt() {
        return finishedAt;
    }

    /**
     * @param finishedAt fecha de cierre
     */
    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
    /**
     * @return versión de control de edición simultánea
     */
    public Long getVersion() {
        return version;
    }

    /**
     * @param version versión de control de edición simultánea
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
