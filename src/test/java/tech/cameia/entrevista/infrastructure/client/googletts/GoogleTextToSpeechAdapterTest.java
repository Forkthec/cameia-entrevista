package tech.cameia.entrevista.infrastructure.client.googletts;

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
import java.util.concurrent.Executors;

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

/**
 * El adaptador de Google Cloud Text-to-Speech contra un servidor simulado del JDK: cuerpo de la petición, voz por
 * idioma (CA-5.15.3), texto sin alterar (CA-5.15.19), errores y audio inválido (CA-5.15.21) y tiempo máximo exacto
 * (CA-5.15.22). Ninguna prueba llama a Google.
 */
class GoogleTextToSpeechAdapterTest {

    private static final String VOICE_ES = "es-US-Chirp3-HD-Kore";
    private static final String VOICE_EN = "en-US-Chirp3-HD-Charon";
    private static final String TEXT = "¿Qué métrica usó para medir el resultado?";
    private static final byte[] MP3 = mp3(417);

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final Deque<Reply> replies = new ArrayDeque<>();
    private HttpServer server;

    /** Respuesta programada del servidor simulado. */
    private record Reply(int status, String body, long delayMs) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            if (key != null) {
                keys.add(key);
            }
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null) {
                authorizations.add(authorization);
            }
            bodies.add(json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply reply;
            synchronized (replies) {
                reply = replies.isEmpty() ? ok(MP3) : replies.poll();
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

    private URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private GoogleTextToSpeechAdapter adapter(SpeechCredentials credentials, Duration timeout, URI base) {
        return new GoogleTextToSpeechAdapter(credentials, base,
                Map.of(InterviewLanguage.SPANISH_COLOMBIA, VOICE_ES, InterviewLanguage.ENGLISH, VOICE_EN), timeout,
                HttpClient.newHttpClient(), json);
    }

    private GoogleTextToSpeechAdapter adapter(Duration timeout) {
        return adapter(SpeechCredentials.apiKey("llave"), timeout, base());
    }

    private GoogleTextToSpeechAdapter adapter() {
        return adapter(Duration.ofSeconds(5));
    }

    private void enqueue(Reply... next) {
        synchronized (replies) {
            replies.addAll(List.of(next));
        }
    }

    private static Reply ok(byte[] audio) {
        return new Reply(200, "{\"audioContent\":\"" + Base64.getEncoder().encodeToString(audio) + "\"}", 0);
    }

    private static Reply status(int status) {
        return new Reply(status, "{\"error\":{\"code\":" + status + ",\"status\":\"X\"}}", 0);
    }

    /** Un MP3 mínimo: un marco MPEG-1 capa III y bytes de datos. */
    private static byte[] mp3(int dataBytes) {
        byte[] bytes = new byte[4 + dataBytes];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFB;
        bytes[2] = (byte) 0x90;
        return bytes;
    }

    /** Un MP3 con una etiqueta ID3 de {@code tagBytes} bytes antes del primer marco. */
    private static byte[] id3(int tagBytes, byte[] frame) {
        byte[] bytes = new byte[10 + tagBytes + frame.length];
        bytes[0] = 'I';
        bytes[1] = 'D';
        bytes[2] = '3';
        bytes[3] = 4;
        bytes[6] = (byte) ((tagBytes >> 21) & 0x7F);
        bytes[7] = (byte) ((tagBytes >> 14) & 0x7F);
        bytes[8] = (byte) ((tagBytes >> 7) & 0x7F);
        bytes[9] = (byte) (tagBytes & 0x7F);
        System.arraycopy(frame, 0, bytes, 10 + tagBytes, frame.length);
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
    @DisplayName("CA-5.15.2: entrega el MP3 de Google con la voz Chirp 3 HD en español")
    void synthesize_shouldReturnMp3() {
        SpeechAudio audio = adapter().synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(audio.content()).isEqualTo(MP3);
        assertThat(audio.mediaType()).isEqualTo("audio/mpeg");
        assertThat(audio.voice()).isEqualTo(VOICE_ES);
        assertThat(paths).containsExactly("POST /v1/text:synthesize");
        JsonNode body = bodies.getFirst();
        assertThat(body.path("input").path("text").asString()).isEqualTo(TEXT);
        assertThat(body.path("voice").path("languageCode").asString()).isEqualTo("es-US");
        assertThat(body.path("voice").path("name").asString()).isEqualTo(VOICE_ES);
        assertThat(body.path("audioConfig").path("audioEncoding").asString()).isEqualTo("MP3");
        assertThat(keys).containsExactly("llave");
    }

    @Test
    @DisplayName("CA-5.15.3: una entrevista en inglés usa la voz en inglés")
    void synthesize_shouldUseVoiceOfLanguage() {
        SpeechAudio audio = adapter().synthesize("Tell me about a time you failed.", InterviewLanguage.ENGLISH);

        assertThat(audio.voice()).isEqualTo(VOICE_EN);
        assertThat(bodies.getFirst().path("voice").path("languageCode").asString()).isEqualTo("en-US");
        assertThat(bodies.getFirst().path("voice").path("name").asString()).isEqualTo(VOICE_EN);
    }

    @Test
    @DisplayName("CA-5.15.19: tildes, ñ, emojis de 4 bytes, comillas, siglas y URL viajan en UTF-8 sin alterarse")
    void synthesize_shouldSendTextUnchanged() {
        String text = "¿Cómo diseñarías «la API» REST de https://cameia.tech/v1? 😀 \"ñ\" 𝕏 and the KPI 2,5 %";

        adapter().synthesize(text, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(bodies.getFirst().path("input").path("text").asString()).isEqualTo(text);
    }

    @Test
    @DisplayName("CA-5.15.19: 500 puntos de código de 4 bytes (2.000 bytes) se envían completos")
    void synthesize_shouldSendLongestText() {
        String text = "😀".repeat(500);

        adapter().synthesize(text, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(bodies.getFirst().path("input").path("text").asString()).isEqualTo(text);
    }

    @Test
    @DisplayName("CA-5.15.19: un texto de más de 5.000 bytes no sale hacia Google")
    void synthesize_shouldNotCall_whenTextExceedsGoogleLimit() {
        String text = "a".repeat(GoogleTextToSpeechAdapter.MAX_TEXT_BYTES + 1);

        assertThatThrownBy(() -> adapter().synthesize(text, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechUnavailableException.class);
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("Exactamente 5.000 bytes sí se envían")
    void synthesize_shouldCall_whenTextIsAtGoogleLimit() {
        adapter().synthesize("a".repeat(GoogleTextToSpeechAdapter.MAX_TEXT_BYTES), InterviewLanguage.ENGLISH);

        assertThat(bodies).hasSize(1);
    }

    @Test
    @DisplayName("Un MP3 con etiqueta ID3 antes del primer marco se acepta")
    void synthesize_shouldAcceptMp3WithId3Tag() {
        byte[] tagged = id3(200, mp3(100));
        enqueue(ok(tagged));

        assertThat(adapter().synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA).content()).isEqualTo(tagged);
    }

    @Test
    @DisplayName("Con credenciales por defecto (ADC) envía el token de la cuenta de servicio")
    void synthesize_shouldSendBearerToken_whenUsingApplicationDefault() {
        SpeechCredentials credentials = uri -> Map.of("Authorization", "Bearer token-de-prueba");

        adapter(credentials, Duration.ofSeconds(5), base()).synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(authorizations).containsExactly("Bearer token-de-prueba");
        assertThat(keys).isEmpty();
    }

    // ---- errores de Google (CA-5.15.21) ----

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 502, 503, 504})
    @DisplayName("CA-5.15.21: un 400, 403 (permiso o facturación), 429 o 5xx responde SPEECH_UNAVAILABLE sin reintento")
    void synthesize_shouldFail_whenGoogleRejects(int status) {
        enqueue(status(status));

        assertThatThrownBy(() -> adapter().synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOfSatisfying(SpeechUnavailableException.class, failure -> assertThat(failure.getReason())
                        .isEqualTo("status=" + status + " [voz=" + VOICE_ES + "]"))
                .hasMessage("No pudimos reproducir la pregunta. Inténtalo de nuevo o léela en pantalla.");
        assertThat(bodies).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "no es json",
        "{\"audioContent\":\"//uQ",
        "[]",
        "null",
        "{}",
        "{\"audioContent\":\"\"}",
        "{\"audioContent\":7}",
        "{\"audioContent\":null}",
        "{\"audioContent\":\"%%%no-base64%%%\"}",
        "{\"audioContent\":\"UklGRiQAAABXQVZFZm10IBAAAAABAAEA\"}",
        "{\"audioContent\":\"//s=\"}",
        "{\"audioContent\":\"SUQzBAAAAAAAAA==\"}"
    })
    @DisplayName("CA-5.15.21: JSON ilegible o cortado, sin audio, vacío, no base64 o que no es MP3 responde SPEECH_UNAVAILABLE")
    void synthesize_shouldReject_whenAudioIsInvalid(String body) {
        enqueue(new Reply(200, body, 0));

        assertThatThrownBy(() -> adapter().synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechUnavailableException.class);
        assertThat(bodies).hasSize(1);
    }

    @Test
    @DisplayName("CA-5.15.21: sin credencial no llama a Google")
    void synthesize_shouldFailWithoutCalling_whenCredentialIsMissing() {
        for (String missing : new String[] {null, "", "  "}) {
            GoogleTextToSpeechAdapter noKey = adapter(SpeechCredentials.apiKey(missing), Duration.ofSeconds(5),
                    base());
            assertThatThrownBy(() -> noKey.synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA))
                    .isInstanceOfSatisfying(SpeechUnavailableException.class,
                            failure -> assertThat(failure.getReason()).isEqualTo("sin credencial de Text-to-Speech"));
        }
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("Una conexión rechazada responde SPEECH_UNAVAILABLE")
    void synthesize_shouldFail_whenConnectionIsRefused() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        GoogleTextToSpeechAdapter refused = adapter(SpeechCredentials.apiKey("llave"), Duration.ofSeconds(5),
                URI.create("http://127.0.0.1:" + closedPort));

        assertThatThrownBy(() -> refused.synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOfSatisfying(SpeechUnavailableException.class,
                        failure -> assertThat(failure.getReason()).contains("conexión fallida"));
    }

    @Test
    @DisplayName("Un idioma sin voz configurada responde SPEECH_UNAVAILABLE sin llamar")
    void synthesize_shouldFail_whenLanguageHasNoVoice() {
        GoogleTextToSpeechAdapter onlySpanish = new GoogleTextToSpeechAdapter(SpeechCredentials.apiKey("llave"),
                base(), Map.of(InterviewLanguage.SPANISH_COLOMBIA, VOICE_ES), Duration.ofSeconds(5),
                HttpClient.newHttpClient(), json);

        assertThatThrownBy(() -> onlySpanish.synthesize(TEXT, InterviewLanguage.ENGLISH))
                .isInstanceOf(SpeechUnavailableException.class);
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("Un hilo interrumpido responde SPEECH_UNAVAILABLE y conserva la interrupción")
    void synthesize_shouldFail_whenInterrupted() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> adapter().synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA))
                    .isInstanceOf(SpeechUnavailableException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    // ---- tiempo máximo (CA-5.15.22) ----

    @Test
    @DisplayName("CA-5.15.22: con 10 s de tope, una respuesta a los 9,9 s se entrega")
    void synthesize_shouldReturnAudio_whenAnsweredJustBeforeTimeout() {
        enqueue(new Reply(200, ok(MP3).body(), 9_900));

        SpeechAudio audio = adapter(Duration.ofMillis(10_000)).synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(audio.content()).isEqualTo(MP3);
    }

    @Test
    @DisplayName("CA-5.15.22: con 10 s de tope, sin respuesta a los 10 s responde SPEECH_TIMEOUT")
    void synthesize_shouldTimeOut_whenNoAnswerWithinTimeout() {
        enqueue(new Reply(200, ok(MP3).body(), 10_500));

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter(Duration.ofMillis(10_000))
                .synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechTimeoutException.class)
                .hasMessage("La voz tardó demasiado. Inténtalo de nuevo o léela en pantalla.");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(10_400));
        assertThat(bodies).hasSize(1);
    }

    @Test
    @DisplayName("El tiempo de obtener la credencial cuenta dentro del tope: si lo agota, SPEECH_TIMEOUT sin llamar")
    void synthesize_shouldTimeOut_whenCredentialTakesAllTheTime() {
        SpeechCredentials slow = uri -> {
            sleep(150);
            return Map.of("Authorization", "Bearer lento");
        };

        assertThatThrownBy(() -> adapter(slow, Duration.ofMillis(100), base())
                .synthesize(TEXT, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(SpeechTimeoutException.class);
        assertThat(bodies).isEmpty();
    }

    // ---- utilidades ----

    @Test
    void isMp3_shouldRequireFrameSyncAndData() {
        assertThat(GoogleTextToSpeechAdapter.isMp3(mp3(1))).isTrue();
        assertThat(GoogleTextToSpeechAdapter.isMp3(mp3(0))).isFalse();
        assertThat(GoogleTextToSpeechAdapter.isMp3(new byte[0])).isFalse();
        assertThat(GoogleTextToSpeechAdapter.isMp3(new byte[] {(byte) 0xFF, (byte) 0x1B, 0, 0, 0})).isFalse();
        assertThat(GoogleTextToSpeechAdapter.isMp3(new byte[] {(byte) 0xFE, (byte) 0xFB, 0, 0, 0})).isFalse();
        assertThat(GoogleTextToSpeechAdapter.isMp3(id3(20, mp3(10)))).isTrue();
        assertThat(GoogleTextToSpeechAdapter.isMp3(id3(20, new byte[] {1, 2, 3, 4, 5, 6}))).isFalse();
        byte[] tagLongerThanFile = id3(0, mp3(10));
        tagLongerThanFile[6] = 0x7F;
        assertThat(GoogleTextToSpeechAdapter.isMp3(tagLongerThanFile)).isFalse();
        assertThat(GoogleTextToSpeechAdapter.isMp3(new byte[] {'I', 'D', 'X', 0, 0, 0, 0, 0, 0, 0})).isFalse();
        assertThat(GoogleTextToSpeechAdapter.isMp3(new byte[] {'I', 'X', '3', 0, 0, 0, 0, 0, 0, 0})).isFalse();
    }

    @Test
    void languageCodeOf_shouldTakeLanguageAndCountry() {
        assertThat(GoogleTextToSpeechAdapter.languageCodeOf("es-US-Chirp3-HD-Kore")).isEqualTo("es-US");
        assertThat(GoogleTextToSpeechAdapter.languageCodeOf("en-US-Chirp3-HD-Charon")).isEqualTo("en-US");
        for (String wrong : new String[] {"Kore", "es-US", "-US-x", "es--x", "es-US-"}) {
            assertThatThrownBy(() -> GoogleTextToSpeechAdapter.languageCodeOf(wrong))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("La representación del audio no imprime sus bytes")
    void speechAudio_toStringShouldNotPrintBytes() {
        assertThat(new SpeechAudio(MP3, "audio/mpeg", VOICE_ES).toString())
                .isEqualTo("SpeechAudio[bytes=421, mediaType=audio/mpeg, voice=" + VOICE_ES + "]");
    }
}
