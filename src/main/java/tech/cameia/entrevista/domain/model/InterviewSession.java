package tech.cameia.entrevista.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.SessionTransitionNotAllowedException;

/**
 * Sesión de entrevista: el agregado que lleva la máquina de estados de la sesión.
 *
 * <p>Toda transición se valida antes de tocar ningún campo, así que una transición rechazada deja
 * la sesión igual que estaba. El tiempo llega siempre por el reloj inyectado.</p>
 */
public final class InterviewSession {

    /** Largo máximo del texto de la oferta laboral. */
    public static final int MAX_JOB_OFFER_LENGTH = 3000;

    private final UUID id;
    private final FirebaseUid owner;
    private SessionConfiguration configuration;
    private int planningRetries;
    private final Instant createdAt;
    private final Long version;
    private SessionStatus status;
    private Instant updatedAt;
    private Instant lastActivityAt;
    private Instant finishedAt;

    private InterviewSession(SessionSnapshot snapshot) {
        this.id = snapshot.id();
        this.owner = snapshot.owner();
        this.configuration = snapshot.configuration();
        this.status = snapshot.status();
        this.planningRetries = snapshot.planningRetries();
        this.createdAt = snapshot.createdAt();
        this.updatedAt = snapshot.updatedAt();
        this.lastActivityAt = snapshot.lastActivityAt();
        this.finishedAt = snapshot.finishedAt();
        this.version = snapshot.version();
    }

    /**
     * Crea una sesión nueva en {@link SessionStatus#CONFIGURED}.
     *
     * @param id identificador de la sesión
     * @param owner dueño de la sesión
     * @param configuration configuración con la que se prepara
     * @param clock reloj que da la fecha de creación
     * @return la sesión nueva, sin versión
     */
    public static InterviewSession configure(UUID id, FirebaseUid owner, SessionConfiguration configuration,
            Clock clock) {
        Instant now = clock.instant();
        return new InterviewSession(new SessionSnapshot(id, owner, configuration, SessionStatus.CONFIGURED, 0,
                now, now, null, null, null));
    }

    /**
     * Reconstruye la sesión desde la persistencia, sin validar transiciones.
     *
     * @param snapshot datos guardados
     * @return la sesión con exactamente esos datos
     */
    public static InterviewSession reconstitute(SessionSnapshot snapshot) {
        return new InterviewSession(snapshot);
    }

    /**
     * Reemplaza la configuración de una sesión que todavía no empezó y reinicia los reintentos de planificación.
     * La fecha de creación no cambia.
     *
     * @param newConfiguration configuración nueva
     * @param clock reloj que da la fecha de modificación
     * @throws SessionTransitionNotAllowedException si la sesión no está configurada; no cambia nada
     */
    public void reconfigure(SessionConfiguration newConfiguration, Clock clock) {
        requireDeletable();
        configuration = newConfiguration;
        planningRetries = 0;
        updatedAt = clock.instant();
    }

    /**
     * Empieza la sesión: {@code CONFIGURED → IN_PROGRESS}, y marca la actividad.
     *
     * @param clock reloj que da la fecha
     * @throws SessionTransitionNotAllowedException si la sesión no está configurada
     */
    public void start(Clock clock) {
        changeTo(SessionStatus.IN_PROGRESS, clock);
        lastActivityAt = updatedAt;
    }

    /**
     * Abandona la sesión: {@code IN_PROGRESS → ABANDONED}, y registra la fecha de cierre.
     *
     * @param clock reloj que da la fecha
     * @throws SessionTransitionNotAllowedException si la sesión no está en curso
     */
    public void abandon(Clock clock) {
        changeTo(SessionStatus.ABANDONED, clock);
        finishedAt = updatedAt;
    }

    /**
     * Pasa a evaluar las respuestas: {@code IN_PROGRESS → EVALUATING}.
     *
     * @param clock reloj que da la fecha
     * @throws SessionTransitionNotAllowedException si la sesión no está en curso
     */
    public void startEvaluation(Clock clock) {
        changeTo(SessionStatus.EVALUATING, clock);
    }

    /**
     * Termina la sesión: {@code EVALUATING → FINISHED}, y registra la fecha de cierre.
     *
     * @param clock reloj que da la fecha
     * @throws SessionTransitionNotAllowedException si la sesión no se está evaluando
     */
    public void finish(Clock clock) {
        changeTo(SessionStatus.FINISHED, clock);
        finishedAt = updatedAt;
    }

    /**
     * Comprueba que la sesión se puede borrar: solo la que sigue configurada.
     *
     * @throws SessionTransitionNotAllowedException si la sesión ya empezó o terminó
     */
    public void requireDeletable() {
        if (status != SessionStatus.CONFIGURED) {
            throw new SessionTransitionNotAllowedException();
        }
    }

    private void changeTo(SessionStatus target, Clock clock) {
        // Se valida antes de tocar ningún campo: una transición rechazada deja el agregado igual.
        if (!status.canChangeTo(target)) {
            throw new SessionTransitionNotAllowedException();
        }
        status = target;
        updatedAt = clock.instant();
    }

    /** @return identificador de la sesión */
    public UUID id() {
        return id;
    }

    /** @return dueño de la sesión */
    public FirebaseUid owner() {
        return owner;
    }

    /** @return configuración con la que se preparó */
    public SessionConfiguration configuration() {
        return configuration;
    }

    /** @return estado actual */
    public SessionStatus status() {
        return status;
    }

    /** @return reintentos de planificación gastados */
    public int planningRetries() {
        return planningRetries;
    }

    /** @return fecha de creación */
    public Instant createdAt() {
        return createdAt;
    }

    /** @return fecha de la última modificación */
    public Instant updatedAt() {
        return updatedAt;
    }

    /** @return fecha de la última actividad, o {@code null} si no empezó */
    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    /** @return fecha de cierre, o {@code null} si no terminó */
    public Instant finishedAt() {
        return finishedAt;
    }

    /** @return versión de control de edición simultánea; {@code null} si la sesión es nueva */
    public Long version() {
        return version;
    }
}
