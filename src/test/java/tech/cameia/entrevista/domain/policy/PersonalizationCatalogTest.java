package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.CatalogOption;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.Tone;

class PersonalizationCatalogTest {

    private final PersonalizationCatalog catalog = new PersonalizationCatalog();

    static Stream<Arguments> everyOption() {
        return Stream.of(
                Arguments.of(Modality.SIMULATION, true),
                Arguments.of(Modality.TRAINING, false),
                Arguments.of(Tone.PROFESSIONAL_NEUTRAL, true),
                Arguments.of(Tone.WARM, true),
                Arguments.of(Tone.STRICT, true),
                Arguments.of(Tone.MOTIVATING, true),
                Arguments.of(AnswerMode.AUDIO, true),
                Arguments.of(AnswerMode.TEXT, true),
                Arguments.of(InterviewLanguage.SPANISH_COLOMBIA, true),
                Arguments.of(InterviewLanguage.ENGLISH, true));
    }

    @Test
    @DisplayName("Simulación está disponible y Entreno no, en ese orden")
    void modes_shouldOfferSimulationAvailableAndTrainingUnavailable() {
        assertThat(catalog.modes()).containsExactly(
                new CatalogOption<>(Modality.SIMULATION, true),
                new CatalogOption<>(Modality.TRAINING, false));
    }

    @Test
    @DisplayName("Los cuatro tonos salen en orden y todos disponibles")
    void tones_shouldOfferFourTonesInOrder_whenRead() {
        assertThat(catalog.tones()).extracting(CatalogOption::value)
                .containsExactly(Tone.PROFESSIONAL_NEUTRAL, Tone.WARM, Tone.STRICT, Tone.MOTIVATING);
        assertThat(catalog.tones()).extracting(CatalogOption::available).containsOnly(true);
    }

    @Test
    @DisplayName("Audio y Texto salen disponibles, en ese orden")
    void answerFormats_shouldOfferAudioAndTextAvailable_whenRead() {
        assertThat(catalog.answerFormats()).containsExactly(
                new CatalogOption<>(AnswerMode.AUDIO, true),
                new CatalogOption<>(AnswerMode.TEXT, true));
    }

    @Test
    @DisplayName("Español de Colombia e inglés salen disponibles, en ese orden")
    void languages_shouldOfferSpanishColombiaAndEnglishAvailable_whenRead() {
        assertThat(catalog.languages()).containsExactly(
                new CatalogOption<>(InterviewLanguage.SPANISH_COLOMBIA, true),
                new CatalogOption<>(InterviewLanguage.ENGLISH, true));
    }

    @Test
    @DisplayName("Cada valor de cada enumerado aparece en su lista")
    void catalog_shouldListEveryEnumValue_whenRead() {
        assertThat(catalog.modes()).extracting(CatalogOption::value).containsExactly(Modality.values());
        assertThat(catalog.tones()).extracting(CatalogOption::value).containsExactly(Tone.values());
        assertThat(catalog.answerFormats()).extracting(CatalogOption::value).containsExactly(AnswerMode.values());
        assertThat(catalog.languages()).extracting(CatalogOption::value).containsExactly(InterviewLanguage.values());
    }

    @ParameterizedTest(name = "{0} disponible = {1}")
    @MethodSource("everyOption")
    @DisplayName("Solo Entreno no está disponible")
    void isAvailable_shouldBeFalseOnlyForTraining(Enum<?> option, boolean expected) {
        assertThat(catalog.isAvailable(option)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Consultar la disponibilidad de un valor nulo lanza NullPointerException")
    void isAvailable_shouldThrow_whenNull() {
        assertThatThrownBy(() -> catalog.isAvailable(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Consultar la disponibilidad de un enumerado que no es del catálogo lanza IllegalArgumentException")
    void isAvailable_shouldThrow_whenEnumIsNotACatalogOne() {
        assertThatThrownBy(() -> catalog.isAvailable(TimeUnit.SECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TimeUnit");
    }

    @Test
    @DisplayName("Las listas del catálogo no se pueden modificar")
    void catalog_shouldReturnUnmodifiableLists_whenRead() {
        List<CatalogOption<Tone>> tones = catalog.tones();
        CatalogOption<Tone> extra = new CatalogOption<>(Tone.WARM, true);

        assertThatThrownBy(() -> tones.add(extra)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.modes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.answerFormats().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.languages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Una opción sin valor lanza NullPointerException")
    void catalogOption_shouldThrow_whenValueNull() {
        assertThatThrownBy(() -> new CatalogOption<Tone>(null, true)).isInstanceOf(NullPointerException.class);
    }
}
