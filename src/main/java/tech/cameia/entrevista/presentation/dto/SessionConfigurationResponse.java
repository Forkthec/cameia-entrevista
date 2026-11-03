package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.domain.model.ValidatedSessionRequest;

/**
 * Configuración con la que quedó la sesión, ya normalizada.
 *
 * @param profileId perfil profesional
 * @param targetRoleId rol objetivo
 * @param jobOffer oferta laboral normalizada, o {@code null} si no hay
 * @param mode modalidad
 * @param tone tono
 * @param answerFormat forma de respuesta
 * @param sessionLanguage idioma
 */
@Schema(description = "Configuración vigente de la sesión")
public record SessionConfigurationResponse(
        @Schema(format = "uuid", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7") UUID profileId,
        @Schema(format = "uuid", example = "a0000001-0000-0000-0000-000000000002") UUID targetRoleId,
        @Schema(description = "Oferta laboral normalizada; null si no se envió", nullable = true,
                example = "Buscamos desarrollador backend con Java") String jobOffer,
        @Schema(allowableValues = {"SIMULACION"}, example = "SIMULACION") String mode,
        @Schema(allowableValues = {"PROFESIONAL_NEUTRO", "CALIDO", "ESTRICTO", "MOTIVADOR"},
                example = "PROFESIONAL_NEUTRO") String tone,
        @Schema(allowableValues = {"AUDIO", "TEXTO"}, example = "AUDIO") String answerFormat,
        @Schema(allowableValues = {"es-CO", "en"}, example = "es-CO") String sessionLanguage) {

    /**
     * Traduce la configuración validada al contrato, con los valores publicados de cada enumerado.
     *
     * @param configuration configuración validada
     * @return la configuración para el cliente
     */
    static SessionConfigurationResponse from(ValidatedSessionRequest configuration) {
        return new SessionConfigurationResponse(configuration.profileId(), configuration.targetRoleId(),
                configuration.jobOffer(), configuration.mode().code(), configuration.tone().code(),
                configuration.answerFormat().code(), configuration.sessionLanguage().code());
    }
}
