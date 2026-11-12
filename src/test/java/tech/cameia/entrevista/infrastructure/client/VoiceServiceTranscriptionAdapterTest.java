package tech.cameia.entrevista.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TranscriptionFailure;
import tech.cameia.entrevista.infrastructure.config.voz.VoiceServiceProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador de Voz contra un servidor simulado del JDK: contrato de la petición y clasificación de cada fallo, que
 * decide el reintento de HU-5.13.
 */
@ExtendWith(OutputCaptureExtension.class)
class VoiceServiceTranscriptionAdapterTest {

    private static final String PRIVATE_TEXT = "texto-privado-de-la-persona";
    private static final byte[] AUDIO = "bytes-del-audio".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> contentTypes = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile Reply reply;

    @FunctionalInterface
    private interface Reply {
        void write(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            contentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            reply.write(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    private VoiceServiceTranscriptionAdapter adapter(URI base, Duration timeout) {
        VoiceServiceProperties properties = new VoiceServiceProperties(base, timeout, Duration.ofSeconds(2),
                Duration.ZERO);
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        return new VoiceServiceTranscriptionAdapter(http, JsonMapper.builder().build(), properties);
    }

    private VoiceServiceTranscriptionAdapter adapter() {
        return adapter(URI.create("http://localhost:" + server.getAddress().getPort()), Duration.ofSeconds(5));
    }

    private static Reply json(int status, String body) {
        return exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        };
    }

    private static AudioClip clip() {
        return new AudioClip(AUDIO, "audio/webm;codecs=opus");
    }

    private static TranscriptionFailure failureOf(Runnable call) {
        try {
            call.run();
        } catch (TranscriptionUnavailableException error) {
            return error.failure();
        }
        throw new AssertionError("Se esperaba un fallo de transcripción");
    }

    @Test
    @DisplayName("Envía el audio y el idioma en multipart a /api/v1/transcriptions y devuelve el texto y el idioma")
    void transcribe_shouldSendMultipartAndReadText(CapturedOutput output) {
        reply = json(200, "{\"text\":\"" + PRIVATE_TEXT + "\",\"language\":\"es\"}");

        Transcription result = adapter().transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(result.text()).isEqualTo(PRIVATE_TEXT);
        assertThat(result.detectedLanguage()).isEqualTo("es");
        assertThat(paths).containsExactly("/api/v1/transcriptions");
        assertThat(contentTypes.get(0)).startsWith("multipart/form-data; boundary=");
        assertThat(bodies.get(0))
                .contains("name=\"language\"\r\n\r\nes-CO\r\n")
                .contains("name=\"audio\"; filename=\"respuesta\"\r\nContent-Type: audio/webm;codecs=opus\r\n\r\n"
                        + "bytes-del-audio\r\n");
        assertThat(output.getAll()).doesNotContain(PRIVATE_TEXT).doesNotContain("bytes-del-audio");
    }

    @Test
    @DisplayName("Sin idioma en la respuesta devuelve el idioma nulo")
    void transcribe_shouldAllowMissingLanguage() {
        reply = json(200, "{\"text\":\"hola\"}");

        assertThat(adapter().transcribe(clip(), InterviewLanguage.ENGLISH).detectedLanguage()).isNull();
    }

    @Test
    @DisplayName("Voz no disponible (503) es un error transitorio: se puede reintentar")
    void transcribe_shouldClassify503AsTransient() {
        reply = json(503, "{\"detail\":\"ocupado\"}");

        assertThat(failureOf(() -> adapter().transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA)))
                .isEqualTo(TranscriptionFailure.TRANSIENT);
    }

    @Test
    @DisplayName("Conexión rechazada es un error transitorio: se puede reintentar")
    void transcribe_shouldClassifyRefusedConnectionAsTransient() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        VoiceServiceTranscriptionAdapter closed = adapter(URI.create("http://localhost:" + closedPort),
                Duration.ofSeconds(5));

        assertThat(failureOf(() -> closed.transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA)))
                .isEqualTo(TranscriptionFailure.TRANSIENT);
    }

    @Test
    @DisplayName("Una respuesta que no llega dentro del tiempo de espera es TIMEOUT: no se reintenta")
    void transcribe_shouldClassifySlowResponseAsTimeout() {
        reply = exchange -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        };
        VoiceServiceTranscriptionAdapter slow = adapter(
                URI.create("http://localhost:" + server.getAddress().getPort()), Duration.ofMillis(300));

        long start = System.nanoTime();
        assertThat(failureOf(() -> slow.transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA)))
                .isEqualTo(TranscriptionFailure.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Otro estado de error (400, 422, 500) es REJECTED: no se reintenta")
    void transcribe_shouldClassifyOtherStatusesAsRejected() {
        for (int status : new int[] {400, 422, 500, 502}) {
            reply = json(status, "{}");
            assertThat(failureOf(() -> adapter().transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA)))
                    .as("estado %s", status).isEqualTo(TranscriptionFailure.REJECTED);
        }
    }

    @Test
    @DisplayName("Un cuerpo ilegible o sin text es REJECTED")
    void transcribe_shouldClassifyUnreadableBodyAsRejected() {
        reply = json(200, "no-es-json");
        assertThat(failureOf(() -> adapter().transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA)))
                .isEqualTo(TranscriptionFailure.REJECTED);

        reply = json(200, "{\"texto\":\"otra forma\"}");
        assertThat(failureOf(() -> adapter().transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA)))
                .isEqualTo(TranscriptionFailure.REJECTED);
    }

    @Test
    @DisplayName("Un tipo de contenido con saltos de línea no se copia a la parte del audio")
    void transcribe_shouldNotInjectHeaders_whenContentTypeHasLineBreaks() {
        reply = json(200, "{\"text\":\"ok\"}");

        adapter().transcribe(new AudioClip(AUDIO, "audio/webm\r\nX-Inyectado: 1"), InterviewLanguage.ENGLISH);

        assertThat(bodies.get(0)).doesNotContain("X-Inyectado").contains("Content-Type: application/octet-stream");
    }

    @Test
    @DisplayName("El fallo llega como TRANSCRIPTION_UNAVAILABLE con el mensaje para la persona")
    void transcribe_shouldThrowBusinessException() {
        reply = json(503, "{}");

        assertThatThrownBy(() -> adapter().transcribe(clip(), InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionUnavailableException.class)
                .hasMessage(TranscriptionUnavailableException.MESSAGE);
    }
}
