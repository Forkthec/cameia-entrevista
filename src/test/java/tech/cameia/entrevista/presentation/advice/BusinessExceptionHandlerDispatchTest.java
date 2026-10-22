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

    private final BusinessExceptionHandler manejador = new BusinessExceptionHandler();

    @AfterEach
    void limpiarPeticion() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Sin petición HTTP en curso se genera un requestId, no hay instance y el error se registra")
    void modeloNoDisponible_shouldGenerateRequestId_whenThereIsNoRequest(CapturedOutput salida) {
        ProblemDetail problema = manejador.modeloNoDisponible(fallo());

        assertThat((String) problema.getProperties().get("requestId")).matches(UUID_V4);
        assertThat(problema.getInstance()).isNull();
        assertThat(salida.getOut()).contains("Modelo de lenguaje no disponible [code=LLM_UNAVAILABLE");
    }

    @Test
    @DisplayName("Una petición sin respuesta asociada conserva el requestId recibido")
    void modeloNoDisponible_shouldKeepRequestId_whenRequestHasNoResponse() {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/prueba");
        peticion.addHeader("X-Request-Id", "abc-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));

        ProblemDetail problema = manejador.modeloNoDisponible(fallo());

        assertThat(problema.getProperties()).containsEntry("requestId", "abc-123");
    }

    @Test
    @DisplayName("En el despacho de error, instance es la ruta original y el error ya registrado no se repite")
    void modeloNoDisponible_shouldLogOnce_whenErrorDispatchRepeatsTheRequest(CapturedOutput salida) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/error");
        peticion.addHeader("X-Request-Id", "abc-123");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion, respuesta));

        // Primer paso: el despacho normal registra el error y marca la petición.
        manejador.modeloNoDisponible(fallo());
        // Segundo paso: el contenedor reenvía la misma petición a /error.
        peticion.setDispatcherType(DispatcherType.ERROR);
        peticion.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/entrevistas");
        ProblemDetail problema = manejador.modeloNoDisponible(fallo());

        assertThat(problema.getInstance()).isEqualTo(URI.create("/api/v1/entrevistas"));
        assertThat(problema.getProperties()).containsEntry("requestId", "abc-123");
        assertThat(respuesta.getHeader("X-Request-Id")).isEqualTo("abc-123");
        assertThat(salida.getOut()).containsOnlyOnce("Modelo de lenguaje no disponible");
    }

    @Test
    @DisplayName("Un error que llega por primera vez en el despacho de error sí se registra")
    void modeloNoDisponible_shouldLog_whenErrorDispatchIsTheFirstTime(CapturedOutput salida) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/error");
        peticion.setDispatcherType(DispatcherType.ERROR);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(peticion, new MockHttpServletResponse()));

        manejador.modeloNoDisponible(fallo());

        assertThat(salida.getOut()).containsOnlyOnce("Modelo de lenguaje no disponible");
    }

    @ParameterizedTest(name = "ruta original [{0}]")
    @ValueSource(strings = {"/%zz", "sin-barra"})
    @DisplayName("Una ruta original inválida o sin barra inicial no se usa como instance")
    void modeloNoDisponible_shouldOmitInstance_whenOriginalPathIsInvalid(String ruta) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/error");
        peticion.setDispatcherType(DispatcherType.ERROR);
        peticion.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, ruta);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(peticion, new MockHttpServletResponse()));

        ProblemDetail problema = manejador.modeloNoDisponible(fallo());

        assertThat(problema.getInstance()).isNull();
    }

    private static LlmUnavailableException fallo() {
        return new LlmUnavailableException(new IOException("conexión cortada"));
    }
}
