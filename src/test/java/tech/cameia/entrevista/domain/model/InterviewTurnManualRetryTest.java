package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tech.cameia.entrevista.domain.exception.TurnNotRetryableException;
import tech.cameia.entrevista.domain.exception.TurnRetryLimitReachedException;

/** Uso de «Reintentar» sobre un turno: contador, estados que lo admiten y tope de cuatro. */
class InterviewTurnManualRetryTest {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-12T16:00:00Z");
    private static final Clock LATER_CLOCK = Clock.fixed(LATER, ZoneOffset.UTC);

    private static InterviewTurn inState(TurnStatus status, int manualRetries) {
        return InterviewTurn.reconstitute(new TurnSnapshot(UUID.randomUUID(), UUID.randomUUID(), status,
                manualRetries, NOW, NOW, 1L));
    }

    @Test
    @DisplayName("Un turno nuevo empieza con cero reintentos")
    void open_shouldStartWithZeroManualRetries() {
        assertThat(InterviewTurn.open(UUID.randomUUID(), Clock.fixed(NOW, ZoneOffset.UTC)).manualRetries()).isZero();
    }

    @Test
    @DisplayName("En reintento, usar «Reintentar» suma uno y pasa a procesando")
    void startManualRetry_shouldCount_whenRetrying() {
        InterviewTurn turn = inState(TurnStatus.RETRYING, 1);

        turn.startManualRetry(false, LATER_CLOCK);

        assertThat(turn.status()).isEqualTo(TurnStatus.PROCESSING);
        assertThat(turn.manualRetries()).isEqualTo(2);
        assertThat(turn.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("Procesando con la evaluación caída se acepta como si estuviera en reintento")
    void startManualRetry_shouldAccept_whenProcessingStale() {
        InterviewTurn turn = inState(TurnStatus.PROCESSING, 0);

        turn.startManualRetry(true, LATER_CLOCK);

        assertThat(turn.status()).isEqualTo(TurnStatus.PROCESSING);
        assertThat(turn.manualRetries()).isEqualTo(1);
    }

    @Test
    @DisplayName("Procesando con la evaluación viva no admite reintento y no cambia nada")
    void startManualRetry_shouldReject_whenProcessingLive() {
        InterviewTurn turn = inState(TurnStatus.PROCESSING, 0);

        assertThatThrownBy(() -> turn.startManualRetry(false, LATER_CLOCK))
                .isInstanceOf(TurnNotRetryableException.class);
        assertThat(turn.manualRetries()).isZero();
        assertThat(turn.updatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(value = TurnStatus.class, names = {"PENDING", "COMPLETED"})
    @DisplayName("Un turno pendiente o completado no admite reintento, aunque se marque caído")
    void startManualRetry_shouldReject_whenPendingOrCompleted(TurnStatus status) {
        InterviewTurn turn = inState(status, 0);

        assertThatThrownBy(() -> turn.startManualRetry(true, LATER_CLOCK))
                .isInstanceOf(TurnNotRetryableException.class);
        assertThat(turn.status()).isEqualTo(status);
        assertThat(turn.manualRetries()).isZero();
    }

    @Test
    @DisplayName("Con cuatro reintentos usados, el quinto se rechaza y el contador queda en cuatro")
    void startManualRetry_shouldRejectFifth() {
        InterviewTurn turn = inState(TurnStatus.RETRYING, 0);
        for (int use = 1; use <= InterviewTurn.MAX_MANUAL_RETRIES; use++) {
            turn.startManualRetry(false, LATER_CLOCK);
            turn.retry(LATER_CLOCK);
        }

        assertThatThrownBy(() -> turn.startManualRetry(false, LATER_CLOCK))
                .isInstanceOf(TurnRetryLimitReachedException.class);
        assertThat(turn.manualRetries()).isEqualTo(4);
        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
    }
}
