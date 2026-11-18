package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;

class EvaluationResultTest {

    @ParameterizedTest
    @ValueSource(strings = {"AVANZAR", "FOLLOW_UP"})
    @DisplayName("Acepta las dos acciones y las cinco clasificaciones")
    void constructor_shouldAcceptEveryAction(String action) {
        for (String classification : EvaluationResult.CLASSIFICATIONS) {
            assertThatCode(() -> new EvaluationResult(action, classification, "motivo", "texto"))
                    .doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"avanzar", "OTRA", "FOLLOWUP"})
    @DisplayName("Una acción ausente o fuera del conjunto es una salida inválida")
    void constructor_shouldRejectAction(String action) {
        assertThatThrownBy(() -> new EvaluationResult(action, "COMPLETA", "motivo", "texto"))
                .isInstanceOf(LlmInvalidOutputException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"completa", "BUENA"})
    @DisplayName("Una clasificación ausente o fuera del conjunto es una salida inválida")
    void constructor_shouldRejectClassification(String classification) {
        assertThatThrownBy(() -> new EvaluationResult("AVANZAR", classification, "motivo", "texto"))
                .isInstanceOf(LlmInvalidOutputException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Un motivo o una retroalimentación vacíos son una salida inválida")
    void constructor_shouldRejectBlankTexts(String blank) {
        assertThatThrownBy(() -> new EvaluationResult("AVANZAR", "COMPLETA", blank, "texto"))
                .isInstanceOf(LlmInvalidOutputException.class);
        assertThatThrownBy(() -> new EvaluationResult("AVANZAR", "COMPLETA", "motivo", blank))
                .isInstanceOf(LlmInvalidOutputException.class);
    }

    @Test
    @DisplayName("4000 puntos de código se aceptan y 4001 no, contando emoji como uno")
    void constructor_shouldLimitTextsToFourThousandCodePoints() {
        assertThatCode(() -> new EvaluationResult("AVANZAR", "COMPLETA", "😀".repeat(4000), "a".repeat(4000)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new EvaluationResult("AVANZAR", "COMPLETA", "a".repeat(4001), "texto"))
                .isInstanceOf(LlmInvalidOutputException.class);
        assertThatThrownBy(() -> new EvaluationResult("AVANZAR", "COMPLETA", "motivo", "a".repeat(4001)))
                .isInstanceOf(LlmInvalidOutputException.class);
    }

    @Test
    @DisplayName("toString no muestra el motivo ni la retroalimentación")
    void toString_shouldNotShowTexts() {
        String text = new EvaluationResult("AVANZAR", "COMPLETA", "secreto-motivo", "secreto-feedback").toString();

        assertThat(text).doesNotContain("secreto").contains("AVANZAR", "COMPLETA");
    }
}
