package tech.cameia.entrevista.infrastructure.client.googletts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import tech.cameia.entrevista.domain.exception.SpeechTimeoutException;
import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.SpeechAudio;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;

/**
 * Adaptador de Google Cloud Text-to-Speech ({@code POST /v1/text:synthesize}) para leer en voz alta una pregunta con
 * una voz Chirp 3 HD.
 *
 * <p>Una voz por idioma, fija por configuración ({@code es-US-Chirp3-HD-…} y {@code en-US-Chirp3-HD-…}); el idioma de
 * la voz sale de su nombre y el tono de la entrevista no la cambia (CA-5.15.3). El texto viaja en UTF-8 sin alterarse
 * y nunca pasa de los 5.000 bytes que admite Google (CA-5.15.19).</p>
 *
 * <p>Pide MP3 y valida que el audio no esté vacío y empiece con una cabecera MP3 (una etiqueta ID3 seguida de un
 * marco MPEG, o el marco directamente). Un 400, 403, 429 o 5xx, una conexión fallida, un JSON ilegible, un cuerpo
 * cortado o un audio inválido responden {@link SpeechUnavailableException}. No hay reintento: todo cabe en el tiempo
 * máximo, que cuenta desde que se pide la credencial (CA-5.15.21, CA-5.15.22).</p>
 *
 * <p>Nunca registra el texto, el audio ni la credencial: solo la voz, el estado HTTP y la razón de un fallo.</p>
 */
public class GoogleTextToSpeechAdapter implements SpeechSynthesisPort {

    /** Tipo de contenido del audio que se entrega. */
    public static final String MEDIA_TYPE = "audio/mpeg";

    /** Bytes máximos del texto que admite Google en una petición. */
    static final int MAX_TEXT_BYTES = 5_000;

    private static final String PATH = "/v1/text:synthesize";
    private static final int ID3_HEADER = 10;
    private static final int MIN_FRAME = 4;

    private final SpeechCredentials credentials;
    private final URI endpoint;
    private final Map<InterviewLanguage, String> voices;
    private final Duration timeout;
    private final HttpClient http;
    private final JsonMapper json;

    /**
     * Crea el adaptador.
     *
     * @param credentials credencial ante Google
     * @param baseUrl URL base de la API, sin barra final
     * @param voices voz por idioma, con la forma {@code idioma-PAIS-Chirp3-HD-Nombre}
     * @param timeout tiempo máximo de la generación
     * @param http cliente HTTP
     * @param json lector y escritor de JSON
     */
    public GoogleTextToSpeechAdapter(SpeechCredentials credentials, URI baseUrl,
            Map<InterviewLanguage, String> voices, Duration timeout, HttpClient http, JsonMapper json) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.endpoint = Objects.requireNonNull(baseUrl, "baseUrl").resolve(PATH);
        this.voices = Map.copyOf(voices);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public SpeechAudio synthesize(String text, InterviewLanguage language) {
        long deadline = System.nanoTime() + timeout.toNanos();
        String voice = voices.get(language);
        if (voice == null) {
            throw new SpeechUnavailableException("sin voz para el idioma " + language, null);
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new SpeechUnavailableException("el texto supera los 5.000 bytes de Google", null);
        }
        Map<String, String> auth = credentials.headers(endpoint);
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new SpeechTimeoutException("tiempo agotado al obtener la credencial", null);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofNanos(remaining))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(text, voice)),
                        StandardCharsets.UTF_8));
        auth.forEach(request::header);
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException timedOut) {
            throw new SpeechTimeoutException("tiempo agotado [voz=" + voice + "]", timedOut);
        } catch (IOException failure) {
            throw new SpeechUnavailableException("conexión fallida [voz=" + voice + "]", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SpeechUnavailableException("petición interrumpida", interrupted);
        }
        int status = response.statusCode();
        if (status < 200 || status > 299) {
            throw new SpeechUnavailableException("status=" + status + " [voz=" + voice + "]", null);
        }
        return new SpeechAudio(audioOf(response.body(), voice), MEDIA_TYPE, voice);
    }

    ObjectNode body(String text, String voice) {
        ObjectNode root = json.createObjectNode();
        root.putObject("input").put("text", text);
        ObjectNode voiceNode = root.putObject("voice");
        voiceNode.put("languageCode", languageCodeOf(voice));
        voiceNode.put("name", voice);
        root.putObject("audioConfig").put("audioEncoding", "MP3");
        return root;
    }

    private byte[] audioOf(String responseBody, String voice) {
        JsonNode root;
        try {
            root = json.readTree(responseBody);
        } catch (JacksonException unreadable) {
            throw new SpeechUnavailableException("JSON ilegible [voz=" + voice + "]", null);
        }
        JsonNode data = root == null ? null : root.get("audioContent");
        if (data == null || !data.isString() || data.asString().isEmpty()) {
            throw new SpeechUnavailableException("respuesta sin audio [voz=" + voice + "]", null);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data.asString());
        } catch (IllegalArgumentException notBase64) {
            throw new SpeechUnavailableException("audio no es base64 [voz=" + voice + "]", null);
        }
        if (!isMp3(bytes)) {
            throw new SpeechUnavailableException("audio vacío o no es MP3 [voz=" + voice + "]", null);
        }
        return bytes;
    }

    /**
     * Idioma de la voz: las dos primeras partes de su nombre ({@code es-US-Chirp3-HD-Kore} da {@code es-US}).
     *
     * @param voice nombre de la voz
     * @return el código de idioma
     * @throws IllegalArgumentException si el nombre no tiene esa forma
     */
    public static String languageCodeOf(String voice) {
        String[] parts = voice.split("-", 3);
        if (parts.length < 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new IllegalArgumentException("La voz debe tener la forma idioma-PAIS-modelo-nombre");
        }
        return parts[0] + "-" + parts[1];
    }

    /**
     * Comprueba que los bytes son un MP3 con datos: un marco MPEG al inicio, o una etiqueta ID3 seguida de un marco.
     *
     * @param bytes audio recibido
     * @return {@code true} si es un MP3 con al menos un marco
     */
    static boolean isMp3(byte[] bytes) {
        long start = 0;
        if (bytes.length >= ID3_HEADER && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
            // Tamaño de la etiqueta en 4 bytes de 7 bits (synchsafe), sin su cabecera de 10 bytes.
            start = ID3_HEADER + ((long) (bytes[6] & 0x7F) << 21 | (bytes[7] & 0x7F) << 14 | (bytes[8] & 0x7F) << 7
                    | (bytes[9] & 0x7F));
        }
        if (bytes.length - start <= MIN_FRAME) {
            return false;
        }
        int frame = (int) start;
        return (bytes[frame] & 0xFF) == 0xFF && (bytes[frame + 1] & 0xE0) == 0xE0;
    }
}
