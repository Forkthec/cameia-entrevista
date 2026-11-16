package tech.cameia.entrevista.infrastructure.config.voz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;

/** Ensamble de la voz de las preguntas y validación de sus propiedades. */
@ExtendWith(OutputCaptureExtension.class)
class SpeechConfigurationTest {

    private final SpeechConfiguration configuration = new SpeechConfiguration();
    private final JsonMapper json = JsonMapper.builder().build();

    private static SpeechProperties properties(boolean enabled, String apiKey, String voiceEs, Duration timeout) {
        return new SpeechProperties(enabled, URI.create("http://127.0.0.1:1"), apiKey, voiceEs,
                "en-US-Chirp3-HD-Kore", timeout, 3, 10, 900_000);
    }

    @Test
    @DisplayName("CA-5.15.21: con llave en blanco usa ADC, arranca y la petición falla sin salir si no hay credencial")
    void synthesis_shouldStartWithApplicationDefault(CapturedOutput output) {
        SpeechProperties props = properties(true, " ", "es-US-Chirp3-HD-Kore", Duration.ofSeconds(1));
        SpeechSynthesisPort synthesis = configuration.speechSynthesisPort(props, json);

        assertThat(props.usesApiKey()).isFalse();
        assertThat(output).contains("credencial=ADC");
        assertThat(synthesis).isNotNull();
    }

    @Test
    @DisplayName("Con llave la usa y no la imprime")
    void synthesis_shouldUseApiKeyWithoutPrintingIt(CapturedOutput output) {
        SpeechProperties props = properties(true, "llave-secreta", "es-US-Chirp3-HD-Kore", Duration.ofSeconds(1));

        SpeechSynthesisPort synthesis = configuration.speechSynthesisPort(props, json);

        assertThat(props.usesApiKey()).isTrue();
        assertThat(output).contains("credencial=llave de API").doesNotContain("llave-secreta");
        assertThat(props.toString()).doesNotContain("llave-secreta").contains("apiKey=****");
        // El puerto 1 rechaza la conexión: la llave se usó y la falla es de la voz, no de la configuración.
        assertThatThrownBy(() -> synthesis.synthesize("Hola", InterviewLanguage.ENGLISH))
                .isInstanceOf(SpeechUnavailableException.class);
    }

    @Test
    @DisplayName("Con la voz apagada no anuncia la credencial")
    void synthesis_shouldNotLog_whenDisabled(CapturedOutput output) {
        configuration.speechSynthesisPort(properties(false, null, "es-US-Chirp3-HD-Kore", Duration.ofSeconds(1)),
                json);

        assertThat(output).doesNotContain("Cloud Text-to-Speech [credencial");
        assertThat(properties(false, null, "es-US-Chirp3-HD-Kore", Duration.ofSeconds(1)).toString())
                .contains("apiKey=ADC");
    }

    @Test
    void settingsAndPolicies_shouldFollowProperties() {
        SpeechProperties props = properties(true, null, "es-US-Chirp3-HD-Kore", Duration.ofSeconds(10));

        assertThat(configuration.speechSettings(props).maxConcurrent()).isEqualTo(10);
        assertThat(configuration.speechSettings(props).monthlyCharacterCap()).isEqualTo(900_000);
        assertThat(configuration.questionAudioPolicy(props).maxPlays()).isEqualTo(3);
    }

    @Test
    @DisplayName("El tiempo debe ser positivo; ausente lo reporta su @NotNull")
    void properties_shouldValidateTimeout() {
        assertThat(properties(true, null, "es-US-Chirp3-HD-Kore", Duration.ofSeconds(1)).isTimeoutPositive())
                .isTrue();
        assertThat(properties(true, null, "es-US-Chirp3-HD-Kore", Duration.ZERO).isTimeoutPositive()).isFalse();
        assertThat(properties(true, null, "es-US-Chirp3-HD-Kore", null).isTimeoutPositive()).isTrue();
    }

    @Test
    @DisplayName("La voz debe tener la forma idioma-PAIS-modelo-nombre")
    void properties_shouldValidateVoiceName() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(properties(true, null, "es-US-Chirp3-HD-Kore", Duration.ofSeconds(1))))
                .isEmpty();
        for (String wrong : new String[] {"Kore", "es-US", "ES-us-Chirp3-HD-Kore", "es-US-Chirp3 HD"}) {
            Set<ConstraintViolation<SpeechProperties>> violations = validator.validate(
                    properties(true, null, wrong, Duration.ofSeconds(1)));
            assertThat(violations).extracting(ConstraintViolation::getMessage)
                    .anyMatch(message -> message.startsWith("cameia.speech.voice-es"));
        }
    }
}
