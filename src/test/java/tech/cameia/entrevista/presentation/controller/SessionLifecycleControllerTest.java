package tech.cameia.entrevista.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.service.CreateSessionService;
import tech.cameia.entrevista.application.service.DeleteSessionService;
import tech.cameia.entrevista.application.service.GetSessionStateService;
import tech.cameia.entrevista.application.service.SessionState;
import tech.cameia.entrevista.application.service.StartSessionService;
import tech.cameia.entrevista.domain.exception.PlanningRetriesExhaustedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.presentation.advice.ProblemDetailTestSupport;

/** Los tres endpoints de la vida de la sesión: iniciar, consultar el estado y cancelar. */
class SessionLifecycleControllerTest {

    private static final UUID SESSION = UUID.fromString("3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10");
    private static final UUID TURN = UUID.fromString("9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12");
    private static final String BASE = "/api/v1/interviews/sessions/";

    private final StartSessionService start = mock(StartSessionService.class);
    private final GetSessionStateService state = mock(GetSessionStateService.class);
    private final DeleteSessionService delete = mock(DeleteSessionService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InterviewSessionController(mock(CreateSessionService.class), start, state, delete,
                    mock(tech.cameia.entrevista.application.service.RegisterAnswerService.class),
                    mock(tech.cameia.entrevista.application.service.ReprocessTurnService.class)))
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    private static SessionState inProgress() {
        return new SessionState(SESSION, SessionStatus.IN_PROGRESS, AnswerMode.AUDIO, 1,
                new SessionState.PendingQuestion(TURN, 1, "Cuéntame quién eres",
                        tech.cameia.entrevista.domain.model.TurnStatus.PENDING, 0));
    }

    @Test
    void start_shouldReturn200WithStateAndOwnerFromHeader() throws Exception {
        when(start.start(any())).thenReturn(inProgress());

        mockMvc.perform(post(BASE + SESSION + "/start").header("X-User-Id", "uid-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION.toString()))
                .andExpect(jsonPath("$.status").value("EN_CURSO"))
                .andExpect(jsonPath("$.interviewFinished").value(false))
                .andExpect(jsonPath("$.progress.currentQuestion").value(1))
                .andExpect(jsonPath("$.progress.totalQuestions").value(5))
                .andExpect(jsonPath("$.pendingQuestion.turnId").value(TURN.toString()))
                .andExpect(jsonPath("$.pendingQuestion.number").value(1))
                .andExpect(jsonPath("$.pendingQuestion.text").value("Cuéntame quién eres"))
                .andExpect(jsonPath("$.history").isEmpty());
        ArgumentCaptor<SessionCommand> command = ArgumentCaptor.forClass(SessionCommand.class);
        verify(start).start(command.capture());
        assertThat(command.getValue().owner().value()).isEqualTo("uid-a");
        assertThat(command.getValue().sessionId()).isEqualTo(SESSION);
    }

    @Test
    void start_shouldReturn401_whenIdentityIsMissing() throws Exception {
        mockMvc.perform(post(BASE + SESSION + "/start"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    void start_shouldReturn503WithRetriesExhaustedCode() throws Exception {
        when(start.start(any())).thenThrow(new PlanningRetriesExhaustedException());

        mockMvc.perform(post(BASE + SESSION + "/start").header("X-User-Id", "uid-a"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PLANNING_RETRIES_EXHAUSTED"));
    }

    @Test
    void start_shouldReturn404WithSameBodyForNotAUuid() throws Exception {
        when(start.start(any())).thenThrow(new SessionNotFoundException());

        mockMvc.perform(post(BASE + "abc/start").header("X-User-Id", "uid-a"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No encontramos lo que buscabas."));
        ArgumentCaptor<SessionCommand> command = ArgumentCaptor.forClass(SessionCommand.class);
        verify(start).start(command.capture());
        assertThat(command.getValue().sessionId()).isNull();
    }

    @Test
    void state_shouldReturnPendingQuestionNullWhenConfigured() throws Exception {
        when(state.state(any())).thenReturn(new SessionState(SESSION, SessionStatus.CONFIGURED, AnswerMode.TEXT, 0, null));

        mockMvc.perform(get(BASE + SESSION + "/state").header("X-User-Id", "uid-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIGURADA"))
                .andExpect(jsonPath("$.answerFormat").value("TEXTO"))
                .andExpect(jsonPath("$.progress.currentQuestion").value(0))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"pendingQuestion\":null")));
    }

    @Test
    void state_shouldMarkInterviewFinishedWhenEvaluating() throws Exception {
        when(state.state(any())).thenReturn(new SessionState(SESSION, SessionStatus.EVALUATING, AnswerMode.TEXT, 5, null));

        mockMvc.perform(get(BASE + SESSION + "/state").header("X-User-Id", "uid-a"))
                .andExpect(jsonPath("$.interviewFinished").value(true))
                .andExpect(jsonPath("$.progress.currentQuestion").value(5));
    }

    @Test
    void state_shouldReturn401_whenIdentityIsMissing() throws Exception {
        mockMvc.perform(get(BASE + SESSION + "/state")).andExpect(status().isUnauthorized());
    }

    @Test
    void delete_shouldReturn204WithoutBody() throws Exception {
        mockMvc.perform(delete(BASE + SESSION).header("X-User-Id", "uid-a"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void delete_shouldReturn409_whenNotConfigured() throws Exception {
        doThrow(new SessionTransitionNotAllowedException()).when(delete).delete(any());

        mockMvc.perform(delete(BASE + SESSION).header("X-User-Id", "uid-a"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_TRANSITION_NOT_ALLOWED"));
    }

    @Test
    void delete_shouldReturn401_whenIdentityIsMissing() throws Exception {
        mockMvc.perform(delete(BASE + SESSION)).andExpect(status().isUnauthorized());
    }
}
