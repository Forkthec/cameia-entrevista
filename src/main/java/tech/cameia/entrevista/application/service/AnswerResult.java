package tech.cameia.entrevista.application.service;

import java.util.UUID;

import tech.cameia.entrevista.domain.model.TurnStatus;

/**
 * Resultado de responder una pregunta: el turno ya completado y el avance de la entrevista. Nunca lleva la
 * retroalimentación ni la evaluación.
 *
 * @param turnId turno que se respondió
 * @param turnStatus estado del turno tras evaluar la respuesta
 * @param currentQuestion número de la pregunta que sigue, de 1 a 5 (5 si la entrevista terminó de responderse)
 * @param totalQuestions total de preguntas de la entrevista
 * @param nextQuestion pregunta siguiente, o {@code null} si la que se respondió era la última
 * @param interviewFinished {@code true} si ya se respondieron las cinco preguntas
 */
public record AnswerResult(UUID turnId, TurnStatus turnStatus, int currentQuestion, int totalQuestions,
        SessionState.PendingQuestion nextQuestion, boolean interviewFinished) {
}
