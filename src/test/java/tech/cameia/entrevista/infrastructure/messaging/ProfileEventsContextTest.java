package tech.cameia.entrevista.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * El servicio arranca y responde aunque el broker no esté disponible: el consumidor reintenta la
 * conexión en segundo plano y no tumba la aplicación.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.addresses=amqp://localhost:1",
        "spring.rabbitmq.listener.simple.auto-startup=true"})
@AutoConfigureTestRestTemplate
@WithLanguageModelProperties
@Testcontainers(disabledWithoutDocker = true)
class ProfileEventsContextTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate client;

    @Test
    @DisplayName("El contexto arranca y /health responde 200 con el broker caído")
    void context_shouldStart_whenBrokerUnavailable() {
        assertThat(client.getForEntity("/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
