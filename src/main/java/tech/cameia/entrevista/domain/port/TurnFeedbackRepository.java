package tech.cameia.entrevista.domain.port;

import java.util.UUID;

import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.model.TurnFeedback;

/**
 * Puerto de persistencia de la retroalimentación de un turno ({@code feedback_turno}).
 */
public interface TurnFeedbackRepository {

    /**
     * Guarda la retroalimentación y la escribe en la base en el acto.
     *
     * @param feedback retroalimentación a guardar
     * @return la retroalimentación guardada
     * @throws TurnConflictException si el turno ya tenía retroalimentación
     */
    TurnFeedback save(TurnFeedback feedback);

    /**
     * Cuenta las retroalimentaciones de los turnos de una sesión.
     *
     * @param sessionId sesión consultada
     * @return cantidad de turnos de la sesión que ya tienen retroalimentación
     */
    int countBySession(UUID sessionId);
}
