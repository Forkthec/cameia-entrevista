package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El resultado del método STAR de una pregunta conductual y su nivel de 0 a 4. */
class StarResultTest {

    @Test
    @DisplayName("Los cuatro componentes dan nivel 4")
    void allFour_shouldGiveLevel4() {
        assertThat(new StarResult(3, "p", true, true, true, true).level()).isEqualTo(4);
    }

    @Test
    @DisplayName("Sin resultado el nivel es 3")
    void withoutResult_shouldGiveLevel3AndResultFalse() {
        StarResult star = new StarResult(3, "p", true, true, true, false);

        assertThat(star.level()).isEqualTo(3);
        assertThat(star.result()).isFalse();
    }

    @Test
    @DisplayName("Ningún componente da nivel 0, no nulo")
    void none_shouldGiveLevel0NotNull() {
        assertThat(new StarResult(5, "p", false, false, false, false).level()).isEqualTo(0);
    }

    @Test
    @DisplayName("Sin resultado, los cuatro componentes y el nivel son nulos")
    void unavailable_shouldHaveAllNull() {
        StarResult star = StarResult.unavailable(3, "¿Pregunta?");

        assertThat(star.situation()).isNull();
        assertThat(star.task()).isNull();
        assertThat(star.action()).isNull();
        assertThat(star.result()).isNull();
        assertThat(star.level()).isNull();
        assertThat(star.questionNumber()).isEqualTo(3);
        assertThat(star.questionText()).isEqualTo("¿Pregunta?");
    }

    @Test
    @DisplayName("Una mezcla de componentes nulos y presentes se rechaza")
    void mixedNulls_shouldBeRejected() {
        assertThatThrownBy(() -> new StarResult(3, "p", true, null, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StarResult(3, "p", null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El número de pregunta debe ser de 1 a 5 y el texto no puede faltar")
    void invalidNumberOrText_shouldBeRejected() {
        assertThatThrownBy(() -> new StarResult(0, "p", true, true, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StarResult(6, "p", true, true, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StarResult(3, null, true, true, true, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toString no imprime el texto de la pregunta")
    void toString_shouldNotPrintText() {
        assertThat(StarResult.unavailable(3, "pregunta privada").toString()).doesNotContain("privada");
    }
}
