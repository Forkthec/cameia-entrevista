package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Comprueba que lo que el contenedor reporta por {@code /error} sale con el formato común del
 * servicio, según el estado original.
 */
@ExtendWith(OutputCaptureExtension.class)
class ContainerErrorControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ContainerErrorController())
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    @ParameterizedTest
    @DisplayName("Cada estado del contenedor responde con su código del catálogo")
    @CsvSource({
        "404, 404, ROUTE_NOT_FOUND",
        "405, 405, METHOD_NOT_ALLOWED",
        "406, 406, MEDIA_TYPE_NOT_ACCEPTABLE",
        "415, 415, MEDIA_TYPE_NOT_ALLOWED",
        "413, 413, REQUEST_PAYLOAD_TOO_LARGE",
        "400, 400, REQUEST_PATH_INVALID_FORMAT"})
    void error_shouldUseCatalogCode_whenContainerReportsStatus(int original, int expected, String code)
            throws Exception {
        mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, original))
                .andExpect(status().is(expected))
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("application/problem+json")))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("El 400 del contenedor dice «La ruta solicitada no es válida.»")
    void error_shouldExplainPath_whenContainerReports400() throws Exception {
        mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
                .andExpect(jsonPath("$.detail").value("La ruta solicitada no es válida."))
                .andExpect(jsonPath("$.title").value("Datos no válidos"));
    }

    @Test
    @DisplayName("Pedir /error directamente responde 404 ROUTE_NOT_FOUND")
    void error_shouldReturn404_whenRequestedDirectly() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("El registro del 404 del contenedor lleva el método original de la petición")
    void error_shouldLogOriginalMethod_whenContainerReports404(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound());

        assertThat(output.getAll()).contains("metodo=POST");
    }

    @Test
    @DisplayName("Un estado sin código propio responde 500 INTERNAL_ERROR y no copia la ruta ni el mensaje")
    void error_shouldReturn500Generic_whenStatusHasNoCode(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 503)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/%zz")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("detalle interno")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."));
        assertThat(output.getAll()).contains("estado=503");
    }

    @Test
    @DisplayName("La respuesta del 400 no incluye la ruta original con codificación inválida")
    void error_shouldNotEchoInvalidPath_whenContainerReports400() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/%zz"))
                .andExpect(jsonPath("$.instance", not(containsStringIgnoringCase("%zz"))));
    }
}
