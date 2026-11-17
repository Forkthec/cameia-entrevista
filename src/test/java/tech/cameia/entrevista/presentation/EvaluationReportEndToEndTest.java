package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiProvider;

/**
 * Reporte de Evaluación contra el servidor completo y PostgreSQL real: se termina una entrevista de cinco preguntas con
 * el modelo y el transcriptor simulados y se consulta el reporte. Ninguna prueba usa un proveedor real.
 */
@AnswerFlowSupport.AnswerFlowTest
@Import(ReportFlowSupport.Transcribers.class)
class EvaluationReportEndToEndTest extends ReportFlowSupport {

    @Test
    @DisplayName("El reporte lista las cinco preguntas con su origen, la voz de las habladas y la fecha y el tono")
    void questions_shouldListFiveAnswersWithOriginAndVoice() {
        String id = finishedMixedSession(UID_A, PROFILE_A);
        model.enqueue(() -> response(REPORT_OK));

        ResponseEntity<String> response = report(UID_A, id);

        assertThat(status(response)).isEqualTo(200);
        String body = response.getBody();
        assertThat((List<?>) JsonPath.read(body, "$.questions")).hasSize(5);
        assertThat((List<Integer>) JsonPath.read(body, "$.questions[*].number")).containsExactly(1, 2, 3, 4, 5);
        assertThat((List<String>) JsonPath.read(body, "$.questions[*].answerOrigin"))
                .containsExactly("TEXT", "VOICE", "TEXT", "VOICE", "TEXT");
        assertThat((String) JsonPath.read(body, "$.questions[1].answerText")).isEqualTo(SPOKEN);
        assertThat((String) JsonPath.read(body, "$.questions[0].answerText")).isEqualTo(ANSWER);
        assertThat((String) JsonPath.read(body, "$.questions[1].questionText")).isEqualTo(QUESTIONS[1]);
        assertThat((Map<String, Object>) JsonPath.read(body, "$.questions[1].voice"))
                .containsEntry("words", 3).containsEntry("fillers", 1).containsEntry("speechRate", 120)
                .containsEntry("pauseTime", 40.0).containsEntry("hesitationRate", 33.3);
        assertThat((Object) JsonPath.read(body, "$.questions[0].voice")).isNull();
        assertThat((Object) JsonPath.read(body, "$.questions[2].voice")).isNull();
        assertThat((String) JsonPath.read(body, "$.interviewDate")).isEqualTo("2026-10-20T15:00:00Z");
        assertThat((String) JsonPath.read(body, "$.tone")).isEqualTo("CALIDO");
        assertThat((String) JsonPath.read(body, "$.sessionLanguage")).isEqualTo("es-CO");
    }

    @Test
    @DisplayName("Una sesión por texto da el contenido y el STAR y deja la voz sin resultado")
    void textSession_shouldReturnContentAndStarAndNullVoice() {
        String id = finishedTextSession(UID_A, PROFILE_A);
        model.enqueue(() -> response(REPORT_OK));

        ResponseEntity<String> response = report(UID_A, id);

        assertThat(status(response)).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("application/json;charset=UTF-8");
        String body = response.getBody();
        assertThat(((Number) JsonPath.read(body, "$.metrics[0].value")).doubleValue()).isEqualTo(3.0);
        assertThat(((Number) JsonPath.read(body, "$.metrics[1].value")).doubleValue()).isEqualTo(3.25);
        assertThat(((Number) JsonPath.read(body, "$.metrics[2].value")).doubleValue()).isEqualTo(4.0);
        assertThat((List<String>) JsonPath.read(body, "$.metrics[*].code")).containsExactly("PRECISION_CLARITY",
                "VERBAL_CHARISMA", "INCLUSIVE_LANGUAGE", "SPEECH_RATE", "PAUSE_TIME", "HESITATION_RATE",
                "ENTHUSIASM");
        for (int voiceMetric = 3; voiceMetric <= 6; voiceMetric++) {
            assertThat((Object) JsonPath.read(body, "$.metrics[" + voiceMetric + "].value")).isNull();
        }
        assertThat((List<Integer>) JsonPath.read(body, "$.star[*].questionNumber")).containsExactly(3);
        assertThat((List<Integer>) JsonPath.read(body, "$.star[*].level")).containsExactly(4);
        assertThat(model.calls()).isEqualTo(1);
        assertThat(reportExecutions(id, "EXITOSA")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.reporte_evaluacion WHERE sesion_id = ?::uuid", id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Las métricas de voz salen solo de las respuestas habladas")
    void mixedSession_shouldUseOnlyVoiceAnswers() {
        String id = finishedMixedSession(UID_A, PROFILE_A);
        model.enqueue(() -> response(REPORT_OK));

        String body = report(UID_A, id).getBody();

        assertThat(((Number) JsonPath.read(body, "$.metrics[3].value")).intValue()).isEqualTo(120);
        assertThat(((Number) JsonPath.read(body, "$.metrics[4].value")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) JsonPath.read(body, "$.metrics[5].value")).doubleValue()).isEqualTo(33.3);
        assertThat((Object) JsonPath.read(body, "$.metrics[6].value")).isNull();
        assertThat((Object) JsonPath.read(body, "$.metrics[6].unit")).isNull();
    }

