package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Esperas e intentos del ciclo de consolidación. */
class ConsolidationRetryPolicyTest {

    private final ConsolidationRetryPolicy policy = new ConsolidationRetryPolicy();

    @Test
    @DisplayName("Antes del segundo intento se esperan 200 ms y antes del tercero 400 ms")
    void waitBefore_shouldGrow() {
        assertThat(policy.waitBefore(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.waitBefore(3)).isEqualTo(Duration.ofMillis(400));
    }

    @Test
    @DisplayName("El ciclo tiene tres intentos")
    void maxAttempts_shouldBeThree() {
        assertThat(ConsolidationRetryPolicy.MAX_ATTEMPTS).isEqualTo(3);
    }
}
