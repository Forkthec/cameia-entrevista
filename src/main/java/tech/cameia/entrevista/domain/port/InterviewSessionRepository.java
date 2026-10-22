package tech.cameia.entrevista.domain.port;

import java.util.Optional;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
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
     * Busca una sesión por su identificador.
     *
     * @param id identificador de la sesión
     * @return la sesión, o vacío si no existe
     */
    Optional<InterviewSession> findById(UUID id);

    /**
     * Borra la sesión solo si sigue configurada; la base borra con ella sus preguntas y turnos.
     *
     * @param id identificador de la sesión
     * @throws SessionTransitionNotAllowedException si la sesión no existe o ya no está configurada
     */
    void deleteConfigured(UUID id);
}
