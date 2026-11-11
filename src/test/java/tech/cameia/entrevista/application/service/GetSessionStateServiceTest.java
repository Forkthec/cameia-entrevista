package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionSnapshot;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

/** Estado de la sesión en cada situación, incluidas las poco comunes. */
class GetSessionStateServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final GetSessionStateService service = new GetSessionStateService(sessions, questions, turns);

    private InterviewSession inProgress() {
        InterviewSession session = InterviewSession.configure(UUID.randomUUID(), OWNER, new SessionConfiguration(
                UUID.randomUUID(), UUID.randomUUID(), null, "{}", Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT,
                InterviewLanguage.SPANISH_COLOMBIA), CLOCK);
        session.start(CLOCK);
        return session;
    }

    @Test
    void state_shouldReportAllQuestionsDoneWhenEvaluatingOrFinished() {
        for (SessionStatus status : List.of(SessionStatus.EVALUATING, SessionStatus.FINISHED)) {
            InterviewSession session = inProgress();
            if (status == SessionStatus.EVALUATING) {
                session.startEvaluation(CLOCK);
            } else {
                session.startEvaluation(CLOCK);
                session.finish(CLOCK);
            }
            SessionState state = service.stateOf(session);
            assertThat(state.status()).isEqualTo(status);
            assertThat(state.currentQuestion()).isEqualTo(5);
            assertThat(state.pending()).isNull();
            assertThat(state.interviewFinished()).isTrue();
        }
    }

    @Test
    void state_shouldReportAnswerModeOfTheSessionInEveryStatus() {
        for (AnswerMode mode : AnswerMode.values()) {
            InterviewSession configured = InterviewSession.configure(UUID.randomUUID(), OWNER,
                    new SessionConfiguration(UUID.randomUUID(), UUID.randomUUID(), null, "{}", Modality.SIMULATION,
                            Tone.WARM, mode, InterviewLanguage.SPANISH_COLOMBIA), CLOCK);
            assertThat(service.stateOf(configured).answerMode()).isEqualTo(mode);

            InterviewSession running = InterviewSession.reconstitute(new SessionSnapshot(configured.id(), OWNER,
                    configured.configuration(), SessionStatus.IN_PROGRESS, 0, CLOCK.instant(), CLOCK.instant(), null,
                    null, 1L));
            when(questions.findBySession(running.id())).thenReturn(List.of());
            when(turns.findOpenBySession(running.id())).thenReturn(Optional.empty());
            assertThat(service.stateOf(running).answerMode()).isEqualTo(mode);

            running.abandon(CLOCK);
            assertThat(service.stateOf(running).answerMode()).isEqualTo(mode);
        }
    }

    @Test
    void state_shouldNotBeFinishedWhileInProgress() {
        assertThat(new SessionState(UUID.randomUUID(), SessionStatus.IN_PROGRESS, AnswerMode.TEXT, 1, null).interviewFinished())
                .isFalse();
    }

    @Test
    void state_shouldReturnLastQuestionWithoutPending_whenNoTurnIsOpen() {
        InterviewSession session = inProgress();
        when(questions.findBySession(session.id())).thenReturn(List.of());
        when(turns.findOpenBySession(session.id())).thenReturn(Optional.empty());

        SessionState state = service.stateOf(session);

        assertThat(state.currentQuestion()).isEqualTo(5);
        assertThat(state.pending()).isNull();
    }

    @Test
    void state_shouldReturnLastQuestionWithoutPending_whenOpenTurnHasNoMatchingQuestion() {
        InterviewSession session = inProgress();
        InterviewQuestion other = new InterviewQuestion(UUID.randomUUID(), session.id(), 2, QuestionType.TECNICA,
                "Dos", UUID.randomUUID(), CLOCK.instant());
        when(questions.findBySession(session.id())).thenReturn(List.of(other));
        when(turns.findOpenBySession(session.id())).thenReturn(Optional.of(InterviewTurn.open(UUID.randomUUID(), CLOCK)));

        assertThat(service.stateOf(session).pending()).isNull();
    }

    @Test
    void state_shouldFindOwnedSessionByCommand() {
        InterviewSession session = inProgress();
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        when(questions.findBySession(session.id())).thenReturn(List.of());
        when(turns.findOpenBySession(session.id())).thenReturn(Optional.empty());

        assertThat(service.state(new SessionCommand(session.id(), OWNER)).sessionId()).isEqualTo(session.id());
    }
}
