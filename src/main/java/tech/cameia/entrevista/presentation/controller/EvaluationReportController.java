package tech.cameia.entrevista.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.service.GetEvaluationReportService;
import tech.cameia.entrevista.application.service.ReportOutcome;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.presentation.dto.EvaluationReportResponse;
import tech.cameia.entrevista.presentation.dto.ReportGeneratingResponse;
import tech.cameia.entrevista.presentation.filter.RequestIdFilter;

/**
 * Consulta del Reporte de Evaluación de una entrevista finalizada.
 *
 * <p>La identidad llega solo en {@code X-User-Id}, que pone el Gateway; el plan del usuario no se lee: todos los
 * planes reciben el mismo cuerpo. El reporte se genera en la primera consulta y es el mismo en las siguientes.</p>
 */
@Tag(name = "Reporte de Evaluación", description = "Resultado de una entrevista finalizada")
@RestController
@RequestMapping("/api/v1/interviews/sessions")
class EvaluationReportController {

    private static final String PROBLEM = "application/problem+json";

    private static final String EXAMPLE_200 = """
            {"sessionId":"3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10","issuedAt":"2026-11-17T15:04:05Z",
             "sessionLanguage":"es-CO","interviewDate":"2026-11-17T14:58:40Z","tone":"PROFESIONAL_NEUTRO",
             "metrics":[
               {"code":"PRECISION_CLARITY","group":"CONTENT","value":3.46,"unit":"SCORE_1_5"},
               {"code":"VERBAL_CHARISMA","group":"CONTENT","value":4.00,"unit":"SCORE_1_5"},
               {"code":"INCLUSIVE_LANGUAGE","group":"CONTENT","value":5.00,"unit":"SCORE_1_5"},
               {"code":"SPEECH_RATE","group":"VOICE","value":120,"unit":"WORDS_PER_MINUTE"},
               {"code":"PAUSE_TIME","group":"VOICE","value":12.0,"unit":"PERCENT"},
               {"code":"HESITATION_RATE","group":"VOICE","value":2.0,"unit":"PER_100_WORDS"},
               {"code":"ENTHUSIASM","group":"VOICE","value":null,"unit":null}],
             "star":[{"questionNumber":3,"questionText":"Cuéntame de una vez que resolviste un conflicto en equipo.",
               "situation":true,"task":true,"action":true,"result":false,"level":3}],
             "questions":[{"number":3,"type":"CONDUCTUAL",
               "questionText":"Cuéntame de una vez que resolviste un conflicto en equipo.",
               "answerText":"En el proyecto final de redes, dos compañeros querían usar tecnologías distintas…",
               "answerOrigin":"TEXT","voice":null}]}""";

    private static final String EXAMPLE_202 = """
            {"code":"REPORT_GENERATING","message":"Tu reporte se está generando.",
             "requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_401 = """
            {"title":"Identidad requerida","status":401,"detail":"Tu sesión expiró. Inicia sesión de nuevo.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/evaluation-report","code":"IDENTITY_REQUIRED","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_404 = """
            {"title":"Entrevista no encontrada","status":404,"detail":"No encontramos lo que buscabas.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/evaluation-report","code":"SESSION_NOT_FOUND","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_409_NOT_FINISHED = """
            {"title":"Entrevista sin terminar","status":409,"detail":"Tu entrevista aún no ha terminado.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/evaluation-report","code":"SESSION_NOT_FINISHED","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_409_ABANDONED = """
            {"title":"Entrevista abandonada","status":409,"detail":"Sesión abandonada: no genera evaluación.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/evaluation-report","code":"SESSION_ABANDONED","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_503 = """
            {"title":"Servicio no disponible","status":503,"detail":"Ocurrió un error. Inténtalo de nuevo.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/evaluation-report","code":"DATABASE_UNAVAILABLE","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private final GetEvaluationReportService service;

    EvaluationReportController(GetEvaluationReportService service) {
        this.service = service;
    }

    /**
     * Devuelve el reporte de una entrevista finalizada, generándolo en la primera consulta.
     *
     * @param userId identidad del Gateway
     * @param sessionId identificador de la sesión, tal como llegó en la ruta
     * @param request petición, de donde sale el identificador de trazabilidad del 202
     * @return 200 con el reporte, o 202 si otra petición lo está generando
     */
    @Operation(summary = "Consultar el Reporte de Evaluación",
            description = "Devuelve el reporte de una entrevista FINALIZADA con sus siete métricas, el STAR de las"
                    + " preguntas conductuales y las cinco preguntas con sus respuestas. La primera consulta lo genera"
                    + " con la IA (puede tardar hasta un minuto); si la IA no responde, el reporte se emite igual con"
                    + " las métricas de contenido y el STAR en null y las de voz calculadas. El reporte no cambia: cada"
                    + " consulta devuelve el mismo cuerpo. Un value null significa «sin resultado», nunca 0. Si otra"
                    + " petición lo está generando responde 202 y se consulta de nuevo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte emitido", content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = EvaluationReportResponse.class),
                    examples = @ExampleObject(name = "reporte", value = EXAMPLE_200))),
            @ApiResponse(responseCode = "202", description = "Otra petición está generando el reporte"
                    + " (code REPORT_GENERATING); se consulta de nuevo en unos segundos", content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ReportGeneratingResponse.class),
                    examples = @ExampleObject(name = "generando", value = EXAMPLE_202))),
            @ApiResponse(responseCode = "401", description = "Falta la identidad del Gateway (code IDENTITY_REQUIRED)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "sinIdentidad", value = EXAMPLE_401))),
            @ApiResponse(responseCode = "404", description = "La sesión no existe, es de otra persona o el"
                    + " identificador no es un UUID (code SESSION_NOT_FOUND)", content = @Content(mediaType = PROBLEM,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "noEncontrada", value = EXAMPLE_404))),
            @ApiResponse(responseCode = "409", description = "La sesión no tiene reporte: sigue configurada, en curso o"
                    + " evaluándose (code SESSION_NOT_FINISHED) o está abandonada (code SESSION_ABANDONED)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                    @ExampleObject(name = "sinTerminar", value = EXAMPLE_409_NOT_FINISHED),
                                    @ExampleObject(name = "abandonada", value = EXAMPLE_409_ABANDONED)})),
            @ApiResponse(responseCode = "503", description = "Base de datos no disponible (code DATABASE_UNAVAILABLE)"
                    + " o la copia del perfil de la sesión no se puede leer (code PROFILE_SNAPSHOT_INVALID)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "baseCaida", value = EXAMPLE_503)))
    })
    @GetMapping(path = "/{sessionId}/evaluation-report", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> report(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId,
            @Parameter(hidden = true) HttpServletRequest request) {
        FirebaseUid owner = FirebaseUid.required(userId);
        ReportOutcome outcome = service.report(SessionCommand.of(sessionId, owner));
        if (outcome instanceof ReportOutcome.Issued issued) {
            return ResponseEntity.ok(EvaluationReportResponse.from(issued));
        }
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ReportGeneratingResponse.of(requestId));
    }
}
