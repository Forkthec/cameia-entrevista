package tech.cameia.entrevista.application.service;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.model.AiExecution;

/**
 * Señal interna de que el ciclo terminó sin un resultado válido: ningún intento lo dio, no se pudo cambiar de
 * intento o el hilo se interrumpió.
 *
 * <p>No es una excepción de negocio y nunca sale de la capa de aplicación: quien llama al ciclo la desenvuelve,
 * cierra la ejecución que quedó en curso con su estado de negocio y relanza la causa.</p>
 */
final class AiCycleFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient BusinessException failure;

    private final transient AiExecution lastMark;

    /**
     * Crea la señal.
     *
     * @param failure causa del último intento
     * @param lastMark ejecución del último intento, aún en curso
     */
    AiCycleFailedException(BusinessException failure, AiExecution lastMark) {
        super(failure);
        this.failure = failure;
        this.lastMark = lastMark;
    }

    /**
     * Causa del último intento.
     *
     * @return la excepción de negocio que debe llegar a la persona
     */
    BusinessException cause() {
        return failure;
    }

    /**
     * Ejecución del último intento.
     *
     * @return la ejecución aún en curso, que cierra quien llama
     */
    AiExecution lastMark() {
        return lastMark;
    }
}
