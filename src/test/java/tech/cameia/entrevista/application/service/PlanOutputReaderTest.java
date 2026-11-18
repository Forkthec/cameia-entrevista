package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.PlanDraft;

/** Lectura de la salida del modelo según el esquema plan-preguntas.v1. */
class PlanOutputReaderTest {

    private final PlanOutputReader reader = new PlanOutputReader(JsonMapper.builder().build());

    @Test
    void read_shouldParseValidPlanAndIgnoreExtraFields() {
        PlanDraft draft = reader.read("""
                {"idioma":"es-CO","extra":1,"preguntas":[
                 {"posicion":1,"tipo":"APERTURA","texto":"Hola","otro":true},
                 {"posicion":2,"tipo":"tecnica","texto":"Dos"}]}""");
        assertThat(draft.language()).isEqualTo("es-CO");
        assertThat(draft.questions()).hasSize(2);
        assertThat(draft.questions().get(1).type()).isEqualTo("tecnica");
        assertThat(draft.questions().get(0).position()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no es json", "[]", "null", "\"texto\"", "{}", "{\"idioma\":\"es-CO\"}",
            "{\"idioma\":5,\"preguntas\":[]}", "{\"idioma\":\"es-CO\",\"preguntas\":{}}",
            "{\"idioma\":\"es-CO\",\"preguntas\":[1]}",
            "{\"idioma\":\"es-CO\",\"preguntas\":[{\"posicion\":\"1\",\"tipo\":\"APERTURA\",\"texto\":\"x\"}]}",
            "{\"idioma\":\"es-CO\",\"preguntas\":[{\"posicion\":1.5,\"tipo\":\"APERTURA\",\"texto\":\"x\"}]}",
            "{\"idioma\":\"es-CO\",\"preguntas\":[{\"posicion\":99999999999,\"tipo\":\"APERTURA\",\"texto\":\"x\"}]}",
            "{\"idioma\":\"es-CO\",\"preguntas\":[{\"tipo\":\"APERTURA\",\"texto\":\"x\"}]}",
            "{\"idioma\":\"es-CO\",\"preguntas\":[{\"posicion\":1,\"tipo\":3,\"texto\":\"x\"}]}",
            "{\"idioma\":\"es-CO\",\"preguntas\":[{\"posicion\":1,\"tipo\":\"APERTURA\",\"texto\":null}]}",
            "{\"idioma\":\"es-CO\",\"preguntas\":[{\"posicion\":1,\"tipo\":\"APERTURA\"}]}"})
    void read_shouldRejectShapesOutsideTheSchema(String output) {
        assertThatThrownBy(() -> reader.read(output)).isInstanceOf(LlmInvalidOutputException.class);
    }

    @Test
    void read_shouldNotCopyModelTextIntoTheReason() {
        assertThatThrownBy(() -> reader.read("SECRETO-DEL-MODELO"))
                .isInstanceOfSatisfying(LlmInvalidOutputException.class,
                        e -> assertThat(e.getReason()).doesNotContain("SECRETO"));
    }
}
