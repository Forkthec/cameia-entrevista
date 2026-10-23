package tech.cameia.entrevista.infrastructure.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;

class RoleCatalogRabbitConfigTest {

    private final RoleCatalogRabbitConfig config = new RoleCatalogRabbitConfig();
    private final ProfileEventsRabbitConfig shared = new ProfileEventsRabbitConfig();

    @Test
    @DisplayName("La cola del catálogo lleva los argumentos de fallidos que declara Perfil")
    void roleCatalogQueue_shouldCarryDeadLetterArguments() {
        var queue = config.roleCatalogQueue();

        assertThat(queue.getName()).isEqualTo("entrevista.catalogo-roles-actualizado");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.getArguments()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "x-dead-letter-exchange", "entrevista.dlx",
                "x-dead-letter-routing-key", "entrevista.catalogo-roles-actualizado"));
    }

    @Test
    @DisplayName("La cola de fallidos del catálogo es durable y no lleva argumentos")
    void deadLetterQueue_shouldBeDurableWithoutArguments() {
        var queue = config.roleCatalogDeadLetterQueue();

        assertThat(queue.getName()).isEqualTo("entrevista.catalogo-roles-actualizado.dlq");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).isEmpty();
    }

    @Test
    @DisplayName("Los enlaces usan las claves del contrato")
    void bindings_shouldUseContractKeys() {
        Binding main = config.roleCatalogBinding(shared.perfilExchange(), config.roleCatalogQueue());
        Binding dead = config.roleCatalogDeadLetterBinding(shared.deadLetterExchange(),
                config.roleCatalogDeadLetterQueue());

        assertThat(main.getExchange()).isEqualTo("perfil.events");
        assertThat(main.getDestination()).isEqualTo("entrevista.catalogo-roles-actualizado");
        assertThat(main.getRoutingKey()).isEqualTo("perfil.catalogo-roles-actualizado");
        assertThat(dead.getExchange()).isEqualTo("entrevista.dlx");
        assertThat(dead.getDestination()).isEqualTo("entrevista.catalogo-roles-actualizado.dlq");
        assertThat(dead.getRoutingKey()).isEqualTo("entrevista.catalogo-roles-actualizado");
    }

    @Test
    @DisplayName("Las constantes públicas coinciden con el contrato")
    void constants_shouldMatchContract() {
        assertThat(RoleCatalogRabbitConfig.ROUTING_KEY).isEqualTo("perfil.catalogo-roles-actualizado");
        assertThat(RoleCatalogRabbitConfig.QUEUE).isEqualTo("entrevista.catalogo-roles-actualizado");
        assertThat(RoleCatalogRabbitConfig.DEAD_LETTER_QUEUE).isEqualTo("entrevista.catalogo-roles-actualizado.dlq");
    }
}
