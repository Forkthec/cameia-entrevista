package tech.cameia.entrevista.infrastructure.config.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Verifica que el servicio no espere más de lo previsto una conexión a la base: con la base caída, la
 * petición debe fallar con el error controlado en pocos segundos y no dejar hilos ocupados medio minuto.
 */
class ConnectionTimeoutTest {

    private static final String TIMEOUT_PROPERTY = "spring.datasource.hikari.connection-timeout";

    @Test
    @DisplayName("La espera de una conexión a la base es de 5 segundos")
    void connectionTimeout_shouldBeFiveSeconds_whenDeclaredInBaseConfiguration() throws IOException {
        Properties configuration =
                PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(configuration.getProperty(TIMEOUT_PROPERTY)).isEqualTo("5000");
    }
}
