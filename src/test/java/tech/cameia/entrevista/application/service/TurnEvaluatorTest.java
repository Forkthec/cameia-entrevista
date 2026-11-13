package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.service.TurnEvaluator.EvaluationJob;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionConsolidationFailedException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.TurnFeedback;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.TurnFeedbackRepository;

/** Evaluar una respuesta guardada: éxito, fallos de la IA, base caída y sesión que cambia mientras se evalúa. */
class TurnEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");
    private static final String VALID = "{\"accion\":\"AVANZAR\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"m\","
            + "\"follow_up_texto\":\"\",\"feedback\":\"f\"}";

    private final LanguageModelPort model = mock(LanguageModelPort.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final AiExecutionRepository executions = mock(AiExecutionRepository.class);
    private final TurnFeedbackRepository feedbacks = mock(TurnFeedbackRepository.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final ConsolidateSessionService consolidation = mock(ConsolidateSessionService.class);
    private final AtomicBoolean inTransaction = new AtomicBoolean();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private TurnEvaluator evaluator;
    private InterviewSession session;
    private List<InterviewQuestion> planned;
    private InterviewTurn turn;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        Clock earlier = Clock.fixed(NOW.minusSeconds(3600), ZoneOffset.UTC);
        session = InterviewSession.configure(UUID.randomUUID(), OWNER, new SessionConfiguration(UUID.randomUUID(),
                UUID.randomUUID(), null, "{}", Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT,
                InterviewLanguage.SPANISH_COLOMBIA), earlier);
        session.start(earlier);
        UUID execution = UUID.randomUUID();
        planned = new ArrayList<>();
        for (int position = 1; position <= 5; position++) {
            planned.add(new InterviewQuestion(UUID.randomUUID(), session.id(), position, QuestionType.TECNICA,
                    "Pregunta " + position, execution, NOW));
        }
        when(transaction.execute(any())).thenAnswer(call -> {
            inTransaction.set(true);
            try {
                return ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null);
            } finally {
                inTransaction.set(false);
            }
        });
        doAnswer(call -> {
            ((Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(questions.findBySession(session.id())).thenReturn(planned);
        when(executions.failIfInProgress(any())).thenReturn(true);
        when(executions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(turns.save(any())).thenAnswer(call -> call.getArgument(0));
        when(feedbacks.save(any())).thenAnswer(call -> call.getArgument(0));
        evaluator = new TurnEvaluator(new AiCallRunner(model, duration -> { }, Duration.ZERO, Duration.ZERO, clock),
                new EvaluationPromptBuilder(),
                new EvaluationOutputReader(JsonMapper.builder().build()), turns, questions, sessions, executions,
                feedbacks, consolidation, transaction, clock, "modelo", "modelo-de-respaldo");
    }

    private EvaluationJob job(int position) {
        InterviewQuestion question = planned.get(position - 1);
        turn = InterviewTurn.open(question.id(), clock);
        turn.startProcessing(clock);
        when(turns.findById(turn.id())).thenReturn(Optional.of(turn));
        AiExecution mark = AiExecution.evaluationStarted(session.id(), turn.id(), "modelo", "evaluacion.v1",
                "gemini", clock);
        return new EvaluationJob(session, turn, question, "mi respuesta", mark);
    }

    private void modelReturns(String text) {
        when(model.generate(any(), any())).thenReturn(new LanguageModelResponse(text, 10, 5, "modelo"));
    }

    @Test
    @DisplayName("Una evaluación que sale bien al primer intento conserva la marca del intento 1")
    void evaluate_shouldKeepFirstAttemptNumberOne() {
        modelReturns(VALID);
        EvaluationJob job = job(1);

        evaluator.evaluate(job);

        ArgumentCaptor<AiExecution> saved = ArgumentCaptor.forClass(AiExecution.class);
        verify(executions).save(saved.capture());
        assertThat(saved.getValue().attempt()).isEqualTo(1);
        assertThat(saved.getValue().id()).isEqualTo(job.mark().id());
        verify(executions, never()).failIfInProgress(any());
    }

    @Test
    @DisplayName("Cambiar de intento cierra la marca fallida y abre la siguiente en una sola transacción")
    void evaluate_shouldSwitchAttemptInOneTransaction() {
        when(model.generate(any(), any())).thenThrow(new LlmUnavailableException(null))
                .thenReturn(new LanguageModelResponse(VALID, 10, 5, "modelo"));
        EvaluationJob job = job(1);

        evaluator.evaluate(job);

        // Una transacción para cambiar de intento y otra para guardar el resultado.
        verify(transaction, times(2)).execute(any());
        InOrder order = inOrder(executions);
        order.verify(executions).failIfInProgress(any());
        ArgumentCaptor<AiExecution> saved = ArgumentCaptor.forClass(AiExecution.class);
        order.verify(executions, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(0).attempt()).isEqualTo(2);
        assertThat(saved.getAllValues().get(0).turnId()).isEqualTo(job.turn().id());
    }

    @Test
    @DisplayName("Si la marca anterior ya no está en curso al cambiar de intento, el turno no se evalúa más")
    void evaluate_shouldStop_whenMarkVanishesBetweenAttempts() {
        when(model.generate(any(), any())).thenThrow(new LlmUnavailableException(null));
        when(executions.failIfInProgress(any())).thenReturn(false);
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.SessionConflictException.class);

        verify(model, times(1)).generate(any(), any());
        verify(feedbacks, never()).save(any());
    }

    @Test
    @DisplayName("Si la base falla al cambiar de intento, responde base no disponible")
    void evaluate_shouldAnswerDatabaseUnavailable_whenSwitchingAttemptFails() {
        when(model.generate(any(), any())).thenThrow(new LlmUnavailableException(null));
        when(executions.failIfInProgress(any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sin conexión"));
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.DatabaseUnavailableException.class);

        verify(model, times(1)).generate(any(), any());
    }

    @Test
    @DisplayName("Si la marca ya la cerró otra petición al cambiar de intento, no se toca el turno de esa otra petición")
    void evaluate_shouldLeaveTurnAlone_whenMarkClosedByAnotherRequest() {
        when(model.generate(any(), any())).thenThrow(new LlmUnavailableException(null));
        when(executions.failIfInProgress(any())).thenReturn(false);
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.SessionConflictException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.PROCESSING);
        verify(turns, never()).save(any());
    }

    @Test
    @DisplayName("Si la base falla solo al cambiar de intento, la evaluación se cierra y el turno queda en reintento")
    void evaluate_shouldCloseEvaluation_whenOnlySwitchingAttemptFails() {
        when(model.generate(any(), any())).thenThrow(new LlmUnavailableException(null));
        when(executions.failIfInProgress(any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sin conexión"))
                .thenReturn(true);
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.DatabaseUnavailableException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
        assertThat(failedExecution().errorCode()).isEqualTo(ErrorCode.DATABASE_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("Si la sesión cambia justo al guardar el resultado, la ejecución se cierra como fallida")
    void evaluate_shouldCloseExecution_whenSessionConflictsWhileSaving() {
        modelReturns(VALID);
        EvaluationJob job = job(1);
        when(sessions.save(any()))
                .thenThrow(new tech.cameia.entrevista.domain.exception.SessionConflictException());

        assertThatThrownBy(() -> evaluator.evaluate(job))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.SessionConflictException.class);

        // La transacción real se deshace; con los repositorios simulados basta ver que la ejecución se cerró.
        assertThat(failedExecution().errorCode()).isEqualTo(ErrorCode.SESSION_CONFLICT.name());
    }

    @Test
    @DisplayName("Si ni siquiera se puede cerrar la evaluación fallida, igual sube la causa original")
    void evaluate_shouldRethrowOriginalCause_whenClosingTheFailedMarkFails() {
        when(model.generate(any(), any())).thenThrow(new LlmLimitReachedException(null));
        when(executions.failIfInProgress(any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sin conexión"));
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(LlmLimitReachedException.class);

        verify(model, times(1)).generate(any(), any());
    }

    @Test
    @DisplayName("Si los tres intentos fallan, sube la causa del último y su marca queda fallida con ese código")
    void evaluate_shouldFailWithLastCause_whenCycleFails() {
        when(model.generate(any(), any())).thenThrow(new LlmUnavailableException(null))
                .thenThrow(new LlmUnavailableException(null))
                .thenReturn(new LanguageModelResponse("no es json", 10, 5, "modelo"));
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(LlmInvalidOutputException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
        assertThat(failedExecution().attempt()).isEqualTo(3);
        assertThat(failedExecution().errorCode()).isEqualTo("LLM_INVALID_OUTPUT");
        verify(feedbacks, never()).save(any());
    }

    @Test
    @DisplayName("El tercer intento con respaldo guarda su proveedor y modelo")
    void evaluate_shouldStoreFallbackProviderAndModel() {
        when(model.isAvailable(AiProvider.FALLBACK)).thenReturn(true);
        when(model.generate(any(), eq(AiProvider.PRIMARY))).thenThrow(new LlmUnavailableException(null));
        when(model.generate(any(), eq(AiProvider.FALLBACK)))
                .thenReturn(new LanguageModelResponse(VALID, 10, 5, "modelo"));
        EvaluationJob job = job(1);

        evaluator.evaluate(job);

        ArgumentCaptor<AiExecution> saved = ArgumentCaptor.forClass(AiExecution.class);
        verify(executions, atLeastOnce()).save(saved.capture());
        AiExecution third = saved.getAllValues().get(1);
        assertThat(third.attempt()).isEqualTo(3);
        assertThat(third.provider()).isEqualTo("groq");
        assertThat(third.model()).isEqualTo("modelo-de-respaldo");
    }

    private AiExecution failedExecution() {
        ArgumentCaptor<AiExecution> captor = ArgumentCaptor.forClass(AiExecution.class);
        verify(executions, atLeastOnce()).failIfInProgress(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Una evaluación válida guarda el resultado en la clave dentro de la misma transacción")
    void evaluate_shouldRecordKeyResult_insideTheSameTransaction() {
        modelReturns(VALID);
        TurnEvaluator.KeyRecorder recorder = mock(TurnEvaluator.KeyRecorder.class);
        EvaluationJob base = job(2);

        AnswerResult result = evaluator.evaluate(new EvaluationJob(base.session(), base.turn(), base.question(),
                base.answer(), base.mark(), recorder));

        verify(recorder).succeeded(result);
        verify(recorder, never()).failed(any());
    }

    @Test
    @DisplayName("Una evaluación fallida guarda el código del fallo en la clave al dejar el turno en reintento")
    void evaluate_shouldRecordKeyFailure_whenModelFails() {
        when(model.generate(any(), any())).thenThrow(new LlmUnavailableException(null));
        TurnEvaluator.KeyRecorder recorder = mock(TurnEvaluator.KeyRecorder.class);
        EvaluationJob base = job(2);

        assertThatThrownBy(() -> evaluator.evaluate(new EvaluationJob(base.session(), base.turn(), base.question(),
                base.answer(), base.mark(), recorder))).isInstanceOf(LlmUnavailableException.class);

        verify(recorder).failed(ErrorCode.LLM_UNAVAILABLE);
        verify(recorder, never()).succeeded(any());
    }

    @Test
    @DisplayName("Una evaluación sin clave no guarda nada en ella")
    void evaluate_shouldIgnoreKey_whenJobHasNoRecorder() {
        TurnEvaluator.KeyRecorder none = TurnEvaluator.KeyRecorder.NONE;

        none.succeeded(null);
        none.failed(ErrorCode.LLM_UNAVAILABLE);

        assertThat(none).isNotNull();
    }

    @Test
    @DisplayName("Una evaluación válida de la pregunta 2 completa el turno y abre el de la pregunta 3")
    void evaluate_shouldCompleteAndOpenNext_whenValid() {
        modelReturns(VALID);
        EvaluationJob job = job(2);

        AnswerResult result = evaluator.evaluate(job);

        assertThat(result.turnStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(result.currentQuestion()).isEqualTo(3);
        assertThat(result.totalQuestions()).isEqualTo(5);
        assertThat(result.interviewFinished()).isFalse();
        assertThat(result.nextQuestion().text()).isEqualTo("Pregunta 3");
        assertThat(result.nextQuestion().number()).isEqualTo(3);
        assertThat(result.nextQuestion().status()).isEqualTo(TurnStatus.PENDING);
        assertThat(turn.status()).isEqualTo(TurnStatus.COMPLETED);
        ArgumentCaptor<TurnFeedback> feedback = ArgumentCaptor.forClass(TurnFeedback.class);
        verify(feedbacks).save(feedback.capture());
        assertThat(feedback.getValue().turnId()).isEqualTo(job.turn().id());
        ArgumentCaptor<AiExecution> saved = ArgumentCaptor.forClass(AiExecution.class);
        verify(executions).save(saved.capture());
        assertThat(saved.getValue().status().name()).isEqualTo("SUCCEEDED");
        assertThat(session.lastActivityAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("La respuesta de la pregunta 5 termina la entrevista sin abrir una sexta")
    void evaluate_shouldFinishWithoutSixth_whenQuestionFive() {
        modelReturns(VALID);

        AnswerResult result = evaluator.evaluate(job(5));

        assertThat(result.interviewFinished()).isTrue();
        assertThat(result.nextQuestion()).isNull();
        assertThat(result.currentQuestion()).isEqualTo(5);
        ArgumentCaptor<InterviewTurn> saved = ArgumentCaptor.forClass(InterviewTurn.class);
        verify(turns).save(saved.capture());
        assertThat(saved.getAllValues()).hasSize(1);
    }

    @Test
    @DisplayName("Al completar la pregunta 5 la sesión pasa a evaluación en la misma transacción")
    void evaluate_shouldStartEvaluationInSameTransaction_whenFifthCompleted() {
        modelReturns(VALID);
        when(turns.countCompleted(session.id())).thenReturn(5);
        List<Boolean> savedInsideTransaction = new ArrayList<>();
        List<SessionStatus> savedStatus = new ArrayList<>();
        when(sessions.save(any())).thenAnswer(call -> {
            savedInsideTransaction.add(inTransaction.get());
            savedStatus.add(call.<InterviewSession>getArgument(0).status());
            return call.getArgument(0);
        });

        evaluator.evaluate(job(5));

        assertThat(savedInsideTransaction).containsExactly(true);
        assertThat(savedStatus).containsExactly(SessionStatus.EVALUATING);
        assertThat(session.finishedAt()).isNull();
    }

    @Test
    @DisplayName("Al completar la pregunta 5 pide la consolidación una vez, ya fuera de la transacción")
    void evaluate_shouldConsolidateAfterCommit_whenFifthCompleted() {
        modelReturns(VALID);
        when(turns.countCompleted(session.id())).thenReturn(5);
        List<Boolean> insideTransaction = new ArrayList<>();
        doAnswer(call -> {
            insideTransaction.add(inTransaction.get());
            return null;
        }).when(consolidation).consolidate(any(), any());

        evaluator.evaluate(job(5));

        verify(consolidation).consolidate(session.id(), OWNER);
        assertThat(insideTransaction).containsExactly(false);
    }

    @Test
    @DisplayName("Si la consolidación falla, la respuesta sigue siendo válida y la sesión queda evaluándose")
    void evaluate_shouldKeepAnswer_whenConsolidationFails() {
        modelReturns(VALID);
        when(turns.countCompleted(session.id())).thenReturn(5);
        doThrow(new SessionConsolidationFailedException()).when(consolidation).consolidate(any(), any());

        AnswerResult result = evaluator.evaluate(job(5));

        assertThat(result.interviewFinished()).isTrue();
        assertThat(turn.status()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(session.status()).isEqualTo(SessionStatus.EVALUATING);
    }

    @Test
    @DisplayName("Al completar la pregunta 4 la sesión sigue en curso y no se consolida")
    void evaluate_shouldStayInProgress_whenQuestionFourCompleted() {
        modelReturns(VALID);

        evaluator.evaluate(job(4));

        assertThat(session.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(consolidation, never()).consolidate(any(), any());
    }

    @Test
    @DisplayName("Con la pregunta 5 completada pero solo cuatro turnos completos, la sesión sigue en curso")
    void evaluate_shouldStayInProgress_whenCountBelowFive() {
        modelReturns(VALID);
        when(turns.countCompleted(session.id())).thenReturn(4);

        evaluator.evaluate(job(5));

        assertThat(session.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(consolidation, never()).consolidate(any(), any());
    }

    @Test
    @DisplayName("Si el modelo no está disponible, el turno queda en reintento y la ejecución fallida")
    void evaluate_shouldRetry_whenModelUnavailable() {
        when(model.generate(any(), any())).thenThrow(new LlmUnavailableException(null));
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(LlmUnavailableException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
        assertThat(failedExecution().errorCode()).isEqualTo("LLM_UNAVAILABLE");
        verify(feedbacks, never()).save(any());
    }

    @Test
    @DisplayName("Si se agotó la cuota del modelo, el turno queda en reintento con su código")
    void evaluate_shouldRetry_whenLimitReached() {
        when(model.generate(any(), any())).thenThrow(new LlmLimitReachedException(null));
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(LlmLimitReachedException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
        assertThat(failedExecution().errorCode()).isEqualTo("LLM_LIMIT_REACHED");
    }

    @Test
    @DisplayName("Si el modelo devuelve una salida inválida, el turno queda en reintento con su código")
    void evaluate_shouldRetry_whenInvalidOutput() {
        modelReturns("no es json");
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(LlmInvalidOutputException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
        assertThat(failedExecution().errorCode()).isEqualTo("LLM_INVALID_OUTPUT");
    }

    @Test
    @DisplayName("Una falla no clasificada del modelo se trata como modelo no disponible")
    void evaluate_shouldMapUnexpectedFailureToUnavailable() {
        when(model.generate(any(), any())).thenThrow(new IllegalStateException("raro"));
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(LlmUnavailableException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
    }

    @Test
    @DisplayName("Si la base falla al guardar, sube base no disponible y el turno sigue procesando")
    void evaluate_shouldThrowDatabaseUnavailable_whenSaveFails() {
        modelReturns(VALID);
        when(feedbacks.save(any())).thenThrow(new DataAccessResourceFailureException("caída"));
        EvaluationJob job = job(1);

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(DatabaseUnavailableException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.PROCESSING);
        verify(executions, never()).failIfInProgress(any());
    }

    @Test
    @DisplayName("Si la sesión se abandonó mientras se evaluaba, no se guarda nada y la ejecución queda fallida")
    void evaluate_shouldRejectAndCloseExecution_whenSessionAbandonedMeanwhile() {
        modelReturns(VALID);
        EvaluationJob job = job(1);
        Instant activity = session.lastActivityAt();
        session.abandon(clock);

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(SessionAbandonedException.class);

        assertThat(failedExecution().errorCode()).isEqualTo(ErrorCode.SESSION_ABANDONED.name());
        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
        verify(feedbacks, never()).save(any());
        verify(sessions, never()).save(any());
        assertThat(activity).isNotEqualTo(NOW);
        assertThat(session.lastActivityAt()).isEqualTo(activity);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"EVALUATING", "FINISHED"})
    @DisplayName("Si la sesión ya se cerró mientras se evaluaba, la ejecución queda fallida con su código")
    void evaluate_shouldRejectAndCloseExecution_whenSessionClosedMeanwhile(SessionStatus closed) {
        modelReturns(VALID);
        EvaluationJob job = job(1);
        session.startEvaluation(clock);
        if (closed == SessionStatus.FINISHED) {
            session.finish(clock);
        }

        assertThatThrownBy(() -> evaluator.evaluate(job)).isInstanceOf(SessionAlreadyClosedException.class);

        assertThat(failedExecution().errorCode()).isEqualTo(ErrorCode.SESSION_ALREADY_CLOSED.name());
        assertThat(turn.status()).isEqualTo(TurnStatus.RETRYING);
        verify(feedbacks, never()).save(any());
    }
}
