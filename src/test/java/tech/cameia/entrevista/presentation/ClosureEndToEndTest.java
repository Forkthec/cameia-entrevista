package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Cierre de la sesión contra el servidor completo y PostgreSQL real: de la respuesta cinco a FINALIZADA. */
@AnswerFlowSupport.AnswerFlowTest
class ClosureEndToEndTest extends AnswerFlowSupport {

    @AfterEach
    void removeCloseBlocker() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_bloquear_cierre ON microentrevista.sesion");
        jdbc.execute("DROP FUNCTION IF EXISTS microentrevista.fn_bloquear_cierre()");
    }

    /** Hace fallar a la base cada vez que una sesión intenta pasar a FINALIZADA. */
    private void blockClosing() {
        jdbc.execute("""
                CREATE FUNCTION microentrevista.fn_bloquear_cierre() RETURNS trigger AS $$
                BEGIN
                    IF NEW.estado = 'FINALIZADA' THEN
                        RAISE EXCEPTION 'cierre bloqueado por la prueba';
                    END IF;
                    RETURN NEW;
                END $$ LANGUAGE plpgsql""");
        jdbc.execute("CREATE TRIGGER trg_bloquear_cierre BEFORE UPDATE ON microentrevista.sesion FOR EACH ROW"
                + " EXECUTE FUNCTION microentrevista.fn_bloquear_cierre()");
    }

    @Test
    @DisplayName("La respuesta cinco deja la sesión FINALIZADA con fecha de fin y las cinco evaluaciones guardadas")
    void answer_shouldFinishSession_whenQuestionFive() {
        String id = startedSession(UID_A, PROFILE_A);

        ResponseEntity<String> last = answerFirst(UID_A, id, 5);

        assertThat((Boolean) JsonPath.read(last.getBody(), "$.interviewFinished")).isTrue();
        assertThat(sessionStatus(id)).isEqualTo("FINALIZADA");
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE id = ?::uuid AND fecha_fin IS NOT NULL",
                id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM microentrevista.turno WHERE estado = 'COMPLETED'")).isEqualTo(5);
        assertThat(model.calls()).isEqualTo(5);
    }

    @Test
    @DisplayName("Con menos de cinco respuestas la sesión sigue EN_CURSO y sin fecha de fin")
    void answer_shouldKeepSessionOpen_whenFewerThanFive() {
        String id = startedSession(UID_A, PROFILE_A);

        answerFirst(UID_A, id, 4);

        assertThat(sessionStatus(id)).isEqualTo("EN_CURSO");
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE fecha_fin IS NOT NULL")).isZero();
    }

    @Test
    @DisplayName("Si el cierre falla tres veces la sesión queda EVALUANDO y la respuesta cinco se acepta")
    void answer_shouldKeepEvaluating_whenClosingFailsThreeTimes() {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 4);
        blockClosing();
        waiter.reset();
        model.enqueue(AnswerFlowSupport::evaluation);

        ResponseEntity<String> last = answer(UID_A, id, turnId(id, 5), ANSWER, "clave-5");

        assertThat(status(last)).isEqualTo(200);
        assertThat(sessionStatus(id)).isEqualTo("EVALUANDO");
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE fecha_fin IS NOT NULL")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.feedback_turno")).isEqualTo(5);
        assertThat(waiter.pauses()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un cierre pendiente responde 503 con su código y al reintentar sin la falla queda FINALIZADA")
    void consolidation_shouldRecover_whenFailureDisappears() {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 4);
        blockClosing();
        model.enqueue(AnswerFlowSupport::evaluation);
        answer(UID_A, id, turnId(id, 5), ANSWER, "clave-5");

        ResponseEntity<String> failed = consolidate(UID_A, id);

        assertThat(status(failed)).isEqualTo(503);
        assertThat(code(failed)).isEqualTo("SESSION_CONSOLIDATION_FAILED");
        assertThat((String) JsonPath.read(failed.getBody(), "$.detail"))
                .isEqualTo("Ocurrió un error. Inténtalo de nuevo.");
        assertThat((String) JsonPath.read(failed.getBody(), "$.requestId")).isNotBlank();
        assertThat(sessionStatus(id)).isEqualTo("EVALUANDO");

        removeCloseBlocker();
        ResponseEntity<String> recovered = consolidate(UID_A, id);

        assertThat(status(recovered)).isEqualTo(200);
        assertThat((String) JsonPath.read(recovered.getBody(), "$.status")).isEqualTo("FINALIZADA");
        assertThat(sessionStatus(id)).isEqualTo("FINALIZADA");
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE id = ?::uuid AND fecha_fin IS NOT NULL",
                id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Consolidar una sesión ya finalizada se puede repetir y no cambia nada")
    void consolidation_shouldBeRepeatable_whenAlreadyFinished() {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 5);
        String endedAt = jdbc.queryForObject("SELECT fecha_fin::text FROM microentrevista.sesion", String.class);
        Long version = jdbc.queryForObject("SELECT version FROM microentrevista.sesion", Long.class);

        for (int round = 0; round < 3; round++) {
            ResponseEntity<String> response = consolidate(UID_A, id);
            assertThat(status(response)).isEqualTo(200);
            assertThat((String) JsonPath.read(response.getBody(), "$.status")).isEqualTo("FINALIZADA");
        }

        assertThat(jdbc.queryForObject("SELECT fecha_fin::text FROM microentrevista.sesion", String.class))
                .isEqualTo(endedAt);
        assertThat(jdbc.queryForObject("SELECT version FROM microentrevista.sesion", Long.class)).isEqualTo(version);
        assertThat(model.calls()).isEqualTo(5);
    }

    @Test
    @DisplayName("Repetir la respuesta cinco con la misma clave devuelve lo mismo sin evaluar de nuevo")
    void answer_shouldReplay_whenQuestionFiveRepeated() {
        String id = startedSession(UID_A, PROFILE_A);
        ResponseEntity<String> first = answerFirst(UID_A, id, 5);
        int calls = model.calls();
        int attempts = count("SELECT count(*) FROM microentrevista.intento_respuesta");

        ResponseEntity<String> replay = answer(UID_A, id, turnId(id, 5), ANSWER, "clave-5");

        assertThat(status(replay)).isEqualTo(200);
        assertThat(replay.getBody()).isEqualTo(first.getBody());
        assertThat(model.calls()).isEqualTo(calls);
        assertThat(count("SELECT count(*) FROM microentrevista.intento_respuesta")).isEqualTo(attempts);
    }

    @Test
    @DisplayName("Responder en una sesión finalizada, evaluándose o abandonada da 409 y no toca nada")
    void answer_shouldReturn409_whenSessionClosed() {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 5);
        String turn5 = turnId(id, 5);
        String[][] cases = {{"FINALIZADA", "SESSION_ALREADY_CLOSED"}, {"EVALUANDO", "SESSION_ALREADY_CLOSED"},
                {"ABANDONADA", "SESSION_ABANDONED"}};

        for (String[] row : cases) {
            forceStatus(id, row[0]);
            int calls = model.calls();
            int attempts = count("SELECT count(*) FROM microentrevista.intento_respuesta");

            ResponseEntity<String> response = answer(UID_A, id, turn5, ANSWER, "otra-" + row[0]);

            assertThat(status(response)).as(row[0]).isEqualTo(409);
            assertThat(code(response)).as(row[0]).isEqualTo(row[1]);
            assertThat(model.calls()).as(row[0]).isEqualTo(calls);
            assertThat(count("SELECT count(*) FROM microentrevista.intento_respuesta")).as(row[0])
                    .isEqualTo(attempts);
            assertThat(count("SELECT count(*) FROM microentrevista.turno")).as(row[0]).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("Una sesión ajena, inexistente o con identificador mal escrito responde el mismo 404")
    void consolidation_shouldReturn404_whenNotVisible() {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 5);

        for (ResponseEntity<String> response : java.util.List.of(consolidate(UID_B, id),
                consolidate(UID_A, "7d9f3a10-2b4c-4d6e-8f01-a2b3c4d5e6f7"), consolidate(UID_A, "no-es-un-uuid"))) {
            assertThat(status(response)).isEqualTo(404);
            assertThat(code(response)).isEqualTo("SESSION_NOT_FOUND");
        }
        assertThat(sessionStatus(id)).isEqualTo("FINALIZADA");
    }

    @Test
    @DisplayName("Sin identidad la consolidación responde 401")
    void consolidation_shouldReturn401_whenNoIdentity() {
        String id = startedSession(UID_A, PROFILE_A);

        ResponseEntity<String> response = consolidate(null, id);

        assertThat(status(response)).isEqualTo(401);
        assertThat(code(response)).isEqualTo("IDENTITY_REQUIRED");
    }

    @Test
    @DisplayName("Consolidar una sesión configurada, en curso o abandonada responde 409 y no la cambia")
    void consolidation_shouldReturn409_whenNotEvaluating() {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 2);
        String configured = configure(UID_B, PROFILE_B);

        assertThat(code(consolidate(UID_A, id))).isEqualTo("SESSION_TRANSITION_NOT_ALLOWED");
        assertThat(code(consolidate(UID_B, configured))).isEqualTo("SESSION_TRANSITION_NOT_ALLOWED");
        forceStatus(id, "ABANDONADA");
        ResponseEntity<String> abandoned = consolidate(UID_A, id);

        assertThat(status(abandoned)).isEqualTo(409);
        assertThat(code(abandoned)).isEqualTo("SESSION_TRANSITION_NOT_ALLOWED");
        assertThat(sessionStatus(id)).isEqualTo("ABANDONADA");
        assertThat(sessionStatus(configured)).isEqualTo("CONFIGURADA");
    }

    @Test
    @DisplayName("Con un turno sin completar o sin retroalimentación el cierre responde 503 sin reintentar")
    void consolidation_shouldReturn503WithoutRetry_whenDataIncomplete() {
        String id = startedSession(UID_A, PROFILE_A);
        answerFirst(UID_A, id, 5);
        forceStatus(id, "EVALUANDO");
        String turn5 = turnId(id, 5);

        for (String broken : new String[] {"PENDING", "PROCESSING", "RETRYING"}) {
            jdbc.update("UPDATE microentrevista.turno SET estado = ? WHERE id = ?::uuid", broken, turn5);
            waiter.reset();

            ResponseEntity<String> response = consolidate(UID_A, id);

            assertThat(status(response)).as(broken).isEqualTo(503);
            assertThat(code(response)).isEqualTo("SESSION_CONSOLIDATION_FAILED");
            assertThat(waiter.pauses()).isZero();
            assertThat(sessionStatus(id)).isEqualTo("EVALUANDO");
        }
        jdbc.update("UPDATE microentrevista.turno SET estado = 'COMPLETED' WHERE id = ?::uuid", turn5);
        jdbc.update("DELETE FROM microentrevista.feedback_turno WHERE turno_id = ?::uuid", turn5);

        assertThat(status(consolidate(UID_A, id))).isEqualTo(503);
        assertThat(sessionStatus(id)).isEqualTo("EVALUANDO");
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE fecha_fin IS NOT NULL")).isZero();
    }
}
