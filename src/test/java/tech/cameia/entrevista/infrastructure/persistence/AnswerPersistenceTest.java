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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.exception.AnswerAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.TurnConflictException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnsweredQuestion;
import tech.cameia.entrevista.domain.model.EvaluationResult;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.TurnFeedback;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.TurnFeedbackRepository;

/**
 * Persistencia de responder una pregunta contra PostgreSQL real: ejecuciones de evaluación, respuesta definitiva,
 * historial, retroalimentación y contador de reintentos.
 */
class AnswerPersistenceTest extends AbstractPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InterviewSessionRepository sessions;
    @Autowired
    private InterviewTurnRepository turns;
    @Autowired
    private AiExecutionRepository executions;
    @Autowired
    private AnswerAttemptRepository answers;
    @Autowired
    private TurnFeedbackRepository feedbacks;

    private UUID sessionId;
    private UUID turnId;

    private void givenSessionWithTurn() {
        sessionId = sessions.save(newSession("uid-a")).id();
        turnId = insertTurn(sessionId);
    }

    private AiExecution startEvaluation() {
        return executions.save(AiExecution.evaluationStarted(sessionId, turnId, "gemini-3.5-flash-lite",
                "evaluacion.v1", "gemini", CLOCK));
    }

    private static EvaluationResult result() {
        return new EvaluationResult("AVANZAR", "COMPLETA", "Responde con situación y resultado", "Buen ejemplo");
    }

    @Test
    @DisplayName("Una evaluación se guarda con su operación y su turno")
    void save_shouldPersistEvaluationWithTurn() {
        givenSessionWithTurn();

        startEvaluation();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT tipo_operacion, turno_id, numero_intento, estado FROM microentrevista.ejecucion_ia
                 WHERE tipo_operacion = 'EVALUAR'
                """);
        assertThat(row).containsEntry("tipo_operacion", "EVALUAR").containsEntry("turno_id", turnId)
                .containsEntry("estado", "EN_CURSO");
        assertThat(executions.findEvaluationInProgress(turnId)).get().satisfies(found -> {
            assertThat(found.turnId()).isEqualTo(turnId);
            assertThat(found.operation().code()).isEqualTo("EVALUAR");
        });
    }

    @Test
    @DisplayName("Una segunda evaluación en curso del mismo turno lanza TURN_CONFLICT")
    void save_shouldThrowTurnConflict_whenSecondEvaluationInProgress() {
        givenSessionWithTurn();
        startEvaluation();

        assertThatThrownBy(this::startEvaluation).isInstanceOf(TurnConflictException.class);
    }

    @Test
    @DisplayName("Una evaluación fallida no cuenta como en curso y deja abrir otra")
    void findEvaluationInProgress_shouldIgnoreFailed() {
        givenSessionWithTurn();
        AiExecution started = startEvaluation();

        executions.save(started.failed(ErrorCode.LLM_UNAVAILABLE, CLOCK));

        assertThat(executions.findEvaluationInProgress(turnId)).isEmpty();
        assertThat(startEvaluation().turnId()).isEqualTo(turnId);
    }

    @Test
    @DisplayName("La planificación se guarda como antes: operación PLANIFICAR y sin turno")
    void save_shouldKeepPlanningAsBefore() {
        sessionId = sessions.save(newSession("uid-a")).id();

        executions.save(AiExecution.planningStarted(sessionId, 1, "modelo", "planificacion.v6", "gemini", CLOCK));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT tipo_operacion, turno_id FROM microentrevista.ejecucion_ia WHERE sesion_id = ?", sessionId);
        assertThat(row).containsEntry("tipo_operacion", "PLANIFICAR").containsEntry("turno_id", null);
        assertThat(executions.findPlanningInProgress(sessionId)).isPresent();
        assertThat(executions.findEvaluationInProgress(turnIdOfNoOne())).isEmpty();
    }

    private static UUID turnIdOfNoOne() {
        return UUID.randomUUID();
    }

    @Test
    @DisplayName("Una respuesta de 4000 emoji se guarda completa y se lee igual")
    void save_shouldStoreFullText() {
        givenSessionWithTurn();
        String text = "😀".repeat(4000);

        answers.save(AnswerAttempt.written(turnId, text, "Cuéntame sobre ti", CLOCK));

        AnswerAttempt found = answers.findDefinitive(turnId).orElseThrow();
        assertThat(found.text()).isEqualTo(text);
        assertThat(found.origin()).isEqualTo(AnswerAttempt.AnswerOrigin.TEXTO);
        assertThat(found.detectedLanguage()).isNull();
        assertThat(found.shownQuestion()).isEqualTo("Cuéntame sobre ti");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT tipo_interaccion, es_definitiva, origen_respuesta FROM microentrevista.intento_respuesta");
        assertThat(row).containsEntry("tipo_interaccion", "INICIAL").containsEntry("es_definitiva", true)
                .containsEntry("origen_respuesta", "TEXTO");
    }

    @Test
    @DisplayName("Una segunda respuesta definitiva del mismo turno lanza ANSWER_ALREADY_EXISTS")
    void save_shouldThrowAnswerAlreadyExists_whenSecondDefinitive() {
        givenSessionWithTurn();
        answers.save(AnswerAttempt.written(turnId, "primera", "Pregunta", CLOCK));

        assertThatThrownBy(() -> answers.save(AnswerAttempt.written(turnId, "segunda", "Pregunta", CLOCK)))
                .isInstanceOf(AnswerAlreadyExistsException.class);
        assertThat(answers.findDefinitive(turnId).orElseThrow().text()).isEqualTo("primera");
    }

    @Test
    @DisplayName("Sin respuesta, findDefinitive devuelve vacío")
    void findDefinitive_shouldBeEmpty_whenNoAnswer() {
        givenSessionWithTurn();

        assertThat(answers.findDefinitive(turnId)).isEmpty();
    }

    @Test
    @DisplayName("El historial sale por posición aunque las respuestas se hayan insertado en otro orden")
    void historyOf_shouldReturnInOrder() {
        sessionId = sessions.save(newSession("uid-a")).id();
        UUID second = insertTurn(sessionId, 2);
        UUID first = insertTurn(sessionId, 1);
        UUID third = insertTurn(sessionId, 3);
        answers.save(AnswerAttempt.written(second, "respuesta dos", "Cuéntame sobre ti", CLOCK));
        answers.save(AnswerAttempt.written(first, "respuesta uno", "Cuéntame sobre ti", CLOCK));
        answers.save(AnswerAttempt.written(third, "respuesta tres", "Cuéntame sobre ti", CLOCK));

        List<AnsweredQuestion> history = answers.historyOf(sessionId);

        assertThat(history).extracting(AnsweredQuestion::number).containsExactly(1, 2, 3);
        assertThat(history).extracting(AnsweredQuestion::answer)
                .containsExactly("respuesta uno", "respuesta dos", "respuesta tres");
        assertThat(history).extracting(AnsweredQuestion::question).containsOnly("Cuéntame sobre ti");
    }

    @Test
    @DisplayName("Sin respuestas el historial está vacío")
    void historyOf_shouldBeEmpty() {
        givenSessionWithTurn();

        assertThat(answers.historyOf(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("La retroalimentación se guarda con sus cuatro claves y sin pregunta de seguimiento")
    void save_shouldStoreFourKeys() {
        givenSessionWithTurn();
        AiExecution execution = startEvaluation();

        feedbacks.save(TurnFeedback.of(turnId, execution.id(), result(), CLOCK));

        String keys = jdbc.queryForObject("""
                SELECT string_agg(k, ',' ORDER BY k) FROM microentrevista.feedback_turno f,
                       jsonb_object_keys(f.feedback) AS k
                """, String.class);
        assertThat(keys).isEqualTo("accion,clasificacion,feedback,motivo");
        assertThat(jdbc.queryForObject("SELECT feedback ->> 'accion' FROM microentrevista.feedback_turno",
                String.class)).isEqualTo("AVANZAR");
    }

    @Test
    @DisplayName("El texto de la retroalimentación conserva comillas y saltos de línea")
    void save_shouldKeepSpecialCharacters() {
        givenSessionWithTurn();
        AiExecution execution = startEvaluation();
        EvaluationResult tricky = new EvaluationResult("FOLLOW_UP", "VAGA", "dijo \"nada\"", "línea 1\nlínea 2 😀");

        feedbacks.save(TurnFeedback.of(turnId, execution.id(), tricky, CLOCK));

        assertThat(jdbc.queryForObject("SELECT feedback ->> 'feedback' FROM microentrevista.feedback_turno",
                String.class)).isEqualTo("línea 1\nlínea 2 😀");
        assertThat(jdbc.queryForObject("SELECT feedback ->> 'motivo' FROM microentrevista.feedback_turno",
                String.class)).isEqualTo("dijo \"nada\"");
    }

    @Test
    @DisplayName("Una segunda retroalimentación del mismo turno lanza TURN_CONFLICT")
    void save_shouldRejectSecondFeedback() {
        givenSessionWithTurn();
        AiExecution execution = startEvaluation();
        feedbacks.save(TurnFeedback.of(turnId, execution.id(), result(), CLOCK));

        assertThatThrownBy(() -> feedbacks.save(TurnFeedback.of(turnId, execution.id(), result(), CLOCK)))
                .isInstanceOf(TurnConflictException.class);
    }

    @Test
    @DisplayName("La columna feedback_star queda nula")
    void save_shouldStoreNullStar() {
        givenSessionWithTurn();
        AiExecution execution = startEvaluation();

        feedbacks.save(TurnFeedback.of(turnId, execution.id(), result(), CLOCK));

        assertThat(jdbc.queryForObject("SELECT feedback_star IS NULL FROM microentrevista.feedback_turno",
                Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("countBySession cuenta solo las retroalimentaciones de los turnos de esa sesión")
    void countBySession_shouldCountOnlyThatSession() {
        givenSessionWithTurn();
        UUID secondTurn = insertTurn(sessionId, 2);
        UUID otherSession = sessions.save(newSession("uid-b")).id();
        UUID otherTurn = insertTurn(otherSession);
        AiExecution first = startEvaluation();
        feedbacks.save(TurnFeedback.of(turnId, first.id(), result(), CLOCK));
        AiExecution second = executions.save(AiExecution.evaluationStarted(sessionId, secondTurn, "modelo",
                "evaluacion.v1", "gemini", CLOCK));
        feedbacks.save(TurnFeedback.of(secondTurn, second.id(), result(), CLOCK));
        AiExecution foreign = executions.save(AiExecution.evaluationStarted(otherSession, otherTurn, "modelo",
                "evaluacion.v1", "gemini", CLOCK));
        feedbacks.save(TurnFeedback.of(otherTurn, foreign.id(), result(), CLOCK));

        assertThat(feedbacks.countBySession(sessionId)).isEqualTo(2);
        assertThat(feedbacks.countBySession(otherSession)).isEqualTo(1);
        assertThat(feedbacks.countBySession(UUID.randomUUID())).isZero();
    }

    @Test
    @DisplayName("findByIdAndSession ignora el turno de otra sesión")
    void findByIdAndSession_shouldIgnoreOtherSession() {
        givenSessionWithTurn();
        UUID otherSession = sessions.save(newSession("uid-b")).id();

        assertThat(turns.findByIdAndSession(turnId, sessionId)).isPresent();
        assertThat(turns.findByIdAndSession(turnId, otherSession)).isEmpty();
        assertThat(turns.findByIdAndSession(UUID.randomUUID(), sessionId)).isEmpty();
    }

    @Test
    @DisplayName("countCompleted cuenta solo los turnos completados de la sesión")
    void countCompleted_shouldCountOnlyCompleted() {
        sessionId = sessions.save(newSession("uid-a")).id();
        UUID first = insertTurn(sessionId, 1);
        UUID second = insertTurn(sessionId, 2);
        insertTurn(sessionId, 3);
        jdbc.update("UPDATE microentrevista.turno SET estado = 'COMPLETED' WHERE id IN (?, ?)", first, second);
        UUID otherSession = sessions.save(newSession("uid-b")).id();
        UUID foreign = insertTurn(otherSession, 1);
        jdbc.update("UPDATE microentrevista.turno SET estado = 'COMPLETED' WHERE id = ?", foreign);

        assertThat(turns.countCompleted(sessionId)).isEqualTo(2);
        assertThat(turns.countCompleted(UUID.randomUUID())).isZero();
    }

    @Test
    @DisplayName("Los reintentos manuales se guardan y se leen")
    void save_shouldPersistManualRetries() {
        givenSessionWithTurn();
        InterviewTurn turn = turns.findById(turnId).orElseThrow();
        turn.startProcessing(CLOCK);
        turn.retry(CLOCK);
        turn.startManualRetry(false, CLOCK);

        turns.save(turn);

        assertThat(jdbc.queryForObject("SELECT reintentos_manuales FROM microentrevista.turno WHERE id = ?",
                Integer.class, turnId)).isEqualTo(1);
        InterviewTurn reloaded = turns.findById(turnId).orElseThrow();
        assertThat(reloaded.manualRetries()).isEqualTo(1);
        assertThat(reloaded.status()).isEqualTo(TurnStatus.PROCESSING);
    }
}
