package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Prueba las reglas de la respuesta del modelo: texto y modelo con contenido y tokens no
 * negativos.
 */
class LanguageModelResponseTest {

    @ParameterizedTest(name = "texto [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("Un texto nulo, vacío o en blanco se rechaza al construir la respuesta")
    void constructor_shouldRejectResponse_whenTextIsBlank(String text) {
        assertThatThrownBy(() -> new LanguageModelResponse(text, 5, 3, "modelo-de-prueba"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El texto de la respuesta no puede estar vacío.");
    }

    @ParameterizedTest(name = "modelo [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("Un modelo nulo, vacío o en blanco se rechaza al construir la respuesta")
    void constructor_shouldRejectResponse_whenModelIsBlank(String model) {
        assertThatThrownBy(() -> new LanguageModelResponse("OK", 5, 3, model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El modelo de la respuesta no puede estar vacío.");
    }

    @Test
    @DisplayName("Un número negativo de tokens de entrada se rechaza")
    void constructor_shouldRejectResponse_whenInputTokensAreNegative() {
        assertThatThrownBy(() -> new LanguageModelResponse("OK", -1, 3, "modelo-de-prueba"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los tokens de la respuesta no pueden ser negativos.");
    }

    @Test
    @DisplayName("Un número negativo de tokens de salida se rechaza")
    void constructor_shouldRejectResponse_whenOutputTokensAreNegative() {
        assertThatThrownBy(() -> new LanguageModelResponse("OK", 5, -1, "modelo-de-prueba"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los tokens de la respuesta no pueden ser negativos.");
    }

    @ParameterizedTest(name = "tokens {0}")
    @ValueSource(ints = {0, 5})
    @DisplayName("Cero tokens, cuando el proveedor no los informa, o un número positivo construyen la respuesta")
    void constructor_shouldBuildResponse_whenTokensAreNotNegative(int tokens) {
        LanguageModelResponse response = new LanguageModelResponse("OK", tokens, tokens, "modelo-de-prueba");

        assertThat(response.text()).isEqualTo("OK");
        assertThat(response.inputTokens()).isEqualTo(tokens);
        assertThat(response.outputTokens()).isEqualTo(tokens);
        assertThat(response.model()).isEqualTo("modelo-de-prueba");
    }

    @Test
    @DisplayName("La representación de texto no incluye el texto de la respuesta")
    void toString_shouldHideText_whenResponseIsPrinted() {
        LanguageModelResponse response = new LanguageModelResponse("Tu respuesta fue buena", 5, 3,
                "modelo-de-prueba");

        assertThat(response.toString())
                .doesNotContain("Tu respuesta fue buena")
                .contains("22 caracteres")
                .contains("inputTokens=5")
                .contains("outputTokens=3")
                .contains("model=modelo-de-prueba");
    }
}
