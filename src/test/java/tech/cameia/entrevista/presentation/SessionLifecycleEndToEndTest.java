package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.support.ReplicaFixtures;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Iniciar, consultar y cancelar la sesión contra el servidor completo y PostgreSQL 16 real, con un modelo de lenguaje
 * simulado que cuenta las llamadas. Ninguna prueba usa el proveedor real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
@Import(SessionLifecycleEndToEndTest.ScriptedModelConfiguration.class)
class SessionLifecycleEndToEndTest {

    private static final String BASE = "/api/v1/interviews/sessions";
    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final String UID_A = "uid-a";
    private static final String UID_B = "uid-b";
    private static final UUID PROFILE_A = ReplicaFixtures.PROFILE_ID;
    private static final UUID PROFILE_B = UUID.fromString("5d0c1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
    private static final String ROLE = "a0000001-0000-0000-0000-000000000002";

    private static final String VALID_PLAN = """
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

    @BeforeEach
    void prepare() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_fallo_pregunta ON microentrevista.pregunta");
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
    }

    // ---- ayudantes ----

    private void seedProfile(UUID id, String uid) {
        ProfileReplica replica = new ProfileReplica(id, new FirebaseUid(uid), "Analista de datos",
                ReplicatedProfileStatus.COMPLETED, null, ReplicaFixtures.CONTEXT_JSON,
                ReplicaFixtures.ASPIRED_ROLES_JSON, NOW);
        transaction.executeWithoutResult(tx -> replicas.upsertIfNotOlder(replica, NOW));
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String uid, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (uid != null) {
            headers.set("X-User-Id", uid);
        }
        return client.exchange(path, method, new HttpEntity<>(json, headers), String.class);
    }

    /** Crea una sesión configurada de la persona por el endpoint real y devuelve su identificador. */
    private String configure(String uid, UUID profile) {
        String json = """
                {"profileId":"%s","targetRoleId":"%s","jobOffer":"Buscamos analista","mode":"SIMULACION",
                 "tone":"CALIDO","answerFormat":"TEXTO","sessionLanguage":"es-CO"}""".formatted(profile, ROLE);
        ResponseEntity<String> created = call(HttpMethod.POST, BASE, uid, json);
        assertThat(created.getStatusCode().value()).isIn(200, 201);
        return JsonPath.read(created.getBody(), "$.sessionId");
    }

    private ResponseEntity<String> start(String uid, String id) {
        return call(HttpMethod.POST, BASE + "/" + id + "/start", uid, null);
    }

    private ResponseEntity<String> state(String uid, String id) {
        return call(HttpMethod.GET, BASE + "/" + id + "/state", uid, null);
    }

    private String code(ResponseEntity<String> response) {
        return JsonPath.read(response.getBody(), "$.code");
    }

    private String statusOf(String id) {
        return jdbc.queryForObject("SELECT estado FROM microentrevista.sesion WHERE id = ?::uuid", String.class, id);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int retriesOf(String id) {
        return count("SELECT reintentos_planificacion FROM microentrevista.sesion WHERE id = ?::uuid", id);
    }

    private int executions(String id, String status) {
        return count("SELECT count(*) FROM microentrevista.ejecucion_ia WHERE sesion_id = ?::uuid AND estado = ?",
                id, status);
    }

    private static LanguageModelResponse plan(String text) {
        return new LanguageModelResponse(text, 1200, 300, "modelo-de-prueba");
    }

    // ---- iniciar ----

    @Test
    @DisplayName("Iniciar crea las cinco preguntas, el turno 1 y deja la sesión EN_CURSO")
    void start_shouldPlanFiveQuestionsAndOpenTurnOne() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> plan(VALID_PLAN));

        ResponseEntity<String> response = start(UID_A, id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getBody(), "$.status")).isEqualTo("EN_CURSO");
        assertThat((String) JsonPath.read(response.getBody(), "$.answerFormat")).isEqualTo("TEXTO");
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.currentQuestion")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.progress.totalQuestions")).isEqualTo(5);
        assertThat((String) JsonPath.read(response.getBody(), "$.pendingQuestion.text"))
                .startsWith("Cuéntame brevemente");
        assertThat(model.calls()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.pregunta WHERE sesion_id = ?::uuid", id)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM microentrevista.turno")).isEqualTo(1);
        assertThat(executions(id, "EXITOSA")).isEqualTo(1);
        assertThat(executions(id, "EN_CURSO")).isZero();
        assertThat(jdbc.queryForObject("SELECT tokens_entrada FROM microentrevista.ejecucion_ia", Long.class))
                .isEqualTo(1200L);
        assertThat(statusOf(id)).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("Iniciar una sesión ya EN_CURSO responde 200 con su estado y no llama a la IA")
    void start_shouldBeIdempotentWhenAlreadyInProgress() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> plan(VALID_PLAN));
        start(UID_A, id);

        ResponseEntity<String> again = start(UID_A, id);

        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(again.getBody(), "$.status")).isEqualTo("EN_CURSO");
        assertThat(model.calls()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.pregunta")).isEqualTo(5);
    }

    @Test
    @DisplayName("Iniciar una sesión ajena, inexistente o con id mal escrito da el mismo 404")
    void start_shouldReturnSame404ForForeignMissingAndMalformed() {
        String id = configure(UID_A, PROFILE_A);

        ResponseEntity<String> foreign = start(UID_B, id);
        ResponseEntity<String> missing = start(UID_A, UUID.randomUUID().toString());
        ResponseEntity<String> malformed = start(UID_A, "abc");

        for (ResponseEntity<String> response : List.of(foreign, missing, malformed)) {
            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(code(response)).isEqualTo("SESSION_NOT_FOUND");
            assertThat((String) JsonPath.read(response.getBody(), "$.detail"))
                    .isEqualTo("No encontramos lo que buscabas.");
        }
        assertThat(model.calls()).isZero();
        assertThat(statusOf(id)).isEqualTo("CONFIGURADA");
    }

    @Test
    @DisplayName("Sin la cabecera de identidad, las tres rutas responden 401")
    void routes_shouldReturn401WithoutIdentity() {
        String id = configure(UID_A, PROFILE_A);

        assertThat(call(HttpMethod.POST, BASE + "/" + id + "/start", null, null).getStatusCode().value())
                .isEqualTo(401);
        assertThat(call(HttpMethod.GET, BASE + "/" + id + "/state", null, null).getStatusCode().value())
                .isEqualTo(401);
        assertThat(call(HttpMethod.DELETE, BASE + "/" + id, null, null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Iniciar una sesión EVALUANDO, FINALIZADA o ABANDONADA responde 409 sin llamar a la IA")
    void start_shouldReturn409WhenSessionIsNotStartable() {
        for (String closed : List.of("EVALUANDO", "FINALIZADA", "ABANDONADA")) {
            jdbc.execute("TRUNCATE microentrevista.sesion CASCADE");
            String id = configure(UID_A, PROFILE_A);
            forceStatus(id, closed);

            ResponseEntity<String> response = start(UID_A, id);

            assertThat(response.getStatusCode().value()).as(closed).isEqualTo(409);
            assertThat(code(response)).isEqualTo("SESSION_TRANSITION_NOT_ALLOWED");
        }
        assertThat(model.calls()).isZero();
    }

    private void forceStatus(String id, String status) {
        String end = status.equals("EVALUANDO") ? "NULL" : "now()";
        jdbc.update("UPDATE microentrevista.sesion SET estado = ?, fecha_fin = " + end + " WHERE id = ?::uuid",
                status, id);
    }

    @Test
    @DisplayName("Si la IA no responde, devuelve 503, la sesión sigue CONFIGURADA y la anterior EN_CURSO")
    void start_shouldKeepConfiguredAndPreviousWhenModelFails() {
        String previous = configure(UID_A, PROFILE_A);
        model.enqueue(() -> plan(VALID_PLAN));
        start(UID_A, previous);
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });

        ResponseEntity<String> response = start(UID_A, id);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(code(response)).isEqualTo("LLM_UNAVAILABLE");
        assertThat(statusOf(id)).isEqualTo("CONFIGURADA");
        assertThat(statusOf(previous)).isEqualTo("EN_CURSO");
        assertThat(executions(id, "FALLIDA")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.pregunta WHERE sesion_id = ?::uuid", id)).isZero();
    }

    @Test
    @DisplayName("El límite de uso del proveedor y una salida inválida responden 503 con su propio código")
    void start_shouldMapLimitAndInvalidOutput() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> {
            throw new LlmLimitReachedException(new RuntimeException("429"));
        });
        ResponseEntity<String> limit = start(UID_A, id);
        model.enqueue(() -> {
            throw new LlmInvalidOutputException("estructura");
        });
        ResponseEntity<String> invalid = start(UID_A, id);

        assertThat(limit.getStatusCode().value()).isEqualTo(503);
        assertThat(code(limit)).isEqualTo("LLM_LIMIT_REACHED");
        assertThat(invalid.getStatusCode().value()).isEqualTo(503);
        assertThat(code(invalid)).isEqualTo("LLM_INVALID_OUTPUT");
        assertThat(statusOf(id)).isEqualTo("CONFIGURADA");
    }

    @Test
    @DisplayName("Un plan con otro número de preguntas, tipos distintos o JSON ilegible es LLM_INVALID_OUTPUT")
    void start_shouldRejectInvalidPlans() {
        String id = configure(UID_A, PROFILE_A);
        for (String bad : List.of("no es json", "{\"idioma\":\"es-CO\",\"preguntas\":[]}",
                VALID_PLAN.replace("\"tipo\":\"CONDUCTUAL\"", "\"tipo\":\"TECNICA\""),
                VALID_PLAN.replace("es-CO", "en"))) {
            model.enqueue(() -> plan(bad));
            jdbc.update("UPDATE microentrevista.sesion SET reintentos_planificacion = 0 WHERE id = ?::uuid", id);
            jdbc.update("DELETE FROM microentrevista.ejecucion_ia");

            ResponseEntity<String> response = start(UID_A, id);

            assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(code(response)).isEqualTo("LLM_INVALID_OUTPUT");
        }
        assertThat(count("SELECT count(*) FROM microentrevista.pregunta")).isZero();
    }

    @Test
    @DisplayName("Contador: causa, causa, reintentos agotados y, el cuarto pedido, agotados sin llamar a la IA")
    void start_shouldCountAttemptsAndExhaust() {
        String id = configure(UID_A, PROFILE_A);
        for (int i = 0; i < 3; i++) {
            model.enqueue(() -> {
                throw new LlmUnavailableException(new RuntimeException("caída"));
            });
        }

        ResponseEntity<String> first = start(UID_A, id);
        ResponseEntity<String> second = start(UID_A, id);
        ResponseEntity<String> third = start(UID_A, id);
        int callsBeforeFourth = model.calls();
        ResponseEntity<String> fourth = start(UID_A, id);

        assertThat(code(first)).isEqualTo("LLM_UNAVAILABLE");
        assertThat(code(second)).isEqualTo("LLM_UNAVAILABLE");
        assertThat(code(third)).isEqualTo("PLANNING_RETRIES_EXHAUSTED");
        assertThat(code(fourth)).isEqualTo("PLANNING_RETRIES_EXHAUSTED");
        assertThat(callsBeforeFourth).isEqualTo(3);
        assertThat(model.calls()).isEqualTo(3);
        assertThat(retriesOf(id)).isEqualTo(2);
        assertThat(executions(id, "FALLIDA")).isEqualTo(3);
        assertThat(statusOf(id)).isEqualTo("CONFIGURADA");
    }

    @Test
    @DisplayName("Un perfil congelado demasiado grande o sin el rol responde 503 PROFILE_SNAPSHOT_INVALID sin llamar a la IA ni gastar intentos")
    void start_shouldRejectUnusableProfileSnapshotWithoutSpendingAttempts() {
        String tooBig = "{\"contextoProfesional\":{\"resumen\":\"" + "x".repeat(32_001) + "\"},\"rolesAspirados\":[]}";
        String withoutRole = "{\"contextoProfesional\":{},\"rolesAspirados\":[]}";
        for (String snapshot : List.of(tooBig, withoutRole)) {
            jdbc.execute("TRUNCATE microentrevista.sesion CASCADE");
            String id = configure(UID_A, PROFILE_A);
            jdbc.update("UPDATE microentrevista.sesion SET contexto_perfil_snapshot = ?::jsonb WHERE id = ?::uuid",
                    snapshot, id);

            ResponseEntity<String> response = start(UID_A, id);

            assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(code(response)).isEqualTo("PROFILE_SNAPSHOT_INVALID");
            assertThat((String) JsonPath.read(response.getBody(), "$.detail")).isEqualTo("No pudimos preparar tu"
                    + " entrevista con tu perfil actual. Revisa tu perfil e inténtalo de nuevo.");
            assertThat(model.calls()).isZero();
            assertThat(retriesOf(id)).isZero();
            assertThat(count("SELECT count(*) FROM microentrevista.ejecucion_ia")).isZero();
            assertThat(statusOf(id)).isEqualTo("CONFIGURADA");
        }
    }

    @Test
    @DisplayName("Los registros de iniciar la sesión no llevan texto del perfil, de la oferta ni de las preguntas")
    void start_shouldNeverLogProfileOfferOrQuestionText() {
        String offerMarker = "OFERTA-UNICA-7731";
        String invalidMarker = "PREGUNTA-INVALIDA-9137";
        String profilePhrase = "tres años de experiencia en SQL";
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String offerJson = """
                    {"profileId":"%s","targetRoleId":"%s","jobOffer":"%s","mode":"SIMULACION","tone":"CALIDO",
                     "answerFormat":"TEXTO","sessionLanguage":"es-CO"}""".formatted(PROFILE_A, ROLE, offerMarker);
            String failing = JsonPath.read(call(HttpMethod.POST, BASE, UID_A, offerJson).getBody(), "$.sessionId");
            // Un plan con dos preguntas iguales (texto con marca única): se rechaza sin citar el texto.
            model.enqueue(() -> plan(VALID_PLAN.replace("¿Cómo validarías la calidad de un conjunto de datos?",
                    "¿Cómo optimizarías una consulta SQL lenta? " + invalidMarker)
                    .replace("¿Cómo optimizarías una consulta SQL lenta?\"", "¿Cómo optimizarías una consulta SQL lenta?"
                            + " " + invalidMarker + "\"")));
            assertThat(start(UID_A, failing).getStatusCode().value()).isEqualTo(503);
            model.enqueue(() -> {
                throw new LlmUnavailableException(new RuntimeException("caída"));
            });
            assertThat(start(UID_A, failing).getStatusCode().value()).isEqualTo(503);
            model.enqueue(() -> plan(VALID_PLAN));
            assertThat(start(UID_A, failing).getStatusCode().value()).isEqualTo(200);
            assertThat(start(UID_A, UUID.randomUUID().toString()).getStatusCode().value()).isEqualTo(404);
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            StringBuilder visible = new StringBuilder(event.getFormattedMessage());
            for (IThrowableProxy error = event.getThrowableProxy(); error != null; error = error.getCause()) {
                visible.append(' ').append(error.getMessage());
            }
            assertThat(visible.toString()).doesNotContain(offerMarker, invalidMarker, profilePhrase,
                    "Cuéntame brevemente", "optimizarías una consulta", "desacuerdo con un compañero");
        }
    }

    @Test
    @DisplayName("Un reintento exitoso tras un fallo inicia la entrevista")
    void start_shouldSucceedOnRetry() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });
        model.enqueue(() -> plan(VALID_PLAN));

        assertThat(start(UID_A, id).getStatusCode().value()).isEqualTo(503);
        ResponseEntity<String> retry = start(UID_A, id);

        assertThat(retry.getStatusCode().value()).isEqualTo(200);
        assertThat(retriesOf(id)).isEqualTo(1);
        assertThat(executions(id, "FALLIDA")).isEqualTo(1);
        assertThat(executions(id, "EXITOSA")).isEqualTo(1);
    }

    @Test
    @DisplayName("Reconfigurar la sesión borra su historial de planificación y reinicia el contador")
    void reconfigure_shouldResetPlanningHistory() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });
        start(UID_A, id);

        String again = configure(UID_A, PROFILE_A);

        assertThat(again).isEqualTo(id);
        assertThat(executions(id, "FALLIDA")).isZero();
        assertThat(retriesOf(id)).isZero();
    }

    @Test
    @DisplayName("Al iniciar, la entrevista anterior en curso queda ABANDONADA y la nueva EN_CURSO")
    void start_shouldAbandonPreviousInProgress() {
        String previous = configure(UID_A, PROFILE_A);
        model.enqueue(() -> plan(VALID_PLAN));
        start(UID_A, previous);
        String next = configure(UID_A, PROFILE_A);
        model.enqueue(() -> plan(VALID_PLAN));

        ResponseEntity<String> response = start(UID_A, next);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(statusOf(previous)).isEqualTo("ABANDONADA");
        assertThat(statusOf(next)).isEqualTo("EN_CURSO");
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE estado = 'EN_CURSO'")).isEqualTo(1);
    }

    @Test
    @DisplayName("Si un fallo imprevisto de la base interrumpe el guardado, todo se revierte y la marca queda FALLIDA")
    void start_shouldRollBackWhenPersistenceFails() {
        String previous = configure(UID_A, PROFILE_A);
        model.enqueue(() -> plan(VALID_PLAN));
        start(UID_A, previous);
        String next = configure(UID_A, PROFILE_A);
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION microentrevista.fn_fallo_pregunta() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'fallo simulado'; END; $$ LANGUAGE plpgsql""");
        jdbc.execute("""
                CREATE TRIGGER trg_fallo_pregunta BEFORE INSERT ON microentrevista.pregunta
                FOR EACH ROW WHEN (NEW.sesion_id = '%s'::uuid) EXECUTE FUNCTION microentrevista.fn_fallo_pregunta()
                """.formatted(next));
        model.enqueue(() -> plan(VALID_PLAN));

