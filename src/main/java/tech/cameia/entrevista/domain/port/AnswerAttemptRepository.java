package tech.cameia.entrevista.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.AnswerAlreadyExistsException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnsweredQuestion;
import tech.cameia.entrevista.domain.model.ReportQuestion;

/**
 * Puerto de persistencia de las respuestas de la persona ({@code intento_respuesta}), que solo se insertan.
 */
public interface AnswerAttemptRepository {

    /**
     * Inserta la respuesta definitiva del turno y la escribe en la base en el acto.
     *
     * @param attempt respuesta a guardar
     * @return la respuesta guardada
     * @throws AnswerAlreadyExistsException si el turno ya tenía su respuesta definitiva
     */
    AnswerAttempt save(AnswerAttempt attempt);

    /**
     * Busca la respuesta definitiva de un turno.
     *
     * @param turnId identificador del turno
     * @return la respuesta, o vacío si no hay
     */
    Optional<AnswerAttempt> findDefinitive(UUID turnId);

    /**
     * Lista las preguntas respondidas de una sesión con su respuesta definitiva, por posición.
     *
     * @param sessionId identificador de la sesión
     * @return el historial; vacío si no hay respuestas
     */
    List<AnsweredQuestion> historyOf(UUID sessionId);

    /**
     * Lista lo que el reporte necesita de cada pregunta que tiene respuesta definitiva: número, tipo, texto de la
     * pregunta, texto y origen de la respuesta y, si fue por voz, sus datos de voz. Las preguntas sin respuesta no
     * aparecen.
     *
     * @param sessionId identificador de la sesión
     * @return las preguntas respondidas por número; vacío si no hay respuestas
     */
    List<ReportQuestion> reportInputsOf(UUID sessionId);
}
