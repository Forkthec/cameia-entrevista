package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.domain.port.TranscriptionPort;
import tech.cameia.entrevista.support.ReplicaFixtures;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Respuesta por voz (HU-5.8) contra el servidor completo y PostgreSQL 16 real. La planificación y la transcripción
 * están simuladas y cuentan sus llamadas; los audios son archivos reales generados con ffmpeg
 * ({@code src/test/resources/audio}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
@Import(AudioAnswerEndToEndTest.ScriptedConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AudioAnswerEndToEndTest {

    private static final String BASE = "/api/v1/interviews/sessions";
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final String UID_A = "uid-a";
    private static final String UID_B = "uid-b";
    private static final UUID PROFILE_A = ReplicaFixtures.PROFILE_ID;
    private static final UUID PROFILE_B = UUID.fromString("5d0c1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
    private static final String ROLE = "a0000001-0000-0000-0000-000000000002";
    private static final String TRANSCRIPT = "Lideré un equipo de cuatro personas y resolví un conflicto de plazos.";
    private static final int TEN_MIB = 10 * 1024 * 1024;

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
    private ScriptedModel model;

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
        seedProfile(PROFILE_A, UID_A);
        seedProfile(PROFILE_B, UID_B);
        model.reset();
        transcriber.reset();
    }

    // ---- ayudantes ----

    private void seedProfile(UUID id, String uid) {
        ProfileReplica replica = new ProfileReplica(id, new FirebaseUid(uid), "Analista de datos",
                ReplicatedProfileStatus.COMPLETED, null, ReplicaFixtures.CONTEXT_JSON,
                ReplicaFixtures.ASPIRED_ROLES_JSON, NOW);
        transaction.executeWithoutResult(tx -> replicas.upsertIfNotOlder(replica, NOW));
    }

    private static byte[] audio(String name) {
        try (InputStream in = AudioAnswerEndToEndTest.class.getResourceAsStream("/audio/" + name)) {
            return in.readAllBytes();
        } catch (IOException | NullPointerException missing) {
            throw new IllegalStateException("Falta el audio de prueba " + name, missing);
        }
    }

    private ResponseEntity<String> json(HttpMethod method, String path, String uid, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", uid);
        return client.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    /** Configura e inicia una entrevista y devuelve el identificador de la sesión y el turno 1. */
    private String[] startInterview(String uid, UUID profile, String answerFormat) {
        String body = """
                {"profileId":"%s","targetRoleId":"%s","jobOffer":"Buscamos analista","mode":"SIMULACION",
                 "tone":"CALIDO","answerFormat":"%s","sessionLanguage":"es-CO"}""".formatted(profile, ROLE,
                answerFormat);
        ResponseEntity<String> created = json(HttpMethod.POST, BASE, uid, body);
        String sessionId = JsonPath.read(created.getBody(), "$.sessionId");
        model.enqueue(() -> new LanguageModelResponse(PLAN, 1200, 300, "modelo-de-prueba"));
        ResponseEntity<String> started = json(HttpMethod.POST, BASE + "/" + sessionId + "/start", uid, null);
        assertThat(started.getStatusCode().value()).isEqualTo(200);
        return new String[] {sessionId, JsonPath.read(started.getBody(), "$.pendingQuestion.turnId")};
    }

    private ResponseEntity<String> sendAudio(String uid, String sessionId, String turnId, byte[] audio,
            String filename, String declaredType, String key) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(declaredType));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        if (turnId != null) {
            form.add("turnId", turnId);
        }
        if (audio != null) {
            form.add("audio", new HttpEntity<>(new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }, partHeaders));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (uid != null) {
            headers.set("X-User-Id", uid);
        }
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return client.exchange(BASE + "/" + sessionId + "/answers/audio", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }

    private ResponseEntity<String> sendAudio(String uid, String sessionId, String turnId, byte[] audio, String key) {
        return sendAudio(uid, sessionId, turnId, audio, "respuesta.webm", "audio/webm", key);
    }

    private static String code(ResponseEntity<String> response) {
        return JsonPath.read(response.getBody(), "$.code");
    }

    private static String detail(ResponseEntity<String> response) {
        return JsonPath.read(response.getBody(), "$.detail");
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int answers() {
        return count("SELECT count(*) FROM microentrevista.intento_respuesta");
    }

    private String turnStatus(String turnId) {
        return jdbc.queryForObject("SELECT estado FROM microentrevista.turno WHERE id = ?::uuid", String.class,
                turnId);
    }

    private String sessionStatus(String sessionId) {
        return jdbc.queryForObject("SELECT estado FROM microentrevista.sesion WHERE id = ?::uuid", String.class,
                sessionId);
    }

    /** Afirma que el rechazo no guardó nada, no pidió la transcripción y dejó la pregunta pendiente. */
    private void assertNothingRegistered(String turnId) {
        assertThat(transcriber.calls()).isZero();
        assertThat(answers()).isZero();
        assertThat(turnStatus(turnId)).isEqualTo("PENDING");
    }

    /** Un WebM real rellenado con un elemento Void de EBML hasta {@code size} bytes: sigue siendo un audio válido. */
    private static byte[] paddedWebm(int size) {
        byte[] real = audio("tono-3s.webm");
        byte[] padded = new byte[size];
        System.arraycopy(real, 0, padded, 0, real.length);
        long payload = (long) size - real.length - 9;
        padded[real.length] = (byte) 0xEC;
        padded[real.length + 1] = 0x01;
        for (int i = 0; i < 7; i++) {
            padded[real.length + 2 + i] = (byte) (payload >> (8 * (6 - i)));
        }
        return padded;
    }

    // ---- camino feliz ----

    @Test
    @DisplayName("CA-5.8.5: el audio se transcribe, se registra como AUDIO y avanza a «Pregunta 2 de 5»")
    void submit_shouldRegisterAudioAnswerAndAdvance() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], audio("voz.webm"), "clave-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getBody(), "$.turn.turnId")).isEqualTo(ids[1]);
        assertThat((String) JsonPath.read(response.getBody(), "$.turn.status")).isEqualTo("COMPLETED");
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.currentQuestion")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.totalQuestions")).isEqualTo(5);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.nextQuestion.number")).isEqualTo(2);
        assertThat((String) JsonPath.read(response.getBody(), "$.nextQuestion.text"))
                .isEqualTo("¿Cómo optimizarías una consulta SQL lenta?");
        assertThat((Boolean) JsonPath.read(response.getBody(), "$.interviewFinished")).isFalse();
        assertThat(response.getBody()).doesNotContain(TRANSCRIPT);
        assertThat(transcriber.calls()).isEqualTo(1);
        assertThat(transcriber.lastLanguage()).isEqualTo(InterviewLanguage.SPANISH_COLOMBIA);
        assertThat(jdbc.queryForMap("SELECT origen_respuesta, respuesta_texto, pregunta_mostrada, es_definitiva"
                + " FROM microentrevista.intento_respuesta")).containsEntry("origen_respuesta", "AUDIO")
                .containsEntry("respuesta_texto", TRANSCRIPT).containsEntry("es_definitiva", true)
                .containsEntry("pregunta_mostrada", "Cuéntame brevemente quién eres y por qué te interesa este rol.");
        assertThat(turnStatus(ids[1])).isEqualTo("COMPLETED");
        String next = JsonPath.read(response.getBody(), "$.nextQuestion.turnId");
        assertThat(turnStatus(next)).isEqualTo("PENDING");
        ResponseEntity<String> state = json(HttpMethod.GET, BASE + "/" + ids[0] + "/state", UID_A, null);
        assertThat((String) JsonPath.read(state.getBody(), "$.pendingQuestion.turnId")).isEqualTo(next);
    }

    @Test
    @DisplayName("El MP4 con AAC que graba Safari también se acepta")
    void submit_shouldAcceptSafariMp4() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], audio("voz-safari.mp4"), "respuesta.mp4",
                "audio/mp4", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(transcriber.lastFormat()).isEqualTo("MP4_AAC");
    }

    @Test
    @DisplayName("Tras la quinta respuesta la entrevista queda EVALUANDO, sin pregunta siguiente")
    void submit_shouldFinishAfterFifthAnswer() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        String turn = ids[1];
        ResponseEntity<String> response = null;
        for (int k = 1; k <= 5; k++) {
            response = sendAudio(UID_A, ids[0], turn, audio("tono-3s.webm"), "clave-" + k);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            if (k < 5) {
                turn = JsonPath.read(response.getBody(), "$.nextQuestion.turnId");
            }
        }

        assertThat((Boolean) JsonPath.read(response.getBody(), "$.interviewFinished")).isTrue();
        assertThat((Object) JsonPath.read(response.getBody(), "$.nextQuestion")).isNull();
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.currentQuestion")).isEqualTo(5);
        assertThat(sessionStatus(ids[0])).isEqualTo("EVALUANDO");
        assertThat(answers()).isEqualTo(5);
    }

    // ---- duplicados ----

    @Test
    @DisplayName("CA-5.8.27: reenviar el mismo audio con la misma clave devuelve lo mismo sin transcribir de nuevo")
    void submit_shouldReplaySameKeyWithoutTranscribingAgain() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        byte[] recording = audio("tono-3s.webm");

        ResponseEntity<String> first = sendAudio(UID_A, ids[0], ids[1], recording.clone(), "clave-turno-1");
        ResponseEntity<String> again = sendAudio(UID_A, ids[0], ids[1], recording.clone(), "clave-turno-1");

        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat(again.getBody()).isEqualTo(first.getBody());
        assertThat(transcriber.calls()).isEqualTo(1);
        assertThat(answers()).isEqualTo(1);
    }

    @Test
    @DisplayName("La misma clave con otro audio responde 409 IDEMPOTENCY_KEY_CONFLICT")
    void submit_shouldRejectSameKeyWithOtherAudio() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), "clave-turno-1");

        ResponseEntity<String> other = sendAudio(UID_A, ids[0], ids[1], audio("voz.webm"), "clave-turno-1");

        assertThat(other.getStatusCode().value()).isEqualTo(409);
        assertThat(code(other)).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(transcriber.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-5.8.6: dos envíos simultáneos del mismo turno piden una sola transcripción")
    void submit_shouldTranscribeOnceOnDoubleSubmit() throws Exception {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        transcriber.enqueue(() -> {
            inside.countDown();
            await(release);
            return TRANSCRIPT;
        });
        byte[] recording = audio("tono-3s.webm");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> first = pool.submit(
                    () -> sendAudio(UID_A, ids[0], ids[1], recording.clone(), "clave-doble"));
            assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();
            ResponseEntity<String> second = sendAudio(UID_A, ids[0], ids[1], recording.clone(), "clave-doble");
            release.countDown();

            assertThat(second.getStatusCode().value()).isEqualTo(409);
            assertThat(code(second)).isEqualTo("SESSION_CONFLICT");
            assertThat(first.get(10, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(transcriber.calls()).isEqualTo(1);
        assertThat(answers()).isEqualTo(1);
        // Al reenviar con la misma clave llega el resultado del primer envío.
        ResponseEntity<String> retried = sendAudio(UID_A, ids[0], ids[1], recording.clone(), "clave-doble");
        assertThat(retried.getStatusCode().value()).isEqualTo(200);
        assertThat(transcriber.calls()).isEqualTo(1);
    }

    // ---- límites del audio ----

    @Test
    @DisplayName("CA-5.8.7: 3:00 de audio se acepta")
    void submit_shouldAcceptThreeMinutes() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        assertThat(sendAudio(UID_A, ids[0], ids[1], audio("silencio-180s.webm"), null).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("CA-5.8.8: 3:02 se rechaza con AUDIO_TOO_LONG, sin registrar ni transcribir")
    void submit_shouldRejectOverThreeMinutes() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> webm = sendAudio(UID_A, ids[0], ids[1], audio("silencio-182s.webm"), null);
        ResponseEntity<String> mp4 = sendAudio(UID_A, ids[0], ids[1], audio("silencio-182s-safari.mp4"),
                "respuesta.mp4", "audio/mp4", null);

        for (ResponseEntity<String> response : List.of(webm, mp4)) {
            assertThat(response.getStatusCode().value()).isEqualTo(422);
            assertThat(code(response)).isEqualTo("AUDIO_TOO_LONG");
            assertThat(detail(response)).isEqualTo("Tu respuesta no puede durar más de 3 minutos.");
        }
        assertNothingRegistered(ids[1]);
    }

    @Test
    @DisplayName("Menos de 1,5 s de audio se rechaza con AUDIO_TOO_SHORT sin transcribir")
    void submit_shouldRejectTooShortAudio() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], audio("tono-1s.webm"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(code(response)).isEqualTo("AUDIO_TOO_SHORT");
        assertThat(detail(response)).isEqualTo("No detectamos tu voz, intenta de nuevo.");
        assertNothingRegistered(ids[1]);
    }

    @Test
    @DisplayName("CA-5.8.12: un audio válido de exactamente 10 485 760 bytes se acepta")
    void submit_shouldAcceptExactlyTenMebibytes() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        byte[] tenMib = paddedWebm(TEN_MIB);
        assertThat(tenMib).hasSize(10_485_760);

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], tenMib, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(transcriber.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-5.8.13: 10 485 761 bytes se rechazan con «Tu audio no puede superar los 10 MB.»")
    void submit_shouldRejectOneByteOverTenMebibytes() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], paddedWebm(TEN_MIB + 1), null);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(code(response)).isEqualTo("AUDIO_TOO_LARGE");
        assertThat(detail(response)).isEqualTo("Tu audio no puede superar los 10 MB.");
        assertNothingRegistered(ids[1]);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tono-3s.mp3", "tono-3s.wav", "tono-3s-con-video.webm", "tono-3s-con-video.mp4"})
    @DisplayName("CA-5.8.14: MP3, WAV o un archivo con video se rechazan con 415 y el mensaje de formato")
    void submit_shouldRejectUnsupportedFormats(String name) {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], audio(name), name, "audio/webm", null);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(code(response)).isEqualTo("AUDIO_INVALID_FORMAT");
        assertThat(detail(response))
                .isEqualTo("Este formato de audio no es compatible. Graba de nuevo o responde por texto.");
        assertNothingRegistered(ids[1]);
    }

    @Test
    @DisplayName("CA-5.8.15: un texto o un ejecutable que se presentan como audio/webm se rechazan igual")
    void submit_shouldRejectFakeWebm() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        byte[] executable = new byte[2048];
        executable[0] = 'M';
        executable[1] = 'Z';

        ResponseEntity<String> text = sendAudio(UID_A, ids[0], ids[1],
                "no soy un audio".getBytes(StandardCharsets.UTF_8), null);
        ResponseEntity<String> exe = sendAudio(UID_A, ids[0], ids[1], executable, null);

        for (ResponseEntity<String> response : List.of(text, exe)) {
            assertThat(response.getStatusCode().value()).isEqualTo(415);
            assertThat(code(response)).isEqualTo("AUDIO_INVALID_FORMAT");
            assertThat(detail(response))
                    .isEqualTo("Este formato de audio no es compatible. Graba de nuevo o responde por texto.");
        }
        assertNothingRegistered(ids[1]);
    }

    @Test
    @DisplayName("CA-5.8.16: el nombre del archivo no aparece en la respuesta ni en el registro")
    void submit_shouldIgnoreFilename(CapturedOutput output) {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"),
                "../../respuesta final.webm", "audio/webm", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).doesNotContain("respuesta final").doesNotContain("../");
        assertThat(output.getAll()).contains("Respuesta por voz registrada").doesNotContain("respuesta final")
                .doesNotContain("../../").doesNotContain(TRANSCRIPT);
    }

    @Test
    @DisplayName("CA-5.8.17: no hay ruta para recuperar un audio enviado")
    void submit_shouldNotExposeAudio() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);

        ResponseEntity<String> get = json(HttpMethod.GET, BASE + "/" + ids[0] + "/answers/audio", UID_A, null);
        ResponseEntity<String> byTurn = json(HttpMethod.GET, BASE + "/" + ids[0] + "/answers/audio/" + ids[1], UID_A,
                null);

        assertThat(get.getStatusCode().value()).isEqualTo(405);
        assertThat(byTurn.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA-5.8.18: el audio no queda en la base y sus bytes se sobrescriben al terminar")
    void submit_shouldDiscardAudio() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);

        byte[] seen = transcriber.lastAudio();
        assertThat(seen).isNotEmpty();
        for (byte b : seen) {
            assertThat(b).isZero();
        }
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'microentrevista'"
                + " AND data_type = 'bytea'")).isZero();
    }

    // ---- sesión y turno ----

    @Test
    @DisplayName("CA-5.8.20: una entrevista por texto rechaza el audio con su mensaje, sin transcribir")
    void submit_shouldRejectAudioForTextInterview() {
        String[] ids = startInterview(UID_A, PROFILE_A, "TEXTO");

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(code(response)).isEqualTo("AUDIO_NOT_ALLOWED");
        assertThat(detail(response)).isEqualTo("Esta entrevista es por texto: escribe tu respuesta.");
        assertNothingRegistered(ids[1]);
    }

    @Test
    @DisplayName("CA-5.8.21: una entrevista ajena o inexistente responde 404 sin transcribir")
    void submit_shouldReturn404ForForeignOrMissingSession() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> foreign = sendAudio(UID_B, ids[0], ids[1], audio("tono-3s.webm"), null);
        ResponseEntity<String> missing = sendAudio(UID_A, UUID.randomUUID().toString(), ids[1],
                audio("tono-3s.webm"), null);
        ResponseEntity<String> malformed = sendAudio(UID_A, "abc", ids[1], audio("tono-3s.webm"), null);

        for (ResponseEntity<String> response : List.of(foreign, missing, malformed)) {
            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(code(response)).isEqualTo("SESSION_NOT_FOUND");
            assertThat(detail(response)).isEqualTo("No encontramos lo que buscabas.");
        }
        assertNothingRegistered(ids[1]);
    }

    @Test
    @DisplayName("CA-5.8.28: una entrevista abandonada, en evaluación o finalizada rechaza el audio con su mensaje")
    void submit_shouldRejectClosedSessions() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        jdbc.update("UPDATE microentrevista.sesion SET estado = 'ABANDONADA', fecha_fin = now() WHERE id = ?::uuid",
                ids[0]);
        ResponseEntity<String> abandoned = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);
        jdbc.update("UPDATE microentrevista.sesion SET estado = 'EVALUANDO', fecha_fin = NULL WHERE id = ?::uuid",
                ids[0]);
        ResponseEntity<String> evaluating = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);
        jdbc.update("UPDATE microentrevista.sesion SET estado = 'FINALIZADA', fecha_fin = now() WHERE id = ?::uuid",
                ids[0]);
        ResponseEntity<String> finished = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);

        assertThat(abandoned.getStatusCode().value()).isEqualTo(409);
        assertThat(code(abandoned)).isEqualTo("SESSION_ABANDONED");
        assertThat(detail(abandoned)).isEqualTo("Sesión abandonada: no genera evaluación.");
        for (ResponseEntity<String> response : List.of(evaluating, finished)) {
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(code(response)).isEqualTo("SESSION_ALREADY_CLOSED");
            assertThat(detail(response)).isEqualTo("Esta entrevista ya no admite esa acción.");
        }
        assertNothingRegistered(ids[1]);
    }

    @Test
    @DisplayName("Un turno ya respondido responde 409 y uno que no es de la sesión, 404")
    void submit_shouldRejectAnsweredOrForeignTurn() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);

        ResponseEntity<String> answered = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);
        ResponseEntity<String> unknown = sendAudio(UID_A, ids[0], UUID.randomUUID().toString(),
                audio("tono-3s.webm"), null);
        ResponseEntity<String> missing = sendAudio(UID_A, ids[0], null, audio("tono-3s.webm"), null);

        assertThat(answered.getStatusCode().value()).isEqualTo(409);
        assertThat(code(answered)).isEqualTo("TURN_TRANSITION_NOT_ALLOWED");
        assertThat(unknown.getStatusCode().value()).isEqualTo(404);
        assertThat(code(unknown)).isEqualTo("TURN_NOT_FOUND");
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(transcriber.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin la parte audio responde 422 AUDIO_REQUIRED; sin identidad, 401")
    void submit_shouldRequireAudioAndIdentity() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");

        ResponseEntity<String> noAudio = sendAudio(UID_A, ids[0], ids[1], null, null);
        ResponseEntity<String> noIdentity = sendAudio(null, ids[0], ids[1], audio("tono-3s.webm"), null);

        assertThat(noAudio.getStatusCode().value()).isEqualTo(422);
        assertThat(code(noAudio)).isEqualTo("AUDIO_REQUIRED");
        assertThat(noIdentity.getStatusCode().value()).isEqualTo(401);
        assertNothingRegistered(ids[1]);
    }

    // ---- transcripción ----

    @Test
    @DisplayName("Si la transcripción falla responde 503, la pregunta sigue pendiente y se puede enviar de nuevo")
    void submit_shouldKeepQuestionPendingWhenTranscriptionFails() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        transcriber.enqueue(() -> {
            throw new TranscriptionUnavailableException(new IllegalStateException("caída simulada"));
        });

        ResponseEntity<String> failed = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), "clave-1");

        assertThat(failed.getStatusCode().value()).isEqualTo(503);
        assertThat(code(failed)).isEqualTo("TRANSCRIPTION_UNAVAILABLE");
        assertThat(detail(failed))
                .isEqualTo("No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.");
        assertThat(answers()).isZero();
        assertThat(turnStatus(ids[1])).isEqualTo("RETRYING");
        ResponseEntity<String> state = json(HttpMethod.GET, BASE + "/" + ids[0] + "/state", UID_A, null);
        assertThat((String) JsonPath.read(state.getBody(), "$.pendingQuestion.turnId")).isEqualTo(ids[1]);

        ResponseEntity<String> retried = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), "clave-1");

        assertThat(retried.getStatusCode().value()).isEqualTo(200);
        assertThat(transcriber.calls()).isEqualTo(2);
        assertThat(answers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una transcripción vacía responde 422 TRANSCRIPTION_EMPTY sin registrar respuesta")
    void submit_shouldRejectEmptyTranscription() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        transcriber.enqueue(() -> "   ");

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(code(response)).isEqualTo("TRANSCRIPTION_EMPTY");
        assertThat(detail(response)).isEqualTo("No detectamos tu voz, intenta de nuevo.");
        assertThat(answers()).isZero();
    }

    @Test
    @DisplayName("Una transcripción de más de 4000 caracteres responde 422 TRANSCRIPTION_TOO_LONG")
    void submit_shouldRejectTooLongTranscription() {
        String[] ids = startInterview(UID_A, PROFILE_A, "AUDIO");
        transcriber.enqueue(() -> "a".repeat(4001));

        ResponseEntity<String> response = sendAudio(UID_A, ids[0], ids[1], audio("tono-3s.webm"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(code(response)).isEqualTo("TRANSCRIPTION_TOO_LONG");
        assertThat(answers()).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- dobles ----

    static class ScriptedModel implements LanguageModelPort {

        private final List<Supplier<LanguageModelResponse>> script = new ArrayList<>();

        synchronized void enqueue(Supplier<LanguageModelResponse> next) {
            script.add(next);
        }

        synchronized void reset() {
            script.clear();
        }

        @Override
        public synchronized LanguageModelResponse generate(LanguageModelPrompt prompt) {
            if (script.isEmpty()) {
                throw new IllegalStateException("La prueba no programó una respuesta del modelo");
            }
            return script.remove(0).get();
        }
    }

    /** Transcriptor programado: por defecto responde {@link #TRANSCRIPT}; guarda el último audio que recibió. */
    static class ScriptedTranscriber implements TranscriptionPort {

        private final List<Supplier<String>> script = new ArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<AudioClip> last = new AtomicReference<>();
        private final AtomicReference<InterviewLanguage> language = new AtomicReference<>();

        synchronized void enqueue(Supplier<String> next) {
            script.add(next);
        }

        synchronized void reset() {
            script.clear();
            calls.set(0);
            last.set(null);
        }

        int calls() {
            return calls.get();
        }

        byte[] lastAudio() {
            return last.get().content();
        }

        String lastFormat() {
            return last.get().format().name();
        }

        InterviewLanguage lastLanguage() {
            return language.get();
        }

        @Override
        public Transcription transcribe(AudioClip clip, InterviewLanguage interviewLanguage) {
            calls.incrementAndGet();
            last.set(clip);
            language.set(interviewLanguage);
            Supplier<String> next;
            synchronized (this) {
                next = script.isEmpty() ? () -> TRANSCRIPT : script.remove(0);
            }
            return new Transcription(next.get(), "modelo-de-voz", 100, 20);
        }
    }

    @TestConfiguration
    static class ScriptedConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ScriptedModel scriptedModel() {
            return new ScriptedModel();
        }

        @Bean
        @Primary
        ScriptedTranscriber scriptedTranscriber() {
            return new ScriptedTranscriber();
        }
    }
}
