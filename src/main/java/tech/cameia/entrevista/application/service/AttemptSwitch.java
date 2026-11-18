package tech.cameia.entrevista.application.service;

import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiProvider;

/**
 * Cambia de intento dentro de un ciclo: cierra el que falló y abre el siguiente en una sola transacción del caso de
 * uso, de modo que la operación nunca queda sin una ejecución en curso.
 */
@FunctionalInterface
public interface AttemptSwitch {

    /**
     * Cierra el intento fallido y abre el siguiente.
     *
     * @param failed intento que falló, ya marcado como fallido con su causa
     * @param attempt número del intento que empieza (2 o 3)
     * @param provider proveedor del intento que empieza
     * @return la ejecución nueva, ya guardada como en curso
     */
    AiExecution next(AiExecution failed, int attempt, AiProvider provider);
}
