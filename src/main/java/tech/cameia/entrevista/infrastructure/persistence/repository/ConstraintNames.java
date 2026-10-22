package tech.cameia.entrevista.infrastructure.persistence.repository;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Lee el nombre de la restricción que una excepción de base de datos violó, para que cada
 * adaptador la traduzca a su error de negocio.
 */
final class ConstraintNames {

    /** Máximo de causas que se recorren, para no girar en una cadena con ciclo. */
    private static final int MAX_CAUSES = 32;

    private ConstraintNames() {
    }

    /**
     * Indica si alguna causa del fallo es la violación de la restricción indicada.
     *
     * @param failure fallo capturado
     * @param constraint nombre de la restricción, sin distinguir mayúsculas
     * @return {@code true} si Hibernate informa esa restricción
     */
    static boolean violates(Throwable failure, String constraint) {
        for (var seen = 0; failure != null && seen < MAX_CAUSES; failure = failure.getCause(), seen++) {
            if (failure instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
