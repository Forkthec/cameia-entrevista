package tech.cameia.entrevista.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.service.GetEvaluationReportService;
import tech.cameia.entrevista.application.service.ReportOutcome;
import tech.cameia.entrevista.application.service.ReportOutcome.QuestionRow;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionNotFinishedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;
import tech.cameia.entrevista.domain.model.ContentScores;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.StarResult;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.VoiceMetrics;
import tech.cameia.entrevista.domain.model.VoiceSample;
import tech.cameia.entrevista.presentation.advice.ProblemDetailTestSupport;
import tech.cameia.entrevista.presentation.filter.RequestIdFilter;

/** Consulta del reporte: el cuerpo exacto del contrato, el 202, los errores y la identidad. */
class EvaluationReportControllerTest {

    private static final UUID SESSION = UUID.fromString("3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10");

    private static final String URL = "/api/v1/interviews/sessions/" + SESSION + "/evaluation-report";

    private static final String CONDUCTUAL_TEXT = "Cuéntame de una vez que resolviste un conflicto en equipo.";

    private static final String QUESTION_2 = "¿Qué diferencia hay entre una transacción optimista y una pesimista en"
            + " una base de datos?";

    private static final String EXPECTED = """
            {"sessionId":"3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10","issuedAt":"2026-11-17T15:04:05Z",
             "sessionLanguage":"es-CO","interviewDate":"2026-11-17T14:58:40Z","tone":"PROFESIONAL_NEUTRO",
             "metrics":[
              {"code":"PRECISION_CLARITY","group":"CONTENT","value":3.46,"unit":"SCORE_1_5"},
              {"code":"VERBAL_CHARISMA","group":"CONTENT","value":4.00,"unit":"SCORE_1_5"},
              {"code":"INCLUSIVE_LANGUAGE","group":"CONTENT","value":5.00,"unit":"SCORE_1_5"},
              {"code":"SPEECH_RATE","group":"VOICE","value":120,"unit":"WORDS_PER_MINUTE"},
              {"code":"PAUSE_TIME","group":"VOICE","value":12.0,"unit":"PERCENT"},
              {"code":"HESITATION_RATE","group":"VOICE","value":2.0,"unit":"PER_100_WORDS"},
              {"code":"ENTHUSIASM","group":"VOICE","value":null,"unit":null}],
             "star":[{"questionNumber":3,"questionText":"%1$s","situation":true,"task":true,"action":true,
               "result":false,"level":3}],
             "questions":[
              {"number":2,"type":"TECNICA","questionText":"%2$s",
               "answerText":"Eh, pues las dos sirven para lo mismo.","answerOrigin":"VOICE",
               "voice":{"words":56,"fillers":4,"speechRate":110,"pauseTime":28.3,"hesitationRate":7.1}},
              {"number":3,"type":"CONDUCTUAL","questionText":"%1$s","answerText":"En el proyecto final de redes…",
               "answerOrigin":"TEXT","voice":null}]}
            """.formatted(CONDUCTUAL_TEXT, QUESTION_2);

