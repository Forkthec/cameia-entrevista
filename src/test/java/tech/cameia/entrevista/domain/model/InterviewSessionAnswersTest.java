package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;

/** Reglas de la sesión al recibir una respuesta: qué estados la admiten y cómo se anota la actividad. */
class InterviewSessionAnswersTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneOffset.UTC);

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
    @DisplayName("Una sesión en curso admite respuestas")
    void requireAcceptsAnswers_shouldPass_whenInProgress() {
        assertThatCode(() -> inState(SessionStatus.IN_PROGRESS).requireAcceptsAnswers()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una sesión abandonada responde SESSION_ABANDONED")
    void requireAcceptsAnswers_shouldThrowAbandoned() {
        assertThatThrownBy(() -> inState(SessionStatus.ABANDONED).requireAcceptsAnswers())
                .isInstanceOf(SessionAbandonedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"EVALUATING", "FINISHED"})
    @DisplayName("Una sesión evaluándose o finalizada responde SESSION_ALREADY_CLOSED")
    void requireAcceptsAnswers_shouldThrowClosed(SessionStatus status) {
        assertThatThrownBy(() -> inState(status).requireAcceptsAnswers())
                .isInstanceOf(SessionAlreadyClosedException.class);
    }

    @Test
    @DisplayName("Una sesión sin empezar no admite respuestas")
    void requireAcceptsAnswers_shouldThrow_whenConfigured() {
        assertThatThrownBy(() -> inState(SessionStatus.CONFIGURED).requireAcceptsAnswers())
                .isInstanceOf(SessionTransitionNotAllowedException.class);
    }

    @Test
    @DisplayName("Anotar actividad fija la última actividad y la modificación al instante del reloj")
    void registerActivity_shouldSetLastActivity() {
        InterviewSession session = inState(SessionStatus.IN_PROGRESS);

        session.registerActivity(CLOCK);

        assertThat(session.lastActivityAt()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z"));
        assertThat(session.updatedAt()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z"));
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"CONFIGURED", "EVALUATING", "FINISHED", "ABANDONED"})
    @DisplayName("Fuera de «en curso» no se anota actividad y la sesión no cambia")
    void registerActivity_shouldRejectOutsideInProgress(SessionStatus status) {
        InterviewSession session = inState(status);
        Instant before = session.updatedAt();

        assertThatThrownBy(() -> session.registerActivity(CLOCK))
                .isInstanceOf(SessionTransitionNotAllowedException.class);
        assertThat(session.updatedAt()).isEqualTo(before);
    }
}
