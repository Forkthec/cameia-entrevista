package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
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

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.support.ReplicaFixtures;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Crea la sesión de entrevista contra el servidor completo y PostgreSQL 16 real: contenedor de servlets, filtro del
 * identificador de petición, manejador de errores, transacciones, índices únicos y claves anti-duplicados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
@Import(CreateSessionEndToEndTest.FixedClockAndKeyProbe.class)
class CreateSessionEndToEndTest {

    private static final String PATH = "/api/v1/interviews/sessions";
    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final String UID_A = "uid-a";
    private static final String UID_B = "uid-b";
    private static final UUID PROFILE_A = ReplicaFixtures.PROFILE_ID;
    private static final UUID PROFILE_B = UUID.fromString("5d0c1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
    private static final String ROLE = "a0000001-0000-0000-0000-000000000002";
    private static final String OTHER_ROLE = "a0000001-0000-0000-0000-000000000001";
    private static final String KEY = "clave-e2e-1";

    /** Hace que el guardado de la clave falle, para comprobar que la transacción entera se revierte. */
    private static final AtomicBoolean FAIL_KEY_SAVE = new AtomicBoolean();

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
    private InterviewSessionRepository sessions;

    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void prepare() {
        jdbc.execute("TRUNCATE microentrevista.sesion, microentrevista.clave_idempotencia, "
                + "microentrevista.contexto_profesional_replica, microentrevista.rol_profesional CASCADE");
        insertRole(ROLE, "ACTIVE");
        insertRole(OTHER_ROLE, "ACTIVE");
        seedProfile(PROFILE_A, UID_A, "Analista de datos", ReplicatedProfileStatus.COMPLETED, null, NOW);
        FAIL_KEY_SAVE.set(false);
    }

    @AfterEach
    void clearFailure() {
        FAIL_KEY_SAVE.set(false);
    }

    // ---- ayudantes ----

    private void insertRole(String id, String status) {
        jdbc.update("""
                INSERT INTO microentrevista.rol_profesional (id, nombre_es, nombre_en, estado, actualizado_en,
                    fecha_replicacion)
                VALUES (?::uuid, 'Rol', 'Role', ?, now(), now())
                """, id, status);
    }

    private void seedProfile(UUID id, String uid, String name, ReplicatedProfileStatus status, Instant archivedAt,
            Instant updatedAt) {
        ProfileReplica replica = new ProfileReplica(id, new FirebaseUid(uid), name, status, archivedAt,
                ReplicaFixtures.CONTEXT_JSON, ReplicaFixtures.ASPIRED_ROLES_JSON, updatedAt);
        transaction.executeWithoutResult(tx -> replicas.upsertIfNotOlder(replica, NOW));
    }

    private static String body(UUID profileId, String role, String offer, String mode, String tone, String format,
            String language) {
        return """
                {"profileId":"%s","targetRoleId":"%s","jobOffer":%s,"mode":"%s","tone":"%s","answerFormat":"%s",
                 "sessionLanguage":"%s"}""".formatted(profileId, role,
                offer == null ? "null" : "\"" + offer + "\"", mode, tone, format, language);
    }

    private static String validBody() {
        return body(PROFILE_A, ROLE, "Buscamos desarrollador", "SIMULACION", "CALIDO", "TEXTO", "es-CO");
    }

    private ResponseEntity<String> post(String uid, String key, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (uid != null) {
            headers.set("X-User-Id", uid);
        }
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return client.exchange(PATH, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private static String field(ResponseEntity<String> response, String path) {
        return JsonPath.<Object>read(response.getBody(), path) == null ? null
                : String.valueOf(JsonPath.<Object>read(response.getBody(), path));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int sessionsOf(String uid) {
        return count("SELECT count(*) FROM microentrevista.sesion WHERE firebase_uid = ?", uid);
    }

    /** Crea una sesión en curso de la persona con tantas respuestas definitivas como se pida. */
    private UUID givenInProgress(String uid, int answers) {
        InterviewSession session = InterviewSession.configure(UUID.randomUUID(), new FirebaseUid(uid),
                new tech.cameia.entrevista.domain.model.SessionConfiguration(PROFILE_A, UUID.fromString(ROLE), null,
                        "{}", tech.cameia.entrevista.domain.model.Modality.SIMULATION,
                        tech.cameia.entrevista.domain.model.Tone.WARM,
                        tech.cameia.entrevista.domain.model.AnswerMode.TEXT,
                        tech.cameia.entrevista.domain.model.InterviewLanguage.SPANISH_COLOMBIA),
                Clock.fixed(NOW, ZoneOffset.UTC));
        session.start(Clock.fixed(NOW, ZoneOffset.UTC));
        UUID id = sessions.save(session).id();
        for (int order = 1; order <= answers; order++) {
            insertAnsweredQuestion(id, order);
        }
        return id;
    }

    private void insertAnsweredQuestion(UUID sessionId, int order) {
        UUID execution = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion, numero_intento,
                    idempotency_key, estado, fecha_inicio, fecha_fin, modelo, version_prompt)
                VALUES (?, ?, NULL, 'PLANIFICAR', ?, ?, 'EXITOSA', now(), now(), 'modelo-de-prueba', 'v1')
                """, execution, sessionId, (order - 1) % 3 + 1, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO microentrevista.pregunta (id, sesion_id, orden, tipo, texto_original, fecha_creacion,
                    ejecucion_ia_id)
                VALUES (?, ?, ?, 'APERTURA', 'Cuéntame sobre ti', now(), ?)
                """, question, sessionId, order, execution);
        jdbc.update("""
                INSERT INTO microentrevista.turno (id, pregunta_id, estado, fecha_creacion, fecha_actualizacion,
                    version)
                VALUES (?, ?, 'COMPLETED', now(), now(), 0)
                """, turn, question);
        jdbc.update("""
                INSERT INTO microentrevista.intento_respuesta (id, turno_id, tipo_interaccion, es_definitiva,
                    origen_respuesta, respuesta_texto, pregunta_mostrada, fecha_creacion)
                VALUES (?, ?, 'INICIAL', true, 'TEXTO', 'Mi respuesta', 'Cuéntame sobre ti', now())
                """, UUID.randomUUID(), turn);
    }

    // ---- creación ----

    @Test
    @DisplayName("Una configuración válida crea la sesión configurada con la fecha del reloj")
    void create_shouldReturn201Configured_whenValid() {
        ResponseEntity<String> response = post(UID_A, null, validBody());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(field(response, "$.status")).isEqualTo("CONFIGURADA");
        assertThat(field(response, "$.configuration.mode")).isEqualTo("SIMULACION");
        assertThat(field(response, "$.configuration.jobOffer")).isEqualTo("Buscamos desarrollador");
        assertThat(response.getBody()).contains("\"ongoingSession\":null");
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT estado, reintentos_planificacion, fecha_creacion, fecha_actualizacion, firebase_uid
                FROM microentrevista.sesion WHERE id = ?::uuid""", field(response, "$.sessionId"));
        assertThat(row.get("estado")).isEqualTo("CONFIGURADA");
        assertThat(row.get("reintentos_planificacion")).isEqualTo(0);
        assertThat(row.get("firebase_uid")).isEqualTo(UID_A);
        assertThat(((java.sql.Timestamp) row.get("fecha_creacion")).toInstant()).isEqualTo(NOW);
        assertThat(((java.sql.Timestamp) row.get("fecha_actualizacion")).toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Una oferta de solo espacios se guarda como null")
    void create_shouldStoreNullOffer_whenOfferBlank() {
        ResponseEntity<String> response = post(UID_A, null,
                body(PROFILE_A, ROLE, "   ", "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE oferta_laboral IS NULL")).isEqualTo(1);
    }

    @Test
    @DisplayName("Una oferta de 3000 caracteres se guarda completa")
    void create_shouldStore3000Offer() {
        ResponseEntity<String> response = post(UID_A, null,
                body(PROFILE_A, ROLE, "é".repeat(3000), "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE char_length(oferta_laboral) = 3000"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Una oferta con texto hostil se guarda literal")
    void create_shouldStoreHostileOfferLiteral() {
        ResponseEntity<String> response = post(UID_A, null, body(PROFILE_A, ROLE,
                "<script>alert(1)<\\/script> '' % _ \\\\", "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT oferta_laboral FROM microentrevista.sesion", String.class))
                .isEqualTo("<script>alert(1)</script> '' % _ \\");
    }

    @Test
    @DisplayName("Crear la sesión no crea preguntas, turnos ni ejecuciones de IA")
    void create_shouldCreateNoQuestions() {
        post(UID_A, null, validBody());

        assertThat(count("SELECT count(*) FROM microentrevista.pregunta")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.turno")).isZero();
        assertThat(count("SELECT count(*) FROM microentrevista.ejecucion_ia")).isZero();
    }

    @Test
    @DisplayName("La copia del perfil en la sesión no cambia aunque el perfil se actualice después")
    void create_shouldKeepSnapshot_whenProfileChangesLater() {
        String id = field(post(UID_A, null, validBody()), "$.sessionId");

        seedProfile(PROFILE_A, UID_A, "Otro", ReplicatedProfileStatus.COMPLETED, null, NOW.plusSeconds(60));

        String snapshot = jdbc.queryForObject(
                "SELECT contexto_perfil_snapshot::text FROM microentrevista.sesion WHERE id = ?::uuid", String.class,
                id);
        assertThat(JsonPath.<String>read(snapshot, "$.nombre")).isEqualTo("Analista de datos");
        assertThat(JsonPath.<String>read(snapshot, "$.perfilId")).isEqualTo(PROFILE_A.toString());
        assertThat(JsonPath.<String>read(snapshot, "$.contextoProfesional.resumen")).contains("SQL");
        assertThat(JsonPath.<String>read(snapshot, "$.rolesAspirados[0].nombre")).isEqualTo("Desarrollador Backend");
        assertThat(JsonPath.<String>read(snapshot, "$.actualizadoEn")).isEqualTo(NOW.toString());
    }

    // ---- reutilización y entrevista en curso ----

    @Test
    @DisplayName("Con una sesión configurada responde 200, mismo id, reintentos en 0 y la fecha de creación intacta")
    void create_shouldReuseConfigured_with200AndResetRetries() {
        String id = field(post(UID_A, null, validBody()), "$.sessionId");
        jdbc.update("UPDATE microentrevista.sesion SET reintentos_planificacion = 2");

        ResponseEntity<String> second = post(UID_A, null,
                body(PROFILE_A, OTHER_ROLE, null, "SIMULACION", "ESTRICTO", "AUDIO", "en"));

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(field(second, "$.sessionId")).isEqualTo(id);
        assertThat(field(second, "$.configuration.tone")).isEqualTo("ESTRICTO");
        assertThat(field(second, "$.configuration.jobOffer")).isNull();
        assertThat(sessionsOf(UID_A)).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM microentrevista.sesion");
        assertThat(row.get("reintentos_planificacion")).isEqualTo(0);
        assertThat(row.get("tono")).isEqualTo("ESTRICTO");
        assertThat(row.get("idioma")).isEqualTo("en");
        assertThat(row.get("oferta_laboral")).isNull();
        assertThat(((java.sql.Timestamp) row.get("fecha_creacion")).toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Con una sesión configurada y otra en curso reutiliza la primera y deja la segunda intacta")
    void create_shouldReuseConfigured_whenAlsoInProgress() {
        UUID running = givenInProgress(UID_A, 1);
        String configured = field(post(UID_A, null, validBody()), "$.sessionId");

        ResponseEntity<String> response = post(UID_A, null, validBody());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(field(response, "$.sessionId")).isEqualTo(configured);
        assertThat(field(response, "$.ongoingSession.sessionId")).isEqualTo(running.toString());
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE id = ? AND estado = 'EN_CURSO' "
                + "AND fecha_fin IS NULL", running)).isEqualTo(1);
    }

    @Test
    @DisplayName("Informa la entrevista en curso con 2 y con 5 respuestas definitivas")
    void create_shouldReportOngoingSession_withAnswersCount() {
        UUID running = givenInProgress(UID_A, 2);
        assertThat(field(post(UID_A, null, validBody()), "$.ongoingSession.answersCount")).isEqualTo("2");

        jdbc.execute("TRUNCATE microentrevista.sesion CASCADE");
        running = givenInProgress(UID_A, 5);
        ResponseEntity<String> five = post(UID_A, null, validBody());

        assertThat(field(five, "$.ongoingSession.answersCount")).isEqualTo("5");
        assertThat(field(five, "$.ongoingSession.sessionId")).isEqualTo(running.toString());
    }

    @Test
    @DisplayName("Sin entrevista en curso ongoingSession es null")
    void create_shouldReportNoOngoing_whenNone() {
        assertThat(post(UID_A, null, validBody()).getBody()).contains("\"ongoingSession\":null");
    }

    @Test
    @DisplayName("La entrevista en curso sigue EN_CURSO y sin fecha de fin")
    void create_shouldLeaveInProgressUntouched() {
        UUID running = givenInProgress(UID_A, 1);
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM microentrevista.sesion WHERE id = ?", running);

        post(UID_A, null, validBody());

        assertThat(jdbc.queryForMap("SELECT * FROM microentrevista.sesion WHERE id = ?", running)).isEqualTo(before);
    }

    @Test
    @DisplayName("Si falla guardar la clave se revierte todo: ninguna sesión nueva y la en curso intacta")
    void create_shouldRollbackAll_whenFailsAfterInsert() {
        UUID running = givenInProgress(UID_A, 1);
        FAIL_KEY_SAVE.set(true);

        ResponseEntity<String> response = post(UID_A, KEY, validBody());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(field(response, "$.code")).isEqualTo("INTERNAL_ERROR");
        assertThat(sessionsOf(UID_A)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.sesion WHERE id = ? AND estado = 'EN_CURSO' "
                + "AND fecha_fin IS NULL", running)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.clave_idempotencia")).isZero();
    }

    @Test
    @DisplayName("Si falla guardar la clave al reutilizar, la sesión configurada queda como estaba")
    void create_shouldRollbackReconfigure_whenKeySaveFails() {
        post(UID_A, null, validBody());
        FAIL_KEY_SAVE.set(true);

        ResponseEntity<String> response = post(UID_A, KEY,
                body(PROFILE_A, ROLE, null, "SIMULACION", "ESTRICTO", "AUDIO", "en"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        Map<String, Object> row = jdbc.queryForMap("SELECT tono, idioma FROM microentrevista.sesion");
        assertThat(row.get("tono")).isEqualTo("CALIDO");
        assertThat(row.get("idioma")).isEqualTo("es-CO");
    }

    // ---- perfil ----

    @Test
    @DisplayName("Sin copia del perfil responde 409 PROFILE_SYNC_PENDING y no guarda nada")
    void create_shouldReturn409SyncPending_whenProfileNotReplicated() {
        jdbc.execute("TRUNCATE microentrevista.contexto_profesional_replica");

        ResponseEntity<String> response = post(UID_A, null, validBody());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(field(response, "$.code")).isEqualTo("PROFILE_SYNC_PENDING");
        assertThat(field(response, "$.detail")).isEqualTo("Tu perfil se está actualizando. Inténtalo en unos segundos.");
        assertThat(field(response, "$.title")).isEqualTo("Perfil en actualización");
        assertThat(sessionsOf(UID_A)).isZero();
    }

    @Test
    @DisplayName("Una copia incompleta o archivada responde 409 PROFILE_SYNC_PENDING")
    void create_shouldReturn409SyncPending_whenReplicaNotUsable() {
        seedProfile(PROFILE_A, UID_A, "Analista", ReplicatedProfileStatus.IN_PROGRESS, null, NOW.plusSeconds(1));
        assertThat(field(post(UID_A, null, validBody()), "$.code")).isEqualTo("PROFILE_SYNC_PENDING");

        seedProfile(PROFILE_A, UID_A, "Analista", ReplicatedProfileStatus.COMPLETED, NOW, NOW.plusSeconds(2));
        ResponseEntity<String> archived = post(UID_A, null, validBody());

        assertThat(archived.getStatusCode().value()).isEqualTo(409);
        assertThat(field(archived, "$.code")).isEqualTo("PROFILE_SYNC_PENDING");
        assertThat(sessionsOf(UID_A)).isZero();
    }

    @Test
    @DisplayName("El perfil de otra persona responde 404 PROFILE_NOT_FOUND y no crea sesión")
    void create_shouldReturn404_whenProfileOfOtherUser() {
        ResponseEntity<String> response = post(UID_B, null, validBody());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(field(response, "$.code")).isEqualTo("PROFILE_NOT_FOUND");
        assertThat(field(response, "$.detail")).isEqualTo("No encontramos lo que buscabas.");
        assertThat(response.getBody()).doesNotContain(UID_A);
        assertThat(sessionsOf(UID_B)).isZero();
    }

    @Test
    @DisplayName("Los campos que no existen se ignoran y el dueño es el del encabezado")
    void create_shouldIgnoreUnexpectedFields_andUseHeaderOwner() {
        String hostile = validBody().replace("{\"profileId\"",
                "{\"firebaseUid\":\"uid-b\",\"status\":\"EN_CURSO\",\"estado\":\"FINALIZADA\",\"plan\":\"PREMIUM\","
                        + "\"planningRetries\":2,\"profileId\"");

        ResponseEntity<String> response = post(UID_A, null, hostile);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(sessionsOf(UID_A)).isEqualTo(1);
        assertThat(sessionsOf(UID_B)).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM microentrevista.sesion", String.class))
                .isEqualTo("CONFIGURADA");
    }

    @Test
    @DisplayName("Texto, inglés y plan gratuito se aceptan")
    void create_shouldAcceptTextAndEnglish_withFreePlan() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", UID_A);
        headers.set("X-User-Plan", "FREE");

        ResponseEntity<String> response = client.exchange(PATH, HttpMethod.POST,
                new HttpEntity<>(body(PROFILE_A, ROLE, null, "SIMULACION", "MOTIVADOR", "TEXTO", "en"), headers),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    // ---- validación ----

    @Test
    @DisplayName("Un rol inactivo o que no existe da TARGET_ROLE_ID_INVALID_VALUE")
    void create_shouldReturn422_whenRoleInactiveOrUnknown() {
        insertRole("a0000001-0000-0000-0000-0000000000ff", "INACTIVE");

        for (String role : List.of("a0000001-0000-0000-0000-0000000000ff", "00000000-0000-0000-0000-000000000000")) {
            ResponseEntity<String> response = post(UID_A, null,
                    body(PROFILE_A, role, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

            assertThat(response.getStatusCode().value()).isEqualTo(422);
            assertThat(field(response, "$.errors[0].code")).isEqualTo("TARGET_ROLE_ID_INVALID_VALUE");
            assertThat(field(response, "$.errors[0].field")).isEqualTo("targetRoleId");
        }
        assertThat(sessionsOf(UID_A)).isZero();
    }

    @Test
    @DisplayName("Un cuerpo vacío da los seis obligatorios a la vez y no guarda nada")
    void create_shouldReturn422WithSixFields_whenEmptyBody() {
        ResponseEntity<String> response = post(UID_A, null, "{}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(String.valueOf(response.getHeaders().getContentType()).toLowerCase())
                .contains("application/problem+json").contains("charset=utf-8");
        assertThat(JsonPath.<List<String>>read(response.getBody(), "$.errors[*].field")).containsExactly("profileId",
                "targetRoleId", "mode", "tone", "answerFormat", "sessionLanguage");
        assertThat(field(response, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat(field(response, "$.requestId")).isNotBlank();
        assertThat(count("SELECT count(*) FROM microentrevista.sesion")).isZero();
    }

    @Test
    @DisplayName("Una oferta con el carácter nulo da 422 JOB_OFFER_INVALID_CHARACTERS y no un 500")
    void create_shouldReject422_whenOfferHasNulCharacter() {
        // El nulo viaja como el escape JSON, tal como lo envía un cliente real.
        ResponseEntity<String> response = post(UID_A, null,
                body(PROFILE_A, ROLE, "abc\\u0000def", "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(field(response, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat(field(response, "$.errors[0].field")).isEqualTo("jobOffer");
        assertThat(field(response, "$.errors[0].code")).isEqualTo("JOB_OFFER_INVALID_CHARACTERS");
        assertThat(sessionsOf(UID_A)).isZero();
    }

    @Test
    @DisplayName("Una oferta de 5 MB se rechaza con JOB_OFFER_TOO_LONG sin llegar a la base")
    void create_shouldReject5MbOffer() {
        ResponseEntity<String> response = post(UID_A, null,
                body(PROFILE_A, ROLE, "a".repeat(5 * 1024 * 1024), "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(field(response, "$.errors[0].code")).isEqualTo("JOB_OFFER_TOO_LONG");
        assertThat(response.getBody().length()).isLessThan(2000);
        assertThat(sessionsOf(UID_A)).isZero();
    }

    @Test
    @DisplayName("Los errores y los éxitos declaran charset UTF-8")
    void create_shouldDeclareCharset() {
        ResponseEntity<String> created = post(UID_A, null, validBody());
        ResponseEntity<String> invalid = post(UID_A, null, "{}");

        assertThat(String.valueOf(created.getHeaders().getContentType()).toLowerCase()).contains("charset=utf-8");
        assertThat(String.valueOf(invalid.getHeaders().getContentType()).toLowerCase()).contains("charset=utf-8");
    }

    @Test
    @DisplayName("La respuesta no expone el contenido del perfil")
    void create_shouldNotExposeProfileData() {
        String text = post(UID_A, null, validBody()).getBody();

        assertThat(text).doesNotContain("resumen").doesNotContain("SQL").doesNotContain("Python")
                .doesNotContain("Analista").doesNotContain("Desarrollador Backend");
    }

    @Test
    @DisplayName("Sin identidad responde 401 y un Content-Type que no es JSON responde 415")
    void create_shouldReturn401And415() {
        assertThat(post(null, null, validBody()).getStatusCode().value()).isEqualTo(401);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set("X-User-Id", UID_A);
        ResponseEntity<String> text = client.exchange(PATH, HttpMethod.POST, new HttpEntity<>(validBody(), headers),
                String.class);

        assertThat(text.getStatusCode().value()).isEqualTo(415);
        assertThat(field(text, "$.code")).isEqualTo("MEDIA_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("JSON roto responde 422 REQUEST_BODY_INVALID_FORMAT")
    void create_shouldReturn422_whenMalformedJson() {
        ResponseEntity<String> response = post(UID_A, null, "{\"mode\":");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(field(response, "$.code")).isEqualTo("REQUEST_BODY_INVALID_FORMAT");
    }

    // ---- anti-duplicados ----

    @Test
    @DisplayName("La misma clave y la misma configuración repiten la misma respuesta con una sola sesión")
    void create_shouldReplaySameResponse_whenSameKey() {
        ResponseEntity<String> first = post(UID_A, KEY, validBody());
        ResponseEntity<String> second = post(UID_A, KEY, validBody());

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(sessionsOf(UID_A)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.clave_idempotencia")).isEqualTo(1);
    }

    @Test
    @DisplayName("Veinte repeticiones con la misma clave aplican una sola vez")
    void create_shouldApplyOnce_when20Repetitions() {
        String firstBody = post(UID_A, KEY, validBody()).getBody();

        for (int i = 0; i < 19; i++) {
            ResponseEntity<String> repeat = post(UID_A, KEY, validBody());
            assertThat(repeat.getStatusCode().value()).isEqualTo(201);
            assertThat(repeat.getBody()).isEqualTo(firstBody);
        }

        assertThat(sessionsOf(UID_A)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.clave_idempotencia")).isEqualTo(1);
    }

    @Test
    @DisplayName("Repetir la clave tras reutilizar la sesión repite el 200")
    void create_shouldReplayStatus200_whenFirstWasReuse() {
        post(UID_A, null, validBody());
        ResponseEntity<String> first = post(UID_A, KEY, validBody());
        ResponseEntity<String> second = post(UID_A, KEY, validBody());

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getBody()).isEqualTo(first.getBody());
    }

    @Test
    @DisplayName("Dos peticiones simultáneas con la misma clave dan una sola sesión y dos respuestas iguales")
    void create_shouldReplaySameResponse_whenSameKeyConcurrent() throws Exception {
        List<ResponseEntity<String>> responses = runConcurrently(
                () -> post(UID_A, KEY, validBody()), () -> post(UID_A, KEY, validBody()));

        assertThat(responses).extracting(response -> response.getStatusCode().value()).containsExactly(201, 201);
        assertThat(field(responses.get(0), "$.sessionId")).isEqualTo(field(responses.get(1), "$.sessionId"));
        assertThat(sessionsOf(UID_A)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microentrevista.clave_idempotencia")).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos peticiones simultáneas con claves distintas no duplican la sesión")
    void create_shouldNotDuplicate_whenTwoKeysConcurrent() throws Exception {
        List<ResponseEntity<String>> responses = runConcurrently(
                () -> post(UID_A, "clave-x", validBody()), () -> post(UID_A, "clave-y", validBody()));

        List<Integer> statuses = responses.stream().map(response -> response.getStatusCode().value()).sorted()
                .toList();
        assertThat(statuses).isIn(List.of(200, 201), List.of(201, 409));
        if (statuses.contains(409)) {
            ResponseEntity<String> conflict = responses.stream().filter(r -> r.getStatusCode().value() == 409)
                    .findFirst().orElseThrow();
            assertThat(field(conflict, "$.code")).isEqualTo("SESSION_ALREADY_EXISTS");
        }
        assertThat(sessionsOf(UID_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos reutilizaciones simultáneas nunca dan 500: 200, o 200 y 409 SESSION_CONFLICT")
    void create_shouldReturnConflict_whenConcurrentReconfigure() throws Exception {
        post(UID_A, null, validBody());

        List<ResponseEntity<String>> responses = runConcurrently(
                () -> post(UID_A, null, body(PROFILE_A, ROLE, "uno", "SIMULACION", "CALIDO", "TEXTO", "es-CO")),
                () -> post(UID_A, null, body(PROFILE_A, ROLE, "dos", "SIMULACION", "ESTRICTO", "TEXTO", "es-CO")));

        List<Integer> statuses = responses.stream().map(response -> response.getStatusCode().value()).sorted()
                .toList();
        assertThat(statuses).isIn(List.of(200, 200), List.of(200, 409));
        assertThat(statuses).doesNotContain(500);
        assertThat(sessionsOf(UID_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("La misma clave con oferta ausente y luego con la oferta «-» responde 409 IDEMPOTENCY_KEY_CONFLICT")
    void create_shouldReturn409_whenSameKeyOfferNullThenDash() {
        post(UID_A, KEY, body(PROFILE_A, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

        ResponseEntity<String> response = post(UID_A, KEY,
                body(PROFILE_A, ROLE, "-", "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(field(response, "$.code")).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(sessionsOf(UID_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("La misma clave con otra configuración responde 409 IDEMPOTENCY_KEY_CONFLICT")
    void create_shouldReturn409_whenSameKeyDifferentBody() {
        post(UID_A, KEY, validBody());

        ResponseEntity<String> response = post(UID_A, KEY,
                body(PROFILE_A, ROLE, "otra", "SIMULACION", "ESTRICTO", "TEXTO", "es-CO"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(field(response, "$.code")).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(field(response, "$.title")).isEqualTo("Solicitud distinta");
        assertThat(jdbc.queryForObject("SELECT tono FROM microentrevista.sesion", String.class)).isEqualTo("CALIDO");
    }

    @Test
    @DisplayName("Tras PROFILE_SYNC_PENDING el reintento con la misma clave funciona cuando llega la copia")
    void create_shouldSucceed_whenRetryWithSameKeyAfterSyncPending() {
        jdbc.execute("TRUNCATE microentrevista.contexto_profesional_replica");
        assertThat(post(UID_A, KEY, validBody()).getStatusCode().value()).isEqualTo(409);
        assertThat(count("SELECT count(*) FROM microentrevista.clave_idempotencia")).isZero();

        seedProfile(PROFILE_A, UID_A, "Analista de datos", ReplicatedProfileStatus.COMPLETED, null, NOW);
        ResponseEntity<String> retry = post(UID_A, KEY, validBody());

        assertThat(retry.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("La misma clave de dos personas son claves distintas")
    void create_shouldIsolateKeysPerUser() {
        seedProfile(PROFILE_B, UID_B, "Perfil de B", ReplicatedProfileStatus.COMPLETED, null, NOW);

        ResponseEntity<String> a = post(UID_A, KEY, validBody());
        ResponseEntity<String> b = post(UID_B, KEY,
                body(PROFILE_B, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO"));

        assertThat(a.getStatusCode().value()).isEqualTo(201);
        assertThat(b.getStatusCode().value()).isEqualTo(201);
        assertThat(field(a, "$.sessionId")).isNotEqualTo(field(b, "$.sessionId"));
        assertThat(count("SELECT count(*) FROM microentrevista.sesion")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM microentrevista.clave_idempotencia")).isEqualTo(2);
    }

    @Test
    @DisplayName("Una clave inválida da 422 con el encabezado como campo y no guarda nada")
    void create_shouldReturn422_whenKeyInvalid() {
        for (String key : List.of("a b", "a/b", "k".repeat(65))) {
            ResponseEntity<String> response = post(UID_A, key, validBody());

            assertThat(response.getStatusCode().value()).isEqualTo(422);
            assertThat(field(response, "$.errors[0].field")).isEqualTo("Idempotency-Key");
            assertThat(field(response, "$.errors[0].code")).isEqualTo("IDEMPOTENCY_KEY_INVALID_FORMAT");
        }
        assertThat(sessionsOf(UID_A)).isZero();
    }

    // ---- rendimiento y documentación ----

    @Test
    @DisplayName("Con 30 personas creando su sesión a la vez el p95 es de 2000 ms o menos")
    void create_shouldMeetLatencyBudget_when30Requests() throws Exception {
        post(UID_A, null, validBody());
        List<Callable> calls = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            UUID profile = UUID.randomUUID();
            String uid = "uid-carga-" + i;
            seedProfile(profile, uid, "Perfil " + i, ReplicatedProfileStatus.COMPLETED, null, NOW);
            calls.add(() -> {
                long start = System.nanoTime();
                ResponseEntity<String> response = post(uid, null,
                        body(profile, ROLE, "Oferta", "SIMULACION", "CALIDO", "TEXTO", "es-CO"));
                assertThat(response.getStatusCode().value()).isEqualTo(201);
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Long> millis = new ArrayList<>();
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (Callable call : calls) {
                futures.add(pool.submit(call::run));
            }
            for (Future<Long> future : futures) {
                millis.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        Collections.sort(millis);
        long p95 = millis.get((int) Math.ceil(millis.size() * 0.95) - 1);

        assertThat(p95).as("p95 = %d ms, p50 = %d ms", p95, millis.get(millis.size() / 2)).isLessThanOrEqualTo(2000L);
    }

    @Test
    @DisplayName("OpenAPI documenta la creación de la sesión con todas sus respuestas")
    void openApi_shouldDocumentCreateSession() {
        String api = client.getForObject("/v3/api-docs", String.class);

        Map<String, Object> responses = JsonPath.read(api, "$.paths['/api/v1/interviews/sessions'].post.responses");
        assertThat(responses).containsKeys("200", "201", "401", "404", "409", "415", "422", "500", "503");
        assertThat(responses.get("409").toString()).contains("PROFILE_SYNC_PENDING")
                .contains("IDEMPOTENCY_KEY_CONFLICT").contains("SESSION_ALREADY_EXISTS");
        assertThat(responses.get("422").toString()).contains("errors");
        Map<String, Object> request = JsonPath.read(api, "$.components.schemas.CreateSessionRequest.properties");
        assertThat(request).containsOnlyKeys("profileId", "targetRoleId", "jobOffer", "mode", "tone", "answerFormat",
                "sessionLanguage");
        assertThat(request.get("jobOffer").toString()).contains("maxLength=3000");
        assertThat(JsonPath.<List<Object>>read(api, "$.paths['/api/v1/interviews/sessions'].post.parameters[*].name"))
                .contains("Idempotency-Key").doesNotContain("X-User-Id");
    }

    // ---- infraestructura de la prueba ----

    @FunctionalInterface
    private interface Callable {
        Long run() throws Exception;
    }

    private List<ResponseEntity<String>> runConcurrently(java.util.concurrent.Callable<ResponseEntity<String>> first,
            java.util.concurrent.Callable<ResponseEntity<String>> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (java.util.concurrent.Callable<ResponseEntity<String>> call : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await();
            go.countDown();
            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Reloj fijo y un decorador de la clave anti-duplicados que puede fallar al guardar. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockAndKeyProbe {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        static BeanPostProcessor keyProbeInstaller() {
            return new KeyProbeInstaller();
        }
    }

    private static final class KeyProbeInstaller implements BeanPostProcessor, Ordered {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof IdempotencyKeyRepository repository && !(bean instanceof FailingKeys)) {
                return new FailingKeys(repository);
            }
            return bean;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    private static final class FailingKeys implements IdempotencyKeyRepository {

        private final IdempotencyKeyRepository delegate;

        FailingKeys(IdempotencyKeyRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.util.Optional<IdempotencyKey> find(FirebaseUid owner, String operation, String key) {
            return delegate.find(owner, operation, key);
        }

        @Override
        public IdempotencyKey save(IdempotencyKey key) {
            if (FAIL_KEY_SAVE.get()) {
                throw new IllegalStateException("fallo de prueba al guardar la clave");
            }
            return delegate.save(key);
        }
    }
}
