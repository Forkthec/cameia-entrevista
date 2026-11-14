package tech.cameia.entrevista.infrastructure.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;

/**
 * Prueba que cada operación respeta su propio tiempo de espera: la evaluación el de
 * {@code cameia.llm.evaluation-timeout} y la planificación el de {@code cameia.llm.timeout}. El servidor
 * simulado del JDK tarda lo que cada prueba le pide antes de responder.
 */
class GeminiLanguageModelAdapterTimeoutTest {

    private static final String RESPUESTA = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"OK"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2,"totalTokenCount":7},
             "modelVersion":"gemini-3.7-flash-001"}
            """;

    private HttpServer servidor;

    /** Cuánto tarda el servidor simulado en responder. */
    private volatile long demoraMs;

    @BeforeEach
    void arrancarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            intercambio.getRequestBody().readAllBytes();
            try {
                Thread.sleep(demoraMs);
                byte[] bytes = RESPUESTA.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, bytes.length);
                intercambio.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException cortada) {
                // El cliente ya se fue por su tiempo de espera: es lo que la prueba provoca.
            } finally {
                intercambio.close();
            }
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("Una evaluación que responde a los 2900 ms con un tope de 3000 ms se acepta en un intento")
    void generate_shouldAccept_whenAnswerBeforeTimeout() {
        demoraMs = 2900;

        LanguageModelResponse respuesta = llamar(evaluacion(), "20s", "3000ms");

        assertThat(respuesta.text()).isEqualTo("OK");
    }

    @Test
    @DisplayName("Una evaluación que responde a los 5000 ms con un tope de 3000 ms falla sin esperar la respuesta")
    void generate_shouldFailUnavailable_whenTimeout() {
        demoraMs = 5000;
        long[] duracionMs = new long[1];

        Throwable fallo = catchThrowable(() -> llamar(evaluacion(), "20s", "3000ms", duracionMs));

        assertThat(fallo).isInstanceOf(LlmUnavailableException.class);
        // Se mide solo la llamada al modelo, sin el arranque del contexto (en frío tarda segundos); el margen de
        // 1,5 s sobre el tope absorbe la lentitud de una máquina cargada y sigue distinguiendo esperar los 5000 ms.
        assertThat(duracionMs[0]).as("la llamada debe volver al cumplirse el tope, no a los 5000 ms")
                .isLessThan(4500);
    }

    @Test
    @DisplayName("La planificación usa el tope de planificación y no el de evaluación")
    void generate_shouldUsePlanningTimeout_forPlan() {
        demoraMs = 1900;

        // Con 1 s de tope de evaluación y 2 s de planificación, a los 1900 ms solo la planificación responde a tiempo.
        assertThat(llamar(planificacion(), "2000ms", "1000ms").text()).isEqualTo("OK");
        assertThat(catchThrowable(() -> llamar(evaluacion(), "2000ms", "1000ms")))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("Una planificación que responde a los 19.900 ms con un tope de 20.000 ms se acepta en un intento")
    void generate_shouldAcceptPlanning_whenAnswerAt19900ms() {
        demoraMs = 19_900;

        LanguageModelResponse respuesta = llamar(planificacion(), "20000ms", "3000ms");

        assertThat(respuesta.text()).isEqualTo("OK");
    }

    private static LanguageModelPrompt evaluacion() {
        return new LanguageModelPrompt("Evalúa.", 16, AiOperation.EVALUATE);
    }

    private static LanguageModelPrompt planificacion() {
        return new LanguageModelPrompt("Planifica.", 16, AiOperation.PLAN);
    }

    private LanguageModelResponse llamar(LanguageModelPrompt prompt, String planningTimeout,
            String evaluationTimeout) {
        return llamar(prompt, planningTimeout, evaluationTimeout, new long[1]);
    }

    /** Igual que la anterior; deja en {@code duracionMs[0]} lo que tardó solo la llamada al modelo. */
    private LanguageModelResponse llamar(LanguageModelPrompt prompt, String planningTimeout,
            String evaluationTimeout, long[] duracionMs) {
        LanguageModelResponse[] resultado = new LanguageModelResponse[1];
        Throwable[] fallo = new Throwable[1];
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class))
                .withUserConfiguration(AdapterWithItsConfiguration.class)
                .withPropertyValues(
                        "cameia.llm.api-key=clave-falsa-de-prueba",
                        "cameia.llm.model=modelo-de-prueba",
                        "cameia.llm.timeout=" + planningTimeout,
                        "cameia.llm.evaluation-timeout=" + evaluationTimeout,
                        "cameia.llm.temperature=1.0",
                        "cameia.llm.thinking-level=low",
                        "cameia.llm.retry.first-wait=0ms",
                        "cameia.llm.retry.second-wait=0ms",
                        "cameia.llm.fallback.api-key=",
                        "cameia.llm.fallback.model=modelo-de-respaldo",
                        "cameia.llm.fallback.base-url=https://respaldo.invalido",
                        "cameia.llm.base-url=http://localhost:" + servidor.getAddress().getPort())
                .run(arrancado -> {
                    GeminiLanguageModelAdapter adaptador = arrancado.getBean(GeminiLanguageModelAdapter.class);
                    long inicio = System.nanoTime();
                    try {
                        resultado[0] = adaptador.generate(prompt);
                    } catch (RuntimeException lanzada) {
                        fallo[0] = lanzada;
                    } finally {
                        duracionMs[0] = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
                    }
                });
        if (fallo[0] instanceof RuntimeException lanzada) {
            throw lanzada;
        }
        return resultado[0];
    }

    /** Los adaptadores y la configuración real de los modelos, sin el resto de la aplicación. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {LanguageModelProperties.class, GeminiLanguageModelAdapter.class})
    static class AdapterWithItsConfiguration {
    }
}
