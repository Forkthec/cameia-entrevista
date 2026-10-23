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

import tech.cameia.entrevista.application.command.ReplicateProfileCommand;
import tech.cameia.entrevista.application.service.ReplicateProfileService;
import tech.cameia.entrevista.domain.model.ReplicationOutcome;
import tech.cameia.entrevista.infrastructure.messaging.config.ProfileEventsRabbitConfig;
import tech.cameia.entrevista.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;

/**
 * Consume {@code perfil.profesional-actualizado} y mantiene la copia local del perfil.
 *
 * <p>Un mensaje que incumple el contrato, o que cambia el dueño de un perfil ya copiado, se rechaza
 * sin reencolar y termina en la cola de fallidos con una sola línea {@code WARN}; un fallo técnico se
 * propaga para que el contenedor lo reintente. El {@code ack} ocurre después del commit del caso de
 * uso. El cuerpo nunca se escribe en el registro. Mientras se procesa, el {@code MDC} lleva
 * {@code requestId}.</p>
 */
@Component
public class ProfileUpdatedListener {

    private static final Logger log = LoggerFactory.getLogger(ProfileUpdatedListener.class);

    private static final String VERSION_HEADER = "x-event-version";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String OWNER_MISMATCH = "PROFILE_OWNER_MISMATCH";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ProfileUpdatedEventReader reader;
    private final ReplicateProfileService service;

    ProfileUpdatedListener(ProfileUpdatedEventReader reader, ReplicateProfileService service) {
        this.reader = reader;
        this.service = service;
    }

    /**
     * Aplica un cambio del perfil.
     *
     * @param payload       cuerpo del evento; {@code null} si estaba vacío
     * @param messageId     propiedad {@code message_id}; puede faltar
     * @param type          propiedad {@code type}; puede faltar
     * @param headers       encabezados del mensaje; de aquí se lee {@code x-event-version}, que puede faltar
     * @param correlationId propiedad {@code correlation_id}; puede faltar
     * @throws AmqpRejectAndDontRequeueException si el mensaje incumple el contrato o cambia el dueño:
     *                                            va a la cola de fallidos sin reintentos
     */
    @RabbitListener(queues = ProfileEventsRabbitConfig.QUEUE, errorHandler = "profileEventsErrorHandler")
    public void onProfileUpdated(ProfileUpdatedPayloadV1 payload,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
            @Header(name = AmqpHeaders.TYPE, required = false) String type,
            @Headers Map<String, Object> headers,
            @Header(name = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
        MDC.put(REQUEST_ID_KEY, requestId(correlationId, messageId));
        try {
            ReplicateProfileCommand command = reader.read(payload, messageId, type, headers.get(VERSION_HEADER));
            if (service.apply(command) == ReplicationOutcome.OWNER_MISMATCH) {
                throw reject(messageId, OWNER_MISMATCH, null, command.replica().profileId().toString());
            }
        } catch (ContractViolation violation) {
            throw reject(messageId, violation.reason(), violation.field(), null);
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

    private static AmqpRejectAndDontRequeueException reject(String messageId, String reason, String field,
            String profileId) {
        log.warn("Evento del perfil rechazado hacia la cola de fallidos "
                + "[cola={}, messageId={}, razón={}, campo={}, perfilId={}]", ProfileEventsRabbitConfig.QUEUE,
                EventLogValues.safeMessageId(messageId), reason, field == null ? "-" : field,
                profileId == null ? "-" : profileId);
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
