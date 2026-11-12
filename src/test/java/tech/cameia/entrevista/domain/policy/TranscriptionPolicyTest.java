package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.TranscriptionEmptyException;
import tech.cameia.entrevista.domain.exception.TranscriptionLanguageMismatchException;
import tech.cameia.entrevista.domain.exception.TranscriptionTooLongException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Transcription;

/** Reglas de la transcripción de HU-5.12: vacía, idioma distinto y largo (CA-5.12.7, 5.12.9, 5.12.10, 5.12.11). */
class TranscriptionPolicyTest {

    private final TranscriptionPolicy policy = new TranscriptionPolicy(0.8);

    @Test
    void accept_shouldReturnStrippedText_whenSpanishInterviewAndSpanishAudio() {
        String text = policy.accept(new Transcription("  Trabajé tres años con Java.  ", "es", 0.97),
                InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(text).isEqualTo("Trabajé tres años con Java.");
    }

    @Test
    void accept_shouldKeepTechnicalTermsUntranslated_whenAudioMixesLanguages() {
        // CA-5.12.2: el idioma dominante es el de la entrevista; los términos técnicos van tal cual.
        String text = policy.accept(new Transcription("Usé Spring Boot y un message broker.", "es", 0.91),
                InterviewLanguage.SPANISH_COLOMBIA);

        assertThat(text).isEqualTo("Usé Spring Boot y un message broker.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   \n\t "})
    void accept_shouldRejectAsEmpty_whenTextIsBlank(String blank) {
        assertThatThrownBy(() -> policy.accept(new Transcription(blank, "es", 0.99),
                InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionEmptyException.class)
                .hasMessage("No detectamos tu voz, intenta de nuevo.");
    }

    @Test
    void accept_shouldRejectAsEmpty_whenTextIsNull() {
        assertThatThrownBy(() -> policy.accept(new Transcription(null, null, null), InterviewLanguage.ENGLISH))
                .isInstanceOf(TranscriptionEmptyException.class);
    }

    @Test
    void accept_shouldAcceptExactly4000Characters() {
        String text = "a".repeat(4000);

        assertThat(policy.accept(new Transcription(text, "en", 0.99), InterviewLanguage.ENGLISH)).hasSize(4000);
    }

    @Test
    void accept_shouldReject4001Characters() {
        assertThatThrownBy(() -> policy.accept(new Transcription("a".repeat(4001), "en", 0.99),
                InterviewLanguage.ENGLISH))
                .isInstanceOf(TranscriptionTooLongException.class)
                .hasMessage("Tu respuesta es demasiado larga. Graba una más corta o responde por texto.");
    }

    @Test
    void accept_shouldCountCodePointsAfterNfc_whenTextHasCombiningMarks() {
        // "e" + acento combinado (2 unidades) se normaliza a "é" (1 punto de código): 4000 en total, se acepta.
        String combined = "é".repeat(4000);

        assertThat(policy.accept(new Transcription(combined, "es", 0.99), InterviewLanguage.SPANISH_COLOMBIA))
                .hasSize(4000);
    }

    @Test
    void accept_shouldCountEmojisAsOneCharacter() {
        String emojis = "😀".repeat(4000);

        assertThat(policy.accept(new Transcription(emojis, "en", 0.99), InterviewLanguage.ENGLISH))
                .isEqualTo(emojis);
    }

    @Test
    void accept_shouldRejectWithEnglishMessage_whenEnglishInterviewHearsSpanish() {
        assertThatThrownBy(() -> policy.accept(new Transcription("Hola, soy Ana.", "es", 0.95),
                InterviewLanguage.ENGLISH))
                .isInstanceOf(TranscriptionLanguageMismatchException.class)
                .hasMessage("Tu entrevista es en inglés. Responde en inglés.")
                .extracting(error -> ((TranscriptionLanguageMismatchException) error).getErrorCode())
                .isEqualTo(ErrorCode.TRANSCRIPTION_LANGUAGE_MISMATCH);
    }

    @Test
    void accept_shouldRejectWithSpanishMessage_whenSpanishInterviewHearsEnglish() {
        assertThatThrownBy(() -> policy.accept(new Transcription("Hi, I am Ana.", "en", 0.8),
                InterviewLanguage.SPANISH_COLOMBIA))
                .isInstanceOf(TranscriptionLanguageMismatchException.class)
                .hasMessage("Tu entrevista es en español. Responde en español.");
    }

    @Test
    void accept_shouldAccept_whenOtherLanguageConfidenceIsBelowThreshold() {
        assertThat(policy.accept(new Transcription("Hi, soy Ana.", "en", 0.79), InterviewLanguage.SPANISH_COLOMBIA))
                .isEqualTo("Hi, soy Ana.");
    }

    @Test
    void accept_shouldAccept_whenProviderDoesNotReportLanguageOrConfidence() {
        assertThat(policy.accept(new Transcription("Hello", null, 0.99), InterviewLanguage.SPANISH_COLOMBIA))
                .isEqualTo("Hello");
        assertThat(policy.accept(new Transcription("Hello", "en", null), InterviewLanguage.SPANISH_COLOMBIA))
                .isEqualTo("Hello");
    }

    @Test
    void accept_shouldMatchRegionalVariants_whenDetectedCodeHasRegion() {
        assertThat(policy.accept(new Transcription("Hello", "en_us", 0.99), InterviewLanguage.ENGLISH))
                .isEqualTo("Hello");
        assertThat(policy.accept(new Transcription("Hola", "ES", 0.99), InterviewLanguage.SPANISH_COLOMBIA))
                .isEqualTo("Hola");
    }

    @Test
    void accept_shouldReportLanguageBeforeLength_whenBothRulesFail() {
        assertThatThrownBy(() -> policy.accept(new Transcription("a".repeat(4001), "es", 0.99),
                InterviewLanguage.ENGLISH))
                .isInstanceOf(TranscriptionLanguageMismatchException.class);
    }

    @Test
    void constructor_shouldRejectThresholdOutOfRange() {
        assertThatThrownBy(() -> new TranscriptionPolicy(1.01)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TranscriptionPolicy(-0.01)).isInstanceOf(IllegalArgumentException.class);
    }
}
