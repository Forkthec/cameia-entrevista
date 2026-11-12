package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.AnsweredQuestion;
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
import tech.cameia.entrevista.domain.model.TurnSnapshot;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

/** Estado de la sesión en cada situación, incluidas las poco comunes. */
class GetSessionStateServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final AnswerAttemptRepository answers = mock(AnswerAttemptRepository.class);
    private final AiExecutionRepository executions = mock(AiExecutionRepository.class);
    private final GetSessionStateService service = new GetSessionStateService(sessions, questions, turns, answers,
            executions, CLOCK, Duration.ofSeconds(20));

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

    private InterviewQuestion questionOne(InterviewSession session) {
        InterviewQuestion one = new InterviewQuestion(UUID.randomUUID(), session.id(), 1, QuestionType.TECNICA,
                "Uno", UUID.randomUUID(), CLOCK.instant());
        when(questions.findBySession(session.id())).thenReturn(List.of(one));
        return one;
    }

    private InterviewTurn openTurn(InterviewSession session, InterviewQuestion question, TurnStatus status,
            int retries) {
        InterviewTurn turn = InterviewTurn.reconstitute(new TurnSnapshot(UUID.randomUUID(), question.id(), status,
                retries, CLOCK.instant(), CLOCK.instant(), 0L));
        when(turns.findOpenBySession(session.id())).thenReturn(Optional.of(turn));
        return turn;
    }

    @Test
    void state_shouldMarkFinished_whenInProgressWithFiveCompletedTurns() {
        InterviewSession session = inProgress();
        when(questions.findBySession(session.id())).thenReturn(List.of());
        when(turns.findOpenBySession(session.id())).thenReturn(Optional.empty());
        when(turns.countCompleted(session.id())).thenReturn(5);

        SessionState state = service.stateOf(session);

        assertThat(state.currentQuestion()).isEqualTo(5);
        assertThat(state.pending()).isNull();
        assertThat(state.interviewFinished()).isTrue();
    }

    @Test
    void state_shouldShowRetrying_whenProcessingStale() {
        InterviewSession session = inProgress();
        InterviewTurn turn = openTurn(session, questionOne(session), TurnStatus.PROCESSING, 0);
        Clock old = Clock.fixed(CLOCK.instant().minusSeconds(36), ZoneOffset.UTC);
        when(executions.findEvaluationInProgress(turn.id())).thenReturn(Optional.of(
                AiExecution.evaluationStarted(session.id(), turn.id(), "m", "v", "g", old)));

        assertThat(service.stateOf(session).pending().status()).isEqualTo(TurnStatus.RETRYING);
    }

    @Test
    void state_shouldShowRetrying_whenProcessingWithoutLiveExecution() {
        InterviewSession session = inProgress();
        InterviewTurn turn = openTurn(session, questionOne(session), TurnStatus.PROCESSING, 0);
        when(executions.findEvaluationInProgress(turn.id())).thenReturn(Optional.empty());

        assertThat(service.stateOf(session).pending().status()).isEqualTo(TurnStatus.RETRYING);
    }

    @Test
    void state_shouldShowProcessing_whenLive() {
        InterviewSession session = inProgress();
        InterviewTurn turn = openTurn(session, questionOne(session), TurnStatus.PROCESSING, 0);
        when(executions.findEvaluationInProgress(turn.id())).thenReturn(Optional.of(
                AiExecution.evaluationStarted(session.id(), turn.id(), "m", "v", "g", CLOCK)));

        assertThat(service.stateOf(session).pending().status()).isEqualTo(TurnStatus.PROCESSING);
    }

    @Test
    void state_shouldShowRetriesUsed() {
        InterviewSession session = inProgress();
        openTurn(session, questionOne(session), TurnStatus.RETRYING, 3);

        SessionState.PendingQuestion pending = service.stateOf(session).pending();

        assertThat(pending.retriesUsed()).isEqualTo(3);
        assertThat(pending.status()).isEqualTo(TurnStatus.RETRYING);
    }

    @Test
    void state_shouldListHistory() {
        InterviewSession session = inProgress();
        openTurn(session, questionOne(session), TurnStatus.PENDING, 0);
        for (int count : new int[] {0, 1, 4, 5}) {
            List<AnsweredQuestion> history = new java.util.ArrayList<>();
            for (int number = 1; number <= count; number++) {
                history.add(new AnsweredQuestion(number, "P" + number, "R" + number));
            }
            when(answers.historyOf(session.id())).thenReturn(history);

            assertThat(service.stateOf(session).history()).isEqualTo(history);
        }
    }

    @Test
    void state_shouldHaveEmptyHistory_whenConfigured() {
        InterviewSession configured = InterviewSession.configure(UUID.randomUUID(), OWNER,
                inProgress().configuration(), CLOCK);

        assertThat(service.stateOf(configured).history()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(answers);
    }
}
