package tech.cameia.entrevista.infrastructure.messaging.payload;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo JSON de {@code perfil.profesional-actualizado} versión 1, tal como lo publica Perfil.
 *
 * <p>Los nombres JSON los fija el contrato publicado; los componentes llevan nombres en inglés. Los
 * campos que el contrato no define se ignoran al leer y no se guardan. Los textos miden puntos de
 * código Unicode ({@link TextLength}); las listas, elementos ({@code @Size}).</p>
 *
 * @param userId              {@code firebaseUid} del dueño del perfil, de 1 a 128 puntos de código
 * @param professionalContext copia del perfil
 * @param aspiredRoles        roles aspirados del perfil, de 1 a 5
 * @param updatedAt           instante del cambio en UTC con milisegundos y sufijo {@code Z}
 */
public record ProfileUpdatedPayloadV1(
        @JsonProperty("usuarioId") @NotNull @TextLength(min = 1, max = 128) String userId,
        @JsonProperty("contextoProfesional") @NotNull @Valid ProfessionalContext professionalContext,
        @JsonProperty("rolesAspirados") @NotNull @Size(min = 1, max = 5)
                List<@NotNull @Valid AspiredRole> aspiredRoles,
        @JsonProperty("actualizadoEn") @NotNull @Pattern(regexp = ProfileUpdatedPayloadV1.INSTANT_PATTERN)
                String updatedAt) {

    /** Instante con milisegundos y {@code Z}; la existencia real de la fecha se comprueba al leerlo. */
    static final String INSTANT_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

    /** UUID canónico de 36 caracteres, en minúsculas o mayúsculas. */
    static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    /** Mes de un año en formato {@code AAAA-MM}. */
    static final String YEAR_MONTH_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";

    /**
     * Bloque {@code contextoProfesional}.
     *
     * @param id          identificador del perfil
     * @param name        nombre del perfil, de 1 a 255 puntos de código
     * @param status      {@code PENDING}, {@code IN_PROGRESS}, {@code IN_REVIEW} o {@code COMPLETED}
     * @param archivedAt  instante ISO-8601 de archivado, o {@code null}
     * @param summary     resumen profesional, de 1 a 2000 puntos de código
     * @param experiences experiencias laborales, de 0 a 4
     * @param educations  formaciones, de 1 a 5
     * @param skills      habilidades, de 1 a 20
     */
    public record ProfessionalContext(
            @JsonProperty("id") @NotNull @Pattern(regexp = UUID_PATTERN) String id,
            @JsonProperty("nombre") @NotNull @TextLength(min = 1, max = 255) String name,
            @JsonProperty("estado") @NotNull @Pattern(regexp = "PENDING|IN_PROGRESS|IN_REVIEW|COMPLETED")
                    String status,
            @JsonProperty("fecha_archivado") String archivedAt,
            @JsonProperty("resumen") @NotNull @TextLength(min = 1, max = 2000) String summary,
            @JsonProperty("experiencias") @NotNull @Size(max = 4) List<@NotNull @Valid WorkExperience> experiences,
            @JsonProperty("formaciones") @NotNull @Size(min = 1, max = 5)
                    List<@NotNull @Valid Education> educations,
            @JsonProperty("habilidades") @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Skill> skills) {
    }

    /**
     * Experiencia laboral.
     *
     * @param company          empresa, de 1 a 100 puntos de código
     * @param position         cargo, de 1 a 100 puntos de código
     * @param description      descripción, o {@code null}; de 1 a 500 puntos de código
     * @param start            mes de inicio {@code AAAA-MM}, o {@code null}
     * @param end              mes de fin {@code AAAA-MM}, o {@code null}
     * @param employmentStatus {@code CURRENT}, {@code UNKNOWN_END} o {@code ENDED}
     */
    public record WorkExperience(
            @JsonProperty("empresa") @NotNull @TextLength(min = 1, max = 100) String company,
            @JsonProperty("cargo") @NotNull @TextLength(min = 1, max = 100) String position,
            @JsonProperty("descripcion") @TextLength(min = 1, max = 500) String description,
            @JsonProperty("inicio") @Pattern(regexp = YEAR_MONTH_PATTERN) String start,
            @JsonProperty("fin") @Pattern(regexp = YEAR_MONTH_PATTERN) String end,
            @JsonProperty("estadoEmpleo") @NotNull @Pattern(regexp = "CURRENT|UNKNOWN_END|ENDED")
                    String employmentStatus) {
    }

    /**
     * Formación académica.
     *
     * @param institution institución, de 1 a 150 puntos de código
     * @param title       título, de 1 a 150 puntos de código
     * @param level       {@code TECHNICAL}, {@code UNDERGRADUATE} o {@code POSTGRADUATE}
     * @param start       mes de inicio {@code AAAA-MM}
     * @param end         mes de fin {@code AAAA-MM}, o {@code null}
     * @param inProgress  si la formación sigue en curso
     */
    public record Education(
            @JsonProperty("institucion") @NotNull @TextLength(min = 1, max = 150) String institution,
            @JsonProperty("titulo") @NotNull @TextLength(min = 1, max = 150) String title,
            @JsonProperty("nivel") @NotNull @Pattern(regexp = "TECHNICAL|UNDERGRADUATE|POSTGRADUATE") String level,
            @JsonProperty("inicio") @NotNull @Pattern(regexp = YEAR_MONTH_PATTERN) String start,
            @JsonProperty("fin") @Pattern(regexp = YEAR_MONTH_PATTERN) String end,
            @JsonProperty("enCurso") @NotNull Boolean inProgress) {
    }

    /**
     * Habilidad.
     *
     * @param name  nombre, de 1 a 60 puntos de código
     * @param level {@code BASIC}, {@code INTERMEDIATE} o {@code ADVANCED}
     */
    public record Skill(
            @JsonProperty("nombre") @NotNull @TextLength(min = 1, max = 60) String name,
            @JsonProperty("nivel") @NotNull @Pattern(regexp = "BASIC|INTERMEDIATE|ADVANCED") String level) {
    }

    /**
     * Rol aspirado.
     *
     * @param code identificador del rol en el catálogo de Perfil
     * @param name nombre del rol, de 1 a 255 puntos de código
     */
    public record AspiredRole(
            @JsonProperty("codigo") @NotNull @Pattern(regexp = UUID_PATTERN) String code,
            @JsonProperty("nombre") @NotNull @TextLength(min = 1, max = 255) String name) {
    }
}
