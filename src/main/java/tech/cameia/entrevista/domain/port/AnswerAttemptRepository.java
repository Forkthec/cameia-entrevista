package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;

/**
 * Puerto de persistencia de las respuestas definitivas ({@code intento_respuesta}).
 */
public interface AnswerAttemptRepository {

    /**
     * Guarda la respuesta definitiva del turno y la escribe en la base en el acto.
     *
     * @param attempt respuesta a guardar
     * @throws TurnTransitionNotAllowedException si el turno ya tenía su respuesta definitiva
     */
    void saveDefinitive(AnswerAttempt attempt);
}
