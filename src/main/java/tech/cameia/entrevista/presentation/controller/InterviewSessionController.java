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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.entrevista.application.command.CreateSessionCommand;
import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.service.CreateSessionService;
import tech.cameia.entrevista.application.service.DeleteSessionService;
import tech.cameia.entrevista.application.service.GetSessionStateService;
import tech.cameia.entrevista.application.service.StartSessionService;
import tech.cameia.entrevista.domain.exception.IdentityRequiredException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.presentation.dto.CreateSessionRequest;
import tech.cameia.entrevista.presentation.dto.CreateSessionResponse;
import tech.cameia.entrevista.presentation.dto.SessionStateResponse;

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

    private static final String PROBLEM = "application/problem+json";

    private static final String EXAMPLE_STATE = """
            {"sessionId":"3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10","status":"EN_CURSO","answerFormat":"TEXTO","interviewFinished":false,
             "progress":{"currentQuestion":1,"totalQuestions":5},
             "pendingQuestion":{"turnId":"9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12","number":1,
               "text":"Cuéntame brevemente quién eres y por qué te interesa este rol."},
             "history":[]}""";

    private static final String EXAMPLE_START_NOT_FOUND = """
            {"title":"Entrevista no encontrada","status":404,"detail":"No encontramos lo que buscabas.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/start","code":"SESSION_NOT_FOUND",
             "requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_NOT_ALLOWED = """
            {"title":"Acción no permitida","status":409,"detail":"Esta entrevista ya no admite esa acción.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/start",
             "code":"SESSION_TRANSITION_NOT_ALLOWED","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_LLM = """
            {"title":"Servicio no disponible","status":503,"detail":"Ocurrió un error. Inténtalo de nuevo.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/start","code":"LLM_UNAVAILABLE",
             "requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_EXHAUSTED = """
            {"title":"Preparación no disponible","status":503,
             "detail":"No pudimos preparar tu entrevista en este momento. Inténtalo más tarde.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/start",
             "code":"PLANNING_RETRIES_EXHAUSTED","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private final CreateSessionService createSession;
    private final StartSessionService startSession;
    private final GetSessionStateService sessionState;
    private final DeleteSessionService deleteSession;

    /**
     * Crea el controlador.
     *
     * @param createSession caso de uso que crea o reutiliza la sesión configurada
     * @param startSession caso de uso que inicia la entrevista
     * @param sessionState caso de uso que consulta el estado
     * @param deleteSession caso de uso que cancela la preparación
     */
    InterviewSessionController(CreateSessionService createSession, StartSessionService startSession,
            GetSessionStateService sessionState, DeleteSessionService deleteSession) {
        this.createSession = createSession;
        this.startSession = startSession;
        this.sessionState = sessionState;
        this.deleteSession = deleteSession;
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
                    + " SESSION_ALREADY_EXISTS (dos creaciones simultáneas) o SESSION_CONFLICT (la IA está preparando las"
                    + " preguntas de la sesión que se reutilizaría: esperar a que termine e intentar de nuevo)",
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

    /**
     * Inicia la entrevista: planifica las cinco preguntas con la IA y pasa la sesión a en curso.
     *
     * @param userId identidad del Gateway
     * @param sessionId identificador de la sesión, tal como llegó en la ruta
     * @return el estado de la sesión
     */
    @Operation(summary = "Iniciar la entrevista",
            description = "Pide a la IA las cinco preguntas de la sesión configurada, las valida y, si todo es correcto,"
                    + " las guarda, crea el turno de la primera pregunta, abandona la entrevista que la persona tuviera"
                    + " en curso y pasa esta a EN_CURSO. Es síncrono: tarda hasta el tiempo máximo del modelo (20 s)."
                    + " Si la sesión ya estaba EN_CURSO responde 200 con su estado, sin llamar a la IA. Si la IA falla,"
                    + " la sesión sigue CONFIGURADA y se puede repetir la llamada, hasta tres intentos en total."
                    + " Ignora el cuerpo y la cabecera Idempotency-Key.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrevista iniciada, o ya estaba en curso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SessionStateResponse.class),
                            examples = @ExampleObject(name = "enCurso", value = EXAMPLE_STATE))),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "sinIdentidad", value = EXAMPLE_401))),
            @ApiResponse(responseCode = "404", description = "La sesión no existe, es de otra persona o el identificador"
                    + " no es un UUID (code SESSION_NOT_FOUND, el mismo cuerpo en los tres casos)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "noEncontrada", value = EXAMPLE_START_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "SESSION_TRANSITION_NOT_ALLOWED (la sesión está EVALUANDO,"
                    + " FINALIZADA o ABANDONADA) o SESSION_CONFLICT (otra petición prepara las preguntas, o la sesión"
                    + " cambió: reintentar una vez)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "yaNoAdmiteLaAccion", value = EXAMPLE_NOT_ALLOWED))),
            @ApiResponse(responseCode = "503", description = "LLM_UNAVAILABLE, LLM_LIMIT_REACHED o LLM_INVALID_OUTPUT"
                    + " (se muestran igual, con Reintentar y Cancelar); PLANNING_RETRIES_EXHAUSTED tras el tercer fallo"
                    + " (solo Cancelar); PROFILE_SNAPSHOT_INVALID si el perfil congelado en la sesión es demasiado grande o no"
                    + " trae el rol (no gasta intentos: la persona debe actualizar su perfil y configurar de nuevo);"
                    + " DATABASE_UNAVAILABLE si la base no responde",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                @ExampleObject(name = "iaNoDisponible", value = EXAMPLE_LLM),
                                @ExampleObject(name = "reintentosAgotados", value = EXAMPLE_EXHAUSTED)})),
            @ApiResponse(responseCode = "500", description = "Error imprevisto (code INTERNAL_ERROR)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "falloInterno", value = EXAMPLE_500)))
    })
    @PostMapping(path = "/{sessionId}/start", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SessionStateResponse> start(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId) {
        FirebaseUid owner = FirebaseUid.required(userId);
        return ResponseEntity.ok(SessionStateResponse.from(
                startSession.start(SessionCommand.of(sessionId, owner))));
    }

    /**
     * Consulta el estado de una sesión propia.
     *
     * @param userId identidad del Gateway
     * @param sessionId identificador de la sesión, tal como llegó en la ruta
     * @return el estado de la sesión
     */
    @Operation(summary = "Consultar el estado de la sesión",
            description = "Devuelve el estado, el avance y, si la sesión está EN_CURSO, la pregunta pendiente con el"
                    + " turnId al que se responde. Solo lectura: no cambia el estado ni cuenta como actividad. Nunca"
                    + " incluye retroalimentación.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de la sesión", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = SessionStateResponse.class),
                    examples = @ExampleObject(name = "enCurso", value = EXAMPLE_STATE))),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "sinIdentidad", value = EXAMPLE_401))),
            @ApiResponse(responseCode = "404", description = "La sesión no existe, es de otra persona o el identificador"
                    + " no es un UUID (code SESSION_NOT_FOUND)", content = @Content(mediaType = PROBLEM,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "noEncontrada", value = EXAMPLE_START_NOT_FOUND))),
            @ApiResponse(responseCode = "503", description = "Base de datos no disponible (code DATABASE_UNAVAILABLE)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "baseCaida", value = EXAMPLE_503))),
            @ApiResponse(responseCode = "500", description = "Error imprevisto (code INTERNAL_ERROR)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "falloInterno", value = EXAMPLE_500)))
    })
    @GetMapping(path = "/{sessionId}/state", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SessionStateResponse> state(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId) {
        FirebaseUid owner = FirebaseUid.required(userId);
        return ResponseEntity.ok(SessionStateResponse.from(
                sessionState.state(SessionCommand.of(sessionId, owner))));
    }

    /**
     * Cancela la preparación: borra una sesión propia que sigue configurada.
     *
     * @param userId identidad del Gateway
     * @param sessionId identificador de la sesión, tal como llegó en la ruta
     * @return 204 sin cuerpo
     */
    @Operation(summary = "Cancelar la preparación de la entrevista",
            description = "Elimina la sesión propia que sigue CONFIGURADA, con sus ejecuciones de la IA. Una sesión que"
                    + " ya empezó o terminó no se puede borrar (409) y se conserva todo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión eliminada"),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "sinIdentidad", value = EXAMPLE_401))),
            @ApiResponse(responseCode = "404", description = "La sesión no existe, es de otra persona o el identificador"
                    + " no es un UUID (code SESSION_NOT_FOUND)", content = @Content(mediaType = PROBLEM,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "noEncontrada", value = EXAMPLE_START_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "La sesión no está CONFIGURADA"
                    + " (code SESSION_TRANSITION_NOT_ALLOWED)", content = @Content(mediaType = PROBLEM,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "yaNoAdmiteLaAccion", value = EXAMPLE_NOT_ALLOWED))),
            @ApiResponse(responseCode = "503", description = "Base de datos no disponible (code DATABASE_UNAVAILABLE)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "baseCaida", value = EXAMPLE_503))),
            @ApiResponse(responseCode = "500", description = "Error imprevisto (code INTERNAL_ERROR)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "falloInterno", value = EXAMPLE_500)))
    })
    @DeleteMapping("/{sessionId}")
    ResponseEntity<Void> delete(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId) {
        FirebaseUid owner = FirebaseUid.required(userId);
        deleteSession.delete(SessionCommand.of(sessionId, owner));
        return ResponseEntity.noContent().build();
    }
}
