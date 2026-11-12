package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tech.cameia.entrevista.domain.model.TranscriptionFailure;

/** Regla de reintento de la transcripción (HU-5.13). */
class TranscriptionRetryPolicyTest {

    private final TranscriptionRetryPolicy policy = new TranscriptionRetryPolicy();

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
        assertThat(policy.shouldRetry(attempt, failure)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Como máximo dos intentos por envío")
    void maxAttempts_shouldBeTwo() {
        assertThat(TranscriptionRetryPolicy.MAX_ATTEMPTS).isEqualTo(2);
    }
}
