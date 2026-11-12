package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/** Responder la pregunta pendiente contra el servidor completo y PostgreSQL real, sin llamar al proveedor real. */
@AnswerFlowSupport.AnswerFlowTest
class AnswerEndToEndTest extends AnswerFlowSupport {

    private static final String NOT_FOUND = "No encontramos lo que buscabas.";

    // ---- camino feliz ----

    @Test
    @DisplayName("Responder completa el turno, avanza a la pregunta 2 y devuelve su texto original")
    void answer_shouldCompleteTurnAndReturnNext_whenEvaluationSucceeds() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(response)).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getBody(), "$.turn.turnId")).isEqualTo(turn1);
        assertThat((String) JsonPath.read(response.getBody(), "$.turn.status")).isEqualTo("COMPLETED");
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.currentQuestion")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.totalQuestions")).isEqualTo(5);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.nextQuestion.number")).isEqualTo(2);
        assertThat((String) JsonPath.read(response.getBody(), "$.nextQuestion.text")).isEqualTo(QUESTIONS[1]);
        assertThat((String) JsonPath.read(response.getBody(), "$.nextQuestion.turnId")).isEqualTo(turnId(id, 2));
        assertThat((Boolean) JsonPath.read(response.getBody(), "$.interviewFinished")).isFalse();
        assertThat(turnStatus(turn1)).isEqualTo("COMPLETED");
        assertThat(turnStatus(turnId(id, 2))).isEqualTo("PENDING");
        assertThat(model.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("La respuesta nunca trae la retroalimentación ni la clasificación de la evaluación")
    void answer_shouldNotExposeFeedback_whenEvaluationSucceeds() {
        String id = startedSession(UID_A, PROFILE_A);
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = answer(UID_A, id, turnId(id, 1), ANSWER, KEY);

        assertThat(response.getBody()).doesNotContain("feedback", "clasificacion", "motivo", "star", "score",
                "Buena estructura", "Responde lo pedido");
    }

    @Test
    @DisplayName("Repetir la misma clave con la misma respuesta devuelve el mismo cuerpo sin otra llamada a la IA")
    void answer_shouldReplaySameBody_whenSameKeyRepeated() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(AnswerFlowSupport::evaluation);
        ResponseEntity<String> first = answer(UID_A, id, turn1, ANSWER, KEY);

        ResponseEntity<String> second = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(second)).isEqualTo(200);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(model.calls()).isEqualTo(1);
        assertThat(rows()).containsExactly(1, 1, 1, 2, 1);
    }

    @Test
    @DisplayName("La misma clave 20 veces seguidas deja un intento, una ejecución y una retroalimentación")
    void answer_shouldApplyOnce_whenSameKeyRepeated20Times() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(AnswerFlowSupport::evaluation);
        String first = answer(UID_A, id, turn1, ANSWER, KEY).getBody();

        for (int i = 0; i < 19; i++) {
            ResponseEntity<String> again = answer(UID_A, id, turn1, ANSWER, KEY);
            assertThat(status(again)).isEqualTo(200);
            assertThat(again.getBody()).isEqualTo(first);
        }

        assertThat(model.calls()).isEqualTo(1);
        assertThat(rows()).containsExactly(1, 1, 1, 2, 1);
    }

    @Test
    @DisplayName("La respuesta 5 termina la entrevista sin sexto turno y la sesión queda FINALIZADA")
    void answer_shouldFinishWithoutSixthTurn_whenQuestionFive() {
        String id = startedSession(UID_A, PROFILE_A);
        ResponseEntity<String> last = null;
        for (int number = 1; number <= 5; number++) {
            model.enqueue(AnswerFlowSupport::evaluation);
            last = answer(UID_A, id, turnId(id, number), ANSWER, "clave-" + number);
            assertThat(status(last)).isEqualTo(200);
        }

        assertThat((Integer) JsonPath.read(last.getBody(), "$.progress.currentQuestion")).isEqualTo(5);
        assertThat(last.getBody()).contains("\"nextQuestion\":null");
        assertThat((Boolean) JsonPath.read(last.getBody(), "$.interviewFinished")).isTrue();
        assertThat(count("SELECT count(*) FROM microentrevista.turno")).isEqualTo(5);
        assertThat(sessionStatus(id)).isEqualTo("FINALIZADA");
        ResponseEntity<String> reloaded = state(UID_A, id);
        assertThat((Boolean) JsonPath.read(reloaded.getBody(), "$.interviewFinished")).isTrue();
        assertThat((Integer) JsonPath.read(reloaded.getBody(), "$.history.length()")).isEqualTo(5);
    }

    @Test
    @DisplayName("Una respuesta de 4000 caracteres se guarda completa, sin recortar")
    void answer_shouldStoreFullText_when4000CodePoints() {
        String id = startedSession(UID_A, PROFILE_A);
        String text = "á".repeat(3998) + "😀";
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = answer(UID_A, id, turnId(id, 1), text, KEY);

        assertThat(status(response)).isEqualTo(200);
        String stored = jdbc.queryForObject("SELECT respuesta_texto FROM microentrevista.intento_respuesta",
                String.class);
        assertThat(stored).isEqualTo(text);
        assertThat(stored.codePointCount(0, stored.length())).isEqualTo(3999);
    }

    @Test
    @DisplayName("Se guarda la respuesta tal cual, con sus espacios, y la pregunta mostrada")
    void answer_shouldPersistFeedbackAndOneExecution_whenEvaluationSucceeds() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        String padded = "  " + ANSWER + " ";
        model.enqueue(AnswerFlowSupport::evaluation);

        answer(UID_A, id, turn1, padded, KEY);

        assertThat(jdbc.queryForObject("SELECT respuesta_texto FROM microentrevista.intento_respuesta", String.class))
                .isEqualTo(padded);
        assertThat(jdbc.queryForObject("SELECT pregunta_mostrada FROM microentrevista.intento_respuesta",
                String.class)).isEqualTo(QUESTIONS[0]);
        assertThat(jdbc.queryForObject("SELECT origen_respuesta FROM microentrevista.intento_respuesta",
                String.class)).isEqualTo("TEXTO");
        assertThat(evaluations(turn1, "EXITOSA")).isEqualTo(1);
        assertThat(evaluations(turn1, "EN_CURSO")).isZero();
        assertThat(jdbc.queryForObject("SELECT version_prompt FROM microentrevista.ejecucion_ia"
                + " WHERE tipo_operacion = 'EVALUAR'", String.class)).isEqualTo("evaluacion.v1");
        assertThat(jdbc.queryForObject("SELECT feedback ->> 'feedback' FROM microentrevista.feedback_turno",
                String.class)).isEqualTo("Buena estructura");
        assertThat(jdbc.queryForObject("SELECT feedback ->> 'clasificacion' FROM microentrevista.feedback_turno",
                String.class)).isEqualTo("COMPLETA");
        assertThat(jdbc.queryForObject("SELECT feedback ? 'follow_up_texto' FROM microentrevista.feedback_turno",
                Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("La pregunta siguiente es la del plan aunque el modelo proponga otro texto")
    void answer_shouldReturnOriginalNextQuestion_evenIfModelProposesOtherText() {
        String id = startedSession(UID_A, PROFILE_A);
        model.enqueue(() -> response("{\"accion\":\"FOLLOW_UP\",\"clasificacion\":\"VAGA\","
                + "\"motivo\":\"Falta detalle\",\"follow_up_texto\":\"¿Y si fallara la cola?\","
                + "\"feedback\":\"Amplía el ejemplo\"}"));

        ResponseEntity<String> response = answer(UID_A, id, turnId(id, 1), ANSWER, KEY);

        assertThat((String) JsonPath.read(response.getBody(), "$.nextQuestion.text")).isEqualTo(QUESTIONS[1]);
        assertThat(response.getBody()).doesNotContain("fallara la cola");
    }

    @Test
    @DisplayName("Con FOLLOW_UP avanza igual y no guarda el texto del seguimiento")
    void answer_shouldAdvanceWithoutFollowUpText_whenModelProposesFollowUp() {
        String id = startedSession(UID_A, PROFILE_A);
        model.enqueue(() -> response("{\"accion\":\"FOLLOW_UP\",\"clasificacion\":\"VAGA\","
                + "\"motivo\":\"Falta detalle\",\"follow_up_texto\":\"¿Y si fallara la cola?\","
                + "\"feedback\":\"Amplía el ejemplo\"}"));

        ResponseEntity<String> response = answer(UID_A, id, turnId(id, 1), ANSWER, KEY);

        assertThat(status(response)).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getBody(), "$.turn.status")).isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno WHERE feedback::text LIKE"
                + " '%fallara la cola%'")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.turno")).isEqualTo(2);
    }

    @Test
    @DisplayName("La retroalimentación STAR queda nula con cada tipo de pregunta")
    void answer_shouldStoreNullStar_forEveryQuestionType() {
        String id = startedSession(UID_A, PROFILE_A);
        for (int number = 1; number <= 5; number++) {
            model.enqueue(AnswerFlowSupport::evaluation);
            assertThat(status(answer(UID_A, id, turnId(id, number), ANSWER, "k" + number))).isEqualTo(200);
        }

        assertThat(count("SELECT count(DISTINCT tipo) FROM microentrevista.pregunta")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno WHERE feedback_star IS NULL"))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Los campos que sobran del cuerpo se ignoran")
    void answer_shouldIgnoreUnexpectedFields() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(AnswerFlowSupport::evaluation);
        String json = "{\"turnId\":\"" + turn1 + "\",\"answerText\":\"" + ANSWER + "\",\"status\":\"COMPLETED\","
                + "\"sessionId\":\"x\",\"owner\":\"uid-otro\",\"feedback\":\"inventado\"}";

        ResponseEntity<String> response = answerRaw(UID_A, id, KEY, json);

        assertThat(status(response)).isEqualTo(200);
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno WHERE feedback::text LIKE"
                + " '%inventado%'")).isZero();
    }

    @Test
    @DisplayName("El dueño sale solo de X-User-Id aunque el cuerpo traiga otro")
    void answer_shouldUseHeaderOwner_whenBodyCarriesOtherOwner() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        String json = "{\"turnId\":\"" + turn1 + "\",\"answerText\":\"" + ANSWER + "\",\"firebaseUid\":\""
                + UID_A + "\"}";

        ResponseEntity<String> response = answerRaw(UID_B, id, KEY, json);

        assertThat(status(response)).isEqualTo(404);
        assertThat(code(response)).isEqualTo("SESSION_NOT_FOUND");
        assertThat(model.calls()).isZero();
    }

    // ---- validación ----

    @Test
    @DisplayName("Sin turnId, sin respuesta y sin clave devuelve los tres errores a la vez, en orden")
    void answer_shouldReturnAllFieldErrorsAtOnce() {
        String id = startedSession(UID_A, PROFILE_A);
        List<Integer> before = rows();

        ResponseEntity<String> response = answerRaw(UID_A, id, null, "{}");

        assertThat(status(response)).isEqualTo(422);
        assertThat(code(response)).isEqualTo("VALIDATION_FAILED");
        assertThat((String) JsonPath.read(response.getBody(), "$.detail")).isEqualTo("Revisa los campos marcados.");
        assertThat((List<String>) JsonPath.read(response.getBody(), "$.errors[*].field"))
                .containsExactly("turnId", "answerText", "Idempotency-Key");
        assertThat((List<String>) JsonPath.read(response.getBody(), "$.errors[*].code"))
                .containsExactly("TURN_ID_REQUIRED", "ANSWER_TEXT_REQUIRED", "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(rows()).isEqualTo(before);
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Un turnId mal escrito y una clave con caracteres no admitidos son errores de campo")
    void answer_shouldReturn422_whenTurnIdOrKeyMalformed() {
        String id = startedSession(UID_A, PROFILE_A);

        ResponseEntity<String> response = answerRaw(UID_A, id, "clave con espacios",
                "{\"turnId\":\"no-es-uuid\",\"answerText\":\"hola\"}");

        assertThat(status(response)).isEqualTo(422);
        assertThat((List<String>) JsonPath.read(response.getBody(), "$.errors[*].code"))
                .containsExactly("TURN_ID_INVALID_FORMAT", "IDEMPOTENCY_KEY_INVALID_FORMAT");
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Una respuesta de 4001 caracteres responde 422 y no guarda nada")
    void answer_shouldReturn422WithoutSaving_whenTooLong() {
        String id = startedSession(UID_A, PROFILE_A);
        List<Integer> before = rows();

        ResponseEntity<String> response = answer(UID_A, id, turnId(id, 1), "a".repeat(4001), KEY);

        assertThat(status(response)).isEqualTo(422);
        assertThat((List<String>) JsonPath.read(response.getBody(), "$.errors[*].code"))
                .containsExactly("ANSWER_TEXT_TOO_LONG");
        assertThat((String) JsonPath.read(response.getBody(), "$.errors[0].message"))
                .isEqualTo("Tu respuesta no puede superar los 4000 caracteres.");
        assertThat(rows()).isEqualTo(before);
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Un millón de caracteres responde 422 ANSWER_TEXT_TOO_LONG, no el error genérico")
    void answer_shouldReturn422_whenHugeText() {
        String id = startedSession(UID_A, PROFILE_A);

        ResponseEntity<String> response = answer(UID_A, id, turnId(id, 1), "a".repeat(1_000_000), KEY);

        assertThat(status(response)).isEqualTo(422);
        assertThat((List<String>) JsonPath.read(response.getBody(), "$.errors[*].code"))
                .containsExactly("ANSWER_TEXT_TOO_LONG");
    }

    @Test
    @DisplayName("Una respuesta vacía o de solo espacios responde 422 y el turno sigue pendiente")
    void answer_shouldKeepTurnPending_whenBlank() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        for (String blank : List.of("", "   ", "\t\n", " ​")) {
            ResponseEntity<String> response = answer(UID_A, id, turn1, blank, KEY);

            assertThat(status(response)).as("«%s»", blank).isEqualTo(422);
            assertThat((String) JsonPath.read(response.getBody(), "$.errors[0].message"))
                    .isEqualTo("Ingresa tu respuesta.");
        }
        assertThat(turnStatus(turn1)).isEqualTo("PENDING");
        assertThat(rows()).containsExactly(0, 0, 0, 1, 0);
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Un cuerpo ilegible responde 422 REQUEST_BODY_INVALID_FORMAT y un tipo de contenido ajeno, 415")
    void answer_shouldReturn422Or415_whenBodyUnreadable() {
        String id = startedSession(UID_A, PROFILE_A);

        ResponseEntity<String> broken = answerRaw(UID_A, id, KEY, "{\"turnId\":");
        ResponseEntity<String> object = answerRaw(UID_A, id, KEY,
                "{\"turnId\":\"" + turnId(id, 1) + "\",\"answerText\":{\"a\":1}}");

        assertThat(status(broken)).isEqualTo(422);
        assertThat(code(broken)).isEqualTo("REQUEST_BODY_INVALID_FORMAT");
        assertThat(status(object)).isEqualTo(422);
        assertThat(code(object)).isEqualTo("REQUEST_BODY_INVALID_FORMAT");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
        headers.set("X-User-Id", UID_A);
        headers.set("Idempotency-Key", KEY);
        ResponseEntity<String> plain = client.exchange(BASE + "/" + id + "/answers", HttpMethod.POST,
                new org.springframework.http.HttpEntity<>("hola", headers), String.class);
        assertThat(status(plain)).isEqualTo(415);
        assertThat(code(plain)).isEqualTo("MEDIA_TYPE_NOT_ALLOWED");
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Sin X-User-Id responde 401 IDENTITY_REQUIRED")
    void answer_shouldReturn401_whenIdentityMissing() {
        String id = startedSession(UID_A, PROFILE_A);

        ResponseEntity<String> response = answer(null, id, turnId(id, 1), ANSWER, KEY);

        assertThat(status(response)).isEqualTo(401);
        assertThat(code(response)).isEqualTo("IDENTITY_REQUIRED");
    }

    // ---- dueño, sesión y turno ----

    @Test
    @DisplayName("Una sesión ajena, inexistente o con id mal escrito da el mismo 404")
    void answer_shouldReturnSame404_whenSessionForeignMissingOrMalformed() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        List<Integer> before = rows();

        List<ResponseEntity<String>> responses = List.of(answer(UID_B, id, turn1, ANSWER, KEY),
                answer(UID_A, UUID.randomUUID().toString(), turn1, ANSWER, KEY),
                answer(UID_A, "abc", turn1, ANSWER, KEY));

        for (ResponseEntity<String> response : responses) {
            assertThat(status(response)).isEqualTo(404);
            assertThat(code(response)).isEqualTo("SESSION_NOT_FOUND");
            assertThat((String) JsonPath.read(response.getBody(), "$.detail")).isEqualTo(NOT_FOUND);
        }
        assertThat(rows()).isEqualTo(before);
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Un turno de otra sesión, propia o ajena, o inexistente responde 404 TURN_NOT_FOUND")
    void answer_shouldReturn404TurnNotFound_whenTurnIsNotOfTheSession() {
        String mine = startedSession(UID_A, PROFILE_A);
        String foreign = startedSession(UID_B, PROFILE_B);
        List<Integer> before = rows();

        for (String turn : List.of(turnId(foreign, 1), UUID.randomUUID().toString())) {
            ResponseEntity<String> response = answer(UID_A, mine, turn, ANSWER, KEY);

            assertThat(status(response)).isEqualTo(404);
            assertThat(code(response)).isEqualTo("TURN_NOT_FOUND");
            assertThat((String) JsonPath.read(response.getBody(), "$.detail")).isEqualTo(NOT_FOUND);
        }
        assertThat(rows()).isEqualTo(before);
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Con la sesión fuera de curso responde 409 con su código, sin guardar ni llamar a la IA")
    void answer_shouldReturn409_whenSessionNotInProgress() {
        String configured = configure(UID_A, PROFILE_A);
        ResponseEntity<String> notStarted = answer(UID_A, configured, UUID.randomUUID().toString(), ANSWER, KEY);
        assertThat(status(notStarted)).isEqualTo(409);
        assertThat(code(notStarted)).isEqualTo("SESSION_TRANSITION_NOT_ALLOWED");
        assertThat((String) JsonPath.read(notStarted.getBody(), "$.detail"))
                .isEqualTo("Esta entrevista ya no admite esa acción.");

        List<String[]> cases = List.of(new String[] {"EVALUANDO", "SESSION_ALREADY_CLOSED"},
                new String[] {"FINALIZADA", "SESSION_ALREADY_CLOSED"},
                new String[] {"ABANDONADA", "SESSION_ABANDONED"});
        for (String[] closed : cases) {
            jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia CASCADE");
            String id = startedSession(UID_A, PROFILE_A);
            String turn1 = turnId(id, 1);
            forceStatus(id, closed[0]);
            List<Integer> before = rows();

            ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

            assertThat(status(response)).as(closed[0]).isEqualTo(409);
            assertThat(code(response)).isEqualTo(closed[1]);
            assertThat(rows()).isEqualTo(before);
            assertThat(sessionStatus(id)).isEqualTo(closed[0]);
            assertThat(turnStatus(turn1)).isEqualTo("PENDING");
        }
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Con otra clave sobre un turno ya respondido responde 409 ANSWER_ALREADY_EXISTS")
    void answer_shouldReturn409AnswerAlreadyExists_whenOtherKeySameTurn() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(AnswerFlowSupport::evaluation);
        answer(UID_A, id, turn1, ANSWER, KEY);
        List<Integer> before = rows();

        ResponseEntity<String> response = answer(UID_A, id, turn1, "Otra respuesta", "otra-clave");

        assertThat(status(response)).isEqualTo(409);
        assertThat(code(response)).isEqualTo("ANSWER_ALREADY_EXISTS");
        assertThat((String) JsonPath.read(response.getBody(), "$.detail"))
                .isEqualTo("Esta pregunta ya tiene una respuesta registrada.");
        assertThat(rows()).isEqualTo(before);
        assertThat(model.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Responder la pregunta 1 cuando la pendiente es la 3 responde 409 ANSWER_ALREADY_EXISTS")
    void answer_shouldReturn409_whenAnsweringAnEarlierQuestion() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        for (int number = 1; number <= 2; number++) {
            model.enqueue(AnswerFlowSupport::evaluation);
            answer(UID_A, id, turnId(id, number), ANSWER, "k" + number);
        }

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, "k-nueva");

        assertThat(status(response)).isEqualTo(409);
        assertThat(code(response)).isEqualTo("ANSWER_ALREADY_EXISTS");
        assertThat(model.calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("Con la misma clave y otra respuesta responde 409 IDEMPOTENCY_KEY_CONFLICT")
    void answer_shouldReturn409IdempotencyKeyConflict_whenSameKeyOtherText() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(AnswerFlowSupport::evaluation);
        answer(UID_A, id, turn1, ANSWER, KEY);
        List<Integer> before = rows();

        ResponseEntity<String> response = answer(UID_A, id, turn1, "Otra respuesta distinta", KEY);

        assertThat(status(response)).isEqualTo(409);
        assertThat(code(response)).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(rows()).isEqualTo(before);
        assertThat(model.calls()).isEqualTo(1);
    }

    // ---- la IA falla ----

    @Test
    @DisplayName("Si la IA falla responde 503 con su código, guarda la respuesta y deja el turno en RETRYING")
    void answer_shouldReturn503AndKeepAnswer_whenModelFails() {
        List<Object[]> causes = List.of(
                new Object[] {new LlmUnavailableException(new RuntimeException("caída")), "LLM_UNAVAILABLE"},
                new Object[] {new LlmLimitReachedException(new RuntimeException("429")), "LLM_LIMIT_REACHED"},
                new Object[] {new LlmInvalidOutputException("estructura"), "LLM_INVALID_OUTPUT"});
        for (Object[] cause : causes) {
            jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia CASCADE");
            String id = startedSession(UID_A, PROFILE_A);
            String turn1 = turnId(id, 1);
            RuntimeException failure = (RuntimeException) cause[0];
            model.enqueue(() -> {
                throw failure;
            });

            ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

            assertThat(status(response)).isEqualTo(503);
            assertThat(code(response)).isEqualTo(cause[1]);
            assertThat((String) JsonPath.read(response.getBody(), "$.detail"))
                    .isEqualTo("Ocurrió un error. Inténtalo de nuevo.");
            assertThat(turnStatus(turn1)).isEqualTo("RETRYING");
            assertThat(sessionStatus(id)).isEqualTo("EN_CURSO");
            assertThat(evaluations(turn1, "FALLIDA")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT respuesta_texto FROM microentrevista.intento_respuesta",
                    String.class)).isEqualTo(ANSWER);
            assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isZero();
            assertThat(count("SELECT count(*) FROM microentrevista.turno")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Una salida del modelo con acción desconocida, motivo vacío o texto que no es JSON es LLM_INVALID_OUTPUT")
    void answer_shouldReturn503InvalidOutput_whenEvaluationIsOutsideTheSchema() {
        List<String> outputs = List.of("esto no es json", "[1,2]",
                "{\"accion\":\"OTRA\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"x\",\"feedback\":\"y\"}",
                "{\"accion\":\"AVANZAR\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"\",\"feedback\":\"y\"}",
                "{\"accion\":\"AVANZAR\",\"clasificacion\":\"RARA\",\"motivo\":\"x\",\"feedback\":\"y\"}",
                "{\"accion\":\"AVANZAR\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"x\"}");
        for (String output : outputs) {
            jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia CASCADE");
            String id = startedSession(UID_A, PROFILE_A);
            String turn1 = turnId(id, 1);
            model.enqueue(() -> response(output));

            ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

            assertThat(status(response)).as(output).isEqualTo(503);
            assertThat(code(response)).isEqualTo("LLM_INVALID_OUTPUT");
            assertThat(turnStatus(turn1)).isEqualTo("RETRYING");
        }
    }

    @Test
    @DisplayName("Repetir la misma clave tras un 503 devuelve el mismo 503 sin llamar otra vez a la IA")
    void answer_shouldReplay503_whenSameKeyAfterFailure() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });
        ResponseEntity<String> first = answer(UID_A, id, turn1, ANSWER, KEY);

        ResponseEntity<String> again = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(first)).isEqualTo(503);
        assertThat(status(again)).isEqualTo(503);
        assertThat(code(again)).isEqualTo("LLM_UNAVAILABLE");
        assertThat(model.calls()).isEqualTo(1);
        assertThat(rows()).containsExactly(1, 1, 0, 1, 1);
    }
}
