package tech.cameia.entrevista.infrastructure.messaging.consumer;

import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import tech.cameia.entrevista.application.command.ReplicateRoleCatalogCommand;
import tech.cameia.entrevista.application.service.ReplicateRoleCatalogService;
import tech.cameia.entrevista.infrastructure.messaging.config.RoleCatalogRabbitConfig;
import tech.cameia.entrevista.infrastructure.messaging.payload.RoleCatalogPayloadV1;

/**
 * Consume {@code perfil.catalogo-roles-actualizado} y mantiene la copia local del catálogo de roles.
 *
 * <p>Un mensaje que incumple el contrato se rechaza sin reencolar y termina en la cola de fallidos
 * con una sola línea {@code WARN}; un fallo técnico se propaga para que el contenedor lo reintente.
 * El {@code ack} ocurre después del commit del caso de uso. Ni el cuerpo ni los nombres de los roles
 * se escriben en el registro. Mientras se procesa, el {@code MDC} lleva {@code requestId}.</p>
 */
@Component
public class RoleCatalogListener {

    private static final Logger log = LoggerFactory.getLogger(RoleCatalogListener.class);

    private static final String VERSION_HEADER = "x-event-version";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final RoleCatalogEventReader reader;
    private final ReplicateRoleCatalogService service;

    RoleCatalogListener(RoleCatalogEventReader reader, ReplicateRoleCatalogService service) {
        this.reader = reader;
        this.service = service;
    }

    /**
     * Aplica una instantánea del catálogo.
     *
     * @param payload       cuerpo del evento; {@code null} si estaba vacío
     * @param messageId     propiedad {@code message_id}; puede faltar
     * @param type          propiedad {@code type}; puede faltar
     * @param headers       encabezados del mensaje; de aquí se lee {@code x-event-version}, que puede faltar
     * @param correlationId propiedad {@code correlation_id}; puede faltar
     * @throws AmqpRejectAndDontRequeueException si el mensaje incumple el contrato: va a la cola de
     *                                            fallidos sin reintentos
     */
    @RabbitListener(queues = RoleCatalogRabbitConfig.QUEUE, errorHandler = "profileEventsErrorHandler")
    public void onRoleCatalogUpdated(RoleCatalogPayloadV1 payload,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
            @Header(name = AmqpHeaders.TYPE, required = false) String type,
            @Headers Map<String, Object> headers,
            @Header(name = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
        MDC.put(REQUEST_ID_KEY, requestId(correlationId, messageId));
        try {
            ReplicateRoleCatalogCommand command = reader.read(payload, messageId, type, headers.get(VERSION_HEADER));
            service.apply(command);
        } catch (ContractViolation violation) {
            throw reject(messageId, violation);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    /** El {@code correlation_id} si tiene la forma de un identificador seguro; si no, el {@code message_id} saneado. */
    private static String requestId(String correlationId, String messageId) {
        if (correlationId != null && REQUEST_ID.matcher(correlationId).matches()) {
            return correlationId;
        }
        return EventLogValues.safeMessageId(messageId);
    }

    private static AmqpRejectAndDontRequeueException reject(String messageId, ContractViolation violation) {
        log.warn("Catálogo de roles rechazado hacia la cola de fallidos [cola={}, messageId={}, razón={}, campo={}]",
                RoleCatalogRabbitConfig.QUEUE, EventLogValues.safeMessageId(messageId), violation.reason(),
                violation.field() == null ? "-" : violation.field());
        return new AmqpRejectAndDontRequeueException(violation.reason());
    }
}
