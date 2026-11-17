package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionNotFinishedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.ContentScores;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.ReportQuestion;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionSnapshot;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.VoiceMetrics;
import tech.cameia.entrevista.domain.model.VoiceSample;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.EvaluationReportRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;

/** Consultar el reporte: dueño, estado de la sesión, reporte existente, generación y base caída. */
class GetEvaluationReportServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10");

    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");

    private static final Instant ENDED = Instant.parse("2026-11-17T14:58:40.987Z");

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);

    private final InterviewQuestionRepository questions = mock(InterviewQuestionRepository.class);

    private final AnswerAttemptRepository attempts = mock(AnswerAttemptRepository.class);

    private final EvaluationReportRepository reports = mock(EvaluationReportRepository.class);

    private final ReportGenerator generator = mock(ReportGenerator.class);

    private GetEvaluationReportService service;

    private final EvaluationReport report = new EvaluationReport(SESSION_ID, InterviewLanguage.ENGLISH,
            ContentScores.NONE, VoiceMetrics.NONE, List.of(), Instant.parse("2026-11-17T15:04:05Z"));

    @BeforeEach
    void setUp() {
        service = new GetEvaluationReportService(sessions, questions, attempts, reports, generator);
    }

    private InterviewSession sessionIn(SessionStatus status) {
        SessionConfiguration configuration = new SessionConfiguration(UUID.randomUUID(), UUID.randomUUID(), null,
                "{}", Modality.SIMULATION, Tone.STRICT, AnswerMode.TEXT, InterviewLanguage.ENGLISH);
        Instant start = Instant.parse("2026-11-17T14:00:00Z");
        InterviewSession session = InterviewSession.reconstitute(new SessionSnapshot(SESSION_ID, OWNER, configuration,
                status, 0, start, start, null, status == SessionStatus.FINISHED ? ENDED : null, 1L));
        when(sessions.findByIdAndOwner(SESSION_ID, OWNER)).thenReturn(Optional.of(session));
        return session;
    }

    private static InterviewQuestion question(int position, QuestionType type) {
        return new InterviewQuestion(UUID.randomUUID(), SESSION_ID, position, type, "pregunta " + position,
                UUID.randomUUID(), Instant.parse("2026-11-17T14:00:00Z"));
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"CONFIGURED", "IN_PROGRESS", "EVALUATING"})
    @DisplayName("Una sesión sin terminar responde que no terminó y nunca llama a la IA")
    void notFinished_shouldNotCallTheAi(SessionStatus status) {
        sessionIn(status);

        assertThatThrownBy(() -> service.report(new SessionCommand(SESSION_ID, OWNER)))
                .isInstanceOf(SessionNotFinishedException.class).hasMessage("Tu entrevista aún no ha terminado.");

        verifyNoInteractions(generator, reports, attempts);
    }

    @Test
    @DisplayName("Una sesión abandonada responde SESSION_ABANDONED")
    void abandoned_shouldThrowSessionAbandoned() {
        sessionIn(SessionStatus.ABANDONED);

        assertThatThrownBy(() -> service.report(new SessionCommand(SESSION_ID, OWNER)))
                .isInstanceOf(SessionAbandonedException.class);

        verifyNoInteractions(generator, reports);
    }

    @Test
    @DisplayName("Una sesión ajena, inexistente o con identificador mal escrito responde lo mismo")
    void notFound_shouldThrowSessionNotFound() {
        when(sessions.findByIdAndOwner(SESSION_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(new SessionCommand(SESSION_ID, OWNER)))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> service.report(new SessionCommand(null, OWNER)))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("Un reporte ya emitido se devuelve con las cinco preguntas y sin llamar al generador")
    void existing_shouldNotGenerate() {
        sessionIn(SessionStatus.FINISHED);
        when(reports.findBySession(SESSION_ID)).thenReturn(Optional.of(report));
        when(questions.findBySession(SESSION_ID)).thenReturn(List.of(question(2, QuestionType.TECNICA),
                question(1, QuestionType.APERTURA)));
        VoiceSample voice = new VoiceSample(56, 30473, 8623, 4);
        when(attempts.reportInputsOf(SESSION_ID)).thenReturn(List.of(
                new ReportQuestion(2, QuestionType.TECNICA, "pregunta 2", "respuesta", AnswerOrigin.AUDIO, voice)));

        ReportOutcome outcome = service.report(new SessionCommand(SESSION_ID, OWNER));

        verify(generator, never()).generate(any());
        ReportOutcome.Issued issued = (ReportOutcome.Issued) outcome;
        assertThat(issued.report()).isSameAs(report);
        assertThat(issued.tone()).isEqualTo(Tone.STRICT);
        assertThat(issued.interviewDate()).isEqualTo(Instant.parse("2026-11-17T14:58:40Z"));
        assertThat(issued.questions()).extracting(ReportOutcome.QuestionRow::number).containsExactly(1, 2);
        assertThat(issued.questions().get(0).answerText()).isNull();
        assertThat(issued.questions().get(0).origin()).isNull();
        assertThat(issued.questions().get(1).answerText()).isEqualTo("respuesta");
        assertThat(issued.questions().get(1).voice()).isEqualTo(voice);
    }

    @Test
    @DisplayName("Sin reporte, una sesión finalizada lo genera y lo devuelve")
    void missing_shouldGenerate() {
        InterviewSession session = sessionIn(SessionStatus.FINISHED);
        when(reports.findBySession(SESSION_ID)).thenReturn(Optional.empty());
        when(generator.generate(session)).thenReturn(Optional.of(report));
        when(questions.findBySession(SESSION_ID)).thenReturn(List.of());
        when(attempts.reportInputsOf(SESSION_ID)).thenReturn(List.of());

        ReportOutcome outcome = service.report(new SessionCommand(SESSION_ID, OWNER));

        assertThat(outcome).isInstanceOf(ReportOutcome.Issued.class);
        verify(generator).generate(session);
    }

    @Test
    @DisplayName("Si otra petición lo está generando devuelve Generating")
    void generating_shouldReturnGenerating() {
        InterviewSession session = sessionIn(SessionStatus.FINISHED);
        when(reports.findBySession(SESSION_ID)).thenReturn(Optional.empty());
        when(generator.generate(session)).thenReturn(Optional.empty());

        assertThat(service.report(new SessionCommand(SESSION_ID, OWNER))).isEqualTo(new ReportOutcome.Generating());

        verify(questions, never()).findBySession(any());
    }

    @Test
    @DisplayName("Si la base falla se propaga DatabaseUnavailableException")
    void databaseDown_shouldThrowDatabaseUnavailable() {
        sessionIn(SessionStatus.FINISHED);
        when(reports.findBySession(SESSION_ID)).thenThrow(new DatabaseUnavailableException(new RuntimeException()));

        assertThatThrownBy(() -> service.report(new SessionCommand(SESSION_ID, OWNER)))
                .isInstanceOf(DatabaseUnavailableException.class);
    }
}
