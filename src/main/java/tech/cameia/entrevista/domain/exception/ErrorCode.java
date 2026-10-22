package tech.cameia.entrevista.domain.exception;

/**
 * Códigos de error estables que el servicio devuelve en el miembro {@code code} de cada
 * respuesta de error.
 *
 * <p>Un código nunca se renombra ni se reutiliza una vez publicado. La forma es
 * {@code <SUJETO>_<CAUSA>} con un vocabulario de causas cerrado; {@code INTERNAL_ERROR} es la
 * excepción, porque describe la operación entera y no una causa. Ningún fallo previsible debe
 * terminar en él: cada uno tiene su código propio.</p>
 */
public enum ErrorCode {

    /** Falló algo imprevisto que la persona no puede corregir. */
    INTERNAL_ERROR,

    /**
     * El modelo de lenguaje no respondió a tiempo, el proveedor falló o devolvió una respuesta
     * vacía o inutilizable; la persona puede reintentar.
     */
    LLM_UNAVAILABLE,

    /** El proveedor del modelo de lenguaje rechazó la llamada por cuota o límite de uso agotado. */
    LLM_LIMIT_REACHED
}
