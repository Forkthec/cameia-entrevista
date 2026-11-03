package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;

class InterviewTurnTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-20T16:00:00Z");
    private static final Clock LATER_CLOCK = Clock.fixed(LATER, ZoneOffset.UTC);
    private static final UUID QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private static InterviewTurn inState(TurnStatus status) {
        return InterviewTurn.reconstitute(new TurnSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000006"), QUESTION_ID, status, NOW, NOW, 2L));
    }

    @Test
    @DisplayName("reconstitute conserva todos los datos, incluida la versión")
    void reconstitute_shouldKeepEveryField() {
        InterviewTurn turn = inState(TurnStatus.PENDING);

        assertThat(turn.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000006"));
        assertThat(turn.questionId()).isEqualTo(QUESTION_ID);
        assertThat(turn.status()).isEqualTo(TurnStatus.PENDING);
        assertThat(turn.createdAt()).isEqualTo(NOW);
        assertThat(turn.updatedAt()).isEqualTo(NOW);
        assertThat(turn.version()).isEqualTo(2L);
    }

    @Test
    @DisplayName("startProcessing pasa de PENDING a PROCESSING")
    void startProcessing_shouldMoveToProcessing_whenPending() {
        InterviewTurn turn = inState(TurnStatus.PENDING);

        turn.startProcessing(LATER_CLOCK);

        assertThat(turn.status()).isEqualTo(TurnStatus.PROCESSING);
        assertThat(turn.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("complete pasa de PROCESSING a COMPLETED")
    void complete_shouldMoveToCompleted_whenProcessing() {
        InterviewTurn turn = inState(TurnStatus.PROCESSING);

        turn.complete(LATER_CLOCK);

        assertThat(turn.status()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(turn.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("retry pasa de PROCESSING a RETRYING")
    void retry_shouldMoveToRetrying_whenProcessing() {
        InterviewTurn turn = inState(TurnStatus.PROCESSING);

        turn.retry(LATER_CLOCK);

        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
    }

    @Test
    @DisplayName("resumeProcessing pasa de RETRYING a PROCESSING")
    void resumeProcessing_shouldMoveToProcessing_whenRetrying() {
        InterviewTurn turn = inState(TurnStatus.RETRYING);

        turn.resumeProcessing(LATER_CLOCK);

        assertThat(turn.status()).isEqualTo(TurnStatus.PROCESSING);
    }

    @Test
    @DisplayName("startProcessing en un turno ya procesándose lanza la excepción y no cambia nada")
    void startProcessing_shouldThrow_whenProcessing() {
        assertRejectedWithoutChange(TurnStatus.PROCESSING, turn -> turn.startProcessing(LATER_CLOCK));
    }

    @Test
    @DisplayName("complete en un turno en reintento lanza la excepción y no cambia nada")
    void complete_shouldThrow_whenRetrying() {
        assertRejectedWithoutChange(TurnStatus.RETRYING, turn -> turn.complete(LATER_CLOCK));
    }

    @Test
    @DisplayName("retry en un turno pendiente lanza la excepción y no cambia nada")
    void retry_shouldThrow_whenPending() {
        assertRejectedWithoutChange(TurnStatus.PENDING, turn -> turn.retry(LATER_CLOCK));
    }

    @Test
    @DisplayName("resumeProcessing en un turno completado lanza la excepción y no cambia nada")
    void resumeProcessing_shouldThrow_whenCompleted() {
        assertRejectedWithoutChange(TurnStatus.COMPLETED, turn -> turn.resumeProcessing(LATER_CLOCK));
    }

    private static void assertRejectedWithoutChange(TurnStatus initial, Consumer<InterviewTurn> action) {
        InterviewTurn turn = inState(initial);

        assertThatThrownBy(() -> action.accept(turn))
                .isInstanceOf(TurnTransitionNotAllowedException.class)
                .hasMessage("Esta pregunta ya no admite esa acción.");

        assertThat(turn.status()).isEqualTo(initial);
        assertThat(turn.updatedAt()).isEqualTo(NOW);
    }
}
