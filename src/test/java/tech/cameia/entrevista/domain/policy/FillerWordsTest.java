package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tech.cameia.entrevista.domain.model.InterviewLanguage;

/** Reconocimiento de muletillas por idioma, sin distinguir mayúsculas, tildes ni puntuación. */
class FillerWordsTest {

    @ParameterizedTest(name = "[{1}] \"{0}\" -> {2}")
    @CsvSource({
            "'Eh,', SPANISH_COLOMBIA, true",
            "'ÉH', SPANISH_COLOMBIA, true",
            "'mmm.', SPANISH_COLOMBIA, true",
            "'ahí', SPANISH_COLOMBIA, false",
            "'um', SPANISH_COLOMBIA, false",
            "'Um,', ENGLISH, true",
            "'Uh,', ENGLISH, true",
            "'Mm-hmm.', ENGLISH, false",
            "'mm', ENGLISH, true",
            "'uhm', ENGLISH, false",
            "'', ENGLISH, false"})
    @DisplayName("Una palabra es muletilla solo si, normalizada, está en la lista de su idioma")
    void isFiller_shouldMatchTheLanguageList(String word, InterviewLanguage language, boolean expected) {
        assertThat(FillerWords.isFiller(word, language)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Una palabra nula no es muletilla")
    void isFiller_shouldRejectNull() {
        assertThat(FillerWords.isFiller(null, InterviewLanguage.ENGLISH)).isFalse();
    }

    @Test
    @DisplayName("La normalización quita tildes, puntuación y mayúsculas, y conserva dígitos")
    void normalize_shouldStripMarksAndPunctuation() {
        assertThat(FillerWords.normalize("¿Éh?!")).isEqualTo("eh");
        assertThat(FillerWords.normalize("a1-b")).isEqualTo("a1b");
        assertThat(FillerWords.normalize("...")).isEmpty();
    }
}
