package tech.cameia.entrevista.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.AnswerResult;

/**
 * Resultado de responder una pregunta: el turno completado y el avance. Nunca incluye retroalimentación,
 * clasificación, motivo, puntajes ni la evaluación de la respuesta.
 *
 * @param turn turno respondido
 * @param progress avance de la entrevista
 * @param nextQuestion pregunta siguiente; {@code null} si la respondida era la última
 * @param interviewFinished {@code true} si ya se respondieron las cinco preguntas
 */
@Schema(description = "Resultado de responder: turno completado y avance. Nunca trae retroalimentación")
public record AnswerResultResponse(
        TurnResponse turn,
        ProgressResponse progress,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "Pregunta siguiente; null si era la última", nullable = true)
        NextQuestionResponse nextQuestion,
        @Schema(description = "true si ya se respondieron las cinco preguntas", example = "false")
        boolean interviewFinished) {

    /**
     * Traduce el resultado del caso de uso al contrato.
     *
     * @param result resultado de responder
     * @return la respuesta para el cliente
     */
    public static AnswerResultResponse from(AnswerResult result) {
        var next = result.nextQuestion();
        return new AnswerResultResponse(new TurnResponse(result.turnId(), result.turnStatus().code()),
                new ProgressResponse(result.currentQuestion(), result.totalQuestions()),
                next == null ? null : new NextQuestionResponse(next.turnId(), next.number(), next.text()),
                result.interviewFinished());
    }
}
