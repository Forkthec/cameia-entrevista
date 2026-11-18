package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** La quinta evaluación en vuelo frente a otra sesión que se inicia: nunca queda una sesión a medias. */
@AnswerFlowSupport.AnswerFlowTest
class AbandonVsCloseRaceTest extends AnswerFlowSupport {

    @Test
    @DisplayName("Iniciar otra sesión mientras se evalúa la quinta respuesta no deja la primera EVALUANDO")
    void answer_shouldNeverLeaveSessionEvaluating_whenAnotherSessionStarts() throws Exception {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 4);
        String turn5 = turnId(id, 5);
        Gate gate = new Gate(EVALUATION_OK);
        model.enqueue(gate::call);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<ResponseEntity<String>> fifth = CompletableFuture.supplyAsync(
                    () -> answer(UID_A, id, turn5, ANSWER, "clave-5"), pool);
            gate.awaitEntered();
            String other = configure(UID_A, PROFILE_A);
            model.enqueue(() -> response(VALID_PLAN));
            ResponseEntity<String> started = call(HttpMethod.POST, BASE + "/" + other + "/start", UID_A, null, null);
            assertThat(status(started)).isEqualTo(200);
            gate.open();

            ResponseEntity<String> result = fifth.get(60, TimeUnit.SECONDS);

            String first = sessionStatus(id);
            assertThat(first).isIn("ABANDONADA", "FINALIZADA");
            assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE fecha_fin IS NULL AND estado = ?",
                    "EVALUANDO")).isZero();
            if (first.equals("ABANDONADA")) {
                assertThat(status(result)).isEqualTo(409);
                assertThat(code(result)).isEqualTo("SESSION_ABANDONED");
                assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno WHERE turno_id = ?::uuid",
                        turn5)).isZero();
                assertThat(sessionStatus(other)).isEqualTo("EN_CURSO");
            } else {
                assertThat(status(result)).isEqualTo(200);
                assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE id = ?::uuid"
                        + " AND fecha_fin IS NOT NULL", id)).isEqualTo(1);
            }
            assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE estado = 'EN_CURSO'")).isEqualTo(1);
        } finally {
            gate.open();
            pool.shutdownNow();
        }
    }
}
