package tech.cameia.entrevista.infrastructure.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.retry.RetryPolicySettings;

import tech.cameia.entrevista.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;

class ProfileEventsRabbitConfigTest {

    private final ProfileEventsRabbitConfig config = new ProfileEventsRabbitConfig();

    @Test
    @DisplayName("El exchange de Perfil es de tipo tema, durable y sin borrado automático")
    void perfilExchange_shouldMatchPerfilDeclaration() {
        var exchange = config.perfilExchange();

        assertThat(exchange.getName()).isEqualTo("perfil.events");
        assertThat(exchange.getType()).isEqualTo("topic");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    @DisplayName("La cola del perfil lleva los argumentos de fallidos que declara Perfil")
    void profileUpdatedQueue_shouldCarryDeadLetterArguments() {
        var queue = config.profileUpdatedQueue();

        assertThat(queue.getName()).isEqualTo("entrevista.perfil-profesional-actualizado");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "x-dead-letter-exchange", "entrevista.dlx",
                "x-dead-letter-routing-key", "entrevista.perfil-profesional-actualizado"));
    }

    @Test
    @DisplayName("El exchange y la cola de fallidos son durables y la cola no lleva argumentos")
    void deadLetter_shouldBeDurableWithoutArguments() {
        var exchange = config.deadLetterExchange();
        var queue = config.profileUpdatedDeadLetterQueue();

        assertThat(exchange.getName()).isEqualTo("entrevista.dlx");
        assertThat(exchange.getType()).isEqualTo("direct");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(queue.getName()).isEqualTo("entrevista.perfil-profesional-actualizado.dlq");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).isEmpty();
    }

    @Test
    @DisplayName("Los enlaces usan las claves del contrato")
    void bindings_shouldUseContractKeys() {
        Binding main = config.profileUpdatedBinding(config.perfilExchange(), config.profileUpdatedQueue());
        Binding dead = config.profileUpdatedDeadLetterBinding(config.deadLetterExchange(),
                config.profileUpdatedDeadLetterQueue());

        assertThat(main.getExchange()).isEqualTo("perfil.events");
        assertThat(main.getDestination()).isEqualTo("entrevista.perfil-profesional-actualizado");
        assertThat(main.getRoutingKey()).isEqualTo("perfil.profesional-actualizado");
        assertThat(dead.getExchange()).isEqualTo("entrevista.dlx");
        assertThat(dead.getDestination()).isEqualTo("entrevista.perfil-profesional-actualizado.dlq");
        assertThat(dead.getRoutingKey()).isEqualTo("entrevista.perfil-profesional-actualizado");
    }

    @Test
    @DisplayName("El convertidor usa el tipo del parámetro y no el encabezado __TypeId__")
    void converter_shouldIgnoreTypeIdHeader() {
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setHeader("__TypeId__", "java.lang.ProcessBuilder");
        properties.setInferredArgumentType(ProfileUpdatedPayloadV1.class);
        var message = new Message("{\"usuarioId\":\"uid\",\"actualizadoEn\":\"2026-10-15T12:00:00.123Z\"}".getBytes(),
                properties);

        Object converted = config.messageConverter().fromMessage(message);

        assertThat(converted).isInstanceOfSatisfying(ProfileUpdatedPayloadV1.class,
                payload -> assertThat(payload.userId()).isEqualTo("uid"));
    }

    @Test
    @DisplayName("El reintento excluye el rechazo por contrato")
    void retryCustomizer_shouldExcludeContractRejections() {
        var settings = new RetryPolicySettings();

        new ProfileEventsRetryConfig().profileEventsRetryCustomizer().customize(settings);

        assertThat(settings.getExceptionExcludes()).containsExactly(AmqpRejectAndDontRequeueException.class);
    }
}
