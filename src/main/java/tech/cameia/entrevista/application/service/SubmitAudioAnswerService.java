package tech.cameia.entrevista.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.command.AudioAnswerCommand;
import tech.cameia.entrevista.domain.exception.AudioNotAllowedException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.AudioAnswer;
import tech.cameia.entrevista.domain.model.AudioInspection;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TranscriptionExecution;
import tech.cameia.entrevista.domain.policy.AudioAnswerPolicy;
import tech.cameia.entrevista.domain.policy.TranscriptionPolicy;
import tech.cameia.entrevista.domain.policy.TranscriptionRetryPolicy;
import tech.cameia.entrevista.domain.port.AudioInspector;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.SpeechTranscriptionPort;
import tech.cameia.entrevista.domain.port.TranscriptionExecutionRepository;

/**
 * Caso de uso: responder por voz la pregunta pendiente (HU-5.8 y HU-5.12). Valida el audio por su contenido real, lo
 * transcribe con AssemblyAI en el idioma de la entrevista, valida la transcripción, la registra como respuesta igual
 * que una escrita y avanza a la siguiente pregunta.
 *
 * <p>Orden de las comprobaciones, todas antes de transcribir y sin guardar nada: sesión propia (404), campos (422),
 * repetición de la misma {@code Idempotency-Key} (devuelve la respuesta guardada), estado de la sesión (409,
 * CA-5.8.28), entrevista por texto (422, CA-5.8.20), turno pendiente (404 o 409), tamaño (413, CA-5.8.13), formato real
 * (415, CA-5.8.14 y CA-5.8.15) y duración (422, CA-5.8.8).</p>
 *
 * <p>La transcripción nunca ocurre dentro de una transacción:</p>
 * <ol>
 *   <li><b>Reserva</b> (transacción corta): el turno pasa a {@code PROCESSING}; un doble envío recibe 409
 *       {@code SESSION_CONFLICT} y no pide otra transcripción (CA-5.8.6).</li>
 *   <li><b>Transcripción</b> (sin transacción): hasta dos intentos según {@link TranscriptionRetryPolicy} (HU-5.13):
 *       un error transitorio del primero (503 o conexión rechazada) se reintenta sin que la persona haga nada; un
 *       intento que agota su tiempo, o el segundo fallido, terminan la petición. Cada intento registra su ejecución
 *       {@code TRANSCRIBIR} con su número (CA-5.12.3). Mientras tanto el turno sigue reservado, así que otro envío de
 *       la misma pregunta recibe 409 (CA-5.13.8). Después se valida el texto (CA-5.12.7, CA-5.12.9, CA-5.12.10).</li>
 *   <li><b>Registro</b> (transacción corta): {@link AnswerRegistrar} guarda la respuesta definitiva y abre el turno
 *       siguiente; con clave, se guarda también la respuesta para repetirla.</li>
 * </ol>
 *
 * <p>Si la transcripción o el registro fallan, el turno pasa a {@code RETRYING}: la pregunta sigue pendiente y la
 * persona puede grabar de nuevo o responder por texto. El audio solo vive en memoria y se sobrescribe al terminar la
 * petición, con éxito o con error (CA-5.8.18, CA-5.12.4). Nunca se registran el audio ni el texto transcrito.</p>
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

    /** Margen sobre el tiempo de la transcripción tras el que una reserva se considera de un proceso muerto. */
    static final Duration CLAIM_MARGIN = Duration.ofSeconds(30);

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
    private final AudioInspector inspector;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final Clock clock;
    private final TranscriptionRetryPolicy retryPolicy;
    private final Pause pause;
    private final Duration claimMaxAge;

    /** Espera antes del reintento; separada para que las pruebas no duerman. */
    @FunctionalInterface
    interface Pause {
        /**
         * Espera el tiempo indicado.
         *
         * @param duration tiempo de espera
         * @throws InterruptedException si el hilo se interrumpe
         */
        void sleep(Duration duration) throws InterruptedException;
    }

    @Autowired
    @SuppressWarnings("java:S107")
    SubmitAudioAnswerService(InterviewSessionRepository sessions, AnswerRegistrar registrar,
            SpeechTranscriptionPort transcriber, TranscriptionExecutionRepository executions,
            IdempotencyKeyRepository keys, TranscriptionPolicy transcriptionPolicy, AudioAnswerPolicy audioPolicy,
            AudioInspector inspector, TransactionTemplate transaction, JsonMapper json, Clock clock,
            TranscriptionRetryPolicy retryPolicy) {
        this(sessions, registrar, transcriber, executions, keys, transcriptionPolicy, audioPolicy, inspector, transaction,
                json, clock, retryPolicy, Thread::sleep);
    }

    @SuppressWarnings("java:S107")
    SubmitAudioAnswerService(InterviewSessionRepository sessions, AnswerRegistrar registrar,
            SpeechTranscriptionPort transcriber, TranscriptionExecutionRepository executions,
            IdempotencyKeyRepository keys, TranscriptionPolicy transcriptionPolicy, AudioAnswerPolicy audioPolicy,
            AudioInspector inspector, TransactionTemplate transaction, JsonMapper json, Clock clock,
            TranscriptionRetryPolicy retryPolicy, Pause pause) {
        this.sessions = sessions;
        this.registrar = registrar;
        this.transcriber = transcriber;
        this.executions = executions;
        this.keys = keys;
        this.transcriptionPolicy = transcriptionPolicy;
        this.audioPolicy = audioPolicy;
        this.inspector = inspector;
        this.transaction = transaction;
        this.json = json;
        this.clock = clock;
        this.retryPolicy = retryPolicy;
        this.pause = pause;
        // Una reserva más vieja que todos los intentos posibles es de un proceso que murió sin liberarla.
        this.claimMaxAge = retryPolicy.maxDuration().plus(CLAIM_MARGIN);
    }

    /**
     * Responde por voz la pregunta pendiente.
     *
     * @param command sesión, dueño, turno, audio y clave
     * @return el turno respondido, el avance y la siguiente pregunta (o {@code interviewFinished} tras la quinta)
     */
    public AnswerResult submit(AudioAnswerCommand command) {
        try {
            return submitAndForget(command);
        } finally {
            // CA-5.8.18 y CA-5.12.4: el audio se descarta al terminar la petición; no queda copia en CAMEIA.
            if (command.audio() != null) {
                Arrays.fill(command.audio(), (byte) 0);
            }
        }
    }

    private AnswerResult submitAndForget(AudioAnswerCommand command) {
        FirebaseUid owner = command.session().owner();
        InterviewSession session = GetSessionStateService.findOwned(sessions, command.session());
        UUID turnId = validateFields(command);
        String key = command.idempotencyKey();
        String fingerprint = key == null ? null : fingerprint(session.id(), turnId, command.audio());
        Optional<AnswerResult> replay = replayIfKnown(owner, key, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        AnswerRegistrar.requireAcceptsAnswers(session.status());
        if (session.configuration().answerMode() != AnswerMode.AUDIO) {
            throw new AudioNotAllowedException();
        }
        AnswerRegistrar.Pending pending = registrar.requireAnswerable(session, turnId, claimMaxAge);
        audioPolicy.checkSize(command.audio().length);
        AudioInspection inspection = audioPolicy.checkContent(inspector.inspect(command.audio()));
        AudioAnswer audio = new AudioAnswer(command.audio(), inspection.format().mimeType());

        InterviewTurn claimed = transaction.execute(status -> registrar.claim(turnId, claimMaxAge));
        InterviewLanguage language = session.configuration().language();
        Transcription transcription;
        String text;
        try {
            transcription = transcribe(session.id(), turnId, audio, language);
            text = transcriptionPolicy.accept(transcription, language);
        } catch (RuntimeException failure) {
            registrar.release(claimed);
            throw failure;
        }

        AnswerResult result;
        try {
            result = transaction.execute(status -> {
                AnswerResult registered = registrar.register(session.id(), owner, claimed, pending.question(), text,
                        AnswerMode.AUDIO, transcription.detectedLanguage());
                if (key != null) {
                    keys.save(new IdempotencyKey(UUID.randomUUID(), owner, OPERATION, key, fingerprint, OK,
                            json.writeValueAsString(registered), clock.instant()));
                }
                return registered;
            });
        } catch (IdempotencyKeyAlreadyExistsException race) {
            // Otra petición con la misma clave terminó primero: se repite su respuesta. Va fuera de la transacción
            // revertida.
            registrar.release(claimed);
            return replayIfKnown(owner, key, fingerprint).orElseThrow(() -> race);
        } catch (RuntimeException failure) {
            registrar.release(claimed);
            throw failure;
        }
        log.info("Respuesta por voz registrada [sessionId={}, turnId={}, firebaseUid={}, formato={}, bytes={},"
                + " audioMs={}, siguiente={}]", session.id(), turnId, owner.value(), inspection.format(),
                audio.size(), inspection.duration().toMillis(),
                result.interviewFinished() ? "-" : result.currentQuestion());
        return result;
    }

    private Transcription transcribe(UUID sessionId, UUID turnId, AudioAnswer audio, InterviewLanguage language) {
        Instant first = clock.instant();
        for (int attempt = 1; ; attempt++) {
            Instant started = clock.instant();
            try {
                Transcription transcription = transcriber.transcribe(audio, language);
                executions.save(TranscriptionExecution.succeeded(sessionId, turnId, attempt, transcriber.model(),
                        transcriber.provider(), started, clock.instant()));
                if (attempt > 1) {
                    log.info("Transcripción obtenida en el reintento automático [sessionId={}, turnId={}, intento={}]",
                            sessionId, turnId, attempt);
                }
                return transcription;
            } catch (TranscriptionUnavailableException failure) {
                executions.save(TranscriptionExecution.failed(sessionId, turnId, attempt, transcriber.model(),
                        transcriber.provider(), ErrorCode.TRANSCRIPTION_UNAVAILABLE, started, clock.instant()));
                Duration elapsed = Duration.between(first, clock.instant());
                if (!retryPolicy.shouldRetry(attempt, failure.failure(), elapsed)) {
                    log.warn("Transcripción sin éxito; no se reintenta [sessionId={}, turnId={}, intento={}, causa={}]",
                            sessionId, turnId, attempt, failure.failure());
                    throw failure;
                }
                log.warn("Transcripción fallida por un error transitorio; se reintenta [sessionId={}, turnId={},"
                        + " intento={}]", sessionId, turnId, attempt);
            }
            waitBeforeRetry();
        }
    }

    private void waitBeforeRetry() {
        try {
            pause.sleep(retryPolicy.pause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TranscriptionUnavailableException("espera del reintento interrumpida", interrupted);
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