        ResponseEntity<String> response = start(UID_A, next);
        jdbc.execute("DROP TRIGGER trg_fallo_pregunta ON microentrevista.pregunta");

        // Un error de SQL que ninguna regla prevé es el único caso del servicio que responde el genérico.
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(code(response)).isEqualTo("INTERNAL_ERROR");
        assertThat(statusOf(previous)).isEqualTo("EN_CURSO");
        assertThat(statusOf(next)).isEqualTo("CONFIGURADA");
        assertThat(executions(next, "FALLIDA")).isEqualTo(1);
        assertThat(executions(next, "EN_CURSO")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.pregunta WHERE sesion_id = ?::uuid", next)).isZero();
    }

    @Test
    @DisplayName("Una marca vigente de otra petición da 409 sin llamar a la IA; una caducada se cierra y se planifica")
    void start_shouldRespectLiveMarkAndCloseStaleOne() {
        String id = configure(UID_A, PROFILE_A);
        insertMark(id, NOW.minusSeconds(5));

        ResponseEntity<String> live = start(UID_A, id);

        assertThat(live.getStatusCode().value()).isEqualTo(409);
        assertThat(code(live)).isEqualTo("SESSION_CONFLICT");
        assertThat(model.calls()).isZero();

        jdbc.update("DELETE FROM microentrevista.ejecucion_ia");
        insertMark(id, NOW.minusSeconds(120));
        model.enqueue(() -> plan(VALID_PLAN));

        ResponseEntity<String> stale = start(UID_A, id);

        assertThat(stale.getStatusCode().value()).isEqualTo(200);
        assertThat(executions(id, "FALLIDA")).isEqualTo(1);
        assertThat(executions(id, "EXITOSA")).isEqualTo(1);
    }

