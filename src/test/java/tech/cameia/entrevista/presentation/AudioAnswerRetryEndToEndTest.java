package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.support.ReplicaFixtures;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * HU-5.13 contra el servidor completo, PostgreSQL 16 real y un servicio de Voz simulado por HTTP: la sesión se crea y
 * se inicia por los endpoints reales (con el modelo de lenguaje simulado) y el audio viaja por el adaptador real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"cameia.voz.timeout=2s", "cameia.voz.retry-pause=0s"})
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
@Import(AudioAnswerRetryEndToEndTest.PlannedModelConfiguration.class)
class AudioAnswerRetryEndToEndTest {

    private static final String BASE = "/api/v1/interviews/sessions";
    private static final String UID = "uid-audio";
    private static final UUID PROFILE = ReplicaFixtures.PROFILE_ID;
    private static final String ROLE = "a0000001-0000-0000-0000-000000000002";
    private static final String UNAVAILABLE_MESSAGE =
            "No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.";

    private static final String PLAN = """
            {"idioma":"es-CO","preguntas":[
             {"posicion":1,"tipo":"APERTURA","texto":"Cuéntame brevemente quién eres y por qué te interesa este rol."},
             {"posicion":2,"tipo":"TECNICA","texto":"¿Cómo optimizarías una consulta SQL lenta?"},
             {"posicion":3,"tipo":"CONDUCTUAL","texto":"Háblame de un desacuerdo con un compañero y cómo lo resolviste."},
             {"posicion":4,"tipo":"TECNICA","texto":"¿Cómo validarías la calidad de un conjunto de datos?"},
             {"posicion":5,"tipo":"SITUACIONAL","texto":"Si un informe tuyo tuviera un error en producción, ¿qué harías?"}]}""";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final FakeVoice VOICE = FakeVoice.start();

    @DynamicPropertySource
    static void voiceUrl(DynamicPropertyRegistry registry) {
        registry.add("cameia.voz.base-url", () -> "http://localhost:" + VOICE.port());
    }

    @AfterAll
    static void stopVoice() {
        VOICE.stop();
    }

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProfileReplicaRepository replicas;

    @Autowired
    private TransactionTemplate transaction;

    private String sessionId;
    private String firstTurnId;

