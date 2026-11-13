package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;

import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;

/**
 * El ciclo de hasta tres intentos de la IA contra el servidor completo y PostgreSQL real: qué proveedor atiende cada
 * intento, qué filas de ejecución deja cada uno y qué responde el servicio cuando el ciclo termina.
 */
@AnswerFlowSupport.AnswerFlowTest
class AiRetryEndToEndTest extends AnswerFlowSupport {

    private static final String FIRST = "numero_intento";
    private static final String PROVIDER = "proveedor";
    private static final String STATE = "estado";
    private static final String ERROR = "error_codigo";

    private static LanguageModelResponse unavailable() {
        throw new LlmUnavailableException(new RuntimeException("caída"));
    }

    private static LanguageModelResponse limited() {
        throw new LlmLimitReachedException(new RuntimeException("429"));
    }

    private static LanguageModelResponse notJson() {
        return response("esto no es json");
    }

    private static LanguageModelResponse skip() {
        return response("{\"accion\":\"SALTAR\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"x\","
                + "\"follow_up_texto\":\"\",\"feedback\":\"y\"}");
    }

    static Stream<Arguments> firstFailures() {
        Supplier<LanguageModelResponse> timeout = AiRetryEndToEndTest::unavailable;
        Supplier<LanguageModelResponse> serverError = AiRetryEndToEndTest::unavailable;
        Supplier<LanguageModelResponse> tooMany = AiRetryEndToEndTest::limited;
        Supplier<LanguageModelResponse> invalid = AiRetryEndToEndTest::notJson;
        return Stream.of(
                Arguments.of("tiempo agotado", timeout, "LLM_UNAVAILABLE", "gemini"),
                Arguments.of("error 500", serverError, "LLM_UNAVAILABLE", "gemini"),
                Arguments.of("429 del principal: el segundo va al respaldo", tooMany, "LLM_LIMIT_REACHED", "groq"),
                Arguments.of("JSON inválido", invalid, "LLM_INVALID_OUTPUT", "gemini"));
    }

    // ---- lectura de la base ----

    private List<Map<String, Object>> evaluationRows(String turnId) {
        return jdbc.queryForList("SELECT numero_intento, proveedor, estado, error_codigo, idempotency_key, modelo"
                + " FROM microentrevista.ejecucion_ia WHERE turno_id = ?::uuid AND tipo_operacion = 'EVALUAR'"
                + " ORDER BY numero_intento, estado", turnId);
    }

    private List<Map<String, Object>> planningRows(String sessionId) {
        return jdbc.queryForList("SELECT numero_intento, proveedor, estado, error_codigo, idempotency_key, modelo"
                + " FROM microentrevista.ejecucion_ia WHERE sesion_id = ?::uuid AND tipo_operacion = 'PLANIFICAR'"
                + " ORDER BY numero_intento", sessionId);
    }

    private static void assertDistinctKeys(List<Map<String, Object>> rows) {
        assertThat(rows.stream().map(row -> row.get("idempotency_key")).distinct().count()).isEqualTo(rows.size());
    }

    /** Deja el turno 1 en reintento con un ciclo completo fallido y limpia el guion del modelo. */
    private String sessionWithRetryingTurn() {
        String id = startedSession(UID_A, PROFILE_A);
        model.enqueueCycle(AiRetryEndToEndTest::unavailable);
        assertThat(status(answer(UID_A, id, turnId(id, 1), ANSWER, KEY))).isEqualTo(503);
        model.reset();
        return id;
    }

