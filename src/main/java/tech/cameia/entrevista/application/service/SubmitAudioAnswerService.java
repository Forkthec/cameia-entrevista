package tech.cameia.entrevista.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.command.AudioAnswerCommand;
import tech.cameia.entrevista.domain.exception.AnswerFormatNotAllowedException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.AudioAnswer;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TranscriptionExecution;
import tech.cameia.entrevista.domain.policy.AudioAnswerPolicy;
import tech.cameia.entrevista.domain.policy.TranscriptionPolicy;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.SpeechTranscriptionPort;
import tech.cameia.entrevista.domain.port.TranscriptionExecutionRepository;

/**
 * Caso de uso: responder por voz la pregunta pendiente (HU-5.12). Transcribe el audio en el idioma de la entrevista,
 * valida la transcripción, la registra como respuesta igual que una escrita y avanza a la siguiente pregunta.
 *
 * <p>Tres fases, para no tener una transacción abierta mientras responde el proveedor:</p>
 * <ol>
 *   <li><b>Validación</b> (lecturas sueltas): sesión propia (404), en curso (409), campos (422), turno pendiente
 *       (409), tipo de audio (415) y forma de respuesta de la sesión (422). Una clave ya usada repite su
 *       respuesta.</li>
 *   <li><b>Transcripción</b> (sin transacción): una sola llamada; se registra la ejecución {@code TRANSCRIBIR} del
 *       turno con su resultado (CA-5.12.3) y se valida el texto: vacío, otro idioma o más de 4000 caracteres se
 *       rechazan sin registrar la respuesta ni gastar un intento (CA-5.12.7, CA-5.12.9, CA-5.12.10).</li>
 *   <li><b>Registro</b> (transacción corta): {@link AnswerRegistrar} guarda la respuesta definitiva y abre el turno
 *       siguiente; con clave, se guarda también la respuesta para repetirla.</li>
 * </ol>
 *
 * <p>El audio solo vive en memoria durante la petición y se borra al terminar, con éxito o con error
 * (CA-5.12.4). Nunca se registran el audio ni el texto transcrito.</p>
 */
@Service
public class SubmitAudioAnswerService {

    /** Operación con la que se guarda la clave anti-duplicados. */
    static final String OPERATION = "RESPONDER_AUDIO";

    /** Campo del turno en el multipart. */
    public static final String TURN_ID_FIELD = "turnId";

    /** Campo del audio en el multipart. */
    public static final String AUDIO_FIELD = "audio";

    /** Campo de la clave anti-duplicados. */
    public static final String IDEMPOTENCY_KEY_FIELD = "Idempotency-Key";

    static final String TURN_ID_REQUIRED = "Falta la pregunta a la que respondes.";
    static final String TURN_ID_INVALID = "La pregunta a la que respondes no es válida.";
    static final String AUDIO_REQUIRED = "Graba tu respuesta antes de enviarla.";
    static final String IDEMPOTENCY_KEY_INVALID = "Ocurrió un error. Inténtalo de nuevo.";

    private static final int OK = 200;
    private static final Pattern IDEMPOTENCY_KEY_FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(SubmitAudioAnswerService.class);

    private final InterviewSessionRepository sessions;
    private final AnswerRegistrar registrar;
    private final SpeechTranscriptionPort transcriber;
    private final TranscriptionExecutionRepository executions;
    private final IdempotencyKeyRepository keys;
    private final TranscriptionPolicy transcriptionPolicy;
    private final AudioAnswerPolicy audioPolicy;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final Clock clock;

