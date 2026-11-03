package tech.cameia.entrevista.infrastructure.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara el exchange de Perfil, la cola donde Entrevista recibe los cambios del perfil, su cola de
 * fallidos y el convertidor JSON.
 *
 * <p>Los atributos son los mismos que declara Perfil para el exchange y para la cola; si no
 * coincidieran, el broker rechazaría la declaración de quien llegue segundo. Los exchanges y el
 * convertidor los comparten los demás consumidores de Entrevista.</p>
 */
@Configuration
public class ProfileEventsRabbitConfig {

    /** Exchange donde Perfil publica sus eventos. */
    public static final String PROFILE_EXCHANGE = "perfil.events";

    /** Clave de enrutamiento de los cambios del perfil. */
    public static final String ROUTING_KEY = "perfil.profesional-actualizado";

    /** Cola donde Entrevista recibe los cambios del perfil. */
    public static final String QUEUE = "entrevista.perfil-profesional-actualizado";

    /** Exchange de mensajes fallidos de Entrevista. */
    public static final String DEAD_LETTER_EXCHANGE = "entrevista.dlx";

    /** Cola de fallidos de los cambios del perfil. */
    public static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    /** @return exchange de tipo tema, durable y sin borrado automático, donde Perfil publica sus eventos */
    @Bean
    TopicExchange perfilExchange() {
        return new TopicExchange(PROFILE_EXCHANGE, true, false);
    }

    /** @return exchange directo, durable y sin borrado automático, de los mensajes fallidos de Entrevista */
    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /** @return cola durable cuyos rechazos van a {@value #DEAD_LETTER_EXCHANGE} con su propio nombre como clave */
    @Bean
    Queue profileUpdatedQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey(QUEUE)
                .build();
    }

    /** @return cola de fallidos durable y sin argumentos */
    @Bean
    Queue profileUpdatedDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /**
     * @param perfilExchange     exchange de los eventos de Perfil
     * @param profileUpdatedQueue cola de los cambios del perfil
     * @return el enlace con la clave {@value #ROUTING_KEY}
     */
    @Bean
    Binding profileUpdatedBinding(TopicExchange perfilExchange, Queue profileUpdatedQueue) {
        return BindingBuilder.bind(profileUpdatedQueue).to(perfilExchange).with(ROUTING_KEY);
    }

    /**
     * @param deadLetterExchange          exchange de fallidos
     * @param profileUpdatedDeadLetterQueue cola de fallidos del perfil
     * @return el enlace con la clave igual al nombre de la cola de origen
     */
    @Bean
    Binding profileUpdatedDeadLetterBinding(DirectExchange deadLetterExchange, Queue profileUpdatedDeadLetterQueue) {
        return BindingBuilder.bind(profileUpdatedDeadLetterQueue).to(deadLetterExchange).with(QUEUE);
    }

    /**
     * Convertidor JSON de los mensajes. Se usa el tipo del parámetro del consumidor y se ignora el
     * encabezado {@code __TypeId__}: un mensaje no debe elegir qué clase se instancia.
     *
     * @return el convertidor
     */
    @Bean
    JacksonJsonMessageConverter messageConverter() {
        var converter = new JacksonJsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
