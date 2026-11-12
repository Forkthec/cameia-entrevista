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

import tech.cameia.entrevista.application.command.AudioAnswerCommand;
import tech.cameia.entrevista.application.service.SubmitAudioAnswerService;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.presentation.dto.AnswerResponse;

/**
 * Adaptador de entrada HTTP de la respuesta por voz (HU-5.12).
 *
 * <p>Las partes del multipart llegan opcionales a propósito: su ausencia la valida el caso de uso, que devuelve
 * todos los errores de campo juntos en el formato común. El audio se lee a memoria y nunca se escribe en disco
 * (umbral del multipart en {@code application.properties}).</p>
 */
@Tag(name = "Respuestas de la entrevista", description = "Responder la pregunta pendiente")
@RestController
@RequestMapping("/api/v1/interviews/sessions")
class AudioAnswerController {

    private static final String PROBLEM = "application/problem+json";

    private static final String EXAMPLE_200 = """
            {"turn":{"turnId":"9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12","status":"COMPLETED"},
             "progress":{"currentQuestion":2,"totalQuestions":5},
             "nextQuestion":{"turnId":"0c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f","number":2,
               "text":"Háblame de un proyecto en el que hayas usado Java."},
             "interviewFinished":false}""";

    private static final String EXAMPLE_200_LAST = """
            {"turn":{"turnId":"9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12","status":"COMPLETED"},
             "progress":{"currentQuestion":5,"totalQuestions":5},"nextQuestion":null,"interviewFinished":true}""";

    private static final String EXAMPLE_EMPTY = """
            {"title":"Transcripción no válida","status":422,"detail":"No detectamos tu voz, intenta de nuevo.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/answers/audio",
             "code":"TRANSCRIPTION_EMPTY","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_LANGUAGE = """
            {"title":"Transcripción no válida","status":422,"detail":"Tu entrevista es en inglés. Responde en inglés.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/answers/audio",
             "code":"TRANSCRIPTION_LANGUAGE_MISMATCH","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private static final String EXAMPLE_UNAVAILABLE = """
            {"title":"Transcripción no disponible","status":503,
             "detail":"No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.",
             "instance":"/api/v1/interviews/sessions/3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10/answers/audio",
             "code":"TRANSCRIPTION_UNAVAILABLE","requestId":"3f9c2a7e-5b1d-4e8a-9c64-2d7f0a1b8e35"}""";

    private final SubmitAudioAnswerService submitAudio;

    /**
     * Crea el controlador.
     *
     * @param submitAudio caso de uso de la respuesta por voz
     */
    AudioAnswerController(SubmitAudioAnswerService submitAudio) {
        this.submitAudio = submitAudio;
    }

    /**
     * Responde por voz la pregunta pendiente.
     *
     * @param userId identidad del Gateway
     * @param sessionId sesión, tal como llegó en la ruta
     * @param idempotencyKey clave opcional para repetir el envío sin duplicar la respuesta
     * @param turnId parte {@code turnId}
     * @param audio parte {@code audio}
     * @return 200 con el avance y la siguiente pregunta
     */
    @Operation(summary = "Responder por voz la pregunta pendiente",
            description = "Recibe la grabación (multipart: turnId y audio), la transcribe con AssemblyAI en el idioma"
                    + " de la entrevista, registra el texto como respuesta (igual que una escrita, sin"
                    + " retroalimentación) y devuelve la siguiente pregunta. Tras la quinta devuelve nextQuestion null"
                    + " e interviewFinished true. El audio no se guarda: se descarta al terminar la petición."
                    + " Síncrono: puede tardar hasta TRANSCRIPTION_TIMEOUT (90 s por defecto). Repetir la misma"
                    + " Idempotency-Key con el mismo audio devuelve la misma respuesta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta registrada", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = AnswerResponse.class),
                    examples = {
                        @ExampleObject(name = "siguientePregunta", value = EXAMPLE_200),
                        @ExampleObject(name = "ultimaPregunta", value = EXAMPLE_200_LAST)})),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "La sesión no existe, es de otra persona o el"
                    + " identificador no es un UUID (code SESSION_NOT_FOUND)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "SESSION_TRANSITION_NOT_ALLOWED (la sesión no está"
                    + " EN_CURSO), TURN_TRANSITION_NOT_ALLOWED (el turno no es el pendiente o ya se respondió),"
                    + " IDEMPOTENCY_KEY_CONFLICT (la clave ya se usó con otro turno u otro audio) o SESSION_CONFLICT",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "413", description = "El audio supera AUDIO_MAX_SIZE (code"
                    + " REQUEST_PAYLOAD_TOO_LARGE)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "415", description = "El audio no es de un tipo admitido (code"
                    + " AUDIO_FORMAT_NOT_ALLOWED) o la petición no es multipart (MEDIA_TYPE_NOT_ALLOWED)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED (falta turnId o audio, o la clave"
                    + " no tiene formato), ANSWER_FORMAT_NOT_ALLOWED (la entrevista es por texto), TRANSCRIPTION_EMPTY,"
                    + " TRANSCRIPTION_LANGUAGE_MISMATCH o TRANSCRIPTION_TOO_LONG. Ninguno registra la respuesta ni gasta"
                    + " un intento",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                @ExampleObject(name = "sinVoz", value = EXAMPLE_EMPTY),
                                @ExampleObject(name = "otroIdioma", value = EXAMPLE_LANGUAGE)})),
            @ApiResponse(responseCode = "503", description = "TRANSCRIPTION_UNAVAILABLE (falló AssemblyAI) o"
                    + " DATABASE_UNAVAILABLE",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "transcripcionFallida", value = EXAMPLE_UNAVAILABLE)))
    })
    @PostMapping(path = "/{sessionId}/answers/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AnswerResponse> answerByVoice(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId,
            @Parameter(description = "Clave única del envío: de 1 a 64 caracteres A-Z a-z 0-9 . _ -")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Turno pendiente al que se responde (UUID)")
            @RequestParam(value = "turnId", required = false) String turnId,
            @Parameter(description = "Grabación: audio/webm, audio/ogg, audio/mp4, audio/mpeg o audio/wav")
            @RequestPart(value = "audio", required = false) MultipartFile audio) {
        FirebaseUid owner = FirebaseUid.required(userId);
        AudioAnswerCommand command = AudioAnswerCommand.of(sessionId, owner, turnId, bytesOf(audio),
                audio == null ? null : audio.getContentType(), idempotencyKey);
        return ResponseEntity.ok(AnswerResponse.from(submitAudio.submit(command)));
    }

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
