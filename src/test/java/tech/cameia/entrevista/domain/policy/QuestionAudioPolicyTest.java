package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.exception.QuestionNotAvailableException;
import tech.cameia.entrevista.domain.exception.QuestionNotFoundException;
import tech.cameia.entrevista.domain.exception.SpeechTextEmptyException;
import tech.cameia.entrevista.domain.exception.SpeechTextTooLongException;

/** Número de pregunta, preguntas mostradas y texto que se envía a la voz (HU-5.15). */
class QuestionAudioPolicyTest {

    private final QuestionAudioPolicy policy = new QuestionAudioPolicy(3);

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "3", "4", "5"})
    @DisplayName("CA-5.15.17: los números 1 a 5 son válidos")
    void parseNumber_shouldAcceptOneToFive(String raw) {
        assertThat(policy.parseNumber(raw)).isEqualTo(Integer.parseInt(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "6", "-1", "a", "", " 2", "2 ", "02", "+2", "2.0", "2e0", "٢", "２",
        "99999999999999999999", "\u0000", "1\u0000"})
    @DisplayName("CA-5.15.12: todo lo que no es un dígito de 1 a 5 responde QUESTION_NOT_FOUND")
    void parseNumber_shouldReject_whenNotADigitFromOneToFive(String raw) {
        assertThatThrownBy(() -> policy.parseNumber(raw))
                .isInstanceOf(QuestionNotFoundException.class)
                .hasMessage("No encontramos lo que buscabas.");
    }

    @Test
    void parseNumber_shouldReject_whenNull() {
        assertThatThrownBy(() -> policy.parseNumber(null)).isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    @DisplayName("CA-5.15.5: la pendiente y las anteriores se pueden escuchar; las futuras no")
    void requireShown_shouldRejectFutureQuestions() {
        policy.requireShown(3, 3);
        policy.requireShown(1, 3);

        assertThatThrownBy(() -> policy.requireShown(4, 3))
                .isInstanceOf(QuestionNotAvailableException.class)
                .hasMessage("Esa pregunta todavía no está disponible.");
    }

    @Test
    @DisplayName("CA-5.15.13: 499 y 500 puntos de código se envían; 501 no")
    void requireSpeakable_shouldAcceptUpTo500CodePoints() {
        assertThat(policy.requireSpeakable("a".repeat(499))).hasSize(499);
        assertThat(policy.requireSpeakable("a".repeat(500))).hasSize(500);

        assertThatThrownBy(() -> policy.requireSpeakable("a".repeat(501)))
                .isInstanceOf(SpeechTextTooLongException.class)
                .hasMessage("No pudimos reproducir esta pregunta. Puedes leerla en pantalla.");
    }

    @Test
    @DisplayName("CA-5.15.19: 500 emojis de 4 bytes se cuentan como 500 y el texto no se altera")
    void requireSpeakable_shouldCountCodePointsAndKeepText() {
        String emojis = "😀".repeat(500);

        assertThat(policy.requireSpeakable(emojis)).isSameAs(emojis);
        assertThat(emojis.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(2000);
        assertThatThrownBy(() -> policy.requireSpeakable(emojis + "😀")).isInstanceOf(SpeechTextTooLongException.class);
    }

    @Test
    @DisplayName("CA-5.15.19: el texto en NFD se envía sin normalizar")
    void requireSpeakable_shouldNotNormalize() {
        String nfd = Normalizer.normalize("¿Cómo diseñarías la canción? https://x.co «API» ñ", Normalizer.Form.NFD);

        assertThat(policy.requireSpeakable(nfd)).isSameAs(nfd);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t "})
    @DisplayName("CA-5.15.20: un texto vacío o en blanco no se envía")
    void requireSpeakable_shouldRejectBlank(String blank) {
        assertThatThrownBy(() -> policy.requireSpeakable(blank))
                .isInstanceOf(SpeechTextEmptyException.class)
                .hasMessage("No pudimos reproducir esta pregunta. Puedes leerla en pantalla.");
    }

    @Test
    void requireSpeakable_shouldRejectNull() {
        assertThatThrownBy(() -> policy.requireSpeakable(null)).isInstanceOf(SpeechTextEmptyException.class);
    }

    @Test
    @DisplayName("Los usos restantes quedan entre 0 y el máximo")
    void remaining_shouldStayWithinBounds() {
        assertThat(policy.remaining(0)).isEqualTo(3);
        assertThat(policy.remaining(2)).isEqualTo(1);
        assertThat(policy.remaining(3)).isZero();
        assertThat(policy.remaining(7)).isZero();
        assertThat(policy.remaining(-1)).isEqualTo(3);
        assertThat(policy.maxPlays()).isEqualTo(3);
    }

    @Test
    void constructor_shouldRejectZeroPlays() {
        assertThatThrownBy(() -> new QuestionAudioPolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
