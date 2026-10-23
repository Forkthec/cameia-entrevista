package tech.cameia.entrevista.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.domain.model.SessionConfigurationDraft;

/**
 * Configuración que la persona envía para preparar su entrevista. Todos los campos llegan como texto: la
 * validación completa, con una causa por campo, es del dominio y responde todos los errores a la vez.
 *
 * @param profileId perfil profesional elegido
 * @param targetRoleId rol objetivo del catálogo
 * @param jobOffer oferta laboral en texto libre
 * @param mode modalidad
 * @param tone tono del entrevistador
 * @param answerFormat forma de respuesta
 * @param sessionLanguage idioma de la entrevista
 */
@Schema(description = "Configuración de la entrevista. Los campos desconocidos se ignoran")
public record CreateSessionRequest(
        @Schema(description = "Perfil Profesional elegido; debe ser de la persona autenticada", format = "uuid",
                example = "7c9e6679-7425-40de-944b-e07fc1f90ae7", requiredMode = Schema.RequiredMode.REQUIRED)
        String profileId,
        @Schema(description = "Rol objetivo; debe estar activo en el catálogo de roles (puede ser distinto de los"
                + " roles del perfil)", format = "uuid", example = "a0000001-0000-0000-0000-000000000002",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String targetRoleId,
        @Schema(description = "Oferta laboral en texto libre. Opcional: vacía equivale a no enviarla. Se normaliza a"
                + " NFC y se recortan los extremos; hasta 3000 caracteres (puntos de código)", maxLength = 3000,
                example = "Buscamos desarrollador backend con Java", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String jobOffer,
        @Schema(description = "Modalidad. ENTRENO todavía no está disponible",
                allowableValues = {"SIMULACION", "ENTRENO"}, example = "SIMULACION",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String mode,
        @Schema(description = "Tono del entrevistador", allowableValues = {"PROFESIONAL_NEUTRO", "CALIDO", "ESTRICTO",
                "MOTIVADOR"}, example = "PROFESIONAL_NEUTRO", requiredMode = Schema.RequiredMode.REQUIRED)
        String tone,
        @Schema(description = "Forma de respuesta", allowableValues = {"AUDIO", "TEXTO"}, example = "AUDIO",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String answerFormat,
        @Schema(description = "Idioma de la entrevista", allowableValues = {"es-CO", "en"}, example = "es-CO",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String sessionLanguage) {

    /**
     * Arma el borrador del dominio con el encabezado de la clave anti-duplicados.
     *
     * @param idempotencyKey valor del encabezado {@code Idempotency-Key}, o {@code null}
     * @return el borrador sin validar
     */
    public SessionConfigurationDraft toDraft(String idempotencyKey) {
        return new SessionConfigurationDraft(profileId, targetRoleId, jobOffer, mode, tone, answerFormat,
                sessionLanguage, idempotencyKey);
    }
}
