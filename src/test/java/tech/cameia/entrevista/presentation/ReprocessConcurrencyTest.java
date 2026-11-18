package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/** Dos reintentos del mismo turno a la vez: solo uno evalúa y el contador sube una sola vez. */
@AnswerFlowSupport.AnswerFlowTest
class ReprocessConcurrencyTest extends AnswerFlowSupport {

    @Test
    @DisplayName("Dos reintentos juntos: uno evalúa, el otro responde 409 TURN_NOT_RETRYABLE y el contador sube 1")
    void reprocess_shouldEvaluateOnce_whenTwoArriveTogether() throws Exception {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        model.enqueue(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });
        assertThat(status(answer(UID_A, id, turn1, ANSWER, KEY))).isEqualTo(503);
        model.reset();
        Gate gate = new Gate(EVALUATION_OK);
        model.enqueue(gate::call);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            CompletableFuture<ResponseEntity<String>> a = CompletableFuture.supplyAsync(
                    () -> raced(start, id, turn1), pool);
            CompletableFuture<ResponseEntity<String>> b = CompletableFuture.supplyAsync(
                    () -> raced(start, id, turn1), pool);
            // La que pierde responde sin esperar a la IA; la ganadora sigue detenida en el modelo.
            ResponseEntity<String> loser = (ResponseEntity<String>) CompletableFuture.anyOf(a, b)
                    .get(30, TimeUnit.SECONDS);
            assertThat(status(loser)).isEqualTo(409);
            assertThat(code(loser)).isEqualTo("TURN_NOT_RETRYABLE");
            gate.open();

            ResponseEntity<String> winner = (loser == a.getNow(null) ? b : a).get(30, TimeUnit.SECONDS);

            assertThat(status(winner)).isEqualTo(200);
            assertThat(model.calls()).isEqualTo(1);
            assertThat(retriesUsed(turn1)).isEqualTo(1);
            assertThat(evaluations(turn1, "EXITOSA")).isEqualTo(1);
            assertThat(evaluations(turn1, "EN_CURSO")).isZero();
            assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isEqualTo(1);
        } finally {
            gate.open();
            pool.shutdownNow();
        }
    }

    private ResponseEntity<String> raced(CyclicBarrier start, String sessionId, String turnId) {
        try {
            start.await(30, TimeUnit.SECONDS);
        } catch (Exception cannotAlign) {
            throw new IllegalStateException(cannotAlign);
        }
        return reprocess(UID_A, sessionId, turnId);
    }
}
