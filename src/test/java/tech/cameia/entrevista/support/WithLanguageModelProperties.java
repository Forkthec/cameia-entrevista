package tech.cameia.entrevista.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.context.TestPropertySource;

/**
 * Da valores falsos a la configuración obligatoria del modelo de lenguaje en las pruebas que
 * arrancan el contexto completo.
 *
 * <p>Sin ellos el servicio no arranca: la llave, el modelo, el tiempo de espera, la
 * temperatura y el nivel de pensamiento son obligatorios. Ninguna prueba llama al proveedor, así
 * que la llave es un texto sin valor.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@TestPropertySource(properties = {
        "spring.ai.google.genai.api-key=clave-falsa-de-prueba",
        "cameia.llm.api-key=clave-falsa-de-prueba",
        "cameia.llm.model=modelo-de-prueba",
        "cameia.llm.timeout=30s",
        "cameia.llm.temperature=1.0",
        "cameia.llm.thinking-level=low"
})
public @interface WithLanguageModelProperties {
}
