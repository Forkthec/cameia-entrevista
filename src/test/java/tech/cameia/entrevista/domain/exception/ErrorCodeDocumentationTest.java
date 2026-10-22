package tech.cameia.entrevista.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Comprueba que cada código de error del servicio está documentado en el catálogo público
 * {@code docs/errores.md}, que es lo que leen Frontend y quien depura.
 *
 * <p>Un código que existe en el código y no en el catálogo es un contrato sin documentar.</p>
 */
class ErrorCodeDocumentationTest {

    private static String catalogo;

    @BeforeAll
    static void leerCatalogo() throws IOException {
        catalogo = Files.readString(Path.of("docs", "errores.md"), StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorCode.class)
    @DisplayName("Cada código de ErrorCode es una fila del catálogo de errores")
    void catalogo_shouldListCode_whenCodeExists(ErrorCode code) {
        // Entre comillas invertidas, como fila de la tabla: evita que un código que es prefijo
        // de otro se dé por documentado.
        assertThat(catalogo).contains("| `" + code.name() + "` |");
    }
}
