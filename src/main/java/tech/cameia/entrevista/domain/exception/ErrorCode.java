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
    LLM_LIMIT_REACHED,

    /** Uno o más campos del cuerpo no cumplen sus reglas. */
    VALIDATION_FAILED,

    /** El cuerpo no se puede leer como el JSON esperado. */
    REQUEST_BODY_INVALID_FORMAT,

    /** Falta un encabezado o parámetro obligatorio, o un valor no tiene el tipo esperado. */
    REQUEST_INVALID_VALUE,

    /** Falta la identidad del Gateway o no es válida. */
    IDENTITY_REQUIRED,

    /** La ruta pedida no existe. */
    ROUTE_NOT_FOUND,

    /** La ruta no admite el método HTTP usado. */
    METHOD_NOT_ALLOWED,

    /** El tipo de contenido enviado no se admite. */
    MEDIA_TYPE_NOT_ALLOWED,

    /** El tipo de respuesta pedido no se puede atender. */
    MEDIA_TYPE_NOT_ACCEPTABLE,

    /** El cuerpo {@code multipart/form-data} no se puede leer (por ejemplo, sin su límite). */
    REQUEST_MULTIPART_INVALID_FORMAT,

    /** La petición no se pudo interpretar, por ejemplo por una ruta con codificación inválida. */
    REQUEST_PATH_INVALID_FORMAT,

    /** Los datos enviados superan el tamaño que la petición admite (más de 10 MB en un multipart). */
    REQUEST_PAYLOAD_TOO_LARGE,

    /** La base de datos no respondió; la persona puede reintentar. */
    DATABASE_UNAVAILABLE,

    /** La sesión no admite la transición pedida desde su estado. */
    SESSION_TRANSITION_NOT_ALLOWED,

    /** El turno no admite la transición pedida desde su estado. */
    TURN_TRANSITION_NOT_ALLOWED,

    /** Otra escritura cambió la sesión antes que esta. */
    SESSION_CONFLICT,

    /** La persona ya tiene otra sesión configurada o en curso. */
    SESSION_ALREADY_EXISTS,

    /** La clave de la petición ya se registró para esa persona y operación. */
    IDEMPOTENCY_KEY_ALREADY_EXISTS
}
