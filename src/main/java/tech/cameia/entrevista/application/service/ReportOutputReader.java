package tech.cameia.entrevista.application.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.ContentGrade;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;

/**
 * Lee la salida del modelo según el esquema {@code reporte.v1}: un objeto con exactamente {@code contenido} y
 * {@code star}.
 *
 * <p>La salida del modelo no es de confianza: cada pregunta esperada debe aparecer una sola vez y ninguna de más,
 * las claves deben ser exactamente las del esquema, las notas son enteros de 1 a 5 y los componentes STAR son
 * booleanos. Una pregunta repetida o ausente indica que un texto de la persona intentó fabricarla o esconderla;
 * ante cualquier error se lanza {@link LlmInvalidOutputException} con una razón fija que nunca copia texto del modelo
 * ni el mensaje de Jackson.</p>
 */
@Component
public class ReportOutputReader {

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$");

    private static final Set<String> ROOT_KEYS = Set.of("contenido", "star");

    private static final Set<String> CONTENT_KEYS = Set.of("numero", "precision_claridad", "carisma_verbal",
            "lenguaje_adecuado");

    private static final Set<String> STAR_KEYS = Set.of("numero", "situacion", "tarea", "accion", "resultado");

    private final JsonMapper json;

    /**
     * Crea el lector.
     *
     * @param json lector de JSON
     */
    public ReportOutputReader(JsonMapper json) {
        this.json = json;
    }

    /**
     * Lee y valida la calificación.
     *
     * @param response respuesta del modelo
     * @param contentNumbers números de las preguntas no conductuales enviadas
     * @param starNumbers números de las preguntas conductuales enviadas
     * @return las notas y las marcas STAR validadas
     * @throws LlmInvalidOutputException si el texto no es JSON, no tiene la forma del esquema, falta, sobra o se
     *         repite una pregunta, o un valor no es admitido
     */
    public ReportGrades read(LanguageModelResponse response, Set<Integer> contentNumbers, Set<Integer> starNumbers) {
        JsonNode root = parse(withoutFence(response.text()));
        if (root == null || !root.isObject() || !keys(root).equals(ROOT_KEYS)) {
            throw new LlmInvalidOutputException("la raíz no tiene exactamente contenido y star");
        }
        List<ContentGrade> content = new ArrayList<>();
        for (JsonNode item : items(root.get("contenido"), CONTENT_KEYS, contentNumbers, "contenido")) {
            content.add(new ContentGrade(item.get("numero").asInt(), grade(item, "precision_claridad"),
                    grade(item, "carisma_verbal"), grade(item, "lenguaje_adecuado")));
        }
        List<ReportGrades.StarMarks> star = new ArrayList<>();
        for (JsonNode item : items(root.get("star"), STAR_KEYS, starNumbers, "star")) {
            star.add(new ReportGrades.StarMarks(item.get("numero").asInt(), flag(item, "situacion"),
                    flag(item, "tarea"), flag(item, "accion"), flag(item, "resultado")));
        }
        return new ReportGrades(content, star);
    }

    /** Valida la lista: arreglo de objetos con las claves exactas y los números esperados, cada uno una vez. */
    private static List<JsonNode> items(JsonNode array, Set<String> keys, Set<Integer> expected, String name) {
        if (array == null || !array.isArray()) {
            throw new LlmInvalidOutputException(name + " no es una lista");
        }
        List<JsonNode> items = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (JsonNode item : array) {
            if (!item.isObject() || !keys(item).equals(keys)) {
                throw new LlmInvalidOutputException(name + " tiene un elemento con claves distintas a las del esquema");
            }
            JsonNode number = item.get("numero");
            if (!number.isInt() || !expected.contains(number.asInt()) || !seen.add(number.asInt())) {
                throw new LlmInvalidOutputException(name + " tiene un número inesperado o repetido");
            }
            items.add(item);
        }
        if (seen.size() != expected.size()) {
            throw new LlmInvalidOutputException(name + " no trae todas las preguntas esperadas");
        }
        return items;
    }

    private static int grade(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (!value.isInt() || value.asInt() < ContentGrade.MIN || value.asInt() > ContentGrade.MAX) {
            throw new LlmInvalidOutputException(field + " no es un entero de 1 a 5");
        }
        return value.asInt();
    }

    private static boolean flag(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (!value.isBoolean()) {
            throw new LlmInvalidOutputException(field + " no es booleano");
        }
        return value.asBoolean();
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private JsonNode parse(String output) {
        try {
            return json.readTree(output);
        } catch (JacksonException | IllegalArgumentException notJson) {
            throw new LlmInvalidOutputException("la salida no es JSON");
        }
    }

    /** Quita un bloque de código con su etiqueta, si el modelo envolvió el JSON en uno. */
    private static String withoutFence(String output) {
        Matcher fence = CODE_FENCE.matcher(output);
        return fence.matches() ? fence.group(1) : output;
    }
}
