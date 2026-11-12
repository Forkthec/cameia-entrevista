package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.service.TurnEvaluator.EvaluationJob;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.exception.TurnNotRetryableException;
import tech.cameia.entrevista.domain.exception.TurnRetryLimitReachedException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.TurnSnapshot;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

/** Reintentar la evaluación: contador, caducidad, carreras y estados de sesión. */
class ReprocessTurnServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final AnswerAttemptRepository answers = mock(AnswerAttemptRepository.class);
    private final AiExecutionRepository executions = mock(AiExecutionRepository.class);
    private final TurnEvaluator evaluator = mock(TurnEvaluator.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);

    private InterviewSession session;
    private InterviewQuestion question;
    private InterviewTurn turn;
    private ReprocessTurnService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        session = newSession();
        question = new InterviewQuestion(UUID.randomUUID(), session.id(), 1, QuestionType.TECNICA, "Pregunta 1",
                UUID.randomUUID(), NOW);
        turn = turn(TurnStatus.RETRYING, 0);
        when(transaction.execute(any())).thenAnswer(call ->
                ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
        doAnswer(call -> {
            ((Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        when(questions.findBySession(session.id())).thenReturn(List.of(question));
        when(answers.findDefinitive(any())).thenReturn(Optional.of(AnswerAttempt.written(UUID.randomUUID(),
                "Mi respuesta", "Pregunta 1", CLOCK)));
        when(executions.findEvaluationInProgress(any())).thenReturn(Optional.empty());
        when(executions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(turns.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new ReprocessTurnService(sessions, turns, questions, answers, executions, evaluator, transaction,
                CLOCK, "modelo", Duration.ofSeconds(20));
        wire(turn);
    }

    private void wire(InterviewTurn current) {
        turn = current;
        when(turns.findByIdAndSession(turn.id(), session.id())).thenReturn(Optional.of(turn));
        when(turns.findById(turn.id())).thenReturn(Optional.of(turn));
        when(evaluator.evaluate(any())).thenAnswer(call -> new AnswerResult(turn.id(), TurnStatus.COMPLETED, 2, 5,
                null, false));
    }

    private InterviewSession newSession() {
        Clock earlier = Clock.fixed(NOW.minusSeconds(3600), ZoneOffset.UTC);
        InterviewSession created = InterviewSession.configure(UUID.randomUUID(), OWNER, new SessionConfiguration(
                UUID.randomUUID(), UUID.randomUUID(), null, "{}", Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT,
                InterviewLanguage.SPANISH_COLOMBIA), earlier);
        created.start(earlier);
        return created;
    }

    private InterviewTurn turn(TurnStatus status, int retries) {
        return InterviewTurn.reconstitute(new TurnSnapshot(UUID.randomUUID(), question.id(), status, retries, NOW,
                NOW, 0L));
    }

    private AnswerResult reprocess() {
        return service.reprocess(new SessionCommand(session.id(), OWNER), turn.id().toString());
    }

    @Test
    @DisplayName("Un reintento con evaluación válida completa el turno y cuenta un uso")
    void reprocess_shouldCompleteTurn_whenEvaluationSucceeds() {
        AnswerResult result = reprocess();

        assertThat(result.turnStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(turn.manualRetries()).isEqualTo(1);
        ArgumentCaptor<EvaluationJob> job = ArgumentCaptor.forClass(EvaluationJob.class);
        verify(evaluator).evaluate(job.capture());
        assertThat(job.getValue().answer()).isEqualTo("Mi respuesta");
        assertThat(job.getValue().mark().turnId()).isEqualTo(turn.id());
    }

    @Test
    @DisplayName("Si el modelo vuelve a fallar, el reintento queda contado y el error sube")
    void reprocess_shouldStayRetrying_whenModelFails() {
        when(evaluator.evaluate(any())).thenThrow(new LlmUnavailableException(null));

        assertThatThrownBy(this::reprocess).isInstanceOf(LlmUnavailableException.class);

        assertThat(turn.manualRetries()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un turno pendiente, procesando vivo o completado no se puede reintentar")
    void reprocess_shouldRejectNotRetryable() {
        for (TurnStatus status : List.of(TurnStatus.PENDING, TurnStatus.PROCESSING, TurnStatus.COMPLETED)) {
            wire(turn(status, 0));
            when(executions.findEvaluationInProgress(turn.id())).thenReturn(Optional.of(
                    AiExecution.evaluationStarted(session.id(), turn.id(), "m", "v", "g", CLOCK)));
            assertThatThrownBy(this::reprocess).isInstanceOf(TurnNotRetryableException.class);
        }
        verify(evaluator, never()).evaluate(any());
    }

    @Test
    @DisplayName("Con los cuatro reintentos usados responde límite alcanzado")
    void reprocess_shouldRejectLimit_whenFour() {
        wire(turn(TurnStatus.RETRYING, 4));

        assertThatThrownBy(this::reprocess).isInstanceOf(TurnRetryLimitReachedException.class);
    }

    @Test
    @DisplayName("Un turno procesando con la evaluación caída se recupera: cierra la marca y evalúa")
    void reprocess_shouldRecoverStaleProcessing() {
        wire(turn(TurnStatus.PROCESSING, 0));
        Clock old = Clock.fixed(NOW.minusSeconds(36), ZoneOffset.UTC);
        AiExecution dead = AiExecution.evaluationStarted(session.id(), turn.id(), "m", "v", "g", old);
        when(executions.findEvaluationInProgress(turn.id())).thenReturn(Optional.of(dead));
        when(executions.failIfInProgress(any())).thenReturn(true);

        reprocess();

        ArgumentCaptor<AiExecution> closed = ArgumentCaptor.forClass(AiExecution.class);
        verify(executions).failIfInProgress(closed.capture());
        assertThat(closed.getValue().id()).isEqualTo(dead.id());
        assertThat(closed.getValue().errorCode()).isEqualTo("LLM_UNAVAILABLE");
        verify(evaluator).evaluate(any());
    }

    @Test
    @DisplayName("Una carrera por la versión del turno se responde como no reintentable")
    void reprocess_shouldMapRaceToNotRetryable() {
        when(turns.save(any())).thenThrow(new SessionConflictException());

        assertThatThrownBy(this::reprocess).isInstanceOf(TurnNotRetryableException.class);
    }

    @Test
    @DisplayName("Un identificador mal escrito o de otra sesión responde turno no encontrado")
    void reprocess_shouldThrowTurnNotFound_whenMalformedOrForeign() {
        SessionCommand command = new SessionCommand(session.id(), OWNER);

        assertThatThrownBy(() -> service.reprocess(command, "abc")).isInstanceOf(TurnNotFoundException.class);
        assertThatThrownBy(() -> service.reprocess(command, UUID.randomUUID().toString()))
                .isInstanceOf(TurnNotFoundException.class);
    }

    @Test
    @DisplayName("Una sesión ajena responde sesión no encontrada")
    void reprocess_shouldThrowSessionNotFound() {
        assertThatThrownBy(() -> service.reprocess(new SessionCommand(session.id(), new FirebaseUid("otra")),
                turn.id().toString())).isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("Una sesión abandonada, cerrada o sin iniciar rechaza el reintento por su estado")
    void reprocess_shouldThrowByStatus() {
        session.abandon(CLOCK);
        assertThatThrownBy(this::reprocess).isInstanceOf(SessionAbandonedException.class);

        session = newSession();
        session.startEvaluation(CLOCK);
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        SessionCommand evaluating = new SessionCommand(session.id(), OWNER);
        assertThatThrownBy(() -> service.reprocess(evaluating, turn.id().toString()))
                .isInstanceOf(SessionAlreadyClosedException.class);

        session.finish(CLOCK);
        assertThatThrownBy(() -> service.reprocess(evaluating, turn.id().toString()))
                .isInstanceOf(SessionAlreadyClosedException.class);

        InterviewSession configured = InterviewSession.configure(UUID.randomUUID(), OWNER, session.configuration(),
                CLOCK);
        when(sessions.findByIdAndOwner(configured.id(), OWNER)).thenReturn(Optional.of(configured));
        assertThatThrownBy(() -> service.reprocess(new SessionCommand(configured.id(), OWNER),
                turn.id().toString())).isInstanceOf(SessionTransitionNotAllowedException.class);
    }

    @Test
    @DisplayName("Reintentar no registra actividad antes de evaluar")
    void reprocess_shouldNotRegisterActivityBeforeEvaluating() {
        Instant before = session.lastActivityAt();
        when(evaluator.evaluate(any())).thenAnswer(call -> {
            assertThat(session.lastActivityAt()).isEqualTo(before);
            return new AnswerResult(turn.id(), TurnStatus.COMPLETED, 2, 5, null, false);
        });

        reprocess();

        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("Un conflicto de turno al reservar se responde como no reintentable")
    void reprocess_shouldMapTurnConflictToNotRetryable() {
        when(turns.save(any())).thenThrow(new TurnConflictException());

        assertThatThrownBy(this::reprocess).isInstanceOf(TurnNotRetryableException.class);
    }

    @Test
    @DisplayName("Si la base falla al reservar el reintento responde base no disponible y no llama al modelo")
    void reprocess_shouldThrowDatabaseUnavailable_whenReservationFails() {
        when(turns.save(any())).thenThrow(new DataAccessResourceFailureException("caída"));

        assertThatThrownBy(this::reprocess).isInstanceOf(DatabaseUnavailableException.class);

        verify(evaluator, never()).evaluate(any());
    }

    @Test
    @DisplayName("Un turno procesando sin marca de evaluación se recupera sin cerrar ninguna marca")
    void reprocess_shouldRecover_whenProcessingWithoutMark() {
        wire(turn(TurnStatus.PROCESSING, 0));

        reprocess();

        verify(executions, never()).failIfInProgress(any());
        verify(evaluator).evaluate(any());
    }

    @Test
    @DisplayName("Un turno que desaparece entre la lectura y la reserva responde turno no encontrado")
    void reprocess_shouldThrowTurnNotFound_whenTurnVanishesDuringReservation() {
        when(turns.findById(turn.id())).thenReturn(Optional.empty());

        assertThatThrownBy(this::reprocess).isInstanceOf(TurnNotFoundException.class);
    }

    @Test
    @DisplayName("Un turno en reintento sin respuesta guardada es un fallo interno de datos")
    void reprocess_shouldFail_whenNoStoredAnswer() {
        when(answers.findDefinitive(any())).thenReturn(Optional.empty());

        assertThatThrownBy(this::reprocess).isInstanceOf(IllegalStateException.class);
    }
}
