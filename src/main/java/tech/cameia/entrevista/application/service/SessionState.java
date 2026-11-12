package tech.cameia.entrevista.application.service;

import java.util.List;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.AnsweredQuestion;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.TurnStatus;

/**
 * Estado de una sesión tal como lo ve la persona: su estado, el avance y la pregunta pendiente. Nunca lleva
 * retroalimentación.
 *
 * @param sessionId identificador de la sesión
 * @param status estado de la sesión
 * @param answerMode forma de respuesta de la sesión: texto o audio
 * @param currentQuestion número de la pregunta pendiente, de 0 a 5
 * @param pending pregunta pendiente, o {@code null} si la sesión no está en curso o ya se respondieron las cinco
 * @param history preguntas ya respondidas con la respuesta guardada, por posición; vacía si no hay
 */
public record SessionState(UUID sessionId, SessionStatus status, AnswerMode answerMode, int currentQuestion,
        PendingQuestion pending, List<AnsweredQuestion> history) {

    /** Crea el estado sin historial de respuestas. */
    public SessionState(UUID sessionId, SessionStatus status, AnswerMode answerMode, int currentQuestion,
            PendingQuestion pending) {
        this(sessionId, status, answerMode, currentQuestion, pending, List.of());
    }

    /**
     * Pregunta que espera respuesta.
     *
     * @param turnId identificador del turno al que se responde
     * @param number posición de la pregunta, de 1 a 5
     * @param text texto original de la pregunta
     * @param status estado del turno: pendiente, procesando o en reintento
     * @param retriesUsed veces que se usó «Reintentar» en el turno, de 0 a 4
     */
    public record PendingQuestion(UUID turnId, int number, String text, TurnStatus status, int retriesUsed) {
    }

    /**
     * Indica si la entrevista ya terminó de responderse.
     *
     * @return {@code true} si se está evaluando, está finalizada o está en curso con las cinco respondidas
     */
    public boolean interviewFinished() {
        return status == SessionStatus.EVALUATING || status == SessionStatus.FINISHED
                || (status == SessionStatus.IN_PROGRESS && currentQuestion == 5 && pending == null);
    }
}
