package tech.cameia.entrevista.application.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.ProfileSnapshotInvalidException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.Tone;

/**
 * Arma el prompt que pide al modelo las cinco preguntas de la entrevista.
 *
 * <p>Usa solo la copia del perfil que la sesión congeló al crearse, la oferta laboral, el tono y el idioma de
 * la sesión; no consulta a Perfil. La plantilla y los bloques de tono son archivos de {@code prompts/} y no se
 * modifican aquí: se reemplazan sus marcadores {@code {{...}}} en una sola pasada, de modo que un marcador
 * escrito por la persona dentro de su texto se queda como texto. El contexto y la oferta van entre las marcas
 * {@code <datos_de_la_persona>}; cualquier marca igual escrita por la persona se quita antes, para que no pueda
 * cerrar el bloque.</p>
 *
 * <p>El log nunca lleva el texto de la persona: solo el tamaño cuando se supera el tope.</p>
 */
@Component
public class PlanningPromptBuilder {

    /** Tope de tokens de salida de la llamada de planificación. */
    public static final int MAX_OUTPUT_TOKENS = 4096;

    /** Máximo de puntos de código del contexto del perfil que se envían al modelo. */
    public static final int MAX_CONTEXT_LENGTH = 32_000;

    private static final Logger logger = LoggerFactory.getLogger(PlanningPromptBuilder.class);

    private static final String BASE_VERSION = "planificacion.v6";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private static final Pattern DATA_MARK = Pattern.compile("(?i)<\\s*/?\\s*datos_de_la_persona\\s*>");

    /** Versión del bloque de cada tono, tal como se midió. */
    private static final Map<Tone, Integer> TONE_VERSIONS = new EnumMap<>(Tone.class);

    static {
        TONE_VERSIONS.put(Tone.PROFESSIONAL_NEUTRAL, 1);
        TONE_VERSIONS.put(Tone.WARM, 3);
        TONE_VERSIONS.put(Tone.STRICT, 2);
    }

    private final JsonMapper json;

    private final String template;

    private final Map<Tone, String> toneBlocks = new EnumMap<>(Tone.class);

    /**
     * Crea el armador y carga la plantilla y los bloques de tono.
     *
     * @param json lector del JSON de la copia del perfil
     */
    public PlanningPromptBuilder(JsonMapper json) {
        this.json = json;
        this.template = read(BASE_VERSION + ".txt");
        TONE_VERSIONS.forEach((tone, version) ->
                toneBlocks.put(tone, read("tono." + tone.code() + ".v" + version + ".txt").strip()));
    }

    /**
     * Arma el prompt de la sesión.
     *
     * @param configuration configuración congelada de la sesión
     * @return el prompt y su versión
     * @throws ProfileSnapshotInvalidException si la copia del perfil no es un JSON legible, el contexto supera
     *         {@value #MAX_CONTEXT_LENGTH} puntos de código o no trae el rol de la sesión
     */
    public PlanningPrompt build(SessionConfiguration configuration) {
        JsonNode snapshot = readSnapshot(configuration.profileSnapshotJson());
        String context = json.writeValueAsString(snapshot.path("contextoProfesional"));
        int size = context.codePointCount(0, context.length());
        if (size > MAX_CONTEXT_LENGTH) {
            logger.warn("El contexto del perfil supera el tope del prompt [caracteres={}, tope={}]", size,
                    MAX_CONTEXT_LENGTH);
            throw new ProfileSnapshotInvalidException(new IllegalStateException("Contexto del perfil de " + size
                    + " caracteres: supera el tope de " + MAX_CONTEXT_LENGTH));
        }
        String role = roleName(snapshot, configuration.targetRoleId());
        String offer = configuration.jobOffer() == null || configuration.jobOffer().isBlank() ? ""
                : "- Oferta laboral (texto libre): " + neutralize(configuration.jobOffer()) + "\n";
        InterviewLanguage language = configuration.language();
        Map<String, String> values = Map.of(
                "rol", role,
                "idiomaNombre", language == InterviewLanguage.ENGLISH ? "inglés" : "español de Colombia",
                "idioma", language.code(),
                "contexto", neutralize(context),
                "oferta", offer,
                "tono", toneBlocks.get(configuration.tone()));
        String text = replaceInOnePass(values);
        String version = BASE_VERSION + "+" + configuration.tone().code() + ".v"
                + TONE_VERSIONS.get(configuration.tone());
        return new PlanningPrompt(new LanguageModelPrompt(text, MAX_OUTPUT_TOKENS), version);
    }

    private JsonNode readSnapshot(String snapshotJson) {
        try {
            return json.readTree(snapshotJson);
        } catch (JacksonException unreadable) {
            // Sin la causa original: su mensaje puede citar un fragmento del texto del perfil.
            throw new ProfileSnapshotInvalidException(
                    new IllegalStateException("La copia del perfil no es un JSON legible"));
        }
    }

    /** Nombre del rol guardado en la copia del perfil al crear la sesión. */
    private static String roleName(JsonNode snapshot, UUID roleId) {
        for (JsonNode role : snapshot.path("rolesAspirados")) {
            String code = role.path("codigo").asString("");
            if (code.toLowerCase(Locale.ROOT).equals(roleId.toString())) {
                String name = role.path("nombre").asString("");
                if (name.isBlank()) {
                    throw new ProfileSnapshotInvalidException(
                            new IllegalStateException("El rol de la copia del perfil no trae nombre"));
                }
                return name;
            }
        }
        throw new ProfileSnapshotInvalidException(new IllegalStateException("La copia del perfil no trae el rol de la sesión"));
    }

    /** Quita las marcas de datos de la persona, repitiendo hasta que no quede ninguna. */
    static String neutralize(String text) {
        String previous;
        String current = text;
        do {
            previous = current;
            current = DATA_MARK.matcher(current).replaceAll("");
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
