package tech.cameia.entrevista.infrastructure.ia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;

/** Sin llave del respaldo el servicio arranca, avisa una sola vez y el tercer intento usará el principal. */
@ExtendWith(OutputCaptureExtension.class)
class FallbackAvailabilityTest {

    private static final String WARNING = "Proveedor de respaldo sin llave: el tercer intento usará el principal";

    private ApplicationContextRunner contexto(String llave) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class))
                .withUserConfiguration(Scan.class)
                .withPropertyValues(
                        "cameia.llm.api-key=clave-falsa-de-prueba",
                        "cameia.llm.model=modelo-de-prueba",
                        "cameia.llm.timeout=5s",
                        "cameia.llm.evaluation-timeout=3s",
                        "cameia.llm.temperature=1.0",
                        "cameia.llm.thinking-level=low",
                        "cameia.llm.retry.first-wait=500ms",
                        "cameia.llm.retry.second-wait=1000ms",
                        "cameia.llm.fallback.api-key=" + llave,
                        "cameia.llm.fallback.model=qwen/qwen3.8-27b",
                        "cameia.llm.fallback.base-url=https://api.groq.com/openai");
    }

    @Test
    @DisplayName("Sin llave del respaldo el contexto arranca, el respaldo no está disponible y el aviso sale una vez")
    void context_shouldStart_withoutFallbackKey(CapturedOutput salida) {
        contexto("").run(arrancado -> {
            assertThat(arrancado).hasNotFailed();
            LanguageModelPort puerto = arrancado.getBean(LanguageModelPort.class);
            assertThat(puerto.isAvailable(AiProvider.PRIMARY)).isTrue();
            assertThat(puerto.isAvailable(AiProvider.FALLBACK)).isFalse();
        });

        assertThat(salida.getAll().split(WARNING, -1)).as("veces que sale el aviso").hasSize(2);
    }

    @Test
    @DisplayName("Con llave del respaldo el respaldo está disponible y no hay aviso")
    void context_shouldHaveFallback_withKey(CapturedOutput salida) {
        contexto("clave-de-prueba").run(arrancado -> {
            assertThat(arrancado).hasNotFailed();
            assertThat(arrancado.getBean(LanguageModelPort.class).isAvailable(AiProvider.FALLBACK)).isTrue();
        });

        assertThat(salida.getAll()).doesNotContain(WARNING);
    }

    /** Los adaptadores y la configuración real de los modelos, sin el resto de la aplicación. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {LanguageModelProperties.class, RoutingLanguageModelAdapter.class})
    static class Scan {
    }
}
