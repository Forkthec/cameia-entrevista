package tech.cameia.entrevista.presentation.advice;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Resolución del {@code X-Request-Id} por el manejador, sin filtro. */
class RequestIdTest {

    private static final String UUID_V4 = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ErrorProbeController())
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .build();

    @Test
    @DisplayName("Un identificador válido se devuelve igual en el cuerpo y en el encabezado")
    void requestId_shouldEchoHeader_whenValid() throws Exception {
        mockMvc.perform(get("/test/errors/boom").header("X-Request-Id", "abc-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.requestId").value("abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
    }

    @Test
    @DisplayName("Un identificador con caracteres no admitidos se reemplaza por un UUID")
    void requestId_shouldGenerate_whenInvalid() throws Exception {
        mockMvc.perform(get("/test/errors/boom").header("X-Request-Id", "<script>"))
                .andExpect(jsonPath("$.requestId", matchesPattern(UUID_V4)));
    }

    @Test
    @DisplayName("Un identificador de 65 caracteres se reemplaza por un UUID")
    void requestId_shouldGenerate_whenTooLong() throws Exception {
        mockMvc.perform(get("/test/errors/boom").header("X-Request-Id", "a".repeat(65)))
                .andExpect(jsonPath("$.requestId", matchesPattern(UUID_V4)));
    }
}
