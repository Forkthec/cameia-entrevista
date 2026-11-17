package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiExecutionStatus;
import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.ContentScores;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.ReportQuestion;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.StarResult;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.VoiceMetrics;
import tech.cameia.entrevista.domain.model.VoiceSample;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.EvaluationReportRepository;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.Waiter;

/** La generación del reporte: reserva, llamada a la IA, cierre y cada camino de falla. */
class ReportGeneratorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T15:04:05Z"), ZoneOffset.UTC);

    private static final UUID ROLE_ID = UUID.randomUUID();

    private static final String VALID = """
            {"contenido":[
              {"numero":1,"precision_claridad":5,"carisma_verbal":5,"lenguaje_adecuado":5},
              {"numero":2,"precision_claridad":1,"carisma_verbal":2,"lenguaje_adecuado":3},
              {"numero":4,"precision_claridad":4,"carisma_verbal":2,"lenguaje_adecuado":1}],
             "star":[
              {"numero":3,"situacion":true,"tarea":true,"accion":true,"resultado":true},
              {"numero":5,"situacion":true,"tarea":true,"accion":true,"resultado":false}]}""";

    private final AiExecutionRepository executions = mock(AiExecutionRepository.class);

    private final AnswerAttemptRepository attempts = mock(AnswerAttemptRepository.class);

    private final EvaluationReportRepository reports = mock(EvaluationReportRepository.class);

    private final ScriptedPort port = new ScriptedPort();

    private final JsonMapper json = JsonMapper.builder().build();

    private final InterviewSession session = session();

    private ReportGenerator generator;

    @BeforeEach
    void setUp() {
        when(executions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(executions.failIfInProgress(any())).thenReturn(true);
        when(reports.saveIssued(any())).thenAnswer(call -> call.getArgument(0));
        when(attempts.reportInputsOf(session.id())).thenReturn(inputs(null));
        generator = newGenerator(Duration.ofMinutes(2));
    }

    private ReportGenerator newGenerator(Duration claimTtl) {
        AiCallRunner runner = new AiCallRunner(port, duration -> { }, Duration.ofMillis(500), Duration.ofSeconds(1),
                CLOCK);
        return new ReportGenerator(executions, attempts, reports, runner, new ReportPromptBuilder(),
                new ReportOutputReader(json), new TransactionTemplate(new NoopTransactions()), json, CLOCK, "modelo",
                "respaldo", Duration.ofSeconds(20), Duration.ofMillis(500), Duration.ofSeconds(1), claimTtl);
    }

    private static InterviewSession session() {
        String snapshot = "{\"rolesAspirados\":[{\"codigo\":\"" + ROLE_ID + "\",\"nombre\":\"Desarrolladora backend\"}]}";
        SessionConfiguration configuration = new SessionConfiguration(UUID.randomUUID(), ROLE_ID,
                "Buscamos backend con Java.", snapshot, Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT,
                InterviewLanguage.ENGLISH);
        return InterviewSession.configure(UUID.randomUUID(), new FirebaseUid("uid-reporte"), configuration, CLOCK);
    }

    private static List<ReportQuestion> inputs(VoiceSample voiceOfTheFirst) {
        QuestionType[] types = {QuestionType.APERTURA, QuestionType.TECNICA, QuestionType.CONDUCTUAL,
            QuestionType.SITUACIONAL, QuestionType.CONDUCTUAL};
        List<ReportQuestion> list = new ArrayList<>();
        for (int number = 1; number <= 5; number++) {
            boolean voice = number == 1 && voiceOfTheFirst != null;
            list.add(new ReportQuestion(number, types[number - 1], "pregunta " + number, "respuesta " + number,
                    voice ? AnswerOrigin.AUDIO : AnswerOrigin.TEXTO, voice ? voiceOfTheFirst : null));
        }
        return list;
    }

    private static LanguageModelResponse response(String text) {
        return new LanguageModelResponse(text, 100, 50, "modelo");
    }

    private List<AiExecution> savedExecutions() {
        ArgumentCaptor<AiExecution> captor = ArgumentCaptor.forClass(AiExecution.class);
        verify(executions, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private EvaluationReport savedReport() {
        ArgumentCaptor<EvaluationReport> captor = ArgumentCaptor.forClass(EvaluationReport.class);
        verify(reports).saveIssued(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Una llamada con la operación del reporte da las métricas de contenido y el STAR")
    void generate_shouldMakeOneCallWithTheReportOperation() {
        port.script(response(VALID));

        EvaluationReport report = generator.generate(session).orElseThrow();

        assertThat(port.prompts).hasSize(1);
        LanguageModelPrompt prompt = port.prompts.get(0);
        assertThat(prompt.operation()).isEqualTo(AiOperation.REPORT);
        assertThat(prompt.text()).contains("Desarrolladora backend").contains("Buscamos backend con Java.")
                .contains("Idioma de la entrevista: en");
        assertThat(report.content()).isEqualTo(new ContentScores(new BigDecimal("3.33"), new BigDecimal("3.00"),
                new BigDecimal("3.00")));
        assertThat(report.star()).extracting(StarResult::questionNumber).containsExactly(3, 5);
        assertThat(report.star()).extracting(StarResult::level).containsExactly(4, 3);
        assertThat(report.issuedAt()).isEqualTo(Instant.parse("2026-11-17T15:04:05Z"));
        assertThat(savedExecutions()).extracting(AiExecution::status)
                .containsExactly(AiExecutionStatus.IN_PROGRESS, AiExecutionStatus.SUCCEEDED);
        AiExecution closed = savedExecutions().get(1);
        assertThat(closed.operation()).isEqualTo(AiOperation.REPORT);
        assertThat(closed.promptVersion()).isEqualTo("reporte.v1");
        assertThat(closed.inputTokens()).isEqualTo(100);
        assertThat(closed.turnId()).isNull();
    }

    @Test
    @DisplayName("Si la IA falla tres veces se emite el reporte sin contenido ni STAR y con la voz")
    void exhaustedCycle_shouldEmitWithNullContentAndStarAndKeepVoice() {
        when(attempts.reportInputsOf(session.id())).thenReturn(inputs(new VoiceSample(120, 60000, 6000, 2)));
        port.script(new LlmUnavailableException(null), new LlmUnavailableException(null),
                new LlmUnavailableException(null));

        EvaluationReport report = generator.generate(session).orElseThrow();

        assertThat(report.content()).isEqualTo(ContentScores.NONE);
        assertThat(report.star()).hasSize(2).allSatisfy(star -> assertThat(star.level()).isNull());
        assertThat(report.voice()).isEqualTo(new VoiceMetrics(120, new BigDecimal("10.0"), new BigDecimal("1.7")));
        assertThat(port.providers).containsExactly(AiProvider.PRIMARY, AiProvider.PRIMARY, AiProvider.FALLBACK);
        AiExecution last = savedExecutions().get(savedExecutions().size() - 1);
        assertThat(last.status()).isEqualTo(AiExecutionStatus.FAILED);
        assertThat(last.errorCode()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(last.attempt()).isEqualTo(3);
    }

    @Test
    @DisplayName("Una salida inválida y luego una válida usan el segundo intento")
    void invalidOutputThenValid_shouldUseTheSecondAttempt() {
        port.script(response("no es json"), response(VALID));

        EvaluationReport report = generator.generate(session).orElseThrow();

        assertThat(report.content().precision()).isEqualByComparingTo("3.33");
        assertThat(port.prompts).hasSize(2);
        verify(executions, times(1)).failIfInProgress(any());
        AiExecution last = savedExecutions().get(savedExecutions().size() - 1);
        assertThat(last.attempt()).isEqualTo(2);
        assertThat(last.status()).isEqualTo(AiExecutionStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("Si todas las preguntas son conductuales el contenido queda sin resultado y el STAR se conserva")
    void onlyBehavioralQuestions_shouldGiveNullContentAndKeepStar() {
        List<ReportQuestion> all = new ArrayList<>();
        for (int number = 1; number <= 5; number++) {
            all.add(new ReportQuestion(number, QuestionType.CONDUCTUAL, "p" + number, "r" + number,
                    AnswerOrigin.TEXTO, null));
        }
        when(attempts.reportInputsOf(session.id())).thenReturn(all);
        String star = "{\"numero\":%d,\"situacion\":true,\"tarea\":false,\"accion\":true,\"resultado\":false}";
        port.script(response("{\"contenido\":[],\"star\":[" + String.join(",", star.formatted(1), star.formatted(2),
                star.formatted(3), star.formatted(4), star.formatted(5)) + "]}"));

        EvaluationReport report = generator.generate(session).orElseThrow();

        assertThat(report.content()).isEqualTo(ContentScores.NONE);
        assertThat(report.star()).hasSize(5).allSatisfy(item -> assertThat(item.level()).isEqualTo(2));
    }

    @Test
    @DisplayName("Las preguntas no conductuales nunca aparecen en el STAR")
    void nonBehavioralQuestions_shouldNotAppearInStar() {
        port.script(response(VALID));

        EvaluationReport report = generator.generate(session).orElseThrow();

        assertThat(report.star()).extracting(StarResult::questionNumber).doesNotContain(1, 2, 4);
    }

    @Test
    @DisplayName("Una sesión solo por texto no tiene métricas de voz")
    void textOnlySession_shouldHaveNullVoice() {
        port.script(response(VALID));

        EvaluationReport report = generator.generate(session).orElseThrow();

        assertThat(report.voice()).isEqualTo(VoiceMetrics.NONE);
    }

    @Test
    @DisplayName("Con una marca viva no se llama a la IA y se devuelve vacío")
    void liveMark_shouldReturnEmptyWithoutCallingTheAi() {
        AiExecution live = AiExecution.reportStarted(session.id(), 1, "modelo", "reporte.v1", "gemini",
                Clock.fixed(Instant.parse("2026-11-17T15:03:55Z"), ZoneOffset.UTC));
        when(executions.findReportInProgress(session.id())).thenReturn(Optional.of(live));

        assertThat(generator.generate(session)).isEmpty();

        assertThat(port.prompts).isEmpty();
        verify(executions, never()).save(any());
        verify(reports, never()).saveIssued(any());
    }

    @Test
    @DisplayName("Una marca vencida se cierra como fallida y se retoma")
    void staleMark_shouldBeClosedAndRetaken() {
        AiExecution stale = AiExecution.reportStarted(session.id(), 1, "modelo", "reporte.v1", "gemini",
                Clock.fixed(Instant.parse("2026-11-17T15:01:00Z"), ZoneOffset.UTC));
        when(executions.findReportInProgress(session.id())).thenReturn(Optional.of(stale));
        port.script(response(VALID));

        assertThat(generator.generate(session)).isPresent();

        List<AiExecution> saved = savedExecutions();
        assertThat(saved.get(0).status()).isEqualTo(AiExecutionStatus.FAILED);
        assertThat(saved.get(0).errorCode()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(saved.get(0).id()).isEqualTo(stale.id());
        assertThat(saved.get(1).status()).isEqualTo(AiExecutionStatus.IN_PROGRESS);
        assertThat(saved.get(1).id()).isNotEqualTo(stale.id());
    }

    @Test
    @DisplayName("Un reporte ya emitido se devuelve sin llamar a la IA ni abrir una marca")
    void existingReport_shouldBeReturnedWithoutCallingTheAi() {
        EvaluationReport stored = new EvaluationReport(session.id(), InterviewLanguage.ENGLISH, ContentScores.NONE,
                VoiceMetrics.NONE, List.of(), CLOCK.instant());
        when(reports.findBySession(session.id())).thenReturn(Optional.of(stored));

        assertThat(generator.generate(session)).contains(stored);

        assertThat(port.prompts).isEmpty();
        verify(executions, never()).save(any());
        verify(reports, never()).saveIssued(any());
    }

    @Test
    @DisplayName("Si guardar falla se cierra la marca como fallida y se responde que la base no está disponible")
    void saveFails_shouldCloseTheMarkAndThrowDatabaseUnavailable() {
        port.script(response(VALID));
        when(reports.saveIssued(any())).thenThrow(new DataAccessResourceFailureException("caída"));

        assertThatThrownBy(() -> generator.generate(session)).isInstanceOf(DatabaseUnavailableException.class);

        ArgumentCaptor<AiExecution> closed = ArgumentCaptor.forClass(AiExecution.class);
        verify(executions).failIfInProgress(closed.capture());
        assertThat(closed.getValue().errorCode()).isEqualTo(ErrorCode.DATABASE_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("Si otro proceso emitió el reporte primero se devuelve el guardado y se cierra la marca propia")
    void concurrentIssue_shouldReturnTheStoredReport() {
        EvaluationReport stored = new EvaluationReport(session.id(), InterviewLanguage.ENGLISH, ContentScores.NONE,
                VoiceMetrics.NONE, List.of(), CLOCK.instant().minusSeconds(5));
        when(reports.findBySession(session.id())).thenReturn(Optional.empty(), Optional.of(stored));
        when(reports.saveIssued(any())).thenThrow(new EvaluationReportRepository.AlreadyIssuedException());
        port.script(response(VALID));

        assertThat(generator.generate(session)).contains(stored);

        AiExecution last = savedExecutions().get(savedExecutions().size() - 1);
        assertThat(last.status()).isEqualTo(AiExecutionStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("Sin preguntas respondidas no se llama a la IA y todo queda sin resultado")
    void noAnsweredQuestions_shouldNotCallTheAi() {
        when(attempts.reportInputsOf(session.id())).thenReturn(List.of());

        EvaluationReport report = generator.generate(session).orElseThrow();

        assertThat(port.prompts).isEmpty();
        assertThat(report.content()).isEqualTo(ContentScores.NONE);
        assertThat(report.voice()).isEqualTo(VoiceMetrics.NONE);
        assertThat(report.star()).isEmpty();
        verify(executions, never()).save(any());
    }

    @Test
    @DisplayName("Si el índice único rechaza la marca, otra petición está generando y se devuelve vacío")
    void reservationConflict_shouldReturnEmpty() {
        when(executions.save(any())).thenThrow(new SessionConflictException());

        assertThat(generator.generate(session)).isEmpty();

        assertThat(port.prompts).isEmpty();
    }

    @Test
    @DisplayName("Si otra petición tomó la marca durante el ciclo, no se guarda nada y se devuelve vacío")
    void markTakenDuringTheCycle_shouldReturnEmpty() {
        when(executions.failIfInProgress(any())).thenReturn(false);
        port.script(new LlmUnavailableException(null));

        assertThat(generator.generate(session)).isEmpty();

        verify(reports, never()).saveIssued(any());
    }

    @Test
    @DisplayName("Un tiempo de marca que no supera el peor ciclo de la IA impide arrancar")
    void claimTtl_shouldExceedTheWorstCycle() {
        assertThatThrownBy(() -> newGenerator(Duration.ofMillis(61_500))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REPORT_GENERATION_CLAIM_TTL");
        assertThat(newGenerator(Duration.ofMillis(61_501))).isNotNull();
    }

    /** Transacciones que no hacen nada: la prueba comprueba la lógica, no el motor de la base. */
    private static final class NoopTransactions implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // Sin efecto.
        }

        @Override
        public void rollback(TransactionStatus status) {
            // Sin efecto.
        }
    }

    /** Modelo guionado: cada llamada consume la siguiente respuesta o lanza la siguiente excepción. */
    private static final class ScriptedPort implements LanguageModelPort {

        final List<LanguageModelPrompt> prompts = new ArrayList<>();

        final List<AiProvider> providers = new ArrayList<>();

        private final Deque<Object> script = new ArrayDeque<>();

        void script(Object... steps) {
            script.addAll(List.of(steps));
        }

        @Override
        public LanguageModelResponse generate(LanguageModelPrompt prompt, AiProvider provider) {
            prompts.add(prompt);
            providers.add(provider);
            Object step = script.removeFirst();
            if (step instanceof RuntimeException failure) {
                throw failure;
            }
            return (LanguageModelResponse) step;
        }

        @Override
        public boolean isAvailable(AiProvider provider) {
            return true;
        }
    }
}
