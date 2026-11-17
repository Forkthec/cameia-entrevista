package tech.cameia.entrevista.application.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.ReportQuestion;

/**
 * Arma el prompt que pide al modelo calificar todas las respuestas de una sesión en una sola llamada.
 *
 * <p>La plantilla y el esquema de salida son archivos de {@code prompts/}. Los marcadores {@code {{idioma}}},
 * {@code {{rol}}}, {@code {{oferta}}} y {@code {{preguntas}}} se reemplazan en una sola pasada, de modo que un
 * marcador escrito por la persona dentro de su texto se queda como texto.</p>
 *
 * <p>Los textos de la persona (respuestas, cargo, oferta y también el texto de las preguntas, que la IA generó a
 * partir de su perfil) se neutralizan antes de insertarse: {@code <} y {@code >}, y sus versiones de ancho completo,
 * se reemplazan por {@code ‹} y {@code ›}, y una línea que empiece como el encabezado de una pregunta se marca con un
 * guion. Así ningún texto puede cerrar un bloque, abrir otro ni fabricar una pregunta.</p>
 */
@Component
public class ReportPromptBuilder {

    /** Versión del prompt, tal como se guarda en la ejecución de la IA. */
    public static final String VERSION = "reporte.v1";

    /** Tope de tokens de salida de la llamada del reporte. */
    public static final int MAX_OUTPUT_TOKENS = 1024;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    /** Línea que empieza como el encabezado de una pregunta («Pregunta 4 (»). */
    private static final Pattern QUESTION_HEADER = Pattern.compile("(?imu)^([ \\t]*)(Pregunta\\s+\\d+\\s*\\()");

    private final String template;

    private final String schema;

    /** Carga la plantilla y el esquema una sola vez. */
    public ReportPromptBuilder() {
        this.template = read(VERSION + ".txt");
        this.schema = read("esquema." + VERSION + ".json").strip();
    }

    /**
     * Arma el prompt del reporte.
     *
     * @param language idioma de la sesión
     * @param role nombre del rol al que aspira la persona
     * @param jobOffer oferta laboral en texto libre; {@code null} o en blanco si la sesión no la tiene
     * @param questions las preguntas de la sesión con su respuesta, en orden de número
     * @return el prompt con su esquema de salida
     */
    public LanguageModelPrompt build(InterviewLanguage language, String role, String jobOffer,
            List<ReportQuestion> questions) {
        String offer = jobOffer == null || jobOffer.isBlank() ? ""
                : "<oferta_laboral>\n" + neutralize(jobOffer) + "\n</oferta_laboral>\n";
        Map<String, String> values = Map.of(
                "idioma", language.code(),
                "rol", neutralize(role),
                "oferta", offer,
                "preguntas", questions.stream().map(ReportPromptBuilder::block).collect(Collectors.joining("\n\n")));
        return new LanguageModelPrompt(replaceInOnePass(values), MAX_OUTPUT_TOKENS, schema, AiOperation.REPORT);
    }

    private static String block(ReportQuestion question) {
        return "Pregunta " + question.number() + " (tipo " + question.type().code() + "): "
                + neutralize(question.questionText()) + "\n<respuesta_del_candidato>\n"
                + neutralize(question.answerText()) + "\n</respuesta_del_candidato>";
    }

    /** Reemplaza los signos de etiqueta y marca los encabezados de pregunta falsos. */
    static String neutralize(String text) {
        String escaped = text.replace('<', '‹').replace('＜', '‹').replace('>', '›').replace('＞', '›');
        return QUESTION_HEADER.matcher(escaped).replaceAll("$1– $2");
    }

    private String replaceInOnePass(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.getOrDefault(matcher.group(1), matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String read(String file) {
        try {
            return new ClassPathResource("prompts/" + file).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("No se pudo leer prompts/" + file, unreadable);
        }
    }
}
