package tech.cameia.entrevista.presentation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas del contrato HTTP de {@link HealthController} definido en la spec CM-101.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CE1: GET /health responde 200 con {\"status\":\"UP\"} en JSON")
    void healthReturnsUpStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("CE2: GET /health ignora parámetros de consulta desconocidos")
    void healthIgnoresUnknownQueryParams() throws Exception {
        mockMvc.perform(get("/health").param("cualquier", "valor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("CN1: POST /health responde 405 Method Not Allowed")
    void healthRejectsNonGetMethods() throws Exception {
        mockMvc.perform(post("/health"))
                .andExpect(status().isMethodNotAllowed());
    }
}