    private final GetEvaluationReportService service = mock(GetEvaluationReportService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EvaluationReportController(service))
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler()).build();

    private static ReportOutcome.Issued issued(List<StarResult> star, List<QuestionRow> rows, EvaluationReport report) {
        return new ReportOutcome.Issued(report, Instant.parse("2026-11-17T14:58:40Z"), Tone.PROFESSIONAL_NEUTRAL,
                rows);
    }

    private static EvaluationReport fullReport(List<StarResult> star) {
        return new EvaluationReport(SESSION, InterviewLanguage.SPANISH_COLOMBIA,
                new ContentScores(new BigDecimal("3.46"), new BigDecimal("4.00"), new BigDecimal("5.00")),
                new VoiceMetrics(120, new BigDecimal("12.0"), new BigDecimal("2.0")), star,
                Instant.parse("2026-11-17T15:04:05Z"));
    }

    @Test
    @DisplayName("El reporte listo se mapea al cuerpo del contrato, con las escalas guardadas y los null")
    void ready_shouldMapTheContract() throws Exception {
        List<StarResult> star = List.of(new StarResult(3, CONDUCTUAL_TEXT, true, true, true, false));
        List<QuestionRow> rows = List.of(
                new QuestionRow(2, QuestionType.TECNICA, QUESTION_2, "Eh, pues las dos sirven para lo mismo.",
                        AnswerOrigin.AUDIO, new VoiceSample(56, 30473, 8623, 4)),
                new QuestionRow(3, QuestionType.CONDUCTUAL, CONDUCTUAL_TEXT, "En el proyecto final de redes…",
                        AnswerOrigin.TEXTO, null));
        when(service.report(any())).thenReturn(issued(star, rows, fullReport(star)));

        MvcResult result = mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(content().json(EXPECTED, true)).andReturn();
        // Las escalas guardadas se conservan en el texto: 4.00, 5.00 y 12.0 no se recortan a 4, 5 y 12.
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"value\":4.00").contains("\"value\":5.00").contains("\"value\":12.0")
                .contains("\"value\":120,").contains("\"value\":null,\"unit\":null");
    }

    @Test
    @DisplayName("Una pregunta respondida por texto o sin respuesta lleva voice en null")
    void questionWithoutVoice_shouldHaveNullVoice() throws Exception {
        List<QuestionRow> rows = List.of(
                new QuestionRow(1, QuestionType.APERTURA, "Preséntate", "Hola", AnswerOrigin.TEXTO, null),
                new QuestionRow(2, QuestionType.TECNICA, "Explica", null, null, null));
        when(service.report(any())).thenReturn(issued(List.of(), rows, fullReport(List.of())));

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].voice").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.questions[0].answerOrigin").value("TEXT"))
                .andExpect(jsonPath("$.questions[1].answerText").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.questions[1].answerOrigin").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"voice\":null")));
    }

    @Test
    @DisplayName("Sin preguntas conductuales el STAR es una lista vacía")
    void noBehavioral_shouldReturnEmptyStar() throws Exception {
        when(service.report(any())).thenReturn(issued(List.of(), List.of(), fullReport(List.of())));

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.star").isArray()).andExpect(jsonPath("$.star").isEmpty());
    }

    @Test
    @DisplayName("Un reporte sin calificación de la IA lleva los valores y el STAR en null, nunca 0")
    void noAiResult_shouldHaveNullValues() throws Exception {
        List<StarResult> star = List.of(StarResult.unavailable(3, CONDUCTUAL_TEXT));
        EvaluationReport report = new EvaluationReport(SESSION, InterviewLanguage.ENGLISH, ContentScores.NONE,
                VoiceMetrics.NONE, star, Instant.parse("2026-11-17T15:04:05Z"));
        when(service.report(any())).thenReturn(issued(star, List.of(), report));

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionLanguage").value("en"))
                .andExpect(jsonPath("$.metrics[0].value").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.metrics[0].unit").value("SCORE_1_5"))
                .andExpect(jsonPath("$.metrics[3].value").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.metrics[6].unit").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.star[0].level").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.star[0].situation").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("Mientras otra petición genera el reporte responde 202 con código, mensaje y requestId")
    void generating_shouldAnswer202WithCodeMessageAndRequestId() throws Exception {
        when(service.report(any())).thenReturn(new ReportOutcome.Generating());

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a").requestAttr(RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                        "req-123"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(content().json("""
                        {"code":"REPORT_GENERATING","message":"Tu reporte se está generando.","requestId":"req-123"}
                        """, true));
    }

    @Test
    @DisplayName("El caso de uso recibe el dueño del encabezado y el identificador de la ruta")
    void shouldPassOwnerAndSessionToTheService() throws Exception {
        when(service.report(any())).thenReturn(new ReportOutcome.Generating());

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isAccepted());

        ArgumentCaptor<SessionCommand> command = ArgumentCaptor.forClass(SessionCommand.class);
        verify(service).report(command.capture());
        assertThat(command.getValue().sessionId()).isEqualTo(SESSION);
        assertThat(command.getValue().owner().value()).isEqualTo("uid-a");
    }

    @Test
    @DisplayName("El plan del usuario no cambia nada: el cuerpo es el mismo con y sin X-User-Plan")
    void plan_shouldNotChangeTheBody() throws Exception {
        when(service.report(any())).thenReturn(issued(List.of(), List.of(), fullReport(List.of())));

        String free = mockMvc.perform(get(URL).header("X-User-Id", "uid-a").header("X-User-Plan", "FREE"))
                .andReturn().getResponse().getContentAsString();
        String premium = mockMvc.perform(get(URL).header("X-User-Id", "uid-a").header("X-User-Plan", "PREMIUM"))
                .andReturn().getResponse().getContentAsString();

        assertThat(free).isEqualTo(premium).isNotEmpty();
    }

    @Test
    @DisplayName("Sin identidad responde 401")
    void missingIdentity_shouldReturn401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    @DisplayName("Una sesión ajena, inexistente o mal escrita responde 404 SESSION_NOT_FOUND")
    void notFound_shouldReturn404() throws Exception {
        when(service.report(any())).thenThrow(new SessionNotFoundException());

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No encontramos lo que buscabas."));
    }

    @Test
    @DisplayName("Una entrevista sin terminar responde 409 SESSION_NOT_FINISHED con su mensaje")
    void notFinished_shouldReturn409() throws Exception {
        when(service.report(any())).thenThrow(new SessionNotFinishedException());

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FINISHED"))
                .andExpect(jsonPath("$.title").value("Entrevista sin terminar"))
                .andExpect(jsonPath("$.detail").value("Tu entrevista aún no ha terminado."));
    }

    @Test
    @DisplayName("Una entrevista abandonada responde 409 SESSION_ABANDONED")
    void abandoned_shouldReturn409() throws Exception {
        when(service.report(any())).thenThrow(new SessionAbandonedException());

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ABANDONED"))
                .andExpect(jsonPath("$.detail").value("Sesión abandonada: no genera evaluación."));
    }

    @Test
    @DisplayName("Si la base no responde, 503 DATABASE_UNAVAILABLE")
    void databaseDown_shouldReturn503() throws Exception {
        when(service.report(any())).thenThrow(new DatabaseUnavailableException(new RuntimeException("caída")));

        mockMvc.perform(get(URL).header("X-User-Id", "uid-a")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Un método distinto de GET responde 405 y un Accept que no es JSON, 406")
    void wrongMethodOrAccept_shouldBeRejected() throws Exception {
        mockMvc.perform(post(URL).header("X-User-Id", "uid-a")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get(URL).header("X-User-Id", "uid-a").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable());
    }
}