    private void insertMark(String sessionId, Instant startedAt) {
        jdbc.update("""
                INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion, numero_intento,
                    idempotency_key, estado, fecha_inicio, modelo, version_prompt, proveedor)
                VALUES (?, ?::uuid, NULL, 'PLANIFICAR', 1, ?, 'EN_CURSO', ?, 'modelo-de-prueba', 'v', 'gemini')
                """, UUID.randomUUID(), sessionId, UUID.randomUUID(), java.sql.Timestamp.from(startedAt));
    }

    @Test
    @DisplayName("Dos inicios a la vez: uno responde 200, el otro 409, y la IA se llama una sola vez")
    void start_shouldAllowOnlyOneConcurrentPlanning() throws Exception {
        String id = configure(UID_A, PROFILE_A);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        model.enqueue(() -> {
            entered.countDown();
            await(release);
            return plan(VALID_PLAN);
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> first = pool.submit(() -> start(UID_A, id));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            ResponseEntity<String> second = pool.submit(() -> start(UID_A, id)).get(10, TimeUnit.SECONDS);
            release.countDown();

            assertThat(second.getStatusCode().value()).isEqualTo(409);
            assertThat(code(second)).isEqualTo("SESSION_CONFLICT");
            assertThat(first.get(10, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            assertThat(model.calls()).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM microentrevista.pregunta")).isEqualTo(5);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private ResponseEntity<String> reconfigure(String uid, UUID profile, String tone) {
        String json = """
                {"profileId":"%s","targetRoleId":"%s","jobOffer":"Buscamos analista","mode":"SIMULACION",
                 "tone":"%s","answerFormat":"TEXTO","sessionLanguage":"es-CO"}""".formatted(profile, ROLE, tone);
        return call(HttpMethod.POST, BASE, uid, json);
    }

    @Test
    @DisplayName("Reconfigurar mientras la IA prepara las preguntas responde 409 y no toca la marca ni la sesión")
    void reconfigure_shouldReturn409WhileMarkIsLive() throws Exception {
        String id = configure(UID_A, PROFILE_A);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        model.enqueue(() -> {
            entered.countDown();
            await(release);
            return plan(VALID_PLAN);
        });
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<ResponseEntity<String>> starting = pool.submit(() -> start(UID_A, id));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            ResponseEntity<String> reconfigured = reconfigure(UID_A, PROFILE_A, "ESTRICTO");
            assertThat(reconfigured.getStatusCode().value()).isEqualTo(409);
            assertThat(code(reconfigured)).isEqualTo("SESSION_CONFLICT");
            assertThat(executions(id, "EN_CURSO")).isEqualTo(1);
            release.countDown();

            assertThat(starting.get(10, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            assertThat(model.calls()).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE id = ?::uuid AND tono = 'CALIDO'", id))
                    .isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Si la configuración cambió mientras la IA respondía, el cierre responde 409 y no guarda nada")
    void start_shouldReturn409WhenConfigurationChangedDuringPlanning() throws Exception {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> {
            // La marca desapareció (por ejemplo, caducó y otro pedido la cerró): reconfigurar ya no se bloquea.
            jdbc.update("DELETE FROM microentrevista.ejecucion_ia WHERE sesion_id = ?::uuid", id);
            assertThat(reconfigure(UID_A, PROFILE_A, "ESTRICTO").getStatusCode().value()).isEqualTo(200);
            return plan(VALID_PLAN);
        });

        ResponseEntity<String> response = start(UID_A, id);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("SESSION_CONFLICT");
        assertThat(statusOf(id)).isEqualTo("CONFIGURADA");
        assertThat(count("SELECT count(*) FROM microentrevista.pregunta WHERE sesion_id = ?::uuid", id)).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.turno")).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Cancelar mientras se planifica: la cancelación gana o el inicio termina, nunca queda a medias")
    void startAndDelete_shouldNeverLeaveHalfState() throws Exception {
        String id = configure(UID_A, PROFILE_A);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        model.enqueue(() -> {
            entered.countDown();
            await(release);
            return plan(VALID_PLAN);
        });
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<ResponseEntity<String>> starting = pool.submit(() -> start(UID_A, id));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            ResponseEntity<String> deleted = call(HttpMethod.DELETE, BASE + "/" + id, UID_A, null);
            release.countDown();
            ResponseEntity<String> started = starting.get(10, TimeUnit.SECONDS);

            assertThat(deleted.getStatusCode().value()).isIn(204, 409);
            assertThat(started.getStatusCode().value()).isIn(200, 404, 409);
            int sessions = count("SELECT count(*) FROM microentrevista.sesion WHERE id = ?::uuid", id);
            int questions = count("SELECT count(*) FROM microentrevista.pregunta WHERE sesion_id = ?::uuid", id);
            assertThat(questions).isIn(0, 5);
            assertThat(sessions == 0 ? 0 : questions).isEqualTo(questions == 5 ? 5 : 0);
            assertThat(count("SELECT count(*) FROM microentrevista.ejecucion_ia WHERE estado = 'EN_CURSO'")).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // ---- estado ----

    @Test
    @DisplayName("El estado muestra CONFIGURADA con 0 de 5, EN_CURSO con la pregunta pendiente y nada de retroalimentación")
    void state_shouldReflectEachStatus() {
        String id = configure(UID_A, PROFILE_A);

        ResponseEntity<String> configured = state(UID_A, id);
        assertThat((String) JsonPath.read(configured.getBody(), "$.status")).isEqualTo("CONFIGURADA");
        assertThat((String) JsonPath.read(configured.getBody(), "$.answerFormat")).isEqualTo("TEXTO");
        assertThat((Integer) JsonPath.read(configured.getBody(), "$.progress.currentQuestion")).isZero();
        assertThat(configured.getBody()).contains("\"pendingQuestion\":null");

        model.enqueue(() -> plan(VALID_PLAN));
        start(UID_A, id);
        ResponseEntity<String> running = state(UID_A, id);
        assertThat((String) JsonPath.read(running.getBody(), "$.status")).isEqualTo("EN_CURSO");
        assertThat((String) JsonPath.read(running.getBody(), "$.answerFormat")).isEqualTo("TEXTO");
        assertThat((Integer) JsonPath.read(running.getBody(), "$.progress.currentQuestion")).isEqualTo(1);
        assertThat((String) JsonPath.read(running.getBody(), "$.pendingQuestion.turnId")).isNotBlank();
        assertThat(running.getBody()).doesNotContainIgnoringCase("feedback").doesNotContainIgnoringCase("retroalim");

        forceStatus(id, "EVALUANDO");
        ResponseEntity<String> evaluating = state(UID_A, id);
        assertThat((Boolean) JsonPath.read(evaluating.getBody(), "$.interviewFinished")).isTrue();
        assertThat((String) JsonPath.read(evaluating.getBody(), "$.answerFormat")).isEqualTo("TEXTO");
        assertThat((Integer) JsonPath.read(evaluating.getBody(), "$.progress.currentQuestion")).isEqualTo(5);
        assertThat(evaluating.getBody()).contains("\"pendingQuestion\":null");
    }

    @Test
    @DisplayName("Consultar el estado de una sesión ajena o con id mal escrito responde 404")
    void state_shouldReturn404ForForeignAndMalformed() {
        String id = configure(UID_A, PROFILE_A);

        assertThat(state(UID_B, id).getStatusCode().value()).isEqualTo(404);
        ResponseEntity<String> malformed = state(UID_A, "no-es-uuid");
        assertThat(malformed.getStatusCode().value()).isEqualTo(404);
        assertThat(code(malformed)).isEqualTo("SESSION_NOT_FOUND");
    }

    // ---- cancelar ----

    @Test
    @DisplayName("Cancelar una sesión CONFIGURADA responde 204 y la borra con sus ejecuciones de la IA")
    void delete_shouldRemoveConfiguredSession() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> {
            throw new LlmUnavailableException(new RuntimeException("caída"));
        });
        start(UID_A, id);

        ResponseEntity<String> response = call(HttpMethod.DELETE, BASE + "/" + id, UID_A, null);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(count("SELECT count(*) FROM microentrevista.sesion")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.ejecucion_ia")).isZero();
    }

    @Test
    @DisplayName("Cancelar una sesión EN_CURSO, EVALUANDO, FINALIZADA o ABANDONADA responde 409 y conserva todo")
    void delete_shouldReturn409WhenNotConfigured() {
        for (String other : List.of("EN_CURSO", "EVALUANDO", "FINALIZADA", "ABANDONADA")) {
            jdbc.execute("TRUNCATE microentrevista.sesion CASCADE");
            String id = configure(UID_A, PROFILE_A);
            if (other.equals("EN_CURSO")) {
                model.enqueue(() -> plan(VALID_PLAN));
                start(UID_A, id);
            } else {
                forceStatus(id, other);
            }

            ResponseEntity<String> response = call(HttpMethod.DELETE, BASE + "/" + id, UID_A, null);

            assertThat(response.getStatusCode().value()).as(other).isEqualTo(409);
            assertThat(code(response)).isEqualTo("SESSION_TRANSITION_NOT_ALLOWED");
            assertThat(statusOf(id)).isEqualTo(other);
        }
    }

    @Test
    @DisplayName("Cancelar una sesión ajena, inexistente o con id mal escrito responde 404")
    void delete_shouldReturn404ForForeignMissingAndMalformed() {
        String id = configure(UID_A, PROFILE_A);

        assertThat(call(HttpMethod.DELETE, BASE + "/" + id, UID_B, null).getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.DELETE, BASE + "/" + UUID.randomUUID(), UID_A, null).getStatusCode().value())
                .isEqualTo(404);
        assertThat(call(HttpMethod.DELETE, BASE + "/xyz", UID_A, null).getStatusCode().value()).isEqualTo(404);
        assertThat(statusOf(id)).isEqualTo("CONFIGURADA");
    }

    // ---- registros y entradas de ataque ----

    @Test
    @DisplayName("Iniciar ignora un cuerpo ilegible, campos extra y la cabecera Idempotency-Key")
    void start_shouldIgnoreBodyAndIdempotencyKey() {
        String id = configure(UID_A, PROFILE_A);
        model.enqueue(() -> plan(VALID_PLAN));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", UID_A);
        headers.set("Idempotency-Key", "cualquiera");

        ResponseEntity<String> response = client.exchange(BASE + "/" + id + "/start", HttpMethod.POST,
                new HttpEntity<>("{no es json", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Rutas con identificadores hostiles no llegan al error genérico")
    void routes_shouldNeverReachGenericErrorForHostileIds() {
        for (String hostile : List.of("%00", "'%20OR%201=1", "%3Cscript%3E", "..%2F..%2Fx", "9".repeat(200))) {
            for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.GET, HttpMethod.DELETE)) {
                String path = method == HttpMethod.POST ? BASE + "/" + hostile + "/start"
                        : method == HttpMethod.GET ? BASE + "/" + hostile + "/state" : BASE + "/" + hostile;
                ResponseEntity<String> response = call(method, path, UID_A, null);
                assertThat(response.getStatusCode().value()).as(method + " " + hostile).isLessThan(500);
            }
        }
    }

    // ---- doble del modelo ----

    /** Modelo de lenguaje simulado: cada llamada consume la siguiente respuesta programada y se cuenta. */
    static class ScriptedModel implements LanguageModelPort {

        private final Deque<Supplier<LanguageModelResponse>> script = new ArrayDeque<>();
        private final AtomicInteger calls = new AtomicInteger();

        synchronized void enqueue(Supplier<LanguageModelResponse> next) {
            script.addLast(next);
        }

        synchronized void reset() {
            script.clear();
            calls.set(0);
        }

        int calls() {
            return calls.get();
        }

        @Override
        public LanguageModelResponse generate(LanguageModelPrompt prompt) {
            calls.incrementAndGet();
            Supplier<LanguageModelResponse> next;
            synchronized (this) {
                next = script.pollFirst();
            }
            if (next == null) {
                throw new IllegalStateException("La prueba no programó una respuesta del modelo");
            }
            return next.get();
        }
    }

    @TestConfiguration
    static class ScriptedModelConfiguration {

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
    }
}
