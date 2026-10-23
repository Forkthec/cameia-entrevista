package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.domain.model.RoleStatus;
import tech.cameia.entrevista.domain.port.RoleCatalogReplicaRepository;

class RoleCatalogReplicaRepositoryAdapterTest extends AbstractPersistenceTest {

    private static final Instant T1100 = Instant.parse("2026-10-24T11:00:00.000Z");
    private static final Instant T1200 = Instant.parse("2026-10-24T12:00:00.123Z");
    private static final Instant T1300 = Instant.parse("2026-10-24T13:00:00.000Z");
    private static final UUID FRONTEND = UUID.fromString("a0000001-0000-0000-0000-000000000001");
    private static final UUID BACKEND = UUID.fromString("a0000001-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private RoleCatalogReplicaRepository roles;

    @BeforeEach
    void cleanRoles() {
        jdbc.execute("TRUNCATE microentrevista.rol_profesional");
    }

    private static ProfessionalRoleReplica role(UUID id, String nameEs, RoleStatus status) {
        return new ProfessionalRoleReplica(id, nameEs, nameEs + " (en)", status);
    }

    private static List<ProfessionalRoleReplica> two(String frontend, RoleStatus backendStatus) {
        return List.of(role(FRONTEND, frontend, RoleStatus.ACTIVE), role(BACKEND, "Backend", backendStatus));
    }

    private int upsert(List<ProfessionalRoleReplica> list, Instant snapshotAt) {
        return transaction.execute(status -> roles.upsertSnapshot(list, snapshotAt, CLOCK.instant()));
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM microentrevista.rol_profesional", Integer.class);
    }

    private String nameEs(UUID id) {
        return jdbc.queryForObject("SELECT nombre_es FROM microentrevista.rol_profesional WHERE id = ?",
                String.class, id);
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT estado FROM microentrevista.rol_profesional WHERE id = ?",
                String.class, id);
    }

