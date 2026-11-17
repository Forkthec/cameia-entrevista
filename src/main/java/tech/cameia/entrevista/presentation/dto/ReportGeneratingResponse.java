package tech.cameia.entrevista.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta 202: otra petición está generando el reporte en este momento.
 *
 * @param code siempre {@code REPORT_GENERATING}
 * @param message mensaje para la persona
 * @param requestId identificador de la petición, el mismo del encabezado {@code X-Request-Id}
 */
@Schema(description = "Otra petición está generando el reporte; se consulta de nuevo en unos segundos")
public record ReportGeneratingResponse(
        @Schema(allowableValues = "REPORT_GENERATING", example = "REPORT_GENERATING") String code,
        @Schema(example = "Tu reporte se está generando.") String message,
        @Schema(example = "3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35") String requestId) {

    /** Código estable de la respuesta. */
    public static final String CODE = "REPORT_GENERATING";

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu reporte se está generando.";

    /**
     * Arma la respuesta con el identificador de la petición.
     *
     * @param requestId identificador de la petición
     * @return la respuesta 202
     */
    public static ReportGeneratingResponse of(String requestId) {
        return new ReportGeneratingResponse(CODE, MESSAGE, requestId);
    }
}
