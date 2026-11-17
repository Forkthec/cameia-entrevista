package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;
import tech.cameia.entrevista.domain.model.ContentScores;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.ReportQuestion;
import tech.cameia.entrevista.domain.model.StarResult;
import tech.cameia.entrevista.domain.model.VoiceMetrics;
import tech.cameia.entrevista.domain.model.VoiceSample;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.EvaluationReportRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;

/**
 * Persistencia del Reporte de Evaluación sobre PostgreSQL real: el reporte y su STAR se guardan y se leen iguales, lo
 * que no se pudo calcular queda {@code NULL} (nunca 0), un segundo reporte se rechaza y las entradas de la IA
 * salen de las respuestas definitivas.
 */
class ReportPersistenceTest extends AbstractPersistenceTest {

    private static final Instant ISSUED = Instant.parse("2026-11-17T15:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InterviewSessionRepository sessions;

    @Autowired
    private AnswerAttemptRepository attempts;

    @Autowired
    private EvaluationReportRepository reports;

    @Autowired
    private AiExecutionRepository executions;

    /** Crea una sesión con las cinco preguntas; la 3 y la 5 son conductuales. Devuelve los turnos por posición. */
    private UUID[] sessionWithFiveQuestions(UUID[] sessionOut) {
        UUID session = sessions.save(newSession("uid-reporte")).id();
        sessionOut[0] = session;
        UUID execution = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion, numero_intento,
                    idempotency_key, estado, fecha_inicio, fecha_fin, modelo, version_prompt)
                VALUES (?, ?, NULL, 'PLANIFICAR', 1, ?, 'EXITOSA', now(), now(), 'modelo-de-prueba', 'v1')
                """, execution, session, UUID.randomUUID());
        UUID[] turns = new UUID[6];
        for (int order = 1; order <= 5; order++) {
            turns[order] = insertQuestionAndTurn(session, execution, order);
        }
        return turns;
    }

    private UUID insertQuestionAndTurn(UUID session, UUID execution, int order) {
        UUID question = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO microentrevista.pregunta (id, sesion_id, orden, tipo, texto_original, fecha_creacion,
                    ejecucion_ia_id)
                VALUES (?, ?, ?, ?, 'Cuéntame sobre ti', now(), ?)
                """, question, session, order, order == 3 || order == 5 ? "CONDUCTUAL" : "APERTURA", execution);
        jdbc.update("""
                INSERT INTO microentrevista.turno (id, pregunta_id, estado, fecha_creacion, fecha_actualizacion,
                    version)
                VALUES (?, ?, 'PENDING', now(), now(), 0)
                """, turn, question);
        return turn;
    }

    private static EvaluationReport fullReport(UUID session) {
        return new EvaluationReport(session, InterviewLanguage.ENGLISH,
                new ContentScores(new BigDecimal("4.33"), new BigDecimal("3.67"), new BigDecimal("3.00")),
                new VoiceMetrics(150, new BigDecimal("12.3"), new BigDecimal("4.5")),
                List.of(new StarResult(5, "Cuéntame sobre ti", null, null, null, null),
                        new StarResult(3, "Cuéntame sobre ti", true, true, true, false)),
                ISSUED);
    }

    @Test
    void saveIssued_shouldRoundTrip() {
        UUID[] session = new UUID[1];
        sessionWithFiveQuestions(session);
        EvaluationReport report = fullReport(session[0]);

        reports.saveIssued(report);

        EvaluationReport read = reports.findBySession(session[0]).orElseThrow();
        assertThat(read).isEqualTo(report);
        assertThat(read.star()).extracting(StarResult::questionNumber).containsExactly(3, 5);
        assertThat(read.star().get(0).level()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT nivel FROM microentrevista.reporte_star WHERE sesion_id = ? "
                + "AND situacion", Integer.class, session[0])).isEqualTo(3);
    }

    @Test
    void saveIssued_shouldStoreNullsAsNull() {
        UUID[] session = new UUID[1];
        sessionWithFiveQuestions(session);
        EvaluationReport empty = new EvaluationReport(session[0], InterviewLanguage.ENGLISH, ContentScores.NONE,
                VoiceMetrics.NONE, List.of(StarResult.unavailable(3, "Cuéntame sobre ti")), ISSUED);

        reports.saveIssued(empty);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM microentrevista.reporte_evaluacion "
                + "WHERE sesion_id = ?", session[0]);
        assertThat(row).containsEntry("precision_claridad", null).containsEntry("carisma_verbal", null)
                .containsEntry("lenguaje_adecuado", null).containsEntry("ritmo_habla", null)
                .containsEntry("tiempo_pausas", null).containsEntry("tasa_vacilacion", null)
                .containsEntry("entusiasmo", null);
        Map<String, Object> star = jdbc.queryForMap("SELECT * FROM microentrevista.reporte_star "
                + "WHERE sesion_id = ?", session[0]);
        assertThat(star).containsEntry("situacion", null).containsEntry("tarea", null)
                .containsEntry("accion", null).containsEntry("resultado", null).containsEntry("nivel", null);
        assertThat(reports.findBySession(session[0]).orElseThrow()).isEqualTo(empty);
    }

    @Test
    void secondReport_shouldBeRejected() {
        UUID[] session = new UUID[1];
        sessionWithFiveQuestions(session);
        reports.saveIssued(fullReport(session[0]));
        EvaluationReport other = new EvaluationReport(session[0], InterviewLanguage.ENGLISH, ContentScores.NONE,
                VoiceMetrics.NONE, List.of(), ISSUED.plusSeconds(60));

        assertThatThrownBy(() -> reports.saveIssued(other))
                .isInstanceOf(EvaluationReportRepository.AlreadyIssuedException.class);

        assertThat(reports.findBySession(session[0]).orElseThrow()).isEqualTo(fullReport(session[0]));
    }

    @Test
    void findBySession_shouldBeEmptyWithoutReport() {
        UUID[] session = new UUID[1];
        sessionWithFiveQuestions(session);

        assertThat(reports.findBySession(session[0])).isEmpty();
    }

    @Test
    void reportInputsOf_shouldReturnFiveQuestionsInOrder() {
        UUID[] session = new UUID[1];
        UUID[] turns = sessionWithFiveQuestions(session);
        for (int order = 5; order >= 1; order--) {
            attempts.save(new AnswerAttempt(UUID.randomUUID(), turns[order], AnswerOrigin.TEXTO, "respuesta " + order,
                    "Cuéntame sobre ti", null, Instant.now()));
        }

        List<ReportQuestion> inputs = attempts.reportInputsOf(session[0]);

        assertThat(inputs).extracting(ReportQuestion::number).containsExactly(1, 2, 3, 4, 5);
        assertThat(inputs).extracting(ReportQuestion::type).containsExactly(QuestionType.APERTURA,
                QuestionType.APERTURA, QuestionType.CONDUCTUAL, QuestionType.APERTURA, QuestionType.CONDUCTUAL);
        assertThat(inputs).extracting(ReportQuestion::answerText).containsExactly("respuesta 1", "respuesta 2",
                "respuesta 3", "respuesta 4", "respuesta 5");
        assertThat(inputs).extracting(ReportQuestion::origin).containsOnly(AnswerOrigin.TEXTO);
        assertThat(inputs).extracting(ReportQuestion::voice).containsOnlyNulls();
    }

    @Test
    void reportInputsOf_shouldIncludeVoiceSamples() {
        UUID[] session = new UUID[1];
        UUID[] turns = sessionWithFiveQuestions(session);
        attempts.save(new AnswerAttempt(UUID.randomUUID(), turns[1], AnswerOrigin.AUDIO, "hola mundo",
                "Cuéntame sobre ti", "es", new VoiceSample(2, 1500, 500, 1), Instant.now()));
        attempts.save(new AnswerAttempt(UUID.randomUUID(), turns[2], AnswerOrigin.TEXTO, "escrita",
                "Cuéntame sobre ti", null, Instant.now()));

        List<ReportQuestion> inputs = attempts.reportInputsOf(session[0]);

        assertThat(inputs).hasSize(2);
        assertThat(inputs.get(0).origin()).isEqualTo(AnswerOrigin.AUDIO);
        assertThat(inputs.get(0).voice()).isEqualTo(new VoiceSample(2, 1500, 500, 1));
        assertThat(inputs.get(1).voice()).isNull();
    }

    @Test
    void reportInputsOf_shouldKeepTheAnswerTextWithFillers() {
        UUID[] session = new UUID[1];
        UUID[] turns = sessionWithFiveQuestions(session);
        String spoken = "Eh, pues las dos sirven para lo mismo.";
        attempts.save(new AnswerAttempt(UUID.randomUUID(), turns[1], AnswerOrigin.AUDIO, spoken, "Cuéntame sobre ti",
                "es", new VoiceSample(8, 4000, 0, 1), Instant.now()));

        assertThat(attempts.reportInputsOf(session[0]).get(0).answerText()).isEqualTo(spoken);
    }

    @Test
    void reportInputsOf_shouldSkipQuestionsWithoutAnswer() {
        UUID[] session = new UUID[1];
        UUID[] turns = sessionWithFiveQuestions(session);
        attempts.save(new AnswerAttempt(UUID.randomUUID(), turns[2], AnswerOrigin.TEXTO, "solo esta",
                "Cuéntame sobre ti", null, Instant.now()));

        List<ReportQuestion> inputs = attempts.reportInputsOf(session[0]);

        assertThat(inputs).extracting(ReportQuestion::number).containsExactly(2);
    }

    @Test
    void findReportInProgress_shouldIgnorePlanningMarks() {
        UUID[] session = new UUID[1];
        sessionWithFiveQuestions(session);
        executions.save(AiExecution.planningStarted(session[0], 3, "modelo", "plan.v1", "gemini", CLOCK));

        assertThat(executions.findReportInProgress(session[0])).isEmpty();

        executions.save(AiExecution.reportStarted(session[0], 1, "modelo", "reporte.v1", "gemini", CLOCK));

        assertThat(executions.findReportInProgress(session[0])).hasValueSatisfying(
                found -> assertThat(found.promptVersion()).isEqualTo("reporte.v1"));
    }

    @Test
    void secondReportMark_shouldBeRejectedAsConflict() {
        UUID[] session = new UUID[1];
        sessionWithFiveQuestions(session);
        executions.save(AiExecution.reportStarted(session[0], 1, "modelo", "reporte.v1", "gemini", CLOCK));

        assertThatThrownBy(() -> executions.save(AiExecution.reportStarted(session[0], 1, "modelo", "reporte.v1",
                "gemini", CLOCK))).isInstanceOf(tech.cameia.entrevista.domain.exception.SessionConflictException.class);
    }

    @Test
    void markOfAnUnknownSession_shouldRethrowTheForeignKeyViolation() {
        assertThatThrownBy(() -> executions.save(AiExecution.reportStarted(UUID.randomUUID(), 1, "modelo",
                "reporte.v1", "gemini", CLOCK)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