    @BeforeEach
    void startInterview() {
        VOICE.reset();
        jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia, "
                + "microentrevista.contexto_profesional_replica, microentrevista.rol_profesional CASCADE");
        jdbc.update("""
                INSERT INTO microentrevista.rol_profesional (id, nombre_es, nombre_en, estado, actualizado_en,
                    fecha_replicacion)
                VALUES (?::uuid, 'Rol', 'Role', 'ACTIVE', now(), now())
                """, ROLE);
        Instant now = Instant.now();
        transaction.executeWithoutResult(tx -> replicas.upsertIfNotOlder(new ProfileReplica(PROFILE,
                new FirebaseUid(UID), "Analista de datos", ReplicatedProfileStatus.COMPLETED, null,
                ReplicaFixtures.CONTEXT_JSON, ReplicaFixtures.ASPIRED_ROLES_JSON, now), now));
        String json = """
                {"profileId":"%s","targetRoleId":"%s","mode":"SIMULACION","tone":"CALIDO",
                 "answerFormat":"AUDIO","sessionLanguage":"es-CO"}""".formatted(PROFILE, ROLE);
        ResponseEntity<String> created = json(HttpMethod.POST, BASE, json);
        sessionId = JsonPath.read(created.getBody(), "$.sessionId");
        ResponseEntity<String> started = json(HttpMethod.POST, BASE + "/" + sessionId + "/start", null);
        assertThat(started.getStatusCode().value()).isEqualTo(200);
        firstTurnId = JsonPath.read(started.getBody(), "$.pendingQuestion.turnId");
    }

    // ---- ayudantes ----

    private ResponseEntity<String> json(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", UID);
        return client.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> sendAudio(String turnId) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType("audio/webm"));
        ByteArrayResource file = new ByteArrayResource("grabacion".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "respuesta.webm";
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("turnId", turnId);
        form.add("audio", new HttpEntity<>(file, partHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Id", UID);
        return client.exchange(BASE + "/" + sessionId + "/answers/audio", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }

    private int answersOf(String turnId) {
        return jdbc.queryForObject("SELECT count(*) FROM microentrevista.intento_respuesta WHERE turno_id = ?::uuid",
                Integer.class, turnId);
    }

    private String turnStatus(String turnId) {
        return jdbc.queryForObject("SELECT estado FROM microentrevista.turno WHERE id = ?::uuid", String.class,
                turnId);
    }

    private int turnCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM microentrevista.turno t JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
                 WHERE p.sesion_id = ?::uuid""", Integer.class, sessionId);
    }

    // ---- pruebas ----

    @Test
    @DisplayName("CA-5.13.1/2: Voz falla con 503 y luego responde; se registra una sola respuesta y avanza a la 2")
    void audio_shouldBeRegisteredOnce_whenRetrySucceeds() {
        VOICE.enqueue(FakeVoice.status(503)).enqueue(FakeVoice.text("Soy analista de datos"));

        ResponseEntity<String> response = sendAudio(firstTurnId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(VOICE.hits()).isEqualTo(2);
        assertThat((String) JsonPath.read(response.getBody(), "$.turn.status")).isEqualTo("COMPLETED");
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.currentQuestion")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.nextQuestion.number")).isEqualTo(2);
        assertThat((Boolean) JsonPath.read(response.getBody(), "$.interviewFinished")).isFalse();
        assertThat(answersOf(firstTurnId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT origen_respuesta || '|' || es_definitiva || '|' || respuesta_texto
                  FROM microentrevista.intento_respuesta WHERE turno_id = ?::uuid""", String.class, firstTurnId))
                .isEqualTo("AUDIO|true|Soy analista de datos");
        assertThat(turnStatus(firstTurnId)).isEqualTo("COMPLETED");
        String state = json(HttpMethod.GET, BASE + "/" + sessionId + "/state", null).getBody();
        assertThat((Integer) JsonPath.read(state, "$.progress.currentQuestion")).isEqualTo(2);
    }

    @Test
    @DisplayName("CA-5.13.3/5/7: dos 503 → 503 TRANSCRIPTION_UNAVAILABLE sin respuesta; grabar de nuevo registra una sola")
    void audio_shouldLeaveQuestionPending_whenAttemptsAreExhausted() {
        VOICE.enqueue(FakeVoice.status(503)).enqueue(FakeVoice.status(503));

        ResponseEntity<String> failed = sendAudio(firstTurnId);

        assertThat(failed.getStatusCode().value()).isEqualTo(503);
        assertThat((String) JsonPath.read(failed.getBody(), "$.code")).isEqualTo("TRANSCRIPTION_UNAVAILABLE");
        assertThat((String) JsonPath.read(failed.getBody(), "$.detail")).isEqualTo(UNAVAILABLE_MESSAGE);
        assertThat(VOICE.hits()).isEqualTo(2);
        assertThat(answersOf(firstTurnId)).isZero();
        assertThat(turnStatus(firstTurnId)).isEqualTo("PENDING");
        assertThat(turnCount()).isEqualTo(1);
        String state = json(HttpMethod.GET, BASE + "/" + sessionId + "/state", null).getBody();
        assertThat((String) JsonPath.read(state, "$.pendingQuestion.turnId")).isEqualTo(firstTurnId);

        VOICE.enqueue(FakeVoice.text("Segunda grabación"));
        ResponseEntity<String> again = sendAudio(firstTurnId);

        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat(answersOf(firstTurnId)).isEqualTo(1);
        assertThat((Integer) JsonPath.read(again.getBody(), "$.nextQuestion.number")).isEqualTo(2);
    }

    @Test
    @DisplayName("CA-5.13.3: si el primer intento agota el tiempo de espera, no reintenta")
    void audio_shouldNotRetry_whenFirstAttemptTimesOut() {
        VOICE.enqueue(FakeVoice.delayed(4000, "tarde")).enqueue(FakeVoice.text("no debe usarse"));

        ResponseEntity<String> response = sendAudio(firstTurnId);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat((String) JsonPath.read(response.getBody(), "$.code")).isEqualTo("TRANSCRIPTION_UNAVAILABLE");
        assertThat(VOICE.hits()).isEqualTo(1);
        assertThat(answersOf(firstTurnId)).isZero();
        assertThat(turnStatus(firstTurnId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("CA-5.13.8: otro audio del mismo turno durante la transcripción recibe 409 TURN_PROCESSING")
    void audio_shouldRejectSecondSubmission_whileTranscribing() throws Exception {
        CountDownLatch inVoice = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        VOICE.enqueue(exchange -> {
            inVoice.countDown();
            await(release);
            FakeVoice.text("La primera").write(exchange);
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<String>> first = pool.submit(() -> sendAudio(firstTurnId));
            assertThat(inVoice.await(10, TimeUnit.SECONDS)).isTrue();

            ResponseEntity<String> second = sendAudio(firstTurnId);
            release.countDown();

            assertThat(second.getStatusCode().value()).isEqualTo(409);
            assertThat((String) JsonPath.read(second.getBody(), "$.code")).isEqualTo("TURN_PROCESSING");
            assertThat(first.get(10, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            assertThat(VOICE.hits()).isEqualTo(1);
            assertThat(answersOf(firstTurnId)).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Sin audio responde 422 VALIDATION_FAILED con el campo audio y no llama a Voz")
    void audio_shouldReject_whenAudioIsMissing() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("turnId", firstTurnId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Id", UID);

        ResponseEntity<String> response = client.exchange(BASE + "/" + sessionId + "/answers/audio",
                HttpMethod.POST, new HttpEntity<>(form, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getBody(), "$.errors[0].field")).isEqualTo("audio");
        assertThat((String) JsonPath.read(response.getBody(), "$.errors[0].code")).isEqualTo("AUDIO_REQUIRED");
        assertThat(VOICE.hits()).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- dobles ----

    /** Servicio de Voz simulado: responde, en orden, las respuestas programadas y cuenta las llamadas. */
    static final class FakeVoice {

        @FunctionalInterface
        interface Reply {
            void write(HttpExchange exchange) throws IOException;
        }

        private final HttpServer server;
        private final Deque<Reply> replies = new ArrayDeque<>();
        private final AtomicInteger hits = new AtomicInteger();

        private FakeVoice(HttpServer server) {
            this.server = server;
        }

        static FakeVoice start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                FakeVoice voice = new FakeVoice(server);
                server.createContext("/api/v1/transcriptions", exchange -> {
                    voice.hits.incrementAndGet();
                    exchange.getRequestBody().readAllBytes();
                    Reply reply;
                    synchronized (voice) {
                        reply = voice.replies.pollFirst();
                    }
                    (reply == null ? status(500) : reply).write(exchange);
                });
                server.setExecutor(Executors.newCachedThreadPool());
                server.start();
                return voice;
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        int port() {
            return server.getAddress().getPort();
        }

        int hits() {
            return hits.get();
        }

        synchronized FakeVoice enqueue(Reply reply) {
            replies.addLast(reply);
            return this;
        }

        synchronized void reset() {
            replies.clear();
            hits.set(0);
        }

        void stop() {
            server.stop(0);
        }

        static Reply status(int status) {
            return exchange -> {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            };
        }

        static Reply text(String text) {
            return exchange -> {
                byte[] body = ("{\"text\":\"" + text + "\",\"language\":\"es\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            };
        }

        static Reply delayed(long millis, String text) {
            return exchange -> {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                try {
                    text(text).write(exchange);
                } catch (IOException closed) {
                    // El cliente ya cortó por tiempo agotado.
                }
            };
        }
    }

    @TestConfiguration
    static class PlannedModelConfiguration {

        @Bean
        @Primary
        LanguageModelPort plannedModel() {
            return prompt -> new LanguageModelResponse(PLAN, 1200, 300, "modelo-de-prueba");
        }
    }
}
