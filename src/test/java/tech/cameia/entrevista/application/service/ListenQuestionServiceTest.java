package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.QuestionAudioLimitReachedException;
import tech.cameia.entrevista.domain.exception.QuestionNotAvailableException;
import tech.cameia.entrevista.domain.exception.QuestionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyClosedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.exception.SpeechBusyException;
import tech.cameia.entrevista.domain.exception.SpeechDisabledException;
import tech.cameia.entrevista.domain.exception.SpeechMonthlyLimitReachedException;
import tech.cameia.entrevista.domain.exception.SpeechTextEmptyException;
import tech.cameia.entrevista.domain.exception.SpeechTimeoutException;
import tech.cameia.entrevista.domain.exception.SpeechUnavailableException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionSnapshot;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.SpeechAudio;
import tech.cameia.entrevista.domain.model.SpeechDirection;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.TurnSnapshot;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.QuestionAudioPolicy;
import tech.cameia.entrevista.domain.policy.SpeechDirectionPolicy;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.SpeechDirectorPort;
import tech.cameia.entrevista.domain.port.SpeechSynthesisPort;
import tech.cameia.entrevista.domain.port.SpeechUsageRepository;

/**
 * Escuchar la pregunta (HU-5.15) con los puertos simulados: orden de las comprobaciones, contadores que se reservan y
 * se devuelven, síntesis simultáneas, cambio de mes y la entrevista que cambia mientras se genera.
 */
