package tech.cameia.entrevista.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import tech.cameia.entrevista.domain.exception.TurnNotRetryableException;
import tech.cameia.entrevista.domain.exception.TurnRetryLimitReachedException;
import tech.cameia.entrevista.domain.exception.TurnTransitionNotAllowedException;

/**
 * Turno de una sesión (una pregunta con su respuesta): lleva la máquina de estados del turno.
 *
 * <p>Una transición rechazada deja el turno igual que estaba.</p>
 */
public final class InterviewTurn {

    /** Veces que la persona puede usar «Reintentar» en un turno. */
    public static final int MAX_MANUAL_RETRIES = 4;

    private final UUID id;
    private final UUID questionId;
    private final Instant createdAt;
    private final Long version;
    private TurnStatus status;
    private int manualRetries;
    private Instant updatedAt;

    private InterviewTurn(TurnSnapshot snapshot) {
        this.id = snapshot.id();
        this.questionId = snapshot.questionId();
        this.status = snapshot.status();
        this.manualRetries = snapshot.manualRetries();
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
        return new InterviewTurn(new TurnSnapshot(UUID.randomUUID(), questionId, TurnStatus.PENDING, 0, now, now, null));
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
     * Empieza un uso de «Reintentar»: suma uno al contador y pasa a procesando.
     *
     * @param stale si el turno está procesando con una evaluación caída, se acepta como si estuviera en reintento
     * @param clock reloj que da la fecha
     * @throws TurnNotRetryableException si el turno no espera un reintento
     * @throws TurnRetryLimitReachedException si ya se usaron los cuatro reintentos
     */
    public void startManualRetry(boolean stale, Clock clock) {
        boolean waiting = status == TurnStatus.RETRYING || (status == TurnStatus.PROCESSING && stale);
        if (!waiting) {
            throw new TurnNotRetryableException();
        }
        if (manualRetries >= MAX_MANUAL_RETRIES) {
            throw new TurnRetryLimitReachedException();
        }
        manualRetries++;
        status = TurnStatus.PROCESSING;
        updatedAt = clock.instant();
    }

    /**
     * Devuelve la pregunta a pendiente cuando falla la transcripción de la respuesta por voz, antes de guardar
     * ninguna respuesta: {@code PROCESSING → PENDING}. La persona puede grabar de nuevo o responder por texto
     * (CA-5.13.5, CA-5.13.6).
     *
     * @param clock reloj que da la fecha
     * @throws TurnTransitionNotAllowedException si el turno no se está procesando
     */
    public void release(Clock clock) {
        changeTo(TurnStatus.PENDING, clock);
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

    /** @return veces que se usó «Reintentar» en este turno */
    public int manualRetries() {
        return manualRetries;
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
