package tech.cameia.entrevista.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.application.command.SubmitAudioAnswerCommand;
import tech.cameia.entrevista.domain.exception.AudioNotAllowedException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TranscriptionEmptyException;
import tech.cameia.entrevista.domain.exception.TranscriptionTooLongException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.AudioInspection;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.AudioAnswerPolicy;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.AudioInspector;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.TranscriptionPort;
import tools.jackson.databind.json.JsonMapper;

/**
 * Caso de uso: registrar la respuesta por voz a la pregunta pendiente (HU-5.8).
 *
 * <p>Orden de las comprobaciones, todas antes de pedir la transcripción y sin guardar nada: sesión propia (404),
 * repetición de la misma {@code Idempotency-Key} (devuelve la respuesta guardada, CA-5.8.27), estado de la sesión
 * (409, CA-5.8.28), forma de respuesta Texto (422, CA-5.8.20), turno pendiente de la sesión (404 o 409), tamaño
 * (413, CA-5.8.13), formato real (415, CA-5.8.14 y CA-5.8.15) y duración (422, CA-5.8.8).</p>
 *
 * <p>La transcripción nunca ocurre dentro de una transacción. Las fases son:</p>
 * <ol>
 *   <li><b>Reserva</b> (transacción corta): el turno pasa a {@code PROCESSING}. El control de versiones del turno
 *       deja pasar una sola reserva: un doble envío recibe 409 {@code SESSION_CONFLICT} y no pide otra transcripción
 *       (CA-5.8.6).</li>
 *   <li><b>Transcripción</b>, sin transacción.</li>
 *   <li><b>Cierre</b> (transacción corta): guarda la respuesta como {@code AUDIO}, completa el turno, abre el de la
 *       pregunta siguiente o pasa la sesión a {@code EVALUANDO} tras la quinta, y guarda la respuesta de la clave
 *       anti-duplicados.</li>
 * </ol>
 *
 * <p>Si la transcripción o el cierre fallan, el turno pasa a {@code RETRYING}: la pregunta sigue pendiente y la
 * persona puede grabar de nuevo o responder por texto. Con éxito o con error, los bytes del audio se sobrescriben al
 * terminar (CA-5.8.18). Los registros llevan identificadores, tamaño, formato y duración; nunca el audio, el texto ni
 * el nombre del archivo recibido (CA-5.8.16).</p>
 *
 * <p>La evaluación de la respuesta por la IA es de HU-5.2: el turno se completa con la respuesta registrada.</p>
 */
@Service
public class SubmitAudioAnswerService {

    private static final Logger log = LoggerFactory.getLogger(SubmitAudioAnswerService.class);

    /** Operación de la clave anti-duplicados; la misma que fija la migración V8. */
    static final String OPERATION = "RESPONDER_AUDIO";

    /** Forma de una clave anti-duplicados válida; la misma del esquema. */
    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private static final String KEY_FIELD = "Idempotency-Key";

    /** Margen sobre el tiempo de la transcripción tras el que una reserva se considera de un proceso muerto. */
    private static final Duration CLAIM_MARGIN = Duration.ofSeconds(30);

    private static final int CANONICAL_UUID_LENGTH = 36;

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewTurnRepository turns;
    private final AnswerAttemptRepository answers;
    private final IdempotencyKeyRepository keys;
    private final AudioInspector inspector;
    private final AudioAnswerPolicy policy;
    private final TranscriptionPort transcriber;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration claimMaxAge;

    @SuppressWarnings("java:S107")
    SubmitAudioAnswerService(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns, AnswerAttemptRepository answers, IdempotencyKeyRepository keys,
            AudioInspector inspector, AudioAnswerPolicy policy, TranscriptionPort transcriber,
            TransactionTemplate transaction, JsonMapper json, Clock clock,
            @Value("${cameia.transcription.timeout}") Duration transcriptionTimeout) {
        this.sessions = sessions;
        this.questions = questions;
        this.turns = turns;
        this.answers = answers;
        this.keys = keys;
        this.inspector = inspector;
        this.policy = policy;
        this.transcriber = transcriber;
        this.transaction = transaction;
        this.json = json;
        this.clock = clock;
        this.claimMaxAge = transcriptionTimeout.plus(CLAIM_MARGIN);
    }

    /**
     * Registra la respuesta por voz.
     *
     * @param command sesión, dueño, turno, clave anti-duplicados y audio
     * @return el turno respondido, el avance y la pregunta siguiente
     */
    public AnswerResult submit(SubmitAudioAnswerCommand command) {
        try {
            return process(command);
        } finally {
            if (command.audio() != null) {
                // El audio no debe quedar recuperable en memoria al terminar la petición (CA-5.8.18).
                Arrays.fill(command.audio(), (byte) 0);
            }
        }
    }

