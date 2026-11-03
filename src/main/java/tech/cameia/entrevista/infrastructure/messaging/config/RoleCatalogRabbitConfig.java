package tech.cameia.entrevista.infrastructure.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara la cola donde Entrevista recibe el catálogo de roles de Perfil, su cola de fallidos y los
 * enlaces de ambas.
 *
 * <p>Los exchanges y el convertidor JSON son los de {@link ProfileEventsRabbitConfig}. Los atributos
 * de la cola son los mismos que declara Perfil; si no coincidieran, el broker rechazaría la
 * declaración de quien llegue segundo.</p>
 */
@Configuration
public class RoleCatalogRabbitConfig {

    /** Clave de enrutamiento de las instantáneas del catálogo de roles. */
    public static final String ROUTING_KEY = "perfil.catalogo-roles-actualizado";

    /** Cola donde Entrevista recibe las instantáneas del catálogo de roles. */
    public static final String QUEUE = "entrevista.catalogo-roles-actualizado";

    /** Cola de fallidos de las instantáneas del catálogo de roles. */
    public static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    /** @return cola durable cuyos rechazos van a {@value ProfileEventsRabbitConfig#DEAD_LETTER_EXCHANGE} con su nombre como clave */
    @Bean
    Queue roleCatalogQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(ProfileEventsRabbitConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QUEUE).build();
    }

    /** @return cola de fallidos durable y sin argumentos */
    @Bean
    Queue roleCatalogDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /**
     * @param perfilExchange   exchange de los eventos de Perfil
     * @param roleCatalogQueue cola del catálogo de roles
     * @return el enlace con la clave {@value #ROUTING_KEY}
     */
    @Bean
    Binding roleCatalogBinding(TopicExchange perfilExchange, Queue roleCatalogQueue) {
        return BindingBuilder.bind(roleCatalogQueue).to(perfilExchange).with(ROUTING_KEY);
    }

    /**
     * @param deadLetterExchange        exchange de fallidos
     * @param roleCatalogDeadLetterQueue cola de fallidos del catálogo
     * @return el enlace con la clave igual al nombre de la cola de origen
     */
    @Bean
    Binding roleCatalogDeadLetterBinding(DirectExchange deadLetterExchange, Queue roleCatalogDeadLetterQueue) {
        return BindingBuilder.bind(roleCatalogDeadLetterQueue).to(deadLetterExchange).with(QUEUE);
    }
}
