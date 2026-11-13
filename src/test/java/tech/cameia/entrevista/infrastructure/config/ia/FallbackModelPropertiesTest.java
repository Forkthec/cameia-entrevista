package tech.cameia.entrevista.infrastructure.config.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Validación de la configuración del proveedor de respaldo al arrancar. */
class FallbackModelPropertiesTest {

    private static final String KEY = "clave-secreta-de-prueba";

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class)
            .withPropertyValues(
                    "cameia.llm.fallback.api-key=" + KEY,
                    "cameia.llm.fallback.model=qwen/qwen3.8-27b",
                    "cameia.llm.fallback.base-url=https://api.groq.com/openai");

    @Test
    @DisplayName("Con valores válidos el contexto arranca, enlaza las propiedades y hay llave")
    void bind_shouldStart_whenValid() {
        context.run(started -> {
            assertThat(started).hasNotFailed();
            FallbackModelProperties properties = started.getBean(FallbackModelProperties.class);
            assertThat(properties.model()).isEqualTo("qwen/qwen3.8-27b");
            assertThat(properties.baseUrl()).isEqualTo("https://api.groq.com/openai");
            assertThat(properties.hasKey()).isTrue();
        });
    }

    @ParameterizedTest(name = "llave [{0}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Una llave vacía o en blanco no impide arrancar y el respaldo queda sin llave")
    void bind_shouldStart_whenKeyIsBlank(String key) {
        context.withPropertyValues("cameia.llm.fallback.api-key=" + key).run(started -> {
            assertThat(started).hasNotFailed();
            assertThat(started.getBean(FallbackModelProperties.class).hasKey()).isFalse();
        });
    }

    @Test
    @DisplayName("Un modelo vacío impide arrancar")
    void bind_shouldFail_whenModelIsBlank() {
        context.withPropertyValues("cameia.llm.fallback.model=")
                .run(started -> assertThat(messages(started.getStartupFailure()))
                        .contains("cameia.llm.fallback.model (LLM_FALLBACK_MODEL) es obligatorio"));
    }

    @ParameterizedTest(name = "dirección [{0}]")
    @ValueSource(strings = {"ftp://x", "http://api.groq.com", "api.groq.com", "http://localhost.evil.com"})
    @DisplayName("Una dirección que no es https ni http://localhost impide arrancar")
    void bind_shouldFail_whenBaseUrlIsNotSecure(String url) {
        context.withPropertyValues("cameia.llm.fallback.base-url=" + url)
                .run(started -> assertThat(messages(started.getStartupFailure()))
                        .contains("cameia.llm.fallback.base-url (LLM_FALLBACK_BASE_URL) debe empezar por https://"));
    }

    @ParameterizedTest(name = "dirección [{0}]")
    @ValueSource(strings = {"http://localhost", "http://localhost:9000", "http://localhost/openai",
            "https://api.groq.com/openai"})
    @DisplayName("Las direcciones https y http://localhost arrancan")
    void bind_shouldStart_whenBaseUrlIsAllowed(String url) {
        context.withPropertyValues("cameia.llm.fallback.base-url=" + url)
                .run(started -> assertThat(started).hasNotFailed());
    }

    @ParameterizedTest(name = "dirección [{0}]")
    @ValueSource(strings = {"https://api.groq.com/openai/v1", "https://api.groq.com/openai/v1/",
            "http://localhost:9000/v1"})
    @DisplayName("Una dirección que ya termina en /v1 impide arrancar: el cliente agrega la versión")
    void bind_shouldFail_whenBaseUrlEndsWithVersion(String url) {
        context.withPropertyValues("cameia.llm.fallback.base-url=" + url)
                .run(started -> assertThat(messages(started.getStartupFailure()))
                        .contains("cameia.llm.fallback.base-url (LLM_FALLBACK_BASE_URL) no debe terminar en /v1"));
    }

    @Test
    @DisplayName("Una dirección vacía impide arrancar")
    void bind_shouldFail_whenBaseUrlIsBlank() {
        context.withPropertyValues("cameia.llm.fallback.base-url=")
                .run(started -> assertThat(messages(started.getStartupFailure()))
                        .contains("cameia.llm.fallback.base-url (LLM_FALLBACK_BASE_URL) es obligatoria"));
    }

    @Test
    @DisplayName("La representación de texto enmascara la llave")
    void toString_shouldMaskKey() {
        FallbackModelProperties properties = new FallbackModelProperties(KEY, "qwen/qwen3.8-27b",
                "https://api.groq.com/openai");

        assertThat(properties.toString())
                .contains("apiKey=****")
                .contains("model=qwen/qwen3.8-27b")
                .doesNotContain(KEY);
    }

    @ParameterizedTest(name = "dirección [{0}]")
    @ValueSource(strings = {"https://api.groq.com/openai", "https://api.groq.com/openai/", "https://api.groq.com/openai//"})
    @DisplayName("La dirección de la API agrega /v1 una sola vez, sin importar las barras finales")
    void apiBaseUrl_shouldAppendVersion(String url) {
        assertThat(new FallbackModelProperties(KEY, "m", url).apiBaseUrl())
                .isEqualTo("https://api.groq.com/openai/v1");
    }

    @Test
    @DisplayName("Una llave nula cuenta como ausente")
    void hasKey_shouldBeFalse_whenNull() {
        assertThat(new FallbackModelProperties(null, "m", "https://x").hasKey()).isFalse();
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

    /** Contexto mínimo que solo enlaza y valida las propiedades del respaldo. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FallbackModelProperties.class)
    static class PropertiesOnly {
    }
}
