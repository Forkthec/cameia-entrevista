package tech.cameia.entrevista.infrastructure.messaging.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

/**
 * Clasifica el fallo de un consumidor de eventos de Perfil.
 *
 * <p>Un cuerpo que no se pudo convertir incumple el contrato: se registra una vez en {@code WARN}
 * con su razón y se rechaza sin reintentos. Un rechazo que el consumidor ya registró se relanza sin
 * tocarlo. Cualquier otro fallo es técnico: se anota en {@code WARN} sin traza y se relanza para que
 * el contenedor lo reintente; el registro en {@code ERROR} con traza lo hace
 * {@link ProfileEventsRecoverer} una sola vez, cuando se agotan los intentos. Nunca se escribe el
 * cuerpo del mensaje.</p>
 */
@Component("profileEventsErrorHandler")
public class ProfileEventsErrorHandler implements RabbitListenerErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ProfileEventsErrorHandler.class);

    /** Máximo de causas que se recorren, para no girar en una cadena con ciclo. */
    private static final int MAX_CAUSES = 32;

    /**
     * Decide qué hacer con el fallo; siempre lanza.
     *
     * @param amqpMessage mensaje original del broker
     * @param channel     canal del consumidor
     * @param message     mensaje ya convertido al modelo de Spring
     * @param exception   fallo del consumidor, con su causa
     * @return nunca devuelve: siempre lanza
     * @throws AmqpRejectAndDontRequeueException si el cuerpo incumple el contrato
     * @throws ListenerExecutionFailedException  si el fallo es técnico y debe reintentarse
     */
    @Override
    public Object handleError(Message amqpMessage, Channel channel, org.springframework.messaging.Message<?> message,
            ListenerExecutionFailedException exception) throws Exception {
        Throwable cause = exception.getCause();
        if (cause instanceof AmqpRejectAndDontRequeueException rejection) {
            throw rejection;
        }
        var properties = amqpMessage.getMessageProperties();
        String queue = properties.getConsumerQueue();
        String messageId = EventLogValues.safeMessageId(properties.getMessageId());
        if (isConversionFailure(exception)) {
            log.warn("Evento del perfil rechazado hacia la cola de fallidos [cola={}, messageId={}, razón={}]",
                    queue, messageId, ContractViolation.PAYLOAD_INVALID_FORMAT);
            throw new AmqpRejectAndDontRequeueException(ContractViolation.PAYLOAD_INVALID_FORMAT);
        }
        log.warn("Falló el procesamiento del evento del perfil; lo reintenta el contenedor "
                + "[cola={}, messageId={}, causa={}]", queue, messageId, rootCauseName(exception));
        throw exception;
    }

    /** Un cuerpo ilegible o con un tipo equivocado falla al convertirse o al enlazarse con el parámetro. */
    private static boolean isConversionFailure(Throwable failure) {
        return hasCause(failure, org.springframework.amqp.support.converter.MessageConversionException.class)
                || hasCause(failure, org.springframework.messaging.converter.MessageConversionException.class)
                || hasCause(failure,
                        org.springframework.messaging.handler.invocation.MethodArgumentResolutionException.class);
    }

    /** Indica si la cadena de causas, hasta {@value #MAX_CAUSES} niveles, contiene una excepción del tipo dado. */
    static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSES; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** Clase simple de la causa raíz, que es lo único del fallo que se registra junto al identificador. */
    static String rootCauseName(Throwable failure) {
        Throwable root = failure;
        for (int depth = 0; root.getCause() != null && depth < MAX_CAUSES; depth++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