    private AnswerResult process(SubmitAudioAnswerCommand command) {
        InterviewSession session = GetSessionStateService.findOwned(sessions,
                new SessionCommand(command.sessionId(), command.owner()));
        String key = validKey(command.idempotencyKey());
        String fingerprint = fingerprint(command);
        Optional<AnswerResult> replay = replayIfKnown(command.owner(), key, fingerprint);
        if (replay.isPresent()) {
            log.info("Respuesta por voz repetida con la misma clave [sessionId={}]", session.id());
            return replay.get();
        }
        requireAcceptsAnswers(session.status());
        if (session.configuration().answerMode() != AnswerMode.AUDIO) {
            throw new AudioNotAllowedException();
        }
        List<InterviewQuestion> planned = questions.findBySession(session.id());
        Pending pending = requirePendingTurn(session.id(), parse(command.rawTurnId()), planned);
        policy.checkSize(command.audio() == null ? null : (long) command.audio().length);
        AudioInspection inspection = policy.checkContent(inspector.inspect(command.audio()));
        AudioClip clip = new AudioClip(command.audio(), inspection);

        InterviewTurn claimed = transaction.execute(status -> claim(pending.turn().id()));
        Transcription transcription;
        try {
            transcription = transcriber.transcribe(clip, session.configuration().language());
            requireUsable(transcription.text());
        } catch (RuntimeException failure) {
            release(claimed, failure);
            throw failure;
        }
        AnswerResult result;
        try {
            result = transaction.execute(status -> finish(command.owner(), session.id(), claimed, pending.question(),
                    planned, transcription.text().strip(), key, fingerprint));
        } catch (IdempotencyKeyAlreadyExistsException race) {
            release(claimed, race);
            return replayIfKnown(command.owner(), key, fingerprint).orElseThrow(() -> race);
        } catch (RuntimeException failure) {
            release(claimed, failure);
            throw failure;
        }
        log.info("Respuesta por voz registrada [sessionId={}, turnId={}, pregunta={}, formato={}, bytes={},"
                + " audioMs={}, caracteres={}]", session.id(), claimed.id(), pending.question().position(),
                clip.format(), clip.content().length, inspection.duration().toMillis(),
                transcription.text().strip().length());
        return result;
    }

    // ---- Comprobaciones previas ----

