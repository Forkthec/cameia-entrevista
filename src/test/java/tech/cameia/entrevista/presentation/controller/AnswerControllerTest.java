package tech.cameia.entrevista.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import tech.cameia.entrevista.application.command.AnswerCommand;
import tech.cameia.entrevista.application.service.AnswerResult;
import tech.cameia.entrevista.application.service.CreateSessionService;
import tech.cameia.entrevista.application.service.ConsolidateSessionService;
import tech.cameia.entrevista.application.service.DeleteSessionService;
import tech.cameia.entrevista.application.service.GetSessionStateService;
import tech.cameia.entrevista.application.service.RegisterAnswerService;
import tech.cameia.entrevista.application.service.ReprocessTurnService;
import tech.cameia.entrevista.application.service.SessionState;
import tech.cameia.entrevista.application.service.StartSessionService;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.TurnNotRetryableException;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.presentation.advice.ProblemDetailTestSupport;

/** Los endpoints de responder y reintentar: contrato exacto, sin retroalimentación y errores con su código. */
class AnswerControllerTest {

    private static final UUID SESSION = UUID.fromString("3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10");
    private static final UUID TURN = UUID.fromString("9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12");
    private static final UUID NEXT = UUID.fromString("5d0c7a1e-3b9f-4c28-a6d4-8e1f2b3c4d5e");
    private static final String ANSWERS = "/api/v1/interviews/sessions/" + SESSION + "/answers";
    private static final String BODY = "{\"turnId\":\"" + TURN + "\",\"answerText\":\"Mi respuesta\"}";

    private final RegisterAnswerService register = mock(RegisterAnswerService.class);
    private final ReprocessTurnService reprocess = mock(ReprocessTurnService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InterviewSessionController(mock(CreateSessionService.class),
                    mock(StartSessionService.class), mock(GetSessionStateService.class),
                    mock(DeleteSessionService.class), register, reprocess,
                    mock(ConsolidateSessionService.class)))
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    private static AnswerResult advance() {
        return new AnswerResult(TURN, TurnStatus.COMPLETED, 2, 5, new SessionState.PendingQuestion(NEXT, 2,
                "Pregunta 2", TurnStatus.PENDING, 0), false);
    }

