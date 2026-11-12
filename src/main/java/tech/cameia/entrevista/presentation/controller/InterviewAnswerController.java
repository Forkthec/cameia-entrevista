package tech.cameia.entrevista.presentation.controller;

import java.io.IOException;
import java.io.UncheckedIOException;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.command.SubmitAudioAnswerCommand;
import tech.cameia.entrevista.application.service.SubmitAudioAnswerService;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.presentation.dto.AnswerResponse;

/**
 * Adaptador de entrada HTTP de las respuestas de la entrevista.
 *
 * <p>Del archivo recibido solo se leen los bytes: el nombre y el tipo declarado se ignoran (CA-5.8.16, CA-5.8.15). Ni
 * este controlador ni el servicio exponen una ruta para recuperar un audio (CA-5.8.17).</p>
 */
@Tag(name = "Respuestas de la entrevista", description = "Registro de las respuestas a las preguntas")
@RestController
@RequestMapping("/api/v1/interviews/sessions")
class InterviewAnswerController {

    private static final String PROBLEM = "application/problem+json";

    private static final String EXAMPLE_200 = """
            {"turn":{"turnId":"9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12","status":"COMPLETED"},
             "progress":{"currentQuestion":2,"totalQuestions":5},
             "nextQuestion":{"turnId":"4c1d2e3f-5a6b-4c7d-8e9f-0a1b2c3d4e5f","number":2,
               "text":"Cuéntame de una situación en la que resolviste un conflicto en tu equipo."},
             "interviewFinished":false}""";

    private static final String EXAMPLE_413 = """
            {"title":"Audio demasiado grande","status":413,"detail":"Tu audio no puede superar los 10 MB.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/answers/audio",
             "code":"AUDIO_TOO_LARGE","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_415 = """
            {"title":"Formato de audio no admitido","status":415,
             "detail":"Este formato de audio no es compatible. Graba de nuevo o responde por texto.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/answers/audio",
             "code":"AUDIO_INVALID_FORMAT","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_422 = """
            {"title":"Datos no válidos","status":422,"detail":"Tu respuesta no puede durar más de 3 minutos.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/answers/audio",
             "code":"AUDIO_TOO_LONG","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private final SubmitAudioAnswerService submitAudio;

    InterviewAnswerController(SubmitAudioAnswerService submitAudio) {
        this.submitAudio = submitAudio;
    }

    /**
     * Registra la respuesta por voz a la pregunta pendiente.
     *
     * @param userId identidad del Gateway
     * @param idempotencyKey clave anti-duplicados, opcional
     * @param sessionId sesión, tal como llegó en la ruta
     * @param turnId turno pendiente, tal como llegó en el formulario
     * @param audio grabación
     * @return el turno respondido, el avance y la pregunta siguiente
     */
    @Operation(summary = "Responder por voz",
            description = "Recibe la grabación (multipart: turnId y audio), revisa su contenido real (WebM con Opus o"
                    + " MP4 con AAC, solo audio, hasta 10 MiB y 3 minutos), la transcribe sin guardarla y registra la"
                    + " respuesta como AUDIO. Avanza a la pregunta siguiente o, tras la quinta, deja la entrevista en"
                    + " EVALUANDO. Repetir la misma Idempotency-Key con el mismo audio devuelve la misma respuesta sin"
                    + " transcribir de nuevo. El audio no se guarda en disco, en un bucket ni en el log; el nombre del"
                    + " archivo se ignora. Puede tardar hasta el tiempo máximo de la transcripción (90 s).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta registrada", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = AnswerResponse.class),
                    examples = @ExampleObject(name = "siguientePregunta", value = EXAMPLE_200))),
            @ApiResponse(responseCode = "401", description = "Sin identidad (IDENTITY_REQUIRED)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "SESSION_NOT_FOUND (sesión ajena, inexistente o mal"
                    + " escrita) o TURN_NOT_FOUND (el turno no es de la sesión)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "SESSION_ABANDONED, SESSION_ALREADY_CLOSED (EVALUANDO o"
                    + " FINALIZADA), SESSION_TRANSITION_NOT_ALLOWED (CONFIGURADA), TURN_TRANSITION_NOT_ALLOWED (la"
                    + " pregunta ya se respondió), SESSION_CONFLICT (el mismo turno se está procesando) o"
                    + " IDEMPOTENCY_KEY_CONFLICT (la clave ya se usó con otro audio)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "413", description = "AUDIO_TOO_LARGE (más de 10 485 760 bytes) o"
                    + " REQUEST_PAYLOAD_TOO_LARGE (petición de más de 12 MiB)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "audioGrande", value = EXAMPLE_413))),
            @ApiResponse(responseCode = "415", description = "AUDIO_INVALID_FORMAT (el contenido real no es WebM/Opus ni"
                    + " MP4/AAC, o trae video) o REQUEST_MULTIPART_INVALID_FORMAT", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "formatoNoAdmitido", value = EXAMPLE_415))),
            @ApiResponse(responseCode = "422", description = "AUDIO_NOT_ALLOWED (entrevista por texto), AUDIO_REQUIRED,"
                    + " AUDIO_TOO_LONG (más de 3 minutos), AUDIO_TOO_SHORT, TRANSCRIPTION_EMPTY,"
                    + " TRANSCRIPTION_TOO_LONG o VALIDATION_FAILED (Idempotency-Key mal formada)", content = @Content(
                    mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "audioLargo", value = EXAMPLE_422))),
            @ApiResponse(responseCode = "503", description = "TRANSCRIPTION_UNAVAILABLE o DATABASE_UNAVAILABLE: la"
                    + " pregunta sigue pendiente", content = @Content(mediaType = PROBLEM,
                    schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(path = "/{sessionId}/answers/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AnswerResponse> submitAudio(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Clave única por turno: de 1 a 64 caracteres A-Z a-z 0-9 . _ -. Repetirla con el"
                    + " mismo audio devuelve la misma respuesta; con otro, 409")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId,
            @Parameter(description = "Turno pendiente (UUID)") @RequestParam(value = "turnId", required = false)
            String turnId,
            @Parameter(description = "Grabación WebM/Opus o MP4/AAC, hasta 10 MiB y 3 minutos")
            @RequestPart(value = "audio", required = false) MultipartFile audio) {
        FirebaseUid owner = FirebaseUid.required(userId);
        SubmitAudioAnswerCommand command = new SubmitAudioAnswerCommand(SessionCommand.of(sessionId, owner).sessionId(),
                owner, turnId, idempotencyKey, bytesOf(audio));
        return ResponseEntity.ok(AnswerResponse.from(submitAudio.submit(command)));
    }

    // Solo los bytes: el nombre original y el Content-Type de la parte nunca se leen.
    private static byte[] bytesOf(MultipartFile audio) {
        if (audio == null) {
            return null;
        }
        try {
            return audio.getBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
