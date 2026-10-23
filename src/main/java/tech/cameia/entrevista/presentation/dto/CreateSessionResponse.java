package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.CreateSessionResult;

/**
 * Sesión configurada y, si la hay, la entrevista que la persona tiene en curso.
 *
 * @param sessionId identificador de la sesión configurada
 * @param status estado de la sesión
 * @param configuration configuración vigente
 * @param ongoingSession entrevista en curso, o {@code null}
 */
@Schema(description = "Sesión configurada")
public record CreateSessionResponse(
        @Schema(format = "uuid", example = "3b1f8f2a-6c1e-4d8b-9f0a-2c3d4e5f6a7b") UUID sessionId,
        @Schema(description = "Estado de la sesión", allowableValues = {"CONFIGURADA"}, example = "CONFIGURADA")
        String status,
        SessionConfigurationResponse configuration,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "Entrevista en curso de la persona; null si no tiene", nullable = true)
        OngoingSessionResponse ongoingSession) {

    /**
     * Traduce el resultado del caso de uso al contrato.
     *
     * @param result resultado de crear o reutilizar la sesión
     * @return la respuesta para el cliente
     */
    public static CreateSessionResponse from(CreateSessionResult result) {
        return new CreateSessionResponse(result.sessionId(), result.status().code(),
                SessionConfigurationResponse.from(result.configuration()),
                result.ongoing() == null ? null : OngoingSessionResponse.from(result.ongoing()));
    }
}
