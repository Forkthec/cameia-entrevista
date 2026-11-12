package tech.cameia.entrevista.application.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;

/**
 * Arma el prompt que pide al modelo evaluar la respuesta de la persona a una pregunta.
 *
 * <p>La plantilla y el esquema de salida son archivos de {@code prompts/}. Los marcadores {@code {{pregunta}}} y
 * {@code {{respuesta}}} se reemplazan en una sola pasada, de modo que un marcador escrito por la persona dentro de
 * su texto se queda como texto. La respuesta va entre las marcas {@code <respuesta_del_candidato>}; cualquier marca
 * igual escrita por la persona se quita antes, repitiendo hasta que no quede ninguna, para que no pueda cerrar el
 * bloque ni abrir instrucciones fuera de él.</p>
 */
@Component
public class EvaluationPromptBuilder {

    /** Versión del prompt, tal como se guarda en la ejecución de la IA. */
    public static final String VERSION = "evaluacion.v1";

    /** Tope de tokens de salida de la llamada de evaluación. */
    public static final int MAX_OUTPUT_TOKENS = 1024;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private static final Pattern ANSWER_MARK = Pattern.compile("(?i)<\\s*/?\\s*respuesta_del_candidato\\s*>");

    private final String template;

    private final String schema;

    /** Carga la plantilla y el esquema una sola vez. */
    public EvaluationPromptBuilder() {
        this.template = read(VERSION + ".txt");
        this.schema = read("esquema." + VERSION + ".json").strip();
    }

    /**
     * Arma el prompt de evaluación.
     *
     * @param question texto de la pregunta tal como se mostró
     * @param answer respuesta de la persona
     * @return el prompt con su esquema de salida
     */
    public LanguageModelPrompt build(String question, String answer) {
        Map<String, String> values = Map.of("pregunta", question, "respuesta", neutralize(answer));
        return new LanguageModelPrompt(replaceInOnePass(values), MAX_OUTPUT_TOKENS, schema, AiOperation.EVALUATE);
    }

    /** Quita las marcas de la respuesta, repitiendo hasta que no quede ninguna. */
    private static String neutralize(String text) {
        String previous;
        String current = text;
        do {
            previous = current;
            current = ANSWER_MARK.matcher(current).replaceAll("");
        } while (!current.equals(previous));
        return current;
    }

    private String replaceInOnePass(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String read(String name) {
        try (var in = new ClassPathResource("prompts/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("No se pudo leer el prompt " + name, failure);
        }
    }
}
