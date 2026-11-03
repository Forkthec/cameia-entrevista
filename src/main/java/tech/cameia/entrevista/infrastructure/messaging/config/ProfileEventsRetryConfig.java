package tech.cameia.entrevista.infrastructure.messaging.config;

import java.util.List;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deja fuera del reintento los mensajes que incumplen el contrato: un dato inválido no mejora al
 * repetirse. Sin esto Spring reintenta todo y cada mensaje inválido haría tres intentos y dejaría
 * tres registros.
 */
@Configuration
public class ProfileEventsRetryConfig {

    /**
     * @return el personalizador del reintento de los consumidores
     */
    @Bean
    RabbitListenerRetrySettingsCustomizer profileEventsRetryCustomizer() {
        return settings -> settings.setExceptionExcludes(List.of(AmqpRejectAndDontRequeueException.class));
    }
}
