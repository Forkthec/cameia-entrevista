package tech.cameia.entrevista.application.service;

import java.util.UUID;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.SessionStatus;

/**
 * Estado de una sesión tal como lo ve la persona: su estado, el avance y la pregunta pendiente. Nunca lleva
 * retroalimentación.
 *
 * @param sessionId identificador de la sesión
 * @param status estado de la sesión
 * @param answerMode forma de respuesta de la sesión: texto o audio
 * @param currentQuestion número de la pregunta pendiente, de 0 a 5
 * @param pending pregunta pendiente, o {@code null} si la sesión no está en curso
 */
public record SessionState(UUID sessionId, SessionStatus status, AnswerMode answerMode, int currentQuestion,
        PendingQuestion pending) {

    /**
     * Pregunta que espera respuesta.
     *
     * @param turnId identificador del turno al que se responde
     * @param number posición de la pregunta, de 1 a 5
     * @param text texto original de la pregunta
     */
    public record PendingQuestion(UUID turnId, int number, String text) {
    }

    /**
     * Indica si la entrevista ya terminó de responderse.
     *
     * @return {@code true} si se está evaluando o está finalizada
     */
    public boolean interviewFinished() {
        return status == SessionStatus.EVALUATING || status == SessionStatus.FINISHED;
    }
}
