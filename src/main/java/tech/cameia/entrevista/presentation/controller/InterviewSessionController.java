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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.entrevista.application.command.CreateSessionCommand;
import tech.cameia.entrevista.application.service.CreateSessionService;
import tech.cameia.entrevista.domain.exception.IdentityRequiredException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.presentation.dto.CreateSessionRequest;
import tech.cameia.entrevista.presentation.dto.CreateSessionResponse;

/**
 * Adaptador de entrada HTTP de las sesiones de entrevista.
 *
 * <p>La identidad llega solo en {@code X-User-Id}, que pone el Gateway; el dueño de la sesión nunca sale del
 * cuerpo. La validación de los datos es del dominio, no del contrato HTTP: por eso el cuerpo no lleva
 * {@code @Valid} y todos los errores de los campos salen juntos.</p>
 */
@Tag(name = "Sesiones de entrevista", description = "Preparación y estado de las sesiones de práctica")
@RestController
@RequestMapping("/api/v1/interviews/sessions")
class InterviewSessionController {

    private static final String EXAMPLE_201 = """
            {"sessionId":"3b1f8f2a-6c1e-4d8b-9f0a-2c3d4e5f6a7b","status":"CONFIGURADA",
             "configuration":{"profileId":"7c9e6679-7425-40de-944b-e07fc1f90ae7",
               "targetRoleId":"a0000001-0000-0000-0000-000000000002","jobOffer":"Buscamos desarrollador backend con Java",
               "mode":"SIMULACION","tone":"PROFESIONAL_NEUTRO","answerFormat":"AUDIO","sessionLanguage":"es-CO"},
             "ongoingSession":{"sessionId":"0e2d4c1a-8b3f-4a6e-9d21-5c7b8a9f0e1d","answersCount":2}}""";

