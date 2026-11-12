package tech.cameia.entrevista.application.service;

import java.util.UUID;

import tech.cameia.entrevista.domain.model.TurnStatus;

/**
 * Resultado de registrar una respuesta: el turno respondido, el avance y la pregunta siguiente. Nunca lleva
 * retroalimentación ni el texto de la respuesta.
 *
 * @param turnId turno respondido
 * @param turnStatus estado en que quedó el turno
 * @param currentQuestion número de la pregunta que sigue pendiente (o 5 si la entrevista terminó)
 * @param totalQuestions total de preguntas de la entrevista
 * @param nextQuestion la pregunta siguiente, o {@code null} si se respondió la última
 * @param interviewFinished si se respondieron todas las preguntas
 */
public record AnswerResult(UUID turnId, TurnStatus turnStatus, int currentQuestion, int totalQuestions,
        SessionState.PendingQuestion nextQuestion, boolean interviewFinished) {
}
