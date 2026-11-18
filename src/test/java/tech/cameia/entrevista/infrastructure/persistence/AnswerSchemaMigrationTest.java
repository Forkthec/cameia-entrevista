package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.port.InterviewSessionRepository;

/**
 * Prueba de integración de la migración que habilita responder una pregunta y reintentar su evaluación:
 * la operación de la clave anti-duplicados, el contador de reintentos del turno y el índice que impide dos
 * evaluaciones simultáneas del mismo turno.
 */
class AnswerSchemaMigrationTest extends AbstractPersistenceTest {

    private static final String KEYS = "microentrevista.clave_idempotencia";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InterviewSessionRepository sessions;

    private void insertKey(String operation) {
        jdbc.update("INSERT INTO " + KEYS + " (id, firebase_uid, operacion, clave, huella_peticion, "
                + "estado_respuesta, cuerpo_respuesta, fecha_creacion) VALUES (?, 'uid-a', ?, 'clave-1', ?, 202, "
                + "'{}'::jsonb, ?)", UUID.randomUUID(), operation, "a".repeat(64),
                Timestamp.from(Instant.parse("2026-11-12T15:00:00Z")));
    }

    private UUID newTurn() {
        return insertTurn(sessions.save(newSession("uid-a")).id());
    }

    private void insertEvaluation(UUID turnId, String status) {
        jdbc.update("""
                INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion, numero_intento,
                    idempotency_key, estado, fecha_inicio, fecha_fin, modelo, version_prompt)
                SELECT ?, p.sesion_id, t.id, 'EVALUAR', 1, ?, ?, now(), CASE WHEN ? = 'EN_CURSO' THEN NULL ELSE now() END,
                       'modelo-de-prueba', 'evaluacion.v1'
                  FROM microentrevista.turno t JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
                 WHERE t.id = ?
                """, UUID.randomUUID(), UUID.randomUUID(), status, status, turnId);
    }

    @Test
    @DisplayName("La operación RESPONDER se acepta con el estado 202 mientras se evalúa")
    void operation_shouldAcceptAnswer() {
        assertThatCode(() -> insertKey("RESPONDER")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una operación desconocida viola ck_clave_idempotencia_operacion")
    void operation_shouldRejectUnknownValue() {
        assertThatThrownBy(() -> insertKey("OTRA")).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_clave_idempotencia_operacion");
    }

    @Test
    @DisplayName("Un turno nuevo empieza con cero reintentos manuales")
    void manualRetries_shouldDefaultToZero() {
        UUID turn = newTurn();

        assertThat(jdbc.queryForObject("SELECT reintentos_manuales FROM microentrevista.turno WHERE id = ?",
                Integer.class, turn)).isZero();
    }

    @Test
    @DisplayName("El contador de reintentos acepta 4 y rechaza 5 con ck_turno_reintentos_manuales")
    void manualRetries_shouldRejectFive() {
        UUID turn = newTurn();

        assertThatCode(() -> jdbc.update("UPDATE microentrevista.turno SET reintentos_manuales = 4 WHERE id = ?", turn))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.update("UPDATE microentrevista.turno SET reintentos_manuales = 5 WHERE id = ?",
                turn)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_reintentos_manuales");
        assertThatThrownBy(() -> jdbc.update("UPDATE microentrevista.turno SET reintentos_manuales = -1 WHERE id = ?",
                turn)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_reintentos_manuales");
    }

    @Test
    @DisplayName("Solo una evaluación en curso por turno; con la primera fallida entra la segunda")
    void evaluationInProgress_shouldBeUniquePerTurn() {
        UUID turn = newTurn();
        insertEvaluation(turn, "EN_CURSO");

        assertThatThrownBy(() -> insertEvaluation(turn, "EN_CURSO"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ejecucion_ia_evaluacion_en_curso");

        jdbc.update("UPDATE microentrevista.ejecucion_ia SET estado = 'FALLIDA', error_codigo = 'LLM_UNAVAILABLE', "
                + "fecha_fin = now() WHERE turno_id = ?", turn);
        assertThatCode(() -> insertEvaluation(turn, "EN_CURSO")).doesNotThrowAnyException();
    }
}
