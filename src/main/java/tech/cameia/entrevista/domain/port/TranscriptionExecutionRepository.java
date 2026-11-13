package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.model.TranscriptionExecution;

/**
 * Puerto de persistencia de las llamadas al servicio de transcripción ({@code ejecucion_ia} con
 * {@code TRANSCRIBIR}).
 */
public interface TranscriptionExecutionRepository {

    /**
     * Guarda la ejecución y la escribe en la base en el acto, en su propia transacción.
     *
     * @param execution ejecución ya terminada
     */
    void save(TranscriptionExecution execution);
}
