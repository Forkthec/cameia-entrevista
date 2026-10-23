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
import tech.cameia.entrevista.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;
import tech.cameia.entrevista.support.EventSchemaComparator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El contrato publicado por Perfil (esquema copiado en {@code docs/eventos}) y el payload que lee
 * este servicio dicen lo mismo: el ejemplo es válido y los límites coinciden.
 */
class ProfileUpdatedContractTest {

    private static final Path SCHEMA = Path.of("docs/eventos/perfil-profesional-actualizado-v1.schema.json");

    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode schema() throws IOException {
        return mapper.readTree(Files.readString(SCHEMA));
    }

    @Test
    @DisplayName("El ejemplo del esquema se convierte al payload y cumple todas sus restricciones")
    void example_shouldBeValid_whenReadFromSchema() throws IOException {
        ProfileUpdatedPayloadV1 payload = mapper.treeToValue(schema().get("examples").get(0),
                ProfileUpdatedPayloadV1.class);

        Set<ConstraintViolation<ProfileUpdatedPayloadV1>> violations =
                Validation.buildDefaultValidatorFactory().getValidator().validate(payload);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("maxLength, minItems, maxItems y los valores permitidos del esquema son los del payload")
    void limits_shouldMatchSchema() throws IOException, NoSuchFieldException {
        assertThat(EventSchemaComparator.differences(schema(), ProfileUpdatedPayloadV1.class)).isEmpty();
    }
}