    @Test
    @DisplayName("E-01: la primera instantánea con la tabla vacía inserta todos los roles con su fecha")
    void upsert_shouldInsertAll_whenTableEmpty() {
        assertThat(upsert(two("Frontend", RoleStatus.INACTIVE), T1200)).isEqualTo(2);

        assertThat(count()).isEqualTo(2);
        assertThat(status(FRONTEND)).isEqualTo("ACTIVE");
        assertThat(status(BACKEND)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM microentrevista.rol_profesional WHERE id = ?",
                Timestamp.class, FRONTEND).toInstant()).isEqualTo(T1200);
        assertThat(jdbc.queryForObject("SELECT fecha_replicacion FROM microentrevista.rol_profesional WHERE id = ?",
                Timestamp.class, FRONTEND).toInstant()).isEqualTo(CLOCK.instant());
        assertThat(jdbc.queryForObject("SELECT nombre_en FROM microentrevista.rol_profesional WHERE id = ?",
                String.class, FRONTEND)).isEqualTo("Frontend (en)");
    }

    @Test
    @DisplayName("E-03: una instantánea anterior no cambia ninguna fila y devuelve 0")
    void upsert_shouldChangeNothing_whenSnapshotOlder() {
        upsert(two("Nuevo", RoleStatus.ACTIVE), T1200);

        assertThat(upsert(two("Viejo", RoleStatus.INACTIVE), T1100)).isZero();

        assertThat(nameEs(FRONTEND)).isEqualTo("Nuevo");
        assertThat(status(BACKEND)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("E-04: una instantánea con el mismo instante se aplica")
    void upsert_shouldApply_whenSameInstant() {
        upsert(two("Primero", RoleStatus.ACTIVE), T1200);

        assertThat(upsert(two("Segundo", RoleStatus.ACTIVE), T1200)).isEqualTo(2);

        assertThat(nameEs(FRONTEND)).isEqualTo("Segundo");
    }

    @Test
    @DisplayName("E-05: un rol pasa de ACTIVE a INACTIVE y vuelve; la fila sigue a la última instantánea")
    void upsert_shouldFollowLatestStatus() {
        upsert(two("F", RoleStatus.ACTIVE), T1100);
        upsert(two("F", RoleStatus.INACTIVE), T1200);
        assertThat(status(BACKEND)).isEqualTo("INACTIVE");

        upsert(two("F", RoleStatus.ACTIVE), T1300);

        assertThat(status(BACKEND)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("E-06: un rol que la instantánea nueva no trae se conserva sin cambios")
    void upsert_shouldKeepRolesMissingFromSnapshot() {
        upsert(two("F", RoleStatus.ACTIVE), T1100);

        assertThat(upsert(List.of(role(FRONTEND, "Solo frontend", RoleStatus.ACTIVE)), T1200)).isEqualTo(1);

        assertThat(count()).isEqualTo(2);
        assertThat(nameEs(BACKEND)).isEqualTo("Backend");
        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM microentrevista.rol_profesional WHERE id = ?",
                Timestamp.class, BACKEND).toInstant()).isEqualTo(T1100);
    }

    @Test
    @DisplayName("Una instantánea mixta cambia solo las filas que no son más nuevas que ella")
    void upsert_shouldChangeOnlyRowsNotNewer() {
        upsert(List.of(role(FRONTEND, "Nuevo", RoleStatus.ACTIVE)), T1300);
        upsert(List.of(role(BACKEND, "Viejo", RoleStatus.ACTIVE)), T1100);

        assertThat(upsert(two("Intento", RoleStatus.INACTIVE), T1200)).isEqualTo(1);

        assertThat(nameEs(FRONTEND)).isEqualTo("Nuevo");
        assertThat(status(BACKEND)).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("E-07: dos hilos con los mismos roles dejan una fila por rol y ningún error")
    void upsert_shouldNotFail_whenConcurrent() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> startThenUpsert(start, "Uno"));
            Future<Integer> second = pool.submit(() -> startThenUpsert(start, "Dos"));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(count()).isEqualTo(2);
        assertThat(nameEs(FRONTEND)).isIn("Uno", "Dos");
    }

    private int startThenUpsert(CountDownLatch start, String name) throws InterruptedException {
        start.await();
        return upsert(two(name, RoleStatus.ACTIVE), T1200);
    }

    @Test
    @DisplayName("E-09: el texto con marcado, comillas y emoji se guarda literal")
    void upsert_shouldStoreHostileTextLiterally() {
        String hostile = "<script>alert(1)</script>' OR '1'='1 -- 100% _ 😀 \\ \"";

        upsert(List.of(role(FRONTEND, hostile, RoleStatus.ACTIVE)), T1200);

        assertThat(nameEs(FRONTEND)).isEqualTo(hostile);
    }

    @Test
    @DisplayName("E-10: 200 roles se guardan en una sentencia")
    void upsert_shouldStore200Roles() {
        List<ProfessionalRoleReplica> many = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            many.add(role(UUID.randomUUID(), "Rol " + i, RoleStatus.ACTIVE));
        }

        assertThat(upsert(many, T1200)).isEqualTo(200);

        assertThat(count()).isEqualTo(200);
    }

    @Test
    @DisplayName("Dos roles con el mismo identificador en una instantánea hacen fallar la sentencia y no dejan filas")
    void upsert_shouldFail_whenDuplicatedIdInSnapshot() {
        List<ProfessionalRoleReplica> duplicated = List.of(role(FRONTEND, "A", RoleStatus.ACTIVE),
                role(FRONTEND, "B", RoleStatus.ACTIVE));

        assertThatThrownBy(() -> upsert(duplicated, T1200)).isInstanceOf(Exception.class);
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("Escribir el catálogo fuera de una transacción falla")
    void upsert_shouldFail_whenOutsideTransaction() {
        assertThatThrownBy(() -> roles.upsertSnapshot(two("F", RoleStatus.ACTIVE), T1200, CLOCK.instant()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
