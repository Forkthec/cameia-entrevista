package tech.cameia.entrevista.presentation.controller;

import java.io.IOException;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import tech.cameia.entrevista.application.command.SubmitAudioAnswerCommand;
import tech.cameia.entrevista.application.service.SubmitAudioAnswerService;
import tech.cameia.entrevista.domain.exception.IdentityRequiredException;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.presentation.dto.AnswerResponse;

/**
 * Adaptador de entrada HTTP de las respuestas por voz de una entrevista en curso.
 *
 * <p>El audio se lee a memoria y nunca se escribe en disco: el límite de multipart deja todo el archivo en memoria
 * ({@code spring.servlet.multipart.file-size-threshold}) y el contenedor lo libera al terminar la petición.</p>
 */
@Tag(name = "Respuestas", description = "Respuestas a las preguntas de una entrevista en curso")
@RestController
@RequestMapping("/api/v1/interviews/sessions/{sessionId}/answers")
class InterviewAnswerController {

    private static final String PROBLEM = "application/problem+json";

    private static final String EXAMPLE_200 = """
            {"turn":{"turnId":"9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12","status":"COMPLETED"},
             "progress":{"currentQuestion":2,"totalQuestions":5},
             "nextQuestion":{"turnId":"1c7f0a55-2b8e-4d61-9a03-7e5b4c3d2f10","number":2,
               "text":"Cuéntame de un conflicto con un compañero y cómo lo resolviste."},
             "interviewFinished":false}""";

    private static final String EXAMPLE_409 = """
            {"title":"Respuesta en proceso","status":409,
             "detail":"Ya estamos procesando tu respuesta a esta pregunta. Espera un momento.",
             "instance":"/api/v1/interviews/sessions/3b1f8f2a-6c1e-4d8b-9f0a-2c3d4e5f6a7b/answers/audio",
             "code":"TURN_PROCESSING","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_503 = """
            {"title":"Transcripción no disponible","status":503,
             "detail":"No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.",
             "instance":"/api/v1/interviews/sessions/3b1f8f2a-6c1e-4d8b-9f0a-2c3d4e5f6a7b/answers/audio",
             "code":"TRANSCRIPTION_UNAVAILABLE","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private final SubmitAudioAnswerService submitAudio;

    /**
     * Crea el controlador.
     *
     * @param submitAudio caso de uso que transcribe y registra la respuesta por voz
     */
    InterviewAnswerController(SubmitAudioAnswerService submitAudio) {
        this.submitAudio = submitAudio;
    }

    /**
     * Transcribe el audio de la pregunta pendiente y registra la respuesta.
     *
     * @param userId identidad que pone el Gateway; obligatoria
     * @param sessionId identificador de la sesión
     * @param turnId turno pendiente que se responde
     * @param audio grabación de la respuesta
     * @return 200 con el turno respondido, el avance y la pregunta siguiente
     * @throws IdentityRequiredException si falta la identidad o no es válida
     */
    @Operation(summary = "Responder por voz la pregunta pendiente",
            description = "Transcribe el audio con el servicio de Voz y registra una sola respuesta definitiva."
                    + " Si el primer intento falla por un error transitorio (Voz responde 503 o rechaza la conexión),"
                    + " reintenta una vez sin que la persona haga nada. Si el segundo intento falla, o el primero"
                    + " agota su tiempo de espera (60 s), no registra nada, deja la pregunta pendiente y responde"
                    + " 503 TRANSCRIPTION_UNAVAILABLE. Mientras se transcribe, otro envío del mismo turno recibe 409"
                    + " TURN_PROCESSING. El audio no se guarda.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta registrada", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = AnswerResponse.class),
                    examples = @ExampleObject(name = "registrada", value = EXAMPLE_200))),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Sesión ajena o inexistente (SESSION_NOT_FOUND), o turno"
                    + " que no es de la sesión (TURN_NOT_FOUND)", content = @Content(mediaType = PROBLEM,
                    schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "TURN_PROCESSING (otro audio del mismo turno se está"
                    + " transcribiendo), TURN_ALREADY_ANSWERED, SESSION_TRANSITION_NOT_ALLOWED (la sesión no está en"
                    + " curso) o SESSION_CONFLICT", content = @Content(mediaType = PROBLEM,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "enProceso", value = EXAMPLE_409))),
            @ApiResponse(responseCode = "413", description = "Audio de más de 10 MB (REQUEST_PAYLOAD_TOO_LARGE)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED (falta turnId o audio),"
                    + " TRANSCRIPTION_EMPTY (no se detectó voz) o TRANSCRIPTION_TOO_LONG", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "TRANSCRIPTION_UNAVAILABLE (intentos agotados) o"
                    + " DATABASE_UNAVAILABLE", content = @Content(mediaType = PROBLEM,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "intentosAgotados", value = EXAMPLE_503)))
    })
    @PostMapping(path = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    AnswerResponse submitAudio(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId,
            @Parameter(description = "Turno pendiente que se responde (UUID)")
            @RequestParam(value = "turnId", required = false) String turnId,
            @Parameter(description = "Grabación de la respuesta (hasta 10 MB)")
            @RequestPart(value = "audio", required = false) MultipartFile audio) {
        FirebaseUid owner = FirebaseUid.required(userId);
        return AnswerResponse.from(submitAudio.submit(
                SubmitAudioAnswerCommand.of(sessionId, owner, turnId, toClip(audio))));
    }

    private static AudioClip toClip(MultipartFile audio) {
        if (audio == null) {
            return null;
        }
        try {
            return new AudioClip(audio.getBytes(), audio.getContentType());
        } catch (IOException unreadable) {
            throw new MultipartException("No se pudo leer el audio", unreadable);
        }
    }
}
