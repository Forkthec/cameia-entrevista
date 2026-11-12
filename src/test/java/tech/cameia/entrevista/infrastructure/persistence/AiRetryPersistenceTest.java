package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;

/**
 * El ciclo de hasta tres intentos contra PostgreSQL real: cada intento es una fila, siempre hay a lo sumo una en curso
 * y el cambio de intento es atómico.
 */
class AiRetryPersistenceTest extends AbstractPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InterviewSessionRepository sessions;
    @Autowired
    private AiExecutionRepository executions;
    @Autowired
    private TransactionTemplate transaction;

    private AiExecution open(UUID sessionId, int attempt, String provider, String model) {
        return executions.save(AiExecution.planningStarted(sessionId, attempt, model, "planificacion.v6+CALIDO.v4",
                provider, CLOCK));
    }

    /** Cierra la marca fallida y abre la siguiente en una sola transacción, como hace el servicio. */
    private AiExecution switchAttempt(AiExecution current, int attempt, String provider, String model) {
        return transaction.execute(status -> {
            executions.failIfInProgress(current.failed(ErrorCode.LLM_UNAVAILABLE, CLOCK));
            return open(current.sessionId(), attempt, provider, model);
        });
    }

    private int inProgress(UUID sessionId) {
        return jdbc.queryForObject("SELECT count(*) FROM microentrevista.ejecucion_ia "
                + "WHERE sesion_id = ? AND estado = 'EN_CURSO'", Integer.class, sessionId);
    }

    @Test
    @DisplayName("Un ciclo de tres intentos deja una sola fila en curso en cada paso y tres filas al final")
    void cycle_shouldKeepOneInProgressRow() {
        UUID sessionId = sessions.save(newSession("uid-a")).id();

        AiExecution first = open(sessionId, 1, "gemini", "gemini-3.5-flash-lite");
        assertThat(inProgress(sessionId)).isEqualTo(1);
        AiExecution second = switchAttempt(first, 2, "gemini", "gemini-3.5-flash-lite");
        assertThat(inProgress(sessionId)).isEqualTo(1);
        switchAttempt(second, 3, "groq", "qwen/qwen3.8-27b");
        assertThat(inProgress(sessionId)).isEqualTo(1);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT numero_intento, proveedor, estado, modelo, "
                + "error_codigo FROM microentrevista.ejecucion_ia WHERE sesion_id = ? ORDER BY numero_intento",
                sessionId);
        assertThat(rows).extracting(row -> row.get("numero_intento")).containsExactly(1, 2, 3);
        assertThat(rows).extracting(row -> row.get("proveedor")).containsExactly("gemini", "gemini", "groq");
        assertThat(rows).extracting(row -> row.get("estado")).containsExactly("FALLIDA", "FALLIDA", "EN_CURSO");
        assertThat(rows.get(2).get("modelo")).isEqualTo("qwen/qwen3.8-27b");
        assertThat(rows.get(0).get("error_codigo")).isEqualTo("LLM_UNAVAILABLE");
        assertThat(rows.get(2).get("error_codigo")).isNull();
    }

    @Test
    @DisplayName("Si el cambio de intento falla a medias no queda hueco: la marca anterior sigue en curso")
    void cycle_shouldNeverLeaveGap() {
        UUID sessionId = sessions.save(newSession("uid-a")).id();
        AiExecution first = open(sessionId, 1, "gemini", "gemini-3.5-flash-lite");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            executions.failIfInProgress(first.failed(ErrorCode.LLM_UNAVAILABLE, CLOCK));
            throw new IllegalStateException("falla entre cerrar y abrir");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(inProgress(sessionId)).isEqualTo(1);
        assertThat(executions.findPlanningInProgress(sessionId)).map(AiExecution::id).contains(first.id());
    }

    @Test
    @DisplayName("Abrir el intento siguiente antes de cerrar el anterior choca con el índice único")
    void cycle_shouldRequireClosingBeforeOpening() {
        UUID sessionId = sessions.save(newSession("uid-a")).id();
        open(sessionId, 1, "gemini", "gemini-3.5-flash-lite");

        assertThatThrownBy(() -> open(sessionId, 2, "gemini", "gemini-3.5-flash-lite"))
                .isInstanceOf(SessionConflictException.class);
        assertThat(inProgress(sessionId)).isEqualTo(1);
    }
}
