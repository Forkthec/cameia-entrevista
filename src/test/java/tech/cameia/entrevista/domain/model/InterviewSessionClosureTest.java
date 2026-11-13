package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Regla de cierre: la sesión pasa a evaluación solo con los cinco turnos completos. */
class InterviewSessionClosureTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T15:05:00Z"), ZoneOffset.UTC);

    private static InterviewSession inState(SessionStatus status) {
        SessionConfiguration configuration = new SessionConfiguration(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                null, "{}", Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT, InterviewLanguage.ENGLISH);
        Instant start = Instant.parse("2026-11-12T14:00:00Z");
        return InterviewSession.reconstitute(new SessionSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000009"), new FirebaseUid("uid-a"), configuration,
                status, 1, start, start, null, null, 3L));
    }

    @Test
    @DisplayName("Con los cinco turnos completos una sesión en curso pasa a evaluación")
    void closeIfComplete_shouldStartEvaluation_whenFive() {
        InterviewSession session = inState(SessionStatus.IN_PROGRESS);

        boolean closed = session.closeIfComplete(5, CLOCK);

        assertThat(closed).isTrue();
        assertThat(session.status()).isEqualTo(SessionStatus.EVALUATING);
        assertThat(session.finishedAt()).isNull();
    }

    @Test
    @DisplayName("Con menos de cinco turnos completos la sesión sigue en curso")
    void closeIfComplete_shouldStay_whenFour() {
        InterviewSession session = inState(SessionStatus.IN_PROGRESS);

        boolean closed = session.closeIfComplete(4, CLOCK);

        assertThat(closed).isFalse();
        assertThat(session.status()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"CONFIGURED", "EVALUATING", "FINISHED", "ABANDONED"})
    @DisplayName("Fuera de «en curso» la regla no cambia nada, ni con cinco turnos completos")
    void closeIfComplete_shouldIgnore_whenNotInProgress(SessionStatus status) {
        InterviewSession session = inState(status);

        boolean closed = session.closeIfComplete(5, CLOCK);

        assertThat(closed).isFalse();
        assertThat(session.status()).isEqualTo(status);
    }
}
