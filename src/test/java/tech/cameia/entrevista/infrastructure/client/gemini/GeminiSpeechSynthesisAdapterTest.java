package tech.cameia.entrevista.infrastructure.client.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.SpeechTimeoutException;
import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.SpeechAudio;
import tech.cameia.entrevista.domain.model.SpeechDirection;

/**
 * El adaptador de Gemini TTS contra un servidor simulado del JDK: cuerpo de la petición, respaldo ante 429, 404 y 5xx,
 * audio inválido (CA-5.15.21) y tiempo máximo exacto (CA-5.15.22). Ninguna prueba llama al proveedor real.
 */
class GeminiSpeechSynthesisAdapterTest {

    private static final String PRIMARY = "gemini-3.8-flash-tts";
    private static final String FALLBACK = "gemini-3.1-flash-tts-preview";
    private static final SpeechDirection DIRECTION = new SpeechDirection(
            "<sigh> ¿Qué métrica usó? <short pause> Sea concreto.", "confident", true);
    private static final byte[] WAV = wav(4800);

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final Deque<Reply> replies = new ArrayDeque<>();
    private HttpServer server;

    /** Respuesta programada del servidor simulado. */
    private record Reply(int status, String body, long delayMs) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/v1beta/interactions", exchange -> {
            keys.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            bodies.add(json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply reply;
            synchronized (replies) {
                reply = replies.isEmpty() ? ok(WAV) : replies.poll();
            }
            sleep(reply.delayMs());
            respond(exchange, reply.status(), reply.body());
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiSpeechSynthesisAdapter adapter(String key, String fallback, Duration timeout) {
        return adapter(key, fallback, timeout, URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    private GeminiSpeechSynthesisAdapter adapter(String key, String fallback, Duration timeout, URI base) {
        return new GeminiSpeechSynthesisAdapter(key, base, PRIMARY, fallback,
                Map.of(InterviewLanguage.SPANISH_COLOMBIA, "Kore", InterviewLanguage.ENGLISH, "Puck"), timeout,
                HttpClient.newHttpClient(), json);
    }

    private GeminiSpeechSynthesisAdapter adapter() {
        return adapter("llave", FALLBACK, Duration.ofSeconds(5));
    }

    private void enqueue(Reply... next) {
        synchronized (replies) {
            replies.addAll(List.of(next));
        }
    }

    private static Reply ok(byte[] audio) {
        return new Reply(200, """
                {"status":"completed","steps":[{"type":"model_output","content":[
                 {"type":"audio","mime_type":"audio/wav","data":"%s"}]}]}"""
                .formatted(Base64.getEncoder().encodeToString(audio)), 0);
    }

    private static Reply status(int status) {
        return new Reply(status, "{\"error\":{\"code\":" + status + "}}", 0);
    }

    private static byte[] wav(int dataBytes) {
        byte[] bytes = new byte[44 + dataBytes];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy("WAVEfmt ".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 8);
        return bytes;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } catch (IOException clientGone) {
            // El cliente cortó por tiempo: no es un fallo del servidor simulado.
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- camino feliz ----

    @Test
    @DisplayName("CA-5.15.2: entrega el WAV con el modelo principal, el estilo y las etiquetas entre ángulos")
    void synthesize_shouldReturnWavFromPrimaryModel() {
        SpeechAudio audio = adapter().synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(audio.content()).isEqualTo(WAV);
        assertThat(audio.mediaType()).isEqualTo("audio/wav");
        assertThat(audio.model()).isEqualTo(PRIMARY);
        JsonNode body = bodies.getFirst();
        assertThat(body.path("model").asString()).isEqualTo(PRIMARY);
        JsonNode content = body.path("input").get(0).path("content").get(0);
        assertThat(content.path("text").asString()).isEqualTo(DIRECTION.text());
        assertThat(content.path("annotations").get(0).path("type").asString()).isEqualTo("speech_metadata");
        assertThat(content.path("annotations").get(0).path("style").asString()).isEqualTo("confident");
        assertThat(body.path("response_format").path("type").asString()).isEqualTo("audio");
        assertThat(body.path("response_format").has("mime_type")).isFalse();
        assertThat(body.path("generation_config").path("speech_config").get(0).path("voice").asString())
                .isEqualTo("Kore");
        assertThat(keys).containsExactly("llave");
    }

    @Test
    @DisplayName("CA-5.15.3: la voz depende del idioma, no del tono")
    void synthesize_shouldUseVoiceOfLanguage() {
        adapter().synthesize(DIRECTION, InterviewLanguage.ENGLISH);

        assertThat(bodies.getFirst().path("generation_config").path("speech_config").get(0).path("voice").asString())
                .isEqualTo("Puck");
    }

    @Test
    @DisplayName("CA-5.15.19: el texto con tildes, emojis y comillas viaja en UTF-8 sin alterarse")
    void synthesize_shouldSendTextUnchanged() {
        String text = "¿Cómo diseñarías «la API» de https://cameia.tech? 😀 \"ñ\"";

        adapter().synthesize(new SpeechDirection(text, "confident", false), InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(bodies.getFirst().path("input").get(0).path("content").get(0).path("text").asString())
                .isEqualTo(text);
    }

    // ---- respaldo ----

    @ParameterizedTest
    @ValueSource(ints = {429, 404, 500, 503})
    @DisplayName("Mucha demanda, modelo retirado o fallo del servidor: pasa al respaldo con corchetes y el estilo inicial")
    void synthesize_shouldUseFallback_whenPrimaryIsBusyOrMissing(int status) {
        enqueue(status(status), ok(WAV));

        SpeechAudio audio = adapter().synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(audio.model()).isEqualTo(FALLBACK);
        assertThat(bodies).hasSize(2);
        JsonNode content = bodies.get(1).path("input").get(0).path("content").get(0);
        assertThat(bodies.get(1).path("model").asString()).isEqualTo(FALLBACK);
        assertThat(content.path("text").asString())
                .isEqualTo("[confident] [sigh] ¿Qué métrica usó? [short pause] Sea concreto.");
        assertThat(content.has("annotations")).isFalse();
    }

    @Test
    @DisplayName("Si el respaldo también falla responde SPEECH_UNAVAILABLE con su causa")
    void synthesize_shouldFail_whenFallbackAlsoFails() {
        enqueue(status(429), status(429));

        assertThatThrownBy(() -> adapter().synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOfSatisfying(SpeechUnavailableException.class, failure -> assertThat(failure.getReason())
                        .contains("status=429").contains(FALLBACK))
                .hasMessage("No pudimos reproducir la pregunta. Inténtalo de nuevo o léela en pantalla.");
        assertThat(bodies).hasSize(2);
    }

    @Test
    @DisplayName("Sin respaldo configurado, un 429 responde SPEECH_UNAVAILABLE sin otro intento")
    void synthesize_shouldNotRetry_whenNoFallback() {
        enqueue(status(429));

        assertThatThrownBy(() -> adapter("llave", " ", Duration.ofSeconds(5))
                .synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechUnavailableException.class);
        assertThat(bodies).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    @DisplayName("CA-5.15.21: un 400, 401 o 403 no pasa al respaldo")
    void synthesize_shouldNotUseFallback_whenRequestIsRejected(int status) {
        enqueue(status(status));

        assertThatThrownBy(() -> adapter().synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOfSatisfying(SpeechUnavailableException.class,
                        failure -> assertThat(failure.getReason()).contains("status=" + status));
        assertThat(bodies).hasSize(1);
    }

    // ---- audio inválido (CA-5.15.21) ----

    @ParameterizedTest
    @ValueSource(strings = {
        "no es json",
        "{\"status\":\"completed\"",
        "[]",
        "{\"status\":\"completed\",\"steps\":[]}",
        "{\"steps\":[{\"content\":[{\"type\":\"text\",\"text\":\"hola\"}]}]}",
        "{\"steps\":[{\"content\":[{\"type\":\"audio\",\"data\":\"\"}]}]}",
        "{\"steps\":[{\"content\":[{\"type\":\"audio\",\"data\":\"%%%no-base64%%%\"}]}]}",
        "{\"steps\":[{\"content\":[{\"type\":\"audio\",\"data\":\"SUQzBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}]}]}",
        "{\"steps\":[{\"content\":[{\"type\":\"audio\",\"data\":\"UklGRiQAAABXQVZF\"}]}]}"
    })
    @DisplayName("CA-5.15.21: un cuerpo ilegible, sin audio, vacío, cortado o que no es WAV responde SPEECH_UNAVAILABLE")
    void synthesize_shouldReject_whenAudioIsInvalid(String body) {
        enqueue(new Reply(200, body, 0));

        assertThatThrownBy(() -> adapter().synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechUnavailableException.class);
        assertThat(bodies).hasSize(1);
    }

    @Test
    @DisplayName("CA-5.15.21: sin llave no llama a Gemini")
    void synthesize_shouldFailWithoutCalling_whenKeyIsMissing() {
        assertThatThrownBy(() -> adapter(null, FALLBACK, Duration.ofSeconds(5))
                .synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOfSatisfying(SpeechUnavailableException.class,
                        failure -> assertThat(failure.getReason()).isEqualTo("sin llave de Gemini"));
        assertThatThrownBy(() -> adapter(" ", FALLBACK, Duration.ofSeconds(5))
                .synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechUnavailableException.class);
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("Una conexión rechazada en los dos modelos responde SPEECH_UNAVAILABLE")
    void synthesize_shouldFail_whenConnectionIsRefused() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        GeminiSpeechSynthesisAdapter refused = adapter("llave", FALLBACK, Duration.ofSeconds(5),
                URI.create("http://127.0.0.1:" + closedPort));

        assertThatThrownBy(() -> refused.synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOfSatisfying(SpeechUnavailableException.class,
                        failure -> assertThat(failure.getReason()).contains("conexión fallida"));
    }

    // ---- tiempo máximo (CA-5.15.22) ----

    @Test
    @DisplayName("CA-5.15.22: con 10 s de tope, una respuesta a los 9,9 s se entrega")
    void synthesize_shouldReturnAudio_whenAnsweredJustBeforeTimeout() {
        Reply late = ok(WAV);
        enqueue(new Reply(late.status(), late.body(), 9_900));

        SpeechAudio audio = adapter("llave", FALLBACK, Duration.ofMillis(10_000))
                .synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(audio.content()).isEqualTo(WAV);
    }

    @Test
    @DisplayName("CA-5.15.22: con 10 s de tope, sin respuesta a los 10 s responde SPEECH_TIMEOUT sin respaldo")
    void synthesize_shouldTimeOut_whenNoAnswerWithinTimeout() {
        Reply late = ok(WAV);
        enqueue(new Reply(late.status(), late.body(), 10_500));

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter("llave", FALLBACK, Duration.ofMillis(10_000))
                .synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechTimeoutException.class)
                .hasMessage("La voz tardó demasiado. Inténtalo de nuevo o léela en pantalla.");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(10_400));
        assertThat(bodies).hasSize(1);
    }

    @Test
    @DisplayName("El respaldo usa solo el tiempo que queda: si se agota, SPEECH_TIMEOUT")
    void synthesize_shouldTimeOut_whenFallbackExceedsRemainingTime() {
        enqueue(new Reply(503, "{}", 200), new Reply(200, ok(WAV).body(), 1_000));

        assertThatThrownBy(() -> adapter("llave", FALLBACK, Duration.ofMillis(600))
                .synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechTimeoutException.class);
    }

    @Test
    void isWav_shouldRequireHeaderAndData() {
        assertThat(GeminiSpeechSynthesisAdapter.isWav(wav(1))).isTrue();
        assertThat(GeminiSpeechSynthesisAdapter.isWav(wav(0))).isFalse();
        assertThat(GeminiSpeechSynthesisAdapter.isWav(new byte[0])).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 8, 9, 10, 11})
    @DisplayName("Un byte distinto en RIFF o en WAVE ya no es WAV")
    void isWav_shouldRejectAnyWrongHeaderByte(int position) {
        byte[] bytes = wav(10);
        bytes[position] = 'X';

        assertThat(GeminiSpeechSynthesisAdapter.isWav(bytes)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"steps\":[{\"content\":[{\"type\":\"audio\",\"data\":7}]}]}",
        "{\"steps\":[{\"content\":[{\"type\":\"audio\"}]}]}",
        "{\"steps\":[{\"content\":[{\"data\":\"UklGRg==\"}]}]}"
    })
    @DisplayName("Un contenido de audio sin datos de texto responde SPEECH_UNAVAILABLE")
    void synthesize_shouldReject_whenAudioDataIsNotText(String body) {
        enqueue(new Reply(200, body, 0));

        assertThatThrownBy(() -> adapter().synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechUnavailableException.class);
    }

    @Test
    @DisplayName("Si el respaldo devuelve un audio inválido, responde SPEECH_UNAVAILABLE con la causa del respaldo")
    void synthesize_shouldFail_whenFallbackAudioIsInvalid() {
        enqueue(status(503), new Reply(200, "{\"steps\":[]}", 0));

        assertThatThrownBy(() -> adapter().synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOfSatisfying(SpeechUnavailableException.class, failure -> assertThat(failure.getReason())
                        .contains("sin audio").contains(FALLBACK));
    }

    @Test
    @DisplayName("Sin respaldo (nulo) un 503 responde SPEECH_UNAVAILABLE")
    void synthesize_shouldFail_whenFallbackIsNull() {
        enqueue(status(503));

        assertThatThrownBy(() -> adapter("llave", null, Duration.ofSeconds(5))
                .synthesize(DIRECTION, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechUnavailableException.class);
        assertThat(bodies).hasSize(1);
    }

    @Test
    @DisplayName("La representación del audio no imprime sus bytes")
    void speechAudio_toStringShouldNotPrintBytes() {
        assertThat(new SpeechAudio(WAV, "audio/wav", PRIMARY).toString())
                .isEqualTo("SpeechAudio[bytes=4844, mediaType=audio/wav, model=" + PRIMARY + "]");
    }
}
