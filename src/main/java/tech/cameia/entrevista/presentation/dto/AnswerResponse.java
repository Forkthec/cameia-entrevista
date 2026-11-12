package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.AnswerResult;

/**
 * Respuesta común del registro de una respuesta (texto o voz): el turno respondido, el avance y la pregunta
 * siguiente. Nunca trae retroalimentación ni el texto transcrito.
 *
 * @param turn turno respondido
 * @param progress avance tras responder
 * @param nextQuestion pregunta siguiente, o {@code null} si se respondió la quinta
 * @param interviewFinished si se respondieron las cinco preguntas
 */
@Schema(description = "Resultado de registrar una respuesta")
public record AnswerResponse(
        AnsweredTurn turn,
        ProgressResponse progress,
        @Schema(nullable = true, description = "Pregunta siguiente; null tras la quinta") PendingQuestionResponse nextQuestion,
        @Schema(description = "true si se respondieron las cinco preguntas") boolean interviewFinished) {

    /**
     * Turno respondido.
     *
     * @param turnId identificador del turno
     * @param status estado en que quedó
     */
    @Schema(description = "Turno respondido")
    public record AnsweredTurn(
            @Schema(format = "uuid", example = "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12") UUID turnId,
            @Schema(example = "COMPLETED") String status) {
    }

    /**
     * Convierte el resultado del caso de uso.
     *
     * @param result resultado del registro
     * @return la respuesta del contrato
     */
    public static AnswerResponse from(AnswerResult result) {
        PendingQuestionResponse next = result.nextQuestion() == null ? null
                : new PendingQuestionResponse(result.nextQuestion().turnId(), result.nextQuestion().number(),
                        result.nextQuestion().text());
        return new AnswerResponse(new AnsweredTurn(result.turnId(), result.turnStatus().code()),
                new ProgressResponse(result.currentQuestion(), result.totalQuestions()), next,
                result.interviewFinished());
    }
}
