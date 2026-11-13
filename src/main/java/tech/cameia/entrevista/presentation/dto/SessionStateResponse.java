package tech.cameia.entrevista.presentation.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.SessionState;

/**
 * Estado de una sesión de entrevista: estado, avance, pregunta pendiente e historial de respuestas definitivas.
 * Nunca incluye retroalimentación.
 *
 * @param sessionId identificador de la sesión
 * @param status estado de la sesión
 * @param answerFormat forma de respuesta de la sesión: {@code TEXTO} o {@code AUDIO}
 * @param interviewFinished {@code true} si la entrevista se está evaluando, ya terminó o ya tiene las cinco respuestas
 * @param progress avance
 * @param pendingQuestion pregunta pendiente; {@code null} si la sesión no está en curso
 * @param history preguntas con respuesta guardada, en orden; vacía mientras no haya respuestas
 */
@Schema(description = "Estado de la sesión de entrevista")
public record SessionStateResponse(
        @Schema(format = "uuid", example = "3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10") UUID sessionId,
        @Schema(description = "Estado de la sesión",
                allowableValues = {"CONFIGURADA", "EN_CURSO", "EVALUANDO", "FINALIZADA", "ABANDONADA"},
                example = "EN_CURSO") String status,
        @Schema(description = "Forma de respuesta elegida al configurar la sesión; no cambia durante la entrevista",
                allowableValues = {"TEXTO", "AUDIO"}, example = "TEXTO") String answerFormat,
        @Schema(description = "true si la entrevista está en EVALUANDO o FINALIZADA, o está EN_CURSO con las cinco preguntas"
                + " respondidas", example = "false")
        boolean interviewFinished,
        ProgressResponse progress,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "Pregunta pendiente; null si la sesión no está EN_CURSO", nullable = true)
        PendingQuestionResponse pendingQuestion,
        @Schema(description = "Preguntas con respuesta guardada, en orden, también la que espera reintento; nunca trae"
                + " retroalimentación")
        List<HistoryItemResponse> history) {

    /** Total de preguntas de toda entrevista. */
    private static final int TOTAL_QUESTIONS = 5;

    /**
     * Traduce el estado del caso de uso al contrato.
     *
     * @param state estado de la sesión
     * @return la respuesta para el cliente
     */
    public static SessionStateResponse from(SessionState state) {
        SessionState.PendingQuestion pending = state.pending();
        return new SessionStateResponse(state.sessionId(), state.status().code(), state.answerMode().code(),
                state.interviewFinished(),
                new ProgressResponse(state.currentQuestion(), TOTAL_QUESTIONS),
                pending == null ? null : new PendingQuestionResponse(pending.turnId(), pending.number(), pending.text(),
                        pending.status().code(), pending.retriesUsed()),
                state.history().stream().map(item -> new HistoryItemResponse(item.number(), item.question(),
                        item.answer())).toList());
    }
}
