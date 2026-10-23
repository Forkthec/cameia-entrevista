package tech.cameia.entrevista.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.domain.model.CatalogOption;

/**
 * Una opción del catálogo tal como la recibe el cliente.
 *
 * @param value     valor que el cliente envía al crear la sesión y que usa para buscar su etiqueta
 * @param available si hoy se puede elegir; {@code false} se muestra deshabilitada con «Próximamente»
 */
@Schema(description = "Opción de personalización con su disponibilidad")
public record CatalogOptionResponse(
        @Schema(description = "Valor de la opción; el cliente lo envía al crear la sesión", example = "SIMULACION",
                requiredMode = Schema.RequiredMode.REQUIRED) String value,
        @Schema(description = "Si hoy se puede elegir; false = «Próximamente»", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean available) {

    /**
     * Traduce una opción del dominio al contrato.
     *
     * @param option opción del dominio
     * @param value  valor del enumerado (su {@code code()})
     * @return la opción para el contrato
     */
    static CatalogOptionResponse of(CatalogOption<?> option, String value) {
        return new CatalogOptionResponse(value, option.available());
    }
}
