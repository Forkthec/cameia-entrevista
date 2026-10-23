package tech.cameia.entrevista.infrastructure.messaging.consumer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Copia de una excepción sin sus mensajes, para registrar un fallo sin datos personales.
 *
 * <p>El mensaje de una excepción puede traer lo que la persona escribió: PostgreSQL pone los valores
 * de las columnas en el de una restricción violada, y Hibernate el SQL con sus parámetros. La copia
 * conserva la clase original (como mensaje), la traza y la cadena de causas, que es lo que hace
 * falta para diagnosticar, y descarta los textos.</p>
 */
final class RedactedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private RedactedException(Throwable original, Throwable cause) {
        super(original.getClass().getName(), cause, false, true);
        setStackTrace(original.getStackTrace());
    }

    /**
     * @param original excepción tal como se lanzó
     * @return la copia sin mensajes, con la misma traza y la misma cadena de causas
     */
    static RedactedException of(Throwable original) {
        return copy(original, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static RedactedException copy(Throwable original, Set<Throwable> seen) {
        seen.add(original);
        Throwable cause = original.getCause();
        // Una cadena con ciclo se corta en la primera repetición.
        RedactedException redactedCause = cause == null || seen.contains(cause) ? null : copy(cause, seen);
        return new RedactedException(original, redactedCause);
    }
}
