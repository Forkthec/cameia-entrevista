package tech.cameia.entrevista.domain.policy;

import tech.cameia.entrevista.domain.model.TranscriptionFailure;

/**
 * Regla de reintento de la transcripción de un audio (HU-5.13).
 *
 * <ul>
 *   <li>Como máximo {@value #MAX_ATTEMPTS} intentos por envío: el inicial y un reintento automático.</li>
 *   <li>Solo se reintenta un error transitorio (Voz no disponible o conexión rechazada).</li>
 *   <li>Si un intento agotó su tiempo de espera, no se reintenta: la persona ya esperó el máximo (CA-5.13.3).</li>
 *   <li>Cualquier otro fallo (audio rechazado, respuesta ilegible) no se reintenta: repetirlo daría lo mismo.</li>
 * </ul>
 */
public final class TranscriptionRetryPolicy {

    /** Intentos máximos por envío de audio. */
    public static final int MAX_ATTEMPTS = 2;

    /**
     * Decide si se hace otro intento después de uno fallido.
     *
     * @param failedAttempt número del intento que acaba de fallar, desde 1
     * @param failure causa del fallo
     * @return {@code true} si se debe intentar una vez más
     */
    public boolean shouldRetry(int failedAttempt, TranscriptionFailure failure) {
        return failure == TranscriptionFailure.TRANSIENT && failedAttempt < MAX_ATTEMPTS;
    }
}
