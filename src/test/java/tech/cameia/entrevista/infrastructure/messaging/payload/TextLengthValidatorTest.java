package tech.cameia.entrevista.infrastructure.messaging.payload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class TextLengthValidatorTest {

    private record Required(@TextLength(min = 1, max = 255) String value) {
    }

    private record Optional(@TextLength(max = 3) String value) {
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String value) {
        return validator.validate(new Required(value)).isEmpty();
    }

    @Test
    @DisplayName("Un valor nulo es válido: su presencia la exige @NotNull")
    void isValid_shouldBeTrue_whenNull() {
        assertThat(valid(null)).isTrue();
    }

    @Test
    @DisplayName("Con mínimo 1 el texto vacío y el de solo espacios son inválidos")
    void isValid_shouldBeFalse_whenEmptyOrBlankWithMinOne() {
        assertThat(valid("")).isFalse();
        assertThat(valid("   ")).isFalse();
        assertThat(valid("\t\n")).isFalse();
    }

    @Test
    @DisplayName("255 emojis son 255 puntos de código y se aceptan")
    void isValid_shouldBeTrue_whenEmojisAtLimit() {
        assertThat(valid("😀".repeat(255))).isTrue();
        assertThat(valid("😀".repeat(256))).isFalse();
    }

    @Test
    @DisplayName("256 caracteres 'é' superan el máximo")
    void isValid_shouldBeFalse_whenOverLimit() {
        assertThat(valid("é".repeat(255))).isTrue();
        assertThat(valid("é".repeat(256))).isFalse();
    }

    @Test
    @DisplayName("Una 'é' descompuesta (e + acento) cuenta 2 puntos de código")
    void isValid_shouldCountCombiningMarks() {
        assertThat(validator.validate(new Optional("é")).isEmpty()).isTrue();
        assertThat(validator.validate(new Optional("éé")).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("El carácter nulo se rechaza porque PostgreSQL no puede guardarlo")
    void isValid_shouldBeFalse_whenNulCharacter() {
        assertThat(valid("a\u0000b")).isFalse();
    }

    @Test
    @DisplayName("Sin mínimo, el texto vacío es válido")
    void isValid_shouldAcceptEmpty_whenMinZero() {
        assertThat(validator.validate(new Optional("")).isEmpty()).isTrue();
    }
}
