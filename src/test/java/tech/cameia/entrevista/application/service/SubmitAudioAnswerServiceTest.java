package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.application.command.AudioAnswerCommand;
import tech.cameia.entrevista.domain.exception.AudioNotAllowedException;
import tech.cameia.entrevista.domain.exception.AudioInvalidFormatException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.TranscriptionEmptyException;
import tech.cameia.entrevista.domain.exception.TranscriptionLanguageMismatchException;
import tech.cameia.entrevista.domain.exception.TranscriptionTooLongException;
import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AiExecutionStatus;
import tech.cameia.entrevista.domain.model.AnswerAttempt;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.AudioAnswer;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionSnapshot;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.model.TranscriptionExecution;
import tech.cameia.entrevista.domain.model.TurnSnapshot;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.AudioAnswerPolicy;
import tech.cameia.entrevista.domain.policy.TranscriptionPolicy;
import tech.cameia.entrevista.domain.policy.TranscriptionRetryPolicy;
import tech.cameia.entrevista.domain.model.TranscriptionFailure;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.SpeechTranscriptionPort;
import tech.cameia.entrevista.domain.port.AudioInspector;
import tech.cameia.entrevista.domain.model.AudioFormat;
import tech.cameia.entrevista.domain.model.AudioInspection;
import tech.cameia.entrevista.domain.port.TranscriptionExecutionRepository;

/**
 * Responder por voz (HU-5.12, CM-349) con dobles de los puertos: avance (CA-5.12.1), idioma (CA-5.12.2), registro
 * de la transcripción (CA-5.12.3), descarte del audio (CA-5.12.4), última pregunta (CA-5.12.6) y rechazos
 * (CA-5.12.7 a CA-5.12.11).
 */
class SubmitAudioAnswerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");
    private static final UUID SESSION = UUID.fromString("3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10");
    private static final String SPANISH_TEXT = "Lideré la migración a Spring Boot en mi último equipo.";

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final AnswerAttemptRepository attempts = mock(AnswerAttemptRepository.class);
    private final SpeechTranscriptionPort transcriber = mock(SpeechTranscriptionPort.class);
    private final TranscriptionExecutionRepository executions = mock(TranscriptionExecutionRepository.class);
    private final IdempotencyKeyRepository keys = mock(IdempotencyKeyRepository.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final AiExecutionRepository aiExecutions = mock(AiExecutionRepository.class);
    private final TurnEvaluator evaluator = mock(TurnEvaluator.class);
    private final AudioInspector inspector = mock(AudioInspector.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<InterviewQuestion> plan = new ArrayList<>();
    private final List<java.time.Duration> pauses = new ArrayList<>();
    private static final java.time.Duration PAUSE = java.time.Duration.ofSeconds(1);
    private SubmitAudioAnswerService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        UUID execution = UUID.randomUUID();
        for (int position = 1; position <= 5; position++) {
            plan.add(new InterviewQuestion(UUID.randomUUID(), SESSION, position, QuestionType.TECNICA,
                    "Pregunta " + position, execution, NOW));
        }
        when(questions.findBySession(SESSION)).thenReturn(plan);
        when(turns.save(any())).thenAnswer(call -> call.getArgument(0));
        when(transaction.execute(any())).thenAnswer(call ->
                ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
        when(transcriber.provider()).thenReturn("assemblyai");
        when(transcriber.model()).thenReturn("universal-3-5-pro,universal-2");
        when(turns.findByIdAndSession(any(), eq(SESSION))).thenAnswer(call -> turns.findById(call.getArgument(0)));
        when(aiExecutions.save(any())).thenAnswer(call -> call.getArgument(0));
        // El evaluador de la respuesta escrita (CM-216) completa el turno y abre el siguiente; aquí se simula su
        // resultado y que guarda la clave anti-duplicados.
        when(evaluator.evaluate(any())).thenAnswer(call -> {
            TurnEvaluator.EvaluationJob job = call.getArgument(0);
            int answered = job.question().position();
            AnswerResult result = answered >= 5
                    ? new AnswerResult(job.turn().id(), TurnStatus.COMPLETED, 5, 5, null, true)
                    : new AnswerResult(job.turn().id(), TurnStatus.COMPLETED, answered + 1, 5,
                            new SessionState.PendingQuestion(UUID.randomUUID(), answered + 1,
                                    "Pregunta " + (answered + 1), TurnStatus.PENDING, 0), false);
            job.recorder().succeeded(result);
            return result;
        });
        AnswerRegistrar registrar = new AnswerRegistrar(sessions, questions, turns, attempts, aiExecutions,
                transaction, clock, "modelo", java.time.Duration.ofMillis(3000), java.time.Duration.ofMillis(500),
                java.time.Duration.ofMillis(1000));
        service = new SubmitAudioAnswerService(sessions, registrar, evaluator, transcriber, executions, keys,
                new TranscriptionPolicy(0.8), new AudioAnswerPolicy(), inspector, transaction, json, clock,
                new TranscriptionRetryPolicy(java.time.Duration.ofSeconds(60), PAUSE,
                        java.time.Duration.ofSeconds(110)), pauses::add);
        when(inspector.inspect(any())).thenReturn(Optional.of(
                new AudioInspection(AudioFormat.WEBM_OPUS, java.time.Duration.ofSeconds(5))));
        org.mockito.Mockito.doAnswer(call -> {
            ((java.util.function.Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
    }

    private void sessionWith(AnswerMode mode, InterviewLanguage language, SessionStatus status) {
        SessionConfiguration configuration = new SessionConfiguration(UUID.randomUUID(), UUID.randomUUID(), null,
                "{}", Modality.SIMULATION, Tone.PROFESSIONAL_NEUTRAL, mode, language);
        InterviewSession session = InterviewSession.reconstitute(new SessionSnapshot(SESSION, OWNER, configuration,
                status, 0, NOW, NOW, NOW, null, 1L));
        when(sessions.findByIdAndOwner(SESSION, OWNER)).thenReturn(Optional.of(session));
    }

    private UUID openTurnAt(int position) {
        UUID turnId = UUID.randomUUID();
        InterviewTurn turn = InterviewTurn.reconstitute(new TurnSnapshot(turnId, plan.get(position - 1).id(),
                TurnStatus.PENDING, 0, NOW, NOW, 0L));
        when(turns.findOpenBySession(SESSION)).thenReturn(Optional.of(turn));
        when(turns.findById(turnId)).thenReturn(Optional.of(turn));
        return turnId;
    }

    private static AudioAnswerCommand command(UUID turnId, byte[] audio, String key) {
        return AudioAnswerCommand.of(SESSION.toString(), OWNER, turnId.toString(), audio, key);
    }

    private static byte[] audio() {
        return new byte[] {1, 2, 3, 4, 5};
    }

    // ---- CA-5.12.1, CA-5.12.2, CA-5.12.3 ----

    @Test
    void submit_shouldRegisterTranscriptAndReturnNextQuestion_whenQuestionKIsAnsweredByVoice() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(2);
        when(transcriber.transcribe(any(), eq(InterviewLanguage.SPANISH_COLOMBIA)))
                .thenReturn(new Transcription(" " + SPANISH_TEXT + " ", "es", 0.98));

        AnswerResult result = service.submit(command(turnId, audio(), null));

        assertThat(result.turnId()).isEqualTo(turnId);
        assertThat(result.turnStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(result.currentQuestion()).isEqualTo(3);
        assertThat(result.totalQuestions()).isEqualTo(5);
        assertThat(result.interviewFinished()).isFalse();
        assertThat(result.nextQuestion().number()).isEqualTo(3);
        assertThat(result.nextQuestion().text()).isEqualTo("Pregunta 3");

        ArgumentCaptor<AnswerAttempt> attempt = ArgumentCaptor.forClass(AnswerAttempt.class);
        verify(attempts).save(attempt.capture());
        assertThat(attempt.getValue().origin()).isEqualTo(AnswerAttempt.AnswerOrigin.AUDIO);
        assertThat(attempt.getValue().text()).isEqualTo(SPANISH_TEXT);
        assertThat(attempt.getValue().detectedLanguage()).isEqualTo("es");
        assertThat(attempt.getValue().shownQuestion()).isEqualTo("Pregunta 2");
    }

    @Test
    void submit_shouldPassInterviewLanguageToTranscriber_whenInterviewIsInEnglish() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.ENGLISH, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription("I led a migration.", "en", 0.99));

        service.submit(command(turnId, audio(), null));

        ArgumentCaptor<AudioAnswer> sent = ArgumentCaptor.forClass(AudioAnswer.class);
        verify(transcriber).transcribe(sent.capture(), eq(InterviewLanguage.ENGLISH));
        assertThat(sent.getValue().mediaType()).isEqualTo("audio/webm");
    }

    @Test
    void submit_shouldRecordFirstTranscriptionAttemptOfTheTurn_whenServiceResponds() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription(SPANISH_TEXT, "es", 0.98));

        service.submit(command(turnId, audio(), null));

        ArgumentCaptor<TranscriptionExecution> execution = ArgumentCaptor.forClass(TranscriptionExecution.class);
        verify(executions).save(execution.capture());
        assertThat(execution.getValue().turnId()).isEqualTo(turnId);
        assertThat(execution.getValue().sessionId()).isEqualTo(SESSION);
        assertThat(execution.getValue().attempt()).isEqualTo(1);
        assertThat(execution.getValue().status()).isEqualTo(AiExecutionStatus.SUCCEEDED);
        assertThat(execution.getValue().provider()).isEqualTo("assemblyai");
    }

    // ---- CA-5.12.4 ----

    @Test
    void submit_shouldWipeAudioBytes_whenRequestEndsWithSuccess() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription(SPANISH_TEXT, "es", 0.98));
        byte[] audio = audio();

        service.submit(command(turnId, audio, null));

        assertThat(audio).containsOnly((byte) 0);
    }

    @Test
    void submit_shouldWipeAudioBytes_whenRequestFails() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription("  ", "es", 0.98));
        byte[] audio = audio();

        assertThatThrownBy(() -> service.submit(command(turnId, audio, null)))
                .isInstanceOf(TranscriptionEmptyException.class);
        assertThat(audio).containsOnly((byte) 0);
    }

    // ---- CA-5.12.6 ----

    @Test
    void submit_shouldFinishWithoutNextQuestion_whenQuestion5IsAnswered() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(5);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription(SPANISH_TEXT, "es", 0.98));

        AnswerResult result = service.submit(command(turnId, audio(), null));

        assertThat(result.nextQuestion()).isNull();
        assertThat(result.interviewFinished()).isTrue();
        assertThat(result.currentQuestion()).isEqualTo(5);
        // El servicio solo reserva el turno; completar y cerrar la sesión lo hace el evaluador de CM-216/CM-220.
        verify(turns).save(any());
        verify(turns, never()).save(org.mockito.ArgumentMatchers.argThat(turn -> !turn.id().equals(turnId)));
    }

    // ---- CA-5.12.7, 5.12.9, 5.12.10, 5.12.11 ----

    @Test
    void submit_shouldNotRegisterAnswer_whenTranscriptIsEmpty() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription("", "es", null));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(TranscriptionEmptyException.class);
        verify(attempts, never()).save(any());
        // La pregunta sigue pendiente: el turno reservado vuelve a PENDING.
        assertThat(turns.findById(turnId).orElseThrow().status()).isEqualTo(TurnStatus.PENDING);
        // La transcripción sí respondió: queda registrada como exitosa (CA-5.12.3).
        verify(executions).save(any());
    }

    @Test
    void submit_shouldNotRegisterAnswer_whenTranscriptHas4001Characters() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription("a".repeat(4001), "es", 0.98));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(TranscriptionTooLongException.class);
        verify(attempts, never()).save(any());
    }

    @Test
    void submit_shouldRegister_whenTranscriptHasExactly4000Characters() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription("a".repeat(4000), "es", 0.98));

        AnswerResult result = service.submit(command(turnId, audio(), null));

        assertThat(result.currentQuestion()).isEqualTo(2);
        verify(attempts).save(any());
    }

    @Test
    void submit_shouldNotRegisterAnswer_whenEnglishInterviewIsAnsweredInSpanish() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.ENGLISH, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription(SPANISH_TEXT, "es", 0.97));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(TranscriptionLanguageMismatchException.class)
                .hasMessage("Tu entrevista es en inglés. Responde en inglés.");
        verify(attempts, never()).save(any());
    }

    // ---- CA-5.12.8 ----

    @Test
    void submit_shouldRecordFailedAttemptAndNotAdvance_whenTranscriptionServiceFails() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any()))
                .thenThrow(new TranscriptionUnavailableException("tiempo agotado", null));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(TranscriptionUnavailableException.class)
                .hasMessage("No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.");
        ArgumentCaptor<TranscriptionExecution> execution = ArgumentCaptor.forClass(TranscriptionExecution.class);
        verify(executions).save(execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(AiExecutionStatus.FAILED);
        assertThat(execution.getValue().errorCode()).isEqualTo("TRANSCRIPTION_UNAVAILABLE");
        verify(attempts, never()).save(any());
        // La pregunta sigue pendiente: el turno reservado vuelve a PENDING.
        assertThat(turns.findById(turnId).orElseThrow().status()).isEqualTo(TurnStatus.PENDING);
    }

    // ---- Validación previa a la transcripción ----

    @Test
    void submit_shouldReturnNotFound_whenSessionIsNotOwned() {
        when(sessions.findByIdAndOwner(SESSION, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(command(UUID.randomUUID(), audio(), null)))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void submit_shouldRejectSession_whenNotInProgress() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.ABANDONED);

        assertThatThrownBy(() -> service.submit(command(UUID.randomUUID(), audio(), null)))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.SessionAbandonedException.class);
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.EVALUATING);
        assertThatThrownBy(() -> service.submit(command(UUID.randomUUID(), audio(), null)))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException.class);
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.CONFIGURED);
        assertThatThrownBy(() -> service.submit(command(UUID.randomUUID(), audio(), null)))
                .isInstanceOf(SessionTransitionNotAllowedException.class);
        verify(transcriber, never()).transcribe(any(), any());
    }

    @Test
    void submit_shouldRejectAllMissingFieldsAtOnce() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.submit(AudioAnswerCommand.of(SESSION.toString(), OWNER, " ", new byte[0],
                "clave con espacios")))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(error -> ((InvalidRequestException) error).rejections().stream()
                        .map(FieldRejection::code).toList())
                .isEqualTo(List.of(ErrorCode.TURN_ID_REQUIRED, ErrorCode.AUDIO_REQUIRED,
                        ErrorCode.IDEMPOTENCY_KEY_INVALID_FORMAT));
    }

    @Test
    void submit_shouldRejectTurnId_whenNotUuid() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.submit(AudioAnswerCommand.of(SESSION.toString(), OWNER, "1-1-1-1-1",
                audio(), null)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(error -> ((InvalidRequestException) error).rejections().get(0).code())
                .isEqualTo(ErrorCode.TURN_ID_INVALID_FORMAT);
    }

    @Test
    void submit_shouldRejectTurn_whenItIsNotThePendingOne() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        openTurnAt(2);

        // Un turno que no es de la sesión no revela si existe (CA-5.8.21).
        assertThatThrownBy(() -> service.submit(command(UUID.randomUUID(), audio(), null)))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.TurnNotFoundException.class);
        verify(transcriber, never()).transcribe(any(), any());
    }

    @Test
    void submit_shouldRejectAudioType_whenNotAudio() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        // El formato se decide por el contenido real, no por el tipo declarado (CA-5.8.14).
        when(inspector.inspect(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(AudioAnswerCommand.of(SESSION.toString(), OWNER, turnId.toString(),
                audio(), null)))
                .isInstanceOf(AudioInvalidFormatException.class);
        verify(transcriber, never()).transcribe(any(), any());
    }

    @Test
    void submit_shouldRejectAudio_whenInterviewIsAnsweredByText() {
        sessionWith(AnswerMode.TEXT, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(AudioNotAllowedException.class);
        verify(transcriber, never()).transcribe(any(), any());
    }

    // ---- Idempotency-Key ----

    @Test
    void submit_shouldStoreResponse_whenKeyIsSent() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenReturn(new Transcription(SPANISH_TEXT, "es", 0.98));

        service.submit(command(turnId, audio(), "clave-1"));

        ArgumentCaptor<IdempotencyKey> saved = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(keys).save(saved.capture());
        assertThat(saved.getValue().operation()).isEqualTo("RESPONDER_AUDIO");
        assertThat(saved.getValue().responseStatus()).isEqualTo(200);
        assertThat(saved.getValue().requestFingerprint())
                .isEqualTo(SubmitAudioAnswerService.fingerprint(SESSION, turnId, audio()));
    }

    @Test
    void submit_shouldReplayStoredResponseWithoutTranscribing_whenSameKeyAndSameAudio() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = UUID.randomUUID();
        AnswerResult stored = new AnswerResult(turnId, TurnStatus.COMPLETED, 2, 5,
                new SessionState.PendingQuestion(UUID.randomUUID(), 2, "Pregunta 2", TurnStatus.PENDING, 0), false);
        when(keys.find(OWNER, "RESPONDER_AUDIO", "clave-1")).thenReturn(Optional.of(new IdempotencyKey(
                UUID.randomUUID(), OWNER, "RESPONDER_AUDIO", "clave-1",
                SubmitAudioAnswerService.fingerprint(SESSION, turnId, audio()), 200,
                json.writeValueAsString(stored), NOW)));

        AnswerResult replay = service.submit(command(turnId, audio(), "clave-1"));

        assertThat(replay).isEqualTo(stored);
        verify(transcriber, never()).transcribe(any(), any());
    }

    @Test
    void submit_shouldRejectKey_whenReusedWithAnotherAudio() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = UUID.randomUUID();
        when(keys.find(OWNER, "RESPONDER_AUDIO", "clave-1")).thenReturn(Optional.of(new IdempotencyKey(
                UUID.randomUUID(), OWNER, "RESPONDER_AUDIO", "clave-1",
                SubmitAudioAnswerService.fingerprint(SESSION, turnId, new byte[] {9}), 200, "{}", NOW)));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), "clave-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    // ---- Integración de HU-5.8 con HU-5.12: turno de la sesión ya respondido, doble envío, tamaño y liberación ----

    @Test
    void submit_shouldRejectTurn_whenTurnOfTheSessionIsNotTheOpenOne() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        openTurnAt(2);
        UUID answered = UUID.randomUUID();
        when(turns.findById(answered)).thenReturn(Optional.of(InterviewTurn.reconstitute(new TurnSnapshot(answered,
                plan.get(0).id(), TurnStatus.COMPLETED, 0, NOW, NOW, 2L))));

        assertThatThrownBy(() -> service.submit(command(answered, audio(), null)))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.AnswerAlreadyExistsException.class);
        verify(transcriber, never()).transcribe(any(), any());
    }

    @Test
    void submit_shouldRejectSecondSubmission_whenTurnIsBeingProcessed() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = UUID.randomUUID();
        InterviewTurn busy = InterviewTurn.reconstitute(new TurnSnapshot(turnId, plan.get(0).id(),
                TurnStatus.PROCESSING, 0, NOW, NOW, 1L));
        when(turns.findOpenBySession(SESSION)).thenReturn(Optional.of(busy));
        when(turns.findById(turnId)).thenReturn(Optional.of(busy));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.TurnConflictException.class);
        verify(transcriber, never()).transcribe(any(), any());
    }

    @Test
    void submit_shouldRejectAudio_whenLargerThanTenMebibytes() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);

        assertThatThrownBy(() -> service.submit(command(turnId, new byte[10_485_761], null)))
                .isInstanceOf(tech.cameia.entrevista.domain.exception.AudioTooLargeException.class);
        verify(transcriber, never()).transcribe(any(), any());
    }

    @Test
    void submit_shouldLeaveQuestionPendingAsRetrying_whenTranscriptionFails() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any()))
                .thenThrow(new TranscriptionUnavailableException("estado error", null));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(TranscriptionUnavailableException.class);

        assertThat(turns.findById(turnId).orElseThrow().status()).isEqualTo(TurnStatus.PENDING);
        verify(attempts, never()).save(any());
    }

    // ---- HU-5.13: reintento automático de la transcripción ----

    private static TranscriptionUnavailableException failing(TranscriptionFailure failure) {
        return new TranscriptionUnavailableException(failure, "simulado", null);
    }

    @Test
    void submit_shouldRetryOnceAndRegisterOneAnswer_whenFirstAttemptFailsTransiently() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenThrow(failing(TranscriptionFailure.TRANSIENT))
                .thenReturn(new Transcription(SPANISH_TEXT, "es", 0.98));

        AnswerResult result = service.submit(command(turnId, audio(), null));

        // CA-5.13.1 y CA-5.13.2: una sola respuesta, sin que la persona haga nada, y la entrevista sigue.
        verify(transcriber, org.mockito.Mockito.times(2)).transcribe(any(), any());
        verify(attempts).save(any());
        assertThat(result.nextQuestion().number()).isEqualTo(2);
        assertThat(pauses).containsExactly(PAUSE);
        ArgumentCaptor<TranscriptionExecution> saved = ArgumentCaptor.forClass(TranscriptionExecution.class);
        verify(executions, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(TranscriptionExecution::attempt, TranscriptionExecution::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, AiExecutionStatus.FAILED),
                        org.assertj.core.groups.Tuple.tuple(2, AiExecutionStatus.SUCCEEDED));
    }

    @Test
    void submit_shouldGiveUpWithoutThirdAttempt_whenSecondAttemptAlsoFails() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenThrow(failing(TranscriptionFailure.TRANSIENT),
                failing(TranscriptionFailure.TRANSIENT)).thenReturn(new Transcription(SPANISH_TEXT, "es", 0.98));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(TranscriptionUnavailableException.class)
                .hasMessage("No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.");

        // CA-5.13.3: no hay tercer intento, no se registra nada y la pregunta sigue pendiente.
        verify(transcriber, org.mockito.Mockito.times(2)).transcribe(any(), any());
        verify(attempts, never()).save(any());
        assertThat(turns.findById(turnId).orElseThrow().status()).isEqualTo(TurnStatus.PENDING);
    }

    @Test
    void submit_shouldNotRetry_whenFirstAttemptTimesOut() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenThrow(failing(TranscriptionFailure.TIMEOUT));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(TranscriptionUnavailableException.class);

        verify(transcriber).transcribe(any(), any());
        assertThat(pauses).isEmpty();
        assertThat(turns.findById(turnId).orElseThrow().status()).isEqualTo(TurnStatus.PENDING);
    }

    @Test
    void submit_shouldNotRetry_whenProviderRejectsTheAudio() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(transcriber.transcribe(any(), any())).thenThrow(failing(TranscriptionFailure.REJECTED));

        assertThatThrownBy(() -> service.submit(command(turnId, audio(), null)))
                .isInstanceOf(TranscriptionUnavailableException.class);

        verify(transcriber).transcribe(any(), any());
    }

    @Test
    void submit_shouldKeepTurnReservedDuringRetry_soASecondSubmissionIsRejected() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        List<Throwable> secondSubmission = new ArrayList<>();
        when(transcriber.transcribe(any(), any())).thenThrow(failing(TranscriptionFailure.TRANSIENT))
                .thenAnswer(call -> {
                    try {
                        service.submit(command(turnId, audio(), null));
                    } catch (RuntimeException rejected) {
                        secondSubmission.add(rejected);
                    }
                    return new Transcription(SPANISH_TEXT, "es", 0.98);
                });

        service.submit(command(turnId, audio(), null));

        // CA-5.13.8: durante el reintento el turno sigue en PROCESSING; el segundo envío no se transcribe.
        assertThat(secondSubmission).singleElement()
                .isInstanceOf(tech.cameia.entrevista.domain.exception.TurnConflictException.class);
        verify(transcriber, org.mockito.Mockito.times(2)).transcribe(any(), any());
        verify(attempts).save(any());
    }
}
