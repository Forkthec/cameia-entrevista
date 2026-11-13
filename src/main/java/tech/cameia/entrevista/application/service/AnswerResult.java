package tech.cameia.entrevista.application.service;

import java.util.UUID;

import tech.cameia.entrevista.domain.model.TurnStatus;

/**
 * Resultado de registrar una respuesta: el turno respondido, el avance y la siguiente pregunta. Nunca lleva
 * retroalimentación (Simulación).
 *
 * @param turnId turno que se respondió
 * @param turnStatus estado en que quedó ese turno
 * @param currentQuestion número de la pregunta que sigue; 5 si ya se respondieron todas
 * @param totalQuestions total de preguntas de la entrevista; siempre 5
 * @param nextQuestion pregunta que sigue, o {@code null} si se respondió la quinta
 * @param interviewFinished {@code true} si ya se respondieron las 5 preguntas
 */
public record AnswerResult(UUID turnId, TurnStatus turnStatus, int currentQuestion, int totalQuestions,
        NextQuestion nextQuestion, boolean interviewFinished) {

    /**
     * Pregunta que sigue.
     *
     * @param turnId turno al que se responde
     * @param number posición de la pregunta, de 2 a 5
     * @param text texto original de la pregunta
     */
    public record NextQuestion(UUID turnId, int number, String text) {
    }
}
