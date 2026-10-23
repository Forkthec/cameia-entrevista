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
    IDEMPOTENCY_KEY_ALREADY_EXISTS,

    /** El perfil no existe o es de otra persona; no se distingue para no revelar su existencia. */
    PROFILE_NOT_FOUND,

    /** La copia local del perfil todavía no está lista para crear la sesión. */
    PROFILE_SYNC_PENDING,

    /** La clave de la petición ya se usó con otra configuración. */
    IDEMPOTENCY_KEY_CONFLICT,

    /** Falta elegir el Perfil Profesional. */
    PROFILE_ID_REQUIRED,

    /** El identificador del Perfil Profesional no tiene formato de UUID. */
    PROFILE_ID_INVALID_FORMAT,

    /** Falta elegir el rol objetivo. */
    TARGET_ROLE_ID_REQUIRED,

    /** El identificador del rol objetivo no tiene formato de UUID. */
    TARGET_ROLE_ID_INVALID_FORMAT,

    /** El rol objetivo no está activo en el catálogo. */
    TARGET_ROLE_ID_INVALID_VALUE,

    /** La oferta laboral supera el largo permitido. */
    JOB_OFFER_TOO_LONG,

    /** Falta elegir la modalidad. */
    MODE_REQUIRED,

    /** La modalidad no es una de la lista. */
    MODE_INVALID_VALUE,

    /** La modalidad existe pero todavía no está disponible. */
    MODE_NOT_ALLOWED,

    /** Falta elegir el tono del entrevistador. */
    TONE_REQUIRED,

    /** El tono no es uno de la lista. */
    TONE_INVALID_VALUE,

    /** Falta elegir la forma de respuesta. */
    ANSWER_FORMAT_REQUIRED,

    /** La forma de respuesta no es una de la lista. */
    ANSWER_FORMAT_INVALID_VALUE,

    /** Falta elegir el idioma de la entrevista. */
    SESSION_LANGUAGE_REQUIRED,

    /** El idioma no es uno de la lista. */
    SESSION_LANGUAGE_INVALID_VALUE,

    /** El encabezado de la clave anti-duplicados no cumple el formato. */
    IDEMPOTENCY_KEY_INVALID_FORMAT
}
