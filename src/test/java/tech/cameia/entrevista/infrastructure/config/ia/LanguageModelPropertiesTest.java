package tech.cameia.entrevista.infrastructure.config.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

/**
 * Prueba que la configuración del modelo se valida al arrancar y que la llave no aparece en el
 * log ni en los mensajes de error.
 *
 * <p>Cada caso arranca un contexto mínimo que solo habilita {@link LanguageModelProperties}, sin
 * los archivos de configuración del repositorio: así el resultado depende solo de los valores
 * de la prueba y nunca de un {@code .env} local.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class LanguageModelPropertiesTest {

    private static final String LLAVE_SECRETA = "clave-secreta-de-prueba";

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class)
            .withPropertyValues(
                    "cameia.llm.api-key=clave-falsa-de-prueba",
                    "cameia.llm.model=modelo-de-prueba",
                    "cameia.llm.timeout=30s",
                    "cameia.llm.temperature=1.0",
                    "cameia.llm.thinking-level=low");

    @Test
    @DisplayName("Con todos los valores válidos el contexto arranca y enlaza cada propiedad")
    void bind_shouldStart_whenAllValuesAreValid() {
        context.run(arrancado -> {
            assertThat(arrancado).hasNotFailed();
            LanguageModelProperties propiedades = arrancado.getBean(LanguageModelProperties.class);
            assertThat(propiedades.apiKey()).isEqualTo("clave-falsa-de-prueba");
            assertThat(propiedades.model()).isEqualTo("modelo-de-prueba");
            assertThat(propiedades.timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(propiedades.temperature()).isEqualTo(1.0);
            assertThat(propiedades.thinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.LOW);
        });
    }

    @ParameterizedTest(name = "llave [{0}]")
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("Una llave vacía o en blanco impide arrancar y el error nombra la propiedad y la variable")
    void bind_shouldFail_whenApiKeyIsBlank(String llave) {
        context.withPropertyValues("cameia.llm.api-key=" + llave)
                .run(arrancado -> assertThat(messages(arrancado.getStartupFailure()))
                        .contains("cameia.llm.api-key (GOOGLE_GENAI_API_KEY) es obligatoria"));
    }

    @Test
    @DisplayName("Sin la propiedad de la llave el servicio no arranca")
    void bind_shouldFail_whenApiKeyIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesOnly.class)
                .withPropertyValues(
                        "cameia.llm.model=modelo-de-prueba",
                        "cameia.llm.timeout=30s",
                        "cameia.llm.temperature=1.0",
                        "cameia.llm.thinking-level=low")
                .run(arrancado -> assertThat(messages(arrancado.getStartupFailure()))
                        .contains("cameia.llm.api-key (GOOGLE_GENAI_API_KEY) es obligatoria"));
    }

    @ParameterizedTest(name = "modelo [{0}]")
    @ValueSource(strings = {"", "  "})
    @DisplayName("Un modelo vacío o en blanco impide arrancar y el error nombra la propiedad y la variable")
    void bind_shouldFail_whenModelIsBlank(String model) {
        context.withPropertyValues("cameia.llm.model=" + model)
                .run(arrancado -> assertThat(messages(arrancado.getStartupFailure()))
                        .contains("cameia.llm.model (LLM_MODEL) es obligatorio"));
    }

    @Test
    @DisplayName("Sin la propiedad del modelo el servicio no arranca")
    void bind_shouldFail_whenModelIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesOnly.class)
                .withPropertyValues(
                        "cameia.llm.api-key=clave-falsa-de-prueba",
                        "cameia.llm.timeout=30s",
                        "cameia.llm.temperature=1.0",
                        "cameia.llm.thinking-level=low")
                .run(arrancado -> assertThat(messages(arrancado.getStartupFailure()))
                        .contains("cameia.llm.model (LLM_MODEL) es obligatorio"));
    }

    @ParameterizedTest(name = "timeout [{0}]")
    @ValueSource(strings = {"0s", "-1s"})
    @DisplayName("Un timeout cero o negativo impide arrancar")
    void bind_shouldFail_whenTimeoutIsNotPositive(String timeout) {
        context.withPropertyValues("cameia.llm.timeout=" + timeout)
                .run(arrancado -> assertThat(messages(arrancado.getStartupFailure()))
                        .contains("cameia.llm.timeout (LLM_TIMEOUT) debe ser mayor que cero"));
    }

    @Test
    @DisplayName("Un timeout que no es una duración impide arrancar")
    void bind_shouldFail_whenTimeoutIsNotADuration() {
        context.withPropertyValues("cameia.llm.timeout=abc")
                .run(arrancado -> assertThat(messages(arrancado.getStartupFailure()))
                        .contains("cameia.llm.timeout"));
    }

    @ParameterizedTest(name = "temperatura [{0}]")
    @ValueSource(strings = {"-0.1", "2.1"})
    @DisplayName("Una temperatura fuera de 0.0 a 2.0 impide arrancar")
    void bind_shouldFail_whenTemperatureIsOutOfRange(String temperatura) {
        context.withPropertyValues("cameia.llm.temperature=" + temperatura)
                .run(arrancado -> assertThat(messages(arrancado.getStartupFailure()))
                        .contains("cameia.llm.temperature debe estar entre 0.0 y 2.0"));
    }

    @Test
    @DisplayName("Un nivel de pensamiento que no existe impide arrancar")
    void bind_shouldFail_whenThinkingLevelIsUnknown() {
        context.withPropertyValues("cameia.llm.thinking-level=ultra")
                .run(arrancado -> assertThat(messages(arrancado.getStartupFailure()))
                        .contains("cameia.llm.thinking-level"));
    }

    @Test
    @DisplayName("Un arranque fallido por otra propiedad no muestra la llave ni en el error ni en el log")
    void run_shouldNotRevealApiKey_whenStartupFailsOnTimeout(CapturedOutput salida) {
        SpringApplicationBuilder aplicacion = new SpringApplicationBuilder(PropertiesOnly.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF);

        // Sin archivos de configuración: solo cuentan los argumentos de la prueba.
        assertThatThrownBy(() -> aplicacion.run(
                "--spring.config.name=cm312-sin-archivos",
                "--cameia.llm.api-key=" + LLAVE_SECRETA,
                "--cameia.llm.model=modelo-de-prueba",
                "--cameia.llm.timeout=abc",
                "--cameia.llm.temperature=1.0",
                "--cameia.llm.thinking-level=low"))
                .satisfies(error -> assertThat(messages(error)).doesNotContain(LLAVE_SECRETA));

        assertThat(salida.getAll())
                .contains("cameia.llm.timeout")
                .doesNotContain(LLAVE_SECRETA);
    }

    @Test
    @DisplayName("Un arranque fallido por validación no muestra la llave ni en el error ni en el log")
    void run_shouldNotRevealApiKey_whenValidationFailsOnModel(CapturedOutput salida) {
        SpringApplicationBuilder aplicacion = new SpringApplicationBuilder(PropertiesOnly.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF);

        assertThatThrownBy(() -> aplicacion.run(
                "--spring.config.name=cm312-sin-archivos",
                "--cameia.llm.api-key=" + LLAVE_SECRETA,
                "--cameia.llm.model=",
                "--cameia.llm.timeout=30s",
                "--cameia.llm.temperature=1.0",
                "--cameia.llm.thinking-level=low"))
                .satisfies(error -> assertThat(messages(error)).doesNotContain(LLAVE_SECRETA));

        assertThat(salida.getAll())
                .contains("cameia.llm.model (LLM_MODEL) es obligatorio")
                .doesNotContain(LLAVE_SECRETA);
    }

    @Test
    @DisplayName("La representación de texto enmascara la llave")
    void toString_shouldMaskApiKey_whenPropertiesArePrinted() {
        LanguageModelProperties propiedades = new LanguageModelProperties(LLAVE_SECRETA, "modelo-de-prueba",
                Duration.ofSeconds(30), 1.0, GoogleGenAiThinkingLevel.LOW);

        assertThat(propiedades.toString())
                .contains("apiKey=****")
                .contains("model=modelo-de-prueba")
                .doesNotContain(LLAVE_SECRETA);
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

    /** Contexto mínimo que solo enlaza y valida las propiedades del modelo. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LanguageModelProperties.class)
    static class PropertiesOnly {
    }
}