    SubmitAudioAnswerService(InterviewSessionRepository sessions, AnswerRegistrar registrar,
            SpeechTranscriptionPort transcriber, TranscriptionExecutionRepository executions,
            IdempotencyKeyRepository keys, TranscriptionPolicy transcriptionPolicy, AudioAnswerPolicy audioPolicy,
            TransactionTemplate transaction, JsonMapper json, Clock clock) {
        this.sessions = sessions;
        this.registrar = registrar;
        this.transcriber = transcriber;
        this.executions = executions;
        this.keys = keys;
        this.transcriptionPolicy = transcriptionPolicy;
        this.audioPolicy = audioPolicy;
        this.transaction = transaction;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Responde por voz la pregunta pendiente.
     *
     * @param command sesión, dueño, turno, audio y clave
     * @return el turno respondido, el avance y la siguiente pregunta (o {@code interviewFinished} tras la quinta)
     * @throws tech.cameia.entrevista.domain.exception.SessionNotFoundException 404 si la sesión no es de la persona
     * @throws SessionTransitionNotAllowedException 409 si la sesión no está en curso
     * @throws InvalidRequestException 422 si falta el turno o el audio, o la clave no tiene formato
     * @throws IdempotencyKeyConflictException 409 si la clave ya se usó con otro turno u otro audio
     * @throws TurnTransitionNotAllowedException 409 si el turno no es el pendiente
     * @throws tech.cameia.entrevista.domain.exception.AudioFormatNotAllowedException 415 si el audio no es de un
     *         tipo admitido
     * @throws AnswerFormatNotAllowedException 422 si la entrevista se responde por texto
     * @throws tech.cameia.entrevista.domain.exception.TranscriptionEmptyException 422 sin voz
     * @throws tech.cameia.entrevista.domain.exception.TranscriptionLanguageMismatchException 422 en otro idioma
     * @throws tech.cameia.entrevista.domain.exception.TranscriptionTooLongException 422 si pasa de 4000 caracteres
     * @throws TranscriptionUnavailableException 503 si el servicio de transcripción falla
     */
    public AnswerResult submit(AudioAnswerCommand command) {
        try {
            return submitAndForget(command);
        } finally {
            // CA-5.12.4: el audio se descarta al terminar la petición; no queda copia en CAMEIA.
            if (command.audio() != null) {
                Arrays.fill(command.audio(), (byte) 0);
            }
        }
    }

    private AnswerResult submitAndForget(AudioAnswerCommand command) {
        FirebaseUid owner = command.session().owner();
        InterviewSession session = GetSessionStateService.findOwned(sessions, command.session());
        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new SessionTransitionNotAllowedException();
        }
        UUID turnId = validateFields(command);
        String key = command.idempotencyKey();
        String fingerprint = key == null ? null : fingerprint(session.id(), turnId, command.audio());
        Optional<AnswerResult> replay = replayIfKnown(owner, key, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        registrar.requirePending(session, turnId);
        String mediaType = audioPolicy.requireAllowed(command.mediaType());
        if (session.configuration().answerMode() != AnswerMode.AUDIO) {
            throw new AnswerFormatNotAllowedException();
        }

        InterviewLanguage language = session.configuration().language();
        Transcription transcription = transcribe(session.id(), turnId,
                new AudioAnswer(command.audio(), mediaType), language);
        String text = transcriptionPolicy.accept(transcription, language);

        AnswerResult result;
        try {
            result = transaction.execute(status -> {
                AnswerResult registered = registrar.register(session.id(), owner, turnId, text, AnswerMode.AUDIO,
                        transcription.detectedLanguage());
                if (key != null) {
                    keys.save(new IdempotencyKey(UUID.randomUUID(), owner, OPERATION, key, fingerprint, OK,
                            json.writeValueAsString(registered), clock.instant()));
                }
                return registered;
            });
        } catch (IdempotencyKeyAlreadyExistsException | TurnTransitionNotAllowedException race) {
            // Otra petición con la misma clave terminó primero: se repite su respuesta. Sin clave, el error llega
            // tal cual. Va fuera de la transacción revertida.
            return replayIfKnown(owner, key, fingerprint).orElseThrow(() -> race);
        }
        log.info("Respuesta por voz registrada [sessionId={}, turnId={}, firebaseUid={}, siguiente={}]",
                session.id(), turnId, owner.value(), result.interviewFinished() ? "-" : result.currentQuestion());
        return result;
    }

    private Transcription transcribe(UUID sessionId, UUID turnId, AudioAnswer audio, InterviewLanguage language) {
        Instant started = clock.instant();
        try {
            Transcription transcription = transcriber.transcribe(audio, language);
            executions.save(TranscriptionExecution.succeeded(sessionId, turnId, transcriber.model(),
                    transcriber.provider(), started, clock.instant()));
            return transcription;
        } catch (TranscriptionUnavailableException failure) {
            executions.save(TranscriptionExecution.failed(sessionId, turnId, transcriber.model(),
                    transcriber.provider(), ErrorCode.TRANSCRIPTION_UNAVAILABLE, started, clock.instant()));
            throw failure;
        }
    }

    // Todas las causas de campo salen juntas, como en la creación de la sesión.
    private static UUID validateFields(AudioAnswerCommand command) {
        List<FieldRejection> rejections = new ArrayList<>();
        UUID turnId = null;
        String raw = command.rawTurnId();
        if (raw == null || raw.isBlank()) {
            rejections.add(new FieldRejection(TURN_ID_FIELD, ErrorCode.TURN_ID_REQUIRED, TURN_ID_REQUIRED));
        } else {
            turnId = parseUuid(raw.strip());
            if (turnId == null) {
                rejections.add(new FieldRejection(TURN_ID_FIELD, ErrorCode.TURN_ID_INVALID_FORMAT,
                        TURN_ID_INVALID));
            }
        }
        if (command.audio() == null || command.audio().length == 0) {
            rejections.add(new FieldRejection(AUDIO_FIELD, ErrorCode.AUDIO_REQUIRED, AUDIO_REQUIRED));
        }
        String key = command.idempotencyKey();
        if (key != null && !IDEMPOTENCY_KEY_FORMAT.matcher(key).matches()) {
            rejections.add(new FieldRejection(IDEMPOTENCY_KEY_FIELD, ErrorCode.IDEMPOTENCY_KEY_INVALID_FORMAT,
                    IDEMPOTENCY_KEY_INVALID));
        }
        if (!rejections.isEmpty()) {
            throw new InvalidRequestException(rejections);
        }
        return turnId;
    }

    private static UUID parseUuid(String raw) {
        if (raw.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private Optional<AnswerResult> replayIfKnown(FirebaseUid owner, String key, String fingerprint) {
        if (key == null) {
            return Optional.empty();
        }
        return keys.find(owner, OPERATION, key).map(saved -> {
            if (!saved.requestFingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException();
            }
            return json.readValue(saved.responseBodyJson(), AnswerResult.class);
        });
    }

    /**
     * Huella de la petición: la sesión, el turno y el contenido del audio. La misma clave con otra grabación u
     * otro turno es otra petición.
     */
    static String fingerprint(UUID sessionId, UUID turnId, byte[] audio) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((sessionId + "|" + turnId + "|").getBytes(StandardCharsets.UTF_8));
            digest.update(audio);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }
}
