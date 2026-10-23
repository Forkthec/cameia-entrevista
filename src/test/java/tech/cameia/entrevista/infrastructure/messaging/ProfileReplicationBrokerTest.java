package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Un mensaje publicado en el exchange de Perfil llega a la copia local, y repetido se aplica una vez. */
class ProfileReplicationBrokerTest extends ProfileEventsBrokerSupport {

    @BeforeEach
    void clean() {
        resetState();
    }

    @Test
    @DisplayName("Un mensaje válido publicado con la clave del contrato guarda la copia completada")
    void replication_shouldStoreCopy_whenPublishedToExchange() {
        UUID messageId = UUID.randomUUID();

        publish(PROFILE_KEY, VALID_BODY, messageId, 1);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT estado FROM microentrevista.contexto_profesional_replica "
                    + "WHERE perfil_id = ?::uuid", String.class, PROFILE_ID)).isEqualTo("COMPLETED");
            assertThat(count("evento_procesado")).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("El mismo mensaje publicado dos veces se consume dos veces y se aplica una sola")
    void replication_shouldApplyOnce_whenSameMessagePublishedTwice() {
        UUID messageId = UUID.randomUUID();

        publish(PROFILE_KEY, VALID_BODY, messageId, 1);
        publish(PROFILE_KEY, VALID_BODY, messageId, 1);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(probe().findCalls()).isEqualTo(2);
            assertThat(ready(PROFILE_QUEUE)).isZero();
        });
        assertThat(count("contexto_profesional_replica")).isEqualTo(1);
        assertThat(count("evento_procesado")).isEqualTo(1);
        assertThat(ready(PROFILE_DLQ)).isZero();
    }
}
