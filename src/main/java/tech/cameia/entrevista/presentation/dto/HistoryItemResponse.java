package tech.cameia.entrevista.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pregunta con respuesta definitiva. Nunca lleva retroalimentación, puntaje ni evaluación.
 *
 * @param number posición de la pregunta, de 1 a 5
 * @param text texto de la pregunta
 * @param answerText respuesta definitiva de la persona
 * @param audioPlaysRemaining usos de audio que le quedan a la pregunta; ausente si la voz no está disponible
 */
@Schema(description = "Pregunta con su respuesta definitiva")
public record HistoryItemResponse(int number, String text, String answerText,
        @Schema(description = "Usos de audio que le quedan a la pregunta, de 0 a 3; ausente si audioEnabled es false",
                minimum = "0", maximum = "3", example = "3", nullable = true) Integer audioPlaysRemaining) {

    /**
     * Crea el elemento sin la voz de la pregunta.
     *
     * @param number posición de la pregunta
     * @param text texto de la pregunta
     * @param answerText respuesta definitiva
     */
    public HistoryItemResponse(int number, String text, String answerText) {
        this(number, text, answerText, null);
    }
}
