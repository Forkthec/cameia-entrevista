package tech.cameia.entrevista.infrastructure.client.assemblyai;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioAnswer;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TranscriptionFailure;
import tech.cameia.entrevista.domain.policy.TranscriptionPolicy;
import tech.cameia.entrevista.domain.port.SpeechTranscriptionPort;

/**
 * Adaptador de AssemblyAI (API REST v2) para transcribir una respuesta por voz.
 *
 * <p>Tres pasos, todos dentro del tiempo máximo configurado: sube el audio a {@code /v2/upload}, crea la
 * transcripción en {@code /v2/transcript} y consulta su estado hasta {@code completed} o {@code error}. Un solo
 * intento: no reintenta. Cada fallo sale clasificado para que el caso de uso decida el reintento de HU-5.13:
 * {@link TranscriptionFailure#TRANSIENT} (503 o conexión rechazada), {@link TranscriptionFailure#TIMEOUT} (se agotó
 * el tiempo del intento) o {@link TranscriptionFailure#REJECTED} (cualquier otro).</p>
 *
 * <p>Idioma (CA-5.12.2, CA-5.12.10): activa la detección automática con los dos idiomas del producto como
 * esperados y el de la entrevista como respaldo. Si el audio está en el idioma de la entrevista se transcribe en
 * él, sin traducir los términos técnicos del otro; si está en otro idioma, la respuesta trae ese idioma y su
 * confianza para que el dominio lo rechace. Sin voz, {@code on_no_speech_detected=fallback} hace que el proveedor
 * devuelva un texto vacío en lugar de un error, que el dominio responde como CA-5.12.7.</p>
 *
 * <p>Nunca registra el audio, la URL de subida ni el texto transcrito: solo el identificador de la transcripción
 * del proveedor y la razón de un fallo.</p>
 */
public class AssemblyAiTranscriptionAdapter implements SpeechTranscriptionPort {

    /** Nombre del proveedor que se guarda en {@code ejecucion_ia.proveedor}. */
    public static final String PROVIDER = "assemblyai";

    /** Modelo que se registra cuando se deja el valor por defecto del proveedor. */
    static final String DEFAULT_MODEL = "assemblyai-default";

    private static final int SERVICE_UNAVAILABLE = 503;

    private static final Logger log = LoggerFactory.getLogger(AssemblyAiTranscriptionAdapter.class);

    private final String apiKey;
    private final URI baseUrl;
    private final List<String> speechModels;
    private final Duration timeout;
    private final Duration pollInterval;
    private final HttpClient http;
    private final JsonMapper json;

    /**
     * Crea el adaptador.
     *
     * @param apiKey llave de la API; puede faltar, y entonces cada llamada falla con
     *        {@link TranscriptionUnavailableException}
     * @param baseUrl URL base de la API, sin barra final
     * @param speechModels modelos en orden de preferencia; vacía deja el valor por defecto del proveedor
     * @param timeout tiempo máximo de toda la transcripción
     * @param pollInterval espera entre dos consultas del estado
     * @param http cliente HTTP
     * @param json lector y escritor de JSON
     */
    public AssemblyAiTranscriptionAdapter(String apiKey, URI baseUrl, List<String> speechModels, Duration timeout,
            Duration pollInterval, HttpClient http, JsonMapper json) {
        this.apiKey = apiKey;
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.speechModels = List.copyOf(speechModels);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public Transcription transcribe(AudioAnswer audio, InterviewLanguage expected) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new TranscriptionUnavailableException("sin llave de AssemblyAI", null);
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        String uploadUrl = upload(audio, deadline);
        String transcriptId = create(uploadUrl, expected, deadline);
        return await(transcriptId, deadline);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return speechModels.isEmpty() ? DEFAULT_MODEL : String.join(",", speechModels);
    }

