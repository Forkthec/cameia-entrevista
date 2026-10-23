package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Un catálogo publicado en el exchange de Perfil llega a la copia local; uno inválido va a la cola de fallidos. */
class RoleCatalogBrokerTest extends ProfileEventsBrokerSupport {

    @BeforeEach
    void clean() {
        resetState();
    }

    @Test
    @DisplayName("Al arrancar se declaran la cola del catálogo y su cola de fallidos")
    void topology_shouldDeclareCatalogQueues_whenStarted() {
        assertThat(ready(ROLES_QUEUE)).isZero();
        assertThat(ready(ROLES_DLQ)).isZero();
    }

    @Test
    @DisplayName("Una instantánea de 46 roles guarda 46 filas y deja la cola sin mensajes sin confirmar")
    void replication_shouldStoreAllRolesAndAck_whenPublished() {
        publish(ROLES_KEY, catalogBody(46, "2026-10-24T12:00:00.123Z"), UUID.randomUUID(), 1);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(count("rol_profesional")).isEqualTo(46);
            assertThat(ready(ROLES_QUEUE)).isZero();
            assertThat(unacknowledged(ROLES_QUEUE)).isZero();
        });
        assertThat(ready(ROLES_DLQ)).isZero();
    }

    @Test
    @DisplayName("Una instantánea con 201 roles va a la cola de fallidos sin reintentos y sin filas")
    void deadLetter_shouldRouteToDlq_whenContractViolated() {
        publish(ROLES_KEY, catalogBody(201, "2026-10-24T12:00:00.123Z"), UUID.randomUUID(), 1);

        awaitDeadLettered(ROLES_QUEUE, ROLES_DLQ);

        assertThat(roleProbe().upsertCalls()).isZero();
        assertThat(count("rol_profesional")).isZero();
    }
}
