package tech.cameia.entrevista.infrastructure.config.documentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la documentación OpenAPI del microservicio de Entrevista.
 *
 * <p>Define los metadatos generales (título, versión y descripción) que springdoc
 * publica como documento OpenAPI en {@code /v3/api-docs}. La interfaz de referencia
 * navegable la sirve Swagger UI en {@code /swagger-ui.html}, que consume ese mismo
 * documento. Las
 * descripciones de cada endpoint y de cada modelo se derivan automáticamente del
 * Javadoc del código gracias a therapi-runtime-javadoc, que springdoc detecta en el
 * classpath sin configuración adicional.</p>
 *
 * <p>Ambas rutas solo se publican en desarrollo. El interruptor {@code API_DOCS_ENABLED}
 * las gobierna a la vez y su valor por defecto lo fija el perfil activo: {@code local}
 * las publica y {@code production} las retira. Este bean se registra siempre, porque
 * springdoc solo lo usa cuando la documentación está publicada (ver A-004 en
 * {@code docs/AMBIGUIDADES.md}).</p>
 */
@Configuration
class OpenAPIConfiguration {

    /**
     * Construye los metadatos generales del documento OpenAPI.
     *
     * @return documento OpenAPI con la información pública del microservicio
     */
    @Bean
    OpenAPI entrevistaOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("CAMEIA - Microservicio de Entrevista 🐘")
                .version("v1")
                .description("API interna para configurar y gestionar sesiones de práctica y "
                        + "simulación de entrevistas. Solo es accesible a través del API Gateway."));
    }
}
