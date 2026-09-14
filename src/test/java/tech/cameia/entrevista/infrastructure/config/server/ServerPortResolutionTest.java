package tech.cameia.entrevista.infrastructure.config.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifica cómo se resuelve el puerto HTTP en el que escucha el microservicio.
 *
 * <p>Cloud Run inyecta la variable {@code PORT} y exige que el proceso escuche
 * exactamente en ese puerto; si no lo hace, el contenedor no arranca. Fuera de Cloud Run
 * manda {@code SERVER_PORT}, y sin ninguna de las dos se usa el 8080 del contenedor.</p>
 *
 * <p>La prueba toma la expresión real de {@code application.properties} y la resuelve
 * sobre un entorno aislado que solo contiene las variables bajo prueba. No carga los
 * archivos de configuración por perfil ni el {@code .env} de la máquina, de modo que el
 * resultado no depende de la configuración local de quien ejecuta las pruebas.</p>
 */
class ServerPortResolutionTest {

    private static final String SERVER_PORT_PROPERTY = "server.port";
    private static final String CLOUD_RUN_VARIABLE = "PORT";
    private static final String LOCAL_VARIABLE = "SERVER_PORT";

    /** Expresión declarada para {@code server.port}, sin marcadores resueltos. */
    private static String portExpression;

    /**
     * Lee la expresión declarada para {@code server.port} en la configuración base.
     *
     * @throws IOException si {@code application.properties} no puede leerse
     */
    @BeforeAll
    static void readPortExpression() throws IOException {
        Properties configuration =
                PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        portExpression = configuration.getProperty(SERVER_PORT_PROPERTY);

        assertThat(portExpression)
                .as("application.properties debe declarar %s", SERVER_PORT_PROPERTY)
                .isNotBlank();
    }

    /**
     * Resuelve la expresión del puerto con las variables de entorno indicadas.
     *
     * @param cloudRunPort valor de {@code PORT}, o {@code null} si no está definida
     * @param localPort valor de {@code SERVER_PORT}, o {@code null} si no está definida
     * @return puerto resultante una vez resueltos los marcadores de posición
     */
    private String resolveWith(String cloudRunPort, String localPort) {
        MockEnvironment environment = new MockEnvironment();
        if (cloudRunPort != null) {
            environment.setProperty(CLOUD_RUN_VARIABLE, cloudRunPort);
        }
        if (localPort != null) {
            environment.setProperty(LOCAL_VARIABLE, localPort);
        }
        return environment.resolveRequiredPlaceholders(portExpression);
    }

    @Test
    @DisplayName("PORT de Cloud Run tiene prioridad sobre SERVER_PORT")
    void cloudRunPortTakesPrecedence() {
        assertThat(resolveWith("9090", "8083")).isEqualTo("9090");
    }

    @Test
    @DisplayName("Con PORT y sin SERVER_PORT, el servicio escucha en el puerto de Cloud Run")
    void cloudRunPortAppliesAlone() {
        assertThat(resolveWith("9090", null)).isEqualTo("9090");
    }

    @Test
    @DisplayName("Sin PORT, el servicio escucha en el puerto de SERVER_PORT")
    void localVariableAppliesWithoutCloudRunPort() {
        assertThat(resolveWith(null, "8083")).isEqualTo("8083");
    }

    @Test
    @DisplayName("Sin PORT ni SERVER_PORT, el servicio escucha en el 8080 del contenedor")
    void containerPortAppliesWithoutAnyVariable() {
        assertThat(resolveWith(null, null)).isEqualTo("8080");
    }
}
