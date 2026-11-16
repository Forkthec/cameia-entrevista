package tech.cameia.entrevista.infrastructure.client.gemini;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import tech.cameia.entrevista.domain.exception.SpeechTimeoutException;
import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.SpeechAudio;
import tech.cameia.entrevista.domain.model.SpeechDirection;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;

/**
 * Adaptador de Gemini TTS (Interactions API, {@code POST /v1beta/interactions}) para leer en voz alta una pregunta.
 *
 * <p>Modelo principal ({@code gemini-3.8-flash-tts} por defecto): las etiquetas van entre ángulos y el estilo en
 * {@code speech_metadata}. Si responde 429 (mucha demanda o cuota), 404 (modelo retirado) o 5xx, o la conexión
 * falla, y queda tiempo, se intenta una vez con el
 * modelo de respaldo ({@code gemini-3.1-flash-tts-preview}): ese modelo usa corchetes y no admite
 * {@code speech_metadata}, así que el estilo va como etiqueta inicial. Los dos intentos caben en el tiempo máximo.</p>
 *
 * <p>El audio sale en WAV (PCM 16 bits, 24 kHz, mono): se valida que no esté vacío y que traiga la cabecera
 * {@code RIFF....WAVE}. Una voz por idioma, fija por configuración; el tono cambia la dirección, no la voz.</p>
 *
 * <p>Nunca registra el texto ni el audio: solo el modelo, el estado HTTP y la razón de un fallo.</p>
 */
public class GeminiSpeechSynthesisAdapter implements SpeechSynthesisPort {

    /** Tipo de contenido del audio que se entrega. */
    public static final String MEDIA_TYPE = "audio/wav";

    private static final int NOT_FOUND = 404;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int WAV_HEADER = 44;
    private static final Pattern ANGLE_TAG = Pattern.compile("<([a-z]+(?: [a-z]+)?)>");
    private static final Logger log = LoggerFactory.getLogger(GeminiSpeechSynthesisAdapter.class);

    private final String apiKey;
    private final URI baseUrl;
    private final String model;
    private final String fallbackModel;
    private final Map<InterviewLanguage, String> voices;
    private final Duration timeout;
    private final HttpClient http;
    private final JsonMapper json;

    /**
     * Crea el adaptador.
     *
     * @param apiKey llave de Gemini; si falta, cada llamada falla con {@link SpeechUnavailableException}
     * @param baseUrl URL base de la API, sin barra final
     * @param model modelo principal
     * @param fallbackModel modelo de respaldo; en blanco, no hay respaldo
     * @param voices voz por idioma
     * @param timeout tiempo máximo de toda la generación
     * @param http cliente HTTP
     * @param json lector y escritor de JSON
     */
    @SuppressWarnings("java:S107")
    public GeminiSpeechSynthesisAdapter(String apiKey, URI baseUrl, String model, String fallbackModel,
            Map<InterviewLanguage, String> voices, Duration timeout, HttpClient http, JsonMapper json) {
        this.apiKey = apiKey;
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.model = Objects.requireNonNull(model, "model");
        this.fallbackModel = fallbackModel == null ? "" : fallbackModel.strip();
        this.voices = Map.copyOf(voices);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public SpeechAudio synthesize(SpeechDirection direction, InterviewLanguage language) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SpeechUnavailableException("sin llave de Gemini", null);
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        String voice = voices.get(language);
        Attempt first = call(model, primaryBody(direction, voice), deadline);
        if (first.audio != null) {
            return first.audio;
        }
        if (first.retryable && !fallbackModel.isEmpty() && deadline - System.nanoTime() > 0) {
            log.warn("Voz principal no disponible; se usa el respaldo [modelo={}, causa={}]", model, first.reason);
            Attempt second = call(fallbackModel, fallbackBody(direction, voice), deadline);
            if (second.audio != null) {
                return second.audio;
            }
            throw new SpeechUnavailableException(second.reason, null);
        }
        throw new SpeechUnavailableException(first.reason, null);
    }

