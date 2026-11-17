package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Las tres métricas de voz se calculan sumando los datos de todas las respuestas por voz de la sesión. */
class VoiceMetricsTest {

    @Test
    @DisplayName("El ejemplo del criterio da 120 palabras por minuto, 12 % de pausas y 2 de vacilación")
    void bookExample_shouldGive120WordsPerMinute12PercentAnd2Hesitation() {
        VoiceMetrics metrics = VoiceMetrics.of(List.of(new VoiceSample(120, 60000, 6000, 2),
                new VoiceSample(180, 90000, 12000, 4)));

        assertThat(metrics.speechRate()).isEqualTo(120);
        assertThat(metrics.pauseTime()).isEqualByComparingTo(new BigDecimal("12.0"));
        assertThat(metrics.hesitationRate()).isEqualByComparingTo(new BigDecimal("2.0"));
    }

    @Test
    @DisplayName("La sesión medida en español da lo medido con los audios reales")
    void measuredSpanishSession_shouldMatchTheMeasurement() {
        VoiceMetrics metrics = VoiceMetrics.of(List.of(new VoiceSample(47, 18823, 1060, 0),
                new VoiceSample(56, 30473, 8623, 4), new VoiceSample(65, 36310, 15734, 6),
                new VoiceSample(7, 2108, 0, 1), new VoiceSample(52, 21483, 3106, 6)));

        assertThat(metrics.speechRate()).isEqualTo(125);
        assertThat(metrics.pauseTime()).isEqualByComparingTo(new BigDecimal("26.1"));
        assertThat(metrics.hesitationRate()).isEqualByComparingTo(new BigDecimal("7.5"));
    }

    @Test
    @DisplayName("La sesión medida en inglés da lo medido con los audios reales")
    void measuredEnglishSession_shouldMatchTheMeasurement() {
        VoiceMetrics metrics = VoiceMetrics.of(List.of(new VoiceSample(36, 17028, 3961, 0),
                new VoiceSample(53, 25329, 4941, 3), new VoiceSample(57, 35325, 13747, 3),
                new VoiceSample(7, 3155, 618, 1), new VoiceSample(49, 24597, 7162, 2)));

        assertThat(metrics.speechRate()).isEqualTo(115);
        assertThat(metrics.pauseTime()).isEqualByComparingTo(new BigDecimal("28.9"));
        assertThat(metrics.hesitationRate()).isEqualByComparingTo(new BigDecimal("4.5"));
    }

    @Test
    @DisplayName("Un solo audio medido: 107 palabras por minuto, 43,3 % de pausas y 9,2 de vacilación")
    void measuredAudio03_shouldMatch() {
        VoiceMetrics metrics = VoiceMetrics.of(List.of(new VoiceSample(65, 36310, 15734, 6)));

        assertThat(metrics.speechRate()).isEqualTo(107);
        assertThat(metrics.pauseTime()).isEqualByComparingTo(new BigDecimal("43.3"));
        assertThat(metrics.hesitationRate()).isEqualByComparingTo(new BigDecimal("9.2"));
    }

    @Test
    @DisplayName("El redondeo es mitad hacia arriba: 14,285 pasa a 14,3 y 199,43 a 199")
    void measuredAudio04_shouldRoundHalfUp() {
        VoiceMetrics metrics = VoiceMetrics.of(List.of(new VoiceSample(7, 2108, 0, 1)));

        assertThat(metrics.speechRate()).isEqualTo(199);
        assertThat(metrics.pauseTime()).isEqualByComparingTo(new BigDecimal("0.0"));
        assertThat(metrics.hesitationRate()).isEqualByComparingTo(new BigDecimal("14.3"));
    }

    @Test
    @DisplayName("Sin respuestas por voz las tres métricas son nulas, no cero")
    void noVoiceAnswers_shouldGiveNullMetrics() {
        VoiceMetrics metrics = VoiceMetrics.of(List.of());

        assertThat(metrics.speechRate()).isNull();
        assertThat(metrics.pauseTime()).isNull();
        assertThat(metrics.hesitationRate()).isNull();
        assertThat(metrics).isEqualTo(VoiceMetrics.NONE);
    }

    @Test
    @DisplayName("Una muestra sin pausas ni muletillas da 0 calculado, no nulo")
    void answersWithoutVoiceData_shouldNotCount() {
        VoiceMetrics metrics = VoiceMetrics.of(List.of(new VoiceSample(10, 6000, 0, 0)));

        assertThat(metrics.speechRate()).isEqualTo(100);
        assertThat(metrics.pauseTime()).isNotNull().isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.hesitationRate()).isNotNull().isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Un ritmo que no cabe en un entero se limita al mayor entero en vez de fallar")
    void impossibleRate_shouldBeCappedInsteadOfFailing() {
        VoiceMetrics metrics = VoiceMetrics.of(List.of(new VoiceSample(Integer.MAX_VALUE, 1, 0, 0)));

        assertThat(metrics.speechRate()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Los tres valores nulos o los tres presentes; nunca una mezcla")
    void mixedNulls_shouldBeRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new VoiceMetrics(100, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "ritmo={0}, pausas={1}, vacilación={2}")
    @DisplayName("Toda mezcla de valores con resultado y sin él se rechaza")
    @CsvSource(value = {"1,NULL,NULL", "NULL,1,NULL", "NULL,NULL,1", "1,1,NULL", "1,NULL,1", "NULL,1,1"},
            nullValues = "NULL")
    void everyMixture_shouldBeRejected(Integer rate, BigDecimal pauses, BigDecimal hesitation) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new VoiceMetrics(rate, pauses, hesitation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Con las sumas más grandes posibles el cálculo no se desborda")
    void hugeSums_shouldNotOverflow() {
        VoiceSample largest = new VoiceSample(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

        VoiceMetrics metrics = VoiceMetrics.of(List.of(largest, largest, largest));

        assertThat(metrics.speechRate()).isEqualTo(60000);
        assertThat(metrics.pauseTime()).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(metrics.hesitationRate()).isEqualByComparingTo(new BigDecimal("100.0"));
    }
}
