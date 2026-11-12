package tech.cameia.entrevista.domain.model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;

/**
 * Turno de una sesión (una pregunta con su respuesta): lleva la máquina de estados del turno.
 *
 * <p>Una transición rechazada deja el turno igual que estaba.</p>
 */
public final class InterviewTurn {

    private final UUID id;
    private final UUID questionId;
    private final Instant createdAt;
    private final Long version;
    private TurnStatus status;
    private Instant updatedAt;

    private InterviewTurn(TurnSnapshot snapshot) {
        this.id = snapshot.id();
        this.questionId = snapshot.questionId();
        this.status = snapshot.status();
        this.createdAt = snapshot.createdAt();
        this.updatedAt = snapshot.updatedAt();
        this.version = snapshot.version();
    }

    /**
     * Abre el turno de una pregunta recién creada, en {@link TurnStatus#PENDING}.
     *
     * @param questionId pregunta a la que pertenece
     * @param clock reloj que da la fecha de creación
     * @return el turno nuevo, sin versión
     */
    public static InterviewTurn open(UUID questionId, Clock clock) {
        Instant now = clock.instant();
        return new InterviewTurn(new TurnSnapshot(UUID.randomUUID(), questionId, TurnStatus.PENDING, now, now, null));
    }

    /**
     * Reconstruye el turno desde la persistencia, sin validar transiciones.
     *
     * @param snapshot datos guardados
     * @return el turno con exactamente esos datos
     */
    public static InterviewTurn reconstitute(TurnSnapshot snapshot) {
        return new InterviewTurn(snapshot);
    }

    /**
     * Empieza a procesar la respuesta: {@code PENDING → PROCESSING}.
     *
     * @param clock reloj que da la fecha
     * @throws TurnTransitionNotAllowedException si el turno no está pendiente
     */
    public void startProcessing(Clock clock) {
        changeTo(TurnStatus.PROCESSING, clock);
    }

    /**
     * Termina el procesamiento: {@code PROCESSING → COMPLETED}.
     *
     * @param clock reloj que da la fecha
     * @throws TurnTransitionNotAllowedException si el turno no se está procesando
     */
    public void complete(Clock clock) {
        changeTo(TurnStatus.COMPLETED, clock);
    }

    /**
     * Marca el procesamiento como fallido para reintentarlo: {@code PROCESSING → RETRYING}.
     *
     * @param clock reloj que da la fecha
     * @throws TurnTransitionNotAllowedException si el turno no se está procesando
     */
    public void retry(Clock clock) {
        changeTo(TurnStatus.RETRYING, clock);
    }

    /**
     * Retoma el procesamiento tras un fallo: {@code RETRYING → PROCESSING}.
     *
     * @param clock reloj que da la fecha
     * @throws TurnTransitionNotAllowedException si el turno no está en reintento
     */
    public void resumeProcessing(Clock clock) {
        changeTo(TurnStatus.PROCESSING, clock);
    }

    /**
     * Libera el turno sin respuesta: {@code PROCESSING → PENDING} o {@code RETRYING → PENDING}. Se usa cuando la
     * transcripción del audio falló del todo (HU-5.13): la pregunta sigue pendiente para responderla otra vez.
     *
     * @param clock reloj que da la fecha
     * @throws TurnTransitionNotAllowedException si el turno no se está procesando ni reintentando
     */
    public void release(Clock clock) {
        changeTo(TurnStatus.PENDING, clock);
    }

    /**
     * Indica si el turno está ocupado procesando una respuesta y la reserva sigue vigente. Una reserva más vieja
     * que {@code maxAge} es de un proceso que murió sin liberarla.
     *
     * @param now instante actual
     * @param maxAge tiempo máximo que puede durar el procesamiento de una respuesta
     * @return {@code true} si está en {@code PROCESSING} o {@code RETRYING} desde hace menos de {@code maxAge}
     */
    public boolean isBusy(Instant now, Duration maxAge) {
        return (status == TurnStatus.PROCESSING || status == TurnStatus.RETRYING)
                && updatedAt.plus(maxAge).isAfter(now);
    }

    private void changeTo(TurnStatus target, Clock clock) {
        // Se valida antes de tocar ningún campo: una transición rechazada deja el turno igual.
        if (!status.canChangeTo(target)) {
            throw new TurnTransitionNotAllowedException();
        }
        status = target;
        updatedAt = clock.instant();
    }

    /** @return identificador del turno */
    public UUID id() {
        return id;
    }

    /** @return pregunta a la que pertenece */
    public UUID questionId() {
        return questionId;
    }

    /** @return estado actual */
    public TurnStatus status() {
        return status;
    }

    /** @return fecha de creación */
    public Instant createdAt() {
        return createdAt;
    }

    /** @return fecha de la última modificación */
    public Instant updatedAt() {
        return updatedAt;
    }

    /** @return versión de control de edición simultánea; {@code null} si el turno es nuevo */
    public Long version() {
        return version;
    }
}
