package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.cameia.entrevista.infrastructure.messaging.ProfileEventsBrokerSupport.catalogBody;
import static tech.cameia.entrevista.infrastructure.messaging.ProfileEventsBrokerSupport.roleId;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.port.RoleCatalogReplicaRepository;
import tech.cameia.entrevista.infrastructure.messaging.consumer.RoleCatalogListener;
import tech.cameia.entrevista.infrastructure.messaging.payload.RoleCatalogPayloadV1;
import tech.cameia.entrevista.support.ReplicaProbeConfiguration;
import tech.cameia.entrevista.support.RoleCatalogRepositoryProbe;
import tech.cameia.entrevista.support.WithLanguageModelProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entrega instantáneas del catálogo directamente al consumidor, con PostgreSQL real, para comprobar
 * la copia, la idempotencia, el orden y los fallos sin depender del broker. El consumidor de RabbitMQ
 * queda apagado en estas pruebas.
 */
@SpringBootTest(properties = {"cameia.llm.model=modelo-de-prueba"})
@WithLanguageModelProperties
@Import(ReplicaProbeConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class RoleCatalogReplicationIntegrationTest {

    private static final String TYPE = "perfil.catalogo-roles-actualizado";
    private static final String MESSAGE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_ID = "22222222-2222-4222-8222-222222222222";
    private static final String THIRD_ID = "33333333-3333-4333-8333-333333333333";
    private static final String NOON = "2026-10-24T12:00:00.123Z";
    private static final String ELEVEN = "2026-10-24T11:00:00.000Z";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private RoleCatalogListener listener;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RoleCatalogReplicaRepository catalog;

    @BeforeEach
    void clean() {
        ((RoleCatalogRepositoryProbe) catalog).reset();
        jdbc.execute("TRUNCATE microentrevista.rol_profesional, microentrevista.evento_procesado");
    }

    private void deliver(String body, String messageId) {
        RoleCatalogPayloadV1 payload = mapper.readValue(body, RoleCatalogPayloadV1.class);
        listener.onRoleCatalogUpdated(payload, messageId, TYPE, Map.of("x-event-version", 1), "req-123");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM microentrevista." + table, Integer.class);
    }

    private String column(String column, int role) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM microentrevista.rol_profesional "
                + "WHERE id = ?::uuid", String.class, roleId(role).toString());
    }

    @Test
    @DisplayName("E-01: la primera instantánea guarda todos los roles con la fecha del mensaje")
    void replication_shouldStoreRoles_whenFirstSnapshot() {
        deliver(catalogBody(2, NOON), MESSAGE_ID);

        assertThat(count("rol_profesional")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT DISTINCT actualizado_en FROM microentrevista.rol_profesional",
                Timestamp.class).toInstant()).isEqualTo(Instant.parse(NOON));
    }

    @Test
    @DisplayName("E-02: el mismo mensaje entregado 20 veces deja una fila de Inbox y no cambia la fecha de réplica")
    void replication_shouldApplyOnce_when20Redeliveries() {
        deliver(catalogBody(2, NOON), MESSAGE_ID);
        Timestamp first = jdbc.queryForObject(
                "SELECT min(fecha_replicacion) FROM microentrevista.rol_profesional", Timestamp.class);

        for (int i = 0; i < 19; i++) {
            deliver(catalogBody(2, NOON), MESSAGE_ID);
        }

        assertThat(count("evento_procesado")).isEqualTo(1);
        assertThat(count("rol_profesional")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT min(fecha_replicacion) FROM microentrevista.rol_profesional",
                Timestamp.class)).isEqualTo(first);
    }

    @Test
    @DisplayName("E-03: una instantánea anterior que llega después no cambia ninguna fila")
    void replication_shouldKeepNewest_whenOlderArrivesLater() {
        deliver(catalogBody(2, NOON), MESSAGE_ID);

        deliver(catalogBody(2, ELEVEN).replace("Rol 1", "Viejo"), OTHER_ID);

        assertThat(column("nombre_es", 1)).isEqualTo("Rol 1");
        assertThat(count("evento_procesado")).isEqualTo(2);
    }

    @Test
    @DisplayName("E-04: la misma fecha con otro mensaje se aplica")
    void replication_shouldApply_whenSameInstantOtherMessage() {
        deliver(catalogBody(2, NOON), MESSAGE_ID);

        deliver(catalogBody(2, NOON).replace("Rol 1", "Nuevo"), OTHER_ID);

        assertThat(column("nombre_es", 1)).isEqualTo("Nuevo");
    }

    @Test
    @DisplayName("E-05: un rol pasa a INACTIVE y vuelve a ACTIVE siguiendo el último mensaje")
    void replication_shouldFollowLastStatus_whenRoleToggles() {
        deliver(catalogBody(2, "2026-10-24T10:00:00.000Z"), MESSAGE_ID);
        deliver(catalogBody(2, ELEVEN).replaceFirst("\"estado\":\"ACTIVE\"", "\"estado\":\"INACTIVE\""), OTHER_ID);
        assertThat(column("estado", 1)).isEqualTo("INACTIVE");

        deliver(catalogBody(2, "2026-10-24T12:00:00.000Z"), THIRD_ID);

        assertThat(column("estado", 1)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("E-06: un rol que no viene en la instantánea nueva se conserva sin cambios")
    void replication_shouldKeepMissingRole_whenSnapshotOmitsIt() {
        deliver(catalogBody(46, ELEVEN), MESSAGE_ID);

        deliver(catalogBody(45, NOON), OTHER_ID);

        assertThat(count("rol_profesional")).isEqualTo(46);
        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM microentrevista.rol_profesional WHERE id = ?::uuid",
                Timestamp.class, roleId(46).toString()).toInstant()).isEqualTo(Instant.parse(ELEVEN));
    }

    @Test
    @DisplayName("E-07: dos instantáneas a la vez dejan una fila por rol y ningún error")
    void replication_shouldNotFail_whenTwoSnapshotsRunConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> {
                awaitStart(start);
                deliver(catalogBody(46, NOON), MESSAGE_ID);
            });
            Future<?> second = pool.submit(() -> {
                awaitStart(start);
                deliver(catalogBody(46, NOON), OTHER_ID);
            });
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("rol_profesional")).isEqualTo(46);
        assertThat(count("evento_procesado")).isEqualTo(2);
    }

    private static void awaitStart(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("E-08: si la base falla una vez no quedan filas parciales y la segunda entrega aplica")
    void replication_shouldRollBackAndApplyOnce_whenDatabaseFailsOnce() {
        AtomicInteger calls = new AtomicInteger();
        ((RoleCatalogRepositoryProbe) catalog).aroundUpsert(real -> () -> {
            int stored = real.getAsInt();
            if (calls.incrementAndGet() == 1) {
                throw new DataAccessResourceFailureException("caida");
            }
            return stored;
        });

        assertThatThrownBy(() -> deliver(catalogBody(2, NOON), MESSAGE_ID))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(count("rol_profesional")).isZero();
        assertThat(count("evento_procesado")).isZero();

        deliver(catalogBody(2, NOON), MESSAGE_ID);

        assertThat(count("rol_profesional")).isEqualTo(2);
        assertThat(count("evento_procesado")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un rol repetido dentro de la instantánea se rechaza sin guardar nada")
    void replication_shouldReject_whenRoleDuplicated() {
        String duplicated = catalogBody(2, NOON).replace(roleId(2).toString(), roleId(1).toString());

        assertThatThrownBy(() -> deliver(duplicated, MESSAGE_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("CATALOG_ROLE_DUPLICATED");
        assertThat(count("rol_profesional")).isZero();
        assertThat(count("evento_procesado")).isZero();
    }

    @Test
    @DisplayName("E-09: un nombre con marcado se guarda literal")
    void replication_shouldStoreLiteral_whenNameHasMarkup() {
        deliver(catalogBody(1, NOON).replace("Rol 1", "<script>alert(1)</script>"), MESSAGE_ID);

        assertThat(column("nombre_es", 1)).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    @DisplayName("E-10: 200 roles se guardan y 201 se rechazan por el contrato")
    void replication_shouldAccept200AndReject201() {
        deliver(catalogBody(200, NOON), MESSAGE_ID);
        assertThat(count("rol_profesional")).isEqualTo(200);

        assertThatThrownBy(() -> deliver(catalogBody(201, "2026-10-24T13:00:00.000Z"), OTHER_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PAYLOAD_CONTRACT_VIOLATION");
        assertThat(count("rol_profesional")).isEqualTo(200);
    }

    @Test
    @DisplayName("E-11: el evento del catálogo no toca la tabla del perfil")
    void replication_shouldNotTouchProfileTable() {
        deliver(catalogBody(2, NOON), MESSAGE_ID);

        assertThat(count("contexto_profesional_replica")).isZero();
    }
}
