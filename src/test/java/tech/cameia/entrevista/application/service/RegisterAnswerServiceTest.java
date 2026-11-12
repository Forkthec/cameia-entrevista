package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.command.AnswerCommand;
import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.AnswerAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.model.ValidatedAnswerRequest;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.Waiter;

/** Registrar la respuesta: validación, reserva, evaluación, repeticiones con la misma clave y fallos. */
class RegisterAnswerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");
    private static final String ANSWER = "Mi respuesta";
    private static final String KEY = "key-1";

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final AnswerAttemptRepository answers = mock(AnswerAttemptRepository.class);
    private final AiExecutionRepository executions = mock(AiExecutionRepository.class);
    private final TurnEvaluator evaluator = mock(TurnEvaluator.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final Map<String, IdempotencyKey> store = new HashMap<>();
    private final AtomicInteger pauses = new AtomicInteger();
    private final MutableClock clock = new MutableClock(NOW);
    private Consumer<Integer> onPause = count -> { };

    private InterviewSession session;
    private InterviewQuestion question;
    private InterviewTurn turn;
    private RegisterAnswerService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        session = newSession();
        question = new InterviewQuestion(UUID.randomUUID(), session.id(), 2, QuestionType.TECNICA, "Pregunta 2",
                UUID.randomUUID(), NOW);
        turn = InterviewTurn.open(question.id(), clock);
        when(transaction.execute(any())).thenAnswer(call ->
                ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
        doAnswer(call -> {
            ((Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(turns.findByIdAndSession(turn.id(), session.id())).thenReturn(Optional.of(turn));
        when(turns.findById(turn.id())).thenAnswer(call -> Optional.of(turn));
        when(turns.save(any())).thenAnswer(call -> call.getArgument(0));
        when(questions.findBySession(session.id())).thenReturn(List.of(question));
        when(answers.save(any())).thenAnswer(call -> call.getArgument(0));
        when(executions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(executions.findEvaluationInProgress(any())).thenAnswer(call -> Optional.empty());
        evaluatorReturns(result());
        service = build(new InMemoryKeys());
    }

    private RegisterAnswerService build(IdempotencyKeyRepository keys) {
        Waiter waiter = duration -> {
            clock.advance(duration);
            onPause.accept(pauses.incrementAndGet());
        };
        return new RegisterAnswerService(sessions, turns, questions, answers, executions, keys, evaluator,
                JsonMapper.builder().build(), waiter, transaction, clock, "modelo", Duration.ofSeconds(20));
    }

    private InterviewSession newSession() {
        Clock earlier = Clock.fixed(NOW.minusSeconds(3600), ZoneOffset.UTC);
        InterviewSession created = InterviewSession.configure(UUID.randomUUID(), OWNER, new SessionConfiguration(
                UUID.randomUUID(), UUID.randomUUID(), null, "{}", Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT,
                InterviewLanguage.SPANISH_COLOMBIA), earlier);
        created.start(earlier);
        return created;
    }

    // Como el evaluador real: guarda el resultado en la clave antes de devolverlo o relanzar.
    private void evaluatorReturns(AnswerResult outcome) {
        doAnswer(call -> {
            call.<TurnEvaluator.EvaluationJob>getArgument(0).recorder().succeeded(outcome);
            return outcome;
        }).when(evaluator).evaluate(any());
    }

    private void evaluatorFails(tech.cameia.entrevista.domain.exception.BusinessException failure) {
        doAnswer(call -> {
            call.<TurnEvaluator.EvaluationJob>getArgument(0).recorder().failed(failure.getErrorCode());
            throw failure;
        }).when(evaluator).evaluate(any());
    }

    private AnswerResult result() {
        return new AnswerResult(turn.id(), TurnStatus.COMPLETED, 3, 5, new SessionState.PendingQuestion(
                UUID.fromString("00000000-0000-4000-8000-000000000003"), 3, "Pregunta 3", TurnStatus.PENDING, 0),
                false);
    }

    private AnswerCommand command() {
        return new AnswerCommand(new SessionCommand(session.id(), OWNER), turn.id().toString(), ANSWER, KEY);
    }

    private String fingerprint() {
        return new ValidatedAnswerRequest(turn.id(), ANSWER, KEY).fingerprint();
    }

    private IdempotencyKey key(int status, String body) {
        return new IdempotencyKey(UUID.randomUUID(), OWNER, "RESPONDER", KEY, fingerprint(), status, body, NOW);
    }

    private IdempotencyKey stored() {
        return store.get(KEY);
    }

    @Test
    @DisplayName("Valida la petición antes de leer nada")
    void register_shouldValidateBeforeReading() {
        AnswerCommand invalid = new AnswerCommand(new SessionCommand(session.id(), OWNER), "no-uuid", "  ", "!");

        assertThatThrownBy(() -> service.register(invalid)).isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(sessions, turns);
    }

    @Test
    @DisplayName("Una sesión ajena o inexistente responde sesión no encontrada")
    void register_shouldThrowSessionNotFound() {
        AnswerCommand foreign = new AnswerCommand(new SessionCommand(session.id(), new FirebaseUid("otra")),
                turn.id().toString(), ANSWER, KEY);
        AnswerCommand missing = new AnswerCommand(new SessionCommand(null, OWNER), turn.id().toString(), ANSWER, KEY);

        assertThatThrownBy(() -> service.register(foreign)).isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service.register(missing)).isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("Una sesión abandonada, cerrada o sin iniciar rechaza la respuesta por su estado")
    void register_shouldThrowByStatus() {
        Clock c = Clock.fixed(NOW, ZoneOffset.UTC);
        session.abandon(c);
        assertThatThrownBy(() -> service.register(command())).isInstanceOf(SessionAbandonedException.class);

        session = newSession();
        session.startEvaluation(c);
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        AnswerCommand evaluating = new AnswerCommand(new SessionCommand(session.id(), OWNER), turn.id().toString(),
                ANSWER, KEY);
        assertThatThrownBy(() -> service.register(evaluating)).isInstanceOf(SessionAlreadyClosedException.class);

        session.finish(c);
        assertThatThrownBy(() -> service.register(evaluating)).isInstanceOf(SessionAlreadyClosedException.class);

        InterviewSession configured = InterviewSession.configure(UUID.randomUUID(), OWNER, session.configuration(), c);
        when(sessions.findByIdAndOwner(configured.id(), OWNER)).thenReturn(Optional.of(configured));
        AnswerCommand notStarted = new AnswerCommand(new SessionCommand(configured.id(), OWNER),
                turn.id().toString(), ANSWER, KEY);
        assertThatThrownBy(() -> service.register(notStarted)).isInstanceOf(SessionTransitionNotAllowedException.class);
    }

    @Test
    @DisplayName("Un turno de otra sesión responde turno no encontrado")
    void register_shouldThrowTurnNotFound_whenOtherSession() {
        when(turns.findByIdAndSession(turn.id(), session.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(TurnNotFoundException.class);
    }

    @Test
    @DisplayName("Un turno que no está pendiente ya tiene su respuesta")
    void register_shouldThrowAnswerAlreadyExists_whenTurnNotPending() {
        turn.startProcessing(clock);

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(AnswerAlreadyExistsException.class);
        verify(answers, never()).save(any());
    }

    @Test
    @DisplayName("La respuesta queda guardada y el turno procesando antes de llamar al modelo, sin transacción abierta")
    void register_shouldCommitAnswerBeforeCallingModel() {
        doAnswer(call -> {
            verify(answers).save(any(AnswerAttempt.class));
            assertThat(turn.status()).isEqualTo(TurnStatus.PROCESSING);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(stored().responseStatus()).isEqualTo(202);
            return result();
        }).when(evaluator).evaluate(any());

        service.register(command());

        verify(evaluator).evaluate(any());
    }

    @Test
    @DisplayName("Guarda la clave con 200 y el resultado cuando la evaluación termina")
    void register_shouldStoreKeyWith200_whenEvaluated() {
        AnswerResult result = service.register(command());

        assertThat(stored().responseStatus()).isEqualTo(200);
        assertThat(stored().responseBodyJson()).doesNotContain(ANSWER);
        assertThat(result.nextQuestion().text()).isEqualTo("Pregunta 3");
        assertThat(session.lastActivityAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Guarda la clave con 503 y relanza cuando el modelo falla")
    void register_shouldStoreKeyWith503AndRethrow_whenModelFails() {
        evaluatorFails(new LlmLimitReachedException(null));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(LlmLimitReachedException.class);

        assertThat(stored().responseStatus()).isEqualTo(503);
        assertThat(stored().responseBodyJson()).contains("LLM_LIMIT_REACHED");
    }

    @Test
    @DisplayName("Repetir la petición con una clave en 200 devuelve el mismo resultado sin llamar al modelo")
    void register_shouldReplay200() {
        AnswerResult first = service.register(command());

        AnswerResult second = service.register(command());

        assertThat(second).isEqualTo(first);
        verify(evaluator).evaluate(any());
    }

    @Test
    @DisplayName("Repetir la petición con una clave en 503 repite el mismo error sin llamar al modelo")
    void register_shouldReplay503() {
        evaluatorFails(new LlmInvalidOutputException("x"));
        assertThatThrownBy(() -> service.register(command())).isInstanceOf(LlmInvalidOutputException.class);
        turn.retry(clock);

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(LlmInvalidOutputException.class);

        verify(evaluator).evaluate(any());
    }

    @Test
    @DisplayName("La misma clave con otra respuesta es un conflicto, aunque siga en curso")
    void register_shouldThrowKeyConflict_whenOtherFingerprint() {
        store.put(KEY, new IdempotencyKey(UUID.randomUUID(), OWNER, "RESPONDER", KEY, "0".repeat(64), 202, "{}", NOW));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(IdempotencyKeyConflictException.class);
        assertThat(pauses.get()).isZero();
    }

    @Test
    @DisplayName("La misma clave en curso espera y devuelve el resultado cuando la primera termina")
    void register_shouldWaitAndReturn_whenSameKeyInProgress() {
        turn.startProcessing(clock);
        store.put(KEY, key(202, "{}"));
        String body = "{\"turnId\":\"" + turn.id() + "\",\"turnStatus\":\"COMPLETED\",\"currentQuestion\":3,"
                + "\"totalQuestions\":5,\"nextTurnId\":\"" + UUID.randomUUID() + "\",\"nextNumber\":3,"
                + "\"nextText\":\"Pregunta 3\",\"interviewFinished\":false}";
        when(executions.findEvaluationInProgress(any())).thenAnswer(call ->
                Optional.of(AiExecution.evaluationStarted(session.id(), turn.id(), "m", "v", "g", clock)));
        onPause = count -> {
            if (count == 3) {
                store.put(KEY, key(200, body));
            }
        };

        AnswerResult result = service.register(command());

        assertThat(result.currentQuestion()).isEqualTo(3);
        assertThat(pauses.get()).isEqualTo(3);
        verify(evaluator, never()).evaluate(any());
    }

    @Test
    @DisplayName("La misma clave que nunca termina responde turno en conflicto al vencer la espera")
    void register_shouldReturnTurnConflict_whenSameKeyNeverFinishes() {
        turn.startProcessing(clock);
        store.put(KEY, key(202, "{}"));
        when(executions.findEvaluationInProgress(any())).thenAnswer(call ->
                Optional.of(AiExecution.evaluationStarted(session.id(), turn.id(), "m", "v", "g", clock)));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(TurnConflictException.class);

        assertThat(clock.instant()).isAfterOrEqualTo(NOW.plusSeconds(35));
    }

    @Test
    @DisplayName("Si el hilo se interrumpe mientras espera la misma clave, sale de inmediato con turno en conflicto")
    void register_shouldReturnTurnConflictAtOnce_whenThreadInterruptedWhileWaiting() {
        turn.startProcessing(clock);
        store.put(KEY, key(202, "{}"));
        when(executions.findEvaluationInProgress(any())).thenAnswer(call ->
                Optional.of(AiExecution.evaluationStarted(session.id(), turn.id(), "m", "v", "g", clock)));
        onPause = count -> Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> service.register(command())).isInstanceOf(TurnConflictException.class);

            assertThat(pauses.get()).isEqualTo(1);
            assertThat(Thread.currentThread().isInterrupted()).as("la marca de interrupción se conserva").isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("La misma clave con la evaluación caída responde modelo no disponible sin esperar")
    void register_shouldReturn503_whenSameKeyAndEvaluationStale() {
        turn.startProcessing(clock);
        store.put(KEY, key(202, "{}"));
        Clock old = Clock.fixed(NOW.minusSeconds(36), ZoneOffset.UTC);
        when(executions.findEvaluationInProgress(any())).thenAnswer(call ->
                Optional.of(AiExecution.evaluationStarted(session.id(), turn.id(), "m", "v", "g", old)));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(LlmUnavailableException.class);

        assertThat(pauses.get()).isZero();
    }

    @Test
    @DisplayName("La misma clave con el turno en reintento responde modelo no disponible")
    void register_shouldReturn503_whenSameKeyAndTurnRetrying() {
        turn.startProcessing(clock);
        turn.retry(clock);
        store.put(KEY, key(202, "{}"));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("La misma clave con el turno ya completado responde que ya hay respuesta")
    void register_shouldReturnAnswerAlreadyExists_whenSameKeyAndTurnCompleted() {
        turn.startProcessing(clock);
        turn.complete(clock);
        store.put(KEY, key(202, "{}"));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(AnswerAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Si la sesión se abandona mientras se evalúa, guarda la clave con 409 y relanza")
    void register_shouldStoreKeyWith409AndRethrow_whenSessionAbandonedMeanwhile() {
        evaluatorFails(new SessionAbandonedException());

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(SessionAbandonedException.class);

        assertThat(stored().responseStatus()).isEqualTo(409);
        assertThat(stored().responseBodyJson()).contains("SESSION_ABANDONED");
    }

    @Test
    @DisplayName("Repetir una clave en 409 repite el mismo error")
    void register_shouldReplay409() {
        evaluatorFails(new SessionAlreadyClosedException());
        assertThatThrownBy(() -> service.register(command())).isInstanceOf(SessionAlreadyClosedException.class);

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(SessionAlreadyClosedException.class);

        verify(evaluator).evaluate(any());
    }

    @Test
    @DisplayName("Si la primera transacción falla, no se llama al modelo")
    void register_shouldNotCallModel_whenFirstTransactionFails() {
        when(answers.save(any())).thenThrow(new DataAccessResourceFailureException("caída"));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(DatabaseUnavailableException.class);

        verify(evaluator, never()).evaluate(any());
    }

    @Test
    @DisplayName("Si la segunda transacción falla, el turno queda procesando y la clave en 202 para recuperarlo")
    void register_shouldLeaveRecoverableTurn_whenSecondTransactionFails() {
        doThrow(new DatabaseUnavailableException(new RuntimeException())).when(evaluator).evaluate(any());

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(DatabaseUnavailableException.class);

        assertThat(turn.status()).isEqualTo(TurnStatus.PROCESSING);
        assertThat(stored().responseStatus()).isEqualTo(202);
    }

    @Test
    @DisplayName("Una clave que otra petición guardó entre la lectura y la reserva se resuelve como repetición")
    void register_shouldResolveRace_whenKeyAppearsDuringReservation() {
        IdempotencyKeyRepository racing = new InMemoryKeys() {
            private boolean first = true;

            @Override
            public Optional<IdempotencyKey> find(FirebaseUid owner, String operation, String key) {
                if (first) {
                    first = false;
                    return Optional.empty();
                }
                return super.find(owner, operation, key);
            }

            @Override
            public IdempotencyKey save(IdempotencyKey saved) {
                if (saved.responseStatus() == 202 && !store.containsKey(KEY)) {
                    store.put(KEY, new IdempotencyKey(UUID.randomUUID(), OWNER, "RESPONDER", KEY, fingerprint(), 200,
                            "{\"turnId\":\"" + turn.id() + "\",\"turnStatus\":\"COMPLETED\",\"currentQuestion\":5,"
                                    + "\"totalQuestions\":5,\"interviewFinished\":true}", NOW));
                    throw new IdempotencyKeyAlreadyExistsException();
                }
                return super.save(saved);
            }
        };

        AnswerResult result = build(racing).register(command());

        assertThat(result.interviewFinished()).isTrue();
        verify(evaluator, never()).evaluate(any());
    }

    @Test
    @DisplayName("Un fallo que no es del modelo ni de la sesión no guarda resultado en la clave")
    void register_shouldNotStoreResult_whenFailureIsNeitherModelNorSession() {
        evaluatorFails(new DatabaseUnavailableException(new RuntimeException()));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(DatabaseUnavailableException.class);

        assertThat(stored().responseStatus()).isEqualTo(202);
    }

    @Test
    @DisplayName("Repetir una clave guardada con cada código de fallo repite su error")
    void register_shouldReplayEachStoredFailureCode() {
        String[][] cases = {
            {"503", "LLM_LIMIT_REACHED"}, {"503", "LLM_UNAVAILABLE"}, {"409", "SESSION_ABANDONED"},
            {"409", "SESSION_TRANSITION_NOT_ALLOWED"}};
        Class<?>[] expected = {LlmLimitReachedException.class, LlmUnavailableException.class,
            SessionAbandonedException.class, SessionTransitionNotAllowedException.class};
        for (int i = 0; i < cases.length; i++) {
            store.put(KEY, key(Integer.parseInt(cases[i][0]), "{\"code\":\"" + cases[i][1] + "\"}"));

            assertThatThrownBy(() -> service.register(command())).isInstanceOf(expected[i]);
        }
    }

    @Test
    @DisplayName("Un turno que otra petición tomó entre la lectura y la reserva responde que ya hay respuesta")
    void register_shouldThrowAnswerAlreadyExists_whenTurnTakenDuringReservation() {
        InterviewTurn taken = InterviewTurn.open(question.id(), clock);
        taken.startProcessing(clock);
        when(turns.findById(turn.id())).thenReturn(Optional.of(taken));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(AnswerAlreadyExistsException.class);

        verify(answers, never()).save(any());
    }

    @Test
    @DisplayName("Si otra petición gana la escritura del turno, responde que ya hay respuesta")
    void register_shouldThrowAnswerAlreadyExists_whenTurnWriteLosesRace() {
        when(turns.save(any())).thenThrow(new tech.cameia.entrevista.domain.exception.SessionConflictException());

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(AnswerAlreadyExistsException.class);

        verify(evaluator, never()).evaluate(any());
    }

    @Test
    @DisplayName("La misma clave en curso con el turno todavía pendiente espera hasta vencer y responde turno en conflicto")
    void register_shouldReturnTurnConflict_whenSameKeyAndTurnStillPending() {
        store.put(KEY, key(202, "{}"));

        assertThatThrownBy(() -> service.register(command())).isInstanceOf(TurnConflictException.class);
    }

    /** Claves de idempotencia en memoria: guardar con el mismo identificador reemplaza, con otro choca. */
    private class InMemoryKeys implements IdempotencyKeyRepository {

        @Override
        public Optional<IdempotencyKey> find(FirebaseUid owner, String operation, String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public IdempotencyKey save(IdempotencyKey saved) {
            IdempotencyKey current = store.get(saved.key());
            if (current != null && !current.id().equals(saved.id())) {
                throw new IdempotencyKeyAlreadyExistsException();
            }
            store.put(saved.key(), saved);
            return saved;
        }
    }

    /** Reloj que solo avanza cuando la prueba lo pide. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
