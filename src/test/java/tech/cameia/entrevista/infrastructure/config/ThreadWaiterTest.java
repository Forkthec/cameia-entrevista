package tech.cameia.entrevista.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Espera real del hilo: dura lo pedido y, si se interrumpe, vuelve de inmediato conservando la marca. */
class ThreadWaiterTest {

    private final ThreadWaiter waiter = new ThreadWaiter();

    @Test
    @DisplayName("Espera al menos el tiempo pedido")
    void pause_shouldWaitGivenTime() {
        long start = System.nanoTime();

        waiter.pause(Duration.ofMillis(50));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("Con el hilo interrumpido vuelve de inmediato y conserva la marca de interrupción")
    void pause_shouldReturnAtOnceAndKeepFlag_whenInterrupted() {
        Thread.currentThread().interrupt();
        long start = System.nanoTime();
        try {
            waiter.pause(Duration.ofSeconds(10));

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
