package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
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

import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Prueba de integración del esquema de la copia del catálogo de roles contra PostgreSQL 16 real:
 * cada restricción se viola a propósito, los largos de columna se comparan con el límite del
 * contrato y se comprueba el tipo nuevo del Inbox. Se omite si no hay Docker.
 */
@SpringBootTest
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class RoleCatalogSchemaMigrationTest {

    private static final String ROLES = "microentrevista.rol_profesional";
    private static final String INBOX = "microentrevista.evento_procesado";
    private static final int NAME_MAX_LENGTH = ProfessionalRoleReplica.MAX_NAME_LENGTH;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("TRUNCATE " + ROLES + ", " + INBOX);
    }

    @Test
    @DisplayName("La migración crea la tabla del catálogo en el esquema de Entrevista")
    void migration_shouldCreateRoleTable() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'microentrevista' AND table_name = 'rol_profesional'
                """, String.class);

        assertThat(tables).containsExactly("rol_profesional");
    }

    @Test
    @DisplayName("Una fila válida se guarda y el estado por omisión es ACTIVE")
    void role_shouldAcceptValidRowAndDefaultToActive() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + ROLES + " (id, nombre_es, nombre_en, actualizado_en, fecha_replicacion) "
                + "VALUES (?, 'Desarrollador Backend', 'Backend Developer', ?, ?)", id, ts(), ts());

        assertThat(jdbc.queryForObject("SELECT estado FROM " + ROLES + " WHERE id = ?", String.class, id))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Un nombre en español en blanco viola ck_rol_profesional_nombre_es")
    void role_shouldRejectBlankSpanishName() {
        assertViolates("ck_rol_profesional_nombre_es", () -> insertRole("   ", "Backend Developer", "ACTIVE"));
    }

    @Test
    @DisplayName("Un nombre en inglés en blanco viola ck_rol_profesional_nombre_en")
    void role_shouldRejectBlankEnglishName() {
        assertViolates("ck_rol_profesional_nombre_en", () -> insertRole("Desarrollador", "   ", "ACTIVE"));
    }

    @ParameterizedTest(name = "estado [{0}]")
    @ValueSource(strings = { "DELETED", "active", "Active", "" })
    @DisplayName("El estado fuera del contrato viola ck_rol_profesional_estado")
    void role_shouldRejectUnknownStatus(String status) {
        assertViolates("ck_rol_profesional_estado", () -> insertRole("Desarrollador", "Developer", status));
    }

    @Test
    @DisplayName("Un nombre de 256 caracteres no cabe y uno de 255 puntos de código sí")
    void role_shouldEnforceNameLength() {
        assertThatThrownBy(() -> insertRole("é".repeat(NAME_MAX_LENGTH + 1), "Developer", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRole("Desarrollador", "é".repeat(NAME_MAX_LENGTH + 1), "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertRole("😀".repeat(NAME_MAX_LENGTH), "😀".repeat(NAME_MAX_LENGTH), "INACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un identificador repetido viola pk_rol_profesional")
    void role_shouldRejectDuplicateId() {
        UUID id = UUID.randomUUID();
        insertRole(id, "Desarrollador", "Developer", "ACTIVE");

        assertViolates("pk_rol_profesional", () -> insertRole(id, "Otro", "Other", "ACTIVE"));
    }

    @ParameterizedTest(name = "columna [{0}]")
    @ValueSource(strings = { "id", "nombre_es", "nombre_en", "estado", "actualizado_en", "fecha_replicacion" })
    @DisplayName("Ninguna columna admite nulo")
    void role_shouldRejectNullColumns(String column) {
        List<String> columns = List.of("id", "nombre_es", "nombre_en", "estado", "actualizado_en",
                "fecha_replicacion");
        Object[] values = { UUID.randomUUID(), "Desarrollador", "Developer", "ACTIVE", ts(), ts() };
        values[columns.indexOf(column)] = null;

        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + ROLES + " (" + String.join(", ", columns)
                + ") VALUES (?, ?, ?, ?, ?, ?)", values))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("null value in column \"" + column + "\"");
    }

    @Test
    @DisplayName("El catálogo no tiene claves foráneas (MAN-03: el rol es de Perfil)")
    void role_shouldHaveNoForeignKeys() {
        Integer foreignKeys = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'microentrevista' AND constraint_type = 'FOREIGN KEY'
                  AND table_name = 'rol_profesional'
                """, Integer.class);

        assertThat(foreignKeys).isZero();
    }

    @Test
    @DisplayName("Los largos de columna son los del contrato del evento")
    void columnLengths_shouldMatchContract() {
        assertThat(columnLength("rol_profesional", "nombre_es")).isEqualTo(NAME_MAX_LENGTH);
        assertThat(columnLength("rol_profesional", "nombre_en")).isEqualTo(NAME_MAX_LENGTH);
        assertThat(columnLength("rol_profesional", "estado")).isEqualTo(8);
    }

    @Test
    @DisplayName("El Inbox acepta los dos tipos de evento y rechaza cualquier otro")
    void inbox_shouldAcceptBothTypesAndRejectOthers() {
        assertThatCode(() -> insertInbox("perfil.profesional-actualizado")).doesNotThrowAnyException();
        assertThatCode(() -> insertInbox("perfil.catalogo-roles-actualizado")).doesNotThrowAnyException();

        assertViolates("ck_evento_procesado_tipo", () -> insertInbox("otro"));
    }

    @Test
    @DisplayName("La tabla y las columnas clave llevan comentario")
    void comments_shouldExist() {
        assertThat(jdbc.queryForObject("SELECT obj_description('" + ROLES + "'::regclass, 'pg_class')",
                String.class)).isNotBlank();
        for (String column : List.of("estado", "actualizado_en")) {
            assertThat(columnComment(column)).as(column).isNotBlank();
        }
    }

    private void insertRole(String nameEs, String nameEn, String status) {
        insertRole(UUID.randomUUID(), nameEs, nameEn, status);
    }

    private void insertRole(UUID id, String nameEs, String nameEn, String status) {
        jdbc.update("INSERT INTO " + ROLES + " (id, nombre_es, nombre_en, estado, actualizado_en, fecha_replicacion) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, nameEs, nameEn, status, ts(), ts());
    }

    private void insertInbox(String type) {
        jdbc.update("INSERT INTO " + INBOX + " (message_id, tipo, procesado_en) VALUES (?, ?, ?)",
                UUID.randomUUID(), type, ts());
    }

    private static Timestamp ts() {
        return Timestamp.from(Instant.parse("2026-10-24T12:00:00.123Z"));
    }

    private Integer columnLength(String table, String column) {
        return jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = 'microentrevista' AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
    }

    private String columnComment(String column) {
        return jdbc.queryForObject("""
                SELECT col_description('microentrevista.rol_profesional'::regclass,
                    (SELECT attnum FROM pg_attribute
                      WHERE attrelid = 'microentrevista.rol_profesional'::regclass AND attname = ?))
                """, String.class, column);
    }

    private static void assertViolates(String constraint, Runnable insert) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }
}
