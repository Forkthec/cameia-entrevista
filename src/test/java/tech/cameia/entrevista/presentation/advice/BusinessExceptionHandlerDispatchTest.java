package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

/**
 * Prueba el manejador fuera del despacho normal de Spring MVC: sin petición HTTP en curso y
 * en el despacho de error del contenedor ({@code /error}), donde la misma petición puede
 * llegar dos veces.
 *
 * <p>El manejador se invoca directamente con la petición fijada en
 * {@link RequestContextHolder}, que es de donde la lee.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class BusinessExceptionHandlerDispatchTest {

    private static final String UUID_V4 = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    private final BusinessExceptionHandler handler = new BusinessExceptionHandler();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Sin petición HTTP en curso se genera un requestId, no hay instance y el error se registra")
    void modelUnavailable_shouldGenerateRequestId_whenThereIsNoRequest(CapturedOutput output) {
        ProblemDetail problem = handler.modelUnavailable(failure());

        assertThat((String) problem.getProperties().get("requestId")).matches(UUID_V4);
        assertThat(problem.getInstance()).isNull();
        assertThat(output.getOut()).contains("Modelo de lenguaje no disponible [code=LLM_UNAVAILABLE");
    }

    @Test
    @DisplayName("Una petición sin respuesta asociada conserva el requestId recibido")
    void modelUnavailable_shouldKeepRequestId_whenRequestHasNoResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/prueba");
        request.addHeader("X-Request-Id", "abc-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ProblemDetail problem = handler.modelUnavailable(failure());

        assertThat(problem.getProperties()).containsEntry("requestId", "abc-123");
    }

    @Test
    @DisplayName("En el despacho de error, instance es la ruta original y el error ya registrado no se repite")
    void modelUnavailable_shouldLogOnce_whenErrorDispatchRepeatsTheRequest(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.addHeader("X-Request-Id", "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));

        // Primer paso: el despacho normal registra el error y marca la petición.
        handler.modelUnavailable(failure());
        // Segundo paso: el contenedor reenvía la misma petición a /error.
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/entrevistas");
        ProblemDetail problem = handler.modelUnavailable(failure());

        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/entrevistas"));
        assertThat(problem.getProperties()).containsEntry("requestId", "abc-123");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
        assertThat(output.getOut()).containsOnlyOnce("Modelo de lenguaje no disponible");
    }

    @Test
    @DisplayName("Un error que llega por primera vez en el despacho de error sí se registra")
    void modelUnavailable_shouldLog_whenErrorDispatchIsTheFirstTime(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, new MockHttpServletResponse()));

        handler.modelUnavailable(failure());

        assertThat(output.getOut()).containsOnlyOnce("Modelo de lenguaje no disponible");
    }

    @ParameterizedTest(name = "ruta original [{0}]")
    @ValueSource(strings = {"/%zz", "sin-barra"})
    @DisplayName("Una ruta original inválida o sin barra inicial no se usa como instance")
    void modelUnavailable_shouldOmitInstance_whenOriginalPathIsInvalid(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, new MockHttpServletResponse()));

        ProblemDetail problem = handler.modelUnavailable(failure());

        assertThat(problem.getInstance()).isNull();
    }

    private static LlmUnavailableException failure() {
        return new LlmUnavailableException(new IOException("conexión cortada"));
    }
}
