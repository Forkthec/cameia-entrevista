package tech.cameia.entrevista.infrastructure.config.voz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.SpeechDirection;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.policy.SpeechDirectionPolicy;
import tech.cameia.entrevista.domain.port.SpeechDirectorPort;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;

/** Ensamble de la voz de las preguntas y validación de sus propiedades. */
@ExtendWith(OutputCaptureExtension.class)
class SpeechConfigurationTest {

    private final SpeechConfiguration configuration = new SpeechConfiguration();
    private final JsonMapper json = JsonMapper.builder().build();

    private static SpeechProperties properties(boolean enabled, Duration timeout, Duration directorTimeout) {
        return new SpeechProperties(enabled, URI.create("http://127.0.0.1:1"), "gemini-3.8-flash-tts",
                "gemini-3.1-flash-tts-preview", "Kore", "Kore", timeout, 3, 10, 900_000,
                new SpeechProperties.Director("gemini-3.5-flash-lite", directorTimeout, "low"));
    }

    @Test
    @DisplayName("CA-5.15.21: con la voz encendida y sin llave arranca, avisa una vez y responde SPEECH_UNAVAILABLE")
    void synthesis_shouldStartWithoutKey(CapturedOutput output) {
        SpeechSynthesisPort synthesis = configuration.speechSynthesisPort(properties(true, Duration.ofSeconds(1),
                Duration.ofSeconds(1)), "", json);

        assertThat(output).contains("GOOGLE_GENAI_API_KEY no está definida");
        assertThatThrownBy(() -> synthesis.synthesize(new SpeechDirection("Hola", "confident", false),
                InterviewLanguage.ENGLISH)).isInstanceOf(SpeechUnavailableException.class);
    }

    @Test
    @DisplayName("Con la voz apagada, o con llave, no avisa por la llave")
    void synthesis_shouldNotWarn_whenDisabledOrKeyPresent(CapturedOutput output) {
        configuration.speechSynthesisPort(properties(false, Duration.ofSeconds(1), Duration.ofSeconds(1)), null, json);
        configuration.speechSynthesisPort(properties(true, Duration.ofSeconds(1), Duration.ofSeconds(1)), "llave",
                json);

        assertThat(output).doesNotContain("GOOGLE_GENAI_API_KEY no está definida");
    }

    @Test
    @DisplayName("El director se arma con el prompt del repositorio y sin llave no dirige")
    void director_shouldLoadPrompt() {
        SpeechDirectorPort director = configuration.speechDirectorPort(properties(true, Duration.ofSeconds(1),
                Duration.ofSeconds(1)), "", json);

        assertThat(director.direct(new SpeechDirectorPort.Request("Hola", 1, Tone.WARM, InterviewLanguage.ENGLISH,
                new SpeechDirectionPolicy().rulesFor(Tone.WARM)))).isEmpty();
    }

    @Test
    void settingsAndPolicies_shouldFollowProperties() {
        SpeechProperties props = properties(true, Duration.ofSeconds(15), Duration.ofSeconds(3));

        assertThat(configuration.speechSettings(props).maxConcurrent()).isEqualTo(10);
        assertThat(configuration.speechSettings(props).monthlyCharacterCap()).isEqualTo(900_000);
        assertThat(configuration.questionAudioPolicy(props).maxPlays()).isEqualTo(3);
        assertThat(configuration.speechDirectionPolicy()).isNotNull();
    }

    @Test
    @DisplayName("Los tiempos deben ser positivos; ausentes los reporta su @NotNull")
    void properties_shouldValidateTimeouts() {
        assertThat(properties(true, Duration.ofSeconds(1), Duration.ofSeconds(1)).isTimeoutPositive()).isTrue();
        assertThat(properties(true, Duration.ZERO, Duration.ofSeconds(1)).isTimeoutPositive()).isFalse();
        assertThat(properties(true, null, Duration.ofSeconds(1)).isTimeoutPositive()).isTrue();
        assertThat(properties(true, Duration.ofSeconds(1), Duration.ofSeconds(-1)).director().isTimeoutPositive())
                .isFalse();
        assertThat(properties(true, Duration.ofSeconds(1), null).director().isTimeoutPositive()).isTrue();
    }
}
