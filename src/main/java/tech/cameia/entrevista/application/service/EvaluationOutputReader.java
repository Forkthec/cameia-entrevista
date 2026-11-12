package tech.cameia.entrevista.application.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.EvaluationResult;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;

/**
 * Lee la salida del modelo según el esquema {@code evaluacion.v1}: un objeto con {@code accion},
 * {@code clasificacion}, {@code motivo} y {@code feedback} de tipo texto. {@code follow_up_texto} se lee si viene y
 * se descarta, y los demás campos que sobran se ignoran.
 *
 * <p>La salida del modelo no es de confianza: ante cualquier error de forma o de valor se lanza
 * {@link LlmInvalidOutputException} con una razón fija que nunca copia texto del modelo ni el mensaje de
 * Jackson.</p>
 */
@Component
public class EvaluationOutputReader {

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$");

    private final JsonMapper json;

    /**
     * Crea el lector.
     *
     * @param json lector de JSON
     */
    public EvaluationOutputReader(JsonMapper json) {
        this.json = json;
    }

    /**
     * Lee la evaluación.
     *
     * @param response respuesta del modelo
     * @return la evaluación validada
     * @throws LlmInvalidOutputException si el texto no es JSON, no tiene la forma del esquema o un valor no es
     *         admitido
     */
    public EvaluationResult read(LanguageModelResponse response) {
        JsonNode root = parse(withoutFence(response.text()));
        if (root == null || !root.isObject()) {
            throw new LlmInvalidOutputException("la raíz no es un objeto");
        }
        return new EvaluationResult(text(root, "accion"), text(root, "clasificacion"), text(root, "motivo"),
                text(root, "feedback"));
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

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isString()) {
            throw new LlmInvalidOutputException(field + " ausente o no es texto");
        }
        return value.asString();
    }
}
