package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

/** Dos consultas del reporte a la vez: una sola llamada a la IA, el 202 de la segunda y la marca vencida. */
@AnswerFlowSupport.AnswerFlowTest
@Import(ReportFlowSupport.Transcribers.class)
class EvaluationReportConcurrencyTest extends ReportFlowSupport {

    @Test
    @DisplayName("Dos consultas a la vez llaman a la IA una vez: la primera responde 200 y la segunda 202")
    void twoReadsAtOnce_shouldCallTheAiOnceAndAnswer200And202() throws Exception {
        String id = finishedTextSession(UID_A, PROFILE_A);
        Gate gate = new Gate(REPORT_OK);
        model.enqueue(gate::call);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<ResponseEntity<String>> first = CompletableFuture
                    .supplyAsync(() -> report(UID_A, id), pool);
            gate.awaitEntered();

            ResponseEntity<String> second = report(UID_A, id);

            assertThat(status(second)).isEqualTo(202);
            assertThat((String) JsonPath.read(second.getBody(), "$.code")).isEqualTo("REPORT_GENERATING");
            assertThat((String) JsonPath.read(second.getBody(), "$.message")).isEqualTo("Tu reporte se está generando.");
            assertThat((String) JsonPath.read(second.getBody(), "$.requestId")).isNotBlank();
            assertThat(second.getHeaders().getFirst("X-Request-Id"))
                    .isEqualTo(JsonPath.read(second.getBody(), "$.requestId"));
            gate.open();
            ResponseEntity<String> done = first.get(60, TimeUnit.SECONDS);

            assertThat(status(done)).isEqualTo(200);
            assertThat(model.calls()).isEqualTo(1);
            assertThat(reportExecutions(id, "EXITOSA")).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM microentrevista.reporte_evaluacion")).isEqualTo(1);
            assertThat(status(report(UID_A, id))).isEqualTo(200);
        } finally {
            gate.open();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Una marca de generación de 120 s se toma: la anterior queda FALLIDA y el reporte sale")
    void staleClaim_shouldBeTakenOver() {
        String id = finishedTextSession(UID_A, PROFILE_A);
        insertClaim(id, 120);
        model.enqueue(() -> response(REPORT_OK));

        ResponseEntity<String> response = report(UID_A, id);

        assertThat(status(response)).isEqualTo(200);
        assertThat(model.calls()).isEqualTo(1);
        assertThat(reportExecutions(id, "FALLIDA")).isEqualTo(1);
        assertThat(reportExecutions(id, "EXITOSA")).isEqualTo(1);
        assertThat(reportExecutions(id, "EN_CURSO")).isZero();
        assertThat(jdbc.queryForObject("SELECT error_codigo FROM microentrevista.ejecucion_ia WHERE sesion_id ="
                + " ?::uuid AND tipo_operacion = 'GENERAR_REPORTE' AND estado = 'FALLIDA'", String.class, id))
                .isEqualTo("LLM_UNAVAILABLE");
    }

    @Test
    @DisplayName("Una marca de generación de 119 s sigue viva: responde 202 y no llama a la IA")
    void liveClaim_shouldAnswer202() {
        String id = finishedTextSession(UID_A, PROFILE_A);
        insertClaim(id, 119);

        ResponseEntity<String> response = report(UID_A, id);

        assertThat(status(response)).isEqualTo(202);
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("application/json;charset=UTF-8");
        assertThat(code(response)).isEqualTo("REPORT_GENERATING");
        assertThat(model.calls()).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.reporte_evaluacion")).isZero();
        assertThat(reportExecutions(id, "EN_CURSO")).isEqualTo(1);
    }

    private void insertClaim(String sessionId, int secondsAgo) {
        jdbc.update("""
                INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion, numero_intento,
                    idempotency_key, estado, modelo, version_prompt, proveedor, fecha_inicio)
                VALUES (?::uuid, ?::uuid, NULL, 'GENERAR_REPORTE', 1, gen_random_uuid(), 'EN_CURSO',
                    'modelo-de-prueba', 'reporte.v1', 'gemini', ?::timestamptz - make_interval(secs => ?))
                """, UUID.randomUUID().toString(), sessionId, NOW.toString(), secondsAgo);
    }
}
