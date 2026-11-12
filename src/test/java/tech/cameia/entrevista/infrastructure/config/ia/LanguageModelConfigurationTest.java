package tech.cameia.entrevista.infrastructure.config.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.retry.RetryTemplate;

/**
 * Prueba que la configuración propia arma un modelo de chat por proveedor y por operación, sin publicar ninguno
 * como bean {@code ChatModel}, y que apaga los reintentos de Spring AI.
 */
class LanguageModelConfigurationTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SpringAiRetryAutoConfiguration.class,
                    ToolCallingAutoConfiguration.class))
            .withUserConfiguration(LanguageModelConfiguration.class)
            .withPropertyValues(
                    "cameia.llm.api-key=clave-falsa-de-prueba",
                    "cameia.llm.model=modelo-de-prueba",
                    "cameia.llm.timeout=30s",
                    "cameia.llm.evaluation-timeout=3s",
                    "cameia.llm.temperature=1.0",
                    "cameia.llm.thinking-level=low",
                    "cameia.llm.retry.first-wait=500ms",
                    "cameia.llm.retry.second-wait=1000ms",
                    "cameia.llm.fallback.api-key=clave-de-prueba",
                    "cameia.llm.fallback.model=qwen/qwen3.8-27b",
                    "cameia.llm.fallback.base-url=https://api.groq.com/openai");

    @Test
    @DisplayName("Hay un modelo de chat por proveedor y por operación y ninguno es un bean ChatModel")
    void context_shouldHaveOneChatModelPerProviderAndOperation() {
        contexto.run(arrancado -> {
            assertThat(arrancado).hasNotFailed();
            GeminiChatModels gemini = arrancado.getBean(GeminiChatModels.class);
            GroqChatModels groq = arrancado.getBean(GroqChatModels.class);
            assertThat(gemini.planning()).isNotNull().isNotSameAs(gemini.evaluation());
            assertThat(gemini.evaluation()).isNotNull();
            assertThat(groq.available()).isTrue();
            assertThat(groq.planning()).isNotNull().isNotSameAs(groq.evaluation());
            assertThat(groq.evaluation()).isNotNull();
            assertThat(arrancado.getBeansOfType(ChatModel.class)).isEmpty();
        });
    }

    @Test
    @DisplayName("La única plantilla de reintentos es la propia y no reintenta ningún fallo")
    void languageModelRetryTemplate_shouldNeverRetry_whenAutoConfigurationRuns() {
        contexto.run(arrancado -> {
            assertThat(arrancado.getBeansOfType(RetryTemplate.class)).containsOnlyKeys("languageModelRetryTemplate");
            RetryTemplate plantilla = arrancado.getBean(RetryTemplate.class);
            assertThat(plantilla.getRetryPolicy().shouldRetry(new IOException("conexión cortada"))).isFalse();
            assertThat(plantilla.getRetryPolicy().shouldRetry(new RuntimeException("503"))).isFalse();
        });
    }

    @Test
    @DisplayName("Con una dirección del proveedor para pruebas el contexto también arranca")
    void geminiChatModels_shouldStart_whenBaseUrlIsSet() {
        contexto.withPropertyValues("cameia.llm.base-url=http://localhost:1")
                .run(arrancado -> assertThat(arrancado).hasNotFailed().hasSingleBean(GeminiChatModels.class));
    }

    @Test
    @DisplayName("Sin llave del respaldo el contexto arranca y el respaldo queda vacío")
    void groqChatModels_shouldBeAbsent_whenNoKey() {
        contexto.withPropertyValues("cameia.llm.fallback.api-key=")
                .run(arrancado -> {
                    assertThat(arrancado).hasNotFailed();
                    assertThat(arrancado.getBean(GroqChatModels.class).available()).isFalse();
                });
    }
}
