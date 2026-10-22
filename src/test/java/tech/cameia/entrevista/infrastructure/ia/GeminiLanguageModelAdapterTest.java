package tech.cameia.entrevista.infrastructure.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;

/**
 * Prueba el adaptador de Gemini de punta a punta contra un servidor simulado del JDK: el
 * contexto arma el cliente, el modelo de chat de Spring AI y el adaptador reales, y solo la
 * dirección del proveedor apunta al servidor de la prueba. Ninguna prueba usa una llave real.
 */
@ExtendWith(OutputCaptureExtension.class)
class GeminiLanguageModelAdapterTest {

    private static final String PROMPT_PRIVADO = "Mi vacante es privada";

    private static final String RESPUESTA_PRIVADA = "respuesta-privada-del-modelo";

    private HttpServer servidor;

    /** Ruta y cuerpo de cada petición que llegó al servidor simulado. */
    private final List<String> rutas = new CopyOnWriteArrayList<>();

    private final List<String> cuerpos = new CopyOnWriteArrayList<>();

    /** Lo que el servidor simulado responde; cada prueba lo fija antes de llamar. */
    private volatile Respuesta respuesta;

    /** Retiene la respuesta del servidor en la prueba de tiempo agotado; se libera al cerrar. */
    private final CountDownLatch liberarServidor = new CountDownLatch(1);