    private static String validKey(String raw) {
        if (raw == null) {
            return null;
        }
        if (!KEY_FORMAT.matcher(raw).matches()) {
            throw new InvalidRequestException(List.of(new FieldRejection(KEY_FIELD,
                    ErrorCode.IDEMPOTENCY_KEY_INVALID_FORMAT, "Ocurrió un error. Inténtalo de nuevo.")));
        }
        return raw;
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

    private static void requireAcceptsAnswers(SessionStatus status) {
        switch (status) {
            case IN_PROGRESS -> {
                // Admite respuestas.
            }
            case ABANDONED -> throw new SessionAbandonedException();
            case EVALUATING, FINISHED -> throw new SessionAlreadyClosedException();
            case CONFIGURED -> throw new SessionTransitionNotAllowedException();
        }
    }

    /**
     * El turno pedido debe ser el abierto de la sesión. Uno que no es de la sesión (o no existe, o no es un UUID)
     * responde 404 sin revelar si existe; uno de la sesión que ya se respondió, 409.
     */
    private Pending requirePendingTurn(UUID sessionId, UUID turnId, List<InterviewQuestion> planned) {
        if (turnId == null) {
            throw new TurnNotFoundException();
        }
        InterviewTurn turn = turns.findById(turnId).orElseThrow(TurnNotFoundException::new);
        InterviewQuestion question = planned.stream().filter(q -> q.id().equals(turn.questionId())).findFirst()
                .orElseThrow(TurnNotFoundException::new);
        boolean isOpen = turns.findOpenBySession(sessionId).map(open -> open.id().equals(turn.id())).orElse(false);
        if (!isOpen || turn.status() == TurnStatus.COMPLETED) {
            throw new TurnTransitionNotAllowedException();
        }
        if (turn.status() == TurnStatus.PROCESSING && isLive(turn)) {
            // Otro envío de esta misma pregunta se está transcribiendo (doble clic, CA-5.8.6).
            throw new SessionConflictException();
        }
        return new Pending(turn, question);
    }

    private static void requireUsable(String text) {
        String clean = text.strip();
        if (clean.isEmpty()) {
            throw new TranscriptionEmptyException();
        }
        if (clean.codePointCount(0, clean.length()) > AnswerAttempt.MAX_TEXT_LENGTH) {
            throw new TranscriptionTooLongException();
        }
    }

    // ---- Fase 1: reserva ----

    private InterviewTurn claim(UUID turnId) {
        InterviewTurn turn = turns.findById(turnId).orElseThrow(TurnNotFoundException::new);
        switch (turn.status()) {
            case PENDING -> turn.startProcessing(clock);
            case RETRYING -> turn.resumeProcessing(clock);
            case PROCESSING -> {
                if (isLive(turn)) {
                    throw new SessionConflictException();
                }
                // Reserva de un proceso que murió a mitad de la transcripción: se libera y se toma de nuevo.
                turn.retry(clock);
                turn.resumeProcessing(clock);
            }
            case COMPLETED -> throw new TurnTransitionNotAllowedException();
        }
        // Si otra petición reservó el mismo turno entre la lectura y la escritura, la versión ya no coincide y el
        // repositorio lanza SESSION_CONFLICT: solo una de las dos sigue a la transcripción.
        return turns.save(turn);
    }

    private boolean isLive(InterviewTurn turn) {
        return turn.updatedAt().plus(claimMaxAge).isAfter(clock.instant());
    }

    // ---- Fase 3: cierre ----

    @SuppressWarnings("java:S107")
    private AnswerResult finish(FirebaseUid owner, UUID sessionId, InterviewTurn claimed, InterviewQuestion question,
            List<InterviewQuestion> planned, String text, String key, String fingerprint) {
        InterviewSession session = GetSessionStateService.findOwned(sessions, new SessionCommand(sessionId, owner));
        requireAcceptsAnswers(session.status());
        InterviewTurn turn = turns.findById(claimed.id()).orElseThrow(TurnNotFoundException::new);
        if (turn.status() != TurnStatus.PROCESSING || !turn.version().equals(claimed.version())) {
            throw new SessionConflictException();
        }
        answers.save(new AnswerAttempt(UUID.randomUUID(), turn.id(), AnswerAttempt.AnswerOrigin.AUDIO, text,
                question.originalText(), null, clock.instant()));
        turn.complete(clock);
        turns.save(turn);
        Optional<InterviewQuestion> next = planned.stream()
                .filter(candidate -> candidate.position() == question.position() + 1).findFirst();
        AnswerResult result;
        if (next.isPresent()) {
            InterviewTurn nextTurn = turns.save(InterviewTurn.open(next.get().id(), clock));
            result = new AnswerResult(turn.id(), TurnStatus.COMPLETED, next.get().position(), InterviewQuestion.TOTAL,
                    new SessionState.PendingQuestion(nextTurn.id(), next.get().position(),
                            next.get().originalText()), false);
        } else {
            session.startEvaluation(clock);
            sessions.save(session);
            result = new AnswerResult(turn.id(), TurnStatus.COMPLETED, InterviewQuestion.TOTAL,
                    InterviewQuestion.TOTAL, null, true);
        }
        if (key != null) {
            keys.save(new IdempotencyKey(UUID.randomUUID(), owner, OPERATION, key, fingerprint, 200,
                    json.writeValueAsString(result), clock.instant()));
        }
        return result;
    }

    // ---- Fallos ----

    /**
     * Devuelve el turno reservado a {@code RETRYING} para que la pregunta siga pendiente. Solo lo toca si sigue siendo
     * la reserva de esta petición; si ni así se puede, la reserva caduca sola tras {@link #claimMaxAge}.
     */
    private void release(InterviewTurn claimed, RuntimeException cause) {
        try {
            transaction.executeWithoutResult(status -> turns.findById(claimed.id())
                    .filter(turn -> turn.status() == TurnStatus.PROCESSING
                            && turn.version().equals(claimed.version()))
                    .ifPresent(turn -> {
                        turn.retry(clock);
                        turns.save(turn);
                    }));
        } catch (RuntimeException releasing) {
            log.error("No se pudo liberar el turno tras un fallo de la respuesta por voz [turnId={}, causa={},"
                    + " causaLiberacion={}]", claimed.id(), cause.getClass().getSimpleName(),
                    releasing.getClass().getSimpleName());
        }
    }

    // ---- Utilidades ----

    private static UUID parse(String raw) {
        if (raw == null || raw.length() != CANONICAL_UUID_LENGTH) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /** Huella de la petición: sesión, turno y contenido del audio, en SHA-256 hexadecimal. */
    private static String fingerprint(SubmitAudioAnswerCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(command.sessionId()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(String.valueOf(command.rawTurnId()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            if (command.audio() != null) {
                digest.update(command.audio());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException missing) {
            // SHA-256 es obligatorio en toda JVM; si faltara, la instalación está rota.
            throw new IllegalStateException(missing);
        }
    }

    private record Pending(InterviewTurn turn, InterviewQuestion question) {
    }
}
