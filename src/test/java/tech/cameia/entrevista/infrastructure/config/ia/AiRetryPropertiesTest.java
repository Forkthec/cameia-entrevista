package tech.cameia.entrevista.infrastructure.config.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Validación de las esperas entre intentos al arrancar. */
class AiRetryPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    @DisplayName("Con 500 ms y 1000 ms el contexto arranca y enlaza las dos esperas")
    void bind_shouldStart_whenWaitsAreValid() {
        context.withPropertyValues("cameia.llm.retry.first-wait=500ms", "cameia.llm.retry.second-wait=1000ms")
                .run(started -> {
                    assertThat(started).hasNotFailed();
                    AiRetryProperties properties = started.getBean(AiRetryProperties.class);
                    assertThat(properties.firstWait()).isEqualTo(Duration.ofMillis(500));
                    assertThat(properties.secondWait()).isEqualTo(Duration.ofMillis(1000));
                });
    }

    @Test
    @DisplayName("Una primera espera de 0 ms es válida")
    void bind_shouldStart_whenFirstWaitIsZero() {
        context.withPropertyValues("cameia.llm.retry.first-wait=0ms", "cameia.llm.retry.second-wait=0ms")
                .run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    @DisplayName("Una primera espera negativa impide arrancar")
    void bind_shouldFail_whenFirstWaitIsNegative() {
        context.withPropertyValues("cameia.llm.retry.first-wait=-1ms", "cameia.llm.retry.second-wait=1000ms")
                .run(started -> assertThat(messages(started.getStartupFailure()))
                        .contains("cameia.llm.retry.first-wait (LLM_RETRY_FIRST_WAIT) no puede ser negativo"));
    }

    @Test
    @DisplayName("Una segunda espera menor que la primera impide arrancar")
    void bind_shouldFail_whenSecondWaitIsBeforeFirst() {
        context.withPropertyValues("cameia.llm.retry.first-wait=500ms", "cameia.llm.retry.second-wait=400ms")
                .run(started -> assertThat(messages(started.getStartupFailure()))
                        .contains("cameia.llm.retry.second-wait (LLM_RETRY_SECOND_WAIT) no puede ser menor que la "
                                + "primera espera"));
    }

    @Test
    @DisplayName("Sin la primera o la segunda espera el servicio no arranca")
    void bind_shouldFail_whenAWaitIsMissing() {
        context.withPropertyValues("cameia.llm.retry.second-wait=1000ms")
                .run(started -> assertThat(messages(started.getStartupFailure()))
                        .contains("cameia.llm.retry.first-wait (LLM_RETRY_FIRST_WAIT) es obligatorio"));
        context.withPropertyValues("cameia.llm.retry.first-wait=500ms")
                .run(started -> assertThat(messages(started.getStartupFailure()))
                        .contains("cameia.llm.retry.second-wait (LLM_RETRY_SECOND_WAIT) es obligatorio"));
    }

    /** Mensajes de toda la cadena de causas, para buscar en ellos sin depender del nivel. */
    private static String messages(Throwable error) {
        assertThat(error).as("el arranque debía fallar").isNotNull();
        List<String> texts = new ArrayList<>();
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            texts.add(String.valueOf(actual.getMessage()));
        }
        return String.join("\n", texts);
    }

    /** Contexto mínimo que solo enlaza y valida las esperas. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiRetryProperties.class)
    static class PropertiesOnly {
    }
}
