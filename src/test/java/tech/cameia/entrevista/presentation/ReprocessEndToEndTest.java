package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/** Reintentar la evaluación de una respuesta ya guardada, contra el servidor completo y PostgreSQL real. */
@AnswerFlowSupport.AnswerFlowTest
class ReprocessEndToEndTest extends AnswerFlowSupport {

    private void failNext() {
        model.enqueueCycle(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });
    }

    /** Deja una sesión en curso cuyo turno 1 tiene su respuesta guardada y espera reintento. */
    private String sessionWithRetryingTurn() {
        String id = startedSession(UID_A, PROFILE_A);
        failNext();
        assertThat(status(answer(UID_A, id, turnId(id, 1), ANSWER, KEY))).isEqualTo(503);
        model.reset();
        return id;
    }

    @Test
    @DisplayName("Reintentar evalúa la respuesta guardada, completa el turno y abre la pregunta 2")
    void reprocess_shouldCompleteTurn_whenEvaluationSucceeds() {
        String id = sessionWithRetryingTurn();
        String turn1 = turnId(id, 1);
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = reprocess(UID_A, id, turn1);

        assertThat(status(response)).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getBody(), "$.turn.status")).isEqualTo("COMPLETED");
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.currentQuestion")).isEqualTo(2);
        assertThat((String) JsonPath.read(response.getBody(), "$.nextQuestion.text")).isEqualTo(QUESTIONS[1]);
        assertThat(response.getBody()).doesNotContain("feedback", "clasificacion", "motivo");
        assertThat(turnStatus(turn1)).isEqualTo("COMPLETED");
        assertThat(retriesUsed(turn1)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.intento_respuesta")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isEqualTo(1);
        assertThat(evaluations(turn1, "FALLIDA")).isEqualTo(3);
        assertThat(evaluations(turn1, "EXITOSA")).isEqualTo(1);
        assertThat(model.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Si el modelo sigue fallando responde 503, el turno sigue en RETRYING y el contador sube")
    void reprocess_shouldStayRetrying_whenModelKeepsFailing() {
        String id = sessionWithRetryingTurn();
        String turn1 = turnId(id, 1);
        failNext();

        ResponseEntity<String> response = reprocess(UID_A, id, turn1);

        assertThat(status(response)).isEqualTo(503);
        assertThat(code(response)).isEqualTo("LLM_UNAVAILABLE");
        assertThat(turnStatus(turn1)).isEqualTo("RETRYING");
        assertThat(retriesUsed(turn1)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isZero();
        assertThat((String) JsonPath.read(state(UID_A, id).getBody(), "$.pendingQuestion.status"))
                .isEqualTo("RETRYING");
    }

    @Test
    @DisplayName("Tras cuatro reintentos que fallan, el quinto responde 409 TURN_RETRY_LIMIT_REACHED sin llamar a la IA")
    void reprocess_shouldReject409LimitReached_onFifthUse() {
        String id = sessionWithRetryingTurn();
        String turn1 = turnId(id, 1);
        for (int use = 1; use <= 4; use++) {
            model.enqueueCycle(() -> {
                throw new LlmInvalidOutputException("estructura");
            });
            ResponseEntity<String> failed = reprocess(UID_A, id, turn1);
            assertThat(status(failed)).as("uso %d", use).isEqualTo(503);
            assertThat(code(failed)).isEqualTo("LLM_INVALID_OUTPUT");
        }
        int callsBefore = model.calls();

        ResponseEntity<String> fifth = reprocess(UID_A, id, turn1);

        assertThat(status(fifth)).isEqualTo(409);
        assertThat(code(fifth)).isEqualTo("TURN_RETRY_LIMIT_REACHED");
        assertThat((String) JsonPath.read(fifth.getBody(), "$.detail")).isEqualTo("No pudimos evaluar tu respuesta"
                + " en este momento. Tu respuesta quedó guardada; inténtalo más tarde.");
        assertThat(model.calls()).isEqualTo(callsBefore);
        assertThat(retriesUsed(turn1)).isEqualTo(4);
        assertThat(turnStatus(turn1)).isEqualTo("RETRYING");
        assertThat(jdbc.queryForObject("SELECT respuesta_texto FROM microentrevista.intento_respuesta",
                String.class)).isEqualTo(ANSWER);
    }

    @Test
    @DisplayName("Un turno pendiente, procesando con la IA viva o ya completado responde 409 TURN_NOT_RETRYABLE")
    void reprocess_shouldReturn409NotRetryable_whenTurnPendingProcessingOrCompleted() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);

        ResponseEntity<String> pending = reprocess(UID_A, id, turn1);

        jdbc.update("UPDATE microentrevista.turno SET estado = 'PROCESSING' WHERE id = ?::uuid", turn1);
        insertEvaluationInProgress(id, turn1, 0);
        ResponseEntity<String> processing = reprocess(UID_A, id, turn1);

        jdbc.update("DELETE FROM microentrevista.ejecucion_ia WHERE tipo_operacion = 'EVALUAR'");
        jdbc.update("UPDATE microentrevista.turno SET estado = 'COMPLETED' WHERE id = ?::uuid", turn1);
        ResponseEntity<String> completed = reprocess(UID_A, id, turn1);

        for (ResponseEntity<String> response : List.of(pending, processing, completed)) {
            assertThat(status(response)).isEqualTo(409);
            assertThat(code(response)).isEqualTo("TURN_NOT_RETRYABLE");
            assertThat((String) JsonPath.read(response.getBody(), "$.detail"))
                    .isEqualTo("Tu respuesta no está esperando un reintento.");
        }
        assertThat(retriesUsed(turn1)).isZero();
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Un turno PROCESSING con la evaluación caída desde hace 36 s se recupera y se evalúa")
    void reprocess_shouldRecoverStaleProcessing() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        answerAndLeaveProcessing(id, turn1, 36);
        assertThat((String) JsonPath.read(state(UID_A, id).getBody(), "$.pendingQuestion.status"))
                .isEqualTo("RETRYING");
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = reprocess(UID_A, id, turn1);

        assertThat(status(response)).isEqualTo(200);
        assertThat(turnStatus(turn1)).isEqualTo("COMPLETED");
        assertThat(retriesUsed(turn1)).isEqualTo(1);
        assertThat(evaluations(turn1, "EN_CURSO")).isZero();
        assertThat(jdbc.queryForObject("SELECT error_codigo FROM microentrevista.ejecucion_ia"
                + " WHERE estado = 'FALLIDA' AND tipo_operacion = 'EVALUAR'", String.class))
                .isEqualTo("LLM_UNAVAILABLE");
    }

    @Test
    @DisplayName("Un turno PROCESSING con la evaluación viva se muestra PROCESSING y no se reintenta")
    void reprocess_shouldRejectLiveProcessing() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        answerAndLeaveProcessing(id, turn1, 5);

        ResponseEntity<String> response = reprocess(UID_A, id, turn1);

        assertThat(status(response)).isEqualTo(409);
        assertThat(code(response)).isEqualTo("TURN_NOT_RETRYABLE");
        assertThat((String) JsonPath.read(state(UID_A, id).getBody(), "$.pendingQuestion.status"))
                .isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("Reintentar con la sesión fuera de curso responde 409 con su código y no evalúa")
    void reprocess_shouldReturn409_whenSessionNotInProgress() {
        List<String[]> cases = List.of(new String[] {"EVALUANDO", "SESSION_ALREADY_CLOSED"},
                new String[] {"FINALIZADA", "SESSION_ALREADY_CLOSED"},
                new String[] {"ABANDONADA", "SESSION_ABANDONED"});
        for (String[] closed : cases) {
            jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia CASCADE");
            String id = sessionWithRetryingTurn();
            String turn1 = turnId(id, 1);
            forceStatus(id, closed[0]);

            ResponseEntity<String> response = reprocess(UID_A, id, turn1);

            assertThat(status(response)).as(closed[0]).isEqualTo(409);
            assertThat(code(response)).isEqualTo(closed[1]);
            assertThat(retriesUsed(turn1)).isZero();
            assertThat(turnStatus(turn1)).isEqualTo("RETRYING");
        }
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Una sesión ajena o mal escrita responde SESSION_NOT_FOUND y un turno ajeno o mal escrito, TURN_NOT_FOUND")
    void reprocess_shouldReturn404_forForeignMissingAndMalformedIds() {
        String id = sessionWithRetryingTurn();
        String turn1 = turnId(id, 1);
        String foreign = startedSession(UID_B, PROFILE_B);

        assertThat(code(reprocess(UID_B, id, turn1))).isEqualTo("SESSION_NOT_FOUND");
        assertThat(code(reprocess(UID_A, "abc", turn1))).isEqualTo("SESSION_NOT_FOUND");
        assertThat(code(reprocess(UID_A, UUID.randomUUID().toString(), turn1))).isEqualTo("SESSION_NOT_FOUND");
        for (String turn : List.of(turnId(foreign, 1), UUID.randomUUID().toString(), "no-es-uuid", "%00",
                "9".repeat(200))) {
            ResponseEntity<String> response = reprocess(UID_A, id, turn);
            assertThat(status(response)).as(turn).isEqualTo(404);
            assertThat(code(response)).isEqualTo("TURN_NOT_FOUND");
        }
        assertThat(retriesUsed(turn1)).isZero();
        assertThat(model.calls()).isZero();
    }

    @Test
    @DisplayName("Sin X-User-Id responde 401 IDENTITY_REQUIRED")
    void reprocess_shouldReturn401_whenIdentityMissing() {
        String id = sessionWithRetryingTurn();

        ResponseEntity<String> response = reprocess(null, id, turnId(id, 1));

        assertThat(status(response)).isEqualTo(401);
        assertThat(code(response)).isEqualTo("IDENTITY_REQUIRED");
    }

    // ---- ayudantes ----

    /** Guarda la respuesta y deja el turno PROCESSING con una evaluación en curso iniciada hace {@code secondsAgo}. */
    private void answerAndLeaveProcessing(String id, String turn1, int secondsAgo) {
        failNext();
        answer(UID_A, id, turn1, ANSWER, KEY);
        model.reset();
        jdbc.update("DELETE FROM microentrevista.ejecucion_ia WHERE tipo_operacion = 'EVALUAR'");
        jdbc.update("UPDATE microentrevista.turno SET estado = 'PROCESSING' WHERE id = ?::uuid", turn1);
        insertEvaluationInProgress(id, turn1, secondsAgo);
    }

    private void insertEvaluationInProgress(String sessionId, String turnId, int secondsAgo) {
        jdbc.update("""
                INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion, numero_intento,
                    idempotency_key, estado, modelo, version_prompt, proveedor, fecha_inicio)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'EVALUAR', 1, gen_random_uuid(), 'EN_CURSO',
                    'modelo-de-prueba', 'evaluacion.v1', 'gemini', ?::timestamptz - make_interval(secs => ?))
                """, sessionId, turnId, NOW.toString(), secondsAgo);
    }
}
