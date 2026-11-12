package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.policy.AiRetryPolicy.Attempt;

/** Elección del proveedor de cada intento del ciclo. */
class AiRetryPolicyTest {

    private static final Attempt PRIMARY_FAILED = new Attempt(AiProvider.PRIMARY, false);
    private static final Attempt PRIMARY_LIMITED = new Attempt(AiProvider.PRIMARY, true);
    private static final Attempt FALLBACK_FAILED = new Attempt(AiProvider.FALLBACK, false);
    private static final Attempt FALLBACK_LIMITED = new Attempt(AiProvider.FALLBACK, true);

    private final AiRetryPolicy policy = new AiRetryPolicy();

    @Test
    @DisplayName("El primer intento va al principal")
    void next_shouldReturnPrimary_whenEmpty() {
        assertThat(policy.next(List.of(), true)).contains(AiProvider.PRIMARY);
    }

    @Test
    @DisplayName("El segundo intento vuelve al principal si falló sin límite")
    void next_shouldReturnPrimary_afterPrimaryFailure() {
        assertThat(policy.next(List.of(PRIMARY_FAILED), true)).contains(AiProvider.PRIMARY);
    }

    @Test
    @DisplayName("El tercer intento va al respaldo")
    void next_shouldReturnFallback_afterTwoPrimaryFailures() {
        assertThat(policy.next(List.of(PRIMARY_FAILED, PRIMARY_FAILED), true)).contains(AiProvider.FALLBACK);
    }

    @Test
    @DisplayName("El tercer intento vuelve al principal si el respaldo no tiene llave")
    void next_shouldReturnPrimary_whenFallbackUnavailable() {
        assertThat(policy.next(List.of(PRIMARY_FAILED, PRIMARY_FAILED), false)).contains(AiProvider.PRIMARY);
    }

    @Test
    @DisplayName("Con límite del principal el segundo intento pasa al respaldo")
    void next_shouldSkipPrimary_whenPrimaryLimited() {
        assertThat(policy.next(List.of(PRIMARY_LIMITED), true)).contains(AiProvider.FALLBACK);
    }

    @Test
    @DisplayName("Con límite del principal y sin respaldo el ciclo termina")
    void next_shouldEnd_whenPrimaryLimitedAndNoFallback() {
        assertThat(policy.next(List.of(PRIMARY_LIMITED), false)).isEmpty();
    }

    @Test
    @DisplayName("Con límite del principal y del respaldo el ciclo termina")
    void next_shouldEnd_whenBothLimited() {
        assertThat(policy.next(List.of(PRIMARY_LIMITED, FALLBACK_LIMITED), true)).isEmpty();
    }

    @Test
    @DisplayName("Con el respaldo limitado el tercer intento vuelve al principal si este no está limitado")
    void next_shouldReturnPrimary_whenFallbackLimited() {
        assertThat(policy.next(List.of(PRIMARY_FAILED, FALLBACK_LIMITED), true)).contains(AiProvider.PRIMARY);
    }

    @Test
    @DisplayName("Tras fallar el respaldo sin límite el segundo intento sigue al principal")
    void next_shouldReturnPrimary_afterFallbackFailure() {
        assertThat(policy.next(List.of(FALLBACK_FAILED), true)).contains(AiProvider.PRIMARY);
    }

    @Test
    @DisplayName("Con el principal limitado y el respaldo fallido sin límite se usa el respaldo")
    void next_shouldReturnFallback_whenPrimaryLimitedAndFallbackFailed() {
        assertThat(policy.next(List.of(PRIMARY_LIMITED, FALLBACK_FAILED), true)).contains(AiProvider.FALLBACK);
    }

    @Test
    @DisplayName("Después de tres intentos no hay más")
    void next_shouldEnd_afterThree() {
        Optional<AiProvider> result = policy.next(List.of(PRIMARY_FAILED, PRIMARY_FAILED, FALLBACK_FAILED), true);
        assertThat(result).isEmpty();
        assertThat(AiRetryPolicy.MAX_ATTEMPTS).isEqualTo(3);
    }
}
