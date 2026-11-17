package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.port.InterviewSessionRepository;

/**
 * Esquema de la migración del Reporte de Evaluación sobre PostgreSQL real: los cuatro enteros de voz de la
 * respuesta, la operación de IA que genera el reporte, el reporte y su método STAR con sus restricciones y la
 * inmutabilidad de lo emitido.
 */
class ReportSchemaMigrationTest extends AbstractPersistenceTest {

    private static final String ANSWERS = "microentrevista.intento_respuesta";
    private static final String REPORT = "microentrevista.reporte_evaluacion";
    private static final String STAR = "microentrevista.reporte_star";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InterviewSessionRepository sessions;

    private UUID newSession() {
        return sessions.save(newSession("uid-reporte")).id();
    }

    private void insertAnswer(UUID turnId, String origin, Object words, Object spanMs, Object pausesMs,
            Object fillers) {
        jdbc.update("INSERT INTO " + ANSWERS + " (id, turno_id, tipo_interaccion, es_definitiva, origen_respuesta,"
                + " respuesta_texto, pregunta_mostrada, idioma_detectado, fecha_creacion, voz_palabras, voz_tramo_ms,"
                + " voz_pausas_ms, voz_muletillas) VALUES (?, ?, 'INICIAL', true, ?, 'respuesta', 'pregunta', ?, now(),"
                + " ?, ?, ?, ?)", UUID.randomUUID(), turnId, origin, "AUDIO".equals(origin) ? "es" : null, words,
                spanMs, pausesMs, fillers);
    }

    private void insertExecution(UUID sessionId, UUID turnId, String operation, String status) {
        jdbc.update("INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion,"
                + " numero_intento, idempotency_key, estado, fecha_inicio, fecha_fin, modelo, version_prompt)"
                + " VALUES (?, ?, ?, ?, 1, ?, ?, now(), CASE WHEN ? = 'EN_CURSO' THEN NULL ELSE now() END,"
                + " 'modelo-de-prueba', 'reporte.v1')", UUID.randomUUID(), sessionId, turnId, operation,
                UUID.randomUUID(), status, status);
    }

    /** Inserta un reporte con una sola columna bajo prueba; con voz, las otras dos métricas de voz van válidas. */
    private void insertReport(UUID sessionId, String column, Object value, boolean withVoice) {
        String sql;
        if (withVoice) {
            sql = "INSERT INTO " + REPORT + " (sesion_id, fecha_emision, ritmo_habla, tiempo_pausas, tasa_vacilacion)"
                    + " VALUES (?, now(), " + ("ritmo_habla".equals(column) ? "?" : "120") + ", "
                    + ("tiempo_pausas".equals(column) ? "?" : "10.0") + ", "
                    + ("tasa_vacilacion".equals(column) ? "?" : "5.0") + ")";
        } else {
            sql = "INSERT INTO " + REPORT + " (sesion_id, fecha_emision, " + column + ") VALUES (?, now(), ?)";
        }
        jdbc.update(sql, sessionId, value);
    }

    private UUID questionOf(UUID sessionId) {
        return jdbc.queryForObject("SELECT id FROM microentrevista.pregunta WHERE sesion_id = ?", UUID.class,
                sessionId);
    }

    private void insertEmptyReport(UUID sessionId) {
        jdbc.update("INSERT INTO " + REPORT + " (sesion_id, fecha_emision) VALUES (?, now())", sessionId);
    }

    private void insertStar(UUID sessionId, UUID questionId, Boolean s, Boolean t, Boolean a, Boolean r,
            Integer level) {
        jdbc.update("INSERT INTO " + STAR + " (sesion_id, pregunta_id, situacion, tarea, accion, resultado, nivel)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)", sessionId, questionId, s, t, a, r, level);
    }

