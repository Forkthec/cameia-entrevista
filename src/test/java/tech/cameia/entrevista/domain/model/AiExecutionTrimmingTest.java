package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Los textos de la ejecución se recortan al largo de su columna y los opcionales pueden faltar. */
class AiExecutionTrimmingTest {

    private static AiExecution of(String model, String prompt, String error, String provider) {
        return new AiExecution(UUID.randomUUID(), UUID.randomUUID(), AiOperation.PLAN, null, 1, UUID.randomUUID(),
                AiExecutionStatus.IN_PROGRESS, model, prompt, error, provider, null, null, Instant.now(), null);
    }

    @Test
    void constructor_shouldAcceptMissingOptionalTexts() {
        AiExecution execution = of("m", null, null, null);
        assertThat(execution.promptVersion()).isNull();
        assertThat(execution.errorCode()).isNull();
        assertThat(execution.provider()).isNull();
    }

    @Test
    void constructor_shouldCutEachTextToItsColumnLength() {
        AiExecution execution = of("m".repeat(200), "p".repeat(200), "e".repeat(200), "g".repeat(200));
        assertThat(execution.model()).hasSize(AiExecution.MAX_MODEL);
        assertThat(execution.promptVersion()).hasSize(AiExecution.MAX_PROMPT_VERSION);
        assertThat(execution.errorCode()).hasSize(AiExecution.MAX_ERROR_CODE);
        assertThat(execution.provider()).hasSize(AiExecution.MAX_PROVIDER);
    }

    @Test
    void evaluationStarted_shouldCarryTurn() {
        UUID turnId = UUID.randomUUID();

        AiExecution execution = AiExecution.evaluationStarted(UUID.randomUUID(), turnId, "m", "evaluacion.v1", "p",
                java.time.Clock.systemUTC());

        assertThat(execution.operation()).isEqualTo(AiOperation.EVALUATE);
        assertThat(execution.turnId()).isEqualTo(turnId);
        assertThat(execution.attempt()).isEqualTo(1);
        assertThat(execution.status()).isEqualTo(AiExecutionStatus.IN_PROGRESS);
        assertThat(execution.succeeded(1, 2, java.time.Clock.systemUTC()).turnId()).isEqualTo(turnId);
    }

    @Test
    void constructor_shouldRejectEvaluationWithoutTurn() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new AiExecution(UUID.randomUUID(), UUID.randomUUID(), AiOperation.EVALUATE,
                        null, 1, UUID.randomUUID(), AiExecutionStatus.IN_PROGRESS, "m", "p", null, null, null, null,
                        Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El turno es obligatorio al evaluar");
    }

    @Test
    void aiOperation_shouldRoundTripItsCode() {
        assertThat(AiOperation.fromCode("PLANIFICAR")).isEqualTo(AiOperation.PLAN);
        assertThat(AiOperation.fromCode("EVALUAR")).isEqualTo(AiOperation.EVALUATE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AiOperation.fromCode("otra"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
