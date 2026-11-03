package tech.cameia.entrevista.infrastructure.config.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.google.genai.Client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.retry.RetryTemplate;

/**
 * Prueba que la configuración propia reemplaza el cliente y los reintentos que traen por defecto
 * las autoconfiguraciones de Spring AI, y que el modelo de chat se arma sobre ellos.
 */
class LanguageModelConfigurationTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SpringAiRetryAutoConfiguration.class,
                    ToolCallingAutoConfiguration.class,
                    GoogleGenAiChatAutoConfiguration.class))
            .withUserConfiguration(LanguageModelConfiguration.class)
            .withPropertyValues(
                    "cameia.llm.api-key=clave-falsa-de-prueba",
                    "cameia.llm.model=modelo-de-prueba",
                    "cameia.llm.timeout=30s",
                    "cameia.llm.temperature=1.0",
                    "cameia.llm.thinking-level=low");

    @Test
    @DisplayName("El único cliente de Gemini es el propio: la autoconfiguración no crea el suyo")
    void languageModelClient_shouldBeTheOnlyClient_whenAutoConfigurationRuns() {
        contexto.run(arrancado -> {
            assertThat(arrancado).hasNotFailed();
            assertThat(arrancado.getBeansOfType(Client.class)).containsOnlyKeys("languageModelClient");
            assertThat(arrancado).hasSingleBean(ChatModel.class);
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
    void languageModelClient_shouldStart_whenBaseUrlIsSet() {
        contexto.withPropertyValues("cameia.llm.base-url=http://localhost:1")
                .run(arrancado -> assertThat(arrancado).hasNotFailed().hasSingleBean(Client.class));
    }
}
