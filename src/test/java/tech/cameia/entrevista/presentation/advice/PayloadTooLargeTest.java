package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

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
 * Comprueba con servidor real que un {@code multipart/form-data} más grande que el máximo de la
 * petición responde con el formato común del servicio y no con un 413 sin cuerpo.
 *
 * <p>El máximo se baja a 1 KB para enviar solo 2 KB: con el valor de producción (10 MB) el
 * contenedor cierra la conexión sin leer el resto del cuerpo y el cliente puede perder la
 * respuesta, lo que haría la prueba inestable sin probar nada más.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.servlet.multipart.max-request-size=1KB", "spring.servlet.multipart.max-file-size=1KB"})
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class PayloadTooLargeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate client;

    @Test
    @DisplayName("Un multipart sobre el máximo devuelve REQUEST_PAYLOAD_TOO_LARGE y un 413 con su cuerpo")
    void oversizedMultipart_shouldReturn413WithCode() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=xx");
        String body = "--xx\r\nContent-Disposition: form-data; name=\"f\"\r\n\r\n" + "a".repeat(2048) + "\r\n--xx--\r\n";

        ResponseEntity<String> response = client.exchange("/health", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(response.getHeaders().getContentType().getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(response.getBody(), "$.code")).isEqualTo("REQUEST_PAYLOAD_TOO_LARGE");
        assertThat((String) JsonPath.read(response.getBody(), "$.detail"))
                .isEqualTo("Los datos enviados superan el tamaño permitido.");
        assertThat((String) JsonPath.read(response.getBody(), "$.requestId"))
                .isEqualTo(response.getHeaders().getFirst("X-Request-Id"))
                .isNotBlank();
    }
}
