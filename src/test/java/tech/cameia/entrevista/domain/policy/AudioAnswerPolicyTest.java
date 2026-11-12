package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.exception.AudioFormatNotAllowedException;

/** Tipos de audio admitidos en la respuesta por voz. */
class AudioAnswerPolicyTest {

    private final AudioAnswerPolicy policy = new AudioAnswerPolicy();

    @Test
    void requireAllowed_shouldDropParametersAndLowercase() {
        assertThat(policy.requireAllowed("Audio/WebM;codecs=opus")).isEqualTo("audio/webm");
        assertThat(policy.requireAllowed("audio/mp4")).isEqualTo("audio/mp4");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "text/plain", "application/octet-stream", "image/png", "audio/flac2"})
    void requireAllowed_shouldReject_whenTypeIsMissingOrNotAudio(String declared) {
        assertThatThrownBy(() -> policy.requireAllowed(declared)).isInstanceOf(AudioFormatNotAllowedException.class);
    }
}
