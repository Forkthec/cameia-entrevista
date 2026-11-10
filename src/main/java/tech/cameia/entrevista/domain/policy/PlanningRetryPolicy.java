package tech.cameia.entrevista.domain.policy;

/**
 * Regla de los reintentos manuales de la planificación: el intento inicial y, como máximo, dos reintentos que
 * pide la persona. No accede a la base ni al reloj.
 *
 * <p>El contador {@code retries} vale 0 en el primer pedido. Sube en 1 con cada pedido que llega después de un
 * intento fallido y antes de llamar a la IA. Si al llegar vale {@value #MAX_MANUAL_RETRIES} y hubo un intento
 * fallido, ya no quedan intentos y el pedido se rechaza sin llamar a la IA.</p>
 */
public final class PlanningRetryPolicy {

    /** Reintentos manuales permitidos además del intento inicial. */
    public static final int MAX_MANUAL_RETRIES = 2;

    /** Qué hacer con un pedido de inicio. */
    public enum Decision {
        /** Se puede llamar a la IA. */
        ALLOW,
        /** Los intentos se agotaron: se rechaza sin llamar a la IA. */
        EXHAUSTED
    }

    /**
     * Decide si el pedido puede llamar a la IA.
     *
     * @param retries reintentos gastados antes de este pedido, de 0 a {@value #MAX_MANUAL_RETRIES}
     * @param hadFailedAttempt {@code true} si la sesión ya tiene un intento de planificación fallido
     * @return {@code EXHAUSTED} solo si hubo un fallo y el contador ya está en el máximo
     */
    public Decision decide(int retries, boolean hadFailedAttempt) {
        return hadFailedAttempt && retries >= MAX_MANUAL_RETRIES ? Decision.EXHAUSTED : Decision.ALLOW;
    }

    /**
     * Calcula el contador que se guarda antes de llamar a la IA.
     *
     * @param retries reintentos gastados antes de este pedido
     * @param hadFailedAttempt {@code true} si la sesión ya tiene un intento fallido
     * @return {@code retries + 1} si este pedido es un reintento; si no, el mismo valor
     */
    public int retriesAfterRequest(int retries, boolean hadFailedAttempt) {
        return hadFailedAttempt ? Math.min(retries + 1, MAX_MANUAL_RETRIES) : retries;
    }

    /**
     * Indica si un fallo de este intento deja la sesión sin más intentos: es el tercero.
     *
     * @param retriesAfterRequest contador guardado para este pedido
     * @return {@code true} si el contador ya está en el máximo
     */
    public boolean isLastAttempt(int retriesAfterRequest) {
        return retriesAfterRequest >= MAX_MANUAL_RETRIES;
    }

    /**
     * Número de intento que se registra en la ejecución de la IA.
     *
     * @param retriesAfterRequest contador guardado para este pedido
     * @return el intento, de 1 a 3
     */
    public int attemptNumber(int retriesAfterRequest) {
        return retriesAfterRequest + 1;
    }
}
