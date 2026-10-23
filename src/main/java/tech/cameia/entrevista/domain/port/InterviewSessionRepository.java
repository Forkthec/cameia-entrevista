package tech.cameia.entrevista.domain.port;

import java.util.Optional;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewSession;

/**
 * Puerto de persistencia de las sesiones de entrevista.
 */
public interface InterviewSessionRepository {

    /**
     * Guarda la sesión, nueva o ya existente, y la escribe en la base en el acto.
     *
     * @param session sesión a guardar; si es nueva su versión es {@code null}
     * @return la sesión tal como quedó guardada, con su versión
     * @throws SessionConflictException si otra escritura la cambió antes
     * @throws SessionAlreadyExistsException si la persona ya tiene otra sesión configurada o en curso
     */
    InterviewSession save(InterviewSession session);

    /**
     * Busca una sesión por su identificador y su dueño; una sesión ajena se trata como inexistente.
     *
     * @param id identificador de la sesión
     * @param owner dueño que la pide
     * @return la sesión, o vacío si no existe o es de otra persona
     */
    Optional<InterviewSession> findByIdAndOwner(UUID id, FirebaseUid owner);

    /**
     * Busca la sesión configurada de una persona; a lo sumo hay una.
     *
     * @param owner dueño de la sesión
     * @return la sesión configurada, o vacío si no tiene
     */
    Optional<InterviewSession> findConfiguredByOwner(FirebaseUid owner);

    /**
     * Busca la sesión en curso de una persona; a lo sumo hay una.
     *
     * @param owner dueño de la sesión
     * @return la sesión en curso, o vacío si no tiene
     */
    Optional<InterviewSession> findInProgressByOwner(FirebaseUid owner);

    /**
     * Cuenta las respuestas definitivas de una sesión: una por pregunta ya respondida.
     *
     * @param sessionId identificador de la sesión
     * @return cantidad de respuestas definitivas; 0 si todavía no hay ninguna
     */
    int countDefinitiveAnswers(UUID sessionId);

    /**
     * Borra la sesión solo si es del dueño y sigue configurada; la base borra con ella sus preguntas y turnos.
     *
     * @param id identificador de la sesión
     * @param owner dueño que la borra
     * @throws SessionTransitionNotAllowedException si la sesión no existe, es de otra persona o ya no está configurada
     */
    void deleteConfigured(UUID id, FirebaseUid owner);
}
