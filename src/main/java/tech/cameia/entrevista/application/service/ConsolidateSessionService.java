package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.SessionCommand;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.SessionConflictException;
import tech.cameia.entrevista.domain.exception.SessionConsolidationFailedException;
import tech.cameia.entrevista.domain.exception.SessionNotFoundException;
import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.policy.ConsolidationRetryPolicy;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.InterviewTurnRepository;
import tech.cameia.entrevista.domain.port.TurnFeedbackRepository;
import tech.cameia.entrevista.domain.port.Waiter;

/**
 * Cierra una sesión que se está evaluando: comprueba que sus cinco turnos están completos y con retroalimentación,
 * registra la fecha de fin y la pasa a finalizada. Cada intento es una transacción corta; la espera entre intentos
 * ocurre sin conexión tomada. No llama a la IA ni cambia la última actividad.
 *
 * <p>Lo usan la evaluación de la quinta respuesta (cierre automático) y el endpoint de consolidación (reintento de la
 * persona). Una consolidación que pierde por versión frente a otra relee la sesión ya finalizada y termina sin
 * cambios.</p>
 */
@Service
public class ConsolidateSessionService {

    private static final Logger log = LoggerFactory.getLogger(ConsolidateSessionService.class);

    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final TurnFeedbackRepository feedbacks;
    private final GetSessionStateService stateService;
    private final Waiter waiter;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final ConsolidationRetryPolicy policy = new ConsolidationRetryPolicy();

    @SuppressWarnings("java:S107")
    ConsolidateSessionService(InterviewSessionRepository sessions, InterviewTurnRepository turns,
            TurnFeedbackRepository feedbacks, GetSessionStateService stateService, Waiter waiter,
            TransactionTemplate transaction, Clock clock) {
        this.sessions = sessions;
        this.turns = turns;
        this.feedbacks = feedbacks;
        this.stateService = stateService;
        this.waiter = waiter;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * Atiende el pedido de la persona: devuelve el estado de su sesión ya finalizada, consolidándola si hace falta.
     *
     * @param command sesión y dueño
     * @return el estado de la sesión finalizada
     * @throws SessionNotFoundException si la sesión no existe, es ajena o el identificador está mal escrito
     * @throws SessionTransitionNotAllowedException si la sesión no está evaluándose ni finalizada
     * @throws SessionConsolidationFailedException si el cierre falla; la sesión sigue en evaluación
     */
    public SessionState consolidateOwned(SessionCommand command) {
        InterviewSession session = GetSessionStateService.findOwned(sessions, command);
        if (session.status() != SessionStatus.FINISHED) {
            if (session.status() != SessionStatus.EVALUATING) {
                throw new SessionTransitionNotAllowedException();
            }
            consolidate(session.id(), session.owner());
        }
        return stateService.state(command);
    }

    /**
     * Ejecuta un ciclo de consolidación de una sesión que se está evaluando, con hasta tres intentos.
     *
     * @param sessionId sesión que se cierra
     * @param owner dueño de la sesión
     * @throws SessionConsolidationFailedException si los tres intentos fallan o faltan respuestas o retroalimentación
     * @throws SessionTransitionNotAllowedException si la sesión no se está evaluando
     */
    public void consolidate(UUID sessionId, FirebaseUid owner) {
        for (int attempt = 1; attempt <= ConsolidationRetryPolicy.MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                // Sin transacción ni conexión tomada mientras se espera.
                waiter.pause(policy.waitBefore(attempt));
            }
            try {
                transaction.executeWithoutResult(status -> finishOnce(sessionId, owner));
                return;
            } catch (DataAccessException | TransactionException | DatabaseUnavailableException
                    | SessionConflictException transientFailure) {
                log.warn("Intento de consolidación fallido [sessionId={}, intento={}, causa={}]", sessionId, attempt,
                        transientFailure.getClass().getSimpleName());
            }
        }
        log.error("Consolidación fallida [code={}, sessionId={}, causa={}]",
                ErrorCode.SESSION_CONSOLIDATION_FAILED, sessionId, "INTENTOS_AGOTADOS");
        throw new SessionConsolidationFailedException();
    }

    // Un intento: relee la sesión, comprueba los cinco turnos y las cinco retroalimentaciones y la finaliza.
    private void finishOnce(UUID sessionId, FirebaseUid owner) {
        InterviewSession session = sessions.findByIdAndOwner(sessionId, owner)
                .orElseThrow(SessionNotFoundException::new);
        if (session.status() == SessionStatus.FINISHED) {
            return;
        }
        if (session.status() != SessionStatus.EVALUATING) {
            throw new SessionTransitionNotAllowedException();
        }
        int completed = turns.countCompleted(sessionId);
        int withFeedback = feedbacks.countBySession(sessionId);
        if (completed != InterviewQuestion.TOTAL || withFeedback != InterviewQuestion.TOTAL) {
            // Un dato roto no se arregla reintentando: se informa y la sesión sigue en evaluación.
            log.error("Consolidación con datos incompletos [code={}, sessionId={}, turnosCompletos={},"
                    + " retroalimentaciones={}]", ErrorCode.SESSION_CONSOLIDATION_FAILED, sessionId, completed,
                    withFeedback);
            throw new SessionConsolidationFailedException();
        }
        session.finish(clock);
        sessions.save(session);
    }
}
