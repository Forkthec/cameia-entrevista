package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiExecutionStatus;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

/**
 * Persistencia de la planificación contra PostgreSQL real: la marca de planificación (índice único parcial),
 * las preguntas con sus restricciones y disparadores, el turno abierto y el orden de abandono e inicio.
 */
class PlanningPersistenceTest extends AbstractPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InterviewSessionRepository sessions;
    @Autowired
    private AiExecutionRepository executions;
    @Autowired
    private InterviewQuestionRepository questions;
    @Autowired
    private InterviewTurnRepository turns;

    private InterviewSession savedSession(String uid) {
        return sessions.save(newSession(uid));
    }

    private AiExecution claim(UUID sessionId, int attempt) {
        return executions.save(AiExecution.planningStarted(sessionId, attempt, "gemini-3.5-flash-lite",
                "planificacion.v6+CALIDO.v4", "gemini", CLOCK));
    }

    private InterviewQuestion question(UUID sessionId, UUID executionId, int position, String text) {
        QuestionType[] template = { QuestionType.APERTURA, QuestionType.TECNICA, QuestionType.CONDUCTUAL,
            QuestionType.TECNICA, QuestionType.SITUACIONAL };
        return new InterviewQuestion(UUID.randomUUID(), sessionId, position, template[position - 1], text,
                executionId, CLOCK.instant());
    }

    @Test
    @DisplayName("Una segunda marca de planificación en curso de la misma sesión se rechaza como conflicto")
    void save_shouldThrowConflict_whenSecondPlanningInProgress() {
        UUID sessionId = savedSession("uid-a").id();
        claim(sessionId, 1);

        assertThatThrownBy(() -> claim(sessionId, 1)).isInstanceOf(SessionConflictException.class);
        assertThat(executions.findPlanningInProgress(sessionId)).isPresent();
    }

    @Test
    @DisplayName("Cerrar la marca libera el índice: otra marca de la misma sesión se acepta")
    void save_shouldAllowNewClaim_afterClosingTheFirst() {
        UUID sessionId = savedSession("uid-a").id();
        AiExecution first = claim(sessionId, 1);

        AiExecution closed = executions.save(first.failed(ErrorCode.LLM_UNAVAILABLE,
                CLOCK));

        assertThat(closed.status()).isEqualTo(AiExecutionStatus.FAILED);
        assertThat(closed.errorCode()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(executions.findPlanningInProgress(sessionId)).isEmpty();
        assertThat(executions.hasFailedPlanning(sessionId)).isTrue();
        assertThat(claim(sessionId, 2).attempt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Dos sesiones distintas pueden tener cada una su marca en curso")
    void save_shouldAllowOneClaimPerSession() {
        claim(savedSession("uid-a").id(), 1);
        claim(savedSession("uid-b").id(), 1);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM microentrevista.ejecucion_ia WHERE estado = 'EN_CURSO'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Una ejecución exitosa guarda versión del prompt, tokens y fecha de cierre")
    void save_shouldPersistSuccess() {
        UUID sessionId = savedSession("uid-a").id();

        AiExecution done = executions.save(claim(sessionId, 1).succeeded(1200, 210,
                CLOCK));

        assertThat(done.promptVersion()).isEqualTo("planificacion.v6+CALIDO.v4");
        assertThat(done.inputTokens()).isEqualTo(1200L);
        assertThat(done.finishedAt()).isEqualTo(CLOCK.instant());
        assertThat(executions.hasFailedPlanning(sessionId)).isFalse();
    }

    @Test
    @DisplayName("failIfInProgress cierra la marca en curso, y no crea ni toca nada si ya no existe o ya está cerrada")
    void failIfInProgress_shouldCloseOnlyAnOpenMark() {
        UUID sessionId = savedSession("uid-a").id();
        AiExecution open = claim(sessionId, 1);

        assertThat(executions.failIfInProgress(open.failed(ErrorCode.DATABASE_UNAVAILABLE, CLOCK))).isTrue();
        assertThat(executions.hasFailedPlanning(sessionId)).isTrue();
        assertThat(executions.failIfInProgress(open.failed(ErrorCode.LLM_UNAVAILABLE, CLOCK))).isFalse();

        executions.deletePlanningOf(sessionId);
        assertThat(executions.failIfInProgress(open.failed(ErrorCode.LLM_UNAVAILABLE, CLOCK))).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM microentrevista.ejecucion_ia WHERE sesion_id = ?", Integer.class, sessionId))
                .isZero();
    }

    @Test
    @DisplayName("deletePlanningOf borra las ejecuciones de planificación de la sesión")
    void deletePlanningOf_shouldRemoveExecutions() {
        UUID sessionId = savedSession("uid-a").id();
        executions.save(claim(sessionId, 1).failed(ErrorCode.LLM_INVALID_OUTPUT, CLOCK));

        executions.deletePlanningOf(sessionId);

        assertThat(executions.hasFailedPlanning(sessionId)).isFalse();
    }

    @Test
    @DisplayName("Las cinco preguntas se guardan y se leen por posición; el turno abierto es el de la primera")
    void saveAll_shouldPersistFiveQuestions_andFindOpenTurn() {
        UUID sessionId = savedSession("uid-a").id();
        AiExecution execution = executions.save(claim(sessionId, 1).succeeded(1, 1, CLOCK));
        List<InterviewQuestion> five = List.of(question(sessionId, execution.id(), 3, "c"),
                question(sessionId, execution.id(), 1, "a"), question(sessionId, execution.id(), 2, "b"),
                question(sessionId, execution.id(), 4, "d"), question(sessionId, execution.id(), 5, "e"));

        questions.saveAll(five);
        InterviewTurn first = turns.save(InterviewTurn.open(five.get(1).id(), CLOCK));

        assertThat(questions.findBySession(sessionId)).extracting(InterviewQuestion::originalText)
                .containsExactly("a", "b", "c", "d", "e");
        assertThat(turns.findOpenBySession(sessionId)).map(InterviewTurn::id).contains(first.id());
        assertThat(turns.findOpenBySession(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("El texto de 500 puntos de código se acepta y el de 501 se rechaza por el largo de la columna")
    void saveAll_shouldEnforce500Limit() {
        UUID sessionId = savedSession("uid-a").id();
        AiExecution execution = executions.save(claim(sessionId, 1).succeeded(1, 1, CLOCK));

        questions.saveAll(List.of(question(sessionId, execution.id(), 1, "é".repeat(500))));
        assertThatThrownBy(() -> questions.saveAll(List.of(question(sessionId, execution.id(), 2, "é".repeat(501)))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("value too long for type character varying(500)");
    }

    @Test
    @DisplayName("Dos preguntas en la misma posición se rechazan por uq_pregunta_sesion_orden")
    void saveAll_shouldRejectDuplicatedPosition() {
        UUID sessionId = savedSession("uid-a").id();
        AiExecution execution = executions.save(claim(sessionId, 1).succeeded(1, 1, CLOCK));
        questions.saveAll(List.of(question(sessionId, execution.id(), 1, "a")));

        assertThatThrownBy(() -> questions.saveAll(List.of(question(sessionId, execution.id(), 1, "b"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_pregunta_sesion_orden");
    }

    @Test
    @DisplayName("Con la sesión EN_CURSO la pregunta no se cambia de texto, tipo ni posición y no se borra")
    void question_shouldBeImmutable_whenSessionStarted() {
        InterviewSession session = savedSession("uid-a");
        AiExecution execution = executions.save(claim(session.id(), 1).succeeded(1, 1, CLOCK));
        InterviewQuestion q = question(session.id(), execution.id(), 1, "a");
        questions.saveAll(List.of(q));
        session.start(CLOCK);
        sessions.save(session);

        for (String sql : List.of("UPDATE microentrevista.pregunta SET texto_original = 'x' WHERE id = ?",
                "UPDATE microentrevista.pregunta SET tipo = 'TECNICA' WHERE id = ?",
                "UPDATE microentrevista.pregunta SET orden = 2 WHERE id = ?",
                "DELETE FROM microentrevista.pregunta WHERE id = ?")) {
            assertThatThrownBy(() -> jdbc.update(sql, q.id())).hasMessageContaining("pregunta inmutable");
        }
    }

    @Test
    @DisplayName("En una transacción, abandonar la sesión en curso y después iniciar la configurada no viola el índice")
    void startAfterAbandon_shouldRespectInProgressIndex_whenSameTransaction() {
        InterviewSession previous = savedSession("uid-a");
        previous.start(CLOCK);
        sessions.save(previous);
        InterviewSession next = savedSession("uid-a");

        transaction.executeWithoutResult(status -> {
            InterviewSession old = sessions.findInProgressByOwner(previous.owner()).orElseThrow();
            old.abandon(CLOCK);
            sessions.save(old);
            InterviewSession current = sessions.findByIdAndOwner(next.id(), next.owner()).orElseThrow();
            current.start(CLOCK);
            sessions.save(current);
        });

        assertThat(sessions.findInProgressByOwner(previous.owner())).map(InterviewSession::id).contains(next.id());
        assertThat(jdbc.queryForObject("SELECT estado FROM microentrevista.sesion WHERE id = ?", String.class,
                previous.id())).isEqualTo("ABANDONADA");
    }

    @Test
    @DisplayName("Iniciar la nueva antes de abandonar la anterior sí viola el índice de una en curso")
    void startBeforeAbandon_shouldViolateInProgressIndex() {
        InterviewSession previous = savedSession("uid-a");
        previous.start(CLOCK);
        sessions.save(previous);
        InterviewSession next = savedSession("uid-a");
        next.start(CLOCK);

        assertThatThrownBy(() -> sessions.save(next))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException.class);
    }
}
