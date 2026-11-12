package tech.cameia.entrevista.infrastructure.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;

import com.google.genai.Client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.infrastructure.media.AudioInspectorForTests;

/**
 * Transcripción real con Gemini de una respuesta en español sintetizada (src/test/resources/audio/voz.*), en los dos
 * formatos que graban los navegadores.
 *
 * <p>Es manual y nunca corre en la integración continua: solo con {@code LLM_LIVE_TEST=true}. Toma la llave de
 * {@code GOOGLE_GENAI_API_KEY} y el modelo de {@code TRANSCRIPTION_MODEL} o {@code LLM_MODEL}. No arranca Spring ni
 * necesita la base. Imprime solo el modelo, los tokens y la longitud del texto, nunca la llave.</p>
 *
 * <p>Ejecución en PowerShell:
 * {@code $env:LLM_LIVE_TEST="true"; ./mvnw.cmd test "-Dtest=GeminiTranscriptionLiveTest"}.</p>
 */
@EnabledIfEnvironmentVariable(named = "LLM_LIVE_TEST", matches = "true")
class GeminiTranscriptionLiveTest {

    @ParameterizedTest
    @ValueSource(strings = {"voz.webm", "voz-safari.mp4"})
    @DisplayName("Una transcripción real devuelve el texto de la respuesta en español")
    void transcribe_shouldReturnSpanishText(String name) throws IOException {
        String model = System.getenv().getOrDefault("TRANSCRIPTION_MODEL", System.getenv("LLM_MODEL"));
        Client client = Client.builder().apiKey(System.getenv("GOOGLE_GENAI_API_KEY")).build();
        GeminiTranscriptionAdapter adapter = new GeminiTranscriptionAdapter(client,
                new TranscriptionProperties(model, Duration.ofSeconds(90)));
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/audio/" + name)) {
            bytes = in.readAllBytes();
        }
        AudioClip clip = new AudioClip(bytes, AudioInspectorForTests.inspect(bytes).orElseThrow());

        Transcription result = adapter.transcribe(clip, InterviewLanguage.SPANISH_COLOMBIA);

        System.out.println("Transcripción real [" + name + "]: " + result + ", tokens=" + result.inputTokens() + "/"
                + result.outputTokens());
        String text = result.text().toLowerCase(Locale.ROOT);
        assertThat(text).contains("equipo").contains("conflicto");
    }
}
