package tech.cameia.entrevista.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tech.cameia.entrevista.infrastructure.messaging.payload.TextLength;
import tools.jackson.databind.JsonNode;

/**
 * Compara el esquema JSON de un evento con el payload que lo lee: cada propiedad del payload debe
 * existir en el esquema y sus largos, cantidades y valores permitidos deben ser los mismos.
 */
public final class EventSchemaComparator {

    private EventSchemaComparator() {
    }

    /**
     * Recorre las propiedades del esquema junto a los componentes del record que las lee.
     *
     * @param schema esquema JSON del evento
     * @param type   record del payload
     * @return las diferencias encontradas; vacío si dicen lo mismo
     * @throws NoSuchFieldException si un componente del record no tiene campo (no ocurre con un record)
     */
    public static List<String> differences(JsonNode schema, Class<?> type) throws NoSuchFieldException {
        List<String> differences = new ArrayList<>();
        compare(schema, type, "$", differences);
        return differences;
    }

    private static void compare(JsonNode objectSchema, Class<?> type, String path, List<String> differences)
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

    private static void compareLimits(JsonNode property, Field field, String here, List<String> differences) {
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

    private static void compareNested(JsonNode property, Field field, String here, List<String> differences)
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
