package tech.cameia.entrevista.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;

/**
 * Caso de uso: cancelar la preparación de una entrevista, es decir borrar una sesión propia que sigue configurada.
 * Sus ejecuciones de la IA y sus preguntas se eliminan en cascada en la base.
 */
@Service
public class DeleteSessionService {

    private static final Logger log = LoggerFactory.getLogger(DeleteSessionService.class);

    private final InterviewSessionRepository sessions;

    DeleteSessionService(InterviewSessionRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * Borra la sesión.
     *
     * @param command sesión pedida y dueño
     * @throws SessionNotFoundException si la sesión no existe, es de otra persona o el identificador no era un UUID
     * @throws SessionTransitionNotAllowedException si la sesión ya no está configurada, o dejó de estarlo mientras
     *         se borraba (por ejemplo, porque otra petición la inició)
     */
    public void delete(SessionCommand command) {
        InterviewSession session = GetSessionStateService.findOwned(sessions, command);
        session.requireDeletable();
        sessions.deleteConfigured(session.id(), command.owner());
        log.info("Sesión de entrevista cancelada [sessionId={}, firebaseUid={}]", session.id(),
                command.owner().value());
    }
}
