package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;

/** Reglas de la respuesta definitiva: origen, idioma detectado y datos de voz. */
class AnswerAttemptTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneOffset.UTC);
    private static final UUID TURN = UUID.randomUUID();

    @Test
    @DisplayName("Una respuesta escrita no tiene datos de voz")
    void written_shouldHaveNoVoice() {
        AnswerAttempt attempt = AnswerAttempt.written(TURN, "texto", "pregunta", CLOCK);

        assertThat(attempt.voice()).isNull();
        assertThat(attempt.origin()).isEqualTo(AnswerOrigin.TEXTO);
    }

    @Test
    @DisplayName("Una respuesta escrita con datos de voz se rechaza")
    void textWithVoice_shouldBeRejected() {
        assertThatThrownBy(() -> new AnswerAttempt(UUID.randomUUID(), TURN, AnswerOrigin.TEXTO, "texto",
                "pregunta", null, new VoiceSample(3, 1000, 0, 0), CLOCK.instant()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("datos de voz");
    }

    @Test
    @DisplayName("Una respuesta por voz conserva sus datos de voz")
    void spoken_shouldKeepItsVoiceSample() {
        VoiceSample sample = new VoiceSample(65, 36310, 15734, 6);

        AnswerAttempt attempt = AnswerAttempt.spoken(TURN, "texto", "pregunta", " es ", sample, CLOCK);

        assertThat(attempt.voice()).isEqualTo(sample);
        assertThat(attempt.detectedLanguage()).isEqualTo("es");
        assertThat(attempt.origin()).isEqualTo(AnswerOrigin.AUDIO);
    }

    @Test
    @DisplayName("El idioma detectado fuera de 2 a 10 caracteres se descarta")
    void spoken_shouldDropAnUnusableLanguage() {
        assertThat(AnswerAttempt.spoken(TURN, "t", "p", "e", null, CLOCK).detectedLanguage()).isNull();
        assertThat(AnswerAttempt.spoken(TURN, "t", "p", "x".repeat(11), null, CLOCK).detectedLanguage()).isNull();
        assertThat(AnswerAttempt.spoken(TURN, "t", "p", "x".repeat(10), null, CLOCK).detectedLanguage())
                .hasSize(10);
    }

    @Test
    @DisplayName("El constructor sin datos de voz los deja nulos")
    void shortConstructor_shouldLeaveVoiceNull() {
        AnswerAttempt attempt = new AnswerAttempt(UUID.randomUUID(), TURN, AnswerOrigin.AUDIO, "t", "p", "es",
                CLOCK.instant());

        assertThat(attempt.voice()).isNull();
    }

    @Test
    @DisplayName("toString no imprime el texto de la respuesta ni de la pregunta")
    void toString_shouldNotPrintText() {
        AnswerAttempt attempt = AnswerAttempt.written(TURN, "secreto", "pregunta privada", CLOCK);

        assertThat(attempt.toString()).doesNotContain("secreto").doesNotContain("privada");
    }
}
