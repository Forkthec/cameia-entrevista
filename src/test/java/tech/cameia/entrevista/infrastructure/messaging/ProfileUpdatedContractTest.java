package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tech.cameia.entrevista.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;
import tech.cameia.entrevista.infrastructure.messaging.payload.TextLength;
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
        List<String> differences = new ArrayList<>();
        compare(schema(), ProfileUpdatedPayloadV1.class, "$", differences);

        assertThat(differences).isEmpty();
    }

    /** Recorre las propiedades del esquema junto a los componentes del record que las lee. */
    private void compare(JsonNode objectSchema, Class<?> type, String path, List<String> differences)
            throws NoSuchFieldException {
        for (RecordComponent component : type.getRecordComponents()) {
            Field field = type.getDeclaredField(component.getName());
            String jsonName = field.getAnnotation(JsonProperty.class).value();
            JsonNode property = objectSchema.path("properties").path(jsonName);
            String here = path + "." + jsonName;
            if (property.isMissingNode()) {
                differences.add(here + ": no existe en el esquema");
                continue;
            }
            compareLimits(property, field, here, differences);
            compareNested(property, field, here, differences);
        }
    }

    private void compareLimits(JsonNode property, Field field, String here, List<String> differences) {
        TextLength text = field.getAnnotation(TextLength.class);
        if (text != null) {
            expect(differences, here + ".maxLength", property.path("maxLength"), text.max(), Integer.MAX_VALUE);
            expect(differences, here + ".minLength", property.path("minLength"), text.min(), 0);
        }
        Size size = field.getAnnotation(Size.class);
        if (size != null) {
            expect(differences, here + ".maxItems", property.path("maxItems"), size.max(), Integer.MAX_VALUE);
            expect(differences, here + ".minItems", property.path("minItems"), size.min(), 0);
        }
        Pattern pattern = field.getAnnotation(Pattern.class);
        if (pattern != null && property.has("enum")) {
            List<String> allowed = new ArrayList<>();
            property.get("enum").forEach(value -> allowed.add(value.asString()));
            assertThat(List.of(pattern.regexp().split("\\|"))).as(here + ".enum")
                    .containsExactlyInAnyOrderElementsOf(allowed);
        }
    }

    private void compareNested(JsonNode property, Field field, String here, List<String> differences)
            throws NoSuchFieldException {
        Class<?> candidate = field.getType();
        JsonNode itemSchema = property;
        if (List.class.isAssignableFrom(candidate)) {
            Type element = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            candidate = (Class<?>) element;
            itemSchema = property.path("items");
        }
        if (candidate.isRecord()) {
            compare(itemSchema, candidate, here, differences);
        }
    }

    /** Anota una diferencia si el límite del payload no es el del esquema; el esquema que lo omite equivale a {@code unbounded}. */
    private static void expect(List<String> differences, String name, JsonNode declared, int actual, int unbounded) {
        int expected = declared.isMissingNode() ? unbounded : declared.asInt();
        if (expected != actual) {
            differences.add(name + ": esquema=" + expected + ", payload=" + actual);
        }
    }
}