    @BeforeEach
    void arrancarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            rutas.add(intercambio.getRequestURI().getPath());
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respuesta.escribir(intercambio);
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        liberarServidor.countDown();
        servidor.stop(0);
    }

    @Test
    @DisplayName("Una respuesta correcta devuelve el texto, los tokens y el modelo que informó Gemini")
    void generate_shouldReturnTextTokensAndModel_whenProviderAnswers() {
        respuesta = json(200, """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"OK"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2,"totalTokenCount":7},
                 "modelVersion":"gemini-3.7-flash-001"}
                """);

        contexto().run(arrancado -> {
            LanguageModelResponse resultado = arrancado.getBean(LanguageModelPort.class)
                    .generate(new LanguageModelPrompt("Responde OK.", 16));

            assertThat(resultado.text()).isEqualTo("OK");
            assertThat(resultado.inputTokens()).isEqualTo(5);
            assertThat(resultado.outputTokens()).isEqualTo(2);
            assertThat(resultado.model()).isEqualTo("gemini-3.7-flash-001");
        });

        assertThat(rutas).singleElement().asString().endsWith("/models/modelo-de-prueba:generateContent");
    }

    @Test
    @DisplayName("Cada llamada envía el texto, el tope de tokens, la temperatura y el nivel de pensamiento")
    void generate_shouldSendConfiguredOptions_whenCalled() {
        respuesta = json(200, respuestaCon("OK"));

        contexto().run(arrancado -> arrancado.getBean(LanguageModelPort.class)
                .generate(new LanguageModelPrompt("Responde OK.", 16)));

        assertThat(cuerpos).singleElement().asString()
                .contains("Responde OK.")
                .containsPattern("\"maxOutputTokens\"\\s*:\\s*16")
                .containsPattern("\"temperature\"\\s*:\\s*1(\\.0)?")
                .containsPattern("\"thinkingLevel\"\\s*:\\s*\"LOW\"");
    }

    @Test
    @DisplayName("Sin conteos de tokens y con la versión del modelo vacía, los tokens valen 0 y el modelo es el configurado")
    void generate_shouldUseZeroTokensAndConfiguredModel_whenProviderOmitsCounts() {
        respuesta = json(200, """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"OK"}]}}],
                 "usageMetadata":{},"modelVersion":""}
                """);

        contexto().run(arrancado -> {
            LanguageModelResponse resultado = arrancado.getBean(LanguageModelPort.class)
                    .generate(new LanguageModelPrompt("Responde OK.", 16));

            assertThat(resultado.inputTokens()).isZero();
            assertThat(resultado.outputTokens()).isZero();
            assertThat(resultado.model()).isEqualTo("modelo-de-prueba");
        });
    }

    @Test
    @DisplayName("La línea de medición lleva el resultado y la duración, sin el prompt ni la respuesta")
    void generate_shouldLogOnlyResultAndDuration_whenProviderAnswers(CapturedOutput salida) {
        respuesta = json(200, respuestaCon(RESPUESTA_PRIVADA));

        contexto().run(arrancado -> arrancado.getBean(LanguageModelPort.class)
                .generate(new LanguageModelPrompt(PROMPT_PRIVADO, 16)));

        assertThat(salida.getOut())
                .containsPattern("Llamada al modelo \\[resultado=ok, duracionMs=\\d+]")
                .doesNotContain(PROMPT_PRIVADO)
                .doesNotContain(RESPUESTA_PRIVADA)
                .doesNotContain("clave-falsa-de-prueba");
    }

    @Test
    @DisplayName("Si el modelo no responde dentro del tiempo de espera, lanza LlmUnavailableException tras una sola petición")
    void generate_shouldThrowUnavailableWithoutRetry_whenProviderTimesOut(CapturedOutput salida) {
        respuesta = intercambio -> {
            try {
                liberarServidor.await();
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
        };

        contexto("200ms").run(arrancado -> assertThatThrownBy(() -> arrancado.getBean(LanguageModelPort.class)
                .generate(new LanguageModelPrompt("Responde OK.", 16)))
                .isInstanceOf(LlmUnavailableException.class)
                // OkHttp corta la llamada con «timeout»; debajo queda el «Canceled» de la cancelación.
                .hasStackTraceContaining(InterruptedIOException.class.getName() + ": timeout"));

        assertThat(rutas).hasSize(1);
        assertThat(salida.getOut()).containsPattern("Llamada al modelo \\[resultado=LLM_UNAVAILABLE, duracionMs=\\d+]");
    }

    @Test
    @DisplayName("Un 429 del proveedor lanza LlmLimitReachedException tras una sola petición")
    void generate_shouldThrowLimitReached_whenProviderQuotaIsExhausted(CapturedOutput salida) {
        respuesta = json(429, """
                {"error":{"code":429,"message":"Resource has been exhausted","status":"RESOURCE_EXHAUSTED"}}
                """);

        assertThat(fallo()).isInstanceOf(LlmLimitReachedException.class);
        assertThat(rutas).hasSize(1);
        assertThat(salida.getOut()).contains("Llamada al modelo [resultado=LLM_LIMIT_REACHED");
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(ints = {400, 500, 503})
    @DisplayName("Otro estado de error del proveedor lanza LlmUnavailableException tras una sola petición")
    void generate_shouldThrowUnavailable_whenProviderFails(int estado) {
        respuesta = json(estado, """
                {"error":{"code":%d,"message":"fallo del proveedor","status":"UNAVAILABLE"}}
                """.formatted(estado));

        assertThat(fallo()).isInstanceOf(LlmUnavailableException.class);
        assertThat(rutas).hasSize(1);
    }

    @Test
    @DisplayName("Una conexión cortada sin respuesta lanza LlmUnavailableException")
    void generate_shouldThrowUnavailable_whenConnectionIsCut() {
        respuesta = HttpExchange::close;

        assertThat(fallo()).isInstanceOf(LlmUnavailableException.class);
        assertThat(rutas).hasSize(1);
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON lanza LlmUnavailableException")
    void generate_shouldThrowUnavailable_whenBodyIsNotJson() {
        respuesta = json(200, "esto no es json");

        assertThat(fallo()).isInstanceOf(LlmUnavailableException.class);
        assertThat(rutas).hasSize(1);
    }

    @ParameterizedTest(name = "texto [{0}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Una respuesta con el texto vacío o en blanco lanza LlmUnavailableException")
    void generate_shouldThrowUnavailable_whenTextIsBlank(String texto) {
        respuesta = json(200, respuestaCon(texto));

        assertThat(fallo()).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("Una respuesta sin candidatos lanza LlmUnavailableException")
    void generate_shouldThrowUnavailable_whenThereAreNoCandidates() {
        respuesta = json(200, """
                {"candidates":[],"usageMetadata":{"promptTokenCount":5},"modelVersion":"gemini-3.7-flash-001"}
                """);

        assertThat(fallo()).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("Una respuesta sin datos de uso ni versión del modelo lanza LlmUnavailableException")
    void generate_shouldThrowUnavailable_whenMetadataIsMissing() {
        respuesta = json(200, """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"OK"}]}}]}
                """);

        assertThat(fallo()).isInstanceOf(LlmUnavailableException.class);
    }

    /** Hace una llamada que debe fallar y devuelve la excepción que lanzó el adaptador. */
    private Throwable fallo() {
        Throwable[] lanzada = new Throwable[1];
        contexto().run(arrancado -> lanzada[0] = catchThrowable(() -> arrancado.getBean(LanguageModelPort.class)
                .generate(new LanguageModelPrompt("Responde OK.", 16))));
        assertThat(lanzada[0]).as("la llamada debía fallar").isNotNull();
        return lanzada[0];
    }

    private ApplicationContextRunner contexto() {
        return contexto("5s");
    }

    private ApplicationContextRunner contexto(String timeout) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class,
                        GoogleGenAiChatAutoConfiguration.class))
                .withUserConfiguration(AdapterWithItsConfiguration.class)
                .withPropertyValues(
                        "cameia.llm.api-key=clave-falsa-de-prueba",
                        "cameia.llm.model=modelo-de-prueba",
                        "cameia.llm.timeout=" + timeout,
                        "cameia.llm.temperature=1.0",
                        "cameia.llm.thinking-level=low",
                        "cameia.llm.base-url=http://localhost:" + servidor.getAddress().getPort());
    }

    /** Cuerpo mínimo que Spring AI acepta: un candidato, los datos de uso y la versión del modelo. */
    private static String respuestaCon(String texto) {
        return """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2,"totalTokenCount":7},
                 "modelVersion":"gemini-3.7-flash-001"}
                """.formatted(texto);
    }

    private static Respuesta json(int estado, String cuerpo) {
        return intercambio -> {
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        };
    }

    /** Lo que el servidor simulado hace con una petición. */
    @FunctionalInterface
    interface Respuesta {

        void escribir(HttpExchange intercambio) throws IOException;
    }

    /** El adaptador y la configuración real del cliente, sin el resto de la aplicación. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {LanguageModelProperties.class, GeminiLanguageModelAdapter.class})
    static class AdapterWithItsConfiguration {
    }
}
