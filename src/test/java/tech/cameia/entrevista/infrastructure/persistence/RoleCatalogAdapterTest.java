package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.port.RoleCatalog;

class RoleCatalogAdapterTest extends AbstractPersistenceTest {

    private static final UUID ACTIVE_ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000001");
    private static final UUID INACTIVE_ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private RoleCatalog roles;

    @BeforeEach
    void seedRoles() {
        jdbc.execute("TRUNCATE microentrevista.rol_profesional");
        insert(ACTIVE_ROLE, "ACTIVE");
        insert(INACTIVE_ROLE, "INACTIVE");
    }

    private void insert(UUID id, String status) {
        jdbc.update("""
                INSERT INTO microentrevista.rol_profesional (id, nombre_es, nombre_en, estado, actualizado_en,
                    fecha_replicacion)
                VALUES (?, 'Rol', 'Role', ?, now(), now())
                """, id, status);
    }

    @Test
    @DisplayName("Un rol activo del catálogo es activo")
    void isActive_shouldBeTrue_forInsertedActiveRole() {
        assertThat(roles.isActive(ACTIVE_ROLE)).isTrue();
    }

    @Test
    @DisplayName("Un rol que no está en la copia no es activo")
    void isActive_shouldBeFalse_forUnknown() {
        assertThat(roles.isActive(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Un rol inactivo no es activo")
    void isActive_shouldBeFalse_forInactive() {
        assertThat(roles.isActive(INACTIVE_ROLE)).isFalse();
    }
}
