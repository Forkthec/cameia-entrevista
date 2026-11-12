package tech.cameia.entrevista.infrastructure.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;

/**
 * Prueba el adaptador del respaldo de punta a punta contra un servidor simulado del JDK: el contexto arma los
 * modelos de chat y el adaptador reales, y solo la dirección del proveedor apunta al servidor de la prueba.
 * Ninguna prueba usa una llave real.
 */
@ExtendWith(OutputCaptureExtension.class)
class GroqLanguageModelAdapterTest {

    private static final String KEY = "clave-de-prueba-secreta";

    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}},"
            + "\"required\":[\"a\"],\"additionalProperties\":false}";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private HttpServer servidor;

    /** Cuerpo y encabezado de autorización de cada petición que llegó al servidor simulado. */
    private final List<String> cuerpos = new CopyOnWriteArrayList<>();

    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();

    private final List<String> rutas = new CopyOnWriteArrayList<>();

    /** Lo que el servidor simulado responde; cada prueba lo fija antes de llamar. */
    private volatile Consumer<HttpExchange> respuesta;

    @BeforeEach
    void arrancarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            rutas.add(intercambio.getRequestURI().getPath());
            autorizaciones.add(intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respuesta.accept(intercambio);
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("Envía el modelo, el razonamiento apagado, el esquema estricto, el texto y la llave del respaldo")
    void generate_shouldSendSchemaAndReasoningNone() {
        respuesta = json(200, completion("{\"a\":\"x\"}"));

        ejecutar(adaptador -> adaptador.generate(
                new LanguageModelPrompt("Evalúa esta respuesta.", 321, SCHEMA, AiOperation.EVALUATE)));

        assertThat(rutas).containsExactly("/v1/chat/completions");
        assertThat(autorizaciones).containsExactly("Bearer " + KEY);
        JsonNode cuerpo = leer(cuerpos.get(0));
        assertThat(cuerpo.get("model").asString()).isEqualTo("qwen/qwen3.8-27b");
        assertThat(cuerpo.get("reasoning_effort").asString()).isEqualTo("none");
        assertThat(cuerpo.get("response_format").get("type").asString()).isEqualTo("json_schema");
        assertThat(cuerpo.get("response_format").get("json_schema").get("strict").asBoolean()).isTrue();
        assertThat(cuerpo.get("response_format").get("json_schema").get("schema").get("required").get(0).asString())
                .isEqualTo("a");
        assertThat(cuerpo.get("messages").get(0).get("content").asString()).isEqualTo("Evalúa esta respuesta.");
        assertThat(cuerpo.toString()).contains("321");
    }

    @Test
    @DisplayName("Sin esquema no pide formato de respuesta")
    void generate_shouldOmitResponseFormat_whenNoSchema() {
        respuesta = json(200, completion("hola"));

        ejecutar(adaptador -> adaptador.generate(new LanguageModelPrompt("Planifica.", 64, AiOperation.PLAN)));

        assertThat(leer(cuerpos.get(0)).has("response_format")).isFalse();
    }

    @Test
    @DisplayName("Una respuesta correcta devuelve el texto, los tokens y el modelo")
    void generate_shouldReturnText() {
        respuesta = json(200, completion("OK"));

        LanguageModelResponse resultado = ejecutar(adaptador -> adaptador.generate(
                new LanguageModelPrompt("Responde OK.", 16, AiOperation.PLAN)));

        assertThat(resultado.text()).isEqualTo("OK");
        assertThat(resultado.inputTokens()).isEqualTo(5);
        assertThat(resultado.outputTokens()).isEqualTo(2);
        assertThat(resultado.model()).isEqualTo("qwen/qwen3.8-27b");
    }

    @Test
    @DisplayName("Una llamada sin llave configurada es IA no disponible y no sale a la red")
    void generate_shouldThrowUnavailable_whenNoKey() {
        contexto("").run(arrancado -> assertThat(catchThrowable(() -> arrancado
                .getBean(GroqLanguageModelAdapter.class)
                .generate(new LanguageModelPrompt("Responde OK.", 16, AiOperation.PLAN))))
                .isInstanceOf(LlmUnavailableException.class));
        assertThat(cuerpos).isEmpty();
    }

    @Test
    @DisplayName("Un 429 del proveedor es límite de uso y no se reintenta dentro del adaptador")
    void generate_shouldThrowLimit_when429() {
        respuesta = json(429, "{\"error\":{\"message\":\"rate limit\",\"type\":\"rate_limit_exceeded\"}}");

        Throwable fallo = fallar();

        assertThat(fallo).isInstanceOf(LlmLimitReachedException.class);
        assertThat(rutas).hasSize(1);
    }

    @Test
    @DisplayName("Un 500 del proveedor es IA no disponible y no se reintenta dentro del adaptador")
    void generate_shouldThrowUnavailable_when500() {
        respuesta = json(500, "{\"error\":{\"message\":\"boom\"}}");

        assertThat(fallar()).isInstanceOf(LlmUnavailableException.class);
        assertThat(rutas).hasSize(1);
    }

    @Test
    @DisplayName("Una respuesta 200 sin opciones es IA no disponible")
    void generate_shouldThrowUnavailable_whenEmptyChoices() {
        respuesta = json(200, "{\"id\":\"x\",\"object\":\"chat.completion\",\"created\":1,"
                + "\"model\":\"qwen/qwen3.8-27b\",\"choices\":[]}");

        assertThat(fallar()).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("Una respuesta con el texto en blanco es IA no disponible")
    void generate_shouldThrowUnavailable_whenBlankText() {
        respuesta = json(200, completion("   "));

        assertThat(fallar()).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("Un 401 por llave inválida es IA no disponible")
    void generate_shouldThrowUnavailable_when401() {
        respuesta = json(401, "{\"error\":{\"message\":\"Invalid API Key\"}}");

        assertThat(fallar()).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("Un cuerpo ilegible o una conexión cortada también son IA no disponible")
    void generate_shouldTranslateAnyFailure() {
        respuesta = json(200, "esto no es json");
        assertThat(fallar()).isInstanceOf(LlmUnavailableException.class);

        respuesta = intercambio -> intercambio.close();
        assertThat(fallar()).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("Los registros nunca contienen la llave, el texto enviado ni el recibido")
    void logs_shouldNotContainKey(CapturedOutput salida) {
        respuesta = json(401, "{\"error\":{\"message\":\"Invalid API Key " + KEY + "\"}}");
        fallar();
        respuesta = json(200, completion("respuesta-privada"));
        ejecutar(adaptador -> adaptador.generate(new LanguageModelPrompt("prompt-privado", 16, AiOperation.PLAN)));

        assertThat(salida.getAll())
                .contains("Llamada al modelo [proveedor=groq, operacion=PLANIFICAR, resultado=LLM_UNAVAILABLE")
                .contains("Llamada al modelo [proveedor=groq, operacion=PLANIFICAR, resultado=ok")
                .doesNotContain(KEY)
                .doesNotContain("prompt-privado")
                .doesNotContain("respuesta-privada");
    }

    @Test
    @DisplayName("Sin llave el respaldo no está disponible")
    void isAvailable_shouldBeFalse_whenNoKey() {
        contexto("").run(arrancado -> assertThat(arrancado.getBean(GroqLanguageModelAdapter.class).isAvailable())
                .isFalse());
    }

    /** Hace una llamada que debe fallar y devuelve su excepción. */
    private Throwable fallar() {
        Throwable[] lanzada = new Throwable[1];
        contexto(KEY).run(arrancado -> lanzada[0] = catchThrowable(() -> arrancado
                .getBean(GroqLanguageModelAdapter.class)
                .generate(new LanguageModelPrompt("Responde OK.", 16, AiOperation.PLAN))));
        assertThat(lanzada[0]).as("la llamada debía fallar").isNotNull();
        return lanzada[0];
    }

    private LanguageModelResponse ejecutar(java.util.function.Function<GroqLanguageModelAdapter, LanguageModelResponse> llamada) {
        LanguageModelResponse[] resultado = new LanguageModelResponse[1];
        contexto(KEY).run(arrancado -> resultado[0] = llamada.apply(arrancado.getBean(GroqLanguageModelAdapter.class)));
        return resultado[0];
    }

    private ApplicationContextRunner contexto(String llave) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class))
                .withUserConfiguration(AdapterWithItsConfiguration.class)
                .withPropertyValues(
                        "cameia.llm.api-key=clave-falsa-de-prueba",
                        "cameia.llm.model=modelo-de-prueba",
                        "cameia.llm.timeout=5s",
                        "cameia.llm.evaluation-timeout=5s",
                        "cameia.llm.temperature=1.0",
                        "cameia.llm.thinking-level=low",
                        "cameia.llm.retry.first-wait=0ms",
                        "cameia.llm.retry.second-wait=0ms",
                        "cameia.llm.fallback.api-key=" + llave,
                        "cameia.llm.fallback.model=qwen/qwen3.8-27b",
                        "cameia.llm.fallback.base-url=http://localhost:" + servidor.getAddress().getPort());
    }

    private static JsonNode leer(String cuerpo) {
        return MAPPER.readTree(cuerpo);
    }

    /** Respuesta de chat completions con un solo mensaje y los tokens del ejemplo. */
    private static String completion(String texto) {
        return """
                {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"qwen/qwen3.8-27b",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}
                """.formatted(texto.replace("\"", "\\\""));
    }

    private static Consumer<HttpExchange> json(int estado, String cuerpo) {
        return intercambio -> {
            try {
                byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(estado, bytes.length);
                intercambio.getResponseBody().write(bytes);
                intercambio.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    /** Los dos adaptadores y la configuración real de los modelos, sin el resto de la aplicación. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {LanguageModelProperties.class, GroqLanguageModelAdapter.class})
    static class AdapterWithItsConfiguration {
    }
}