    private static String constraintOf(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException psql && psql.getServerErrorMessage() != null
                    && psql.getServerErrorMessage().getConstraint() != null) {
                return psql.getServerErrorMessage().getConstraint();
            }
        }
        return null;
    }

    @Test
    @DisplayName("La tabla de respuestas solo suma cuatro columnas de voz, todas enteras")
    void answerTable_shouldOnlyAddFourIntegerVoiceColumns() {
        List<String> columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns"
                + " WHERE table_schema = 'microentrevista' AND table_name = 'intento_respuesta'", String.class);
        assertThat(columns).containsExactlyInAnyOrder("id", "turno_id", "tipo_interaccion", "es_definitiva",
                "origen_respuesta", "respuesta_texto", "pregunta_mostrada", "idioma_detectado", "fecha_creacion",
                "voz_palabras", "voz_tramo_ms", "voz_pausas_ms", "voz_muletillas");
        assertThat(jdbc.queryForList("SELECT data_type FROM information_schema.columns WHERE table_schema ="
                + " 'microentrevista' AND table_name = 'intento_respuesta' AND column_name LIKE 'voz\\_%'",
                String.class)).containsOnly("integer").hasSize(4);
    }

    @Test
    @DisplayName("Una muestra de voz válida se guarda")
    void voiceColumns_shouldAcceptAValidSample() {
        UUID turn = insertTurn(newSession());
        assertThatCode(() -> insertAnswer(turn, "AUDIO", 65, 36310, 15734, 6)).doesNotThrowAnyException();
        assertThat(jdbc.queryForMap("SELECT voz_palabras, voz_tramo_ms, voz_pausas_ms, voz_muletillas FROM "
                + ANSWERS + " WHERE turno_id = ?", turn))
                .containsEntry("voz_palabras", 65).containsEntry("voz_tramo_ms", 36310)
                .containsEntry("voz_pausas_ms", 15734).containsEntry("voz_muletillas", 6);
    }

    @Test
    @DisplayName("Una muestra de voz incompleta se rechaza")
    void voiceColumns_shouldRejectPartialSample() {
        UUID turn = insertTurn(newSession());
        assertThatThrownBy(() -> insertAnswer(turn, "AUDIO", 65, null, 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_intento_respuesta_voz_completa");
    }

    @Test
    @DisplayName("Las pausas no pueden durar más que el tramo hablado")
    void voiceColumns_shouldRejectPausesLongerThanSpan() {
        UUID turn = insertTurn(newSession());
        assertThatThrownBy(() -> insertAnswer(turn, "AUDIO", 10, 1000, 1001, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_intento_respuesta_voz_valores");
    }

    @Test
    @DisplayName("No puede haber más muletillas que palabras")
    void voiceColumns_shouldRejectMoreFillersThanWords() {
        UUID turn = insertTurn(newSession());
        assertThatThrownBy(() -> insertAnswer(turn, "AUDIO", 2, 1000, 0, 3))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_intento_respuesta_voz_valores");
    }

    @Test
    @DisplayName("El tramo hablado de cero milisegundos se rechaza")
    void voiceColumns_shouldRejectZeroSpan() {
        UUID turn = insertTurn(newSession());
        assertThatThrownBy(() -> insertAnswer(turn, "AUDIO", 1, 0, 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_intento_respuesta_voz_valores");
    }

    @Test
    @DisplayName("Una respuesta escrita no puede traer datos de voz")
    void voiceColumns_shouldRejectVoiceOnTextAnswer() {
        UUID turn = insertTurn(newSession());
        assertThatThrownBy(() -> insertAnswer(turn, "TEXTO", 5, 1000, 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_intento_respuesta_voz_origen");
    }

    @Test
    @DisplayName("La operación GENERAR_REPORTE no lleva turno y las demás reglas siguen igual")
    void aiExecution_shouldAcceptReportWithoutTurn() {
        UUID session = newSession();
        UUID turn = insertTurn(session);

        assertThatCode(() -> insertExecution(session, null, "GENERAR_REPORTE", "EXITOSA")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertExecution(session, turn, "GENERAR_REPORTE", "EXITOSA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ejecucion_ia_turno");
        assertThatCode(() -> insertExecution(session, null, "PLANIFICAR", "EXITOSA")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertExecution(session, null, "EVALUAR", "EXITOSA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ejecucion_ia_turno");
        assertThatThrownBy(() -> insertExecution(session, turn, "OTRA", "EXITOSA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ejecucion_ia_tipo_operacion");
    }

    @Test
    @DisplayName("Solo una generación del reporte puede estar en curso por sesión")
    void reportClaim_shouldAllowOnlyOneInProgressPerSession() {
        UUID session = newSession();
        insertExecution(session, null, "GENERAR_REPORTE", "EN_CURSO");

        assertThatThrownBy(() -> insertExecution(session, null, "GENERAR_REPORTE", "EN_CURSO"))
                .hasMessageContaining("uq_ejecucion_ia_reporte_en_curso");
        assertThatCode(() -> insertExecution(session, null, "GENERAR_REPORTE", "EXITOSA")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una sesión tiene un solo reporte")
    void report_shouldBeUniquePerSession() {
        UUID session = newSession();
        insertEmptyReport(session);

        assertThatThrownBy(() -> insertEmptyReport(session)).hasMessageContaining("pk_reporte_evaluacion");
    }

    @ParameterizedTest(name = "{0} = {1} rompe {2}")
    @CsvSource({
            "precision_claridad, 0.99, ck_reporte_evaluacion_precision_claridad, false",
            "precision_claridad, 5.01, ck_reporte_evaluacion_precision_claridad, false",
            "carisma_verbal, 0.99, ck_reporte_evaluacion_carisma_verbal, false",
            "lenguaje_adecuado, 5.01, ck_reporte_evaluacion_lenguaje_adecuado, false",
            "tiempo_pausas, 100.1, ck_reporte_evaluacion_tiempo_pausas, true",
            "tiempo_pausas, -0.1, ck_reporte_evaluacion_tiempo_pausas, true",
            "ritmo_habla, -1, ck_reporte_evaluacion_ritmo_habla, true",
            "tasa_vacilacion, 100.1, ck_reporte_evaluacion_tasa_vacilacion, true",
            "entusiasmo, 1, ck_reporte_evaluacion_entusiasmo, false"})
    @DisplayName("Un valor fuera de rango del reporte se rechaza con su restricción")
    void report_shouldRejectOutOfRangeValues(String column, BigDecimal value, String constraint,
            boolean withVoice) {
        UUID session = newSession();
        Object parameter = "ritmo_habla".equals(column) ? (Object) value.intValue() : value;

        assertThatThrownBy(() -> insertReport(session, column, parameter, withVoice))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    @Test
    @DisplayName("Los extremos del rango del reporte se aceptan")
    void report_shouldAcceptBoundaryValues() {
        UUID session = newSession();
        assertThatCode(() -> jdbc.update("INSERT INTO " + REPORT + " (sesion_id, precision_claridad,"
                + " carisma_verbal, lenguaje_adecuado, ritmo_habla, tiempo_pausas, tasa_vacilacion, fecha_emision)"
                + " VALUES (?, 1, 5, 1.00, 0, 100.0, 0.0, now())", session)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Las métricas de voz del reporte van las tres o ninguna")
    void report_shouldRejectPartialVoiceMetrics() {
        UUID session = newSession();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + REPORT + " (sesion_id, ritmo_habla, fecha_emision)"
                + " VALUES (?, 120, now())", session))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reporte_evaluacion_voz_completa");
    }

    @Test
    @DisplayName("El nivel del STAR debe ser la suma de sus cuatro componentes")
    void star_shouldRejectWrongLevel() {
        UUID session = newSession();
        UUID question = questionOfNewTurn(session);
        insertEmptyReport(session);

        assertThatThrownBy(() -> insertStar(session, question, true, true, true, false, 4))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_star_nivel");
        assertThatCode(() -> insertStar(session, question, true, true, true, false, 3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Los componentes del STAR van todos o ninguno")
    void star_shouldRejectPartialNulls() {
        UUID session = newSession();
        UUID question = questionOfNewTurn(session);
        insertEmptyReport(session);

        assertThatThrownBy(() -> insertStar(session, question, true, null, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reporte_star_completo");
        assertThatCode(() -> insertStar(session, question, null, null, null, null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El reporte emitido y su STAR no se pueden modificar")
    void report_shouldBeImmutable() {
        UUID session = newSession();
        UUID question = questionOfNewTurn(session);
        jdbc.update("INSERT INTO " + REPORT + " (sesion_id, ritmo_habla, tiempo_pausas, tasa_vacilacion,"
                + " fecha_emision) VALUES (?, 120, 10.0, 5.0, now())", session);
        insertStar(session, question, true, true, true, true, 4);

        assertThatThrownBy(() -> jdbc.update("UPDATE " + REPORT + " SET ritmo_habla = 1 WHERE sesion_id = ?",
                session)).satisfies(e -> assertThat(constraintOf(e)).isEqualTo("trg_reporte_inmutable"));
        assertThatThrownBy(() -> jdbc.update("UPDATE " + STAR + " SET situacion = false, nivel = 3 WHERE sesion_id = ?",
                session)).satisfies(e -> assertThat(constraintOf(e)).isEqualTo("trg_reporte_inmutable"));
    }

    @Test
    @DisplayName("Borrar la sesión borra su reporte y su STAR")
    void deletingSession_shouldCascadeToReportAndStar() {
        UUID session = newSession();
        UUID question = questionOfNewTurn(session);
        insertEmptyReport(session);
        insertStar(session, question, true, true, true, true, 4);
        jdbc.update("UPDATE microentrevista.sesion SET estado = 'FINALIZADA', fecha_fin = now() WHERE id = ?", session);

        jdbc.update("DELETE FROM microentrevista.sesion WHERE id = ?", session);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + REPORT, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + STAR, Integer.class)).isZero();
    }

    private UUID questionOfNewTurn(UUID sessionId) {
        insertTurn(sessionId);
        return questionOf(sessionId);
    }
}
