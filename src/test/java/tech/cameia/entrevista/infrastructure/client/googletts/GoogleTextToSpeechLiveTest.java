package tech.cameia.entrevista.infrastructure.client.googletts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.SpeechAudio;

/**
 * Llamada real a Google Cloud Text-to-Speech con una pregunta ficticia por idioma.
 *
 * <p>Es manual y nunca corre en la integración continua: solo con {@code SPEECH_LIVE_TEST=true}. Usa
 * {@code SPEECH_API_KEY} si está definida y, si no, las credenciales por defecto de la aplicación
 * ({@code gcloud auth application-default login} o {@code GOOGLE_APPLICATION_CREDENTIALS}). Hace una llamada por
 * idioma, deja los MP3 en {@code target/voz-en-vivo/} para escucharlos y no imprime la credencial.</p>
 *
 * <p>PowerShell: {@code $env:SPEECH_LIVE_TEST="true"; ./mvnw.cmd test "-Dtest=GoogleTextToSpeechLiveTest"}.</p>
 */
@EnabledIfEnvironmentVariable(named = "SPEECH_LIVE_TEST", matches = "true")
class GoogleTextToSpeechLiveTest {

    private static final Map<InterviewLanguage, String> QUESTIONS = Map.of(
            InterviewLanguage.SPANISH_COLOMBIA, "Cuénteme de una ocasión en la que un despliegue a producción falló."
                    + " ¿Qué métrica usó para medir el impacto y qué resultado obtuvo tras corregirlo?",
            InterviewLanguage.ENGLISH, "Tell me about a time a production deployment failed. Which metric did you"
                    + " use to measure the impact, and what result did you get after fixing it?");

    @ParameterizedTest
    @EnumSource(InterviewLanguage.class)
    @DisplayName("Google entrega un MP3 con la voz Chirp 3 HD del idioma dentro de 10 s")
    void synthesize_shouldReturnMp3_whenCalledWithRealCredentials(InterviewLanguage language) throws IOException {
        String apiKey = System.getenv("SPEECH_API_KEY");
        SpeechCredentials credentials = apiKey != null && !apiKey.isBlank() ? SpeechCredentials.apiKey(apiKey.strip())
                : SpeechCredentials.applicationDefault();
        GoogleTextToSpeechAdapter adapter = new GoogleTextToSpeechAdapter(credentials,
                URI.create("https://texttospeech.googleapis.com"),
                Map.of(InterviewLanguage.SPANISH_COLOMBIA, voice("SPEECH_VOICE_ES", "es-US-Chirp3-HD-Kore"),
                        InterviewLanguage.ENGLISH, voice("SPEECH_VOICE_EN", "en-US-Chirp3-HD-Kore")),
                Duration.ofSeconds(10), HttpClient.newHttpClient(), JsonMapper.builder().build());

        long start = System.nanoTime();
        SpeechAudio audio;
        try {
            audio = adapter.synthesize(QUESTIONS.get(language), language);
        } catch (SpeechUnavailableException failed) {
            System.out.println("Voz fallida: " + failed.getReason());
            throw failed;
        }
        System.out.println("Voz: ms=" + (System.nanoTime() - start) / 1_000_000 + ", " + audio);

        assertThat(GoogleTextToSpeechAdapter.isMp3(audio.content())).isTrue();
        Path out = Path.of("target", "voz-en-vivo", audio.voice() + ".mp3");
        Files.createDirectories(out.getParent());
        Files.write(out, audio.content());
    }

    private static String voice(String variable, String fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
