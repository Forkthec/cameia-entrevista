package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
import java.util.function.Function;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.exception.SpeechTimeoutException;
import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.model.SpeechAudio;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;
import tech.cameia.entrevista.support.ReplicaFixtures;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Escuchar la pregunta (HU-5.15) contra el servidor completo y PostgreSQL 16 real: contrato HTTP, contadores en la
 * base, preguntas mostradas, estados de la sesión, errores de la voz y registros. La planificación y la voz están
 * simuladas; ninguna prueba llama a Gemini ni a Google Cloud Text-to-Speech.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
@Import(QuestionAudioEndToEndTest.ScriptedConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class QuestionAudioEndToEndTest {

    private static final String BASE = "/api/v1/interviews/sessions";
    private static final Instant NOW = Instant.parse("2026-11-15T15:00:00Z");
    private static final String UID_A = "uid-a";
    private static final String UID_B = "uid-b";
    private static final UUID PROFILE_A = ReplicaFixtures.PROFILE_ID;
    private static final UUID PROFILE_B = UUID.fromString("5d0c1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
    private static final String ROLE = "a0000001-0000-0000-0000-000000000002";
    private static final byte[] MP3 = mp3();
    private static final String SECRET_WORDS = "consulta SQL lenta";

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
    private AudioAnswerEndToEndTest.ScriptedModel model;

    @Autowired
    private ScriptedVoice voice;

    @BeforeEach
    void prepare() {
        jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia, "
                + "microentrevista.contexto_profesional_replica, microentrevista.rol_profesional, "
                + "microentrevista.consumo_voz_mensual CASCADE");
        jdbc.update("""
                INSERT INTO microentrevista.rol_profesional (id, nombre_es, nombre_en, estado, actualizado_en,
                    fecha_replicacion)
                VALUES (?::uuid, 'Rol', 'Role', 'ACTIVE', now(), now())
                """, ROLE);
        seedProfile(PROFILE_A, UID_A);
        seedProfile(PROFILE_B, UID_B);
        model.reset();
        voice.reset();
    }

    // ---- ayudantes ----

    private void seedProfile(UUID id, String uid) {
        ProfileReplica replica = new ProfileReplica(id, new FirebaseUid(uid), "Analista de datos",
                ReplicatedProfileStatus.COMPLETED, null, ReplicaFixtures.CONTEXT_JSON,
                ReplicaFixtures.ASPIRED_ROLES_JSON, NOW);
        transaction.executeWithoutResult(tx -> replicas.upsertIfNotOlder(replica, NOW));
    }

    private static byte[] mp3() {
        byte[] bytes = new byte[4 + 480];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFB;
        bytes[2] = (byte) 0x90;
        return bytes;
    }

    private ResponseEntity<String> json(HttpMethod method, String path, String uid, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", uid);
        return client.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    /** Configura e inicia una entrevista con tono estricto y devuelve el identificador de la sesión y el turno 1. */
    private String[] startInterview(String uid, UUID profile) {
        String body = """
                {"profileId":"%s","targetRoleId":"%s","jobOffer":"Buscamos analista","mode":"SIMULACION",
                 "tone":"ESTRICTO","answerFormat":"TEXTO","sessionLanguage":"es-CO"}""".formatted(profile, ROLE);
        ResponseEntity<String> created = json(HttpMethod.POST, BASE, uid, body);
        String sessionId = JsonPath.read(created.getBody(), "$.sessionId");
        model.enqueue(() -> new LanguageModelResponse(PLAN, 1200, 300, "modelo-de-prueba"));
        ResponseEntity<String> started = json(HttpMethod.POST, BASE + "/" + sessionId + "/start", uid, null);
        assertThat(started.getStatusCode().value()).isEqualTo(200);
        return new String[] {sessionId, JsonPath.read(started.getBody(), "$.pendingQuestion.turnId")};
    }

    private void answerText(String uid, String sessionId, String turnId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", uid);
        headers.set("Idempotency-Key", "respuesta-" + turnId);
        ResponseEntity<String> answered = client.exchange(BASE + "/" + sessionId + "/answers", HttpMethod.POST,
                new HttpEntity<>("""
                        {"turnId":"%s","answerText":"Usé índices y revisé el plan de ejecución."}"""
                        .formatted(turnId), headers), String.class);
        assertThat(answered.getStatusCode().value()).as(answered.getBody()).isEqualTo(200);
    }

    private ResponseEntity<byte[]> listen(String uid, String sessionId, String number) {
        return listen(uid, sessionId, number, "audio/mpeg");
    }

    private ResponseEntity<byte[]> listen(String uid, String sessionId, String number, String accept) {
        HttpHeaders headers = new HttpHeaders();
        if (uid != null) {
            headers.set("X-User-Id", uid);
        }
        headers.set(HttpHeaders.ACCEPT, accept);
        URI uri = URI.create(client.getRootUri() + BASE + "/" + sessionId + "/questions/" + number + "/audio");
        return client.exchange(uri, HttpMethod.POST, new HttpEntity<>(headers), byte[].class);
    }

    private static String code(ResponseEntity<byte[]> response) {
        return JsonPath.read(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8), "$.code");
    }

    private static String detail(ResponseEntity<byte[]> response) {
        return JsonPath.read(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8), "$.detail");
    }

    private int plays(String sessionId, int position) {
        return jdbc.queryForObject("""
                SELECT coalesce((SELECT u.usos FROM microentrevista.uso_audio_pregunta u
                  JOIN microentrevista.pregunta p ON p.id = u.pregunta_id
                  WHERE p.sesion_id = ?::uuid AND p.orden = ?), 0)""", Integer.class, sessionId, position);
    }

    private long monthCharacters() {
        return jdbc.queryForObject("SELECT coalesce(sum(caracteres), 0) FROM microentrevista.consumo_voz_mensual",
                Long.class);
    }

    private ResponseEntity<String> state(String uid, String sessionId) {
        return json(HttpMethod.GET, BASE + "/" + sessionId + "/state", uid, null);
    }

    private void setSessionStatus(String sessionId, String status) {
        jdbc.update("""
                UPDATE microentrevista.sesion SET estado = ?,
                    fecha_fin = CASE WHEN ? IN ('ABANDONADA', 'FINALIZADA') THEN now() END
                WHERE id = ?::uuid""", status, status, sessionId);
    }

    /** Última actividad y versión de la sesión, y estado y versión del turno. */
    private String snapshot(String sessionId, String turnId) {
        return jdbc.queryForObject("""
                SELECT coalesce(fecha_ultima_actividad::text, '-') || '|' || version FROM microentrevista.sesion
                WHERE id = ?::uuid""", String.class, sessionId)
                + jdbc.queryForObject("SELECT '|' || estado || '|' || version FROM microentrevista.turno"
                        + " WHERE id = ?::uuid", String.class, turnId);
    }

    // ---- estado (CA-5.15.1, CA-5.15.9) ----

    @Test
    @DisplayName("CA-5.15.1: el estado ofrece la voz y cada pregunta nueva empieza con 3 usos")
    void state_shouldOfferAudioWithThreePlays() {
        String[] ids = startInterview(UID_A, PROFILE_A);

        ResponseEntity<String> state = state(UID_A, ids[0]);

        assertThat((Boolean) JsonPath.read(state.getBody(), "$.audioEnabled")).isTrue();
        assertThat((Integer) JsonPath.read(state.getBody(), "$.pendingQuestion.audioPlaysRemaining")).isEqualTo(3);
    }

    @Test
    @DisplayName("CA-5.15.1: tras escuchar, el estado muestra los usos restantes de la pendiente y de las anteriores")
    void state_shouldShowRemainingPlaysOfShownQuestions() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        assertThat(listen(UID_A, ids[0], "1").getStatusCode().value()).isEqualTo(200);
        answerText(UID_A, ids[0], ids[1]);

        ResponseEntity<String> state = state(UID_A, ids[0]);

        assertThat((Integer) JsonPath.read(state.getBody(), "$.pendingQuestion.number")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(state.getBody(), "$.pendingQuestion.audioPlaysRemaining")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(state.getBody(), "$.history[0].audioPlaysRemaining")).isEqualTo(2);
    }

    @Test
    @DisplayName("CA-5.15.1: los usos de una persona no afectan a los de otra")
    void listen_shouldKeepCountersPerSession() {
        String[] mine = startInterview(UID_A, PROFILE_A);
        String[] theirs = startInterview(UID_B, PROFILE_B);
        listen(UID_A, mine[0], "1");
        listen(UID_A, mine[0], "1");

        assertThat(plays(mine[0], 1)).isEqualTo(2);
        assertThat((Integer) JsonPath.read(state(UID_B, theirs[0]).getBody(),
                "$.pendingQuestion.audioPlaysRemaining")).isEqualTo(3);
    }

    // ---- camino feliz (CA-5.15.2) ----

    @Test
    @DisplayName("CA-5.15.2: responde audio/mpeg con su largo, no-store y nosniff, y no cambia la entrevista")
    void listen_shouldReturnMp3WithoutChangingTheInterview() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        String before = snapshot(ids[0], ids[1]);

        ResponseEntity<byte[]> response = listen(UID_A, ids[0], "1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // spring.servlet.encoding.force-response agrega ;charset=UTF-8 a toda respuesta; el navegador lo ignora.
        assertThat(response.getHeaders().getContentType().getType()).isEqualTo("audio");
        assertThat(response.getHeaders().getContentType().getSubtype()).isEqualTo("mpeg");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(MP3.length);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getBody()).isEqualTo(MP3);
        assertThat(plays(ids[0], 1)).isEqualTo(1);
        assertThat(monthCharacters()).isEqualTo(
                "Cuéntame brevemente quién eres y por qué te interesa este rol.".length());
        // CA-5.15.14 y CA-5.15.18: la última actividad, la sesión y el turno quedan iguales.
        assertThat(snapshot(ids[0], ids[1])).isEqualTo(before);
    }

    @Test
    @DisplayName("CA-5.15.3: la voz recibe el texto guardado de la pregunta, sin alterar, y el idioma de la entrevista")
    void listen_shouldSendStoredQuestionAndLanguage() {
        String[] ids = startInterview(UID_A, PROFILE_A);

        listen(UID_A, ids[0], "1");

        assertThat(voice.lastText.get()).isEqualTo("Cuéntame brevemente quién eres y por qué te interesa este rol.");
        assertThat(voice.lastLanguage.get()).isEqualTo(InterviewLanguage.SPANISH_COLOMBIA);
    }

    // ---- límites ----

    @Test
    @DisplayName("CA-5.15.6: la tercera se entrega y la cuarta responde 409 QUESTION_AUDIO_LIMIT_REACHED")
    void listen_shouldStopAfterThreePlays() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        for (int i = 0; i < 3; i++) {
            assertThat(listen(UID_A, ids[0], "1").getStatusCode().value()).isEqualTo(200);
        }

        ResponseEntity<byte[]> fourth = listen(UID_A, ids[0], "1");

        assertThat(fourth.getStatusCode().value()).isEqualTo(409);
        assertThat(fourth.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(code(fourth)).isEqualTo("QUESTION_AUDIO_LIMIT_REACHED");
        assertThat(detail(fourth))
                .isEqualTo("Ya escuchaste esta pregunta el máximo de veces. Puedes leerla en pantalla.");
        assertThat(voice.calls.get()).isEqualTo(3);
        assertThat(plays(ids[0], 1)).isEqualTo(3);
    }

    @Test
    @DisplayName("CA-5.15.7: con un uso restante, dos peticiones simultáneas dan un 200 y un 409; el contador queda en 3")
    void listen_shouldAllowOnlyOne_whenTwoRequestsRace() throws Exception {
        String[] ids = startInterview(UID_A, PROFILE_A);
        listen(UID_A, ids[0], "1");
        listen(UID_A, ids[0], "1");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return listen(UID_A, ids[0], "1").getStatusCode().value();
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(plays(ids[0], 1)).isEqualTo(3);
    }

    @Test
    @DisplayName("CA-5.15.28: con 900.000 caracteres en el mes responde 503; con 899.999 todavía se entrega")
    void listen_shouldRespectMonthlyCap() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        jdbc.update("INSERT INTO microentrevista.consumo_voz_mensual (mes, caracteres) VALUES ('2026-11-01', 900000)");

        ResponseEntity<byte[]> capped = listen(UID_A, ids[0], "1");

        assertThat(capped.getStatusCode().value()).isEqualTo(503);
        assertThat(code(capped)).isEqualTo("SPEECH_MONTHLY_LIMIT_REACHED");
        assertThat(detail(capped)).isEqualTo("La voz no está disponible por ahora. Puedes leer la pregunta.");
        assertThat(voice.calls.get()).isZero();
        assertThat(plays(ids[0], 1)).isZero();

        jdbc.update("UPDATE microentrevista.consumo_voz_mensual SET caracteres = 899999");
        assertThat(listen(UID_A, ids[0], "1").getStatusCode().value()).isEqualTo(200);
        assertThat(monthCharacters()).isGreaterThan(899_999L);
    }

    // ---- preguntas y números ----

    @Test
    @DisplayName("CA-5.15.5: una pregunta futura responde 409 QUESTION_NOT_AVAILABLE sin llamar a la voz")
    void listen_shouldRejectFutureQuestion() {
        String[] ids = startInterview(UID_A, PROFILE_A);

        ResponseEntity<byte[]> response = listen(UID_A, ids[0], "2");

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("QUESTION_NOT_AVAILABLE");
        assertThat(detail(response)).isEqualTo("Esa pregunta todavía no está disponible.");
        assertThat(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain(SECRET_WORDS);
        assertThat(voice.calls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "6", "-1", "a", "%202", "02", "+2", "2.0", "2e0", "%D9%A2", "99999999999999999999",
        "%252"})
    @DisplayName("CA-5.15.12: un número que no es 1 a 5 responde 404 QUESTION_NOT_FOUND, nunca 500")
    void listen_shouldRejectInvalidNumbers(String number) {
        String[] ids = startInterview(UID_A, PROFILE_A);

        ResponseEntity<byte[]> response = listen(UID_A, ids[0], number);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(code(response)).isEqualTo("QUESTION_NOT_FOUND");
        assertThat(detail(response)).isEqualTo("No encontramos lo que buscabas.");
        assertThat(voice.calls.get()).isZero();
        assertThat(plays(ids[0], 1)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"%00, 400, REQUEST_PATH_INVALID_FORMAT", "'', 404, ROUTE_NOT_FOUND"})
    @DisplayName("CA-5.15.12: un número vacío o con %00 lo rechaza el servidor antes del caso de uso, sin el error"
            + " genérico, sin voz y sin gastar usos")
    void listen_shouldRejectEmptyOrNullNumberBeforeTheUseCase(String number, int status, String errorCode) {
        String[] ids = startInterview(UID_A, PROFILE_A);

        ResponseEntity<byte[]> response = listen(UID_A, ids[0], number);

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(code(response)).isEqualTo(errorCode).isNotEqualTo("INTERNAL_ERROR");
        assertThat(voice.calls.get()).isZero();
        assertThat(plays(ids[0], 1)).isZero();
    }


    @ParameterizedTest
    @ValueSource(strings = {"foreign", "00000000-0000-0000-0000-000000000000", "3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a1",
        "3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a100", "%7B3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10%7D",
        "%27%20OR%201%3D1%20--", "%2e%2e"})
    @DisplayName("CA-5.15.11: una entrevista ajena, inexistente o mal escrita responde el mismo 404 SESSION_NOT_FOUND")
    void listen_shouldHideForeignOrMissingSessions(String sessionId) {
        String[] theirs = startInterview(UID_B, PROFILE_B);
        String target = "foreign".equals(sessionId) ? theirs[0] : sessionId;

        ResponseEntity<byte[]> response = listen(UID_A, target, "1");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(code(response)).isEqualTo("SESSION_NOT_FOUND");
        assertThat(detail(response)).isEqualTo("No encontramos lo que buscabas.");
        assertThat(voice.calls.get()).isZero();
    }

    @Test
    @DisplayName("Sin identidad responde 401 IDENTITY_REQUIRED")
    void listen_shouldRequireIdentity() {
        String[] ids = startInterview(UID_A, PROFILE_A);

        ResponseEntity<byte[]> response = listen(null, ids[0], "1");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(code(response)).isEqualTo("IDENTITY_REQUIRED");
    }

    @Test
    @DisplayName("Un GET o un Accept que no admite audio responden 405 y 406 en el formato común")
    void listen_shouldRejectWrongMethodOrAccept() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", UID_A);

        ResponseEntity<String> get = client.exchange(BASE + "/" + ids[0] + "/questions/1/audio", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        ResponseEntity<byte[]> json = listen(UID_A, ids[0], "1", "application/json");

        assertThat(get.getStatusCode().value()).isEqualTo(405);
        assertThat(json.getStatusCode().value()).isEqualTo(406);
        assertThat(voice.calls.get()).isZero();
    }

    // ---- estados de la sesión (CA-5.15.10, CA-5.15.18, CA-5.15.25) ----

    @Test
    @DisplayName("CA-5.15.10: abandonada, evaluando o configurada responden su 409 sin llamar a la voz")
    void listen_shouldRejectSessionsNotInProgress() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        setSessionStatus(ids[0], "ABANDONADA");
        assertThat(code(listen(UID_A, ids[0], "1"))).isEqualTo("SESSION_ABANDONED");
        setSessionStatus(ids[0], "EVALUANDO");
        assertThat(code(listen(UID_A, ids[0], "1"))).isEqualTo("SESSION_ALREADY_CLOSED");
        assertThat((Boolean) JsonPath.read(state(UID_A, ids[0]).getBody(), "$.audioEnabled")).isFalse();

        String configured = JsonPath.read(json(HttpMethod.POST, BASE, UID_B, """
                {"profileId":"%s","targetRoleId":"%s","mode":"SIMULACION","tone":"CALIDO","answerFormat":"TEXTO",
                 "sessionLanguage":"es-CO"}""".formatted(PROFILE_B, ROLE)).getBody(), "$.sessionId");
        ResponseEntity<byte[]> notStarted = listen(UID_B, configured, "1");
        assertThat(notStarted.getStatusCode().value()).isEqualTo(409);
        assertThat(code(notStarted)).isEqualTo("SESSION_TRANSITION_NOT_ALLOWED");
        assertThat(voice.calls.get()).isZero();
    }

    @Test
    @DisplayName("CA-5.15.18: con el turno en reintento se puede escuchar y el turno no cambia")
    void listen_shouldWork_whenTurnIsRetrying() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        jdbc.update("UPDATE microentrevista.turno SET estado = 'RETRYING' WHERE id = ?::uuid", ids[1]);

        assertThat(listen(UID_A, ids[0], "1").getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT estado FROM microentrevista.turno WHERE id = ?::uuid", String.class,
                ids[1])).isEqualTo("RETRYING");
    }

    @Test
    @DisplayName("CA-5.15.25: si la entrevista se abandona mientras se genera, responde 409 sin audio y el uso queda")
    void listen_shouldNotDeliver_whenSessionIsAbandonedDuringSynthesis() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        voice.behavior.set(text -> {
            setSessionStatus(ids[0], "ABANDONADA");
            return new SpeechAudio(MP3, "audio/mpeg", "voz");
        });

        ResponseEntity<byte[]> response = listen(UID_A, ids[0], "1");

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("SESSION_ABANDONED");
        assertThat(detail(response)).isEqualTo("Sesión abandonada: no genera evaluación.");
        assertThat(plays(ids[0], 1)).isEqualTo(1);
    }

    // ---- fallos de la voz (CA-5.15.8, CA-5.15.21, CA-5.15.23) ----

    @Test
    @DisplayName("CA-5.15.8: si la voz falla o tarda, responde 503 con su código y no descuenta el uso")
    void listen_shouldNotCount_whenSpeechFails() {
        String[] ids = startInterview(UID_A, PROFILE_A);
        voice.behavior.set(text -> {
            throw new SpeechUnavailableException("status=500", null);
        });
        ResponseEntity<byte[]> failed = listen(UID_A, ids[0], "1");
        voice.behavior.set(text -> {
            throw new SpeechTimeoutException("tiempo agotado", null);
        });
        ResponseEntity<byte[]> late = listen(UID_A, ids[0], "1");

        assertThat(failed.getStatusCode().value()).isEqualTo(503);
        assertThat(code(failed)).isEqualTo("SPEECH_UNAVAILABLE");
        assertThat(detail(failed)).isEqualTo("No pudimos reproducir la pregunta. Inténtalo de nuevo o léela en pantalla.");
        assertThat(late.getStatusCode().value()).isEqualTo(503);
        assertThat(code(late)).isEqualTo("SPEECH_TIMEOUT");
        assertThat(detail(late)).isEqualTo("La voz tardó demasiado. Inténtalo de nuevo o léela en pantalla.");
        assertThat(plays(ids[0], 1)).isZero();
        assertThat(monthCharacters()).isZero();
        // La entrevista sigue respondible.
        assertThat((String) JsonPath.read(state(UID_A, ids[0]).getBody(), "$.pendingQuestion.status"))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("CA-5.15.15: ni en éxito ni en fallo los registros llevan el texto de la pregunta o el audio")
    void listen_shouldNotLogQuestionText(CapturedOutput output) {
        String[] ids = startInterview(UID_A, PROFILE_A);
        answerText(UID_A, ids[0], ids[1]);
        listen(UID_A, ids[0], "2");
        voice.behavior.set(text -> {
            throw new SpeechUnavailableException("status=500", null);
        });
        listen(UID_A, ids[0], "2");
        voice.behavior.set(text -> {
            throw new SpeechTimeoutException("tiempo agotado", null);
        });
        listen(UID_A, ids[0], "2");

        assertThat(output).contains("Voz de la pregunta generada").contains("SPEECH_TIMEOUT")
                .doesNotContain(SECRET_WORDS).doesNotContain("//uQ");
    }

    // ---- dobles ----

    /** Voz programada; cuenta las llamadas y guarda lo último que recibió. */
    static class ScriptedVoice implements SpeechSynthesisPort {

        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> lastText = new AtomicReference<>();
        final AtomicReference<InterviewLanguage> lastLanguage = new AtomicReference<>();
        final AtomicReference<Function<String, SpeechAudio>> behavior = new AtomicReference<>();

        void reset() {
            calls.set(0);
            lastText.set(null);
            lastLanguage.set(null);
            behavior.set(text -> new SpeechAudio(MP3, "audio/mpeg", "es-US-Chirp3-HD-Kore"));
        }

        @Override
        public SpeechAudio synthesize(String text, InterviewLanguage language) {
            calls.incrementAndGet();
            lastText.set(text);
            lastLanguage.set(language);
            return behavior.get().apply(text);
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
        AudioAnswerEndToEndTest.ScriptedModel scriptedModel() {
            return new AudioAnswerEndToEndTest.ScriptedModel();
        }

        /** La voz simulada reemplaza a Google Cloud Text-to-Speech. */
        @Bean
        @Primary
        ScriptedVoice scriptedVoice() {
            return new ScriptedVoice();
        }
    }
}
