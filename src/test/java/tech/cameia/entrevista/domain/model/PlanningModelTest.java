package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.exception.ErrorCode;

/** Reglas del modelo de la planificación: ejecución de la IA, pregunta, tipo y turno nuevo. */
class PlanningModelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final UUID SESSION = UUID.randomUUID();

    private static AiExecution started(int attempt) {
        return AiExecution.planningStarted(SESSION, attempt, "gemini-3.5-flash-lite", "planificacion.v6+CALIDO.v4",
                "gemini", CLOCK);
    }

    @Test
    @DisplayName("La ejecución nueva está en curso, sin cierre ni código de error")
    void planningStarted_shouldBeInProgress() {
        AiExecution execution = started(1);

        assertThat(execution.status()).isEqualTo(AiExecutionStatus.IN_PROGRESS);
        assertThat(execution.finishedAt()).isNull();
        assertThat(execution.errorCode()).isNull();
        assertThat(execution.promptVersion()).isEqualTo("planificacion.v6+CALIDO.v4");
    }

    @Test
    @DisplayName("Cerrar como exitosa o fallida conserva la identidad y fija estado, fecha y datos")
    void close_shouldKeepIdentity() {
        AiExecution base = started(2);

        AiExecution ok = base.succeeded(1200, 210, CLOCK);
        AiExecution failed = base.failed(ErrorCode.LLM_INVALID_OUTPUT, CLOCK);

        assertThat(ok.id()).isEqualTo(base.id());
        assertThat(ok.status()).isEqualTo(AiExecutionStatus.SUCCEEDED);
        assertThat(ok.inputTokens()).isEqualTo(1200L);
        assertThat(failed.status()).isEqualTo(AiExecutionStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("LLM_INVALID_OUTPUT");
        assertThat(failed.finishedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("Los textos de procedencia se recortan al largo de su columna, por puntos de código")
    void constructor_shouldCutTextsToColumnLength() {
        String longText = "😀".repeat(300);

        AiExecution execution = AiExecution.planningStarted(SESSION, 1, longText, longText, longText, CLOCK);

        assertThat(execution.model().codePointCount(0, execution.model().length())).isEqualTo(AiExecution.MAX_MODEL);
        assertThat(execution.promptVersion().codePointCount(0, execution.promptVersion().length()))
                .isEqualTo(AiExecution.MAX_PROMPT_VERSION);
        assertThat(execution.provider().codePointCount(0, execution.provider().length()))
                .isEqualTo(AiExecution.MAX_PROVIDER);
    }

    @ParameterizedTest(name = "intento {0}")
    @ValueSource(ints = { 0, 4, -1 })
    @DisplayName("El intento debe estar entre 1 y 3")
    void constructor_shouldRejectAttemptOutOfRange(int attempt) {
        assertThatThrownBy(() -> started(attempt)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("La marca vigente caduca al cumplirse el plazo y solo cuenta si está en curso")
    void isLive_shouldExpireAfterMaxAge() {
        AiExecution execution = started(1);
        Duration maxAge = Duration.ofMillis(24_500);

        assertThat(execution.isLive(CLOCK.instant().plusMillis(24_499), maxAge)).isTrue();
        assertThat(execution.isLive(CLOCK.instant().plusMillis(24_500), maxAge)).isFalse();
        assertThat(execution.failed(ErrorCode.LLM_UNAVAILABLE, CLOCK).isLive(CLOCK.instant(), maxAge)).isFalse();
    }

    @Test
    @DisplayName("Los estados de la ejecución se traducen a su valor de la base y de vuelta")
    void status_shouldRoundTrip() {
        for (AiExecutionStatus status : AiExecutionStatus.values()) {
            assertThat(AiExecutionStatus.fromCode(status.code())).isEqualTo(status);
        }
        assertThatThrownBy(() -> AiExecutionStatus.fromCode("X")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Los tipos de pregunta se traducen, y solo la conductual aplica STAR")
    void questionType_shouldRoundTrip_andOnlyBehavioralUsesStar() {
        for (QuestionType type : QuestionType.values()) {
            assertThat(QuestionType.fromCode(type.code())).isEqualTo(type);
            assertThat(type.startApplies()).isEqualTo(type == QuestionType.CONDUCTUAL);
        }
        assertThatThrownBy(() -> QuestionType.fromCode("tecnica")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "posición {0}")
    @ValueSource(ints = { 0, 6 })
    @DisplayName("La posición de una pregunta debe estar entre 1 y 5")
    void question_shouldRejectPositionOutOfRange(int position) {
        assertThatThrownBy(() -> new InterviewQuestion(UUID.randomUUID(), SESSION, position, QuestionType.APERTURA,
                "x", UUID.randomUUID(), CLOCK.instant())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El turno nuevo nace PENDING, sin versión, con la pregunta indicada")
    void turnOpen_shouldBePending() {
        UUID question = UUID.randomUUID();

        InterviewTurn turn = InterviewTurn.open(question, CLOCK);

        assertThat(turn.status()).isEqualTo(TurnStatus.PENDING);
        assertThat(turn.questionId()).isEqualTo(question);
        assertThat(turn.version()).isNull();
        assertThat(turn.createdAt()).isEqualTo(CLOCK.instant());
    }
}
