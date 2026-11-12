package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.SubmitAudioAnswerCommand;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TranscriptionEmptyException;
import tech.cameia.entrevista.domain.exception.TranscriptionTooLongException;
import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.exception.TurnAlreadyAnsweredException;
import tech.cameia.entrevista.domain.exception.TurnNotFoundException;
import tech.cameia.entrevista.domain.exception.TurnProcessingException;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.AudioClip;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TranscriptionFailure;
import tech.cameia.entrevista.domain.model.TurnSnapshot;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.TranscriptionRetryPolicy;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.SpeechToTextPort;

/**
 * Criterios de HU-5.13 sobre el caso de uso de responder por voz: reintento automático, intentos agotados, audio
 * descartado, una sola respuesta por pregunta y rechazo de un segundo envío durante los intentos.
 *
 * <p>Los turnos viven en un repositorio en memoria que respeta la versión como la base, así que las carreras entre
 * dos envíos del mismo turno se comportan como en producción.</p>
 */
class SubmitAudioAnswerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration PAUSE = Duration.ofSeconds(1);

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final AnswerAttemptRepository answers = mock(AnswerAttemptRepository.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryTurns turns = new InMemoryTurns();
    private final ScriptedSpeech speech = new ScriptedSpeech();
    private final List<AnswerAttempt> inserted = new ArrayList<>();
    private final List<Duration> pauses = new ArrayList<>();

    private InterviewSession session;
    private List<InterviewQuestion> plan;
    private InterviewTurn firstTurn;
    private SubmitAudioAnswerService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        session = InterviewSession.configure(UUID.randomUUID(), OWNER, new SessionConfiguration(UUID.randomUUID(),
                UUID.randomUUID(), null, "{}", Modality.SIMULATION, Tone.WARM, AnswerMode.AUDIO,
                InterviewLanguage.SPANISH_COLOMBIA), clock);
        session.start(clock);
        plan = new ArrayList<>();
        QuestionType[] types = {QuestionType.APERTURA, QuestionType.TECNICA, QuestionType.CONDUCTUAL,
            QuestionType.TECNICA, QuestionType.SITUACIONAL};
        for (int position = 1; position <= InterviewQuestion.TOTAL; position++) {
            plan.add(new InterviewQuestion(UUID.randomUUID(), session.id(), position, types[position - 1],
                    "Pregunta " + position, UUID.randomUUID(), NOW));
        }
        firstTurn = turns.save(InterviewTurn.open(plan.get(0).id(), clock));

        when(sessions.findByIdAndOwner(session.id(), OWNER)).thenAnswer(call -> Optional.of(session));
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(questions.findBySession(session.id())).thenReturn(plan);
        doAnswer(call -> inserted.add(call.getArgument(0))).when(answers).insert(any());
        when(transaction.execute(any())).thenAnswer(call ->
                ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
        doAnswer(call -> {
            ((Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());

        service = new SubmitAudioAnswerService(sessions, questions, turns, answers, speech,
                new TranscriptionRetryPolicy(), transaction, clock, TIMEOUT, PAUSE, pauses::add);
    }

    private SubmitAudioAnswerCommand command(UUID turnId) {
        return new SubmitAudioAnswerCommand(session.id(), OWNER, turnId.toString(), audio());
    }

    private static AudioClip audio() {
        return new AudioClip("audio".getBytes(StandardCharsets.UTF_8), "audio/webm");
    }

    private static TranscriptionUnavailableException fails(TranscriptionFailure failure) {
        return new TranscriptionUnavailableException(failure);
    }

    // ---- CA-5.13.1 y CA-5.13.2 ----

    @Test
    @DisplayName("CA-5.13.1/2: si el primer intento falla por un error transitorio y el segundo funciona, registra una sola respuesta y avanza")
    void submit_shouldRegisterOnce_whenSecondAttemptSucceeds() {
        speech.then(fails(TranscriptionFailure.TRANSIENT)).then(new Transcription("Mi respuesta", "es"));

        AnswerResult result = service.submit(command(firstTurn.id()));

        assertThat(speech.calls).isEqualTo(2);
        assertThat(pauses).containsExactly(PAUSE);
        assertThat(inserted).singleElement().satisfies(answer -> {
            assertThat(answer.turnId()).isEqualTo(firstTurn.id());
            assertThat(answer.origin()).isEqualTo(AnswerMode.AUDIO);
            assertThat(answer.definitive()).isTrue();
            assertThat(answer.text()).isEqualTo("Mi respuesta");
            assertThat(answer.shownQuestion()).isEqualTo("Pregunta 1");
            assertThat(answer.detectedLanguage()).isEqualTo("es");
        });
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.COMPLETED);
        assertThat(result.turnId()).isEqualTo(firstTurn.id());
        assertThat(result.turnStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(result.currentQuestion()).isEqualTo(2);
        assertThat(result.totalQuestions()).isEqualTo(5);
        assertThat(result.interviewFinished()).isFalse();
        assertThat(result.nextQuestion().number()).isEqualTo(2);
        assertThat(result.nextQuestion().text()).isEqualTo("Pregunta 2");
        assertThat(turns.status(result.nextQuestion().turnId())).isEqualTo(TurnStatus.PENDING);
        assertThat(session.lastActivityAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Si el primer intento funciona no reintenta ni espera")
    void submit_shouldCallOnce_whenFirstAttemptSucceeds() {
        speech.then(new Transcription("Hola", null));

        service.submit(command(firstTurn.id()));

        assertThat(speech.calls).isEqualTo(1);
        assertThat(pauses).isEmpty();
        assertThat(inserted).hasSize(1);
    }

    @Test
    @DisplayName("Entre el intento fallido y el reintento el turno queda en RETRYING")
    void submit_shouldMarkRetrying_betweenAttempts() {
        List<TurnStatus> seenOnRetry = new ArrayList<>();
        speech.then(fails(TranscriptionFailure.TRANSIENT)).then(() -> {
            seenOnRetry.add(turns.status(firstTurn.id()));
            return new Transcription("Bien", "es");
        });

        service.submit(command(firstTurn.id()));

        assertThat(seenOnRetry).containsExactly(TurnStatus.RETRYING);
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.COMPLETED);
    }

    @Test
    @DisplayName("Con la quinta respuesta la entrevista termina y pasa a evaluación")
    void submit_shouldFinishInterview_whenLastQuestion() {
        InterviewTurn last = turns.save(InterviewTurn.open(plan.get(4).id(), clock));
        speech.then(new Transcription("Final", "es"));

        AnswerResult result = service.submit(command(last.id()));

        assertThat(result.interviewFinished()).isTrue();
        assertThat(result.nextQuestion()).isNull();
        assertThat(result.currentQuestion()).isEqualTo(5);
        assertThat(session.status()).isEqualTo(SessionStatus.EVALUATING);
    }

    // ---- CA-5.13.3 y CA-5.13.4 ----

    @Test
    @DisplayName("CA-5.13.3: si el segundo intento también falla, no hace un tercero, no registra y la pregunta queda pendiente")
    void submit_shouldGiveUp_whenSecondAttemptFails() {
        speech.then(fails(TranscriptionFailure.TRANSIENT)).then(fails(TranscriptionFailure.TRANSIENT))
                .then(new Transcription("nunca", "es"));

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TranscriptionUnavailableException.class)
                .hasMessage("No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.")
                .extracting(error -> ((TranscriptionUnavailableException) error).getErrorCode())
                .isEqualTo(ErrorCode.TRANSCRIPTION_UNAVAILABLE);

        assertThat(speech.calls).isEqualTo(2);
        verify(answers, never()).insert(any());
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
        assertThat(turns.count()).isEqualTo(1);
        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("CA-5.13.3: si el primer intento agota los 60 s, no reintenta y la pregunta queda pendiente")
    void submit_shouldNotRetry_whenFirstAttemptTimesOut() {
        speech.then(fails(TranscriptionFailure.TIMEOUT)).then(new Transcription("nunca", "es"));

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TranscriptionUnavailableException.class);

        assertThat(speech.calls).isEqualTo(1);
        assertThat(pauses).isEmpty();
        verify(answers, never()).insert(any());
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
    }

    @Test
    @DisplayName("Un fallo no transitorio (Voz rechazó el audio) no se reintenta")
    void submit_shouldNotRetry_whenRejected() {
        speech.then(fails(TranscriptionFailure.REJECTED));

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TranscriptionUnavailableException.class);

        assertThat(speech.calls).isEqualTo(1);
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
    }

    @Test
    @DisplayName("Una falla no clasificada del adaptador sale como TRANSCRIPTION_UNAVAILABLE y libera el turno")
    void submit_shouldReleaseTurn_whenAdapterFailsUnexpectedly() {
        speech.then(new IllegalStateException("sorpresa"));

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TranscriptionUnavailableException.class);

        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
    }

    @Test
    @DisplayName("CA-5.13.4: el audio solo se entrega a Voz; no se guarda en ninguna respuesta")
    void submit_shouldNotKeepAudio_whenAttemptsAreExhausted() {
        AudioClip clip = audio();
        speech.then(fails(TranscriptionFailure.TRANSIENT)).then(fails(TranscriptionFailure.TRANSIENT));

        assertThatThrownBy(() -> service.submit(
                new SubmitAudioAnswerCommand(session.id(), OWNER, firstTurn.id().toString(), clip)))
                .isInstanceOf(TranscriptionUnavailableException.class);

        assertThat(speech.received).allSatisfy(received -> assertThat(received).isSameAs(clip));
        verify(answers, never()).insert(any());
    }

    // ---- CA-5.13.5 a CA-5.13.7 ----

    @Test
    @DisplayName("CA-5.13.5/7: tras agotar los intentos, grabar de nuevo en la misma pregunta registra una sola respuesta y avanza")
    void submit_shouldAcceptNewRecording_afterExhaustedAttempts() {
        speech.then(fails(TranscriptionFailure.TRANSIENT)).then(fails(TranscriptionFailure.TRANSIENT))
                .then(new Transcription("Segunda grabación", "es"));
        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TranscriptionUnavailableException.class);

        AnswerResult result = service.submit(command(firstTurn.id()));

        assertThat(inserted).singleElement().extracting(AnswerAttempt::text).isEqualTo("Segunda grabación");
        assertThat(result.nextQuestion().number()).isEqualTo(2);
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.COMPLETED);
    }

    @Test
    @DisplayName("CA-5.13.7: una pregunta ya respondida no admite otra respuesta")
    void submit_shouldReject_whenTurnAlreadyAnswered() {
        speech.then(new Transcription("Primera", "es"));
        service.submit(command(firstTurn.id()));

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TurnAlreadyAnsweredException.class);

        assertThat(speech.calls).isEqualTo(1);
        assertThat(inserted).hasSize(1);
    }

    // ---- CA-5.13.8 ----

    @Test
    @DisplayName("CA-5.13.8: otro audio del mismo turno durante la transcripción se rechaza sin transcribirlo ni guardarlo")
    void submit_shouldRejectSecondSubmission_whileTranscribing() {
        List<Throwable> nested = new ArrayList<>();
        speech.then(() -> {
            nested.add(catchThrowable(() -> service.submit(command(firstTurn.id()))));
            return new Transcription("La primera", "es");
        });

        service.submit(command(firstTurn.id()));

        assertThat(nested).singleElement().isInstanceOf(TurnProcessingException.class);
        assertThat(speech.calls).isEqualTo(1);
        assertThat(inserted).singleElement().extracting(AnswerAttempt::text).isEqualTo("La primera");
    }

    @Test
    @DisplayName("CA-5.13.8: otro audio del mismo turno durante el reintento también se rechaza")
    void submit_shouldRejectSecondSubmission_whileRetrying() {
        List<Throwable> nested = new ArrayList<>();
        speech.then(fails(TranscriptionFailure.TRANSIENT)).then(() -> {
            nested.add(catchThrowable(() -> service.submit(command(firstTurn.id()))));
            return new Transcription("La primera", "es");
        });

        service.submit(command(firstTurn.id()));

        assertThat(nested).singleElement().isInstanceOf(TurnProcessingException.class);
        assertThat(speech.calls).isEqualTo(2);
        assertThat(inserted).hasSize(1);
    }

    @Test
    @DisplayName("CA-5.13.8: si dos envíos reservan a la vez, el que pierde la versión recibe TURN_PROCESSING")
    void submit_shouldReject_whenClaimLosesTheRace() {
        turns.failNextSaveWithConflict();

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TurnProcessingException.class);

        assertThat(speech.calls).isZero();
    }

    @Test
    @DisplayName("Una reserva caducada (proceso que murió) se libera y el envío nuevo se atiende")
    void submit_shouldTakeOverStaleClaim() {
        Instant old = NOW.minus(Duration.ofMinutes(10));
        turns.put(new TurnSnapshot(firstTurn.id(), firstTurn.questionId(), TurnStatus.PROCESSING, old, old, 3L));
        speech.then(new Transcription("Recuperada", "es"));

        service.submit(command(firstTurn.id()));

        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.COMPLETED);
    }

    // ---- Validaciones previas ----

    @Test
    @DisplayName("Una sesión ajena o inexistente responde SESSION_NOT_FOUND sin llamar a Voz")
    void submit_shouldReject_whenSessionIsNotOwned() {
        SubmitAudioAnswerCommand foreign = new SubmitAudioAnswerCommand(session.id(), new FirebaseUid("otro"),
                firstTurn.id().toString(), audio());
        when(sessions.findByIdAndOwner(session.id(), new FirebaseUid("otro"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(foreign)).isInstanceOf(SessionNotFoundException.class);
        assertThat(speech.calls).isZero();
    }

    @Test
    @DisplayName("Una sesión que no está en curso responde SESSION_TRANSITION_NOT_ALLOWED")
    void submit_shouldReject_whenSessionIsNotInProgress() {
        session.abandon(clock);

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(SessionTransitionNotAllowedException.class);
        assertThat(speech.calls).isZero();
    }

    @Test
    @DisplayName("Un turno de otra sesión, inexistente o mal escrito responde TURN_NOT_FOUND")
    void submit_shouldReject_whenTurnIsUnknown() {
        InterviewTurn foreign = turns.save(InterviewTurn.open(UUID.randomUUID(), clock));

        assertThatThrownBy(() -> service.submit(command(foreign.id()))).isInstanceOf(TurnNotFoundException.class);
        assertThatThrownBy(() -> service.submit(command(UUID.randomUUID())))
                .isInstanceOf(TurnNotFoundException.class);
        assertThatThrownBy(() -> service.submit(
                new SubmitAudioAnswerCommand(session.id(), OWNER, "no-es-uuid", audio())))
                .isInstanceOf(TurnNotFoundException.class);
        assertThat(speech.calls).isZero();
    }

    @Test
    @DisplayName("Sin turnId o sin audio responde VALIDATION_FAILED con el campo")
    void submit_shouldReject_whenFieldsAreMissing() {
        assertThatThrownBy(() -> service.submit(new SubmitAudioAnswerCommand(session.id(), OWNER, " ", audio())))
                .isInstanceOfSatisfying(InvalidRequestException.class, error -> assertThat(error.rejections())
                        .singleElement().satisfies(field -> {
                            assertThat(field.field()).isEqualTo("turnId");
                            assertThat(field.code()).isEqualTo(ErrorCode.TURN_ID_REQUIRED);
                        }));
        assertThatThrownBy(() -> service.submit(new SubmitAudioAnswerCommand(session.id(), OWNER,
                firstTurn.id().toString(), new AudioClip(new byte[0], "audio/webm"))))
                .isInstanceOfSatisfying(InvalidRequestException.class, error -> assertThat(error.rejections())
                        .singleElement().satisfies(field -> {
                            assertThat(field.field()).isEqualTo("audio");
                            assertThat(field.code()).isEqualTo(ErrorCode.AUDIO_REQUIRED);
                        }));
        assertThatThrownBy(() -> service.submit(new SubmitAudioAnswerCommand(session.id(), OWNER,
                firstTurn.id().toString(), null))).isInstanceOf(InvalidRequestException.class);
        assertThat(speech.calls).isZero();
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
    }

    // ---- Transcripción inutilizable y fallos del registro ----

    @Test
    @DisplayName("Una transcripción vacía no se registra y la pregunta queda pendiente")
    void submit_shouldReject_whenTranscriptionIsEmpty() {
        speech.then(new Transcription("   ", "es"));

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TranscriptionEmptyException.class);

        verify(answers, never()).insert(any());
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
    }

    @Test
    @DisplayName("Una transcripción de más de 4000 caracteres no se registra")
    void submit_shouldReject_whenTranscriptionIsTooLong() {
        speech.then(new Transcription("a".repeat(4001), "es"));

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(TranscriptionTooLongException.class);

        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
    }

    @Test
    @DisplayName("Si la base falla al registrar, responde DATABASE_UNAVAILABLE y libera el turno")
    void submit_shouldReleaseTurn_whenRegisteringFails() {
        speech.then(new Transcription("Bien", "es"));
        doThrow(new DataAccessResourceFailureException("caída")).when(answers).insert(any());

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(DatabaseUnavailableException.class);

        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
        verify(answers, times(1)).insert(any());
    }

    @Test
    @DisplayName("Si la entrevista dejó de estar en curso mientras se transcribía, no registra y libera el turno")
    void submit_shouldNotRegister_whenSessionChangedMeanwhile() {
        speech.then(() -> {
            session.abandon(clock);
            return new Transcription("Tarde", "es");
        });

        assertThatThrownBy(() -> service.submit(command(firstTurn.id())))
                .isInstanceOf(SessionTransitionNotAllowedException.class);

        verify(answers, never()).insert(any());
        assertThat(turns.status(firstTurn.id())).isEqualTo(TurnStatus.PENDING);
    }

    // ---- Dobles ----

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException error) {
            return error;
        }
    }

    /** Voz simulada: devuelve, en orden, las salidas programadas. */
    private static final class ScriptedSpeech implements SpeechToTextPort {
        private final Deque<Supplier<Transcription>> script = new ArrayDeque<>();
        private final List<AudioClip> received = new ArrayList<>();
        private int calls;

        ScriptedSpeech then(Transcription result) {
            script.add(() -> result);
            return this;
        }

        ScriptedSpeech then(RuntimeException failure) {
            script.add(() -> {
                throw failure;
            });
            return this;
        }

        ScriptedSpeech then(Supplier<Transcription> step) {
            script.add(step);
            return this;
        }

        @Override
        public Transcription transcribe(AudioClip audio, InterviewLanguage language) {
            calls++;
            received.add(audio);
            Supplier<Transcription> step = script.poll();
            if (step == null) {
                throw new AssertionError("Llamada a Voz no programada");
            }
            return step.get();
        }
    }

    /** Turnos en memoria con control de versión, como la base. */
    private static final class InMemoryTurns implements InterviewTurnRepository {
        private final Map<UUID, TurnSnapshot> rows = new HashMap<>();
        private boolean conflictOnNextSave;

        void failNextSaveWithConflict() {
            conflictOnNextSave = true;
        }

        void put(TurnSnapshot snapshot) {
            rows.put(snapshot.id(), snapshot);
        }

        TurnStatus status(UUID id) {
            return rows.get(id).status();
        }

        int count() {
            return rows.size();
        }

        @Override
        public InterviewTurn save(InterviewTurn turn) {
            if (conflictOnNextSave) {
                conflictOnNextSave = false;
                throw new SessionConflictException();
            }
            TurnSnapshot stored = rows.get(turn.id());
            Long storedVersion = stored == null ? null : stored.version();
            if (!java.util.Objects.equals(storedVersion, turn.version())) {
                throw new SessionConflictException();
            }
            TurnSnapshot saved = new TurnSnapshot(turn.id(), turn.questionId(), turn.status(), turn.createdAt(),
                    turn.updatedAt(), storedVersion == null ? 0L : storedVersion + 1);
            rows.put(turn.id(), saved);
            return InterviewTurn.reconstitute(saved);
        }

        @Override
        public Optional<InterviewTurn> findById(UUID id) {
            return Optional.ofNullable(rows.get(id)).map(InterviewTurn::reconstitute);
        }

        @Override
        public Optional<InterviewTurn> findOpenBySession(UUID sessionId) {
            throw new UnsupportedOperationException();
        }
    }
}
