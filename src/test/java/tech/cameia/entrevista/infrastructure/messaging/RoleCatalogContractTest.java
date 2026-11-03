package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.infrastructure.messaging.payload.RoleCatalogPayloadV1;
import tech.cameia.entrevista.support.EventSchemaComparator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El contrato del catálogo de roles publicado por Perfil (esquema copiado en {@code docs/eventos}) y
 * el payload que lee este servicio dicen lo mismo, y los límites del esquema son los de la columna.
 */
class RoleCatalogContractTest {

    private static final Path SCHEMA = Path.of("docs/eventos/perfil-catalogo-roles-actualizado-v1.schema.json");

    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode schema() throws IOException {
        return mapper.readTree(Files.readString(SCHEMA));
    }

    @Test
    @DisplayName("El ejemplo del esquema se convierte al payload y cumple todas sus restricciones")
    void example_shouldBeValid_whenReadFromSchema() throws IOException {
        RoleCatalogPayloadV1 payload = mapper.treeToValue(schema().get("examples").get(0),
                RoleCatalogPayloadV1.class);

        Set<ConstraintViolation<RoleCatalogPayloadV1>> violations =
                Validation.buildDefaultValidatorFactory().getValidator().validate(payload);

        assertThat(violations).isEmpty();
        assertThat(payload.roles()).hasSize(2);
    }

    @Test
    @DisplayName("maxLength, minItems, maxItems y los valores permitidos del esquema son los del payload")
    void limits_shouldMatchSchema() throws IOException, NoSuchFieldException {
        assertThat(EventSchemaComparator.differences(schema(), RoleCatalogPayloadV1.class)).isEmpty();
    }

    @Test
    @DisplayName("El largo máximo del nombre en el esquema es el del dominio y el de la columna")
    void nameLength_shouldMatchDomain() throws IOException {
        JsonNode role = schema().at("/properties/roles/items/properties");

        assertThat(role.at("/nombreEs/maxLength").asInt()).isEqualTo(ProfessionalRoleReplica.MAX_NAME_LENGTH);
        assertThat(role.at("/nombreEn/maxLength").asInt()).isEqualTo(ProfessionalRoleReplica.MAX_NAME_LENGTH);
        assertThat(schema().at("/properties/roles/maxItems").asInt())
                .isEqualTo(tech.cameia.entrevista.application.command.ReplicateRoleCatalogCommand.MAX_ROLES);
    }
}
