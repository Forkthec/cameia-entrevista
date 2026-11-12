package tech.cameia.entrevista.infrastructure.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TranscriptionFailure;
import tech.cameia.entrevista.domain.port.SpeechToTextPort;
import tech.cameia.entrevista.infrastructure.config.voz.VoiceServiceProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador del puerto de transcripción sobre el servicio de Voz (Python + FastAPI, WhisperX).
 *
 * <p><b>Contrato provisional</b> (Voz aún no publica el suyo): {@code POST {base}/api/v1/transcriptions} con
 * {@code multipart/form-data} de dos partes, {@code audio} (el archivo, con su tipo) y {@code language} (BCP-47 de la
 * entrevista). Responde {@code 200} con {@code {"text": "...", "language": "es"}}.</p>
 *
 * <p>Clasificación de cada intento (decide el reintento en el caso de uso):</p>
 * <ul>
 *   <li>{@link TranscriptionFailure#TRANSIENT}: {@code 503}, conexión rechazada o que no se pudo abrir a tiempo.</li>
 *   <li>{@link TranscriptionFailure#TIMEOUT}: la respuesta completa no llegó dentro de {@code cameia.voz.timeout}.</li>
 *   <li>{@link TranscriptionFailure#REJECTED}: cualquier otro estado, un corte a mitad de la respuesta o un cuerpo
 *       ilegible.</li>
 * </ul>
 *
 * <p>El audio solo viaja en el cuerpo de la petición: no se escribe en disco ni en el registro. Los registros llevan
 * el estado HTTP y la clase de la causa, nunca el texto transcrito.</p>
 */
@Component
class VoiceServiceTranscriptionAdapter implements SpeechToTextPort {

    private static final Logger log = LoggerFactory.getLogger(VoiceServiceTranscriptionAdapter.class);

    /** Ruta del recurso de transcripción en Voz. */
    static final String PATH = "/api/v1/transcriptions";

    private static final int OK = 200;
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final String CRLF = "\r\n";

    private final HttpClient http;
    private final JsonMapper json;
    private final URI endpoint;
    private final Duration timeout;

    VoiceServiceTranscriptionAdapter(@Qualifier("voiceServiceHttpClient") HttpClient http, JsonMapper json,
            VoiceServiceProperties properties) {
        this.http = http;
        this.json = json;
        this.endpoint = properties.baseUrl().resolve(PATH);
        this.timeout = properties.timeout();
    }

    @Override
    public Transcription transcribe(AudioClip audio, InterviewLanguage language) {
        String boundary = "cameia-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, audio, language)))
                .build();
        HttpResponse<String> response = send(request);
        return read(response);
    }

    // El tope cubre la respuesta completa, no solo los encabezados: HttpRequest.timeout no corta un cuerpo lento.
    private HttpResponse<String> send(HttpRequest request) {
        CompletableFuture<HttpResponse<String>> call = http.sendAsync(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException expired) {
            call.cancel(true);
            throw failure(TranscriptionFailure.TIMEOUT, expired);
        } catch (InterruptedException interrupted) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(TranscriptionFailure.REJECTED, interrupted);
        } catch (ExecutionException failed) {
            throw failure(classify(failed.getCause()), failed.getCause());
        }
    }

    private static TranscriptionFailure classify(Throwable cause) {
        if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException) {
            return TranscriptionFailure.TRANSIENT;
        }
        if (cause instanceof HttpTimeoutException) {
            return TranscriptionFailure.TIMEOUT;
        }
        return TranscriptionFailure.REJECTED;
    }

    private Transcription read(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == SERVICE_UNAVAILABLE) {
            throw failure(TranscriptionFailure.TRANSIENT, "estado=" + status);
        }
        if (status != OK) {
            throw failure(TranscriptionFailure.REJECTED, "estado=" + status);
        }
        try {
            JsonNode body = json.readTree(response.body());
            JsonNode text = body.get("text");
            if (text == null || !text.isString()) {
                throw failure(TranscriptionFailure.REJECTED, "cuerpo sin text");
            }
            JsonNode language = body.get("language");
            return new Transcription(text.asString(),
                    language != null && language.isString() ? language.asString() : null);
        } catch (JacksonException unreadable) {
            throw failure(TranscriptionFailure.REJECTED, unreadable);
        }
    }

    private static byte[] multipart(String boundary, AudioClip audio, InterviewLanguage language) {
        ByteArrayOutputStream body = new ByteArrayOutputStream(audio.content().length + 512);
        write(body, "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"language\"" + CRLF + CRLF
                + language.code() + CRLF
                + "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"respuesta\"" + CRLF
                + "Content-Type: " + safeType(audio.contentType()) + CRLF + CRLF);
        body.writeBytes(audio.content());
        write(body, CRLF + "--" + boundary + "--" + CRLF);
        return body.toByteArray();
    }

    // El tipo lo declara el cliente: un salto de línea permitiría inyectar encabezados en la parte.
    private static String safeType(String contentType) {
        return contentType.chars().anyMatch(c -> c < 0x20 || c == 0x7f) ? AudioClip.UNKNOWN_TYPE : contentType;
    }

    private static void write(ByteArrayOutputStream body, String text) {
        body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private static TranscriptionUnavailableException failure(TranscriptionFailure kind, Throwable cause) {
        log.warn("Intento de transcripción fallido [causa={}, tipo={}]", kind,
                cause == null ? "-" : cause.getClass().getSimpleName());
        return new TranscriptionUnavailableException(kind, cause);
    }

    private static TranscriptionUnavailableException failure(TranscriptionFailure kind, String reason) {
        log.warn("Intento de transcripción fallido [causa={}, detalle={}]", kind, reason);
        return new TranscriptionUnavailableException(kind, new IOException(reason));
    }
}
