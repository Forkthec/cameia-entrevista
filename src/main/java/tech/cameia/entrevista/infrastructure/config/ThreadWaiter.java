package tech.cameia.entrevista.infrastructure.config;

import java.time.Duration;

import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.port.Waiter;

/**
 * Espera real del hilo actual. Si el hilo se interrumpe, restaura la marca de interrupción y vuelve de inmediato.
 */
@Component
class ThreadWaiter implements Waiter {

    @Override
    public void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
