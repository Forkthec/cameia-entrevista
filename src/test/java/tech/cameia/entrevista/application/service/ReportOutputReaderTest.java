package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.ContentGrade;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;

/** La salida del modelo no es de confianza: solo se acepta exactamente la forma pedida. */
class ReportOutputReaderTest {

    private static final Set<Integer> CONTENT = Set.of(1, 2, 4);

    private static final Set<Integer> STAR = Set.of(3, 5);

    private static final String VALID = """
            {"contenido":[
              {"numero":1,"precision_claridad":5,"carisma_verbal":5,"lenguaje_adecuado":5},
              {"numero":2,"precision_claridad":1,"carisma_verbal":2,"lenguaje_adecuado":3},
              {"numero":4,"precision_claridad":3,"carisma_verbal":2,"lenguaje_adecuado":1}],
             "star":[
              {"numero":3,"situacion":true,"tarea":true,"accion":true,"resultado":true},
              {"numero":5,"situacion":true,"tarea":true,"accion":true,"resultado":false}]}""";

    private final ReportOutputReader reader = new ReportOutputReader(JsonMapper.builder().build());

    private static LanguageModelResponse response(String text) {
        return new LanguageModelResponse(text, 10, 10, "modelo");
    }

    private static String contentItem(int number, String precision, String extra) {
        return "{\"numero\":" + number + ",\"precision_claridad\":" + precision
                + ",\"carisma_verbal\":2,\"lenguaje_adecuado\":3" + extra + "}";
    }

    private static final String STAR_OK = "\"star\":[{\"numero\":3,\"situacion\":true,\"tarea\":true,"
            + "\"accion\":true,\"resultado\":true},{\"numero\":5,\"situacion\":true,\"tarea\":true,"
            + "\"accion\":true,\"resultado\":false}]";

    private static String withContent(String... items) {
        return "{\"contenido\":[" + String.join(",", items) + "]," + STAR_OK + "}";
    }

    @Test
    @DisplayName("La salida válida da las notas y las marcas STAR de cada pregunta")
    void valid_shouldReadGradesAndStar() {
        ReportGrades grades = reader.read(response(VALID), CONTENT, STAR);

        assertThat(grades.content()).containsExactlyInAnyOrder(new ContentGrade(1, 5, 5, 5),
                new ContentGrade(2, 1, 2, 3), new ContentGrade(4, 3, 2, 1));
        assertThat(grades.star()).containsExactlyInAnyOrder(new ReportGrades.StarMarks(3, true, true, true, true),
                new ReportGrades.StarMarks(5, true, true, true, false));
    }

    @Test
    @DisplayName("Un bloque de código alrededor del JSON se quita")
    void fencedJson_shouldBeRead() {
        assertThat(reader.read(response("```json\n" + VALID + "\n```"), CONTENT, STAR).content()).hasSize(3);
    }

    @Test
    @DisplayName("Una sesión sin preguntas conductuales acepta star vacío")
    void noBehavioralQuestions_shouldAcceptEmptyStar() {
        String json = "{\"contenido\":[" + contentItem(1, "5", "") + "],\"star\":[]}";

        ReportGrades grades = reader.read(response(json), Set.of(1), Set.of());

        assertThat(grades.content()).hasSize(1);
        assertThat(grades.star()).isEmpty();
    }

    static java.util.stream.Stream<Arguments> invalidOutputs() {
        String c1 = contentItem(1, "5", "");
        String c2 = contentItem(2, "1", "");
        String c4 = contentItem(4, "3", "");
        return java.util.stream.Stream.of(
                Arguments.of("JSON roto", "{\"contenido\":[", false),
                Arguments.of("texto que no es JSON", "Aquí está tu reporte", false),
                Arguments.of("la raíz es una lista", "[]", false),
                Arguments.of("clave raíz de más", VALID.replace("{\"contenido\"", "{\"x\":1,\"contenido\""), false),
                Arguments.of("falta star", "{\"contenido\":[" + String.join(",", c1, c2, c4) + "]}", false),
                Arguments.of("falta contenido", "{" + STAR_OK + "}", false),
                Arguments.of("número 2 repetido", withContent(c1, c2, c2, c4), false),
                Arguments.of("número 6 de más", withContent(c1, c2, c4, contentItem(6, "3", "")), false),
                Arguments.of("falta el 4", withContent(c1, c2), false),
                Arguments.of("nota 0", withContent(c1, contentItem(2, "0", ""), c4), false),
                Arguments.of("nota 6", withContent(c1, contentItem(2, "6", ""), c4), false),
                Arguments.of("nota como texto", withContent(c1, contentItem(2, "\"4\"", ""), c4), false),
                Arguments.of("nota booleana", withContent(c1, contentItem(2, "true", ""), c4), false),
                Arguments.of("nota decimal", withContent(c1, contentItem(2, "4.5", ""), c4), false),
                Arguments.of("nota 4.0 con decimales", withContent(c1, contentItem(2, "4.0", ""), c4), false),
                Arguments.of("nota nula", withContent(c1, contentItem(2, "null", ""), c4), false),
                Arguments.of("campo extra motivo", withContent(c1, contentItem(2, "1", ",\"motivo\":\"x\""), c4),
                        false),
                Arguments.of("la conductual 3 en contenido", withContent(c1, c2, c4, contentItem(3, "3", "")),
                        false),
                Arguments.of("componente con texto", VALID.replace("\"situacion\":true", "\"situacion\":\"si\""),
                        false),
                Arguments.of("componente nulo", VALID.replace("\"situacion\":true", "\"situacion\":null"), false),
                Arguments.of("falta un componente", VALID.replace("\"tarea\":true,", ""), false),
                Arguments.of("campo extra en star", VALID.replace("\"resultado\":false", "\"resultado\":false,\"x\":1"),
                        false),
                Arguments.of("star repite el 5", VALID.replace("\"numero\":3", "\"numero\":5"), false),
                Arguments.of("número no entero", VALID.replace("\"numero\":1,", "\"numero\":1.5,"), false));
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Toda salida que no cumple la forma lanza LlmInvalidOutputException")
    @MethodSource("invalidOutputs")
    void invalid_shouldThrowInvalidOutput(String name, String output, boolean unused) {
        assertThatThrownBy(() -> reader.read(response(output), CONTENT, STAR))
                .isInstanceOf(LlmInvalidOutputException.class);
    }

    @Test
    @DisplayName("La razón del error nunca copia texto del modelo")
    void reason_shouldNotCopyModelText() {
        String secret = "{\"contenido\":[],\"star\":[],\"motivo\":\"SECRETO-DEL-MODELO\"}";

        assertThatThrownBy(() -> reader.read(response(secret), CONTENT, STAR))
                .isInstanceOfSatisfying(LlmInvalidOutputException.class,
                        e -> assertThat(e.getReason()).doesNotContain("SECRETO"));
    }
}
