package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Los textos de la ejecución se recortan al largo de su columna y los opcionales pueden faltar. */
class AiExecutionTrimmingTest {

    private static AiExecution of(String model, String prompt, String error, String provider) {
        return new AiExecution(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(),
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
}
