package tech.cameia.entrevista.application.service;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.SessionAbandonedException;
import tech.cameia.entrevista.domain.exception.SessionNotFinishedException;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.ReportQuestion;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.EvaluationReportRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;

/**
 * Caso de uso: consultar el Reporte de Evaluación de una sesión propia. Si la entrevista terminó y el reporte aún no
 * existe, lo genera en esta misma petición; si otra petición lo está generando, responde que se consulte de nuevo.
 *
 * <p>No cambia el estado de la sesión ni cuenta como actividad. Solo las sesiones finalizadas tienen reporte.</p>
 */
@Service
public class GetEvaluationReportService {

    private final InterviewSessionRepository sessions;

    private final InterviewQuestionRepository questions;

    private final AnswerAttemptRepository attempts;

    private final EvaluationReportRepository reports;

    private final ReportGenerator generator;

    GetEvaluationReportService(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            AnswerAttemptRepository attempts, EvaluationReportRepository reports, ReportGenerator generator) {
        this.sessions = sessions;
        this.questions = questions;
        this.attempts = attempts;
        this.reports = reports;
        this.generator = generator;
    }

    /**
     * Devuelve el reporte de la sesión, generándolo si hace falta.
     *
     * @param command sesión pedida y dueño
     * @return el reporte con lo que se muestra de la sesión, o {@link ReportOutcome.Generating}
     * @throws tech.cameia.entrevista.domain.exception.SessionNotFoundException si la sesión no existe, es de otra
     *         persona o el identificador no era un UUID
     * @throws SessionAbandonedException si la sesión está abandonada
     * @throws SessionNotFinishedException si la sesión está configurada, en curso o evaluándose
     * @throws tech.cameia.entrevista.domain.exception.DatabaseUnavailableException si la base falla
     */
    public ReportOutcome report(SessionCommand command) {
        InterviewSession session = GetSessionStateService.findOwned(sessions, command);
        requireFinished(session);
        Optional<EvaluationReport> existing = reports.findBySession(session.id());
        Optional<EvaluationReport> report = existing.isPresent() ? existing : generator.generate(session);
        return report.<ReportOutcome>map(issued -> issued(session, issued)).orElseGet(ReportOutcome.Generating::new);
    }

    private static void requireFinished(InterviewSession session) {
        switch (session.status()) {
            case ABANDONED -> throw new SessionAbandonedException();
            case CONFIGURED, IN_PROGRESS, EVALUATING -> throw new SessionNotFinishedException();
            default -> {
                // FINALIZADA: tiene reporte o se genera.
            }
        }
    }

    // Las preguntas y respuestas definitivas no cambian tras cerrar la sesión: el cuerpo es el mismo en cada consulta.
    private ReportOutcome issued(InterviewSession session, EvaluationReport report) {
        Map<Integer, ReportQuestion> answered = attempts.reportInputsOf(session.id()).stream()
                .collect(Collectors.toMap(ReportQuestion::number, Function.identity()));
        List<ReportOutcome.QuestionRow> rows = questions.findBySession(session.id()).stream()
                .sorted(Comparator.comparingInt(InterviewQuestion::position))
                .map(question -> row(question, answered.get(question.position()))).toList();
        return new ReportOutcome.Issued(report, session.finishedAt().truncatedTo(ChronoUnit.SECONDS),
                session.configuration().tone(), rows);
    }

    private static ReportOutcome.QuestionRow row(InterviewQuestion question, ReportQuestion answer) {
        return answer == null
                ? new ReportOutcome.QuestionRow(question.position(), question.type(), question.originalText(), null,
                        null, null)
                : new ReportOutcome.QuestionRow(question.position(), question.type(), question.originalText(),
                        answer.answerText(), answer.origin(), answer.voice());
    }
}
