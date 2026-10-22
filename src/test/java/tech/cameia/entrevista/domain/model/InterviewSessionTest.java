package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;

class InterviewSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-20T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Clock LATER_CLOCK = Clock.fixed(LATER, ZoneOffset.UTC);

    private static final SessionConfiguration CONFIGURATION = new SessionConfiguration(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            null, "{}", Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT, InterviewLanguage.ENGLISH);

    private static InterviewSession inState(SessionStatus status) {
        return InterviewSession.reconstitute(new SessionSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000009"), new FirebaseUid("uid-a"), CONFIGURATION,
                status, 1, NOW, NOW, null, null, 3L));
    }

    @Test
    @DisplayName("configure crea la sesión configurada, sin reintentos, sin versión y con las fechas del reloj")
    void configure_shouldStartConfigured() {
        UUID id = UUID.randomUUID();

        InterviewSession session = InterviewSession.configure(id, new FirebaseUid("uid-a"), CONFIGURATION, CLOCK);

        assertThat(session.id()).isEqualTo(id);
        assertThat(session.owner()).isEqualTo(new FirebaseUid("uid-a"));
        assertThat(session.configuration()).isEqualTo(CONFIGURATION);
        assertThat(session.status()).isEqualTo(SessionStatus.CONFIGURED);
        assertThat(session.planningRetries()).isZero();
        assertThat(session.createdAt()).isEqualTo(NOW);
        assertThat(session.updatedAt()).isEqualTo(NOW);
        assertThat(session.lastActivityAt()).isNull();
        assertThat(session.finishedAt()).isNull();
        assertThat(session.version()).isNull();
    }

    @Test
    @DisplayName("reconstitute conserva todos los datos, incluida la versión, sin validar nada")
    void reconstitute_shouldKeepEveryField() {
        InterviewSession session = InterviewSession.reconstitute(new SessionSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000009"), new FirebaseUid("uid-a"), CONFIGURATION,
                SessionStatus.FINISHED, 2, NOW, LATER, NOW, LATER, 7L));

        assertThat(session.status()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session.planningRetries()).isEqualTo(2);
        assertThat(session.updatedAt()).isEqualTo(LATER);
        assertThat(session.lastActivityAt()).isEqualTo(NOW);
        assertThat(session.finishedAt()).isEqualTo(LATER);
        assertThat(session.version()).isEqualTo(7L);
    }

    @Test
    @DisplayName("start pasa a EN_CURSO y marca la última actividad con el reloj")
    void start_shouldMoveToInProgress_andTouchActivity() {
        InterviewSession session = inState(SessionStatus.CONFIGURED);

        session.start(LATER_CLOCK);

        assertThat(session.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.lastActivityAt()).isEqualTo(LATER);
        assertThat(session.updatedAt()).isEqualTo(LATER);
        assertThat(session.finishedAt()).isNull();
    }

    @Test
    @DisplayName("abandon pasa a ABANDONADA y registra la fecha de cierre")
    void abandon_shouldSetFinishedAt() {
        InterviewSession session = inState(SessionStatus.IN_PROGRESS);

        session.abandon(LATER_CLOCK);

        assertThat(session.status()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(session.finishedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("startEvaluation pasa a EVALUANDO sin fecha de cierre")
    void startEvaluation_shouldMoveToEvaluating() {
        InterviewSession session = inState(SessionStatus.IN_PROGRESS);

        session.startEvaluation(LATER_CLOCK);

        assertThat(session.status()).isEqualTo(SessionStatus.EVALUATING);
        assertThat(session.updatedAt()).isEqualTo(LATER);
        assertThat(session.finishedAt()).isNull();
    }

    @Test
    @DisplayName("finish pasa a FINALIZADA y registra la fecha de cierre")
    void finish_shouldSetFinishedAt() {
        InterviewSession session = inState(SessionStatus.EVALUATING);

        session.finish(LATER_CLOCK);

        assertThat(session.status()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session.finishedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("start en una sesión en curso lanza la excepción y no cambia nada")
    void start_shouldThrowTransitionNotAllowed_whenInProgress() {
        assertRejectedWithoutChange(SessionStatus.IN_PROGRESS, session -> session.start(LATER_CLOCK));
    }

    @Test
    @DisplayName("abandon en una sesión configurada lanza la excepción y no cambia nada")
    void abandon_shouldThrowTransitionNotAllowed_whenConfigured() {
        assertRejectedWithoutChange(SessionStatus.CONFIGURED, session -> session.abandon(LATER_CLOCK));
    }

    @Test
    @DisplayName("finish en una sesión en curso lanza la excepción y no cambia nada")
    void finish_shouldThrowTransitionNotAllowed_whenInProgress() {
        assertRejectedWithoutChange(SessionStatus.IN_PROGRESS, session -> session.finish(LATER_CLOCK));
    }

    @Test
    @DisplayName("startEvaluation en una sesión finalizada lanza la excepción y no cambia nada")
    void startEvaluation_shouldThrowTransitionNotAllowed_whenFinished() {
        assertRejectedWithoutChange(SessionStatus.FINISHED, session -> session.startEvaluation(LATER_CLOCK));
    }

    private static void assertRejectedWithoutChange(SessionStatus initial, Consumer<InterviewSession> action) {
        InterviewSession session = inState(initial);

        assertThatThrownBy(() -> action.accept(session)).isInstanceOf(SessionTransitionNotAllowedException.class);

        assertThat(session.status()).isEqualTo(initial);
        assertThat(session.updatedAt()).isEqualTo(NOW);
        assertThat(session.lastActivityAt()).isNull();
        assertThat(session.finishedAt()).isNull();
    }

    @Test
    @DisplayName("requireDeletable deja pasar a la sesión configurada")
    void requireDeletable_shouldPass_whenConfigured() {
        assertThatCode(() -> inState(SessionStatus.CONFIGURED).requireDeletable()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = "CONFIGURED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("requireDeletable lanza la excepción en los otros cuatro estados")
    void requireDeletable_shouldThrow_whenNotConfigured(SessionStatus status) {
        assertThatThrownBy(() -> inState(status).requireDeletable())
                .isInstanceOf(SessionTransitionNotAllowedException.class)
                .hasMessage("Esta entrevista ya no admite esa acción.");
    }

    @Test
    @DisplayName("El largo máximo de la oferta laboral es 3000")
    void maxJobOfferLength_shouldBe3000() {
        assertThat(InterviewSession.MAX_JOB_OFFER_LENGTH).isEqualTo(3000);
    }
}
