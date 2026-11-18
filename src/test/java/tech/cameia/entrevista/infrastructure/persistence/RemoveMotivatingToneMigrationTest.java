package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.ToneConverter;

/**
 * Prueba la migración que retira el tono Motivador contra una base con datos reales: lleva la base a la
 * versión 4, inserta sesiones y respuestas guardadas del anti-duplicados con el tono retirado y migra a la
 * última versión. Usa Flyway directo, sin contexto de Spring, porque el arranque de la aplicación migra
 * siempre hasta la última versión y no deja sembrar datos antes.
 *
 * <p>Se omite si no hay Docker.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class RemoveMotivatingToneMigrationTest {

    private static final String SCHEMA = "microentrevista";
    private static final Timestamp OLD_UPDATE = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"));
    private static final String HASH = "a".repeat(64);

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void migrateToVersionFour() {
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        flyway().target("4").load().migrate();
    }

    // ---- sesiones ----

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"CONFIGURADA", "EN_CURSO", "EVALUANDO", "FINALIZADA", "ABANDONADA"})
    @DisplayName("Una sesión con Motivador pasa a Profesional y neutro sin importar su estado")
    void migration_shouldRewriteSessionsToNeutral_whenSavedWithMotivating(String status) {
        UUID id = insertSession("uid-m", status, "MOTIVADOR");
        Map<String, Object> before = session(id);

        migrateToLatest();

        Map<String, Object> after = session(id);
        assertThat(after.get("tono")).isEqualTo("PROFESIONAL_NEUTRO");
        assertThat(after.get("estado")).isEqualTo(status);
        assertThat(after.get("fecha_fin")).isEqualTo(before.get("fecha_fin"));
        assertThat(after.get("modalidad")).isEqualTo(before.get("modalidad"));
        assertThat(after.get("fecha_creacion")).isEqualTo(before.get("fecha_creacion"));
    }

    @Test
    @DisplayName("Las sesiones con otro tono no se tocan: mismo tono, misma versión y misma fecha")
    void migration_shouldKeepOtherTones() {
        UUID warm = insertSession("uid-w", "CONFIGURADA", "CALIDO");
        UUID strict = insertSession("uid-s", "FINALIZADA", "ESTRICTO");
        UUID neutral = insertSession("uid-n", "EN_CURSO", "PROFESIONAL_NEUTRO");
        List<Map<String, Object>> before = List.of(session(warm), session(strict), session(neutral));

        migrateToLatest();

        assertThat(List.of(session(warm), session(strict), session(neutral))).isEqualTo(before);
    }

    @Test
    @DisplayName("La fila reescrita sube su versión y su fecha de actualización")
    void migration_shouldBumpVersion() {
        UUID id = insertSession("uid-m", "EN_CURSO", "MOTIVADOR");

        migrateToLatest();

        Map<String, Object> after = session(id);
        assertThat(((Number) after.get("version")).longValue()).isEqualTo(1L);
        assertThat(((Timestamp) after.get("fecha_actualizacion")).toInstant()).isAfter(OLD_UPDATE.toInstant());
    }

    @ParameterizedTest(name = "valor «{0}»")
    @ValueSource(strings = {"MOTIVADOR", "motivador", "", " CALIDO"})
    @DisplayName("Después de migrar, la base rechaza Motivador y cualquier valor fuera de los tres tonos")
    void migration_shouldRejectMotivating_afterMigration(String tone) {
        migrateToLatest();

        assertThatThrownBy(() -> insertSession("uid-x", "CONFIGURADA", tone))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_sesion_tono");
    }

    @ParameterizedTest(name = "valor {0}")
    @ValueSource(strings = {"PROFESIONAL_NEUTRO", "CALIDO", "ESTRICTO"})
    @DisplayName("Después de migrar, la base sigue aceptando los tres tonos del catálogo")
    void migration_shouldAcceptCatalogTones_afterMigration(String tone) {
        migrateToLatest();

        UUID id = insertSession("uid-ok", "CONFIGURADA", tone);

        assertThat(session(id).get("tono")).isEqualTo(tone);
    }

    @Test
    @DisplayName("La aplicación lee sin error la sesión reescrita")
    void migration_shouldLetApplicationReadRewrittenSession() {
        UUID id = insertSession("uid-m", "ABANDONADA", "MOTIVADOR");

        migrateToLatest();

        String stored = (String) session(id).get("tono");
        assertThat(new ToneConverter().convertToEntityAttribute(stored)).isEqualTo(Tone.PROFESSIONAL_NEUTRAL);
    }

    // ---- respuestas guardadas del anti-duplicados ----

    @Test
    @DisplayName("La respuesta guardada con Motivador queda con Profesional y neutro y lo demás intacto")
    void migration_shouldRewriteStoredIdempotencyBody() {
        insertKey("k-motivating", storedBody("k-motivating", "MOTIVATING"));
        Map<String, Object> before = key("k-motivating");

        migrateToLatest();

        Map<String, Object> after = key("k-motivating");
        assertThat(jsonField("k-motivating", "configuration,tone")).isEqualTo("PROFESSIONAL_NEUTRAL");
        assertThat(jsonField("k-motivating", "status")).isEqualTo("CONFIGURED");
        assertThat(jsonField("k-motivating", "configuration,idempotencyKey")).isEqualTo("k-motivating");
        assertThat(jsonField("k-motivating", "configuration,mode")).isEqualTo("SIMULATION");
        assertThat(after.get("huella_peticion")).isEqualTo(before.get("huella_peticion"));
        assertThat(after.get("estado_respuesta")).isEqualTo(before.get("estado_respuesta"));
    }

    @Test
    @DisplayName("Las respuestas guardadas con otro tono no cambian")
    void migration_shouldKeepOtherStoredBodies() {
        insertKey("k-warm", storedBody("k-warm", "WARM"));
        insertKey("k-strict", storedBody("k-strict", "STRICT"));
        String warmBefore = (String) key("k-warm").get("cuerpo");
        String strictBefore = (String) key("k-strict").get("cuerpo");

        migrateToLatest();

        assertThat(key("k-warm").get("cuerpo")).isEqualTo(warmBefore);
        assertThat(key("k-strict").get("cuerpo")).isEqualTo(strictBefore);
    }

    @ParameterizedTest(name = "cuerpo {0}")
    @ValueSource(strings = {"{}", "{\"configuration\":{}}", "{\"configuration\":{\"tone\":null}}",
            "{\"configuration\":\"texto\"}", "{\"configuration\":[]}"})
    @DisplayName("Un cuerpo sin configuración o sin tono no se toca y no da error")
    void migration_shouldIgnoreBodiesWithoutConfiguration(String body) {
        insertKey("k-odd", body);
        String before = (String) key("k-odd").get("cuerpo");

        migrateToLatest();

        assertThat(key("k-odd").get("cuerpo")).isEqualTo(before);
    }

    @Test
    @DisplayName("Con las tablas vacías la migración termina sin error")
    void migration_shouldSucceed_whenTablesEmpty() {
        migrateToLatest();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM microentrevista.sesion", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM microentrevista.clave_idempotencia", Integer.class))
                .isZero();
    }

    // ---- ayudantes ----

    /** Misma configuración de esquema que la aplicación: esquema propio, creado por Flyway. */
    private static FluentConfiguration flyway() {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(true);
    }

    private void migrateToLatest() {
        flyway().load().migrate();
    }

    private UUID insertSession(String uid, String status, String tone) {
        UUID id = UUID.randomUUID();
        boolean closed = status.equals("FINALIZADA") || status.equals("ABANDONADA");
        jdbc.update("""
                INSERT INTO microentrevista.sesion (id, firebase_uid, perfil_id, rol_objetivo_id,
                    contexto_perfil_snapshot, modalidad, tono, forma_respuesta, idioma, estado,
                    fecha_creacion, fecha_actualizacion, fecha_fin)
                VALUES (?, ?, ?, ?, '{}'::jsonb, 'SIMULACION', ?, 'TEXTO', 'es-CO', ?, ?, ?, ?)
                """, id, uid, UUID.randomUUID(), UUID.randomUUID(), tone, status, OLD_UPDATE, OLD_UPDATE,
                closed ? OLD_UPDATE : null);
        return id;
    }

    private Map<String, Object> session(UUID id) {
        return jdbc.queryForMap("SELECT * FROM microentrevista.sesion WHERE id = ?", id);
    }

    private static String storedBody(String key, String tone) {
        return """
                {"status":"CONFIGURED","ongoing":null,"sessionId":"%s","configuration":{"mode":"SIMULATION",
                 "tone":"%s","idempotencyKey":"%s"}}""".formatted(UUID.randomUUID(), tone, key);
    }

    private void insertKey(String key, String body) {
        jdbc.update("""
                INSERT INTO microentrevista.clave_idempotencia (id, firebase_uid, operacion, clave, huella_peticion,
                    estado_respuesta, cuerpo_respuesta, fecha_creacion)
                VALUES (?, 'uid-k', 'CREAR_SESION', ?, ?, 201, ?::jsonb, now())
                """, UUID.randomUUID(), key, HASH, body);
    }

    private Map<String, Object> key(String key) {
        return jdbc.queryForMap("""
                SELECT huella_peticion, estado_respuesta, cuerpo_respuesta::text AS cuerpo
                  FROM microentrevista.clave_idempotencia WHERE clave = ?""", key);
    }

    private String jsonField(String key, String path) {
        return jdbc.queryForObject("""
                SELECT cuerpo_respuesta #>> ?::text[] FROM microentrevista.clave_idempotencia WHERE clave = ?""",
                String.class, "{" + path + "}", key);
    }
}
