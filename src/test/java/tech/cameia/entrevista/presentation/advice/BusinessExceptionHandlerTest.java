package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final String DETAIL = "Ocurrió un error. Inténtalo de nuevo.";

    private final MockMvc probe = MockMvcBuilders
            .standaloneSetup(new ErrorProbeController())
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FailingController())
            .setControllerAdvice(new BusinessExceptionHandler())
            .build();

    @Test
    @DisplayName("Un modelo no disponible responde 503 LLM_UNAVAILABLE con el requestId recibido")
    void modelUnavailable_shouldReturn503LlmUnavailable_whenAdapterThrows() throws Exception {
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
                .andExpect(jsonPath("$.detail").value(DETAIL))
                .andExpect(jsonPath("$.instance").value("/sin-modelo"))
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value("abc-123"));
    }

    @Test
    @DisplayName("Una cuota agotada del proveedor responde 503 LLM_LIMIT_REACHED")
    void modelLimitReached_shouldReturn503LlmLimitReached_whenProviderQuotaIsExhausted() throws Exception {
        mockMvc.perform(get("/sin-cuota").header("X-Request-Id", "abc-123"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.title").value("Servicio no disponible"))
                .andExpect(jsonPath("$.detail").value(DETAIL))
                .andExpect(jsonPath("$.code").value("LLM_LIMIT_REACHED"))
                .andExpect(jsonPath("$.requestId").value("abc-123"));
    }

    @Test
    @DisplayName("Un fallo imprevisto responde 500 INTERNAL_ERROR sin el mensaje ni la clase de la excepción")
    void unexpectedFailure_shouldReturn500WithoutDetail_whenUnexpectedExceptionIsThrown() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/falla").header("X-Request-Id", "abc-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.detail").value(DETAIL))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").value("abc-123"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("detalle interno")
                .doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("Sin X-Request-Id se genera un UUID v4, igual en el cuerpo y en el encabezado")
    void resolveRequestId_shouldGenerateUuid_whenHeaderIsMissing() throws Exception {
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
    void resolveRequestId_shouldGenerateUuid_whenHeaderIsInvalid(String recibido) throws Exception {
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
    void modelUnavailable_shouldLogOnceWithTrace_whenAdapterThrows(CapturedOutput salida) throws Exception {
        mockMvc.perform(get("/sin-modelo").header("X-Request-Id", "abc-123"))
                .andExpect(status().isServiceUnavailable());

        assertThat(salida.getOut())
                .containsOnlyOnce("Modelo de lenguaje no disponible [code=LLM_UNAVAILABLE, requestId=abc-123]")
                .contains("ERROR")
                .contains("java.io.IOException: conexión cortada");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "SESSION_TRANSITION_NOT_ALLOWED|409|Acción no permitida|Esta entrevista ya no admite esa acción.",
        "TURN_TRANSITION_NOT_ALLOWED|409|Acción no permitida|Esta pregunta ya no admite esa acción.",
        "SESSION_CONFLICT|409|Cambio simultáneo|La entrevista cambió mientras se procesaba tu solicitud. Inténtalo de nuevo.",
        "SESSION_ALREADY_EXISTS|409|Entrevista existente|Ya tienes una entrevista en preparación o en curso. Inténtalo de nuevo.",
        "IDEMPOTENCY_KEY_ALREADY_EXISTS|409|Solicitud repetida|Esta solicitud ya se registró.",
        "IDENTITY_REQUIRED|401|Identidad requerida|Tu sesión expiró. Inicia sesión de nuevo."
    })
    @DisplayName("Cada código de negocio responde con el estado, el título y el mensaje de su definición")
    void businessException_shouldUseCatalog(String code, int status, String title, String message) throws Exception {
        probe.perform(get("/test/errors/business/" + code))
                .andExpect(status().is(status))
                .andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.detail").value(message))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.requestId", not(emptyString())));
    }

    @Test
    @DisplayName("Dos campos inválidos devuelven 422 con un elemento por cada campo")
    void validation_shouldReturn422WithEveryField_whenTwoFieldsInvalid() throws Exception {
        probe.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Revisa los campos marcados."))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "city")))
                .andExpect(jsonPath("$.errors[0].code").value("REQUEST_INVALID_VALUE"));
    }

    @Test
    @DisplayName("Un JSON mal formado responde 422 REQUEST_BODY_INVALID_FORMAT")
    void unreadableBody_shouldReturn422_whenJsonMalformed() throws Exception {
        probe.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"))
                .andExpect(jsonPath("$.detail").value("Revisa el formato de los datos enviados."));
    }

    @Test
    @DisplayName("Un tipo de contenido no admitido responde 415 MEDIA_TYPE_NOT_ALLOWED")
    void unsupportedMediaType_shouldReturn415() throws Exception {
        probe.perform(post("/test/errors/body").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Un multipart ilegible responde 415 REQUEST_MULTIPART_INVALID_FORMAT")
    void multipart_shouldReturn415_whenBodyMalformed() throws Exception {
        probe.perform(get("/test/errors/multipart"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("application/problem+json")))
                .andExpect(jsonPath("$.code").value("REQUEST_MULTIPART_INVALID_FORMAT"))
                .andExpect(jsonPath("$.detail").value("Revisa el formato de los datos enviados."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("Un multipart demasiado grande responde 413 REQUEST_PAYLOAD_TOO_LARGE")
    void multipart_shouldReturn413WithCode_whenTooLarge() throws Exception {
        probe.perform(get("/test/errors/multipart-too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.code").value("REQUEST_PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.detail").value("Los datos enviados superan el tamaño permitido."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("Un método no admitido responde 405 METHOD_NOT_ALLOWED con Allow")
    void methodNotAllowed_shouldReturn405WithAllow() throws Exception {
        probe.perform(post("/test/errors/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Un encabezado que falta y no es la identidad responde 422 REQUEST_INVALID_VALUE")
    void missingHeader_shouldReturn422_whenNotIdentity() throws Exception {
        probe.perform(get("/test/errors/header"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_VALUE"))
                .andExpect(jsonPath("$.detail").value("Revisa los datos enviados."));
    }

    @Test
    @DisplayName("Un identificador que no es UUID responde 422 y ni el mensaje ni el log repiten el valor")
    void typeMismatch_shouldReturn422_whenPathIsNotUuid(CapturedOutput salida) throws Exception {
        // «instance» es la ruta que la propia persona pidió (RFC 9457); el valor no debe estar en otro miembro.
        MvcResult resultado = probe.perform(get("/test/errors/typed/valor-secreto"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_VALUE"))
                .andExpect(jsonPath("$.detail").value("Revisa los datos enviados."))
                .andReturn();

        assertThat((String) JsonPath.read(resultado.getResponse().getContentAsString(), "$.detail"))
                .doesNotContain("valor-secreto");
        assertThat(salida.getOut()).contains("parametro=id").doesNotContain("valor-secreto");
    }

    @ParameterizedTest(name = "base caída: {0}")
    @ValueSource(strings = {"transaction", "resource", "connection"})
    @DisplayName("Una base de datos caída responde 503 DATABASE_UNAVAILABLE sin el mensaje técnico")
    void databaseDown_shouldReturn503(String type, CapturedOutput salida) throws Exception {
        MvcResult resultado = probe.perform(get("/test/errors/database/" + type))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(DETAIL))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("db-interno");
        assertThat(salida.getOut()).contains("Base de datos no disponible [code=DATABASE_UNAVAILABLE");
    }

    @Test
    @DisplayName("Una violación de restricción responde 500 INTERNAL_ERROR genérico")
    void integrity_shouldReturn500Generic() throws Exception {
        probe.perform(get("/test/errors/integrity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(DETAIL));
    }

    @Test
    @DisplayName("Cualquier otra excepción responde 500 sin detalle interno")
    void unexpected_shouldReturn500Generic_whenAnyException() throws Exception {
        MvcResult resultado = probe.perform(get("/test/errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("detalle interno").doesNotContain("Exception").doesNotContain("trace");
    }

    @Test
    @DisplayName("El log de un rechazo lleva el código y la identidad, y nunca el valor del cuerpo")
    void logs_shouldNotContainBodyOrValues(CapturedOutput salida) throws Exception {
        probe.perform(post("/test/errors/body").header("X-User-Id", "uid-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"secreto-123\"}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(salida.getOut()).contains("VALIDATION_FAILED").contains("uid-a").doesNotContain("secreto-123");
    }

    @Test
    @DisplayName("Una identidad inválida se registra como «-», nunca con su valor")
    void logs_shouldMaskFirebaseUid_whenIdentityIsInvalid(CapturedOutput salida) throws Exception {
        probe.perform(post("/test/errors/body").header("X-User-Id", "x".repeat(129))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(salida.getOut()).contains("firebaseUid=-").doesNotContain("x".repeat(129));
    }

    @Test
    @DisplayName("Un código de negocio sin definición responde 500 INTERNAL_ERROR y se registra")
    void businessFailure_shouldReturn500_whenCodeHasNoDefinition(CapturedOutput salida) throws Exception {
        probe.perform(get("/test/errors/undefined-business"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(salida.getOut()).contains("Código sin definición: LLM_UNAVAILABLE");
    }

    private static String requestIdDe(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.requestId");
    }

    /** Controlador que solo existe en la prueba y lanza cada una de las excepciones manejadas. */
    @RestController
    static class FailingController {

        @GetMapping("/sin-modelo")
        String withoutModel() {
            throw new LlmUnavailableException(new IOException("conexión cortada"));
        }

        @GetMapping("/sin-cuota")
        String withoutQuota() {
            throw new LlmLimitReachedException(new IllegalStateException("429 RESOURCE_EXHAUSTED"));
        }

        @GetMapping("/falla")
        String falla() {
            throw new IllegalStateException("detalle interno");
        }

        @PostMapping("/con-cuerpo")
        String withBody(@RequestBody Map<String, String> body) {
            return "ok";
        }

        @GetMapping("/con-numero")
        String withNumber(@RequestParam int value) {
            return "ok";
        }
    }
}
