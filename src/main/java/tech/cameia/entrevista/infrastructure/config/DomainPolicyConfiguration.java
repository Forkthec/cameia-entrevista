package tech.cameia.entrevista.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;

/**
 * Ensambla como beans las reglas de dominio, que se construyen con {@code new} y no conocen Spring.
 */
@Configuration
class DomainPolicyConfiguration {

    /**
     * Crea el catálogo de personalización, que no guarda estado y se comparte en todo el servicio.
     *
     * @return el catálogo de personalización, único para todo el servicio
     */
    @Bean
    PersonalizationCatalog personalizationCatalog() {
        return new PersonalizationCatalog();
    }
}
