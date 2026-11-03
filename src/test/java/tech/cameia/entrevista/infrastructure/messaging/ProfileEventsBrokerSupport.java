package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.domain.port.RoleCatalogReplicaRepository;
import tech.cameia.entrevista.support.ReplicaProbeConfiguration;
import tech.cameia.entrevista.support.ReplicaRepositoryProbe;
import tech.cameia.entrevista.support.RoleCatalogRepositoryProbe;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las pruebas con un PostgreSQL y un RabbitMQ reales y el consumidor de eventos encendido.
 *
 * <p>Los contenedores se arrancan en el inicializador estático (y no con {@code @Container}) para que
 * el contexto de Spring, que se reutiliza entre clases, nunca apunte a un contenedor ya detenido;
 * Testcontainers los retira al terminar la JVM. Se omiten si no hay Docker.</p>
 */
@SpringBootTest(properties = {"cameia.llm.model=modelo-de-prueba",
        "spring.rabbitmq.listener.simple.auto-startup=true"})
@Import(ReplicaProbeConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class ProfileEventsBrokerSupport {

    protected static final String EXCHANGE = "perfil.events";
    protected static final String PROFILE_KEY = "perfil.profesional-actualizado";
    protected static final String PROFILE_QUEUE = "entrevista.perfil-profesional-actualizado";
    protected static final String PROFILE_DLQ = PROFILE_QUEUE + ".dlq";
    protected static final String ROLES_KEY = "perfil.catalogo-roles-actualizado";
    protected static final String ROLES_QUEUE = "entrevista.catalogo-roles-actualizado";
    protected static final String ROLES_DLQ = ROLES_QUEUE + ".dlq";

    /** Identificador del perfil del ejemplo del contrato. */
    protected static final String PROFILE_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    /** Ejemplo del contrato v1 de {@code perfil.profesional-actualizado}. */
    protected static final String VALID_BODY = """
            {"usuarioId":"6f1d2c3b4a5e4f60718293a4b5c6d7e8","contextoProfesional":{"id":"%s",\
            "nombre":"Analista de datos","estado":"COMPLETED","fecha_archivado":null,\
            "resumen":"Analista con tres años de experiencia en SQL y Python.",\
            "experiencias":[{"empresa":"ACME","cargo":"Analista","descripcion":null,"inicio":"2022-01",\
            "fin":null,"estadoEmpleo":"CURRENT"}],\
            "formaciones":[{"institucion":"Universidad del Cauca","titulo":"Ingeniería de Sistemas",\
            "nivel":"UNDERGRADUATE","inicio":"2017-02","fin":"2021-12","enCurso":false}],\
            "habilidades":[{"nombre":"Python","nivel":"INTERMEDIATE"}]},\
            "rolesAspirados":[{"codigo":"a0000001-0000-0000-0000-000000000002","nombre":"Desarrollador Backend"}],\
            "actualizadoEn":"2026-10-15T12:00:00.123Z"}""".formatted(PROFILE_ID);

    /** Instantánea del catálogo con 2 roles, igual al ejemplo del contrato. */
    protected static final String VALID_CATALOG_BODY = catalogBody(2, "2026-10-24T12:00:00.123Z");

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final GenericContainer<?> RABBIT = new GenericContainer<>(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withExposedPorts(5672, 15672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    /**
     * Arma una instantánea del catálogo con {@code roles} roles activos de nombres distintos.
     *
     * @param roles    cantidad de roles
     * @param snapshot instante de la instantánea, en UTC
     * @return el cuerpo JSON del evento
     */
    public static String catalogBody(int roles, String snapshot) {
        StringBuilder items = new StringBuilder();
        for (int i = 1; i <= roles; i++) {
            if (i > 1) {
                items.append(',');
            }
            items.append("{\"codigo\":\"%s\",\"nombreEs\":\"Rol %d\",\"nombreEn\":\"Role %d\",\"estado\":\"ACTIVE\"}"
                    .formatted(roleId(i), i, i));
        }
        return "{\"actualizadoEn\":\"%s\",\"roles\":[%s]}".formatted(snapshot, items);
    }

    /** @return el identificador del rol número {@code n} de {@link #catalogBody} */
    public static UUID roleId(int n) {
        return new UUID(0xa000000000000000L, n);
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected RabbitAdmin rabbitAdmin;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Copia del perfil, envuelta por el decorador de prueba; se ve igual que en producción. */
    @Autowired
    protected ProfileReplicaRepository replicas;

    /** Copia del catálogo de roles, envuelta por el decorador de prueba. */
    @Autowired
    protected RoleCatalogReplicaRepository roleCatalog;

    /** @return el decorador de la copia del catálogo de roles */
    protected RoleCatalogRepositoryProbe roleProbe() {
        return (RoleCatalogRepositoryProbe) roleCatalog;
    }

    /** @return el decorador que cuenta llamadas e inyecta fallos en la copia del perfil */
    protected ReplicaRepositoryProbe probe() {
        return (ReplicaRepositoryProbe) replicas;
    }

    /** Deja las colas, las tablas de la réplica y el decorador de prueba en su estado inicial. */
    protected void resetState() {
        for (String queue : new String[] {PROFILE_QUEUE, PROFILE_DLQ, ROLES_QUEUE, ROLES_DLQ}) {
            rabbitAdmin.purgeQueue(queue, false);
        }
        probe().reset();
        roleProbe().reset();
        jdbc.execute("TRUNCATE microentrevista.contexto_profesional_replica, microentrevista.rol_profesional, "
                + "microentrevista.evento_procesado");
    }

    /** Publica un cuerpo en el exchange de Perfil con las propiedades de un productor real. */
    protected void publish(String routingKey, String body, UUID messageId, Object version) {
        MessageBuilderSupport<Message> builder = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setType(routingKey)
                .setCorrelationId("req-123")
                .setHeader("x-event-version", version);
        if (messageId != null) {
            builder.setMessageId(messageId.toString());
        }
        Message message = builder.build();
        rabbitTemplate.send(EXCHANGE, routingKey, message);
    }

    /** @return mensajes listos de la cola, o {@code -1} si no existe */
    protected long ready(String queue) {
        var info = rabbitAdmin.getQueueInfo(queue);
        return info == null ? -1 : info.getMessageCount();
    }

    /** @return mensajes entregados al consumidor y sin confirmar, según la API de administración */
    protected long unacknowledged(String queue) throws IOException, InterruptedException {
        String credentials = Base64.getEncoder().encodeToString("guest:guest".getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "http://" + RABBIT.getHost() + ":" + RABBIT.getMappedPort(15672) + "/api/queues/%2F/" + queue))
                .header("Authorization", "Basic " + credentials).build();
        JsonNode body = JSON.readTree(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body());
        return body.path("messages_unacknowledged").asLong(-1);
    }

    protected int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM microentrevista." + table, Integer.class);
    }

    /** Espera hasta 15 segundos a que la cola de fallidos tenga el mensaje y la principal quede vacía. */
    protected void awaitDeadLettered(String queue, String deadLetterQueue) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(ready(deadLetterQueue)).isEqualTo(1);
            assertThat(ready(queue)).isZero();
        });
    }
}
