package tech.cameia.entrevista.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;
import tech.cameia.entrevista.domain.policy.PlanValidator;
import tech.cameia.entrevista.domain.policy.PlanningRetryPolicy;
import tech.cameia.entrevista.domain.policy.SessionConfigurationPolicy;
import tech.cameia.entrevista.domain.port.RoleCatalog;

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

    /**
     * Crea el validador del plan de preguntas que devuelve el modelo, que no guarda estado.
     *
     * @return el validador del plan de preguntas
     */
    @Bean
    PlanValidator planValidator() {
        return new PlanValidator();
    }

    /**
     * Crea la política de intentos de planificación de una sesión (el inicial y dos reintentos).
     *
     * @return la política de reintentos de la planificación
     */
    @Bean
    PlanningRetryPolicy planningRetryPolicy() {
        return new PlanningRetryPolicy();
    }

    /**
     * Crea el reloj del servicio, en UTC, que los casos de uso usan para fechar lo que escriben.
     * Las pruebas lo reemplazan por uno fijo.
     *
     * @return el reloj del sistema en UTC
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Crea la política que valida la configuración de la sesión contra el catálogo de personalización y la
     * copia local del catálogo de roles.
     *
     * @param catalog catálogo de personalización
     * @param roles copia local del catálogo de roles
     * @return la política de configuración de la sesión
     */
    @Bean
    SessionConfigurationPolicy sessionConfigurationPolicy(PersonalizationCatalog catalog, RoleCatalog roles) {
        return new SessionConfigurationPolicy(catalog, roles);
    }
}
