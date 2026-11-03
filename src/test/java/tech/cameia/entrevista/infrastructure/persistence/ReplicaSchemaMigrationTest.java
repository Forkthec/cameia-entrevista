package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Prueba de integración del esquema de la copia del perfil y del Inbox contra PostgreSQL 16 real:
 * cada restricción se viola a propósito y los largos de columna se comparan con los del dominio.
 * Se omite si no hay Docker.
 */
@SpringBootTest
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class ReplicaSchemaMigrationTest {

    private static final String REPLICA = "microentrevista.contexto_profesional_replica";
    private static final String INBOX = "microentrevista.evento_procesado";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("TRUNCATE " + REPLICA + ", " + INBOX);
    }

    @Test
    @DisplayName("La migración crea las dos tablas en el esquema de Entrevista")
    void migration_shouldCreateBothTables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'microentrevista'
                  AND table_name IN ('contexto_profesional_replica', 'evento_procesado')
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder("contexto_profesional_replica", "evento_procesado");
    }

    @Test
    @DisplayName("Una fila válida se guarda")
    void replica_shouldAcceptValidRow() {
        assertThatCode(() -> insertReplica("uid-a", "Analista", "COMPLETED", "{}", "[{\"codigo\":\"x\"}]"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "estado [{0}]")
    @ValueSource(strings = { "ARCHIVED", "completed", "" })
    @DisplayName("El estado fuera del contrato viola ck_contexto_profesional_replica_estado")
    void replica_shouldRejectUnknownStatus(String status) {
        assertViolates("ck_contexto_profesional_replica_estado",
                () -> insertReplica("uid-a", "Analista", status, "{}", "[1]"));
    }

    @Test
    @DisplayName("Un nombre en blanco viola ck_contexto_profesional_replica_nombre")
    void replica_shouldRejectBlankName() {
        assertViolates("ck_contexto_profesional_replica_nombre",
                () -> insertReplica("uid-a", "   ", "COMPLETED", "{}", "[1]"));
    }

    @Test
    @DisplayName("Un firebase_uid en blanco viola ck_contexto_profesional_replica_firebase_uid")
    void replica_shouldRejectBlankFirebaseUid() {
        assertViolates("ck_contexto_profesional_replica_firebase_uid",
                () -> insertReplica("   ", "Analista", "COMPLETED", "{}", "[1]"));
    }

    @Test
    @DisplayName("Un nombre de 256 caracteres no cabe en la columna")
    void replica_shouldReject256CharName() {
        assertThatThrownBy(() -> insertReplica("uid-a", "é".repeat(256), "COMPLETED", "{}", "[1]"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Un nombre de 255 puntos de código (510 unidades UTF-16) cabe")
    void replica_shouldAccept255CodePointName() {
        assertThatCode(() -> insertReplica("uid-a", "😀".repeat(255), "COMPLETED", "{}", "[1]"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un firebase_uid de 129 caracteres no cabe y uno de 128 sí")
    void replica_shouldEnforceFirebaseUidLength() {
        assertThatThrownBy(() -> insertReplica("u".repeat(129), "Analista", "COMPLETED", "{}", "[1]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertReplica("u".repeat(128), "Analista", "COMPLETED", "{}", "[1]"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "roles [{0}]")
    @ValueSource(strings = { "{}", "[]", "[1,2,3,4,5,6]", "\"texto\"", "null" })
    @DisplayName("Los roles aspirados deben ser un arreglo de 1 a 5 elementos")
    void replica_shouldRejectInvalidRoles(String roles) {
        assertViolates("ck_contexto_profesional_replica_roles",
                () -> insertReplica("uid-a", "Analista", "COMPLETED", "{}", roles));
    }

    @ParameterizedTest(name = "contexto [{0}]")
    @ValueSource(strings = { "[]", "\"texto\"", "5" })
    @DisplayName("El contexto profesional debe ser un objeto")
    void replica_shouldRejectContextNotObject(String context) {
        assertViolates("ck_contexto_profesional_replica_contexto",
                () -> insertReplica("uid-a", "Analista", "COMPLETED", context, "[1]"));
    }

    @ParameterizedTest(name = "columna [{0}]")
    @ValueSource(strings = { "perfil_id", "firebase_uid", "nombre", "estado", "contexto_profesional",
            "roles_aspirados", "actualizado_en", "fecha_replicacion" })
    @DisplayName("Ninguna columna obligatoria admite nulo")
    void replica_shouldRejectNullColumns(String column) {
        List<String> columns = List.of("perfil_id", "firebase_uid", "nombre", "estado", "contexto_profesional",
                "roles_aspirados", "actualizado_en", "fecha_replicacion");
        Object[] values = { UUID.randomUUID(), "uid-a", "Analista", "COMPLETED", "{}", "[1]", ts(), ts() };
        values[columns.indexOf(column)] = null;

        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + REPLICA + " (" + String.join(", ", columns)
                + ") VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)", values))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("null value in column \"" + column + "\"");
    }

    @Test
    @DisplayName("Ni la copia ni el Inbox tienen claves foráneas (MAN-03)")
    void replica_shouldHaveNoForeignKeys() {
        Integer foreignKeys = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'microentrevista' AND constraint_type = 'FOREIGN KEY'
                  AND table_name IN ('contexto_profesional_replica', 'evento_procesado')
                """, Integer.class);

        assertThat(foreignKeys).isZero();
    }

    @Test
    @DisplayName("El Inbox rechaza un tipo de evento desconocido")
    void inbox_shouldRejectUnknownType() {
        assertViolates("ck_evento_procesado_tipo",
                () -> insertInbox(UUID.randomUUID(), "cuenta.creada"));
    }

    @Test
    @DisplayName("El Inbox rechaza un message_id repetido")
    void inbox_shouldRejectDuplicateMessageId() {
        UUID id = UUID.randomUUID();
        insertInbox(id, "perfil.profesional-actualizado");

        assertViolates("pk_evento_procesado", () -> insertInbox(id, "perfil.profesional-actualizado"));
    }

    @Test
    @DisplayName("Los largos de columna son los del dominio")
    void columnLengths_shouldMatchDomain() {
        assertThat(columnLength("contexto_profesional_replica", "firebase_uid")).isEqualTo(FirebaseUid.MAX_LENGTH);
        assertThat(columnLength("contexto_profesional_replica", "nombre")).isEqualTo(ProfileReplica.MAX_NAME_LENGTH);
    }

    @Test
    @DisplayName("Las tablas y las columnas clave llevan comentario")
    void comments_shouldExist() {
        assertThat(tableComment("contexto_profesional_replica")).isNotBlank();
        assertThat(tableComment("evento_procesado")).isNotBlank();
        for (String column : List.of("estado", "contexto_profesional", "roles_aspirados", "actualizado_en")) {
            assertThat(columnComment("contexto_profesional_replica", column)).as(column).isNotBlank();
        }
    }

    private void insertReplica(String uid, String name, String status, String context, String roles) {
        jdbc.update("INSERT INTO " + REPLICA + " (perfil_id, firebase_uid, nombre, estado, contexto_profesional, "
                + "roles_aspirados, actualizado_en, fecha_replicacion) "
                + "VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)",
                UUID.randomUUID(), uid, name, status, context, roles, ts(), ts());
    }

    private void insertInbox(UUID id, String type) {
        jdbc.update("INSERT INTO " + INBOX + " (message_id, tipo, procesado_en) VALUES (?, ?, ?)", id, type, ts());
    }

    private static java.sql.Timestamp ts() {
        return java.sql.Timestamp.from(Instant.parse("2026-10-20T15:00:00Z"));
    }

    private Integer columnLength(String table, String column) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = 'microentrevista' AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
    }

    private String tableComment(String table) {
        return jdbc.queryForObject("SELECT obj_description('microentrevista." + table + "'::regclass, 'pg_class')",
                String.class);
    }

    private String columnComment(String table, String column) {
        return jdbc.queryForObject("""
                SELECT col_description(('microentrevista.' || ?)::regclass,
                    (SELECT attnum FROM pg_attribute WHERE attrelid = ('microentrevista.' || ?)::regclass
                       AND attname = ?))
                """, String.class, table, table, column);
    }

    private static void assertViolates(String constraint, Runnable insert) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }
}
