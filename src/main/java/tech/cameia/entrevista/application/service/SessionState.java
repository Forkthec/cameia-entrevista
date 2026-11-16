package tech.cameia.entrevista.application.service;

import java.util.List;
import java.util.Map;
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
 * @param audioEnabled {@code true} si se puede escuchar la pregunta: la voz está encendida y la sesión en curso
 *        (HU-5.15)
 * @param audioPlaysRemaining usos de audio que le quedan a cada pregunta mostrada, por número; vacío si la voz no está
 *        disponible
 */
public record SessionState(UUID sessionId, SessionStatus status, AnswerMode answerMode, int currentQuestion,
        PendingQuestion pending, List<AnsweredQuestion> history, boolean audioEnabled,
        Map<Integer, Integer> audioPlaysRemaining) {

    /** Copia las colecciones. */
    public SessionState {
        history = List.copyOf(history);
        audioPlaysRemaining = Map.copyOf(audioPlaysRemaining);
    }

    /** Crea el estado sin la voz de las preguntas. */
    public SessionState(UUID sessionId, SessionStatus status, AnswerMode answerMode, int currentQuestion,
            PendingQuestion pending, List<AnsweredQuestion> history) {
        this(sessionId, status, answerMode, currentQuestion, pending, history, false, Map.of());
    }

    /**
     * Devuelve el mismo estado con la voz de las preguntas.
     *
     * @param enabled si se puede escuchar la pregunta
     * @param playsRemaining usos de audio que quedan por número de pregunta
     * @return una copia con los datos de la voz
     */
    public SessionState withAudio(boolean enabled, Map<Integer, Integer> playsRemaining) {
        return new SessionState(sessionId, status, answerMode, currentQuestion, pending, history, enabled,
                playsRemaining);
    }

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
