package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.domain.port.Waiter;
import tech.cameia.entrevista.support.ReplicaFixtures;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Base de las pruebas de responder y reintentar contra el servidor completo y PostgreSQL 16 real, con un modelo de
 * lenguaje simulado que cuenta las llamadas y una espera que casi no duerme. Una sola base de datos se comparte entre
 * las clases para que Spring reutilice el contexto.
 */
abstract class AnswerFlowSupport {

    static final String BASE = "/api/v1/interviews/sessions";
    static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    static final String UID_A = "uid-ana-001";
    static final String UID_B = "uid-beto-002";
    static final UUID PROFILE_A = ReplicaFixtures.PROFILE_ID;
    static final UUID PROFILE_B = UUID.fromString("5d0c1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
    static final String ROLE = "a0000001-0000-0000-0000-000000000002";
    static final String KEY = "clave-respuesta-1";
    static final String ANSWER = "Usé colas para desacoplar el envío de correos.";

    static final String[] QUESTIONS = {
            "Cuéntame brevemente quién eres y por qué te interesa este rol.",
            "¿Cómo optimizarías una consulta SQL lenta?",
            "Háblame de un desacuerdo con un compañero y cómo lo resolviste.",
            "¿Cómo validarías la calidad de un conjunto de datos?",
            "Si un informe tuyo tuviera un error en producción, ¿qué harías?"};

    static final String VALID_PLAN = """
            {"idioma":"es-CO","preguntas":[
             {"posicion":1,"tipo":"APERTURA","texto":"%s"},
             {"posicion":2,"tipo":"TECNICA","texto":"%s"},
             {"posicion":3,"tipo":"CONDUCTUAL","texto":"%s"},
             {"posicion":4,"tipo":"TECNICA","texto":"%s"},
             {"posicion":5,"tipo":"SITUACIONAL","texto":"%s"}]}""".formatted((Object[]) QUESTIONS);

    static final String EVALUATION_OK = "{\"accion\":\"AVANZAR\",\"clasificacion\":\"COMPLETA\","
            + "\"motivo\":\"Responde lo pedido\",\"follow_up_texto\":\"\",\"feedback\":\"Buena estructura\"}";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static PostgreSQLContainer sharedPostgres;

    @Autowired
    TestRestTemplate client;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProfileReplicaRepository replicas;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    ScriptedModel model;

    @Autowired
    CountingWaiter waiter;

    @DynamicPropertySource
    static synchronized void database(DynamicPropertyRegistry registry) {
        if (sharedPostgres == null) {
            sharedPostgres = new PostgreSQLContainer("postgres:16-alpine");
            sharedPostgres.start();
        }
        registry.add("spring.datasource.url", sharedPostgres::getJdbcUrl);
        registry.add("spring.datasource.username", sharedPostgres::getUsername);
        registry.add("spring.datasource.password", sharedPostgres::getPassword);
    }

    @BeforeEach
    void prepareDatabase() {
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
        waiter.reset();
    }

    // ---- peticiones ----

    private void seedProfile(UUID id, String uid) {
        ProfileReplica replica = new ProfileReplica(id, new FirebaseUid(uid), "Analista de datos",
                ReplicatedProfileStatus.COMPLETED, null, ReplicaFixtures.CONTEXT_JSON,
                ReplicaFixtures.ASPIRED_ROLES_JSON, NOW);
        transaction.executeWithoutResult(tx -> replicas.upsertIfNotOlder(replica, NOW));
    }

    ResponseEntity<String> call(HttpMethod method, String path, String uid, String key, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (uid != null) {
            headers.set("X-User-Id", uid);
        }
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return client.exchange(path, method, new HttpEntity<>(json, headers), String.class);
    }

    static String body(String turnId, String answerText) {
        return JSON.writeValueAsString(Map.of("turnId", turnId, "answerText", answerText));
    }

    ResponseEntity<String> answer(String uid, String sessionId, String turnId, String text, String key) {
        return answerRaw(uid, sessionId, key, body(turnId, text));
    }

    ResponseEntity<String> answerRaw(String uid, String sessionId, String key, String json) {
        return call(HttpMethod.POST, BASE + "/" + sessionId + "/answers", uid, key, json);
    }

    ResponseEntity<String> reprocess(String uid, String sessionId, String turnId) {
        return call(HttpMethod.POST, BASE + "/" + sessionId + "/turns/" + turnId + "/reprocess", uid, null, null);
    }

    ResponseEntity<String> consolidate(String uid, String sessionId) {
        return call(HttpMethod.POST, BASE + "/" + sessionId + "/consolidation", uid, null, null);
    }

    /** Responde los primeros {@code count} turnos con una evaluación válida y devuelve la última respuesta. */
    ResponseEntity<String> answerFirst(String uid, String sessionId, int count) {
        ResponseEntity<String> last = null;
        for (int number = 1; number <= count; number++) {
            model.enqueue(AnswerFlowSupport::evaluation);
            last = answer(uid, sessionId, turnId(sessionId, number), ANSWER, "clave-" + number);
            assertThat(status(last)).isEqualTo(200);
        }
        return last;
    }

    ResponseEntity<String> state(String uid, String sessionId) {
        return call(HttpMethod.GET, BASE + "/" + sessionId + "/state", uid, null, null);
    }

    /** Crea una sesión configurada por el endpoint real. */
    String configure(String uid, UUID profile) {
        String json = """
                {"profileId":"%s","targetRoleId":"%s","jobOffer":"Buscamos analista","mode":"SIMULACION",
                 "tone":"CALIDO","answerFormat":"TEXTO","sessionLanguage":"es-CO"}""".formatted(profile, ROLE);
        ResponseEntity<String> created = call(HttpMethod.POST, BASE, uid, null, json);
        assertThat(created.getStatusCode().value()).isIn(200, 201);
        return JsonPath.read(created.getBody(), "$.sessionId");
    }

    /** Crea e inicia una sesión: queda EN_CURSO con la pregunta 1 pendiente. */
    String startedSession(String uid, UUID profile) {
        String id = configure(uid, profile);
        model.enqueue(() -> response(VALID_PLAN));
        ResponseEntity<String> started = call(HttpMethod.POST, BASE + "/" + id + "/start", uid, null, null);
        assertThat(started.getStatusCode().value()).isEqualTo(200);
        model.reset();
        return id;
    }

    // ---- lectura de la base ----

    String turnId(String sessionId, int number) {
        return jdbc.queryForObject("""
                SELECT t.id::text FROM microentrevista.turno t JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
                WHERE p.sesion_id = ?::uuid AND p.orden = ?
                """, String.class, sessionId, number);
    }

    String turnStatus(String turnId) {
        return jdbc.queryForObject("SELECT estado FROM microentrevista.turno WHERE id = ?::uuid", String.class,
                turnId);
    }

    int retriesUsed(String turnId) {
        return count("SELECT reintentos_manuales FROM microentrevista.turno WHERE id = ?::uuid", turnId);
    }

    String sessionStatus(String sessionId) {
        return jdbc.queryForObject("SELECT estado FROM microentrevista.sesion WHERE id = ?::uuid", String.class,
                sessionId);
    }

    void forceStatus(String sessionId, String status) {
        String end = status.equals("EVALUANDO") ? "NULL" : "now()";
        jdbc.update("UPDATE microentrevista.sesion SET estado = ?, fecha_fin = " + end + " WHERE id = ?::uuid",
                status, sessionId);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** Filas que deja una respuesta: intentos, ejecuciones de evaluación, retroalimentaciones, turnos y claves. */
    List<Integer> rows() {
        return List.of(
                count("SELECT count(*) FROM microentrevista.intento_respuesta"),
                count("SELECT count(*) FROM microentrevista.ejecucion_ia WHERE tipo_operacion = 'EVALUAR'"),
                count("SELECT count(*) FROM microentrevista.feedback_turno"),
                count("SELECT count(*) FROM microentrevista.turno"),
                count("SELECT count(*) FROM microentrevista.clave_idempotencia WHERE operacion = 'RESPONDER'"));
    }

    int evaluations(String turnId, String status) {
        return count("SELECT count(*) FROM microentrevista.ejecucion_ia WHERE turno_id = ?::uuid"
                + " AND tipo_operacion = 'EVALUAR' AND estado = ?", turnId, status);
    }

    static String code(ResponseEntity<String> response) {
        return JsonPath.read(response.getBody(), "$.code");
    }

    static int status(ResponseEntity<String> response) {
        return response.getStatusCode().value();
    }

    static LanguageModelResponse response(String text) {
        return new LanguageModelResponse(text, 800, 200, "modelo-de-prueba");
    }

    static LanguageModelResponse evaluation() {
        return response(EVALUATION_OK);
    }

    // ---- dobles ----

    /** Modelo de lenguaje simulado: cada llamada consume la siguiente respuesta programada y se cuenta. */
    static class ScriptedModel implements LanguageModelPort {

        private final Deque<Supplier<LanguageModelResponse>> script = new ArrayDeque<>();
        private final AtomicInteger calls = new AtomicInteger();

        synchronized void enqueue(Supplier<LanguageModelResponse> next) {
            script.addLast(next);
        }

        /** Programa un ciclo completo que falla: la misma respuesta para cada uno de los tres intentos. */
        synchronized void enqueueCycle(Supplier<LanguageModelResponse> next) {
            for (int attempt = 0; attempt < 3; attempt++) {
                script.addLast(next);
            }
        }

        synchronized void reset() {
            script.clear();
            calls.set(0);
        }

        int calls() {
            return calls.get();
        }

        @Override
        public boolean isAvailable(AiProvider provider) {
            return provider == AiProvider.PRIMARY;
        }

        @Override
        public LanguageModelResponse generate(LanguageModelPrompt prompt, AiProvider provider) {
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

    /** Detiene la llamada al modelo hasta que la prueba la suelte, para forzar que dos peticiones se solapen. */
    static class Gate {

        private final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        private final String output;

        Gate(String output) {
            this.output = output;
        }

        /** Respuesta programada del modelo: avisa que llegó y espera la orden de seguir. */
        LanguageModelResponse call() {
            entered.countDown();
            try {
                if (!release.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("La prueba no soltó el modelo a tiempo");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return response(output);
        }

        void awaitEntered() throws InterruptedException {
            assertThat(entered.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        void open() {
            release.countDown();
        }
    }

    /** Espera que casi no duerme y cuenta cuántas veces se le pidió esperar. */
    static class CountingWaiter implements Waiter {

        private final AtomicInteger pauses = new AtomicInteger();

        void reset() {
            pauses.set(0);
        }

        int pauses() {
            return pauses.get();
        }

        @Override
        public void pause(Duration duration) {
            pauses.incrementAndGet();
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @TestConfiguration
    static class Doubles {

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
        CountingWaiter countingWaiter() {
            return new CountingWaiter();
        }
    }

    /** Configuración común de las pruebas de responder y reintentar. */
    @java.lang.annotation.Documented
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureTestRestTemplate
    @WithLanguageModelProperties
    @Testcontainers(disabledWithoutDocker = true)
    @Import(AnswerFlowSupport.Doubles.class)
    @interface AnswerFlowTest {
    }
}
