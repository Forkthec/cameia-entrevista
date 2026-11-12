package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.exception.AudioInvalidFormatException;
import tech.cameia.entrevista.domain.exception.AudioTooLargeException;
import tech.cameia.entrevista.domain.exception.AudioTooLongException;
import tech.cameia.entrevista.domain.exception.AudioTooShortException;
import tech.cameia.entrevista.domain.model.AudioFormat;
import tech.cameia.entrevista.domain.model.AudioInspection;

class AudioAnswerPolicyTest {

    private final AudioAnswerPolicy policy = new AudioAnswerPolicy();

    private static Optional<AudioInspection> lasting(Duration duration) {
        return Optional.of(new AudioInspection(AudioFormat.WEBM_OPUS, duration));
    }

    @Test
    @DisplayName("CA-5.8.12: un audio de exactamente 10 485 760 bytes se acepta")
    void checkSize_shouldAcceptExactlyTenMebibytes() {
        policy.checkSize(10_485_760L);
    }

    @Test
    @DisplayName("CA-5.8.13: un audio de 10 485 761 bytes se rechaza con el mensaje de 10 MB")
    void checkSize_shouldRejectOneByteOverTenMebibytes() {
        assertThatThrownBy(() -> policy.checkSize(10_485_761L)).isInstanceOf(AudioTooLargeException.class)
                .hasMessage("Tu audio no puede superar los 10 MB.");
    }

    @Test
    @DisplayName("CA-5.8.7: 3:00 y hasta 181 s se aceptan")
    void checkContent_shouldAcceptThreeMinutes() {
        assertThat(policy.checkContent(lasting(Duration.ofSeconds(180))).duration()).hasSeconds(180);
        assertThat(policy.checkContent(lasting(Duration.ofSeconds(181))).duration()).hasSeconds(181);
    }

    @Test
    @DisplayName("CA-5.8.8: 3:02 se rechaza con AUDIO_TOO_LONG y su mensaje")
    void checkContent_shouldRejectThreeMinutesAndTwoSeconds() {
        assertThatThrownBy(() -> policy.checkContent(lasting(Duration.ofSeconds(182))))
                .isInstanceOf(AudioTooLongException.class)
                .hasMessage("Tu respuesta no puede durar más de 3 minutos.");
        assertThatThrownBy(() -> policy.checkContent(lasting(Duration.ofMillis(181_001))))
                .isInstanceOf(AudioTooLongException.class);
    }

    @Test
    @DisplayName("Menos de 1,5 s se rechaza sin pedir la transcripción; 1,5 s se acepta")
    void checkContent_shouldRejectTooShort() {
        assertThatThrownBy(() -> policy.checkContent(lasting(Duration.ofMillis(1499))))
                .isInstanceOf(AudioTooShortException.class).hasMessage("No detectamos tu voz, intenta de nuevo.");
        assertThat(policy.checkContent(lasting(Duration.ofMillis(1500)))).isNotNull();
    }

    @Test
    @DisplayName("CA-5.8.14: un contenido no reconocido es formato no admitido")
    void checkContent_shouldRejectUnknownFormat() {
        assertThatThrownBy(() -> policy.checkContent(Optional.empty()))
                .isInstanceOf(AudioInvalidFormatException.class)
                .hasMessage("Este formato de audio no es compatible. Graba de nuevo o responde por texto.");
    }
}
