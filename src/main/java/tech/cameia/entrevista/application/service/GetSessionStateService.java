package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.AnsweredQuestion;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.domain.policy.EvaluationStaleness;
import tech.cameia.entrevista.domain.port.AiExecutionRepository;
import tech.cameia.entrevista.domain.port.AnswerAttemptRepository;
import tech.cameia.entrevista.domain.port.InterviewQuestionRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;

/**
 * Caso de uso: consultar el estado de una sesión propia. Es de solo lectura: no cambia el estado ni cuenta como
 * actividad.
 */
@Service
public class GetSessionStateService {

    private static final Logger log = LoggerFactory.getLogger(GetSessionStateService.class);

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewTurnRepository turns;
    private final AnswerAttemptRepository answers;
    private final AiExecutionRepository executions;
    private final EvaluationStaleness staleness;
    private final Clock clock;

    GetSessionStateService(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns, AnswerAttemptRepository answers, AiExecutionRepository executions,
            Clock clock, @Value("${cameia.llm.timeout}") Duration llmTimeout) {
        this.sessions = sessions;
        this.questions = questions;
        this.turns = turns;
        this.answers = answers;
        this.executions = executions;
        this.clock = clock;
        this.staleness = new EvaluationStaleness(llmTimeout);
    }

    /**
     * Devuelve el estado de la sesión.
     *
     * @param command sesión pedida y dueño
     * @return el estado de la sesión
     * @throws SessionNotFoundException si la sesión no existe, es de otra persona o el identificador no era un UUID
     */
    public SessionState state(SessionCommand command) {
        return stateOf(findOwned(sessions, command));
    }

    /**
     * Calcula el estado de una sesión ya cargada.
     *
     * @param session sesión de la que se informa
     * @return el estado: forma de respuesta, avance y, solo si está en curso, la pregunta pendiente
     */
    SessionState stateOf(InterviewSession session) {
        SessionStatus status = session.status();
        AnswerMode answerMode = session.configuration().answerMode();
        if (status == SessionStatus.CONFIGURED) {
            return new SessionState(session.id(), status, answerMode, 0, null);
        }
        List<AnsweredQuestion> history = answers.historyOf(session.id());
        if (status == SessionStatus.EVALUATING || status == SessionStatus.FINISHED) {
            return new SessionState(session.id(), status, answerMode, InterviewQuestion.TOTAL, null, history);
        }
        List<InterviewQuestion> planned = questions.findBySession(session.id());
        Optional<InterviewTurn> open = turns.findOpenBySession(session.id());
        Optional<InterviewQuestion> current = open.flatMap(turn -> planned.stream()
                .filter(question -> question.id().equals(turn.questionId())).findFirst());
        int number = current.map(InterviewQuestion::position).orElse(InterviewQuestion.TOTAL);
        if (status == SessionStatus.IN_PROGRESS && current.isPresent()) {
            InterviewTurn turn = open.get();
            return new SessionState(session.id(), status, answerMode, number, new SessionState.PendingQuestion(
                    turn.id(), number, current.get().originalText(), visibleStatus(turn), turn.manualRetries()),
                    history);
        }
        // Con las cinco respondidas no queda turno abierto: es el cierre normal de las respuestas, no un defecto.
        if (status == SessionStatus.IN_PROGRESS && turns.countCompleted(session.id()) < InterviewQuestion.TOTAL) {
            // Una sesión en curso con preguntas por responder siempre tiene su turno abierto: que falte es una
            // inconsistencia que se registra.
            log.warn("Sesión en curso sin turno abierto [sessionId={}]", session.id());
        }
        return new SessionState(session.id(), status, answerMode, number, null, history);
    }

    // Un turno procesando cuya evaluación murió se muestra en reintento, para que la persona pueda reintentar.
    private TurnStatus visibleStatus(InterviewTurn turn) {
        if (turn.status() == TurnStatus.PROCESSING
                && staleness.isStale(executions.findEvaluationInProgress(turn.id()), clock.instant())) {
            return TurnStatus.RETRYING;
        }
        return turn.status();
    }

    /**
     * Busca la sesión del dueño o lanza {@link SessionNotFoundException}: el mismo error para una sesión ajena,
     * inexistente o mal escrita, de modo que no se revela si existe.
     *
     * @param sessions repositorio de sesiones
     * @param command sesión pedida y dueño
     * @return la sesión propia
     */
    static InterviewSession findOwned(InterviewSessionRepository sessions, SessionCommand command) {
        if (command.sessionId() == null) {
            throw new SessionNotFoundException();
        }
        return sessions.findByIdAndOwner(command.sessionId(), command.owner())
                .orElseThrow(SessionNotFoundException::new);
    }
}
