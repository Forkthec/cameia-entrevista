package tech.cameia.entrevista.application.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.InterviewTurn;
import tech.cameia.entrevista.domain.model.SessionStatus;
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

    GetSessionStateService(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewTurnRepository turns) {
        this.sessions = sessions;
        this.questions = questions;
        this.turns = turns;
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
        if (status == SessionStatus.EVALUATING || status == SessionStatus.FINISHED) {
            return new SessionState(session.id(), status, answerMode, InterviewQuestion.TOTAL, null);
        }
        List<InterviewQuestion> planned = questions.findBySession(session.id());
        Optional<InterviewTurn> open = turns.findOpenBySession(session.id());
        Optional<InterviewQuestion> current = open.flatMap(turn -> planned.stream()
                .filter(question -> question.id().equals(turn.questionId())).findFirst());
        int number = current.map(InterviewQuestion::position).orElse(InterviewQuestion.TOTAL);
        if (status == SessionStatus.IN_PROGRESS && current.isPresent()) {
            return new SessionState(session.id(), status, answerMode, number, new SessionState.PendingQuestion(
                    open.get().id(), number, current.get().originalText()));
        }
        if (status == SessionStatus.IN_PROGRESS) {
            // Una sesión en curso siempre tiene su turno abierto: que falte es una inconsistencia que se registra.
            log.warn("Sesión en curso sin turno abierto [sessionId={}]", session.id());
        }
        return new SessionState(session.id(), status, answerMode, number, null);
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