    private String upload(AudioAnswer audio, long deadline) {
        HttpRequest request = authorized(baseUrl.resolve("/v2/upload"), deadline)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(audio.content()))
                .build();
        JsonNode body = send(request, "subida");
        JsonNode url = body.get("upload_url");
        if (url == null || !url.isString() || url.asString().isBlank()) {
            throw new TranscriptionUnavailableException("subida sin upload_url", null);
        }
        return url.asString();
    }

    private String create(String uploadUrl, InterviewLanguage expected, long deadline) {
        String requestBody = json.writeValueAsString(transcriptRequest(uploadUrl, expected));
        HttpRequest request = authorized(baseUrl.resolve("/v2/transcript"), deadline)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        JsonNode body = send(request, "creación");
        JsonNode id = body.get("id");
        if (id == null || !id.isString() || id.asString().isBlank()) {
            throw new TranscriptionUnavailableException("creación sin id", null);
        }
        return id.asString();
    }

    /**
     * Cuerpo de la creación de la transcripción.
     *
     * @param uploadUrl URL del audio subido
     * @param expected idioma de la entrevista
     * @return el JSON de la petición
     */
    ObjectNode transcriptRequest(String uploadUrl, InterviewLanguage expected) {
        String own = TranscriptionPolicy.baseLanguage(expected.code());
        String other = expected == InterviewLanguage.ENGLISH
                ? TranscriptionPolicy.baseLanguage(InterviewLanguage.SPANISH_COLOMBIA.code())
                : TranscriptionPolicy.baseLanguage(InterviewLanguage.ENGLISH.code());
        ObjectNode root = json.createObjectNode();
        root.put("audio_url", uploadUrl);
        root.put("language_detection", true);
        ObjectNode options = root.putObject("language_detection_options");
        ArrayNode languages = options.putArray("expected_languages");
        languages.add(own);
        languages.add(other);
        options.put("fallback_language", own);
        options.put("on_no_speech_detected", "fallback");
        if (!speechModels.isEmpty()) {
            ArrayNode models = root.putArray("speech_models");
            speechModels.forEach(models::add);
        }
        return root;
    }

    private Transcription await(String transcriptId, long deadline) {
        URI status = baseUrl.resolve("/v2/transcript/" + transcriptId);
        while (true) {
            JsonNode body = send(authorized(status, deadline).GET().build(), "consulta");
            String state = body.path("status").isString() ? body.path("status").asString() : "";
            switch (state) {
                case "completed" -> {
                    return toTranscription(body, transcriptId);
                }
                case "error" -> {
                    // El mensaje del proveedor puede citar la URL del audio: no se registra, solo el identificador.
                    throw new TranscriptionUnavailableException("estado error [transcriptId=" + transcriptId + "]",
                            null);
                }
                case "queued", "processing" -> sleepUntilNextPoll(deadline);
                default -> throw new TranscriptionUnavailableException("estado desconocido", null);
            }
        }
    }

    private Transcription toTranscription(JsonNode body, String transcriptId) {
        JsonNode text = body.get("text");
        if (text != null && !text.isNull() && !text.isString()) {
            throw new TranscriptionUnavailableException("texto con tipo inválido", null);
        }
        JsonNode language = body.get("language_code");
        JsonNode confidence = body.get("language_confidence");
        log.info("Transcripción completada [transcriptId={}]", transcriptId);
        return new Transcription(text == null || text.isNull() ? "" : text.asString(),
                language != null && language.isString() ? language.asString() : null,
                confidence != null && confidence.isNumber() ? confidence.asDouble() : null);
    }

    private void sleepUntilNextPoll(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new TranscriptionUnavailableException(TranscriptionFailure.TIMEOUT, "tiempo agotado", null);
        }
        try {
            Thread.sleep(Duration.ofNanos(Math.min(remaining, pollInterval.toNanos())));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TranscriptionUnavailableException("espera interrumpida", interrupted);
        }
    }

    private HttpRequest.Builder authorized(URI uri, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new TranscriptionUnavailableException(TranscriptionFailure.TIMEOUT, "tiempo agotado", null);
        }
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofNanos(remaining))
                .header("Authorization", apiKey)
                .header("Accept", "application/json");
    }

    private JsonNode send(HttpRequest request, String step) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpConnectTimeoutException notConnected) {
            // La conexión no se pudo abrir: el proveedor no la aceptó a tiempo, es transitorio.
            throw new TranscriptionUnavailableException(TranscriptionFailure.TRANSIENT, "conexión no abierta en " + step,
                    notConnected);
        } catch (HttpTimeoutException timedOut) {
            throw new TranscriptionUnavailableException(TranscriptionFailure.TIMEOUT, "tiempo agotado en " + step,
                    timedOut);
        } catch (ConnectException refused) {
            throw new TranscriptionUnavailableException(TranscriptionFailure.TRANSIENT, "conexión rechazada en " + step,
                    refused);
        } catch (IOException failure) {
            throw new TranscriptionUnavailableException("conexión fallida en " + step, failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TranscriptionUnavailableException("petición interrumpida en " + step, interrupted);
        }
        if (response.statusCode() == SERVICE_UNAVAILABLE) {
            throw new TranscriptionUnavailableException(TranscriptionFailure.TRANSIENT, step + " respondió 503", null);
        }
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new TranscriptionUnavailableException(step + " respondió " + response.statusCode(), null);
        }
        try {
            JsonNode body = json.readTree(response.body());
            if (body == null || !body.isObject()) {
                throw new TranscriptionUnavailableException(step + " sin objeto JSON", null);
            }
            return body;
        } catch (JacksonException unreadable) {
            throw new TranscriptionUnavailableException(step + " con JSON ilegible", null);
        }
    }
}
