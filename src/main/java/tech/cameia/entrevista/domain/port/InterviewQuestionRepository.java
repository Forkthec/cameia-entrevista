package tech.cameia.entrevista.domain.port;

import java.util.List;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.InterviewQuestion;

/**
 * Puerto de persistencia de las preguntas de una sesión.
 */
public interface InterviewQuestionRepository {

    /**
     * Guarda las preguntas y las escribe en la base en el acto.
     *
     * @param questions preguntas nuevas, ya validadas
     */
    void saveAll(List<InterviewQuestion> questions);

    /**
     * Lista las preguntas de una sesión en orden de posición.
     *
     * @param sessionId identificador de la sesión
     * @return las preguntas por posición ascendente; vacía si la sesión no tiene
     */
    List<InterviewQuestion> findBySession(UUID sessionId);
}
