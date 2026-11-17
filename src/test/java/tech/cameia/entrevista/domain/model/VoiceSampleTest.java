package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cálculo de los cuatro datos de voz con los tiempos de las palabras. */
class VoiceSampleTest {

    private static final InterviewLanguage ES = InterviewLanguage.SPANISH_COLOMBIA;

    @Test
    @DisplayName("Sin palabras no hay datos de voz")
    void noWords_shouldGiveNoSample() {
        assertThat(VoiceSample.from(List.of(), ES)).isEmpty();
        assertThat(VoiceSample.from(null, ES)).isEmpty();
    }

    @Test
    @DisplayName("Una palabra con tiempos conserva su tramo")
    void oneTimedWord_shouldKeepItsSpan() {
        assertThat(VoiceSample.from(List.of(new TimedWord("Sí", 100, 400)), ES))
                .contains(new VoiceSample(1, 300, 0, 0));
    }

    @Test
    @DisplayName("Un tramo de cero milisegundos no da datos de voz")
    void zeroSpan_shouldGiveNoSample() {
        assertThat(VoiceSample.from(List.of(new TimedWord("eh", 500, 500)), ES)).isEmpty();
    }

    @Test
    @DisplayName("Una pausa cuenta desde 500 ms exactos")
    void pauseThreshold_shouldCountFrom500() {
        List<TimedWord> words = List.of(new TimedWord("a", 0, 100), new TimedWord("b", 599, 700),
                new TimedWord("c", 1200, 1300), new TimedWord("d", 1801, 1900));

        assertThat(VoiceSample.from(words, ES)).contains(new VoiceSample(4, 1900, 1001, 0));
    }

    @Test
    @DisplayName("Las palabras solapadas no suman pausa")
    void overlappingWords_shouldNotAddPause() {
        List<TimedWord> words = List.of(new TimedWord("a", 0, 500), new TimedWord("b", 400, 900));

        assertThat(VoiceSample.from(words, ES)).contains(new VoiceSample(2, 900, 0, 0));
    }

    @Test
    @DisplayName("Las muletillas en español se cuentan, incluida la primera palabra")
    void spanishFillers_shouldBeCounted() {
        List<TimedWord> words = List.of(new TimedWord("Eh,", 0, 200), new TimedWord("soy", 700, 900),
                new TimedWord("muy", 950, 1100), new TimedWord("mm", 1700, 1800));

        assertThat(VoiceSample.from(words, ES)).contains(new VoiceSample(4, 1800, 1100, 2));
    }

    @Test
    @DisplayName("La lista de muletillas es la del idioma de la entrevista")
    void fillers_shouldFollowTheInterviewLanguage() {
        List<TimedWord> words = List.of(new TimedWord("Um,", 0, 200), new TimedWord("eh", 300, 400));

        assertThat(VoiceSample.from(words, InterviewLanguage.ENGLISH)).contains(new VoiceSample(2, 400, 0, 1));
        assertThat(VoiceSample.from(words, ES)).contains(new VoiceSample(2, 400, 0, 1));
    }

    @Test
    @DisplayName("Un tramo que no cabe en un entero no da datos de voz")
    void hugeSpan_shouldGiveNoSample() {
        assertThat(VoiceSample.from(List.of(new TimedWord("a", 0, 3_000_000_000L)), ES)).isEmpty();
    }

    @Test
    @DisplayName("El constructor rechaza valores fuera de rango")
    void constructor_shouldRejectOutOfRange() {
        assertThatThrownBy(() -> new VoiceSample(0, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoiceSample(1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoiceSample(1, 10, 11, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoiceSample(1, 10, 0, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoiceSample(1, 10, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoiceSample(1, 10, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
