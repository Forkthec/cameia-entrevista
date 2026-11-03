package tech.cameia.entrevista.presentation.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;

/**
 * Respuesta de {@code GET /api/v1/interviews/config/catalog}: las cuatro listas de opciones de la entrevista.
 * Las etiquetas visibles no viajan: el cliente las toma de su catálogo de textos con el {@code value}.
 *
 * @param modes         modalidades en orden
 * @param tones         tonos del entrevistador en orden
 * @param answerFormats formas de respuesta en orden
 * @param languages     idiomas de la sesión en orden
 */
@Schema(description = "Catálogo de personalización de la entrevista")
public record PersonalizationCatalogResponse(
        @ArraySchema(arraySchema = @Schema(description = "Modalidades en orden. Valores: SIMULACION, ENTRENO (hoy no disponible)"))
        List<CatalogOptionResponse> modes,
        @ArraySchema(arraySchema = @Schema(description = "Tonos del entrevistador en orden. Valores: PROFESIONAL_NEUTRO, CALIDO, ESTRICTO, MOTIVADOR"))
        List<CatalogOptionResponse> tones,
        @ArraySchema(arraySchema = @Schema(description = "Formas de respuesta en orden. Valores: AUDIO, TEXTO"))
        List<CatalogOptionResponse> answerFormats,
        @ArraySchema(arraySchema = @Schema(description = "Idiomas de la sesión en orden. Valores: es-CO, en"))
        List<CatalogOptionResponse> languages) {

    /**
     * Traduce el catálogo del dominio al contrato, conservando el orden de cada lista.
     *
     * @param catalog catálogo del dominio
     * @return la respuesta para el cliente
     */
    public static PersonalizationCatalogResponse from(PersonalizationCatalog catalog) {
        return new PersonalizationCatalogResponse(
                catalog.modes().stream().map(o -> CatalogOptionResponse.of(o, o.value().code())).toList(),
                catalog.tones().stream().map(o -> CatalogOptionResponse.of(o, o.value().code())).toList(),
                catalog.answerFormats().stream().map(o -> CatalogOptionResponse.of(o, o.value().code())).toList(),
                catalog.languages().stream().map(o -> CatalogOptionResponse.of(o, o.value().code())).toList());
    }
}