@ExtendWith(OutputCaptureExtension.class)
class ListenQuestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T15:00:00Z");
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");
    private static final UUID SESSION = UUID.fromString("3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10");
    private static final byte[] WAV = {'R', 'I', 'F', 'F'};

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);
    private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
    private final SpeechDirectorPort director = mock(SpeechDirectorPort.class);
    private final SpeechSynthesisPort synthesis = mock(SpeechSynthesisPort.class);
    private final MemoryUsage usage = new MemoryUsage();
    private final List<InterviewQuestion> plan = new ArrayList<>();
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Contadores en memoria con la misma semántica que las sentencias condicionadas de la base. */
    static class MemoryUsage implements SpeechUsageRepository {
        final Map<UUID, Integer> plays = new HashMap<>();
        final Map<YearMonth, Long> characters = new HashMap<>();
        volatile boolean failOnRelease;

        @Override
        public synchronized boolean reservePlay(UUID questionId, int maxPlays) {
            int used = plays.getOrDefault(questionId, 0);
            if (used >= maxPlays) {
                return false;
            }
            plays.put(questionId, used + 1);
            return true;
        }

        @Override
        public synchronized void releasePlay(UUID questionId) {
            if (failOnRelease) {
                throw new IllegalStateException("base caída");
            }
            plays.computeIfPresent(questionId, (id, used) -> Math.max(0, used - 1));
        }

        @Override
        public synchronized Map<UUID, Integer> playsBySession(UUID sessionId) {
            return Map.copyOf(plays);
        }

        @Override
        public synchronized boolean reserveCharacters(YearMonth month, int count, long cap) {
            long used = characters.getOrDefault(month, 0L);
            if (used >= cap) {
                return false;
            }
            characters.put(month, used + count);
            return true;
        }

        @Override
        public synchronized void releaseCharacters(YearMonth month, int count) {
            characters.computeIfPresent(month, (m, used) -> Math.max(0, used - count));
        }
    }

    @BeforeEach
    void prepare() {
        UUID execution = UUID.randomUUID();
        for (int position = 1; position <= 5; position++) {
            plan.add(new InterviewQuestion(UUID.randomUUID(), SESSION, position, QuestionType.TECNICA,
                    "Pregunta número " + position + " sobre Java", execution, NOW));
        }
        when(questions.findBySession(SESSION)).thenReturn(plan);
        when(director.direct(any())).thenReturn(Optional.empty());
        when(synthesis.synthesize(any(), any())).thenReturn(new SpeechAudio(WAV, "audio/wav", "gemini-3.8-flash-tts"));
        sessionWith(SessionStatus.IN_PROGRESS);
        openTurnAt(3);
    }

    private ListenQuestionService service(SpeechSettings settings) {
        return new ListenQuestionService(sessions, questions, turns, usage, director, synthesis,
                new QuestionAudioPolicy(3), new SpeechDirectionPolicy(), settings, clock);
    }

    private ListenQuestionService service() {
        return service(new SpeechSettings(true, 10, 900_000));
    }

    private InterviewSession sessionWith(SessionStatus status) {
        InterviewSession session = session(status);
        when(sessions.findByIdAndOwner(SESSION, OWNER)).thenReturn(Optional.of(session));
        return session;
    }

    private static InterviewSession session(SessionStatus status) {
        SessionConfiguration configuration = new SessionConfiguration(UUID.randomUUID(), UUID.randomUUID(), null,
                "{}", Modality.SIMULATION, Tone.STRICT, AnswerMode.TEXT, InterviewLanguage.SPANISH_COLOMBIA);
        return InterviewSession.reconstitute(new SessionSnapshot(SESSION, OWNER, configuration, status, 0, NOW, NOW,
                NOW, null, 1L));
    }

    private void openTurnAt(int position) {
        InterviewTurn turn = InterviewTurn.reconstitute(new TurnSnapshot(UUID.randomUUID(),
                plan.get(position - 1).id(), TurnStatus.PENDING, 0, NOW, NOW, 0L));
        when(turns.findOpenBySession(SESSION)).thenReturn(Optional.of(turn));
    }

    private static SessionCommand command() {
        return SessionCommand.of(SESSION.toString(), OWNER);
    }

    private int playsOf(int position) {
        return usage.plays.getOrDefault(plan.get(position - 1).id(), 0);
    }

    // ---- camino feliz ----

    @Test
    @DisplayName("CA-5.15.2: entrega el audio, gasta un uso y suma los caracteres del mes")
    void listen_shouldReturnAudioAndCountUsage() {
        SpeechAudio audio = service().listen(command(), "2");

        assertThat(audio.content()).isEqualTo(WAV);
        assertThat(playsOf(2)).isEqualTo(1);
        assertThat(usage.characters.get(YearMonth.of(2026, 11))).isEqualTo("Pregunta número 2 sobre Java".length());
    }

    @Test
    @DisplayName("El director coloca las etiquetas y la voz recibe la dirección aceptada")
    void listen_shouldSendDirectedText_whenProposalIsValid() {
        when(director.direct(any())).thenReturn(Optional.of(new SpeechDirectorPort.Proposal(
                "<grunt> Pregunta número 3 sobre Java", "urgent")));

        service().listen(command(), "3");

        verify(synthesis).synthesize(new SpeechDirection("<grunt> Pregunta número 3 sobre Java", "urgent", true),
                InterviewLanguage.SPANISH_COLOMBIA);
    }

    @Test
    @DisplayName("Una propuesta que cambia la pregunta se descarta y se lee tal cual con el estilo del tono")
    void listen_shouldReadPlainText_whenProposalChangesTheQuestion() {
        when(director.direct(any())).thenReturn(Optional.of(new SpeechDirectorPort.Proposal(
                "<grunt> Otra pregunta distinta", "urgent")));

        service().listen(command(), "1");

        verify(synthesis).synthesize(new SpeechDirection("Pregunta número 1 sobre Java", "confident", false),
                InterviewLanguage.SPANISH_COLOMBIA);
    }

    @Test
    @DisplayName("CA-5.15.18: escuchar no guarda la sesión ni el turno (no mueve la última actividad)")
    void listen_shouldNotSaveSessionOrTurn() {
        service().listen(command(), "3");

        verify(sessions, never()).save(any());
        verify(turns, never()).save(any());
    }

    // ---- orden de las comprobaciones ----

    @Test
    @DisplayName("CA-5.15.11: una sesión ajena o mal escrita responde SESSION_NOT_FOUND antes que el número")
    void listen_shouldRejectForeignSessionFirst() {
        assertThatThrownBy(() -> service().listen(SessionCommand.of(UUID.randomUUID().toString(), OWNER), "9"))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service().listen(SessionCommand.of("' OR 1=1 --", OWNER), "1"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("CA-5.15.12: el número inválido va antes que el estado de la sesión")
    void listen_shouldRejectNumberBeforeState() {
        sessionWith(SessionStatus.ABANDONED);

        assertThatThrownBy(() -> service().listen(command(), "6")).isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    @DisplayName("CA-5.15.10: una entrevista que no está en curso responde el 409 de su estado sin llamar a la voz")
    void listen_shouldRejectSessionsNotInProgress() {
        sessionWith(SessionStatus.CONFIGURED);
        assertThatThrownBy(() -> service().listen(command(), "1"))
                .isInstanceOf(SessionTransitionNotAllowedException.class);
        sessionWith(SessionStatus.ABANDONED);
        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SessionAbandonedException.class);
        sessionWith(SessionStatus.EVALUATING);
        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SessionAlreadyClosedException.class);
        sessionWith(SessionStatus.FINISHED);
        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SessionAlreadyClosedException.class);
        verify(synthesis, never()).synthesize(any(), any());
    }

    @Test
    @DisplayName("CA-5.15.9: con la voz apagada responde SPEECH_DISABLED antes de mirar la pregunta")
    void listen_shouldRejectWhenDisabled() {
        assertThatThrownBy(() -> service(new SpeechSettings(false, 10, 900_000)).listen(command(), "4"))
                .isInstanceOf(SpeechDisabledException.class)
                .hasMessage("La voz no está disponible por ahora. Puedes leer la pregunta.");
        verify(synthesis, never()).synthesize(any(), any());
    }

    @Test
    @DisplayName("CA-5.15.5: una pregunta futura no se genera ni gasta usos")
    void listen_shouldRejectFutureQuestion() {
        assertThatThrownBy(() -> service().listen(command(), "4")).isInstanceOf(QuestionNotAvailableException.class);
        assertThat(usage.plays).isEmpty();
        assertThat(usage.characters).isEmpty();
    }

    @Test
    @DisplayName("Con las cinco respondidas y la sesión aún en curso, todas se pueden escuchar")
    void listen_shouldAllowAll_whenNoTurnIsOpen() {
        when(turns.findOpenBySession(SESSION)).thenReturn(Optional.empty());

        assertThat(service().listen(command(), "5").content()).isEqualTo(WAV);
    }

    @Test
    @DisplayName("Una pregunta mostrada que falta en el plan responde QUESTION_NOT_AVAILABLE")
    void listen_shouldRejectMissingQuestion() {
        when(questions.findBySession(SESSION)).thenReturn(List.of(plan.get(0), plan.get(2)));

        assertThatThrownBy(() -> service().listen(command(), "2")).isInstanceOf(QuestionNotAvailableException.class);
    }

    @Test
    @DisplayName("CA-5.15.20: un texto en blanco no llama a la voz ni gasta usos")
    void listen_shouldRejectBlankText() {
        plan.set(0, new InterviewQuestion(plan.get(0).id(), SESSION, 1, QuestionType.TECNICA, "   ",
                plan.get(0).executionId(), NOW));

        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SpeechTextEmptyException.class);
        assertThat(usage.plays).isEmpty();
    }

    // ---- límites ----

    @Test
    @DisplayName("CA-5.15.6: la tercera escucha se entrega y la cuarta responde QUESTION_AUDIO_LIMIT_REACHED")
    void listen_shouldAllowThreePlaysPerQuestion() {
        ListenQuestionService service = service();
        service.listen(command(), "2");
        service.listen(command(), "2");
        service.listen(command(), "2");

        assertThatThrownBy(() -> service.listen(command(), "2"))
                .isInstanceOf(QuestionAudioLimitReachedException.class)
                .hasMessage("Ya escuchaste esta pregunta el máximo de veces. Puedes leerla en pantalla.");
        assertThat(playsOf(2)).isEqualTo(3);
        assertThat(usage.characters.get(YearMonth.of(2026, 11))).isEqualTo(3L * "Pregunta número 2 sobre Java".length());
    }

    @Test
    @DisplayName("CA-5.15.28: con el tope alcanzado no llama a la voz; con un carácter menos, sí")
    void listen_shouldRespectMonthlyCap() {
        usage.characters.put(YearMonth.of(2026, 11), 900_000L);
        assertThatThrownBy(() -> service().listen(command(), "1"))
                .isInstanceOf(SpeechMonthlyLimitReachedException.class);
        assertThat(usage.plays).isEmpty();

        usage.characters.put(YearMonth.of(2026, 11), 899_999L);
        assertThat(service().listen(command(), "1").content()).isEqualTo(WAV);
    }

    @Test
    @DisplayName("CA-5.15.28: el 1 de enero (UTC) vuelve a funcionar aunque diciembre esté en el tope")
    void listen_shouldResetCapOnNewMonth() {
        usage.characters.put(YearMonth.of(2026, 12), 900_000L);
        clock = Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneOffset.UTC);
        assertThatThrownBy(() -> service().listen(command(), "1"))
                .isInstanceOf(SpeechMonthlyLimitReachedException.class);

        clock = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertThat(service().listen(command(), "1").content()).isEqualTo(WAV);
        assertThat(usage.characters).containsKey(YearMonth.of(2027, 1));
    }

    @Test
    @DisplayName("CA-5.15.26: con 10 síntesis en curso, la 11.ª responde SPEECH_BUSY sin gastar usos y sin esperar")
    void listen_shouldRejectEleventhConcurrentSynthesis() throws Exception {
        when(turns.findOpenBySession(SESSION)).thenReturn(Optional.empty());
        CountDownLatch inside = new CountDownLatch(10);
        CountDownLatch release = new CountDownLatch(1);
        when(synthesis.synthesize(any(), any())).thenAnswer(call -> {
            inside.countDown();
            release.await(10, TimeUnit.SECONDS);
            return new SpeechAudio(WAV, "audio/wav", "modelo");
        });
        ListenQuestionService service = service(new SpeechSettings(true, 10, 900_000));
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<SpeechAudio>> running = new ArrayList<>();
            // 10 peticiones repartidas en las 5 preguntas: 2 por pregunta, sin llegar al límite de 3.
            for (int i = 0; i < 10; i++) {
                String number = String.valueOf(i % 5 + 1);
                running.add(pool.submit(() -> service.listen(command(), number)));
            }
            assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();
            long charactersBefore = usage.characters.get(YearMonth.of(2026, 11));

            assertThatThrownBy(() -> service.listen(command(), "1"))
                    .isInstanceOf(SpeechBusyException.class)
                    .hasMessage("Hay muchas personas escuchando. Inténtalo en unos segundos.");
            assertThat(playsOf(1)).isEqualTo(2);
            assertThat(usage.characters.get(YearMonth.of(2026, 11))).isEqualTo(charactersBefore);

            release.countDown();
            for (Future<SpeechAudio> future : running) {
                assertThat(future.get(10, TimeUnit.SECONDS).content()).isEqualTo(WAV);
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(service.listen(command(), "1").content()).isEqualTo(WAV);
    }

    @Test
    @DisplayName("CA-5.15.7: dos peticiones simultáneas con un uso restante: una se entrega y la otra no")
    void listen_shouldNeverPassLimit_whenRequestsRace() throws Exception {
        UUID question = plan.get(1).id();
        usage.plays.put(question, 2);
        ListenQuestionService service = service();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = pool.submit(() -> outcome(service, start));
            Future<String> second = pool.submit(() -> outcome(service, start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("200", "QUESTION_AUDIO_LIMIT_REACHED");
        } finally {
            pool.shutdownNow();
        }
        assertThat(usage.plays.get(question)).isEqualTo(3);
    }

    private static String outcome(ListenQuestionService service, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.listen(command(), "2");
            return "200";
        } catch (QuestionAudioLimitReachedException limit) {
            return limit.getErrorCode().name();
        }
    }

    // ---- fallos de la voz ----

    @Test
    @DisplayName("CA-5.15.8 y CA-5.15.23: si la voz falla o tarda, el uso y los caracteres vuelven")
    void listen_shouldGiveBackUsage_whenSpeechFails() {
        when(synthesis.synthesize(any(), any())).thenThrow(new SpeechUnavailableException("status=500", null));
        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SpeechUnavailableException.class);

        doThrow(new SpeechTimeoutException("tiempo agotado", null)).when(synthesis).synthesize(any(), any());
        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SpeechTimeoutException.class);

        assertThat(playsOf(1)).isZero();
        assertThat(usage.characters.get(YearMonth.of(2026, 11))).isZero();
    }

    @Test
    @DisplayName("CA-5.15.21: un fallo imprevisto responde SPEECH_UNAVAILABLE, nunca INTERNAL_ERROR")
    void listen_shouldWrapUnexpectedFailures() {
        when(synthesis.synthesize(any(), any())).thenThrow(new IllegalStateException("inesperado"));

        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SpeechUnavailableException.class);
        assertThat(playsOf(1)).isZero();
    }

    @Test
    @DisplayName("CA-5.15.23: si la base falla al devolver el uso se registra con su código y la persona ve el error de la voz")
    void listen_shouldLogWhenGiveBackFails(CapturedOutput output) {
        when(synthesis.synthesize(any(), any())).thenThrow(new SpeechUnavailableException("status=500", null));
        usage.failOnRelease = true;

        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SpeechUnavailableException.class);
        assertThat(output).contains("No se pudo devolver el uso de audio [code=SPEECH_UNAVAILABLE");
        assertThat(playsOf(1)).isBetween(0, 3);
    }

    @Test
    @DisplayName("CA-5.15.25: si la entrevista se abandona mientras se genera, no entrega el audio y el uso queda gastado")
    void listen_shouldNotDeliver_whenSessionChangesDuringSynthesis() {
        InterviewSession running = session(SessionStatus.IN_PROGRESS);
        InterviewSession abandoned = session(SessionStatus.ABANDONED);
        AtomicReference<InterviewSession> current = new AtomicReference<>(running);
        when(sessions.findByIdAndOwner(SESSION, OWNER)).thenAnswer(call -> Optional.of(current.get()));
        when(synthesis.synthesize(any(), any())).thenAnswer(call -> {
            current.set(abandoned);
            return new SpeechAudio(WAV, "audio/wav", "modelo");
        });

        assertThatThrownBy(() -> service().listen(command(), "1"))
                .isInstanceOf(SessionAbandonedException.class)
                .hasMessage("Sesión abandonada: no genera evaluación.");
        assertThat(playsOf(1)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-5.15.15: los registros no llevan el texto de la pregunta, en éxito ni en fallo")
    void listen_shouldNotLogQuestionText(CapturedOutput output) {
        when(director.direct(any())).thenReturn(Optional.of(new SpeechDirectorPort.Proposal(
                "<sigh> Pregunta número 1 sobre Java", "urgent")));
        service().listen(command(), "1");
        when(synthesis.synthesize(any(), any())).thenThrow(new SpeechUnavailableException("status=500", null));
        assertThatThrownBy(() -> service().listen(command(), "1")).isInstanceOf(SpeechUnavailableException.class);

        assertThat(output).contains("Voz de la pregunta generada").contains("Voz de la pregunta no disponible")
                .doesNotContain("sobre Java");
    }
}
