package tech.cameia.entrevista.presentation.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.servlet.ServletException;

import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * El identificador de la petición se acepta o se reemplaza según su forma, queda en el
 * encabezado de la respuesta, en el cuerpo de los errores y en cada línea del registro, y el MDC
 * nunca queda sucio entre peticiones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterTest {

    private static final String UUID_V4 = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate client;

    private ResponseEntity<String> get(String path, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        if (requestId != null) {
            headers.set("X-Request-Id", requestId);
        }
        return client.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("Un identificador válido se devuelve igual en el encabezado")
    void requestId_shouldBeEchoed_whenHeaderValid() {
        ResponseEntity<String> response = get("/health", "abc-123");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Sin encabezado se genera un UUID")
    void requestId_shouldBeGenerated_whenHeaderMissing() {
        assertThat(get("/health", null).getHeaders().getFirst("X-Request-Id")).matches(UUID_V4);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "con espacio",
        "ñandú", "a%0d%0ab", ""})
    @DisplayName("Un identificador que no cumple la forma se reemplaza por un UUID")
    void requestId_shouldBeReplaced_whenHeaderInvalid(String sent) {
        String returned = get("/health", sent).getHeaders().getFirst("X-Request-Id");

        assertThat(returned).isNotEqualTo(sent).matches(UUID_V4);
    }

    @Test
    @DisplayName("Un identificador de 64 caracteres, el límite, se acepta")
    void requestId_shouldAcceptBoundary_when64Characters() {
        String limit = "a".repeat(64);

        assertThat(get("/health", limit).getHeaders().getFirst("X-Request-Id")).isEqualTo(limit);
    }

    @Test
    @DisplayName("El requestId del cuerpo del error es el del encabezado de la respuesta")
    void requestId_shouldMatchBody_whenErrorResponse() {
        ResponseEntity<String> response = get("/test/errors/boom", "abc-123");

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat((String) JsonPath.read(response.getBody(), "$.requestId")).isEqualTo("abc-123");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Sin encabezado, el requestId generado es el mismo en el cuerpo y en el encabezado")
    void requestId_shouldMatchBody_whenGeneratedAndErrorResponse() {
        ResponseEntity<String> response = get("/test/errors/boom", null);

        assertThat((String) JsonPath.read(response.getBody(), "$.requestId"))
                .matches(UUID_V4)
                .isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    @DisplayName("La línea del registro del error lleva el requestId entre corchetes")
    void requestId_shouldAppearInLogLine_whenFilterRuns(CapturedOutput output) {
        get("/test/errors/boom", "abc-123");

        List<String> lines = output.getOut().lines().filter(l -> l.contains("Fallo no controlado")).toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).contains("[abc-123]");
    }

    @Test
    @DisplayName("El MDC queda vacío después de una petición")
    void mdc_shouldBeEmpty_afterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ok");
        request.addHeader("X-Request-Id", "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] inside = new String[1];

        new RequestIdFilter().doFilter(request, response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                        inside[0] = MDC.get("requestId");
                    }
                });

        assertThat(inside[0]).isEqualTo("abc-123");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("El MDC queda vacío aunque la cadena lance una excepción")
    void mdc_shouldBeEmpty_whenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ok");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            new RequestIdFilter().doFilter(request, response, (req, res) -> {
                throw new ServletException("falla de prueba");
            });
        } catch (ServletException | java.io.IOException expected) {
            assertThat(expected).hasMessage("falla de prueba");
        }

        assertThat(MDC.get("requestId")).isNull();
    }
}
