package tech.cameia.entrevista.presentation.advice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.entrevista.domain.exception.IdentityRequiredException;

/**
 * La identidad llega solo en el encabezado {@code X-User-Id} del Gateway: ausente, vacía, en
 * blanco, demasiado larga o con caracteres de control es una falta de identidad (401), y la que
 * venga en el cuerpo nunca se usa.
 */
class IdentityHeaderTest {

    private static final String PATH = "/test/errors/identity";

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ErrorProbeController())
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    @Test
    @DisplayName("Sin encabezado responde 401 IDENTITY_REQUIRED")
    void identity_shouldReturn401_whenHeaderMissing() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.detail").value(IdentityRequiredException.MESSAGE));
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "   ", "uid\u0007"})
    @DisplayName("Vacío, solo espacios o con carácter de control responde 401")
    void identity_shouldReturn401_whenHeaderIsEmptyBlankOrHasControlChar(String value) throws Exception {
        mockMvc.perform(get(PATH).header("X-User-Id", value))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    @DisplayName("129 caracteres responde 401")
    void identity_shouldReturn401_whenHeaderTooLong() throws Exception {
        mockMvc.perform(get(PATH).header("X-User-Id", "u".repeat(129)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    @DisplayName("128 caracteres se acepta")
    void identity_shouldAccept_when128Chars() throws Exception {
        mockMvc.perform(get(PATH).header("X-User-Id", "u".repeat(128)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("u".repeat(128)));
    }

    @Test
    @DisplayName("128 puntos de código con emoji se aceptan aunque pasen de 128 unidades UTF-16")
    void identity_shouldAccept_when128CodePointsWithEmoji() throws Exception {
        String uid = "😀".repeat(128);

        mockMvc.perform(get(PATH).header("X-User-Id", uid))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La identidad del cuerpo se ignora y rige la del encabezado")
    void identity_shouldIgnoreBodyIdentity() throws Exception {
        mockMvc.perform(post("/test/errors/identity-body").header("X-User-Id", "uid-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"firebaseUid\":\"otro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("uid-a"));
    }
}