    @Test
    @DisplayName("Si la IA falla tres veces el reporte sale igual, con el contenido y el STAR en null")
    void threeFailedAttempts_shouldEmitWithNullContent() {
        String id = finishedMixedSession(UID_A, PROFILE_A);
        model.enqueueCycle(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });

        ResponseEntity<String> response = report(UID_A, id);

        assertThat(status(response)).isEqualTo(200);
        String body = response.getBody();
        for (int content = 0; content <= 2; content++) {
            assertThat((Object) JsonPath.read(body, "$.metrics[" + content + "].value")).isNull();
        }
        assertThat(((Number) JsonPath.read(body, "$.metrics[3].value")).intValue()).isEqualTo(120);
        assertThat((List<Object>) JsonPath.read(body, "$.star[*].level")).containsExactly((Object) null);
        assertThat((List<Object>) JsonPath.read(body, "$.star[*].situation")).containsExactly((Object) null);
        assertThat(model.calls()).isEqualTo(3);
        assertThat(reportExecutions(id, "FALLIDA")).isEqualTo(3);
        assertThat(reportExecutions(id, "EN_CURSO")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.reporte_evaluacion WHERE sesion_id = ?::uuid", id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Un límite de uso del principal pasa al respaldo y el reporte se calcula con él")
    void limitOnPrimary_shouldUseFallback() {
        String id = finishedTextSession(UID_A, PROFILE_A);
        model.fallbackAvailable(true);
        model.enqueue(() -> {
            throw new LlmLimitReachedException(new RuntimeException("cuota"));
        });
        model.enqueue(() -> response(REPORT_OK));

        ResponseEntity<String> response = report(UID_A, id);

        assertThat(status(response)).isEqualTo(200);
        assertThat(model.providers()).containsExactly(AiProvider.PRIMARY, AiProvider.FALLBACK);
        assertThat(((Number) JsonPath.read(response.getBody(), "$.metrics[0].value")).doubleValue()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Una segunda lectura devuelve el mismo cuerpo sin llamar a la IA")
    void secondRead_shouldReturnTheSameBodyWithoutCallingTheAi() {
        String id = finishedMixedSession(UID_A, PROFILE_A);
        model.enqueue(() -> response(REPORT_OK));

        String first = report(UID_A, id).getBody();
        String second = report(UID_A, id).getBody();
        String third = report(UID_A, id).getBody();

        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
        assertThat(model.calls()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.ejecucion_ia WHERE sesion_id = ?::uuid"
                + " AND tipo_operacion = 'GENERAR_REPORTE'", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una entrevista abandonada responde 409 SESSION_ABANDONED")
    void abandoned_shouldReturn409() {
        String id = startedSession(UID_A, PROFILE_A);
        forceStatus(id, "ABANDONADA");

        ResponseEntity<String> response = report(UID_A, id);

        assertThat(status(response)).isEqualTo(409);
        assertThat(code(response)).isEqualTo("SESSION_ABANDONED");
        assertThat((String) JsonPath.read(response.getBody(), "$.detail"))
                .isEqualTo("Sesión abandonada: no genera evaluación.");
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Una entrevista configurada, en curso o evaluándose responde 409 SESSION_NOT_FINISHED sin llamar a la IA")
    void notFinished_shouldReturn409() {
        String configured = configure(UID_A, PROFILE_A);
        assertNotFinished(report(UID_A, configured));

        String running = startedSession(UID_B, PROFILE_B);
        assertNotFinished(report(UID_B, running));

        forceStatus(running, "EVALUANDO");
        assertNotFinished(report(UID_B, running));
        assertThat(model.calls()).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.ejecucion_ia WHERE tipo_operacion = 'GENERAR_REPORTE'"))
                .isZero();
    }

    private static void assertNotFinished(ResponseEntity<String> response) {
        assertThat(status(response)).isEqualTo(409);
        assertThat(code(response)).isEqualTo("SESSION_NOT_FINISHED");
        assertThat((String) JsonPath.read(response.getBody(), "$.detail")).isEqualTo("Tu entrevista aún no ha terminado.");
    }

    @Test
    @DisplayName("Una sesión ajena, inexistente o mal escrita responde el mismo 404")
    void foreignMissingAndMalformed_shouldReturnTheSame404() {
        String id = finishedTextSession(UID_A, PROFILE_A);

        ResponseEntity<String> foreign = report(UID_B, id);
        ResponseEntity<String> missing = report(UID_A, UUID.randomUUID().toString());
        ResponseEntity<String> malformed = report(UID_A, "no-es-un-uuid");

        for (ResponseEntity<String> response : List.of(foreign, missing, malformed)) {
            assertThat(status(response)).isEqualTo(404);
            assertThat(code(response)).isEqualTo("SESSION_NOT_FOUND");
        }
        assertThat(withoutVariableParts(missing)).isEqualTo(withoutVariableParts(foreign));
        assertThat(withoutVariableParts(malformed)).isEqualTo(withoutVariableParts(foreign));
        assertThat(model.calls()).isZero();
    }

    // El requestId cambia en cada petición y la ruta repite lo que la persona escribió: el resto debe ser idéntico.
    private static Map<String, Object> withoutVariableParts(ResponseEntity<String> response) {
        Map<String, Object> body = new HashMap<>(JsonPath.read(response.getBody(), "$"));
        body.remove("requestId");
        body.remove("instance");
        return body;
    }

    @Test
    @DisplayName("Sin identidad responde 401 IDENTITY_REQUIRED")
    void noIdentity_shouldReturn401() {
        String id = finishedTextSession(UID_A, PROFILE_A);

        ResponseEntity<String> response = report(null, id);

        assertThat(status(response)).isEqualTo(401);
        assertThat(code(response)).isEqualTo("IDENTITY_REQUIRED");
    }

    @Test
    @DisplayName("Si la IA entrega una salida inválida y luego una válida, el reporte se calcula con la segunda")
    void invalidThenValidOutput_shouldUseTheSecondAttempt() {
        String id = finishedTextSession(UID_A, PROFILE_A);
        model.enqueue(() -> response("no es JSON"));
        model.enqueue(() -> response(REPORT_OK));

        ResponseEntity<String> response = report(UID_A, id);

        assertThat(status(response)).isEqualTo(200);
        assertThat(((Number) JsonPath.read(response.getBody(), "$.metrics[0].value")).doubleValue()).isEqualTo(3.0);
        assertThat(model.calls()).isEqualTo(2);
        assertThat(reportExecutions(id, "FALLIDA")).isEqualTo(1);
        assertThat(reportExecutions(id, "EXITOSA")).isEqualTo(1);
    }
}
