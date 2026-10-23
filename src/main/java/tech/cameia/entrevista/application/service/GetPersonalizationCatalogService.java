package tech.cameia.entrevista.application.service;

import org.springframework.stereotype.Service;

import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;

/**
 * Caso de uso: consultar el catálogo de personalización de la entrevista.
 *
 * <p>No depende del usuario ni de su plan: todos ven las mismas opciones. No toca la base ni otros
 * servicios, así que responde aunque la base no esté disponible.</p>
 */
@Service
public class GetPersonalizationCatalogService {

    private final PersonalizationCatalog catalog;

    /**
     * Crea el caso de uso.
     *
     * @param catalog catálogo único del servicio
     */
    public GetPersonalizationCatalogService(PersonalizationCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Devuelve el catálogo.
     *
     * @return el catálogo con sus opciones y su disponibilidad
     */
    public PersonalizationCatalog execute() {
        return catalog;
    }
}
