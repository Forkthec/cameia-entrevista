package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioAnswer;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.domain.port.SpeechTranscriptionPort;
import tech.cameia.entrevista.support.ReplicaFixtures;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Responder por voz (HU-5.12, CM-349) contra el servidor completo y PostgreSQL 16 real: crear e iniciar una
 * entrevista por voz, responder las 5 preguntas con audio y comprobar lo que queda en la base. El modelo de
 * lenguaje y el servicio de transcripción son simulados: ninguna prueba usa un proveedor real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
@Import(VoiceAnswerEndToEndTest.FakesConfiguration.class)
class VoiceAnswerEndToEndTest {

    private static final String BASE = "/api/v1/interviews/sessions";
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final String UID = "uid-voz";
    private static final String ROLE = "a0000001-0000-0000-0000-000000000002";
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

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProfileReplicaRepository replicas;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private ScriptedTranscriber transcriber;

    @BeforeEach
    void prepare() {
        jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia, "
                + "microentrevista.contexto_profesional_replica, microentrevista.rol_profesional CASCADE");
        jdbc.update("""
                INSERT INTO microentrevista.rol_profesional (id, nombre_es, nombre_en, estado, actualizado_en,
                    fecha_replicacion)
                VALUES (?::uuid, 'Rol', 'Role', 'ACTIVE', now(), now())
                """, ROLE);
        ProfileReplica replica = new ProfileReplica(ReplicaFixtures.PROFILE_ID, new FirebaseUid(UID),
                "Analista de datos", ReplicatedProfileStatus.COMPLETED, null, ReplicaFixtures.CONTEXT_JSON,
                ReplicaFixtures.ASPIRED_ROLES_JSON, NOW);
        transaction.executeWithoutResult(tx -> replicas.upsertIfNotOlder(replica, NOW));
        transcriber.reset();
    }

    private String startVoiceInterview(String answerFormat) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", UID);
        String body = """
                {"profileId":"%s","targetRoleId":"%s","jobOffer":"Buscamos analista","mode":"SIMULACION",
                 "tone":"CALIDO","answerFormat":"%s","sessionLanguage":"es-CO"}"""
                .formatted(ReplicaFixtures.PROFILE_ID, ROLE, answerFormat);
        String sessionId = JsonPath.read(client.exchange(BASE, HttpMethod.POST, new HttpEntity<>(body, headers),
                String.class).getBody(), "$.sessionId");
        ResponseEntity<String> started = client.exchange(BASE + "/" + sessionId + "/start", HttpMethod.POST,
                new HttpEntity<>(null, headers), String.class);
        assertThat(started.getStatusCode().value()).isEqualTo(200);
        return sessionId;
    }

    private String pendingTurn(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", UID);
        return JsonPath.read(client.exchange(BASE + "/" + sessionId + "/state", HttpMethod.GET,
                new HttpEntity<>(null, headers), String.class).getBody(), "$.pendingQuestion.turnId");
    }

    /** Audio real (WebM con Opus, con voz): desde HU-5.8 el formato y la duración se leen del contenido. */
    private static byte[] realAudio() {
        try (var in = VoiceAnswerEndToEndTest.class.getResourceAsStream("/audio/voz.webm")) {
            return in.readAllBytes();
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    private ResponseEntity<String> answer(String sessionId, String turnId, byte[] audio, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Id", UID);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        HttpHeaders audioHeaders = new HttpHeaders();
        audioHeaders.setContentType(MediaType.parseMediaType("audio/webm;codecs=opus"));
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("turnId", turnId);
        parts.add("audio", new HttpEntity<>(new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "respuesta.webm";
            }
        }, audioHeaders));
        return client.exchange(BASE + "/" + sessionId + "/answers/audio", HttpMethod.POST,
                new HttpEntity<>(parts, headers), String.class);
    }

    private int count(String sql, String sessionId) {
        return jdbc.queryForObject(sql, Integer.class, UUID.fromString(sessionId));
    }

    @Test
    void answeringFiveQuestionsByVoice_shouldAdvanceToTheEndAndKeepOnlyTheText() {
        String sessionId = startVoiceInterview("AUDIO");
        String turnId = pendingTurn(sessionId);

        for (int question = 1; question <= 5; question++) {
            int number = question;
            transcriber.enqueue(() -> new Transcription("Respuesta hablada número " + number, "es", 0.97));
            ResponseEntity<String> response = answer(sessionId, turnId, realAudio(), null);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).doesNotContainIgnoringCase("feedback");
            if (question < 5) {
                assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.currentQuestion"))
                        .isEqualTo(question + 1);
                assertThat((Integer) JsonPath.read(response.getBody(), "$.nextQuestion.number"))
                        .isEqualTo(question + 1);
                assertThat((Boolean) JsonPath.read(response.getBody(), "$.interviewFinished")).isFalse();
                turnId = JsonPath.read(response.getBody(), "$.nextQuestion.turnId");
            } else {
                assertThat(response.getBody()).contains("\"nextQuestion\":null");
                assertThat((Boolean) JsonPath.read(response.getBody(), "$.interviewFinished")).isTrue();
            }
        }

        assertThat(count("""
                SELECT count(*) FROM microentrevista.intento_respuesta ir
                  JOIN microentrevista.turno t ON t.id = ir.turno_id
                  JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
                 WHERE p.sesion_id = ? AND ir.origen_respuesta = 'AUDIO' AND ir.idioma_detectado = 'es'
                """, sessionId)).isEqualTo(5);
        assertThat(count("""
                SELECT count(*) FROM microentrevista.ejecucion_ia
                 WHERE sesion_id = ? AND tipo_operacion = 'TRANSCRIBIR' AND estado = 'EXITOSA' AND numero_intento = 1
                """, sessionId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT estado FROM microentrevista.sesion WHERE id = ?", String.class,
                UUID.fromString(sessionId))).isEqualTo("EN_CURSO");
    }

    @Test
    void emptyTranscription_shouldNotRegisterAndKeepTheSameQuestion() {
        String sessionId = startVoiceInterview("AUDIO");
        String turnId = pendingTurn(sessionId);
        transcriber.enqueue(() -> new Transcription("   ", "es", 0.5));

        ResponseEntity<String> response = answer(sessionId, turnId, realAudio(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getBody(), "$.code")).isEqualTo("TRANSCRIPTION_EMPTY");
        assertThat(pendingTurn(sessionId)).isEqualTo(turnId);
        assertThat(count("""
                SELECT count(*) FROM microentrevista.ejecucion_ia
                 WHERE sesion_id = ? AND tipo_operacion = 'TRANSCRIBIR'
                """, sessionId)).isEqualTo(1);
    }

    @Test
    void transcriptionFailure_shouldReturn503AndKeepTheSameQuestion() {
        String sessionId = startVoiceInterview("AUDIO");
        String turnId = pendingTurn(sessionId);
        transcriber.enqueue(() -> {
            throw new TranscriptionUnavailableException("simulado", null);
        });

        ResponseEntity<String> response = answer(sessionId, turnId, realAudio(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat((String) JsonPath.read(response.getBody(), "$.code")).isEqualTo("TRANSCRIPTION_UNAVAILABLE");
        assertThat(pendingTurn(sessionId)).isEqualTo(turnId);
        assertThat(count("""
                SELECT count(*) FROM microentrevista.ejecucion_ia
                 WHERE sesion_id = ? AND tipo_operacion = 'TRANSCRIBIR' AND estado = 'FALLIDA'
                   AND error_codigo = 'TRANSCRIPTION_UNAVAILABLE'
                """, sessionId)).isEqualTo(1);
    }

    @Test
    void sameKeyAndSameAudio_shouldReplayTheResponseWithoutTranscribingAgain() {
        String sessionId = startVoiceInterview("AUDIO");
        String turnId = pendingTurn(sessionId);
        transcriber.enqueue(() -> new Transcription("Mi primera respuesta", "es", 0.97));

        ResponseEntity<String> first = answer(sessionId, turnId, realAudio(), "clave-voz-1");
        ResponseEntity<String> again = answer(sessionId, turnId, realAudio(), "clave-voz-1");

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(again.getBody(), "$.nextQuestion.turnId"))
                .isEqualTo(JsonPath.read(first.getBody(), "$.nextQuestion.turnId"));
        assertThat(transcriber.calls()).isEqualTo(1);
    }

    @Test
    void audioForTextInterview_shouldBeRejectedWithoutTranscribing() {
        String sessionId = startVoiceInterview("TEXTO");

        ResponseEntity<String> response = answer(sessionId, pendingTurn(sessionId), realAudio(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getBody(), "$.code")).isEqualTo("AUDIO_NOT_ALLOWED");
        assertThat(transcriber.calls()).isZero();
    }

    /** Transcriptor que responde lo que se le encola y cuenta las llamadas. */
    static class ScriptedTranscriber implements SpeechTranscriptionPort {

        private final Deque<Supplier<Transcription>> script = new ArrayDeque<>();
        private final AtomicInteger calls = new AtomicInteger();

        synchronized void enqueue(Supplier<Transcription> next) {
            script.add(next);
        }

        synchronized void reset() {
            script.clear();
            calls.set(0);
        }

        int calls() {
            return calls.get();
        }

        @Override
        public synchronized Transcription transcribe(AudioAnswer audio, InterviewLanguage expected) {
            calls.incrementAndGet();
            Supplier<Transcription> next = script.poll();
            if (next == null) {
                throw new TranscriptionUnavailableException("sin guion", null);
            }
            return next.get();
        }

        @Override
        public String provider() {
            return "simulado";
        }

        @Override
        public String model() {
            return "modelo-simulado";
        }
    }

    @TestConfiguration
    static class FakesConfiguration {

        @Bean
        @Primary
        ScriptedTranscriber scriptedTranscriber() {
            return new ScriptedTranscriber();
        }

        @Bean
        @Primary
        LanguageModelPort fixedPlanModel() {
            return prompt -> new LanguageModelResponse(PLAN, 100, 200, "modelo-de-prueba");
        }
    }
}
