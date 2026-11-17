package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La transcripción y la palabra con tiempos nunca imprimen texto de la persona. */
class TranscriptionTest {

    @Test
    @DisplayName("El texto de las palabras nunca aparece en toString; solo su cantidad")
    void toString_shouldNotPrintWords() {
        Transcription transcription = new Transcription("secreto", "es", 0.9, Duration.ofSeconds(1),
                List.of(new TimedWord("secreto", 0, 100)));

        assertThat(transcription.toString()).doesNotContain("secreto").contains("words=1");
        assertThat(new TimedWord("secreto", 0, 100).toString()).doesNotContain("secreto");
    }

    @Test
    @DisplayName("Las palabras ausentes quedan como lista vacía")
    void nullWords_shouldBecomeEmpty() {
        assertThat(new Transcription("a", "es", 0.9, null, null).words()).isEmpty();
        assertThat(new Transcription("a", "es", 0.9, null).words()).isEmpty();
        assertThat(new Transcription("a", "es", 0.9).words()).isEmpty();
    }

    @Test
    @DisplayName("La lista de palabras es una copia inmutable")
    void words_shouldBeDefensivelyCopied() {
        List<TimedWord> source = new ArrayList<>(List.of(new TimedWord("a", 0, 1)));
        Transcription transcription = new Transcription("a", "es", 0.9, null, source);
        source.clear();

        assertThat(transcription.words()).hasSize(1);
        assertThatThrownBy(() -> transcription.words().add(new TimedWord("b", 1, 2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Una palabra con tiempos inválidos se rechaza")
    void timedWord_shouldRejectInvalidTimes() {
        assertThatThrownBy(() -> new TimedWord("a", -1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimedWord("a", 10, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimedWord(null, 0, 5)).isInstanceOf(NullPointerException.class);
    }
}
