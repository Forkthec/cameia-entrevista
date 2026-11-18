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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionConsolidationFailedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionSnapshot;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.TurnFeedbackRepository;
import tech.cameia.entrevista.domain.port.Waiter;

/** Consolidación: cierre con fecha de fin, reintentos con espera creciente, datos rotos y pedido de la persona. */
class ConsolidateSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T15:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final TurnFeedbackRepository feedbacks = mock(TurnFeedbackRepository.class);
    private final GetSessionStateService stateService = mock(GetSessionStateService.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final List<Duration> waits = new ArrayList<>();
    private final AtomicBoolean inTransaction = new AtomicBoolean();
    private final AtomicBoolean waitedInsideTransaction = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();
    private int failuresBeforeSuccess;
    private RuntimeException failure = new DataAccessResourceFailureException("caída");
    private ConsolidateSessionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        Waiter waiter = duration -> {
            waits.add(duration);
            if (inTransaction.get()) {
                waitedInsideTransaction.set(true);
            }
        };
        doAnswer(call -> {
            if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw failure;
            }
            inTransaction.set(true);
            try {
                ((Consumer<Object>) call.getArgument(0)).accept(null);
            } finally {
                inTransaction.set(false);
            }
            return null;
        }).when(transaction).executeWithoutResult(any());
        when(turns.countCompleted(SESSION_ID)).thenReturn(5);
        when(feedbacks.countBySession(SESSION_ID)).thenReturn(5);
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new ConsolidateSessionService(sessions, turns, feedbacks, stateService, waiter, transaction,
                CLOCK);
    }

    private InterviewSession sessionIn(SessionStatus status) {
        SessionConfiguration configuration = new SessionConfiguration(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                null, "{}", Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT, InterviewLanguage.ENGLISH);
        Instant start = Instant.parse("2026-11-12T14:00:00Z");
        Instant end = status == SessionStatus.FINISHED ? start : null;
        InterviewSession session = InterviewSession.reconstitute(new SessionSnapshot(SESSION_ID, OWNER,
                configuration, status, 1, start, start, null, end, 3L));
        when(sessions.findByIdAndOwner(SESSION_ID, OWNER)).thenReturn(Optional.of(session));
        return session;
    }

    @Test
    @DisplayName("Con los cinco turnos y las cinco retroalimentaciones finaliza la sesión con la fecha de fin")
    void consolidate_shouldFinishAndSetEndDate() {
        InterviewSession session = sessionIn(SessionStatus.EVALUATING);

        service.consolidate(SESSION_ID, OWNER);

        assertThat(session.status()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session.finishedAt()).isEqualTo(NOW);
        assertThat(session.lastActivityAt()).isNull();
        verify(sessions).save(session);
        assertThat(waits).isEmpty();
    }

    @Test
    @DisplayName("Una sesión ya finalizada termina sin cambios")
    void consolidate_shouldDoNothing_whenAlreadyFinished() {
        InterviewSession session = sessionIn(SessionStatus.FINISHED);

        service.consolidate(SESSION_ID, OWNER);

        assertThat(session.finishedAt()).isEqualTo(Instant.parse("2026-11-12T14:00:00Z"));
        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("Con dos fallos transitorios reintenta con esperas de 200 ms y 400 ms y cierra en el tercero")
    void consolidate_shouldRetryWithGrowingWait_whenTransientFailure() {
        InterviewSession session = sessionIn(SessionStatus.EVALUATING);
        failuresBeforeSuccess = 2;

        service.consolidate(SESSION_ID, OWNER);

        assertThat(waits).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(session.status()).isEqualTo(SessionStatus.FINISHED);
    }

    @Test
    @DisplayName("Con tres fallos agota los intentos y responde cierre fallido con la sesión intacta")
    void consolidate_shouldThrow_whenThreeFailures() {
        InterviewSession session = sessionIn(SessionStatus.EVALUATING);
        failuresBeforeSuccess = 3;

        assertThatThrownBy(() -> service.consolidate(SESSION_ID, OWNER))
                .isInstanceOf(SessionConsolidationFailedException.class);

        assertThat(waits).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(session.status()).isEqualTo(SessionStatus.EVALUATING);
    }

    @Test
    @DisplayName("Si falta una retroalimentación no reintenta: un solo intento, sin esperas")
    void consolidate_shouldFailWithoutRetry_whenFeedbackMissing() {
        InterviewSession session = sessionIn(SessionStatus.EVALUATING);
        when(feedbacks.countBySession(SESSION_ID)).thenReturn(4);

        assertThatThrownBy(() -> service.consolidate(SESSION_ID, OWNER))
                .isInstanceOf(SessionConsolidationFailedException.class);

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(waits).isEmpty();
        assertThat(session.status()).isEqualTo(SessionStatus.EVALUATING);
    }

    @Test
    @DisplayName("Si falta un turno completo no reintenta y no finaliza")
    void consolidate_shouldFailWithoutRetry_whenTurnMissing() {
        InterviewSession session = sessionIn(SessionStatus.EVALUATING);
        when(turns.countCompleted(SESSION_ID)).thenReturn(4);

        assertThatThrownBy(() -> service.consolidate(SESSION_ID, OWNER))
                .isInstanceOf(SessionConsolidationFailedException.class);

        assertThat(attempts.get()).isEqualTo(1);
        verify(sessions, never()).save(any());
        assertThat(session.status()).isEqualTo(SessionStatus.EVALUATING);
    }

    @Test
    @DisplayName("Un conflicto de versión se reintenta: la otra consolidación ganó y se relee la sesión finalizada")
    void consolidate_shouldRetry_whenVersionConflict() {
        sessionIn(SessionStatus.EVALUATING);
        failuresBeforeSuccess = 1;
        failure = new SessionConflictException();
        InterviewSession finished = sessionIn(SessionStatus.FINISHED);

        service.consolidate(SESSION_ID, OWNER);

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(waits).containsExactly(Duration.ofMillis(200));
        verify(sessions, never()).save(finished);
    }

    @Test
    @DisplayName("Una sesión que no se está evaluando no se consolida")
    void consolidate_shouldRejectNotEvaluating() {
        sessionIn(SessionStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.consolidate(SESSION_ID, OWNER))
                .isInstanceOf(SessionTransitionNotAllowedException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una sesión inexistente responde sesión no encontrada")
    void consolidate_shouldThrowNotFound_whenMissing() {
        when(sessions.findByIdAndOwner(SESSION_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consolidate(SESSION_ID, OWNER)).isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("La espera entre intentos ocurre sin transacción abierta")
    void consolidate_shouldNotHoldTransactionWhileWaiting() {
        sessionIn(SessionStatus.EVALUATING);
        failuresBeforeSuccess = 2;

        service.consolidate(SESSION_ID, OWNER);

        assertThat(waits).hasSize(2);
        assertThat(waitedInsideTransaction).isFalse();
    }

    @Test
    @DisplayName("El pedido de la persona sobre una sesión finalizada devuelve el estado sin ciclo")
    void consolidateOwned_shouldReturnState_whenFinished() {
        sessionIn(SessionStatus.FINISHED);
        SessionCommand command = new SessionCommand(SESSION_ID, OWNER);
        SessionState state = mock(SessionState.class);
        when(stateService.state(command)).thenReturn(state);

        assertThat(service.consolidateOwned(command)).isSameAs(state);

        assertThat(attempts.get()).isZero();
    }

    @Test
    @DisplayName("El pedido de la persona sobre una sesión evaluándose consolida y devuelve el estado")
    void consolidateOwned_shouldConsolidate_whenEvaluating() {
        InterviewSession session = sessionIn(SessionStatus.EVALUATING);
        SessionCommand command = new SessionCommand(SESSION_ID, OWNER);
        SessionState state = mock(SessionState.class);
        when(stateService.state(command)).thenReturn(state);

        assertThat(service.consolidateOwned(command)).isSameAs(state);

        assertThat(session.status()).isEqualTo(SessionStatus.FINISHED);
    }

    @Test
    @DisplayName("Una sesión configurada, en curso o abandonada responde transición no permitida")
    void consolidateOwned_shouldReject409_whenNotEvaluatingNorFinished() {
        SessionCommand command = new SessionCommand(SESSION_ID, OWNER);
        for (SessionStatus status : List.of(SessionStatus.CONFIGURED, SessionStatus.IN_PROGRESS,
                SessionStatus.ABANDONED)) {
            sessionIn(status);

            assertThatThrownBy(() -> service.consolidateOwned(command))
                    .isInstanceOf(SessionTransitionNotAllowedException.class);
        }
        assertThat(attempts.get()).isZero();
    }

    @Test
    @DisplayName("Una sesión ajena o inexistente responde sesión no encontrada")
    void consolidateOwned_shouldThrowNotFound_whenForeign() {
        sessionIn(SessionStatus.EVALUATING);

        assertThatThrownBy(() -> service.consolidateOwned(new SessionCommand(SESSION_ID, new FirebaseUid("otra"))))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service.consolidateOwned(new SessionCommand(null, OWNER)))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
