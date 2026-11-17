package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Promedio de las notas de contenido y validación de cada nota. */
class ContentScoresTest {

    private static List<ContentGrade> grades(int... precisions) {
        return java.util.stream.IntStream.range(0, precisions.length)
                .mapToObj(i -> new ContentGrade(i + 1, precisions[i], precisions[i], precisions[i])).toList();
    }

    @ParameterizedTest(name = "{0} da {1}")
    @DisplayName("El promedio se redondea a 2 decimales, mitad hacia arriba")
    @CsvSource({"'5,1,3', 3.00", "'5,2,3', 3.33", "'4,5', 4.50", "'1', 1.00", "'2,3,3', 2.67"})
    void average_shouldRoundToTwoDecimalsHalfUp(String values, String expected) {
        int[] precisions = java.util.Arrays.stream(values.split(",")).mapToInt(Integer::parseInt).toArray();

        ContentScores scores = ContentScores.average(grades(precisions));

        assertThat(scores.precision()).isEqualTo(new BigDecimal(expected));
        assertThat(scores.charisma()).isEqualTo(new BigDecimal(expected));
        assertThat(scores.language()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("Cada métrica se promedia por separado")
    void average_shouldAverageEachMetricOnItsOwn() {
        ContentScores scores = ContentScores.average(List.of(new ContentGrade(1, 5, 1, 3),
                new ContentGrade(2, 4, 2, 3)));

        assertThat(scores.precision()).isEqualTo(new BigDecimal("4.50"));
        assertThat(scores.charisma()).isEqualTo(new BigDecimal("1.50"));
        assertThat(scores.language()).isEqualTo(new BigDecimal("3.00"));
    }

    @Test
    @DisplayName("Sin notas las tres métricas quedan sin resultado")
    void average_shouldGiveNoneWithoutGrades() {
        assertThat(ContentScores.average(List.of())).isEqualTo(ContentScores.NONE);
        assertThat(ContentScores.NONE.precision()).isNull();
    }

    @Test
    @DisplayName("Las tres métricas nulas o las tres presentes; nunca una mezcla")
    void mixedNulls_shouldBeRejected() {
        assertThatThrownBy(() -> new ContentScores(BigDecimal.ONE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "nota {0} es inválida")
    @DisplayName("Una nota fuera de 1 a 5 se rechaza")
    @CsvSource({"0", "6", "-1", "2147483647"})
    void grade_shouldRejectOutOfRange(int value) {
        assertThatThrownBy(() -> new ContentGrade(1, value, 3, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContentGrade(1, 3, value, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContentGrade(1, 3, 3, value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Los límites 1 y 5 se aceptan y el número de pregunta debe ser de 1 a 5")
    void grade_shouldAcceptTheLimits() {
        assertThat(new ContentGrade(1, 1, 5, 1).charisma()).isEqualTo(5);
        assertThatThrownBy(() -> new ContentGrade(0, 3, 3, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContentGrade(6, 3, 3, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