    // ---- evaluar ----

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstFailures")
    @DisplayName("Si el primer intento falla, el segundo evalúa: 200, dos filas y una sola respuesta guardada")
    void answer_shouldSucceedOnSecondAttempt(String name, Supplier<LanguageModelResponse> first, String firstCode,
            String secondProvider) {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.fallbackAvailable(true);
        model.enqueue(first);
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(response)).isEqualTo(200);
        assertThat(turnStatus(turn1)).isEqualTo("COMPLETED");
        List<Map<String, Object>> rows = evaluationRows(turn1);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry(FIRST, 1).containsEntry(PROVIDER, "gemini")
                .containsEntry(STATE, "FALLIDA").containsEntry(ERROR, firstCode);
        assertThat(rows.get(1)).containsEntry(FIRST, 2).containsEntry(PROVIDER, secondProvider)
                .containsEntry(STATE, "EXITOSA");
        assertDistinctKeys(rows);
        assertThat(count("SELECT count(*) FROM microentrevista.intento_respuesta")).isEqualTo(1);
        assertThat(waiter.pauses()).isEqualTo(1);
    }

    @ParameterizedTest(name = "causa del tercero: {0}")
    @MethodSource("lastFailures")
    @DisplayName("Si los tres intentos fallan responde 503 con la causa del último y deja el turno en RETRYING")
    void answer_shouldReturn503WithLastCause_whenThreeFail(String code, Supplier<LanguageModelResponse> last) {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(last);

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(response)).isEqualTo(503);
        assertThat(code(response)).isEqualTo(code);
        assertThat(turnStatus(turn1)).isEqualTo("RETRYING");
        assertThat(sessionStatus(id)).isEqualTo("EN_CURSO");
        List<Map<String, Object>> rows = evaluationRows(turn1);
        assertThat(rows).extracting(row -> row.get(FIRST)).containsExactly(1, 2, 3);
        assertThat(rows).extracting(row -> row.get(STATE)).containsOnly("FALLIDA");
        assertThat(rows.get(2)).containsEntry(ERROR, code);
        assertDistinctKeys(rows);
        assertThat(model.calls()).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.intento_respuesta")).isEqualTo(1);
    }

    static Stream<Arguments> lastFailures() {
        Supplier<LanguageModelResponse> unavailable = AiRetryEndToEndTest::unavailable;
        Supplier<LanguageModelResponse> invalid = AiRetryEndToEndTest::notJson;
        return Stream.of(Arguments.of("LLM_UNAVAILABLE", unavailable), Arguments.of("LLM_INVALID_OUTPUT", invalid));
    }

    @Test
    @DisplayName("El tercer intento usa el respaldo y evalúa: 200 con la tercera fila de proveedor groq")
    void answer_shouldSucceedOnThirdAttemptWithFallback() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.fallbackAvailable(true);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(response)).isEqualTo(200);
        assertThat(model.providers()).containsExactly(AiProvider.PRIMARY, AiProvider.PRIMARY, AiProvider.FALLBACK);
        List<Map<String, Object>> rows = evaluationRows(turn1);
        assertThat(rows).extracting(row -> row.get(PROVIDER)).containsExactly("gemini", "gemini", "groq");
        assertThat(rows.get(2)).containsEntry(STATE, "EXITOSA").containsEntry("modelo", "qwen/qwen3.8-27b");
        assertThat(turnStatus(turn1)).isEqualTo("COMPLETED");
        assertThat(waiter.pauses()).isEqualTo(2);
    }

    @Test
    @DisplayName("Tras un 429 del principal, el segundo y el tercer intento van al respaldo")
    void answer_shouldSkipPrimary_after429() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.fallbackAvailable(true);
        model.enqueue(AiRetryEndToEndTest::limited);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(response)).isEqualTo(200);
        assertThat(model.providers()).containsExactly(AiProvider.PRIMARY, AiProvider.FALLBACK, AiProvider.FALLBACK);
        assertThat(evaluationRows(turn1)).extracting(row -> row.get(PROVIDER))
                .containsExactly("gemini", "groq", "groq");
    }

    @Test
    @DisplayName("Si los dos proveedores devuelven 429 el ciclo termina en dos intentos con LLM_LIMIT_REACHED")
    void answer_shouldStop_whenBothLimited() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.fallbackAvailable(true);
        model.enqueue(AiRetryEndToEndTest::limited);
        model.enqueue(AiRetryEndToEndTest::limited);

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(response)).isEqualTo(503);
        assertThat(code(response)).isEqualTo("LLM_LIMIT_REACHED");
        assertThat(model.providers()).containsExactly(AiProvider.PRIMARY, AiProvider.FALLBACK);
        assertThat(evaluationRows(turn1)).hasSize(2);
        assertThat(turnStatus(turn1)).isEqualTo("RETRYING");
    }

    @Test
    @DisplayName("Sin llave del respaldo, un 429 del principal termina el ciclo en un intento")
    void answer_shouldStopAtFirst_whenLimitedAndNoFallback() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(AiRetryEndToEndTest::limited);

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(response)).isEqualTo(503);
        assertThat(code(response)).isEqualTo("LLM_LIMIT_REACHED");
        assertThat(evaluationRows(turn1)).hasSize(1);
        assertThat(waiter.pauses()).isZero();
    }

    @Test
    @DisplayName("Una acción fuera del esquema del respaldo es salida inválida y no se acepta")
    void answer_shouldRejectFallbackOutputOutsideSchema() {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.fallbackAvailable(true);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(AiRetryEndToEndTest::skip);

        ResponseEntity<String> response = answer(UID_A, id, turn1, ANSWER, KEY);

        assertThat(status(response)).isEqualTo(503);
        assertThat(code(response)).isEqualTo("LLM_INVALID_OUTPUT");
        assertThat(evaluationRows(turn1).get(2)).containsEntry(PROVIDER, "groq").containsEntry(STATE, "FALLIDA");
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isZero();
    }

    // ---- reintentar ----

    @Test
    @DisplayName("Reintentar corre un ciclo nuevo desde el intento 1: falla uno, acierta el segundo, sin respuesta nueva")
    void reprocess_shouldRunNewCycle() {
        String id = sessionWithRetryingTurn();
        String turn1 = turnId(id, 1);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> response = reprocess(UID_A, id, turn1);

        assertThat(status(response)).isEqualTo(200);
        assertThat(retriesUsed(turn1)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.intento_respuesta")).isEqualTo(1);
        List<Map<String, Object>> rows = evaluationRows(turn1);
        assertThat(rows).hasSize(5);
        assertThat(rows.stream().filter(row -> Integer.valueOf(1).equals(row.get(FIRST))).count()).isEqualTo(2);
        assertThat(rows.stream().filter(row -> "EXITOSA".equals(row.get(STATE))).findFirst().orElseThrow())
                .containsEntry(FIRST, 2);
        assertDistinctKeys(rows);
    }

    @Test
    @DisplayName("Si el ciclo del reintento también falla responde 503, el turno sigue en RETRYING y el contador sube")
    void reprocess_shouldReturn503AndStayRetrying_whenCycleFails() {
        String id = sessionWithRetryingTurn();
        String turn1 = turnId(id, 1);
        model.enqueueCycle(AiRetryEndToEndTest::unavailable);

        ResponseEntity<String> response = reprocess(UID_A, id, turn1);

        assertThat(status(response)).isEqualTo(503);
        assertThat(code(response)).isEqualTo("LLM_UNAVAILABLE");
        assertThat(turnStatus(turn1)).isEqualTo("RETRYING");
        assertThat(retriesUsed(turn1)).isEqualTo(1);
        assertThat(evaluationRows(turn1)).hasSize(6);
    }

    // ---- planificar ----

    @Test
    @DisplayName("Si los tres intentos de planificar fallan responde 503 y la sesión sigue CONFIGURADA sin preguntas")
    void start_shouldReturn503AndStayConfigured_whenThreeFail() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueueCycle(AiRetryEndToEndTest::unavailable);

        ResponseEntity<String> response = call(org.springframework.http.HttpMethod.POST, BASE + "/" + id + "/start",
                UID_A, null, null);

        assertThat(status(response)).isEqualTo(503);
        assertThat(code(response)).isEqualTo("LLM_UNAVAILABLE");
        assertThat(sessionStatus(id)).isEqualTo("CONFIGURADA");
        assertThat(count("SELECT count(*) FROM microentrevista.pregunta")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.turno")).isZero();
        List<Map<String, Object>> rows = planningRows(id);
        assertThat(rows).extracting(row -> row.get(FIRST)).containsExactly(1, 2, 3);
        assertThat(rows).extracting(row -> row.get(STATE)).containsOnly("FALLIDA");
        assertDistinctKeys(rows);
    }

    @Test
    @DisplayName("Si el primer intento de planificar falla, el segundo inicia la entrevista con cinco preguntas")
    void start_shouldStartOnSecondAttempt() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(() -> response(VALID_PLAN));

        ResponseEntity<String> response = call(org.springframework.http.HttpMethod.POST, BASE + "/" + id + "/start",
                UID_A, null, null);

        assertThat(status(response)).isEqualTo(200);
        assertThat(sessionStatus(id)).isEqualTo("EN_CURSO");
        assertThat(count("SELECT count(*) FROM microentrevista.pregunta WHERE sesion_id = ?::uuid", id))
                .isEqualTo(5);
        List<Map<String, Object>> rows = planningRows(id);
        assertThat(rows).extracting(row -> row.get(FIRST)).containsExactly(1, 2);
        assertThat(rows).extracting(row -> row.get(STATE)).containsExactly("FALLIDA", "EXITOSA");
        assertDistinctKeys(rows);
    }

    @Test
    @DisplayName("El plan del tercer intento con el respaldo se guarda con su proveedor y modelo")
    void start_shouldStartWithFallbackOnThirdAttempt() {
        String id = configure(UID_A, PROFILE_A);
        model.fallbackAvailable(true);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(AiRetryEndToEndTest::unavailable);
        model.enqueue(() -> response(VALID_PLAN));

        ResponseEntity<String> response = call(org.springframework.http.HttpMethod.POST, BASE + "/" + id + "/start",
                UID_A, null, null);

        assertThat(status(response)).isEqualTo(200);
        assertThat(planningRows(id).get(2)).containsEntry(PROVIDER, "groq").containsEntry("modelo",
                "qwen/qwen3.8-27b");
    }
}
