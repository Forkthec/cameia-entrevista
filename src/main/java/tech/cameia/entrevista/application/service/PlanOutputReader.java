package tech.cameia.entrevista.application.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.PlanDraft;
import tech.cameia.entrevista.domain.model.PlannedQuestion;

/**
 * Lee la salida del modelo según el esquema {@code plan-preguntas.v1}: un objeto con {@code idioma} y
 * {@code preguntas}, y en cada pregunta {@code posicion} (entero), {@code tipo} y {@code texto} (textos). Los
 * campos que sobran se ignoran. Solo comprueba la forma; las reglas del plan son de {@code PlanValidator}.
 *
 * <p>La salida del modelo no es de confianza: si no tiene la forma, se lanza
 * {@link LlmInvalidOutputException} con una razón fija que nunca copia texto del modelo.</p>
 */
@Component
public class PlanOutputReader {

    private final JsonMapper json;

    /**
     * Crea el lector.
     *
     * @param json lector de JSON
     */
    public PlanOutputReader(JsonMapper json) {
        this.json = json;
    }

    /**
     * Lee el plan.
     *
     * @param output texto que devolvió el modelo
     * @return el plan sin validar sus reglas
     * @throws LlmInvalidOutputException si el texto no es JSON o no tiene la forma del esquema
     */
    public PlanDraft read(String output) {
        JsonNode root;
        try {
            root = json.readTree(output);
        } catch (JacksonException | IllegalArgumentException notJson) {
            throw new LlmInvalidOutputException("la salida no es JSON");
        }
        if (root == null || !root.isObject()) {
            throw new LlmInvalidOutputException("la raíz no es un objeto");
        }
        JsonNode language = root.get("idioma");
        JsonNode questions = root.get("preguntas");
        if (language == null || !language.isString()) {
            throw new LlmInvalidOutputException("idioma ausente o no es texto");
        }
        if (questions == null || !questions.isArray()) {
            throw new LlmInvalidOutputException("preguntas ausente o no es una lista");
        }
        List<PlannedQuestion> list = new ArrayList<>();
        for (JsonNode question : questions) {
            list.add(readQuestion(question));
        }
        return new PlanDraft(language.asString(), list);
    }

    private static PlannedQuestion readQuestion(JsonNode question) {
        if (question == null || !question.isObject()) {
            throw new LlmInvalidOutputException("una pregunta no es un objeto");
        }
        JsonNode position = question.get("posicion");
        JsonNode type = question.get("tipo");
        JsonNode text = question.get("texto");
        if (position == null || !position.isIntegralNumber() || !position.canConvertToInt()) {
            throw new LlmInvalidOutputException("posicion ausente o no es un entero");
        }
        if (type == null || !type.isString()) {
            throw new LlmInvalidOutputException("tipo ausente o no es texto");
        }
        if (text == null || !text.isString()) {
            throw new LlmInvalidOutputException("texto ausente o no es texto");
        }
        return new PlannedQuestion(position.asInt(), type.asString(), text.asString());
    }
}