    @Test
    @DisplayName("Responder devuelve 200 con el turno, el avance y la pregunta siguiente, y nada más")
    void answer_shouldReturn200WithExactBody() throws Exception {
        when(register.register(any())).thenReturn(advance());

        mockMvc.perform(post(ANSWERS).header("X-User-Id", "uid-a").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"turn\":{\"turnId\":\"" + TURN + "\",\"status\":\"COMPLETED\"},"
                        + "\"progress\":{\"currentQuestion\":2,\"totalQuestions\":5},"
                        + "\"nextQuestion\":{\"turnId\":\"" + NEXT + "\",\"number\":2,\"text\":\"Pregunta 2\"},"
                        + "\"interviewFinished\":false}", true));
        ArgumentCaptor<AnswerCommand> command = ArgumentCaptor.forClass(AnswerCommand.class);
        verify(register).register(command.capture());
        assertThat(command.getValue().session().owner().value()).isEqualTo("uid-a");
        assertThat(command.getValue().session().sessionId()).isEqualTo(SESSION);
        assertThat(command.getValue().idempotencyKey()).isEqualTo("k1");
        assertThat(command.getValue().answerText()).isEqualTo("Mi respuesta");
    }

    @Test
    @DisplayName("La respuesta nunca trae retroalimentación y la última pregunta lleva nextQuestion null")
    void answer_shouldNeverIncludeFeedbackAndKeepNextQuestionNull() throws Exception {
        when(register.register(any())).thenReturn(new AnswerResult(TURN, TurnStatus.COMPLETED, 5, 5, null, true));

        mockMvc.perform(post(ANSWERS).header("X-User-Id", "uid-a").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"nextQuestion\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString("feedback"),
                        org.hamcrest.Matchers.containsString("clasificacion"),
                        org.hamcrest.Matchers.containsString("motivo")))))
                .andExpect(jsonPath("$.interviewFinished").value(true));
    }

    @Test
    @DisplayName("Un Content-Type que no es JSON responde 415")
    void answer_shouldReturn415_whenNotJson() throws Exception {
        mockMvc.perform(post(ANSWERS).header("X-User-Id", "uid-a").header("Idempotency-Key", "k1")
                        .contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Un cuerpo cortado responde 422 REQUEST_BODY_INVALID_FORMAT")
    void answer_shouldReturn422_whenBodyIsBroken() throws Exception {
        mockMvc.perform(post(ANSWERS).header("X-User-Id", "uid-a").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"turnId\":"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("Un answerText que es un objeto responde 422 REQUEST_BODY_INVALID_FORMAT")
    void answer_shouldReturn422_whenAnswerTextIsObject() throws Exception {
        mockMvc.perform(post(ANSWERS).header("X-User-Id", "uid-a").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"turnId\":\"" + TURN
                                + "\",\"answerText\":{\"a\":1}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("Un answerText numérico llega como texto")
    void answer_shouldReadNumberAsText() throws Exception {
        when(register.register(any())).thenReturn(advance());

        mockMvc.perform(post(ANSWERS).header("X-User-Id", "uid-a").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"turnId\":\"" + TURN
                                + "\",\"answerText\":123}"))
                .andExpect(status().isOk());
        ArgumentCaptor<AnswerCommand> command = ArgumentCaptor.forClass(AnswerCommand.class);
        verify(register).register(command.capture());
        assertThat(command.getValue().answerText()).isEqualTo("123");
    }

    @Test
    @DisplayName("Los campos que sobran se ignoran")
    void answer_shouldIgnoreExtraFields() throws Exception {
        when(register.register(any())).thenReturn(advance());

        mockMvc.perform(post(ANSWERS).header("X-User-Id", "uid-a").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"turnId\":\"" + TURN
                                + "\",\"answerText\":\"x\",\"firebaseUid\":\"otro\",\"extra\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sin X-User-Id responde 401")
    void answer_shouldReturn401_whenIdentityMissing() throws Exception {
        mockMvc.perform(post(ANSWERS).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    @DisplayName("Un fallo de la IA responde 503 con su código")
    void answer_shouldReturn503_whenModelFails() throws Exception {
        when(register.register(any())).thenThrow(new LlmUnavailableException(null));

        mockMvc.perform(post(ANSWERS).header("X-User-Id", "uid-a").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Reintentar devuelve 200 con el mismo cuerpo y pasa el turno tal como llegó en la ruta")
    void reprocess_shouldReturn200() throws Exception {
        when(reprocess.reprocess(any(), eq("abc"))).thenReturn(advance());

        mockMvc.perform(post("/api/v1/interviews/sessions/" + SESSION + "/turns/abc/reprocess")
                        .header("X-User-Id", "uid-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turn.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress.currentQuestion").value(2));
    }

    @Test
    @DisplayName("Reintentar un turno que no espera reintento responde 409 TURN_NOT_RETRYABLE")
    void reprocess_shouldReturn409_whenNotRetryable() throws Exception {
        when(reprocess.reprocess(any(), any())).thenThrow(new TurnNotRetryableException());

        mockMvc.perform(post("/api/v1/interviews/sessions/" + SESSION + "/turns/" + TURN + "/reprocess")
                        .header("X-User-Id", "uid-a"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TURN_NOT_RETRYABLE"));
    }

    @Test
    @DisplayName("Reintentar sin X-User-Id responde 401")
    void reprocess_shouldReturn401_whenIdentityMissing() throws Exception {
        mockMvc.perform(post("/api/v1/interviews/sessions/" + SESSION + "/turns/" + TURN + "/reprocess"))
                .andExpect(status().isUnauthorized());
    }
}
