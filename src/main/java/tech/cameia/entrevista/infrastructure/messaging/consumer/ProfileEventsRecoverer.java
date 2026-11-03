package tech.cameia.entrevista.infrastructure.messaging.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

/**
 * Recibe el mensaje cuando se agotan los intentos y lo manda a la cola de fallidos de su origen.
 *
 * <p>Es el único sitio que registra en {@code ERROR}, con traza, un fallo técnico: una sola línea
 * por mensaje, con la cola, el identificador y la clase del fallo, y nunca con el cuerpo. La traza
 * va sin los mensajes de las excepciones, porque el de una excepción de persistencia puede traer los
 * valores de las columnas. Un mensaje que incumple el contrato también pasa por aquí (el reintento
 * lo excluye, pero no el recuperador): ya quedó registrado una vez en {@code WARN}, así que solo se
 * rechaza. Rechaza sin reencolar para que el broker lo enrute por la configuración de mensajes
 * fallidos de la cola.</p>
 */
@Component
public class ProfileEventsRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(ProfileEventsRecoverer.class);

    /**
     * Registra el mensaje agotado y lo rechaza sin reencolar.
     *
     * @param message mensaje original del broker
     * @param cause   último fallo del consumidor
     * @throws ListenerExecutionFailedException siempre, con una causa de rechazo sin reencolar
     */
    @Override
    public void recover(Message message, Throwable cause) {
        if (!ProfileEventsErrorHandler.hasCause(cause, AmqpRejectAndDontRequeueException.class)) {
            var properties = message.getMessageProperties();
            log.error("Mensaje del perfil agotado tras los reintentos; va a la cola de fallidos "
                    + "[cola={}, messageId={}, causa={}]", properties.getConsumerQueue(),
                    EventLogValues.safeMessageId(properties.getMessageId()),
                    ProfileEventsErrorHandler.rootCauseName(cause), RedactedException.of(cause));
        }
        throw new ListenerExecutionFailedException("Intentos agotados",
                new AmqpRejectAndDontRequeueException(cause), message);
    }
}
