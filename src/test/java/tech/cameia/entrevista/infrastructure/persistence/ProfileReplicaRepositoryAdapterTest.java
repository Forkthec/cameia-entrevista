package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.support.ReplicaFixtures;

class ProfileReplicaRepositoryAdapterTest extends AbstractPersistenceTest {

    private static final Instant T100 = Instant.parse("2026-10-15T12:00:00.100Z");
    private static final Instant T122 = Instant.parse("2026-10-15T12:00:00.122Z");
    private static final Instant T123 = Instant.parse("2026-10-15T12:00:00.123Z");
    private static final Instant T200 = Instant.parse("2026-10-15T12:00:00.200Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProfileReplicaRepository replicas;

    @BeforeEach
    void cleanReplica() {
        jdbc.execute("TRUNCATE microentrevista.contexto_profesional_replica");
    }

    private boolean upsert(ProfileReplica replica) {
        return transaction.execute(status -> replicas.upsertIfNotOlder(replica, CLOCK.instant()));
    }

    @Test
    @DisplayName("Una copia nueva se inserta")
    void upsert_shouldInsert_whenNew() {
        assertThat(upsert(ReplicaFixtures.replica(T123))).isTrue();

        ProfileReplica stored = replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow();
        assertThat(stored.owner()).isEqualTo(ReplicaFixtures.OWNER);
        assertThat(stored.name()).isEqualTo("Analista de datos");
        assertThat(stored.status()).isEqualTo(ReplicatedProfileStatus.COMPLETED);
        assertThat(stored.archivedAt()).isNull();
        assertThat(stored.updatedAt()).isEqualTo(T123);
        assertThat(stored.professionalContextJson()).contains("Analista con tres años");
        assertThat(stored.aspiredRolesJson()).contains("a0000001-0000-0000-0000-000000000002");
        assertThat(stored.isUsableForInterview()).isTrue();
    }

    @Test
    @DisplayName("El estado y la fecha de archivado se guardan tal como llegan")
    void upsert_shouldKeepStatusAndArchivedAt() {
        Instant archived = Instant.parse("2026-10-15T12:00:00Z");
        ProfileReplica archivedReplica = new ProfileReplica(ReplicaFixtures.PROFILE_ID, ReplicaFixtures.OWNER,
                "Analista", ReplicatedProfileStatus.IN_REVIEW, archived, ReplicaFixtures.CONTEXT_JSON,
                ReplicaFixtures.ASPIRED_ROLES_JSON, T123);

        upsert(archivedReplica);

        ProfileReplica stored = replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow();
        assertThat(stored.status()).isEqualTo(ReplicatedProfileStatus.IN_REVIEW);
        assertThat(stored.archivedAt()).isEqualTo(archived);
        assertThat(stored.isUsableForInterview()).isFalse();
    }

    @Test
    @DisplayName("Un mensaje 1 ms más viejo que la copia no la cambia")
    void upsert_shouldIgnore_whenOlderThanCopy() {
        upsert(ReplicaFixtures.replica(ReplicaFixtures.OWNER, "Nuevo", T123));

        assertThat(upsert(ReplicaFixtures.replica(ReplicaFixtures.OWNER, "Viejo", T122))).isFalse();

        assertThat(replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow().name()).isEqualTo("Nuevo");
    }

    @Test
    @DisplayName("Un mensaje con el mismo instante que la copia la reemplaza")
    void upsert_shouldApply_whenSameInstant() {
        upsert(ReplicaFixtures.replica(ReplicaFixtures.OWNER, "Primero", T123));

        assertThat(upsert(ReplicaFixtures.replica(ReplicaFixtures.OWNER, "Segundo", T123))).isTrue();

        assertThat(replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow().name()).isEqualTo("Segundo");
    }

    @Test
    @DisplayName("Un mensaje de otro dueño no reemplaza la copia aunque sea más nuevo")
    void upsert_shouldNotChangeOwner_whenOwnerDiffers() {
        upsert(ReplicaFixtures.replica(T100));

        boolean changed = upsert(ReplicaFixtures.replica(new FirebaseUid("uid-b"), "Robado", T200));

        assertThat(changed).isFalse();
        ProfileReplica stored = replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow();
        assertThat(stored.owner()).isEqualTo(ReplicaFixtures.OWNER);
        assertThat(stored.name()).isEqualTo("Analista de datos");
    }

    @Test
    @DisplayName("El texto hostil se guarda literal y sin interpretar")
    void upsert_shouldStoreHostileTextLiterally() {
        String hostile = "<script>alert(1)</script>' OR '1'='1 -- 100% _ 😀 \\";

        upsert(ReplicaFixtures.replica(ReplicaFixtures.OWNER, hostile, T123));

        assertThat(replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow().name()).isEqualTo(hostile);
    }

    @Test
    @DisplayName("Con dos hilos y mensajes fuera de orden queda el más nuevo")
    void upsert_shouldKeepNewest_whenConcurrent() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> older = pool.submit(() -> startThenUpsert(start, "Viejo", T100));
            Future<Boolean> newer = pool.submit(() -> startThenUpsert(start, "Nuevo", T200));
            start.countDown();
            older.get(10, TimeUnit.SECONDS);
            newer.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        ProfileReplica stored = replicas.findById(ReplicaFixtures.PROFILE_ID).orElseThrow();
        assertThat(stored.name()).isEqualTo("Nuevo");
        assertThat(stored.updatedAt()).isEqualTo(T200);
    }

    private boolean startThenUpsert(CountDownLatch start, String name, Instant updatedAt)
            throws InterruptedException {
        start.await();
        return upsert(ReplicaFixtures.replica(ReplicaFixtures.OWNER, name, updatedAt));
    }

    @Test
    @DisplayName("Una copia que nunca llegó no existe")
    void findById_shouldReturnEmpty_whenNeverReplicated() {
        assertThat(replicas.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Una copia replicada se devuelve completa")
    void findById_shouldReturnCopy_whenReplicated() {
        upsert(ReplicaFixtures.replica(T123));

        assertThat(replicas.findById(ReplicaFixtures.PROFILE_ID)).isPresent();
    }

    @Test
    @DisplayName("Escribir la copia fuera de una transacción falla")
    void upsert_shouldFail_whenOutsideTransaction() {
        assertThatThrownBy(() -> replicas.upsertIfNotOlder(ReplicaFixtures.replica(T123), CLOCK.instant()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
