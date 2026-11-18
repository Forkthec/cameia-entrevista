package tech.cameia.entrevista.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Avance de la entrevista.
 *
 * @param currentQuestion número de la pregunta pendiente: 0 si la sesión está configurada, 5 si ya se respondieron
 *        todas, y en una sesión abandonada, el de la pregunta en que se quedó
 * @param totalQuestions total de preguntas de la entrevista; siempre 5
 */
@Schema(description = "Avance de la entrevista")
public record ProgressResponse(
        @Schema(description = "Número de la pregunta pendiente, de 0 a 5", minimum = "0", maximum = "5", example = "3")
        int currentQuestion,
        @Schema(description = "Total de preguntas; siempre 5", example = "5") int totalQuestions) {
}
