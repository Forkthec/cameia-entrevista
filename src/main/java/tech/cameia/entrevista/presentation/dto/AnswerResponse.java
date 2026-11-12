package tech.cameia.entrevista.presentation.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.AnswerResult;

/**
 * Respuesta común de registrar una respuesta (escrita o por voz): el turno respondido, el avance y la siguiente
 * pregunta. Nunca lleva retroalimentación. Es la forma que ya consume el Cliente Web.
 *
 * @param turn turno respondido y su estado
 * @param progress avance: la pregunta que sigue y el total
 * @param nextQuestion pregunta que sigue, o {@code null} tras la quinta
 * @param interviewFinished {@code true} cuando ya se respondieron las 5 preguntas
 */
@Schema(description = "Resultado de registrar una respuesta; nunca trae retroalimentación")
public record AnswerResponse(
        TurnResponse turn,
        ProgressResponse progress,
        @Schema(nullable = true, description = "Pregunta que sigue; null tras responder la quinta")
        PendingQuestionResponse nextQuestion,
        @Schema(description = "true cuando ya se respondieron las 5 preguntas", example = "false")
        boolean interviewFinished) {

    /**
     * Turno respondido.
     *
     * @param turnId identificador del turno
     * @param status estado en que quedó
     */
    @Schema(description = "Turno respondido")
    public record TurnResponse(
            @Schema(format = "uuid", example = "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12") UUID turnId,
            @Schema(description = "Estado del turno", example = "COMPLETED") String status) {
    }

    /**
     * Convierte el resultado del caso de uso.
     *
     * @param result resultado de registrar la respuesta
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
