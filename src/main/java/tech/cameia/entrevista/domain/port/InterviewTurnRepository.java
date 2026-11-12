package tech.cameia.entrevista.domain.port;

import java.util.Optional;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.model.InterviewTurn;

/**
 * Puerto de persistencia de los turnos de una sesión.
 */
public interface InterviewTurnRepository {

    /**
     * Guarda el turno y lo escribe en la base en el acto.
     *
     * @param turn turno a guardar
     * @return el turno tal como quedó guardado, con su versión
     * @throws SessionConflictException si otra escritura lo cambió antes
     */
    InterviewTurn save(InterviewTurn turn);

    /**
     * Busca un turno por su identificador.
     *
     * @param id identificador del turno
     * @return el turno, o vacío si no existe
     */
    Optional<InterviewTurn> findById(UUID id);

    /**
     * Busca el turno abierto de una sesión: el de la primera pregunta cuyo turno no está completado.
     *
     * @param sessionId identificador de la sesión
     * @return el turno abierto, o vacío si la sesión no tiene turnos o todos están completados
     */
    Optional<InterviewTurn> findOpenBySession(UUID sessionId);

    /**
     * Busca un turno por su identificador solo si pertenece a la sesión.
     *
     * @param turnId identificador del turno
     * @param sessionId identificador de la sesión
     * @return el turno, o vacío si no existe o es de otra sesión
     */
    Optional<InterviewTurn> findByIdAndSession(UUID turnId, UUID sessionId);

    /**
     * Cuenta los turnos completados de una sesión.
     *
     * @param sessionId identificador de la sesión
     * @return cantidad de turnos completados
     */
    int countCompleted(UUID sessionId);
}