    /** Resultado de un intento: el audio, o la razón del fallo y si vale la pena el respaldo. */
    private record Attempt(SpeechAudio audio, String reason, boolean retryable) {
    }

    ObjectNode primaryBody(SpeechDirection direction, String voice) {
        ObjectNode content = textItem(direction.text());
        ObjectNode metadata = content.putArray("annotations").addObject();
        metadata.put("type", "speech_metadata");
        metadata.put("style", direction.style());
        return body(model, content, voice);
    }

    ObjectNode fallbackBody(SpeechDirection direction, String voice) {
        String bracketed = ANGLE_TAG.matcher(direction.text()).replaceAll("[$1]");
        return body(fallbackModel, textItem("[" + direction.style() + "] " + bracketed), voice);
    }

    private ObjectNode textItem(String text) {
        ObjectNode content = json.createObjectNode();
        content.put("type", "text");
        content.put("text", text);
        return content;
    }

    private ObjectNode body(String modelId, ObjectNode content, String voice) {
        ObjectNode root = json.createObjectNode();
        root.put("model", modelId);
        ObjectNode input = root.putArray("input").addObject();
        input.put("type", "user_input");
        input.putArray("content").add(content);
        // Sin mime_type: WAV es el formato por defecto y el modelo de respaldo rechaza el campo (400, 15-11-2026).
        root.putObject("response_format").put("type", "audio");
        ArrayNode speech = root.putObject("generation_config").putArray("speech_config");
        speech.addObject().put("voice", voice);
        return root;
    }

    private Attempt call(String modelId, ObjectNode body, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new SpeechTimeoutException("tiempo agotado antes de " + modelId, null);
        }
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/v1beta/interactions"))
                .timeout(Duration.ofNanos(remaining))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException timedOut) {
            throw new SpeechTimeoutException("tiempo agotado [modelo=" + modelId + "]", timedOut);
        } catch (IOException failure) {
            return new Attempt(null, "conexión fallida [modelo=" + modelId + "]", true);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SpeechUnavailableException("petición interrumpida", interrupted);
        }
        int status = response.statusCode();
        if (status == TOO_MANY_REQUESTS || status == NOT_FOUND || status >= 500) {
            return new Attempt(null, "status=" + status + " [modelo=" + modelId + "]", true);
        }
        if (status < 200 || status > 299) {
            return new Attempt(null, "status=" + status + " [modelo=" + modelId + "]", false);
        }
        return audioOf(response.body(), modelId);
    }

    private Attempt audioOf(String responseBody, String modelId) {
        JsonNode root;
        try {
            root = json.readTree(responseBody);
        } catch (JacksonException unreadable) {
            return new Attempt(null, "JSON ilegible [modelo=" + modelId + "]", false);
        }
        String data = findAudio(root);
        if (data == null) {
            return new Attempt(null, "respuesta sin audio [modelo=" + modelId + "]", false);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException notBase64) {
            return new Attempt(null, "audio no es base64 [modelo=" + modelId + "]", false);
        }
        if (!isWav(bytes)) {
            return new Attempt(null, "audio vacío o no es WAV [modelo=" + modelId + "]", false);
        }
        return new Attempt(new SpeechAudio(bytes, MEDIA_TYPE, modelId), null, false);
    }

    // steps[].content[] con type=audio; la forma de la respuesta del 15-11-2026.
    private static String findAudio(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (JsonNode step : root.path("steps")) {
            for (JsonNode content : step.path("content")) {
                JsonNode data = content.get("data");
                if ("audio".equals(content.path("type").asString("")) && data != null && data.isString()
                        && !data.asString().isEmpty()) {
                    return data.asString();
                }
            }
        }
        return null;
    }

    /**
     * Comprueba que los bytes son un WAV con datos: cabecera {@code RIFF....WAVE} y algo más que la cabecera.
     *
     * @param bytes audio recibido
     * @return {@code true} si es un WAV con datos
     */
    static boolean isWav(byte[] bytes) {
        return bytes.length > WAV_HEADER && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
    }
}
