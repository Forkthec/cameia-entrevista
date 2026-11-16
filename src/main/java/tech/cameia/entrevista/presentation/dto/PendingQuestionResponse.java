package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pregunta que espera respuesta.
 *
 * @param turnId identificador del turno: es el que se envía al responder
 * @param number posición de la pregunta, de 1 a 5
 * @param text texto original de la pregunta, nunca reformulado
 * @param status estado del turno: pendiente, procesando o en reintento
 * @param retriesUsed veces que se usó «Reintentar» en el turno, de 0 a 4
 * @param audioPlaysRemaining usos de audio que le quedan a la pregunta; ausente si la voz no está disponible
 */
@Schema(description = "Pregunta pendiente de respuesta")
public record PendingQuestionResponse(
        @Schema(format = "uuid", example = "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12") UUID turnId,
        @Schema(minimum = "1", maximum = "5", example = "1") int number,
        @Schema(description = "Texto original de la pregunta", maxLength = 500,
                example = "Cuéntame brevemente quién eres y por qué te interesa este rol.") String text,
        @Schema(description = "Estado del turno. RETRYING: la evaluación falló o quedó colgada y se puede reintentar",
                allowableValues = {"PENDING", "PROCESSING", "RETRYING"}, example = "PENDING") String status,
        @Schema(description = "Reintentos usados en este turno, de 0 a 4", minimum = "0", maximum = "4",
                example = "0") int retriesUsed,
        @Schema(description = "Usos de audio que le quedan a la pregunta, de 0 a 3 (3 en una pregunta nueva); ausente"
                + " si audioEnabled es false", minimum = "0", maximum = "3", example = "3", nullable = true)
        Integer audioPlaysRemaining) {

    /**
     * Crea la pregunta sin la voz.
     *
     * @param turnId identificador del turno
     * @param number posición de la pregunta
     * @param text texto original
     * @param status estado del turno
     * @param retriesUsed reintentos usados
     */
    public PendingQuestionResponse(UUID turnId, int number, String text, String status, int retriesUsed) {
        this(turnId, number, text, status, retriesUsed, null);
    }
}
