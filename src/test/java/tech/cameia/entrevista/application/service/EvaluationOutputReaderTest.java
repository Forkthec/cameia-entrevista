package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.EvaluationResult;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;

class EvaluationOutputReaderTest {

    private static final String VALID = """
            {"accion":"AVANZAR","clasificacion":"COMPLETA","motivo":"Responde todo","follow_up_texto":"","feedback":"Bien"}
            """;

    private final EvaluationOutputReader reader = new EvaluationOutputReader(JsonMapper.builder().build());

    private EvaluationResult read(String output) {
        return reader.read(new LanguageModelResponse(output, 1, 1, "modelo"));
    }

    private void assertInvalid(String output) {
        assertThatThrownBy(() -> read(output)).isInstanceOf(LlmInvalidOutputException.class);
    }

    @Test
    @DisplayName("Una salida válida de avanzar se lee completa")
    void read_shouldParseValidOutput() {
        EvaluationResult result = read(VALID);

        assertThat(result.action()).isEqualTo("AVANZAR");
        assertThat(result.classification()).isEqualTo("COMPLETA");
        assertThat(result.reason()).isEqualTo("Responde todo");
        assertThat(result.feedback()).isEqualTo("Bien");
    }

    @Test
    @DisplayName("Un FOLLOW_UP con una pregunta de seguimiento de 600 caracteres se acepta y se descarta")
    void read_shouldAcceptFollowUpAndDiscardIt() {
        String output = "{\"accion\":\"FOLLOW_UP\",\"clasificacion\":\"VAGA\",\"motivo\":\"m\",\"follow_up_texto\":\""
                + "a".repeat(600) + "\",\"feedback\":\"f\"}";

        EvaluationResult result = read(output);

        assertThat(result.action()).isEqualTo("FOLLOW_UP");
        assertThat(result.toString()).doesNotContain("aaaa");
    }

    @Test
    @DisplayName("El JSON envuelto en un bloque de código se lee igual")
    void read_shouldStripCodeFence() {
        assertThat(read("```json\n" + VALID + "```").action()).isEqualTo("AVANZAR");
        assertThat(read("```\n" + VALID + "\n```").action()).isEqualTo("AVANZAR");
    }

    @Test
    @DisplayName("Los campos que sobran se ignoran")
    void read_shouldIgnoreExtraFields() {
        assertThat(read("{\"accion\":\"AVANZAR\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"m\",\"feedback\":\"f\","
                + "\"extra\":5}").action()).isEqualTo("AVANZAR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no es json", "[]", "[1,2]", "\"texto\"", "5", "null", "   {"})
    @DisplayName("Una salida que no es un objeto JSON es inválida")
    void read_shouldRejectNonObject(String output) {
        assertInvalid(output);
    }

    @Test
    @DisplayName("Una acción o una clasificación fuera de su conjunto es inválida")
    void read_shouldRejectUnknownValues() {
        assertInvalid(VALID.replace("AVANZAR", "SALTAR"));
        assertInvalid(VALID.replace("COMPLETA", "BUENA"));
    }

    @Test
    @DisplayName("Un motivo o una retroalimentación vacíos, ausentes o no textuales son inválidos")
    void read_shouldRejectBadTexts() {
        assertInvalid(VALID.replace("\"Responde todo\"", "\"\""));
        assertInvalid(VALID.replace("\"Bien\"", "\"\""));
        assertInvalid("{\"accion\":\"AVANZAR\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"m\"}");
        assertInvalid(VALID.replace("\"Responde todo\"", "5"));
        assertInvalid(VALID.replace("\"Bien\"", "null"));
    }

    @Test
    @DisplayName("Un motivo de 4001 caracteres es inválido")
    void read_shouldRejectTooLongReason() {
        assertInvalid(VALID.replace("Responde todo", "a".repeat(4001)));
    }

    @Test
    @DisplayName("El mensaje del error no copia el texto del modelo")
    void read_shouldNotLeakModelText() {
        assertThatThrownBy(() -> read("secreto-del-modelo {")).isInstanceOf(LlmInvalidOutputException.class)
                .satisfies(error -> assertThat(String.valueOf(error.getMessage())).doesNotContain("secreto"));
    }
}
