package tech.cameia.entrevista.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.ReportOutcome.QuestionRow;
import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;

/**
 * Una pregunta de la sesión con su respuesta definitiva y, si fue por voz, sus datos de voz.
 *
 * @param number número de la pregunta, de 1 a 5
 * @param type tipo de la pregunta
 * @param questionText texto con que se le mostró a la persona
 * @param answerText respuesta definitiva tal como se guardó, o {@code null} si no hay
 * @param answerOrigin {@code TEXT}, {@code VOICE} o {@code null} si no hay respuesta
 * @param voice datos de voz; {@code null} si la respuesta fue escrita o no los tiene
 */
@Schema(description = "Una pregunta de la sesión con su respuesta")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReportQuestionResponse(
        @Schema(minimum = "1", maximum = "5", example = "2") int number,
        @Schema(allowableValues = {"APERTURA", "TECNICA", "CONDUCTUAL", "SITUACIONAL"}, example = "TECNICA")
        String type,
        @Schema(example = "¿Qué diferencia hay entre una transacción optimista y una pesimista?")
        String questionText,
        @Schema(description = "Respuesta definitiva tal como se guardó; null si no hay", nullable = true,
                example = "Eh, pues las dos sirven para lo mismo.") String answerText,
        @Schema(allowableValues = {"TEXT", "VOICE"}, nullable = true, example = "VOICE") String answerOrigin,
        @Schema(description = "Datos de voz; null si fue escrita o no hay datos", nullable = true)
        AnswerVoiceResponse voice) {

    /**
     * Traduce la pregunta del caso de uso al contrato.
     *
     * @param row pregunta con su respuesta
     * @return el DTO
     */
    static ReportQuestionResponse from(QuestionRow row) {
        return new ReportQuestionResponse(row.number(), row.type().code(), row.questionText(), row.answerText(),
                originOf(row.origin()), AnswerVoiceResponse.from(row.voice()));
    }

    private static String originOf(AnswerOrigin origin) {
        if (origin == null) {
            return null;
        }
        return origin == AnswerOrigin.AUDIO ? "VOICE" : "TEXT";
    }
}
