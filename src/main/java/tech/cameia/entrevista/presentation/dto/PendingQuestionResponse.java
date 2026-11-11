package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pregunta que espera respuesta.
 *
 * @param turnId identificador del turno: es el que se envía al responder
 * @param number posición de la pregunta, de 1 a 5
 * @param text texto original de la pregunta, nunca reformulado
 */
@Schema(description = "Pregunta pendiente de respuesta")
public record PendingQuestionResponse(
        @Schema(format = "uuid", example = "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12") UUID turnId,
        @Schema(minimum = "1", maximum = "5", example = "1") int number,
        @Schema(description = "Texto original de la pregunta", maxLength = 500,
                example = "Cuéntame brevemente quién eres y por qué te interesa este rol.") String text) {
}
