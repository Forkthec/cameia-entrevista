package tech.cameia.entrevista.infrastructure.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.AudioFormat;
import tech.cameia.entrevista.domain.model.AudioInspection;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;

/**
 * Adaptador de transcripción contra un servidor simulado del JDK que imita la API de Gemini. Ninguna prueba usa el
 * proveedor real.
 */
@ExtendWith(OutputCaptureExtension.class)
class GeminiTranscriptionAdapterTest {

    private static final byte[] AUDIO = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4};
    private static final String SECRET_TEXT = "Lidere un equipo de cuatro personas";

    private HttpServer server;
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply.get().write(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GeminiTranscriptionAdapter adapter(Duration timeout) {
        Client client = Client.builder().apiKey("clave-falsa-de-prueba")
                .httpOptions(HttpOptions.builder().baseUrl("http://localhost:" + server.getAddress().getPort())
                        .build())
                .build();
        return new GeminiTranscriptionAdapter(client, new TranscriptionProperties("modelo-de-voz", timeout));
    }

    private static AudioClip clip() {
        return new AudioClip(AUDIO.clone(), new AudioInspection(AudioFormat.WEBM_OPUS, Duration.ofSeconds(3)));
    }

    private static String answer(String text) {
        return """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":15,"totalTokenCount":135},
                 "modelVersion":"gemini-voz-001"}
                """.formatted(text);
    }

    private static Reply json(int status, String body) {
        return exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        };
    }

    @Test
    @DisplayName("Envía el audio en línea con su tipo y devuelve el texto, los tokens y el modelo")
    void transcribe_shouldSendInlineAudioAndReturnText() {
        reply.set(json(200, answer("  " + SECRET_TEXT + " ")));

        Transcription result = adapter(Duration.ofSeconds(5)).transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(result.text()).isEqualTo(SECRET_TEXT);
        assertThat(result.model()).isEqualTo("gemini-voz-001");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(15);
        // Una sola llamada a generateContent, sin subir el audio a la API de archivos (CA-5.8.18).
        assertThat(lastPath.get()).endsWith("/models/modelo-de-voz:generateContent").doesNotContain("upload");
        assertThat(lastBody.get()).contains("\"mimeType\":\"audio/webm\"")
                .contains(Base64.getEncoder().encodeToString(AUDIO)).doesNotContain("fileUri");
    }

    @Test
    @DisplayName("Si el modelo indica que no hay voz, el texto llega vacío")
    void transcribe_shouldReturnBlankWhenNoSpeech() {
        reply.set(json(200, answer(GeminiTranscriptionAdapter.NO_SPEECH)));

        assertThat(adapter(Duration.ofSeconds(5)).transcribe(clip(), InterviewLanguage.ENGLISH).text()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429, 500, 503})
    @DisplayName("Un error del proveedor es TRANSCRIPTION_UNAVAILABLE, sin reintentos")
    void transcribe_shouldFailWhenProviderFails(int status) {
        reply.set(json(status, "{\"error\":{\"code\":" + status + ",\"message\":\"fallo\"}}"));
        GeminiTranscriptionAdapter adapter = adapter(Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class)
                .hasMessage("No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.");
    }

    @Test
    @DisplayName("Una respuesta sin candidatos es TRANSCRIPTION_UNAVAILABLE")
    void transcribe_shouldFailWithoutCandidates() {
        reply.set(json(200, "{\"candidates\":[]}"));
        GeminiTranscriptionAdapter adapter = adapter(Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class);
    }

    @Test
    @DisplayName("Si el proveedor no responde dentro del tiempo de espera es TRANSCRIPTION_UNAVAILABLE")
    void transcribe_shouldFailOnTimeout() {
        reply.set(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            json(200, answer("tarde")).write(exchange);
        });
        GeminiTranscriptionAdapter adapter = adapter(Duration.ofMillis(300));

        assertThatThrownBy(() -> adapter.transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class);
    }

    @Test
    @DisplayName("El registro no lleva el texto transcrito ni el audio")
    void transcribe_shouldNotLogTextOrAudio(CapturedOutput output) {
        reply.set(json(200, answer(SECRET_TEXT)));

        adapter(Duration.ofSeconds(5)).transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(output.getAll()).contains("Transcripción [resultado=ok").doesNotContain(SECRET_TEXT)
                .doesNotContain(Base64.getEncoder().encodeToString(AUDIO));
    }

    /** Lo que el servidor simulado hace con una petición. */
    @FunctionalInterface
    interface Reply {

        void write(HttpExchange exchange) throws IOException;
    }
}
