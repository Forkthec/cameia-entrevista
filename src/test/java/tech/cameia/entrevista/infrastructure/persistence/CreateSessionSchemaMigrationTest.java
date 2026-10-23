package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
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

import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Prueba de integración de la restricción de operación de la clave anti-duplicados contra PostgreSQL
 * 16 real. Se omite si no hay Docker.
 */
@SpringBootTest
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class CreateSessionSchemaMigrationTest {

    private static final String KEYS = "microentrevista.clave_idempotencia";
    private static final String FINGERPRINT = "a".repeat(64);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.execute("TRUNCATE " + KEYS);
    }

    private void insert(String operation) {
        jdbc.update("INSERT INTO " + KEYS + " (id, firebase_uid, operacion, clave, huella_peticion, "
                + "estado_respuesta, cuerpo_respuesta, fecha_creacion) VALUES (?, 'uid-a', ?, 'clave-1', ?, 201, "
                + "'{}'::jsonb, ?)", UUID.randomUUID(), operation, FINGERPRINT,
                Timestamp.from(Instant.parse("2026-10-20T15:00:00Z")));
    }

    @Test
    @DisplayName("La operación CREAR_SESION se acepta")
    void idempotency_shouldAcceptCreateSession() {
        assertThatCode(() -> insert("CREAR_SESION")).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"REGISTRAR", "crear_sesion", " CREAR_SESION", "CREAR_SESION "})
    @DisplayName("Cualquier otra operación viola ck_clave_idempotencia_operacion")
    void idempotency_shouldRejectUnknownOperation(String operation) {
        assertThatThrownBy(() -> insert(operation)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_clave_idempotencia_operacion");
    }

    @Test
    @DisplayName("La columna de operación queda documentada con su valor permitido")
    void idempotency_shouldCommentOperationColumn() {
        String comment = jdbc.queryForObject("""
                SELECT col_description('microentrevista.clave_idempotencia'::regclass, attnum)
                FROM pg_attribute
                WHERE attrelid = 'microentrevista.clave_idempotencia'::regclass AND attname = 'operacion'
                """, String.class);

        assertThat(comment).contains("CREAR_SESION");
    }
}
