package tech.cameia.entrevista.infrastructure.messaging.consumer;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tech.cameia.entrevista.application.command.ReplicateProfileCommand;
import tech.cameia.entrevista.domain.exception.IdentityRequiredException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tech.cameia.entrevista.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;

/**
 * Comprueba un mensaje de {@code perfil.profesional-actualizado} contra el contrato v1 y lo
 * convierte en el comando del caso de uso.
 *
 * <p>El orden es fijo para que el registro sea estable: propiedades del mensaje ({@code message_id},
 * {@code type}, versión), forma del cuerpo, reglas del contrato (se informa la ruta de la primera
 * violación en orden alfabético) y, al final, las fechas. Nunca se copia al error el valor recibido.</p>
 */
@Component
public class ProfileUpdatedEventReader {

    /** Tipo del evento, igual a la clave de enrutamiento. */
    static final String EVENT_TYPE = "perfil.profesional-actualizado";

    private static final int SUPPORTED_VERSION = 1;
    private static final Pattern CANONICAL_UUID = Pattern
            .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PATH_SEGMENT = Pattern.compile("([A-Za-z]+)((?:\\[\\d+\\])*)");

    /** Nombre de cada propiedad del cuerpo según el contrato publicado por Perfil. */
    private static final Map<String, String> CONTRACT_NAMES = Map.ofEntries(
            Map.entry("userId", "usuarioId"),
            Map.entry("professionalContext", "contextoProfesional"),
            Map.entry("aspiredRoles", "rolesAspirados"),
            Map.entry("updatedAt", "actualizadoEn"),
            Map.entry("id", "id"),
            Map.entry("name", "nombre"),
            Map.entry("status", "estado"),
            Map.entry("archivedAt", "fecha_archivado"),
            Map.entry("summary", "resumen"),
            Map.entry("experiences", "experiencias"),
            Map.entry("educations", "formaciones"),
            Map.entry("skills", "habilidades"),
            Map.entry("company", "empresa"),
            Map.entry("position", "cargo"),
            Map.entry("description", "descripcion"),
            Map.entry("start", "inicio"),
            Map.entry("end", "fin"),
            Map.entry("employmentStatus", "estadoEmpleo"),
            Map.entry("institution", "institucion"),
            Map.entry("title", "titulo"),
            Map.entry("level", "nivel"),
            Map.entry("inProgress", "enCurso"),
            Map.entry("code", "codigo"));

    private final Validator validator;
    private final JsonMapper jsonMapper;

    ProfileUpdatedEventReader(Validator validator, JsonMapper jsonMapper) {
        this.validator = validator;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Comprueba el mensaje y arma el comando.
     *
     * @param payload   cuerpo ya convertido; {@code null} si estaba vacío
     * @param messageId propiedad {@code message_id}; puede faltar
     * @param type      propiedad {@code type}; puede faltar
     * @param version   encabezado {@code x-event-version}; puede faltar
     * @return el comando con la copia lista para guardar
     * @throws ContractViolation con la razón estable y, si aplica, la ruta del campo según el contrato
     */
    ReplicateProfileCommand read(ProfileUpdatedPayloadV1 payload, String messageId, String type, Object version) {
        UUID id = readMessageId(messageId);
        if (!EVENT_TYPE.equals(type)) {
            throw new ContractViolation(ContractViolation.EVENT_TYPE_INVALID, null);
        }
        if (!isSupportedVersion(version)) {
            throw new ContractViolation(ContractViolation.EVENT_VERSION_UNSUPPORTED, null);
        }
        if (payload == null) {
            throw new ContractViolation(ContractViolation.PAYLOAD_INVALID_FORMAT, null);
        }
        validateRules(payload);
        return new ReplicateProfileCommand(id, toReplica(payload));
    }

    /**
     * La versión es un entero igual a 1. Se acepta cualquier tipo entero del encabezado AMQP
     * (byte, short, int o long): según el cliente que publique, el mismo {@code 1} llega con uno u otro.
     */
    private static boolean isSupportedVersion(Object version) {
        return (version instanceof Integer || version instanceof Long || version instanceof Short
                || version instanceof Byte) && ((Number) version).longValue() == SUPPORTED_VERSION;
    }

    private static UUID readMessageId(String messageId) {
        if (messageId == null || !CANONICAL_UUID.matcher(messageId).matches()) {
            throw new ContractViolation(ContractViolation.MESSAGE_ID_INVALID, null);
        }
        return UUID.fromString(messageId);
    }

    /** Aplica las reglas del contrato y falla con la ruta de la primera violación en orden alfabético. */
    private void validateRules(ProfileUpdatedPayloadV1 payload) {
        validator.validate(payload).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .min(Comparator.naturalOrder())
                .ifPresent(path -> {
                    throw violation(contractPath(path));
                });
    }

    private ProfileReplica toReplica(ProfileUpdatedPayloadV1 payload) {
        var context = payload.professionalContext();
        FirebaseUid owner = readOwner(payload.userId());
        Instant archivedAt = context.archivedAt() == null ? null
                : parseInstant(context.archivedAt(), "contextoProfesional.fecha_archivado");
        Instant updatedAt = parseInstant(payload.updatedAt(), "actualizadoEn");
        return new ProfileReplica(UUID.fromString(context.id()), owner, context.name(),
                ReplicatedProfileStatus.valueOf(context.status()), archivedAt, toJson(context),
                toJson(payload.aspiredRoles()), updatedAt);
    }

    /** El dueño debe ser un identificador que el dominio acepta: sin control, formato ni separadores. */
    private static FirebaseUid readOwner(String userId) {
        try {
            return FirebaseUid.required(userId);
        } catch (IdentityRequiredException invalid) {
            throw violation("usuarioId");
        }
    }

    private static Instant parseInstant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw violation(field);
        }
    }

    private String toJson(Object block) {
        try {
            return jsonMapper.writeValueAsString(block);
        } catch (JacksonException failure) {
            // No ocurre con estos tipos; si ocurriera, es un fallo técnico y se reintenta.
            throw new IllegalStateException("No se pudo serializar el bloque del perfil", failure);
        }
    }

    private static ContractViolation violation(String field) {
        return new ContractViolation(ContractViolation.PAYLOAD_CONTRACT_VIOLATION, field);
    }

    /** Traduce una ruta de Bean Validation (nombres del código) a la ruta del contrato, con sus índices. */
    static String contractPath(String path) {
        var result = new StringBuilder();
        for (String segment : path.split("\\.")) {
            var matcher = PATH_SEGMENT.matcher(segment);
            if (!matcher.matches()) {
                continue; // p. ej. «<list element>» de un elemento nulo: la ruta termina en el índice
            }
            if (!result.isEmpty()) {
                result.append('.');
            }
            result.append(CONTRACT_NAMES.getOrDefault(matcher.group(1), matcher.group(1))).append(matcher.group(2));
        }
        return result.toString();
    }
}
