package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.infrastructure.messaging.consumer.ProfileUpdatedListener;
import tech.cameia.entrevista.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;
import tech.cameia.entrevista.support.ReplicaProbeConfiguration;
import tech.cameia.entrevista.support.ReplicaRepositoryProbe;
import tech.cameia.entrevista.support.WithLanguageModelProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entrega mensajes directamente al consumidor, con el cuerpo del contrato y PostgreSQL real, para
 * comprobar la copia, la idempotencia, el orden y el rechazo por cambio de dueño sin depender del
 * broker. El consumidor de RabbitMQ queda apagado en estas pruebas.
 */
@SpringBootTest(properties = {"cameia.llm.model=modelo-de-prueba"})
@WithLanguageModelProperties
@Import(ReplicaProbeConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ProfileReplicationIntegrationTest {

    private static final String MESSAGE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String TYPE = "perfil.profesional-actualizado";
    private static final String OWNER = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";

    /** Ejemplo del contrato v1. */
    private static final String VALID_BODY = """
            {"usuarioId":"6f1d2c3b4a5e4f60718293a4b5c6d7e8","contextoProfesional":{"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7",\
            "nombre":"Analista de datos","estado":"COMPLETED","fecha_archivado":null,\
            "resumen":"Analista con tres años de experiencia en SQL y Python.",\
            "experiencias":[{"empresa":"ACME","cargo":"Analista","descripcion":null,"inicio":"2022-01",\
            "fin":null,"estadoEmpleo":"CURRENT"}],\
            "formaciones":[{"institucion":"Universidad del Cauca","titulo":"Ingeniería de Sistemas",\
            "nivel":"UNDERGRADUATE","inicio":"2017-02","fin":"2021-12","enCurso":false}],\
            "habilidades":[{"nombre":"Python","nivel":"INTERMEDIATE"}]},\
            "rolesAspirados":[{"codigo":"a0000001-0000-0000-0000-000000000002","nombre":"Desarrollador Backend"}],\
            "actualizadoEn":"2026-10-15T12:00:00.123Z"}""";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private ProfileUpdatedListener listener;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProfileReplicaRepository replicas;

    @BeforeEach
    void clean() {
        ((ReplicaRepositoryProbe) replicas).reset();
        jdbc.execute("TRUNCATE microentrevista.contexto_profesional_replica, microentrevista.evento_procesado");
    }

    private void deliver(String body, String messageId) {
        ProfileUpdatedPayloadV1 payload = mapper.readValue(body, ProfileUpdatedPayloadV1.class);
        listener.onProfileUpdated(payload, messageId, TYPE, Map.of("x-event-version", 1), "req-123");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM microentrevista." + table, Integer.class);
    }

    private String name() {
        return jdbc.queryForObject("SELECT nombre FROM microentrevista.contexto_profesional_replica", String.class);
    }

    @Test
    @DisplayName("Un evento válido guarda la copia completada y anota el mensaje en el Inbox")
    void replication_shouldStoreCopy_whenValidEvent() {
        deliver(VALID_BODY, MESSAGE_ID);

        assertThat(replicas.findById(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7")).orElseThrow()
                .isUsableForInterview()).isTrue();
        assertThat(jdbc.queryForObject("SELECT estado FROM microentrevista.contexto_profesional_replica",
                String.class)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT message_id::text FROM microentrevista.evento_procesado",
                String.class)).isEqualTo(MESSAGE_ID);
    }

    @Test
    @DisplayName("El mismo mensaje entregado 20 veces deja una fila y no cambia la fecha de réplica")
    void replication_shouldApplyOnce_when20Redeliveries() {
        deliver(VALID_BODY, MESSAGE_ID);
        Timestamp first = jdbc.queryForObject(
                "SELECT fecha_replicacion FROM microentrevista.contexto_profesional_replica", Timestamp.class);

        for (int i = 0; i < 19; i++) {
            deliver(VALID_BODY, MESSAGE_ID);
        }

        assertThat(count("contexto_profesional_replica")).isEqualTo(1);
        assertThat(count("evento_procesado")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT fecha_replicacion FROM microentrevista.contexto_profesional_replica",
                Timestamp.class)).isEqualTo(first);
    }

    @Test
    @DisplayName("Un evento más viejo que llega después no sobrescribe al más nuevo")
    void replication_shouldKeepNewest_whenOlderArrivesLater() {
        deliver(VALID_BODY.replace("2026-10-15T12:00:00.123Z", "2026-10-15T12:00:00.200Z")
                .replace("Analista de datos", "Nuevo"), MESSAGE_ID);
        deliver(VALID_BODY.replace("2026-10-15T12:00:00.123Z", "2026-10-15T12:00:00.100Z")
                .replace("Analista de datos", "Viejo"), "22222222-2222-4222-8222-222222222222");

        assertThat(name()).isEqualTo("Nuevo");
        assertThat(count("evento_procesado")).isEqualTo(2);
    }

    @Test
    @DisplayName("Un mensaje del mismo perfil con otro dueño se rechaza y la copia conserva su dueño")
    void replication_shouldReject_whenOwnerChanges() {
        deliver(VALID_BODY, MESSAGE_ID);

        assertThatThrownBy(() -> deliver(VALID_BODY.replace(OWNER, "otro-dueno"),
                "22222222-2222-4222-8222-222222222222"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("PROFILE_OWNER_MISMATCH");

        assertThat(jdbc.queryForObject("SELECT firebase_uid FROM microentrevista.contexto_profesional_replica",
                String.class)).isEqualTo(OWNER);
        assertThat(count("evento_procesado")).isEqualTo(1);
    }

    @Test
    @DisplayName("Si la base falla una vez no quedan filas parciales y la segunda entrega aplica el mensaje una vez")
    void replication_shouldRollBackAndApplyOnce_whenDatabaseFailsOnce() {
        AtomicInteger calls = new AtomicInteger();
        ((ReplicaRepositoryProbe) replicas).aroundUpsert(real -> () -> {
            boolean stored = real.getAsBoolean();
            if (calls.incrementAndGet() == 1) {
                throw new DataAccessResourceFailureException("caida");
            }
            return stored;
        });

        assertThatThrownBy(() -> deliver(VALID_BODY, MESSAGE_ID))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(count("contexto_profesional_replica")).isZero();
        assertThat(count("evento_procesado")).isZero();

        deliver(VALID_BODY, MESSAGE_ID);

        assertThat(count("contexto_profesional_replica")).isEqualTo(1);
        assertThat(count("evento_procesado")).isEqualTo(1);
    }
}
