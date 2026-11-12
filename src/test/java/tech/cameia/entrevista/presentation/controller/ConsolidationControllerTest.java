package tech.cameia.entrevista.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.service.ConsolidateSessionService;
import tech.cameia.entrevista.application.service.CreateSessionService;
import tech.cameia.entrevista.application.service.DeleteSessionService;
import tech.cameia.entrevista.application.service.GetSessionStateService;
import tech.cameia.entrevista.application.service.RegisterAnswerService;
import tech.cameia.entrevista.application.service.ReprocessTurnService;
import tech.cameia.entrevista.application.service.SessionState;
import tech.cameia.entrevista.application.service.StartSessionService;
import tech.cameia.entrevista.domain.exception.SessionConsolidationFailedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.presentation.advice.ProblemDetailTestSupport;

/** Endpoint que reintenta el cierre de la entrevista: respuesta, errores, identidad y entrada ignorada. */
class ConsolidationControllerTest {

    private static final UUID SESSION = UUID.fromString("3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10");
    private static final String URL = "/api/v1/interviews/sessions/" + SESSION + "/consolidation";

    private final ConsolidateSessionService consolidate = mock(ConsolidateSessionService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InterviewSessionController(mock(CreateSessionService.class),
                    mock(StartSessionService.class), mock(GetSessionStateService.class),
                    mock(DeleteSessionService.class), mock(RegisterAnswerService.class),
                    mock(ReprocessTurnService.class), consolidate))
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    @Test
    @DisplayName("Una sesión cerrada responde 200 con el estado FINALIZADA, sin pregunta pendiente")
    void consolidate_shouldReturn200WithFinishedState() throws Exception {
        when(consolidate.consolidateOwned(any()))
                .thenReturn(new SessionState(SESSION, SessionStatus.FINISHED, AnswerMode.TEXT, 5, null));

        mockMvc.perform(post(URL).header("X-User-Id", "uid-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADA"))
                .andExpect(jsonPath("$.interviewFinished").value(true))
                .andExpect(jsonPath("$.progress.currentQuestion").value(5))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"pendingQuestion\":null")));
        ArgumentCaptor<SessionCommand> command = ArgumentCaptor.forClass(SessionCommand.class);
        verify(consolidate).consolidateOwned(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().sessionId()).isEqualTo(SESSION);
        org.assertj.core.api.Assertions.assertThat(command.getValue().owner().value()).isEqualTo("uid-a");
    }

    @Test
    @DisplayName("Sin identidad responde 401")
    void consolidate_shouldReturn401_whenIdentityIsMissing() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    @DisplayName("El cuerpo y los encabezados de retroalimentación se ignoran")
    void consolidate_shouldIgnoreBodyAndFeedbackHints() throws Exception {
        when(consolidate.consolidateOwned(any()))
                .thenReturn(new SessionState(SESSION, SessionStatus.FINISHED, AnswerMode.TEXT, 5, null));

        mockMvc.perform(post(URL + "?feedback=true").header("X-User-Id", "uid-a")
                        .header("X-Include-Feedback", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "feedback"))));
    }

    @Test
    @DisplayName("Una sesión ajena, inexistente o mal escrita responde 404 con el mismo código")
    void consolidate_shouldReturn404_whenNotFound() throws Exception {
        when(consolidate.consolidateOwned(any())).thenThrow(new SessionNotFoundException());

        mockMvc.perform(post(URL).header("X-User-Id", "uid-a")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("Una sesión que no se está evaluando responde 409 con su código")
    void consolidate_shouldReturn409_whenNotEvaluating() throws Exception {
        when(consolidate.consolidateOwned(any())).thenThrow(new SessionTransitionNotAllowedException());

        mockMvc.perform(post(URL).header("X-User-Id", "uid-a")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_TRANSITION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail").value("Esta entrevista ya no admite esa acción."));
    }

    @Test
    @DisplayName("Un cierre fallido responde 503 con su código y el mensaje claro")
    void consolidate_shouldReturn503_whenConsolidationFails() throws Exception {
        when(consolidate.consolidateOwned(any())).thenThrow(new SessionConsolidationFailedException());

        mockMvc.perform(post(URL).header("X-User-Id", "uid-a")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SESSION_CONSOLIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."));
    }
}