    private static final String EXAMPLE_401 = """
            {"title":"Identidad requerida","status":401,"detail":"Tu sesión expiró. Inicia sesión de nuevo.",
             "instance":"/api/v1/interviews/sessions","code":"IDENTITY_REQUIRED","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_404 = """
            {"title":"Perfil no encontrado","status":404,"detail":"No encontramos lo que buscabas.",
             "instance":"/api/v1/interviews/sessions","code":"PROFILE_NOT_FOUND","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_409_SYNC = """
            {"title":"Perfil en actualización","status":409,"detail":"Tu perfil se está actualizando. Inténtalo en unos segundos.",
             "instance":"/api/v1/interviews/sessions","code":"PROFILE_SYNC_PENDING","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_409_KEY = """
            {"title":"Solicitud distinta","status":409,"detail":"Esta solicitud no coincide con la que enviaste antes. Inténtalo de nuevo.",
             "instance":"/api/v1/interviews/sessions","code":"IDEMPOTENCY_KEY_CONFLICT","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_409_EXISTS = """
            {"title":"Entrevista existente","status":409,"detail":"Ya tienes una entrevista en preparación o en curso. Inténtalo de nuevo.",
             "instance":"/api/v1/interviews/sessions","code":"SESSION_ALREADY_EXISTS","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_415 = """
            {"title":"Tipo de contenido no admitido","status":415,"detail":"Tipo de contenido no admitido.",
             "instance":"/api/v1/interviews/sessions","code":"MEDIA_TYPE_NOT_ALLOWED","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_422 = """
            {"title":"Datos no válidos","status":422,"detail":"Revisa los campos marcados.",
             "instance":"/api/v1/interviews/sessions","code":"VALIDATION_FAILED","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35",
             "errors":[{"field":"mode","code":"MODE_NOT_ALLOWED","message":"Esta modalidad estará disponible próximamente."},
                       {"field":"tone","code":"TONE_REQUIRED","message":"Elige el tono del entrevistador."}]}""";

    private static final String EXAMPLE_503 = """
            {"title":"Servicio no disponible","status":503,"detail":"Ocurrió un error. Inténtalo de nuevo.",
             "instance":"/api/v1/interviews/sessions","code":"DATABASE_UNAVAILABLE","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_500 = """
            {"title":"Error interno","status":500,"detail":"Ocurrió un error. Inténtalo de nuevo.",
             "instance":"/api/v1/interviews/sessions","code":"INTERNAL_ERROR","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private final CreateSessionService createSession;

    /**
     * Crea el controlador.
     *
     * @param createSession caso de uso que crea o reutiliza la sesión configurada
     */
    InterviewSessionController(CreateSessionService createSession) {
        this.createSession = createSession;
    }

    /**
     * Crea la sesión configurada de la persona o reutiliza la que ya tenía.
     *
     * @param userId identidad que pone el Gateway; obligatoria
     * @param idempotencyKey clave opcional: repetirla con la misma configuración devuelve la misma respuesta
     * @param body configuración enviada, sin validar
     * @return 201 con la sesión nueva o 200 con la reutilizada
     * @throws IdentityRequiredException si falta la identidad o no es válida
     */
    @Operation(summary = "Crear o reutilizar la sesión configurada",
            description = "Valida la configuración (todos los errores a la vez), toma una copia del contexto del perfil"
                    + " y deja la sesión en CONFIGURADA. Si la persona ya tenía una sesión configurada, la actualiza"
                    + " (200, mismo sessionId). Informa la entrevista en curso, si hay, sin modificarla. No crea"
                    + " preguntas ni llama a la IA.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sesión nueva", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = CreateSessionResponse.class),
                    examples = @ExampleObject(name = "sesionNueva", value = EXAMPLE_201))),
            @ApiResponse(responseCode = "200", description = "Se reutilizó la sesión configurada, o se repitió la"
                    + " respuesta de una Idempotency-Key ya usada con la misma configuración", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = CreateSessionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "sinIdentidad", value = EXAMPLE_401))),
            @ApiResponse(responseCode = "404", description = "El perfil es de otra persona (code PROFILE_NOT_FOUND)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "perfilAjeno", value = EXAMPLE_404))),
            @ApiResponse(responseCode = "409", description = "PROFILE_SYNC_PENDING (el perfil aún no llegó o no está"
                    + " completo), IDEMPOTENCY_KEY_CONFLICT (la clave ya se usó con otra configuración),"
                    + " SESSION_ALREADY_EXISTS (dos creaciones simultáneas) o SESSION_CONFLICT",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                @ExampleObject(name = "perfilEnActualizacion", value = EXAMPLE_409_SYNC),
                                @ExampleObject(name = "claveConOtraConfiguracion", value = EXAMPLE_409_KEY),
                                @ExampleObject(name = "sesionExistente", value = EXAMPLE_409_EXISTS)})),
            @ApiResponse(responseCode = "415", description = "El Content-Type no es application/json"
                    + " (code MEDIA_TYPE_NOT_ALLOWED)", content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "tipoNoAdmitido", value = EXAMPLE_415))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED con un elemento por campo en errors,"
                    + " o REQUEST_BODY_INVALID_FORMAT si el cuerpo no es un JSON válido", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "camposInvalidos", value = EXAMPLE_422))),
            @ApiResponse(responseCode = "503", description = "Base de datos no disponible (code DATABASE_UNAVAILABLE)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "baseCaida", value = EXAMPLE_503))),
            @ApiResponse(responseCode = "500", description = "Error imprevisto (code INTERNAL_ERROR)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "falloInterno", value = EXAMPLE_500)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CreateSessionResponse> create(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Clave única por intento de crear la sesión: de 1 a 64 caracteres A-Z a-z 0-9 . _ -."
                    + " Repetirla con la misma configuración devuelve la misma respuesta; con otra, 409")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateSessionRequest body) {
        FirebaseUid owner = FirebaseUid.required(userId);
        var result = createSession.create(new CreateSessionCommand(owner, body.toDraft(idempotencyKey)));
        return ResponseEntity.status(result.httpStatus()).body(CreateSessionResponse.from(result));
    }
}
