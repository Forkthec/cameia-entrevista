package tech.cameia.entrevista.infrastructure.messaging.consumer;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tech.cameia.entrevista.application.command.ReplicateRoleCatalogCommand;
import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.domain.model.RoleStatus;
import tech.cameia.entrevista.infrastructure.messaging.payload.RoleCatalogPayloadV1;

/**
 * Comprueba un mensaje de {@code perfil.catalogo-roles-actualizado} contra el contrato v1 y lo
 * convierte en el comando del caso de uso.
 *
 * <p>El orden es fijo para que el registro sea estable: propiedades del mensaje ({@code message_id},
 * {@code type}, versión), forma del cuerpo, reglas del contrato (se informa la ruta de la primera
 * violación en orden alfabético), fecha y, al final, que ningún {@code codigo} se repita. Nunca se
 * copia al error el valor recibido.</p>
 */
@Component
public class RoleCatalogEventReader {

    /** Tipo del evento, igual a la clave de enrutamiento. */
    static final String EVENT_TYPE = "perfil.catalogo-roles-actualizado";

    private final Validator validator;

    RoleCatalogEventReader(Validator validator) {
        this.validator = validator;
    }

    /**
     * Comprueba el mensaje y arma el comando.
     *
     * @param payload   cuerpo ya convertido; {@code null} si estaba vacío
     * @param messageId propiedad {@code message_id}; puede faltar
     * @param type      propiedad {@code type}; puede faltar
     * @param version   encabezado {@code x-event-version}; puede faltar
     * @return el comando con los roles listos para guardar
     * @throws ContractViolation con la razón estable y, si aplica, la ruta del campo según el contrato
     */
    ReplicateRoleCatalogCommand read(RoleCatalogPayloadV1 payload, String messageId, String type, Object version) {
        UUID id = ProfileUpdatedEventReader.readMessageId(messageId);
        if (!EVENT_TYPE.equals(type)) {
            throw new ContractViolation(ContractViolation.EVENT_TYPE_INVALID, null);
        }
        if (!ProfileUpdatedEventReader.isSupportedVersion(version)) {
            throw new ContractViolation(ContractViolation.EVENT_VERSION_UNSUPPORTED, null);
        }
        if (payload == null) {
            throw new ContractViolation(ContractViolation.PAYLOAD_INVALID_FORMAT, null);
        }
        validateRules(payload);
        Instant snapshotAt = parseInstant(payload.updatedAt());
        return new ReplicateRoleCatalogCommand(id, toRoles(payload.roles()), snapshotAt);
    }

    /** Aplica las reglas del contrato y falla con la ruta de la primera violación en orden alfabético. */
    private void validateRules(RoleCatalogPayloadV1 payload) {
        validator.validate(payload).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .min(Comparator.naturalOrder())
                .ifPresent(path -> {
                    throw new ContractViolation(ContractViolation.PAYLOAD_CONTRACT_VIOLATION,
                            ProfileUpdatedEventReader.contractPath(path));
                });
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new ContractViolation(ContractViolation.PAYLOAD_CONTRACT_VIOLATION, "actualizadoEn");
        }
    }

    /** Convierte los elementos y falla en el segundo que repite un {@code codigo}. */
    private static List<ProfessionalRoleReplica> toRoles(List<RoleCatalogPayloadV1.RoleItem> items) {
        List<ProfessionalRoleReplica> roles = new ArrayList<>(items.size());
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            RoleCatalogPayloadV1.RoleItem item = items.get(index);
            UUID code = UUID.fromString(item.code());
            if (!seen.add(code)) {
                throw new ContractViolation(ContractViolation.CATALOG_ROLE_DUPLICATED,
                        "roles[" + index + "].codigo");
            }
            roles.add(new ProfessionalRoleReplica(code, item.nameEs(), item.nameEn(),
                    RoleStatus.valueOf(item.status())));
        }
        return roles;
    }
}
