package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.model.AnswerAttempt;

/**
 * Puerto de persistencia de las respuestas de los turnos ({@code intento_respuesta}).
 */
public interface AnswerAttemptRepository {

    /**
     * Inserta la respuesta y la escribe en la base en el acto. Un segundo intento definitivo del mismo turno lo
     * rechaza el índice único de la base.
     *
     * @param attempt respuesta nueva
     */
    void insert(AnswerAttempt attempt);
}
