package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
 * Comprueba el catálogo de personalización contra el servidor completo: el contenedor de servlets,
 * el filtro del identificador de petición, el manejador de errores y la documentación OpenAPI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class PersonalizationCatalogEndToEndTest {

    private static final String PATH = "/api/v1/interviews/config/catalog";

    private static final String EXPECTED_BODY = """
            {"modes":[{"value":"SIMULACION","available":true},{"value":"ENTRENO","available":false}],\
            "tones":[{"value":"PROFESIONAL_NEUTRO","available":true},{"value":"CALIDO","available":true},\
            {"value":"ESTRICTO","available":true},{"value":"MOTIVADOR","available":true}],\
            "answerFormats":[{"value":"AUDIO","available":true},{"value":"TEXTO","available":true}],\
            "languages":[{"value":"es-CO","available":true},{"value":"en","available":true}]}""";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate client;

    @LocalServerPort
    private int port;

    private HttpHeaders identity(String uid) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", uid);
        return headers;
    }

    private ResponseEntity<String> exchange(HttpMethod method, HttpHeaders headers) {
        return client.exchange(PATH, method, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("El servidor real responde 200 con charset UTF-8, X-Request-Id y el catálogo exacto")
    void catalog_shouldReturn200WithCharset_whenRealServer() {
        HttpHeaders headers = identity("uid-e2e");
        headers.set("X-Request-Id", "cat-e2e-1");

        ResponseEntity<String> response = exchange(HttpMethod.GET, headers);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(String.valueOf(response.getHeaders().getContentType()).toLowerCase())
                .contains("application/json").contains("charset=utf-8");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("cat-e2e-1");
        assertThat(response.getBody()).isEqualTo(EXPECTED_BODY);
    }

    @Test
    @DisplayName("Sin identidad el servidor real responde 401 problem+json con charset UTF-8")
    void catalog_shouldReturn401WithCharset_whenIdentityMissing_onRealServer() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, new HttpHeaders());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(String.valueOf(response.getHeaders().getContentType()).toLowerCase())
                .contains("application/problem+json").contains("charset=utf-8");
        assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("IDENTITY_REQUIRED");
        assertThat(JsonPath.<String>read(response.getBody(), "$.requestId")).isNotBlank();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    @DisplayName("Un método distinto de GET responde 405 METHOD_NOT_ALLOWED con Allow")
    void catalog_shouldReturn405WithAllow_whenMethodNotGet(String method) {
        ResponseEntity<String> response = exchange(HttpMethod.valueOf(method), identity("uid-e2e"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
    }

    // Envía la petición por un socket crudo: el cliente HTTP de las pruebas no deja mandar un byte de control en un encabezado.
    private String rawResponse(String identityHeaderValue) throws IOException {
        String request = "GET " + PATH + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                + "X-User-Id: " + identityHeaderValue + "\r\n\r\n";
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"CONTROL_CHARACTER", "OVERSIZED"})
    @DisplayName("Un X-User-Id con byte de control o de más de 8 KB lo rechaza el contenedor con 400 REQUEST_PATH_INVALID_FORMAT")
    void catalog_shouldReturn400_whenIdentityRejectedByContainer_onRealServer(String kind) throws IOException {
        String value = "CONTROL_CHARACTER".equals(kind) ? "uid\u0007" : "u".repeat(9000);

        String response = rawResponse(value);

        assertThat(response).startsWith("HTTP/1.1 400");
        assertThat(response).contains("application/problem+json");
        assertThat(JsonPath.<String>read(response.substring(response.indexOf('{')), "$.code"))
                .isEqualTo("REQUEST_PATH_INVALID_FORMAT");
        assertThat(JsonPath.<String>read(response.substring(response.indexOf('{')), "$.detail"))
                .isEqualTo("La petición no es válida.");
        assertThat(response).doesNotContain("modes");
    }

    @Test
    @DisplayName("Un Accept de XML responde 406 MEDIA_TYPE_NOT_ACCEPTABLE")
    void catalog_shouldReturn406_whenAcceptXml() {
        HttpHeaders headers = identity("uid-e2e");
        headers.setAccept(List.of(MediaType.APPLICATION_XML));

        ResponseEntity<String> response = exchange(HttpMethod.GET, headers);

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("MEDIA_TYPE_NOT_ACCEPTABLE");
    }

    @Test
    @DisplayName("50 peticiones seguidas devuelven el mismo cuerpo y el p95 queda dentro de 2 s")
    void catalog_shouldMeetLatencyBudget_when50Requests() {
        HttpHeaders headers = identity("uid-e2e");
        for (int i = 0; i < 5; i++) {
            exchange(HttpMethod.GET, headers);
        }
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long start = System.nanoTime();
            ResponseEntity<String> response = exchange(HttpMethod.GET, headers);
            millis.add((System.nanoTime() - start) / 1_000_000);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(EXPECTED_BODY);
        }
        Collections.sort(millis);

        long p50 = millis.get(24);
        long p95 = millis.get(47);
        assertThat(p95).as("p95 = %d ms, p50 = %d ms", p95, p50).isLessThanOrEqualTo(2000L);
    }

    @Test
    @DisplayName("OpenAPI documenta la ruta con sus cinco respuestas y los cuatro miembros del catálogo")
    void openApi_shouldDocumentCatalog_withEveryResponse() {
        String api = client.getForObject("/v3/api-docs", String.class);

        Map<String, Object> responses = JsonPath.read(api,
                "$.paths['/api/v1/interviews/config/catalog'].get.responses");
        assertThat(responses).containsKeys("200", "401", "405", "406", "500");
        // Cada respuesta lleva un ejemplo: el 200 con el catálogo y los errores con su código y su requestId.
        assertThat(responses.get("200").toString()).contains("example=").contains("SIMULACION").contains("ENTRENO");
        for (String status : List.of("401", "405", "406", "500")) {
            assertThat(responses.get(status).toString()).as("ejemplo de %s", status)
                    .contains("example=").contains("requestId").contains("code");
        }
        Map<String, Object> properties = JsonPath.read(api,
                "$.components.schemas.PersonalizationCatalogResponse.properties");
        assertThat(properties).containsOnlyKeys("modes", "tones", "answerFormats", "languages");
        List<Object> userIdParameters = JsonPath.read(api,
                "$.paths['/api/v1/interviews/config/catalog'].get..parameters[?(@.name=='X-User-Id')]");
        assertThat(userIdParameters).isEmpty();
    }
}
