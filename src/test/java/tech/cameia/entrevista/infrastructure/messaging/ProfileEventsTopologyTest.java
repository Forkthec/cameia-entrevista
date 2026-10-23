package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * Topología que el servicio declara en un RabbitMQ real: los nombres del contrato, las colas de
 * fallidos y los argumentos de fallidos de la cola principal.
 */
class ProfileEventsTopologyTest extends ProfileEventsBrokerSupport {

    @Autowired
    @Qualifier("profileUpdatedQueue")
    private Queue profileUpdatedQueue;

    @Test
    @DisplayName("Al arrancar existen las colas del contrato")
    void topology_shouldDeclareContractNames_whenStarted() {
        assertThat(rabbitAdmin.getQueueInfo(PROFILE_QUEUE)).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo(PROFILE_DLQ)).isNotNull();
        assertThatCode(() -> rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(EXCHANGE)))
                .doesNotThrowAnyException();
        assertThatCode(() -> rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive("entrevista.dlx")))
                .doesNotThrowAnyException();
    }

    /**
     * RabbitMQ no devuelve los argumentos de una cola por AMQP; se comprueban volviendo a declararla:
     * con sus argumentos reales la declaración es idempotente y sin ellos el broker responde
     * {@code PRECONDITION_FAILED}.
     */
    @Test
    @DisplayName("La cola lleva entrevista.dlx y su propio nombre como destino de los fallidos")
    void topology_shouldRejectDifferentArguments_whenQueueIsRedeclared() {
        assertThat(profileUpdatedQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", "entrevista.dlx")
                .containsEntry("x-dead-letter-routing-key", PROFILE_QUEUE);

        rabbitTemplate.execute(channel ->
                channel.queueDeclare(PROFILE_QUEUE, true, false, false, profileUpdatedQueue.getArguments()));

        assertThatThrownBy(() -> rabbitTemplate.execute(channel ->
                channel.queueDeclare(PROFILE_QUEUE, true, false, false, Map.of())))
                .isInstanceOf(AmqpIOException.class)
                .rootCause().isInstanceOf(ShutdownSignalException.class).hasMessageContaining("PRECONDITION_FAILED");
    }
}
