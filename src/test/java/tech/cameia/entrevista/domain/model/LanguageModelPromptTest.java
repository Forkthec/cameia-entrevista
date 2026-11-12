package tech.cameia.entrevista.domain.model;

import tech.cameia.entrevista.domain.model.AiOperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Prueba las reglas del prompt: un texto con contenido y un tope de tokens de al menos 1.
 */
class LanguageModelPromptTest {

    @ParameterizedTest(name = "texto [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("Un texto nulo, vacío o en blanco se rechaza al construir el prompt")
    void constructor_shouldRejectPrompt_whenTextIsBlank(String text) {
        assertThatThrownBy(() -> new LanguageModelPrompt(text, 16, AiOperation.PLAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El texto del prompt no puede estar vacío.");
    }

    @ParameterizedTest(name = "tope {0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("Un tope de tokens menor que 1 se rechaza al construir el prompt")
    void constructor_shouldRejectPrompt_whenMaxOutputTokensIsBelowOne(int tope) {
        assertThatThrownBy(() -> new LanguageModelPrompt("Responde OK.", tope, AiOperation.PLAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El tope de tokens de salida debe ser al menos 1.");
    }

    @Test
    @DisplayName("El tope mínimo válido, 1, construye el prompt")
    void constructor_shouldBuildPrompt_whenMaxOutputTokensIsOne() {
        LanguageModelPrompt prompt = new LanguageModelPrompt("Responde OK.", 1, AiOperation.PLAN);

        assertThat(prompt.text()).isEqualTo("Responde OK.");
        assertThat(prompt.maxOutputTokens()).isEqualTo(1);
    }

    @Test
    @DisplayName("La representación de texto no incluye el texto del prompt")
    void toString_shouldHideText_whenPromptIsPrinted() {
        LanguageModelPrompt prompt = new LanguageModelPrompt("Mi vacante es secreta", 16, AiOperation.PLAN);

        assertThat(prompt.toString())
                .doesNotContain("Mi vacante es secreta")
                .contains("21 caracteres")
                .contains("maxOutputTokens=16");
    }

    @Test
    @DisplayName("El constructor de dos argumentos deja el esquema en nulo")
    void constructor_shouldLeaveSchemaNull_whenTwoArguments() {
        assertThat(new LanguageModelPrompt("Responde OK.", 16, AiOperation.PLAN).jsonSchema()).isNull();
    }

    @Test
    @DisplayName("Un esquema en blanco se rechaza y uno con contenido se conserva sin mostrarse en toString")
    void constructor_shouldValidateSchema() {
        assertThatThrownBy(() -> new LanguageModelPrompt("Responde OK.", 16, "  ", AiOperation.EVALUATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El esquema de la respuesta no puede estar en blanco.");
        LanguageModelPrompt prompt = new LanguageModelPrompt("Responde OK.", 16, "{\"type\":\"object\"}", AiOperation.EVALUATE);

        assertThat(prompt.jsonSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(prompt.toString()).doesNotContain("object");
    }

    @Test
    @DisplayName("La operación es obligatoria y aparece en la representación de texto")
    void constructor_shouldRequireOperation() {
        assertThatThrownBy(() -> new LanguageModelPrompt("Responde OK.", 16, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La operación del prompt es obligatoria.");
        assertThat(new LanguageModelPrompt("Responde OK.", 16, AiOperation.EVALUATE).toString())
                .contains("operation=EVALUATE");
    }

    @Test
    @DisplayName("withSchema devuelve una copia con otro esquema y deja intacto el original")
    void withSchema_shouldReturnCopy() {
        LanguageModelPrompt original = new LanguageModelPrompt("Planifica.", 16, AiOperation.PLAN);

        LanguageModelPrompt copy = original.withSchema("{\"type\":\"object\"}");

        assertThat(copy.jsonSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(copy.text()).isEqualTo("Planifica.");
        assertThat(copy.operation()).isEqualTo(AiOperation.PLAN);
        assertThat(original.jsonSchema()).isNull();
    }
}
