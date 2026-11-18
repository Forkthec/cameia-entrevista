package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pregunta que sigue tras responder.
 *
 * @param turnId turno nuevo, al que se responde a continuación
 * @param number posición de la pregunta, de 2 a 5
 * @param text texto original de la pregunta, nunca reformulado
 */
@Schema(description = "Pregunta siguiente")
public record NextQuestionResponse(
        @Schema(format = "uuid", example = "5d0c7a1e-3b9f-4c28-a6d4-8e1f2b3c4d5e") UUID turnId,
        @Schema(minimum = "2", maximum = "5", example = "2") int number,
        @Schema(description = "Texto original de la pregunta", maxLength = 500,
                example = "Háblame de un conflicto con un compañero y cómo lo resolviste.") String text) {
}
