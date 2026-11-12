package tech.cameia.entrevista.infrastructure.client.assemblyai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioAnswer;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;

/**
 * El adaptador de AssemblyAI contra un servidor simulado del JDK: subida, creación con detección de idioma
 * (CA-5.12.2, CA-5.12.10), espera del resultado y fallos que terminan en {@code TRANSCRIPTION_UNAVAILABLE}
 * (CA-5.12.8). Ninguna prueba llama al proveedor real.
 */
class AssemblyAiTranscriptionAdapterTest {

    private static final AudioAnswer AUDIO = new AudioAnswer(new byte[] {1, 2, 3}, "audio/webm");

    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, String> requestBodies = new ConcurrentHashMap<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final AtomicInteger polls = new AtomicInteger();
    private HttpServer server;
    private volatile int uploadStatus = 200;
    private volatile String finalStatusBody;
    private volatile int pollsBeforeDone = 1;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/upload", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            respond(exchange, uploadStatus, "{\"upload_url\":\"https://cdn.example/audio-1\"}");
        });
        server.createContext("/v2/transcript", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod())) {
                requestBodies.put("create", new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                respond(exchange, 200, "{\"id\":\"tr-1\",\"status\":\"queued\"}");
                return;
            }
            if (polls.incrementAndGet() <= pollsBeforeDone) {
                respond(exchange, 200, "{\"id\":\"tr-1\",\"status\":\"processing\"}");
            } else {
                respond(exchange, 200, finalStatusBody);
            }
        });
        server.start();
        finalStatusBody = """
                {"id":"tr-1","status":"completed","text":"Usé Spring Boot y Kafka.",
                 "language_code":"es","language_confidence":0.93}""";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AssemblyAiTranscriptionAdapter adapter(String key, Duration timeout) {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new AssemblyAiTranscriptionAdapter(key, base, List.of("universal-3-5-pro", "universal-2"), timeout,
                Duration.ofMillis(10), HttpClient.newHttpClient(), json);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void transcribe_shouldReturnTextAndDetectedLanguage_whenTranscriptCompletes() {
        Transcription result = adapter("llave", Duration.ofSeconds(5))
                .transcribe(AUDIO, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(result.text()).isEqualTo("Usé Spring Boot y Kafka.");
        assertThat(result.detectedLanguage()).isEqualTo("es");
        assertThat(result.languageConfidence()).isEqualTo(0.93);
        assertThat(polls.get()).isEqualTo(2);
        assertThat(authorizations).containsOnly("llave");
    }

    @Test
    void transcribe_shouldRequestDetectionWithInterviewLanguageAsFallback_whenInterviewIsInEnglish() {
        adapter("llave", Duration.ofSeconds(5)).transcribe(AUDIO, InterviewLanguage.ENGLISH);

        JsonNode body = json.readTree(requestBodies.get("create"));
        assertThat(body.path("audio_url").asString()).isEqualTo("https://cdn.example/audio-1");
        assertThat(body.path("language_detection").asBoolean()).isTrue();
        JsonNode options = body.path("language_detection_options");
        assertThat(options.path("expected_languages").get(0).asString()).isEqualTo("en");
        assertThat(options.path("expected_languages").get(1).asString()).isEqualTo("es");
        assertThat(options.path("fallback_language").asString()).isEqualTo("en");
        assertThat(options.path("on_no_speech_detected").asString()).isEqualTo("fallback");
        assertThat(body.path("speech_models").get(0).asString()).isEqualTo("universal-3-5-pro");
        assertThat(body.has("language_confidence_threshold")).isFalse();
    }

    @Test
    void transcribe_shouldReturnEmptyText_whenProviderReturnsNullText() {
        finalStatusBody = "{\"id\":\"tr-1\",\"status\":\"completed\",\"text\":null,\"language_code\":\"es\"}";

        Transcription result = adapter("llave", Duration.ofSeconds(5))
                .transcribe(AUDIO, InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(result.text()).isEmpty();
        assertThat(result.languageConfidence()).isNull();
    }

    @Test
    void transcribe_shouldFail_whenTranscriptEndsInError() {
        finalStatusBody = "{\"id\":\"tr-1\",\"status\":\"error\",\"error\":\"Audio file is corrupted\"}";

        assertThatThrownBy(() -> adapter("llave", Duration.ofSeconds(5))
                .transcribe(AUDIO, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class);
    }

    @Test
    void transcribe_shouldFail_whenUploadIsRejected() {
        uploadStatus = 401;

        assertThatThrownBy(() -> adapter("llave-mala", Duration.ofSeconds(5))
                .transcribe(AUDIO, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class)
                .extracting(error -> ((TranscriptionUnavailableException) error).getReason())
                .isEqualTo("subida respondió 401");
    }

    @Test
    void transcribe_shouldFail_whenTranscriptDoesNotFinishInTime() {
        pollsBeforeDone = Integer.MAX_VALUE;

        assertThatThrownBy(() -> adapter("llave", Duration.ofMillis(300))
                .transcribe(AUDIO, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class);
    }

    @Test
    void transcribe_shouldFail_whenResponseIsNotJson() {
        finalStatusBody = "<html>bad gateway</html>";

        assertThatThrownBy(() -> adapter("llave", Duration.ofSeconds(5))
                .transcribe(AUDIO, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class);
    }

    @Test
    void transcribe_shouldFailWithoutCallingProvider_whenKeyIsMissing() {
        assertThatThrownBy(() -> adapter(" ", Duration.ofSeconds(5))
                .transcribe(AUDIO, InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class);
        assertThat(authorizations).isEmpty();
    }

    @Test
    void model_shouldJoinConfiguredModels() {
        assertThat(adapter("llave", Duration.ofSeconds(5)).model()).isEqualTo("universal-3-5-pro,universal-2");
        assertThat(adapter("llave", Duration.ofSeconds(5)).provider()).isEqualTo("assemblyai");
    }
}
