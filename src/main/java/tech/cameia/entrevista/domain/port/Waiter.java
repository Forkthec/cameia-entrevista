package tech.cameia.entrevista.domain.port;

import java.time.Duration;

/**
 * Puerto de espera: permite pausar el hilo sin que las pruebas tengan que dormir.
 */
public interface Waiter {

    /**
     * Espera el tiempo dado; si el hilo se interrumpe, restaura la marca de interrupción y vuelve.
     *
     * @param duration tiempo de espera
     */
    void pause(Duration duration);
}
