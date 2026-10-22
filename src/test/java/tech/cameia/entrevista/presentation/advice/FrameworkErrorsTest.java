package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Comprueba que los errores del propio framework web (ruta inexistente, método no permitido,
 * tipo de respuesta no admitido) salen con el formato de error del servicio y no como la página
 * de error por defecto.
 *
 * <p>Usa el contexto completo con un puerto aleatorio porque el comportamiento depende de cómo el
 * contenedor de servlets y Spring resuelven la ruta antes de llegar a un controlador.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class FrameworkErrorsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate cliente;

    @Test
    @DisplayName("Una ruta inexistente devuelve ROUTE_NOT_FOUND y un 404")
    void unknownRoute_shouldReturn404WithCode() {
        ResponseEntity<String> respuesta = cliente.getForEntity("/ruta-que-no-existe", String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertEsErrorDelServicio(respuesta, "ROUTE_NOT_FOUND", "No existe la ruta solicitada.");
    }

    @Test
    @DisplayName("Un método no permitido devuelve METHOD_NOT_ALLOWED, un 405 y Allow")
    void wrongMethod_shouldReturn405WithAllow() {
        ResponseEntity<String> respuesta = cliente.exchange("/health", HttpMethod.POST, HttpEntity.EMPTY,
                String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(405);
        assertEsErrorDelServicio(respuesta, "METHOD_NOT_ALLOWED", "Método no permitido.");
        assertThat(respuesta.getHeaders().getAllow()).contains(HttpMethod.GET);
    }

    @Test
    @DisplayName("Un Accept de XML devuelve MEDIA_TYPE_NOT_ACCEPTABLE y un 406")
    void xmlAccept_shouldReturn406WithCode() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_XML));

        ResponseEntity<String> respuesta = cliente.exchange("/health", HttpMethod.GET, new HttpEntity<>(headers),
                String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(406);
        assertEsErrorDelServicio(respuesta, "MEDIA_TYPE_NOT_ACCEPTABLE", "Tipo de respuesta no admitido.");
    }

    @Test
    @DisplayName("Un tipo de contenido no admitido devuelve MEDIA_TYPE_NOT_ALLOWED y un 415")
    void unsupportedContentType_shouldReturn415WithCode() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<String> respuesta = cliente.exchange("/test/errors/body", HttpMethod.POST,
                new HttpEntity<>("hola", headers), String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(415);
        assertEsErrorDelServicio(respuesta, "MEDIA_TYPE_NOT_ALLOWED", "Tipo de contenido no admitido.");
    }

    @Test
    @DisplayName("Ningún error del framework revela textos internos")
    void noError_shouldRevealInternals() {
        ResponseEntity<String> respuesta = cliente.getForEntity("/ruta-que-no-existe", String.class);

        assertThat(respuesta.getBody())
                .doesNotContain("trace")
                .doesNotContain("exception")
                .doesNotContain("NoResourceFoundException")
                .doesNotContain("org.springframework")
                .doesNotContain("Whitelabel");
    }

    private void assertEsErrorDelServicio(ResponseEntity<String> respuesta, String codigo, String detalle) {
        assertThat(respuesta.getHeaders().getContentType()).isNotNull();
        assertThat(respuesta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(respuesta.getHeaders().getContentType().getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.code")).isEqualTo(codigo);
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.detail")).isEqualTo(detalle);
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.requestId"))
                .isEqualTo(respuesta.getHeaders().getFirst("X-Request-Id"))
                .isNotBlank();
    }
}
