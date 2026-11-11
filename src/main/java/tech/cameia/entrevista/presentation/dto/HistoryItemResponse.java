package tech.cameia.entrevista.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pregunta con respuesta definitiva. Nunca lleva retroalimentación, puntaje ni evaluación.
 *
 * @param number posición de la pregunta, de 1 a 5
 * @param text texto de la pregunta
 * @param answerText respuesta definitiva de la persona
 */
@Schema(description = "Pregunta con su respuesta definitiva")
public record HistoryItemResponse(int number, String text, String answerText) {
}
