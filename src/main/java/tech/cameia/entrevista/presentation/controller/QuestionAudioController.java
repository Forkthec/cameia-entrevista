package tech.cameia.entrevista.presentation.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.service.ListenQuestionService;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.SpeechAudio;

/**
 * Adaptador de entrada HTTP para escuchar una pregunta ya mostrada (HU-5.15).
 *
 * <p>Responde el audio en el cuerpo, sin guardarlo, con {@code Cache-Control: no-store}: ni el navegador ni un
 * intermediario deben conservar una copia. Si falla, el cuerpo es el error del servicio en
 * {@code application/problem+json}, nunca audio.</p>
 */
@Tag(name = "Voz de las preguntas", description = "Escuchar la pregunta con una voz generada")
@RestController
@RequestMapping("/api/v1/interviews/sessions")
class QuestionAudioController {

    private static final String PROBLEM = "application/problem+json";

    /** Tipo de contenido del audio, el mismo que entrega el adaptador de voz. */
    private static final String AUDIO_WAV = "audio/wav";

    private final ListenQuestionService listen;

    /**
     * Crea el controlador.
     *
     * @param listen caso de uso de escuchar la pregunta
     */
    QuestionAudioController(ListenQuestionService listen) {
        this.listen = listen;
    }

    /**
     * Genera el audio de una pregunta ya mostrada.
     *
     * @param userId identidad del Gateway
     * @param sessionId sesión, tal como llegó en la ruta
     * @param number número de la pregunta, tal como llegó en la ruta
     * @return 200 con el audio WAV
     */
    @Operation(summary = "Escuchar una pregunta ya mostrada",
            description = "Sin cuerpo. Gemini TTS lee el texto guardado de la pregunta (nunca uno enviado por el"
                    + " cliente), con etiquetas de voz que un modelo coloca según el tono de la entrevista. Responde"
                    + " audio/wav (PCM 16 bits, 24 kHz, mono) que no se guarda. Cada respuesta 200 gasta un uso de"
                    + " audio de la pregunta (3 por pregunta); un error no lo gasta. No cambia la entrevista ni su"
                    + " última actividad. Puede tardar hasta SPEECH_TIMEOUT (15 s por defecto).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audio de la pregunta",
                    content = @Content(mediaType = AUDIO_WAV,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "SESSION_NOT_FOUND (la sesión no existe, es de otra"
                    + " persona o el identificador no es un UUID) o QUESTION_NOT_FOUND (el número no es 1 a 5)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "SESSION_TRANSITION_NOT_ALLOWED, SESSION_ABANDONED o"
                    + " SESSION_ALREADY_CLOSED (la entrevista no está en curso, también si cambió mientras se"
                    + " generaba), QUESTION_NOT_AVAILABLE (la pregunta todavía no se muestra) o"
                    + " QUESTION_AUDIO_LIMIT_REACHED (sin usos de audio)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "SPEECH_TEXT_EMPTY o SPEECH_TEXT_TOO_LONG (defensa: la"
                    + " base ya limita el texto de 1 a 500 caracteres)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "SPEECH_DISABLED, SPEECH_MONTHLY_LIMIT_REACHED,"
                    + " SPEECH_BUSY, SPEECH_TIMEOUT o SPEECH_UNAVAILABLE; ninguno gasta el uso de audio",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(path = "/{sessionId}/questions/{number}/audio", produces = AUDIO_WAV)
    ResponseEntity<byte[]> listen(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId,
            @Parameter(description = "Número de la pregunta, de 1 a 5") @PathVariable String number) {
        FirebaseUid owner = FirebaseUid.required(userId);
        SpeechAudio audio = listen.listen(SessionCommand.of(sessionId, owner), number);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.mediaType()))
                .contentLength(audio.content().length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(audio.content());
    }
}
