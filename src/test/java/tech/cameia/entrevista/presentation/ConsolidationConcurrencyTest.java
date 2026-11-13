package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Dos cierres de la misma sesión a la vez: ambos terminan bien y la sesión se finaliza una sola vez. */
@AnswerFlowSupport.AnswerFlowTest
class ConsolidationConcurrencyTest extends AnswerFlowSupport {

    @AfterEach
    void removeSlowdown() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_cierre_lento ON microentrevista.sesion");
        jdbc.execute("DROP FUNCTION IF EXISTS microentrevista.fn_cierre_lento()");
    }

    /** Retrasa el cierre dentro de la base para que la segunda petición lea la sesión antes de que la primera confirme. */
    private void slowDownClosing() {
        jdbc.execute("""
                CREATE FUNCTION microentrevista.fn_cierre_lento() RETURNS trigger AS $$
                BEGIN
                    IF NEW.estado = 'FINALIZADA' AND OLD.estado = 'EVALUANDO' THEN
                        PERFORM pg_sleep(1);
                    END IF;
                    RETURN NEW;
                END $$ LANGUAGE plpgsql""");
        jdbc.execute("CREATE TRIGGER trg_cierre_lento BEFORE UPDATE ON microentrevista.sesion FOR EACH ROW"
                + " EXECUTE FUNCTION microentrevista.fn_cierre_lento()");
    }

    @Test
    @DisplayName("Dos consolidaciones simultáneas responden 200 y la sesión se finaliza una sola vez")
    void consolidation_shouldFinishOnce_whenTwoArriveTogether() throws Exception {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 5);
        forceStatus(id, "EVALUANDO");
        Long before = jdbc.queryForObject("SELECT version FROM microentrevista.sesion", Long.class);
        slowDownClosing();
        waiter.reset();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            CompletableFuture<ResponseEntity<String>> a = CompletableFuture.supplyAsync(() -> raced(start, id), pool);
            CompletableFuture<ResponseEntity<String>> b = CompletableFuture.supplyAsync(() -> raced(start, id), pool);

            assertThat(status(a.get(60, TimeUnit.SECONDS))).isEqualTo(200);
            assertThat(status(b.get(60, TimeUnit.SECONDS))).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        assertThat(sessionStatus(id)).isEqualTo("FINALIZADA");
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE fecha_fin IS NOT NULL")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM microentrevista.sesion", Long.class))
                .isEqualTo(before + 1);
        // Una de las dos perdió la carrera, esperó y encontró la sesión ya finalizada: el solape fue real.
        assertThat(waiter.pauses()).isGreaterThanOrEqualTo(1);
    }

    private ResponseEntity<String> raced(CyclicBarrier start, String id) {
        try {
            start.await(30, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        return consolidate(UID_A, id);
    }
}
