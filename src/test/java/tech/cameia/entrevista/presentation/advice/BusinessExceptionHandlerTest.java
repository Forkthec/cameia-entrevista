package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/**
 * Prueba el formato común de las respuestas de error: el estado, el código estable, el
 * identificador de la petición, el texto genérico de los fallos y lo que queda en el log.
 *
 * <p>Los fallos se provocan con un controlador propio de la prueba, porque hoy ningún
 * controlador real lanza las excepciones del cliente del modelo.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class BusinessExceptionHandlerTest {

    private static final String UUID_V4 = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    private static final String DETALLE = "Ocurrió un error. Inténtalo de nuevo.";

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FailingController())
            .setControllerAdvice(new BusinessExceptionHandler())
            .build();

    @Test
    @DisplayName("Un modelo no disponible responde 503 LLM_UNAVAILABLE con el requestId recibido")
    void modeloNoDisponible_shouldReturn503LlmUnavailable_whenAdapterThrows() throws Exception {
        mockMvc.perform(get("/sin-modelo").header("X-Request-Id", "abc-123"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("application/problem+json")))
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                // Spring 7 omite «type» cuando vale «about:blank», como en Cuentas; RFC 9457 3.1.1
                // define que su ausencia equivale a ese valor.
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.title").value("Servicio no disponible"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(DETALLE))
                .andExpect(jsonPath("$.instance").value("/sin-modelo"))
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value("abc-123"));
    }

    @Test
    @DisplayName("Una cuota agotada del proveedor responde 503 LLM_LIMIT_REACHED")
    void limiteDelModeloAgotado_shouldReturn503LlmLimitReached_whenProviderQuotaIsExhausted() throws Exception {
        mockMvc.perform(get("/sin-cuota").header("X-Request-Id", "abc-123"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.title").value("Servicio no disponible"))
                .andExpect(jsonPath("$.detail").value(DETALLE))
                .andExpect(jsonPath("$.code").value("LLM_LIMIT_REACHED"))
                .andExpect(jsonPath("$.requestId").value("abc-123"));
    }

    @Test
    @DisplayName("Un fallo imprevisto responde 500 INTERNAL_ERROR sin el mensaje ni la clase de la excepción")
    void falloInterno_shouldReturn500WithoutDetail_whenUnexpectedExceptionIsThrown() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/falla").header("X-Request-Id", "abc-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.detail").value(DETALLE))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").value("abc-123"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("detalle interno")
                .doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("Sin X-Request-Id se genera un UUID v4, igual en el cuerpo y en el encabezado")
    void resolverRequestId_shouldGenerateUuid_whenHeaderIsMissing() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/sin-modelo"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.requestId", matchesPattern(UUID_V4)))
                .andReturn();

        assertThat(resultado.getResponse().getHeader("X-Request-Id")).isEqualTo(requestIdDe(resultado));
    }

    @ParameterizedTest(name = "X-Request-Id inválido: [{0}]")
    @ValueSource(strings = {
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "abc 123",
        "abc/123"
    })
    @DisplayName("Un X-Request-Id que no cumple la forma admitida se reemplaza por un UUID v4")
    void resolverRequestId_shouldGenerateUuid_whenHeaderIsInvalid(String recibido) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/sin-modelo").header("X-Request-Id", recibido))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId", matchesPattern(UUID_V4)))
                .andReturn();

        assertThat(requestIdDe(resultado)).isNotEqualTo(recibido);
        assertThat(resultado.getResponse().getHeader("X-Request-Id")).isEqualTo(requestIdDe(resultado));
    }

    @Test
    @DisplayName("El error se registra una vez en ERROR, con code, requestId y la traza de la causa")
    void modeloNoDisponible_shouldLogOnceWithTrace_whenAdapterThrows(CapturedOutput salida) throws Exception {
        mockMvc.perform(get("/sin-modelo").header("X-Request-Id", "abc-123"))
                .andExpect(status().isServiceUnavailable());

        assertThat(salida.getOut())
                .containsOnlyOnce("Modelo de lenguaje no disponible [code=LLM_UNAVAILABLE, requestId=abc-123]")
                .contains("ERROR")
                .contains("java.io.IOException: conexión cortada");
    }

    private static String requestIdDe(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.requestId");
    }

    /** Controlador que solo existe en la prueba y lanza cada una de las excepciones manejadas. */
    @RestController
    static class FailingController {

        @GetMapping("/sin-modelo")
        String sinModelo() {
            throw new LlmUnavailableException(new IOException("conexión cortada"));
        }

        @GetMapping("/sin-cuota")
        String sinCuota() {
            throw new LlmLimitReachedException(new IllegalStateException("429 RESOURCE_EXHAUSTED"));
        }

        @GetMapping("/falla")
        String falla() {
            throw new IllegalStateException("detalle interno");
        }
    }
}
