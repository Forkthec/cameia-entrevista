package tech.cameia.entrevista.infrastructure.client.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.SpeechAudio;
import tech.cameia.entrevista.domain.model.SpeechDirection;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.policy.SpeechDirectionPolicy;
import tech.cameia.entrevista.domain.port.SpeechDirectorPort;

/**
 * Llamada real a Gemini: el director coloca las etiquetas de una pregunta ficticia y Gemini TTS la lee.
 *
 * <p>Es manual y nunca corre en la integración continua: solo con {@code SPEECH_LIVE_TEST=true}. Toma
 * {@code GOOGLE_GENAI_API_KEY} del entorno o del {@code .env}. Hace dos llamadas (director y voz), o tres con
 * {@code SPEECH_LIVE_FALLBACK=true} (también el modelo de respaldo). Deja los WAV en {@code target/voz-en-vivo/} para
 * escucharlos y no imprime la llave; el texto es ficticio.</p>
 *
 * <p>PowerShell: {@code $env:SPEECH_LIVE_TEST="true"; ./mvnw.cmd test "-Dtest=GeminiSpeechLiveTest"}.</p>
 */
@EnabledIfEnvironmentVariable(named = "SPEECH_LIVE_TEST", matches = "true")
class GeminiSpeechLiveTest {

    private static final URI BASE = URI.create("https://generativelanguage.googleapis.com");
    private static final String QUESTION = "Describa exactamente una ocasión en la que un despliegue a producción"
            + " falló. ¿Qué métrica usó para medir el impacto y qué resultado cuantificable obtuvo tras corregirlo?";

    private final JsonMapper json = JsonMapper.builder().build();
    private final SpeechDirectionPolicy policy = new SpeechDirectionPolicy();

    @Test
    @DisplayName("El director propone las etiquetas (o se lee tal cual) y Gemini TTS entrega un WAV")
    void directAndSynthesize_shouldReturnWav_whenCalledWithRealKey() throws IOException {
        String key = apiKey();
        HttpClient http = HttpClient.newHttpClient();
        GeminiSpeechDirectorAdapter director = new GeminiSpeechDirectorAdapter(key, BASE, "gemini-3.5-flash-lite",
                "low", Duration.ofSeconds(10), prompt(), http, json);

        long start = System.nanoTime();
        Optional<SpeechDirectorPort.Proposal> proposal = director.direct(new SpeechDirectorPort.Request(QUESTION, 3,
                Tone.STRICT, InterviewLanguage.SPANISH_COLOMBIA, policy.rulesFor(Tone.STRICT)));
        long directorMs = (System.nanoTime() - start) / 1_000_000;
        Optional<SpeechDirection> accepted = proposal.flatMap(p -> policy.accept(QUESTION, Tone.STRICT, p.text(),
                p.style()));
        System.out.println("Director: ms=" + directorMs + ", propuesta=" + proposal.isPresent() + ", aceptada="
                + accepted.isPresent() + accepted.map(d -> ", texto=" + d.text() + ", estilo=" + d.style()).orElse(""));

        boolean fallback = "true".equals(System.getenv("SPEECH_LIVE_FALLBACK"));
        GeminiSpeechSynthesisAdapter synthesis = new GeminiSpeechSynthesisAdapter(key, BASE,
                fallback ? "modelo-inexistente-para-forzar-el-respaldo" : "gemini-3.8-flash-tts",
                "gemini-3.1-flash-tts-preview", Map.of(InterviewLanguage.SPANISH_COLOMBIA, "Kore",
                        InterviewLanguage.ENGLISH, "Kore"), Duration.ofSeconds(30), http, json);
        start = System.nanoTime();
        SpeechAudio audio;
        try {
            // Sin dirección válida se lee la pregunta tal cual, como en el caso de uso.
            audio = synthesis.synthesize(accepted.orElseGet(() -> policy.plain(QUESTION, Tone.STRICT)),
                    InterviewLanguage.SPANISH_COLOMBIA);
        } catch (SpeechUnavailableException failed) {
            System.out.println("Voz fallida: " + failed.getReason());
            throw failed;
        }
        System.out.println("Voz: ms=" + (System.nanoTime() - start) / 1_000_000 + ", " + audio);

        assertThat(GeminiSpeechSynthesisAdapter.isWav(audio.content())).isTrue();
        Path out = Path.of("target", "voz-en-vivo", audio.model() + ".wav");
        Files.createDirectories(out.getParent());
        Files.write(out, audio.content());
    }

    private static String prompt() throws IOException {
        try (InputStream in = new ClassPathResource("prompts/voz.direccion.v1.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String apiKey() throws IOException {
        String fromEnv = System.getenv("GOOGLE_GENAI_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.strip();
        }
        return Files.readAllLines(Path.of(".env"), StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("GOOGLE_GENAI_API_KEY="))
                .map(line -> line.substring("GOOGLE_GENAI_API_KEY=".length()).strip())
                .findFirst().orElseThrow(() -> new IllegalStateException("Falta GOOGLE_GENAI_API_KEY"));
    }
}
