package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;

class GetPersonalizationCatalogServiceTest {

    @Test
    @DisplayName("El caso de uso devuelve el catálogo único del servicio")
    void execute_shouldReturnInjectedCatalog() {
        PersonalizationCatalog catalog = new PersonalizationCatalog();

        GetPersonalizationCatalogService service = new GetPersonalizationCatalogService(catalog);

        assertThat(service.execute()).isSameAs(catalog);
    }
}
