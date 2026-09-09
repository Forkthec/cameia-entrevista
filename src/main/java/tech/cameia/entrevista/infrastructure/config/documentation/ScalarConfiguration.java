package tech.cameia.entrevista.infrastructure.config.documentation;

import com.scalar.maven.webmvc.ScalarWebMvcAutoConfiguration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Activa la interfaz de referencia de API de Scalar.
 *
 * <p>La biblioteca {@code scalar-webmvc} se publica como autoconfiguración de Spring
 * Boot 3 y su clase está anotada con {@code @Configuration} en lugar de
 * {@code @AutoConfiguration}. Spring Boot 4 ignora esas entradas del archivo de
 * autoconfiguración, así que se importa la clase de forma explícita para registrar el
 * controlador que sirve Scalar.</p>
 *
 * <p>El comportamiento se ajusta con las propiedades {@code scalar.*} de
 * {@code application.properties}: {@code scalar.enabled}, {@code scalar.path}
 * ({@code /scalar}) y {@code scalar.url}, que apunta al documento OpenAPI publicado por
 * springdoc en {@code /v3/api-docs}.</p>
 */
@Configuration
@Import(ScalarWebMvcAutoConfiguration.class)
class ScalarConfiguration {
}
