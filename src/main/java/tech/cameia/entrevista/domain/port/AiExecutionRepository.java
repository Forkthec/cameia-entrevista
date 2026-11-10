package tech.cameia.entrevista.domain.port;

import java.util.Optional;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.model.AiExecution;

/**
 * Puerto de persistencia de las ejecuciones de la IA de la planificación.
 */
public interface AiExecutionRepository {

    /**
     * Guarda la ejecución, nueva o ya existente, y la escribe en la base en el acto.
     *
     * @param execution ejecución a guardar
     * @return la ejecución guardada
     * @throws SessionConflictException si la sesión ya tiene otra planificación en curso
     */
    AiExecution save(AiExecution execution);

    /**
     * Busca la planificación en curso de una sesión; a lo sumo hay una.
     *
     * @param sessionId identificador de la sesión
     * @return la ejecución en curso, o vacío si no hay
     */
    Optional<AiExecution> findPlanningInProgress(UUID sessionId);

    /**
     * Indica si la sesión tiene algún intento de planificación fallido.
     *
     * @param sessionId identificador de la sesión
     * @return {@code true} si hay al menos una ejecución de planificación fallida
     */
    boolean hasFailedPlanning(UUID sessionId);

    /**
     * Borra las ejecuciones de planificación de la sesión; se usa al reconfigurarla, que reinicia sus intentos.
     *
     * @param sessionId identificador de la sesión
     */
    void deletePlanningOf(UUID sessionId);
}
