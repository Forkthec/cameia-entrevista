package tech.cameia.entrevista.application.service;

import java.util.UUID;

import tech.cameia.entrevista.domain.model.TurnStatus;

/**
 * Resultado de registrar una respuesta: el turno respondido, el avance y la pregunta siguiente. Nunca trae
 * retroalimentación.
 *
 * @param turnId turno respondido
 * @param turnStatus estado del turno tras registrar la respuesta
 * @param currentQuestion número de la pregunta pendiente tras responder; 5 si ya no quedan
 * @param totalQuestions total de preguntas; siempre 5
 * @param nextQuestion pregunta siguiente, o {@code null} si era la última
 * @param interviewFinished {@code true} si se respondieron las cinco
 */
public record AnswerResult(UUID turnId, TurnStatus turnStatus, int currentQuestion, int totalQuestions,
        NextQuestion nextQuestion, boolean interviewFinished) {

    /**
     * Pregunta que sigue.
     *
     * @param turnId turno abierto para ella
     * @param number posición, de 1 a 5
     * @param text texto original
     */
    public record NextQuestion(UUID turnId, int number, String text) {
    }
}
