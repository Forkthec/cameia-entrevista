package tech.cameia.entrevista.infrastructure.config.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifica el interruptor que publica o retira la documentación de la API.
 *
 * <p>La documentación se sirve en dos rutas que van juntas: el documento OpenAPI en
 * {@code /v3/api-docs} y la interfaz Swagger UI en {@code /swagger-ui.html}. Ambas se
 * gobiernan con la misma variable {@code API_DOCS_ENABLED}, cuyo valor por defecto lo
 * fija el perfil activo.</p>
 *
 * <p>La prueba carga los archivos de configuración reales sobre un entorno aislado, sin
 * las variables de sistema de la máquina, para que el resultado dependa solo de los
 * perfiles del repositorio.</p>
 */
class ApiDocsFlagTest {

    private static final String API_DOCS = "springdoc.api-docs.enabled";
    private static final String SWAGGER_UI = "springdoc.swagger-ui.enabled";
    private static final String OVERRIDE = "API_DOCS_ENABLED";

    /**
     * Carga la configuración del repositorio para un perfil concreto.
     *
     * @param profile perfil de Spring que se activa durante la carga
     * @param override valor del interruptor, o {@code null} para dejar actuar al perfil
     * @return entorno resultante, con los marcadores de posición ya resolubles
     */
    private ConfigurableEnvironment environmentFor(String profile, String override) {
        MockEnvironment environment = new MockEnvironment();
        if (override != null) {
            environment.setProperty(OVERRIDE, override);
        }
        ConfigDataEnvironmentPostProcessor.applyTo(environment, null, null, profile);
        return environment;
    }

    @Test
    @DisplayName("El perfil local publica el documento OpenAPI y la interfaz Swagger UI")
    void localProfileEnablesDocumentation() {
        ConfigurableEnvironment environment = environmentFor("local", null);

        assertThat(environment.getProperty(API_DOCS, Boolean.class)).isTrue();
        assertThat(environment.getProperty(SWAGGER_UI, Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("El perfil production retira el documento OpenAPI y la interfaz Swagger UI")
    void productionProfileDisablesDocumentation() {
        ConfigurableEnvironment environment = environmentFor("production", null);

        assertThat(environment.getProperty(API_DOCS, Boolean.class)).isFalse();
        assertThat(environment.getProperty(SWAGGER_UI, Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("Sin perfil activo se aplica el perfil local y la documentación se publica")
    void defaultProfileEnablesDocumentation() {
        MockEnvironment environment = new MockEnvironment();
        ConfigDataEnvironmentPostProcessor.applyTo(environment, null, null);

        assertThat(environment.getProperty(API_DOCS, Boolean.class)).isTrue();
        assertThat(environment.getProperty(SWAGGER_UI, Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("Un perfil sin configuración propia hereda la documentación retirada")
    void unknownProfileDisablesDocumentation() {
        ConfigurableEnvironment environment = environmentFor("staging", null);

        assertThat(environment.getProperty(API_DOCS, Boolean.class)).isFalse();
        assertThat(environment.getProperty(SWAGGER_UI, Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("API_DOCS_ENABLED reactiva la documentación sobre el perfil production")
    void overrideEnablesDocumentationInProduction() {
        ConfigurableEnvironment environment = environmentFor("production", "true");

        assertThat(environment.getProperty(API_DOCS, Boolean.class)).isTrue();
        assertThat(environment.getProperty(SWAGGER_UI, Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("API_DOCS_ENABLED retira la documentación sobre el perfil local")
    void overrideDisablesDocumentationInLocal() {
        ConfigurableEnvironment environment = environmentFor("local", "false");

        assertThat(environment.getProperty(API_DOCS, Boolean.class)).isFalse();
        assertThat(environment.getProperty(SWAGGER_UI, Boolean.class)).isFalse();
    }
}
