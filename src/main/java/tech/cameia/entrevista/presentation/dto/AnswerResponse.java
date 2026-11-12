package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.AnswerResult;

/**
 * Respuesta común de registrar una respuesta (escrita o por voz). Nunca trae retroalimentación.
 *
 * @param turn turno respondido
 * @param progress avance tras responder
 * @param nextQuestion pregunta siguiente, o {@code null} si era la última
 * @param interviewFinished {@code true} si ya se respondieron las cinco
 */
@Schema(description = "Resultado de registrar la respuesta de un turno")
public record AnswerResponse(
        TurnResponse turn,
        ProgressResponse progress,
        @Schema(description = "Pregunta siguiente; null si era la última", nullable = true)
        PendingQuestionResponse nextQuestion,
        @Schema(description = "true si ya se respondieron las cinco preguntas") boolean interviewFinished) {

    /**
     * Turno respondido.
     *
     * @param turnId identificador del turno
     * @param status estado del turno: COMPLETED
     */
    @Schema(description = "Turno respondido")
    public record TurnResponse(
            @Schema(format = "uuid", example = "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12") UUID turnId,
            @Schema(example = "COMPLETED") String status) {
    }

    /**
     * Traduce el resultado del caso de uso.
     *
     * @param result resultado
     * @return la respuesta del contrato
     */
    public static AnswerResponse from(AnswerResult result) {
        AnswerResult.NextQuestion next = result.nextQuestion();
        return new AnswerResponse(new TurnResponse(result.turnId(), result.turnStatus().code()),
                new ProgressResponse(result.currentQuestion(), result.totalQuestions()),
                next == null ? null : new PendingQuestionResponse(next.turnId(), next.number(), next.text()),
                result.interviewFinished());
    }
}
