package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Peticiones de responder que se solapan: la clave repetida, dos claves sobre el mismo turno y otra entrevista. */
@AnswerFlowSupport.AnswerFlowTest
class AnswerConcurrencyTest extends AnswerFlowSupport {

    @Test
    @DisplayName("La misma clave mientras la primera evalúa espera y devuelve el mismo resultado, con una sola llamada a la IA")
    void answer_shouldReturnSameResult_whenSameKeyArrivesWhileProcessing() throws Exception {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        Gate gate = new Gate(EVALUATION_OK);
        model.enqueue(gate::call);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> first = pool.submit(() -> answer(UID_A, id, turn1, ANSWER, KEY));
            gate.awaitEntered();
            Future<ResponseEntity<String>> second = pool.submit(() -> answer(UID_A, id, turn1, ANSWER, KEY));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (waiter.pauses() == 0 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(waiter.pauses()).as("la segunda petición entró a esperar").isPositive();
            assertThat(second.isDone()).isFalse();
            gate.open();

            ResponseEntity<String> one = first.get(30, TimeUnit.SECONDS);
            ResponseEntity<String> two = second.get(30, TimeUnit.SECONDS);

            assertThat(status(one)).isEqualTo(200);
            assertThat(status(two)).isEqualTo(200);
            assertThat(two.getBody()).isEqualTo(one.getBody());
            assertThat(model.calls()).isEqualTo(1);
            assertThat(rows()).containsExactly(1, 1, 1, 2, 1);
        } finally {
            gate.open();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Dos claves distintas sobre el mismo turno a la vez: una responde 200 y la otra 409 ANSWER_ALREADY_EXISTS")
    void answer_shouldKeepOneAnswer_whenTwoKeysRace() throws Exception {
        String id = startedSession(UID_A, PROFILE_A);
        String turn1 = turnId(id, 1);
        Gate gate = new Gate(EVALUATION_OK);
        model.enqueue(gate::call);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            CompletableFuture<ResponseEntity<String>> a = CompletableFuture.supplyAsync(
                    () -> raced(start, () -> answer(UID_A, id, turn1, ANSWER, "clave-a")), pool);
            CompletableFuture<ResponseEntity<String>> b = CompletableFuture.supplyAsync(
                    () -> raced(start, () -> answer(UID_A, id, turn1, ANSWER, "clave-b")), pool);
            // La que pierde responde sin esperar a la IA; la ganadora sigue detenida en el modelo.
            ResponseEntity<String> loser = (ResponseEntity<String>) CompletableFuture.anyOf(a, b)
                    .get(30, TimeUnit.SECONDS);
            assertThat(status(loser)).isEqualTo(409);
            assertThat(code(loser)).isEqualTo("ANSWER_ALREADY_EXISTS");
            gate.open();

            ResponseEntity<String> winner = (loser == a.getNow(null) ? b : a).get(30, TimeUnit.SECONDS);

            assertThat(status(winner)).isEqualTo(200);
            assertThat(model.calls()).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM microentrevista.intento_respuesta")).isEqualTo(1);
            assertThat(evaluations(turn1, "EXITOSA")).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isEqualTo(1);
        } finally {
            gate.open();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Si la persona inicia otra entrevista mientras la IA evalúa, la respuesta da 409 SESSION_ABANDONED y el turno queda RETRYING")
    void answer_shouldReturn409Abandoned_whenOtherInterviewStartsDuringEvaluation() throws Exception {
        String id = startedSession(UID_A, PROFILE_A);
        for (int number = 1; number <= 2; number++) {
            model.enqueue(AnswerFlowSupport::evaluation);
            assertThat(status(answer(UID_A, id, turnId(id, number), ANSWER, "k" + number))).isEqualTo(200);
        }
        String turn3 = turnId(id, 3);
        Gate gate = new Gate(EVALUATION_OK);
        model.enqueue(gate::call);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<String>> third = pool.submit(() -> answer(UID_A, id, turn3, ANSWER, "k3"));
            gate.awaitEntered();
            String other = configure(UID_A, PROFILE_A);
            model.enqueue(() -> response(VALID_PLAN));
            assertThat(status(call(HttpMethod.POST, BASE + "/" + other + "/start", UID_A, null, null)))
                    .isEqualTo(200);
            assertThat(sessionStatus(id)).isEqualTo("ABANDONADA");
            gate.open();

            ResponseEntity<String> response = third.get(30, TimeUnit.SECONDS);

            assertThat(status(response)).isEqualTo(409);
            assertThat(code(response)).isEqualTo("SESSION_ABANDONED");
            assertThat(turnStatus(turn3)).isEqualTo("RETRYING");
            assertThat(count("SELECT count(*) FROM microentrevista.turno t JOIN microentrevista.pregunta p"
                    + " ON p.id = t.pregunta_id WHERE p.sesion_id = ?::uuid", id)).isEqualTo(3);
            assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno f JOIN microentrevista.turno t"
                    + " ON t.id = f.turno_id JOIN microentrevista.pregunta p ON p.id = t.pregunta_id"
                    + " WHERE p.sesion_id = ?::uuid", id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT error_codigo FROM microentrevista.ejecucion_ia"
                    + " WHERE turno_id = ?::uuid AND estado = 'FALLIDA'", String.class, turn3))
                    .isEqualTo("SESSION_ABANDONED");
            assertThat(evaluations(turn3, "EN_CURSO")).isZero();
            int callsBefore = model.calls();

            ResponseEntity<String> again = answer(UID_A, id, turn3, ANSWER, "k3");

            assertThat(status(again)).isEqualTo(409);
            assertThat((String) JsonPath.read(again.getBody(), "$.code")).isEqualTo("SESSION_ABANDONED");
            assertThat(model.calls()).isEqualTo(callsBefore);
            assertThat(List.of(turnStatus(turn3))).containsExactly("RETRYING");
        } finally {
            gate.open();
            pool.shutdownNow();
        }
    }

    private static ResponseEntity<String> raced(CyclicBarrier start, java.util.function.Supplier<ResponseEntity<String>> call) {
        try {
            start.await(30, TimeUnit.SECONDS);
        } catch (Exception cannotAlign) {
            throw new IllegalStateException(cannotAlign);
        }
        return call.get();
    }
}
