package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.model.AnswerAttempt;

/**
 * Puerto de persistencia de las respuestas de la persona ({@code intento_respuesta}), que solo se insertan.
 */
public interface AnswerAttemptRepository {

    /**
     * Inserta la respuesta definitiva del turno y la escribe en la base en el acto.
     *
     * @param attempt respuesta a guardar
     * @return la respuesta guardada
     */
    AnswerAttempt save(AnswerAttempt attempt);
}
