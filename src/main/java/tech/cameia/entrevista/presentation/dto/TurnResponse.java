package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Turno que se acaba de responder.
 *
 * @param turnId identificador del turno
 * @param status estado del turno; siempre {@code COMPLETED} en una respuesta exitosa
 */
@Schema(description = "Turno respondido")
public record TurnResponse(
        @Schema(format = "uuid", example = "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12") UUID turnId,
        @Schema(description = "Estado del turno", allowableValues = {"COMPLETED"}, example = "COMPLETED")
        String status) {
}
