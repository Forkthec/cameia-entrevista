package tech.cameia.entrevista.infrastructure.speech;

import java.util.concurrent.TimeUnit;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.port.TranscriptionPort;

/**
 * Implementa {@link TranscriptionPort} con Gemini: el audio viaja en línea dentro de la propia llamada
 * ({@code inline_data}), sin subirlo a la API de archivos ni a ningún bucket, y la respuesta es el texto plano.
 *
 * <p>Es el adaptador mínimo de HU-5.8 (CM-345). La detección del idioma de la respuesta y los reintentos de la
 * transcripción son de HU-5.12 (CM-349) y de CM-362.</p>
 *
 * <p>Una llamada hace un solo intento y espera como máximo {@code cameia.transcription.timeout}. Por cada llamada se
 * registra una sola línea con el resultado, el formato, el tamaño y la duración; nunca el audio ni el texto.</p>
 */
@Component
@EnableConfigurationProperties(TranscriptionProperties.class)
class GeminiTranscriptionAdapter implements TranscriptionPort {

    private static final Logger logger = LoggerFactory.getLogger(GeminiTranscriptionAdapter.class);

    /** Respuesta que el prompt pide cuando el audio no trae voz inteligible. */
    static final String NO_SPEECH = "<<SIN_VOZ>>";

    private static final String INSTRUCTIONS = """
            Eres un servicio de transcripción. Transcribe palabra por palabra lo que dice la persona en el audio, \
            que es su respuesta a una pregunta de entrevista de trabajo. El idioma esperado es %s, pero transcribe \
            en el idioma en que la persona habla, sin traducir. No resumas, no corrijas, no agregues comentarios, \
            etiquetas, marcas de tiempo ni comillas. Devuelve solo el texto transcrito. Si el audio no contiene voz \
            inteligible, devuelve exactamente %s.""";

    private final Client client;
    private final TranscriptionProperties properties;

    GeminiTranscriptionAdapter(Client client, TranscriptionProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Transcription transcribe(AudioClip clip, InterviewLanguage language) {
        long start = System.nanoTime();
        String result = "ok";
        try {
            return toTranscription(call(clip, language));
        } catch (TranscriptionUnavailableException failure) {
            result = failure.getErrorCode().name();
            throw failure;
        } finally {
            logger.info("Transcripción [resultado={}, formato={}, bytes={}, audioMs={}, duracionMs={}]", result,
                    clip.format(), clip.content().length, clip.inspection().duration().toMillis(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
    }

    private GenerateContentResponse call(AudioClip clip, InterviewLanguage language) {
        Content request = Content.fromParts(
                Part.fromBytes(clip.content(), clip.format().mimeType()),
                Part.fromText(INSTRUCTIONS.formatted(languageName(language), NO_SPEECH)));
        try {
            return client.models.generateContent(properties.model(), request, config());
        } catch (RuntimeException failure) {
            // Cualquier fallo de la llamada (tiempo agotado, 4xx/5xx del proveedor, cuota, conexión) es pasajero
            // para la persona: puede grabar de nuevo o responder por texto.
            throw new TranscriptionUnavailableException(failure);
        }
    }

    private GenerateContentConfig config() {
        return GenerateContentConfig.builder()
                .temperature(0f)
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(properties.timeout().toMillis()))
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
    }

    private Transcription toTranscription(GenerateContentResponse response) {
        boolean hasCandidates = response != null && response.candidates().map(list -> !list.isEmpty()).orElse(false);
        String text = hasCandidates ? response.text() : null;
        if (text == null) {
            throw new TranscriptionUnavailableException(
                    new IllegalStateException("La transcripción llegó sin texto ni candidatos"));
        }
        String clean = text.strip();
        if (clean.contains(NO_SPEECH)) {
            clean = "";
        }
        GenerateContentResponseUsageMetadata usage = response.usageMetadata().orElse(null);
        long input = usage == null ? 0 : usage.promptTokenCount().orElse(0);
        long output = usage == null ? 0 : usage.candidatesTokenCount().orElse(0);
        return new Transcription(clean, response.modelVersion().filter(m -> !m.isBlank()).orElse(properties.model()),
                input, output);
    }

    private static String languageName(InterviewLanguage language) {
        return language == InterviewLanguage.ENGLISH ? "inglés" : "español de Colombia";
    }
}
