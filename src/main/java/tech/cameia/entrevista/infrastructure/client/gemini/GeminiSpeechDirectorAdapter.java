package tech.cameia.entrevista.infrastructure.client.gemini;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.policy.SpeechDirectionPolicy;
import tech.cameia.entrevista.domain.port.SpeechDirectorPort;

/**
 * Adaptador de Gemini ({@code generateContent}, respuesta JSON con esquema) que decide dónde van las etiquetas de voz
 * de una pregunta y con qué estilo se lee, según el tono y el momento de la entrevista.
 *
 * <p>Es una mejora: con su propio tiempo máximo, corto, y sin reintentos. Si no hay llave, el modelo no responde a
 * tiempo, responde un error o un JSON sin los dos campos, devuelve vacío y la pregunta se lee tal cual. La validación
 * de lo propuesto es del dominio ({@code SpeechDirectionPolicy}).</p>
 *
 * <p>El texto de la pregunta va delimitado y declarado como dato. Nunca registra el prompt ni la respuesta.</p>
 */
public class GeminiSpeechDirectorAdapter implements SpeechDirectorPort {

    /** Versión del prompt, para el registro. */
    public static final String PROMPT_VERSION = "voz.direccion.v1";

    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final String SCHEMA = """
            {"type":"object","properties":{"estilo":{"type":"string"},"texto":{"type":"string"}},
             "required":["estilo","texto"]}""";
    private static final Logger log = LoggerFactory.getLogger(GeminiSpeechDirectorAdapter.class);

    private final String apiKey;
    private final URI baseUrl;
    private final String model;
    private final String thinkingLevel;
    private final Duration timeout;
    private final String template;
    private final HttpClient http;
    private final JsonMapper json;

    /**
     * Crea el adaptador.
     *
     * @param apiKey llave de Gemini; si falta, nunca dirige
     * @param baseUrl URL base de la API, sin barra final
     * @param model modelo de texto, rápido (por defecto {@code gemini-3.5-flash-lite})
     * @param thinkingLevel nivel de pensamiento; en blanco, el del modelo
     * @param timeout tiempo máximo de la llamada
     * @param template prompt con sus marcadores
     * @param http cliente HTTP
     * @param json lector y escritor de JSON
     */
    @SuppressWarnings("java:S107")
    public GeminiSpeechDirectorAdapter(String apiKey, URI baseUrl, String model, String thinkingLevel,
            Duration timeout, String template, HttpClient http, JsonMapper json) {
        this.apiKey = apiKey;
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.model = Objects.requireNonNull(model, "model");
        this.thinkingLevel = thinkingLevel == null ? "" : thinkingLevel.strip();
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.template = Objects.requireNonNull(template, "template");
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public Optional<Proposal> direct(Request request) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        HttpRequest call = HttpRequest.newBuilder(
                        baseUrl.resolve("/v1beta/models/" + model + ":generateContent"))
                .timeout(timeout)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(prompt(request)))))
                .build();
        try {
            HttpResponse<String> response = http.send(call, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                log.warn("Dirección de la voz no disponible; se lee la pregunta tal cual [status={}]",
                        response.statusCode());
                return Optional.empty();
            }
            return proposalOf(response.body());
        } catch (IOException failure) {
            log.warn("Dirección de la voz no disponible; se lee la pregunta tal cual [causa={}]",
                    failure.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException unexpected) {
            // Dirigir es una mejora: un fallo imprevisto tampoco impide leer la pregunta.
            log.warn("Dirección de la voz con un fallo imprevisto; se lee la pregunta tal cual [causa={}]",
                    unexpected.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Llena el prompt con la pregunta y las reglas del tono.
     *
     * @param request pregunta y contexto
     * @return el texto del prompt
     */
    String prompt(Request request) {
        String moment = switch (request.number()) {
            case 1 -> "la primera, abre la entrevista";
            case 5 -> "la última, cierra la entrevista";
            default -> "en medio de la entrevista";
        };
        String language = request.language() == InterviewLanguage.ENGLISH ? "inglés" : "español";
        String tags = String.join(", ", request.rules().tags().stream().map(tag -> "<" + tag + ">").toList());
        // La pregunta se reemplaza al final: un marcador escrito dentro de ella queda como texto.
        return template.replace("{{tono}}", request.tone().code())
                .replace("{{numero}}", String.valueOf(request.number()))
                .replace("{{momento}}", moment)
                .replace("{{idioma}}", language)
                .replace("{{estilos}}", String.join(", ", request.rules().styles()))
                .replace("{{etiquetas}}", tags)
                .replace("{{maximo}}", String.valueOf(SpeechDirectionPolicy.MAX_TAGS))
                .replace("{{guia_tono}}", request.rules().guide())
                .replace("{{pregunta}}", request.question());
    }

    ObjectNode body(String prompt) {
        ObjectNode root = json.createObjectNode();
        ObjectNode content = root.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", prompt);
        ObjectNode config = root.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseSchema", json.readTree(SCHEMA));
        config.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        if (!thinkingLevel.isEmpty()) {
            config.putObject("thinkingConfig").put("thinkingLevel", thinkingLevel);
        }
        return root;
    }

    private Optional<Proposal> proposalOf(String responseBody) {
        try {
            JsonNode parts = json.readTree(responseBody).path("candidates").path(0).path("content").path("parts");
            String text = null;
            for (JsonNode part : parts) {
                if (part.path("text").isString() && !part.path("thought").asBoolean(false)) {
                    text = part.path("text").asString();
                }
            }
            if (text == null) {
                log.warn("Dirección de la voz sin texto; se lee la pregunta tal cual");
                return Optional.empty();
            }
            JsonNode proposal = json.readTree(text);
            JsonNode style = proposal.get("estilo");
            JsonNode tagged = proposal.get("texto");
            if (style == null || tagged == null || !style.isString() || !tagged.isString()) {
                log.warn("Dirección de la voz sin estilo o sin texto; se lee la pregunta tal cual");
                return Optional.empty();
            }
            return Optional.of(new Proposal(tagged.asString(), style.asString()));
        } catch (JacksonException unreadable) {
            log.warn("Dirección de la voz ilegible; se lee la pregunta tal cual");
            return Optional.empty();
        }
    }
}
