package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.PlanningRetriesExhaustedException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiExecutionStatus;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.policy.PlanValidator;
import tech.cameia.entrevista.domain.policy.PlanningRetryPolicy;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.LanguageModelPort;

/** Caminos de fallo de iniciar la sesión, con dobles de los puertos y las transacciones. */
class StartSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");
    private static final UUID ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000002");
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");
    private static final String VALID_PLAN = """
            {"idioma":"es-CO","preguntas":[
             {"posicion":1,"tipo":"APERTURA","texto":"Uno"},{"posicion":2,"tipo":"TECNICA","texto":"Dos"},
             {"posicion":3,"tipo":"CONDUCTUAL","texto":"Tres"},{"posicion":4,"tipo":"TECNICA","texto":"Cuatro"},
             {"posicion":5,"tipo":"SITUACIONAL","texto":"Cinco"}]}""";

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final AiExecutionRepository executions = mock(AiExecutionRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final LanguageModelPort model = mock(LanguageModelPort.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private InterviewSession session;
    private StartSessionService service;

    @BeforeEach
    void prepare() {
        String snapshot = "{\"perfilId\":\"22222222-2222-4222-8222-222222222222\",\"nombre\":\"Perfil\","
                + "\"contextoProfesional\":{\"resumen\":\"SQL\"},\"rolesAspirados\":[{\"codigo\":\"" + ROLE
                + "\",\"nombre\":\"Backend\"}],\"actualizadoEn\":\"2026-11-01T00:00:00Z\"}";
        session = InterviewSession.configure(UUID.randomUUID(), OWNER, new SessionConfiguration(UUID.randomUUID(),
                ROLE, null, snapshot, Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT,
                InterviewLanguage.SPANISH_COLOMBIA), clock);
        when(transaction.execute(any())).thenAnswer(call ->
                ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
        doAnswer(call -> {
            ((Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        when(executions.findPlanningInProgress(session.id())).thenReturn(Optional.empty());
        when(executions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(executions.failIfInProgress(any())).thenReturn(true);
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        GetSessionStateService state = new GetSessionStateService(sessions, questions, turns,
                mock(tech.cameia.entrevista.domain.port.AnswerAttemptRepository.class), executions, clock,
                Duration.ofMillis(20000));
        service = new StartSessionService(sessions, executions, questions, turns, model,
                new PlanningPromptBuilder(JsonMapper.builder().build()), new PlanOutputReader(JsonMapper.builder().build()),
                new PlanValidator(), new PlanningRetryPolicy(), state, transaction, clock, "modelo-de-prueba",
                Duration.ofMillis(20000));
    }

    private SessionCommand command() {
        return new SessionCommand(session.id(), OWNER);
    }

    /** La marca tal como se cerró como fallida: el cierre solo actualiza una fila en curso, nunca la inserta. */
    private AiExecution lastExecution() {
        ArgumentCaptor<AiExecution> captor = ArgumentCaptor.forClass(AiExecution.class);
        verify(executions, org.mockito.Mockito.atLeastOnce()).failIfInProgress(captor.capture());
        return captor.getValue();
    }

    @Test
    void start_shouldTurnUnexpectedModelFailureIntoLlmUnavailable() {
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        when(model.generate(any())).thenThrow(new IllegalStateException("cliente roto"));

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(LlmUnavailableException.class);

        assertThat(lastExecution().status()).isEqualTo(AiExecutionStatus.FAILED);
        assertThat(lastExecution().errorCode()).isEqualTo("LLM_UNAVAILABLE");
        verify(questions, never()).saveAll(any());
    }

    @Test
    void start_shouldCloseMarkAsDatabaseUnavailable_whenSavingFailsWithDataAccessError() {
        givenValidModelAnswer();
        doThrow(new DataAccessResourceFailureException("sin conexión")).when(questions).saveAll(any());

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(DatabaseUnavailableException.class);

        assertThat(lastExecution().errorCode()).isEqualTo(ErrorCode.DATABASE_UNAVAILABLE.name());
    }

    @Test
    void start_shouldCloseMarkAsDatabaseUnavailable_whenTransactionFails() {
        givenValidModelAnswer();
        doThrow(new TransactionSystemException("commit")).when(questions).saveAll(any());

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(DatabaseUnavailableException.class);

        assertThat(lastExecution().errorCode()).isEqualTo(ErrorCode.DATABASE_UNAVAILABLE.name());
    }

    @Test
    void start_shouldReportExhaustedRetries_whenSavingFailsOnTheLastAttempt() {
        session.registerPlanningRetry(clock);
        when(executions.hasFailedPlanning(session.id())).thenReturn(true);
        givenValidModelAnswer();
        doThrow(new DataAccessResourceFailureException("sin conexión")).when(questions).saveAll(any());

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(PlanningRetriesExhaustedException.class);

        assertThat(lastExecution().errorCode()).isEqualTo(ErrorCode.DATABASE_UNAVAILABLE.name());
    }

    @Test
    void start_shouldAnswerDatabaseUnavailableWithoutCallingTheModel_whenReservingFails() {
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        doThrow(new TransactionSystemException("commit")).when(transaction).execute(any());

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(DatabaseUnavailableException.class);

        verify(model, never()).generate(any());
    }

    @Test
    void start_shouldCloseMarkAsInternalError_whenSavingFailsUnexpectedly() {
        givenValidModelAnswer();
        doThrow(new IllegalStateException("defecto")).when(questions).saveAll(any());

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(IllegalStateException.class);

        assertThat(lastExecution().errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
    }

    @Test
    void start_shouldKeepBusinessCode_whenSavingFailsWithBusinessError() {
        givenValidModelAnswer();
        doThrow(new SessionConflictException()).when(questions).saveAll(any());

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(SessionConflictException.class);

        assertThat(lastExecution().errorCode()).isEqualTo(ErrorCode.SESSION_CONFLICT.name());
    }

    @Test
    void start_shouldStillRethrowOriginalFailure_whenClosingTheMarkFails() {
        givenValidModelAnswer();
        doThrow(new IllegalStateException("defecto")).when(questions).saveAll(any());
        when(executions.failIfInProgress(any()))
                .thenThrow(new DataAccessResourceFailureException("tampoco se pudo cerrar"));

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(IllegalStateException.class)
                .hasMessage("defecto");
    }

    @Test
    void start_shouldReturnConflict_whenSessionStoppedBeingConfiguredBeforeClosing() {
        InterviewSession started = startedCopy();
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session), Optional.of(session),
                Optional.of(started));
        when(model.generate(any())).thenReturn(new LanguageModelResponse(VALID_PLAN, 10, 5, "m"));

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(SessionConflictException.class);

        assertThat(lastExecution().errorCode()).isEqualTo("SESSION_CONFLICT");
    }

    @Test
    void start_shouldReturnNotFound_whenSessionDisappearsBeforeClosing() {
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session), Optional.of(session),
                Optional.empty());
        when(model.generate(any())).thenReturn(new LanguageModelResponse(VALID_PLAN, 10, 5, "m"));

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void start_shouldReturnNotFound_whenSessionDisappearsBeforeReserving() {
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session), Optional.empty());

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(SessionNotFoundException.class);

        verify(model, never()).generate(any());
    }

    @Test
    void start_shouldReturnConflict_whenSessionChangedBeforeReserving() {
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session),
                Optional.of(startedCopy()));

        assertThatThrownBy(() -> service.start(command())).isInstanceOf(SessionConflictException.class);

        verify(model, never()).generate(any());
    }

    private void givenValidModelAnswer() {
        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenReturn(Optional.of(session));
        when(model.generate(any())).thenReturn(new LanguageModelResponse(VALID_PLAN, 10, 5, "m"));
    }

    private InterviewSession startedCopy() {
        InterviewSession copy = InterviewSession.configure(session.id(), OWNER, session.configuration(), clock);
        copy.start(clock);
        return copy;
    }
}
