package tech.cameia.entrevista.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.entrevista.application.service.GetPersonalizationCatalogService;
import tech.cameia.entrevista.domain.exception.IdentityRequiredException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.presentation.dto.PersonalizationCatalogResponse;

/**
 * Adaptador de entrada HTTP de la configuración de la entrevista.
 *
 * <p>La identidad llega solo en {@code X-User-Id}, que pone el Gateway; sin ella se responde 401. El
 * catálogo es el mismo para todos los usuarios y planes.</p>
 */
@Tag(name = "Configuración de la entrevista", description = "Opciones con que se personaliza una entrevista")
@RestController
@RequestMapping("/api/v1/interviews/config")
class InterviewConfigController {

    private final GetPersonalizationCatalogService getCatalog;

    /**
     * Crea el controlador.
     *
     * @param getCatalog caso de uso que consulta el catálogo
     */
    InterviewConfigController(GetPersonalizationCatalogService getCatalog) {
        this.getCatalog = getCatalog;
    }

    /**
     * Devuelve las modalidades, tonos, formas de respuesta e idiomas con su disponibilidad.
     *
     * @param userId identidad que pone el Gateway; obligatoria
     * @return 200 con el catálogo
     * @throws IdentityRequiredException si falta la identidad o no es válida
     */
    @Operation(summary = "Consultar el catálogo de personalización",
            description = "Opciones de la entrevista y su disponibilidad. Las mismas con que se valida la creación de la sesión.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = PersonalizationCatalogResponse.class),
                    examples = @ExampleObject(value = "{\"modes\":[{\"value\":\"SIMULACION\",\"available\":true},{\"value\":\"ENTRENO\",\"available\":false}],"
                            + "\"tones\":[{\"value\":\"PROFESIONAL_NEUTRO\",\"available\":true},{\"value\":\"CALIDO\",\"available\":true},"
                            + "{\"value\":\"ESTRICTO\",\"available\":true},{\"value\":\"MOTIVADOR\",\"available\":true}],"
                            + "\"answerFormats\":[{\"value\":\"AUDIO\",\"available\":true},{\"value\":\"TEXTO\",\"available\":true}],"
                            + "\"languages\":[{\"value\":\"es-CO\",\"available\":true},{\"value\":\"en\",\"available\":true}]}"))),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":401,\"code\":\"IDENTITY_REQUIRED\",\"detail\":\"Tu sesión expiró. Inicia sesión de nuevo.\",\"requestId\":\"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35\"}"))),
            @ApiResponse(responseCode = "405", description = "Método no admitido (code METHOD_NOT_ALLOWED)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":405,\"code\":\"METHOD_NOT_ALLOWED\",\"detail\":\"Método no permitido.\",\"requestId\":\"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35\"}"))),
            @ApiResponse(responseCode = "406", description = "Tipo de respuesta no admitido (code MEDIA_TYPE_NOT_ACCEPTABLE)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":406,\"code\":\"MEDIA_TYPE_NOT_ACCEPTABLE\",\"detail\":\"Tipo de respuesta no admitido.\",\"requestId\":\"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35\"}"))),
            @ApiResponse(responseCode = "500", description = "Error imprevisto (code INTERNAL_ERROR)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":500,\"code\":\"INTERNAL_ERROR\",\"detail\":\"Ocurrió un error. Inténtalo de nuevo.\",\"requestId\":\"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35\"}")))
    })
    @GetMapping(path = "/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PersonalizationCatalogResponse> catalog(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId) {
        FirebaseUid.required(userId); // solo exige que la petición venga del Gateway con un usuario
        return ResponseEntity.ok(PersonalizationCatalogResponse.from(getCatalog.execute()));
    }
}
