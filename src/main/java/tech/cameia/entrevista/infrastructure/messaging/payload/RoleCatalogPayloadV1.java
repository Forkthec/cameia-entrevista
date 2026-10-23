package tech.cameia.entrevista.infrastructure.messaging.payload;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo JSON de {@code perfil.catalogo-roles-actualizado} versión 1, tal como lo publica Perfil:
 * una instantánea completa del catálogo de roles profesionales.
 *
 * <p>Los nombres JSON los fija el contrato publicado; los componentes llevan nombres en inglés. Los
 * campos que el contrato no define se ignoran al leer y no se guardan. Los textos miden puntos de
 * código Unicode ({@link TextLength}); la lista, elementos ({@code @Size}).</p>
 *
 * @param updatedAt instante de la instantánea en UTC con milisegundos y sufijo {@code Z}
 * @param roles     todos los roles del catálogo, de 1 a 200, sin repetir {@code codigo}
 */
public record RoleCatalogPayloadV1(
        @JsonProperty("actualizadoEn") @NotNull @Pattern(regexp = ProfileUpdatedPayloadV1.INSTANT_PATTERN)
                String updatedAt,
        @JsonProperty("roles") @NotNull @Size(min = 1, max = 200) List<@NotNull @Valid RoleItem> roles) {

    /**
     * Rol del catálogo.
     *
     * @param code   identificador del rol en Perfil, UUID canónico
     * @param nameEs nombre en español, de 1 a 255 puntos de código
     * @param nameEn nombre en inglés, de 1 a 255 puntos de código
     * @param status {@code ACTIVE} o {@code INACTIVE}
     */
    public record RoleItem(
            @JsonProperty("codigo") @NotNull @Pattern(regexp = ProfileUpdatedPayloadV1.UUID_PATTERN) String code,
            @JsonProperty("nombreEs") @NotNull @TextLength(min = 1, max = 255) String nameEs,
            @JsonProperty("nombreEn") @NotNull @TextLength(min = 1, max = 255) String nameEn,
            @JsonProperty("estado") @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }
}
