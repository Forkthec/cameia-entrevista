package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tech.cameia.entrevista.domain.model.TranscriptionFailure;

/** Regla del reintento automático de la transcripción (HU-5.13). */
class TranscriptionRetryPolicyTest {

    private final TranscriptionRetryPolicy policy = new TranscriptionRetryPolicy(Duration.ofSeconds(60),
            Duration.ofSeconds(1), Duration.ofSeconds(110));

    @ParameterizedTest(name = "intento {0}, causa {1} → reintenta: {2}")
    @CsvSource({
        "1, TRANSIENT, true",
        "2, TRANSIENT, false",
        "1, TIMEOUT, false",
        "2, TIMEOUT, false",
        "1, REJECTED, false",
        "2, REJECTED, false"
    })
    @DisplayName("Solo reintenta un error transitorio del primer intento (CA-5.13.1 y CA-5.13.3)")
    void shouldRetry_onlyTransientFirstAttempt(int attempt, TranscriptionFailure failure, boolean expected) {
        assertThat(policy.shouldRetry(attempt, failure, Duration.ofSeconds(2))).isEqualTo(expected);
    }

    @Test
    @DisplayName("No reintenta si un intento completo más la pausa ya no caben en el presupuesto total")
    void shouldRetry_shouldRespectTheTotalBudget() {
        assertThat(policy.shouldRetry(1, TranscriptionFailure.TRANSIENT, Duration.ofSeconds(49))).isTrue();
        assertThat(policy.shouldRetry(1, TranscriptionFailure.TRANSIENT, Duration.ofSeconds(50))).isFalse();
    }

    @Test
    @DisplayName("Como máximo dos intentos; la duración máxima no supera el presupuesto")
    void maxAttemptsAndDuration() {
        assertThat(TranscriptionRetryPolicy.MAX_ATTEMPTS).isEqualTo(2);
        assertThat(policy.maxDuration()).isEqualTo(Duration.ofSeconds(110));
        assertThat(new TranscriptionRetryPolicy(Duration.ofSeconds(5), Duration.ofSeconds(1), Duration.ofSeconds(110))
                .maxDuration()).isEqualTo(Duration.ofSeconds(11));
        assertThat(policy.pause()).isEqualTo(Duration.ofSeconds(1));
    }
}
