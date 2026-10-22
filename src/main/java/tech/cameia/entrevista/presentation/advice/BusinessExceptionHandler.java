package tech.cameia.entrevista.presentation.advice;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.IdentityRequiredException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.exception.RequestPayloadTooLargeException;
import tech.cameia.entrevista.domain.model.FirebaseUid;

/**
 * Traduce los fallos a respuestas {@code application/problem+json} (RFC 9457).
 *
 * <p>Toda respuesta de error lleva, además de {@code type}, {@code title}, {@code status},
 * {@code detail} e {@code instance}, dos miembros propios: {@code code}, un código estable
 * del catálogo {@link ErrorCode} con el que el cliente decide qué mostrar, y
 * {@code requestId}, el identificador con el que esa respuesta se encuentra en el log.</p>
 *
 * <p>Los fallos técnicos se registran completos en el log y al cliente solo le llega un texto
 * genérico: el detalle de una excepción interna describe la infraestructura y no ayuda a la
 * persona. Cada error se registra aquí y una sola vez: los fallos del servicio (5xx) en
 * {@code ERROR} con traza, y los rechazos de la petición (4xx) en {@code WARN} con el estado, el
 * código, el {@code requestId} y la identidad de quien llama, sin trazas. Ninguna línea del log ni
 * ninguna respuesta repite el texto enviado al modelo, el recibido ni los valores del cuerpo.</p>
 *
 * <p>Cubre los fallos de negocio (tabla {@link #DEFINITIONS}), la validación de la petición, los
 * rechazos de protocolo de Spring MVC, la base de datos no disponible, el cliente del modelo y el
 * fallo imprevisto.</p>
 */
@RestControllerAdvice
class BusinessExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BusinessExceptionHandler.class);

    /** Encabezado con el identificador de la petición, que pone el Gateway. */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Encabezado con la identidad de la persona, que pone el Gateway. */
    private static final String IDENTITY_HEADER = "X-User-Id";

    /** Miembro de la respuesta con los campos rechazados. */
    private static final String ERRORS = "errors";

    /** Valor del registro cuando la petición no trae una identidad válida. */
    private static final String NO_IDENTITY = "-";

    /** Tope de causas que se recorren al buscar la restricción o el SQLState de un fallo de la base. */
    private static final int MAX_CAUSES = 32;

    /** Título de los rechazos por datos que la persona debe corregir. */
    private static final String TITLE_VALIDATION = "Datos no válidos";

    /** {@code detail} de la validación de campos. */
    private static final String DETAIL_VALIDATION = "Revisa los campos marcados.";

    /** {@code detail} de una entrada con el valor o el tipo equivocado fuera del cuerpo. */
    private static final String DETAIL_INVALID_DATA = "Revisa los datos enviados.";

    /** Cómo responde el servicio a cada fallo de negocio: estado y título de la respuesta. */
    record Definition(HttpStatus status, String title) {
    }

    /**
     * Fallos de negocio de la entrevista. Su {@code detail} es el mensaje de la excepción, escrito
     * para la persona. Un código que no esté aquí responde {@code 500} y se registra como defecto.
     */
    static final Map<ErrorCode, Definition> DEFINITIONS = Map.of(
            ErrorCode.SESSION_TRANSITION_NOT_ALLOWED, new Definition(HttpStatus.CONFLICT, "Acción no permitida"),
            ErrorCode.TURN_TRANSITION_NOT_ALLOWED, new Definition(HttpStatus.CONFLICT, "Acción no permitida"),
            ErrorCode.SESSION_CONFLICT, new Definition(HttpStatus.CONFLICT, "Cambio simultáneo"),
            ErrorCode.SESSION_ALREADY_EXISTS, new Definition(HttpStatus.CONFLICT, "Entrevista existente"),
            ErrorCode.IDEMPOTENCY_KEY_ALREADY_EXISTS, new Definition(HttpStatus.CONFLICT, "Solicitud repetida"),
            ErrorCode.IDENTITY_REQUIRED, new Definition(HttpStatus.UNAUTHORIZED, "Identidad requerida"),
            ErrorCode.REQUEST_PATH_INVALID_FORMAT, new Definition(HttpStatus.BAD_REQUEST, TITLE_VALIDATION),
            ErrorCode.REQUEST_PAYLOAD_TOO_LARGE, new Definition(HttpStatus.PAYLOAD_TOO_LARGE, "Datos demasiado grandes"));

    /** Un campo rechazado: su nombre, el código estable y el mensaje para la persona. */
    record RejectedField(String field, String code, String message) {
    }

    /**
     * Atributo de la petición con el identificador ya resuelto. El despacho de error del contenedor
     * ({@code /error}) reutiliza la misma petición: así la respuesta lleva el identificador que ya
     * quedó en el registro.
     */
    private static final String REQUEST_ID_ATTRIBUTE = BusinessExceptionHandler.class.getName() + ".requestId";

    /** Atributo de la petición que marca que su error ya se registró. */
    private static final String LOGGED_ATTRIBUTE = BusinessExceptionHandler.class.getName() + ".logged";

    /** Tipo de contenido de todo error del servicio, con la codificación explícita (ASVS 4.1.1). */
    private static final String PROBLEM_JSON_UTF8 = "application/problem+json;charset=UTF-8";

    /**
     * Forma admitida para un identificador recibido. Lo que no la cumple se reemplaza por
     * uno nuevo: así un valor con saltos de línea o de tamaño arbitrario nunca llega al log
     * ni a la respuesta.
     */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /** Título de los fallos de una dependencia que la persona puede reintentar. */
    private static final String TITLE_UNAVAILABLE = "Servicio no disponible";

    /** {@code detail} de todo fallo que la persona no puede corregir. */
    private static final String DETAIL_INTERNAL = "Ocurrió un error. Inténtalo de nuevo.";

    /**
     * El modelo de lenguaje no respondió a tiempo, el proveedor falló o la respuesta no se puede
     * usar.
     *
     * <p>Es un fallo del servicio y no de la persona, así que se registra en {@code ERROR}
     * con la causa técnica; la respuesta solo dice que puede reintentar.</p>
     *
     * @param error excepción con la causa técnica
     * @return {@code 503 Service Unavailable} con el código {@code LLM_UNAVAILABLE}
     */
    @ExceptionHandler(LlmUnavailableException.class)
    ProblemDetail modelUnavailable(LlmUnavailableException error) {
        return serviceFailure(HttpStatus.SERVICE_UNAVAILABLE, TITLE_UNAVAILABLE, error.getErrorCode(),
                "Modelo de lenguaje no disponible", error);
    }

    /**
     * El proveedor del modelo de lenguaje rechazó la llamada por cuota agotada.
     *
     * <p>Responde {@code 503} y no {@code 429}: el límite agotado es el de la cuenta del servicio
     * con el proveedor, no uno de la persona.</p>
     *
     * @param error excepción con la respuesta del proveedor como causa
     * @return {@code 503 Service Unavailable} con el código {@code LLM_LIMIT_REACHED}
     */
    @ExceptionHandler(LlmLimitReachedException.class)
    ProblemDetail modelLimitReached(LlmLimitReachedException error) {
        return serviceFailure(HttpStatus.SERVICE_UNAVAILABLE, TITLE_UNAVAILABLE, error.getErrorCode(),
                "Límite de uso del modelo de lenguaje agotado", error);
    }

    /**
     * Fallo de negocio previsto: el estado, el título y el código salen de {@link #DEFINITIONS} y
     * el {@code detail} es el mensaje de la excepción.
     *
     * <p>Un código sin definición es un defecto del servicio, no de la persona: se registra en
     * {@code ERROR} y responde como un fallo imprevisto. Una prueba impide que ocurra.</p>
     *
     * @param error excepción de negocio
     * @return la respuesta de su definición, o {@code 500 INTERNAL_ERROR} si no la tiene
     */
    @ExceptionHandler(BusinessException.class)
    ProblemDetail businessFailure(BusinessException error) {
        Definition definition = DEFINITIONS.get(error.getErrorCode());
        if (definition == null) {
            logger.error("Código sin definición: {}", error.getErrorCode());
            return serviceFailure(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ErrorCode.INTERNAL_ERROR,
                    "Fallo no controlado", error);
        }
        return rejection(definition.status(), definition.title(), error.getMessage(), error.getErrorCode(), "");
    }

    /**
     * Campos que incumplen las validaciones del contrato.
     *
     * <p>Se devuelve un solo elemento por campo, el de la primera restricción que Spring informa
     * para él: dos mensajes para el mismo campo confunden a la persona y el formulario solo puede
     * mostrar uno.</p>
     *
     * @param error excepción con la lista de campos rechazados
     * @return {@code 422 Unprocessable Entity} con un elemento por campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidFields(MethodArgumentNotValidException error) {
        Map<String, RejectedField> byField = new LinkedHashMap<>();
        for (FieldError failure : error.getBindingResult().getFieldErrors()) {
            byField.putIfAbsent(failure.getField(), new RejectedField(failure.getField(), codeOf(failure).name(),
                    failure.getDefaultMessage() == null ? "Valor no válido" : failure.getDefaultMessage()));
        }
        List<RejectedField> fields = new ArrayList<>(byField.values());
        // Solo nombres de campo y códigos, que salen del contrato: nunca el valor ni el mensaje.
        String summary = fields.stream()
                .map(field -> field.field() + "=" + field.code())
                .collect(Collectors.joining(",", "campos=[", "]"));
        ProblemDetail problem = rejection(HttpStatus.UNPROCESSABLE_ENTITY, TITLE_VALIDATION, DETAIL_VALIDATION,
                ErrorCode.VALIDATION_FAILED, summary);
        problem.setProperty(ERRORS, fields);
        return problem;
    }

    /**
     * Cuerpo que no se puede interpretar: JSON mal formado o un valor de otro tipo.
     *
     * <p>El detalle de la excepción no se devuelve ni se registra: describe la estructura interna
     * del modelo y, en un cuerpo, puede incluir el valor recibido.</p>
     *
     * @param error excepción de deserialización
     * @return {@code 422 Unprocessable Entity} con el código {@code REQUEST_BODY_INVALID_FORMAT}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(HttpMessageNotReadableException error) {
        return rejection(HttpStatus.UNPROCESSABLE_ENTITY, TITLE_VALIDATION, "Revisa el formato de los datos enviados.",
                ErrorCode.REQUEST_BODY_INVALID_FORMAT, "causa=" + mostSpecificClass(error));
    }

    /**
     * Petición a la que le falta un encabezado o un parámetro que exige la ruta.
     *
     * <p>Si el encabezado que falta es la identidad del Gateway, es la misma falta que una
     * identidad inválida: {@code 401 IDENTITY_REQUIRED}. Cualquier otro enlace faltante es un dato
     * que la persona debe corregir. Se registra el nombre del encabezado, nunca su valor.</p>
     *
     * @param error excepción de enlace de la petición
     * @return {@code 401} si falta la identidad; {@code 422 REQUEST_INVALID_VALUE} en los demás casos
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    ProblemDetail incompleteRequest(ServletRequestBindingException error) {
        if (error instanceof MissingRequestHeaderException header
                && IDENTITY_HEADER.equalsIgnoreCase(header.getHeaderName())) {
            Definition definition = DEFINITIONS.get(ErrorCode.IDENTITY_REQUIRED);
            return rejection(definition.status(), definition.title(), IdentityRequiredException.MESSAGE,
                    ErrorCode.IDENTITY_REQUIRED, "encabezado=" + header.getHeaderName());
        }
        String missing = error instanceof MissingRequestHeaderException header
                ? "encabezado=" + header.getHeaderName()
                : "causa=" + error.getClass().getSimpleName();
        return rejection(HttpStatus.UNPROCESSABLE_ENTITY, TITLE_VALIDATION, DETAIL_INVALID_DATA,
                ErrorCode.REQUEST_INVALID_VALUE, missing);
    }

    /**
     * Parámetro de ruta o de consulta con un valor de otro tipo (por ejemplo, un identificador
     * que no es un UUID).
     *
     * <p>Solo se registra el nombre del parámetro; el valor recibido nunca.</p>
     *
     * @param error excepción de conversión de tipo
     * @return {@code 422 Unprocessable Entity} con el código {@code REQUEST_INVALID_VALUE}
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parameterTypeMismatch(MethodArgumentTypeMismatchException error) {
        return rejection(HttpStatus.UNPROCESSABLE_ENTITY, TITLE_VALIDATION, DETAIL_INVALID_DATA,
                ErrorCode.REQUEST_INVALID_VALUE, "parametro=" + error.getName());
    }

    /**
     * Ruta que no existe.
     *
     * @param error excepción de Spring cuando ningún controlador ni recurso atiende la ruta
     * @return {@code 404 Not Found} con el código {@code ROUTE_NOT_FOUND}
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail routeNotFound(NoResourceFoundException error) {
        return rejection(HttpStatus.NOT_FOUND, "Ruta no encontrada", "No existe la ruta solicitada.",
                ErrorCode.ROUTE_NOT_FOUND, "metodo=" + error.getHttpMethod());
    }

    /**
     * Método HTTP que la ruta no admite.
     *
     * <p>El encabezado {@code Allow} con los métodos admitidos se agrega a la respuesta, como pide
     * HTTP para un 405.</p>
     *
     * @param error excepción con los métodos que sí admite la ruta
     * @return {@code 405 Method Not Allowed} con el código {@code METHOD_NOT_ALLOWED}
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException error) {
        ProblemDetail problem = rejection(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "Método no permitido.", ErrorCode.METHOD_NOT_ALLOWED, "metodo=" + error.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(error.getHeaders()).body(problem);
    }

    /**
     * Tipo de contenido que la ruta no admite.
     *
     * @param error excepción con el tipo recibido y los admitidos
     * @return {@code 415 Unsupported Media Type} con el código {@code MEDIA_TYPE_NOT_ALLOWED}
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedContentType(HttpMediaTypeNotSupportedException error) {
        ProblemDetail problem = rejection(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido",
                "Tipo de contenido no admitido.", ErrorCode.MEDIA_TYPE_NOT_ALLOWED, "");
        // Accept con los tipos admitidos, como sugiere HTTP para un 415.
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).headers(error.getHeaders()).body(problem);
    }

    /**
     * Tipo de respuesta que quien llama no acepta.
     *
     * <p>El cuerpo sale en {@code application/problem+json} como todo error del servicio; el tipo
     * se fija de forma explícita porque, con un {@code Accept} ilegible, Spring no puede negociar
     * uno y respondería sin cuerpo.</p>
     *
     * @param error excepción con los tipos que la ruta sí produce
     * @return {@code 406 Not Acceptable} con el código {@code MEDIA_TYPE_NOT_ACCEPTABLE}
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> notAcceptableType(HttpMediaTypeNotAcceptableException error) {
        ProblemDetail problem = rejection(HttpStatus.NOT_ACCEPTABLE, "Tipo de respuesta no admitido",
                "Tipo de respuesta no admitido.", ErrorCode.MEDIA_TYPE_NOT_ACCEPTABLE, "");
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(new MediaType("application", "problem+json", StandardCharsets.UTF_8))
                .body(problem);
    }

    /**
     * Cuerpo {@code multipart/form-data} que no se puede leer, por ejemplo sin el límite
     * ({@code boundary}) que separa sus partes.
     *
     * <p>Es un error de quien llama. El cuerpo demasiado grande tiene su propio manejador
     * ({@link #payloadTooLarge}).</p>
     *
     * @param error excepción del procesamiento multipart
     * @return {@code 415 Unsupported Media Type} con el código {@code REQUEST_MULTIPART_INVALID_FORMAT}
     */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail multipartUnreadable(MultipartException error) {
        return rejection(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido",
                "Revisa el formato de los datos enviados.", ErrorCode.REQUEST_MULTIPART_INVALID_FORMAT,
                "causa=" + mostSpecificClass(error));
    }

    /**
     * Cuerpo {@code multipart/form-data} que supera el tamaño máximo de la petición o de un archivo.
     *
     * <p>Es un error de quien llama y se puede prever: sin este manejador Spring lo responde con
     * {@code sendError(413)} y la respuesta sale sin cuerpo.</p>
     *
     * @param error excepción del procesamiento multipart
     * @return {@code 413 Content Too Large} con el código {@code REQUEST_PAYLOAD_TOO_LARGE}
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail payloadTooLarge(MaxUploadSizeExceededException error) {
        return businessFailure(new RequestPayloadTooLargeException());
    }

    /**
     * La base de datos no está disponible (caída, conexión cortada o pool agotado).
     *
     * <p>Es un fallo del servicio y pasajero: responde {@code 503} y se registra en {@code ERROR}
     * con la traza, el SQLState y la clase más específica. El mensaje de la excepción (puede citar
     * el servidor o el usuario de la base) no llega al cliente.</p>
     *
     * @param error excepción de acceso a datos
     * @return {@code 503 Service Unavailable} con el código {@code DATABASE_UNAVAILABLE}
     */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
        CannotGetJdbcConnectionException.class})
    ProblemDetail databaseUnavailable(Exception error) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, TITLE_UNAVAILABLE, DETAIL_INTERNAL,
                ErrorCode.DATABASE_UNAVAILABLE);
        if (shouldLog()) {
            logger.error("Base de datos no disponible [code={}, requestId={}, sqlState={}, causa={}]",
                    ErrorCode.DATABASE_UNAVAILABLE, problem.getProperties().get("requestId"), sqlStateOf(error),
                    mostSpecificClass(error), error);
        }
        return problem;
    }

    /**
     * Violación de una restricción de la base de datos que ninguna regla tradujo.
     *
     * <p>Las restricciones alcanzables por una entrada de la persona se traducen a su error de
     * negocio en el adaptador de persistencia; una que llega aquí es un defecto y responde como
     * cualquier fallo imprevisto. El mensaje de la base incluye el valor de la columna (un dato
     * personal), así que ni se devuelve ni se registra: el log lleva solo el nombre de la
     * restricción y el SQLState.</p>
     *
     * @param error excepción traducida por Spring
     * @return {@code 500 Internal Server Error} con un mensaje genérico
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrityViolation(DataIntegrityViolationException error) {
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", DETAIL_INTERNAL,
                ErrorCode.INTERNAL_ERROR);
        if (shouldLog()) {
            logger.error("Violación de restricción de la base [constraint={}, sqlState={}, code={}, requestId={}, causa={}]",
                    constraintNameOf(error), sqlStateOf(error), ErrorCode.INTERNAL_ERROR,
                    problem.getProperties().get("requestId"), mostSpecificClass(error));
        }
        return problem;
    }

    /**
     * Cualquier fallo que ningún otro manejador cubre.
     *
     * <p>Con la traza: es la única forma de diagnosticar un fallo que nadie previó. El
     * {@code requestId} de la respuesta lleva a esa línea del log.</p>
     *
     * <p>Los rechazos de protocolo de Spring MVC que ningún manejador de arriba cubre (por ejemplo,
     * una parte de formulario multipart ausente) no son fallos del servicio: se relanzan sin
     * registrar, y Spring los responde con su estado 4xx.</p>
     *
     * @param error excepción no prevista
     * @return {@code 500 Internal Server Error} con un mensaje genérico
     * @throws Exception el mismo rechazo de protocolo, para que lo resuelva Spring
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedFailure(Exception error) throws Exception {
        if (isProtocolRejection(error)) {
            // Relanzar la misma excepción hace que Spring pase al siguiente resolvedor sin registrar nada.
            throw error;
        }
        return serviceFailure(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ErrorCode.INTERNAL_ERROR,
                "Fallo no controlado", error);
    }

    /**
     * Indica si la excepción es un rechazo de la petición que Spring MVC sabe responder: las que
     * traen su propio estado ({@link ErrorResponse}) y las dos que el resolvedor por defecto
     * traduce a {@code 400} sin traerlo.
     */
    private static boolean isProtocolRejection(Exception error) {
        return error instanceof ErrorResponse
                || error instanceof HttpMessageNotReadableException
                || error instanceof TypeMismatchException;
    }

    /** Fallo del servicio (5xx), registrado una sola vez en {@code ERROR} con traza. */
    private ProblemDetail serviceFailure(HttpStatus status, String title, ErrorCode code, String summary,
            Exception error) {
        ProblemDetail problem = problem(status, title, DETAIL_INTERNAL, code);
        if (shouldLog()) {
            logger.error("{} [code={}, requestId={}]", summary, code, problem.getProperties().get("requestId"),
                    error);
        }
        return problem;
    }

    /**
     * Rechazo de la petición (4xx), registrado una vez en {@code WARN} sin traza, con el estado, el
     * código, el {@code requestId} y la identidad de quien llama.
     */
    private ProblemDetail rejection(HttpStatus status, String title, String detail, ErrorCode code,
            String context) {
        ProblemDetail problem = problem(status, title, detail, code);
        if (shouldLog()) {
            logger.warn("Petición rechazada [status={}, code={}, requestId={}, firebaseUid={}] {}", status.value(),
                    code, problem.getProperties().get("requestId"), firebaseUidForLog(), context);
        }
        return problem;
    }

    /**
     * Identidad de quien llama para el registro: el valor de {@code X-User-Id} si es una identidad
     * válida y {@code -} si falta o no lo es, de modo que un valor mal formado nunca llega al log.
     */
    private static String firebaseUidForLog() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet)) {
            return NO_IDENTITY;
        }
        try {
            return FirebaseUid.required(servlet.getRequest().getHeader(IDENTITY_HEADER)).value();
        } catch (IdentityRequiredException withoutIdentity) {
            // La falta de identidad ya se responde por su propio camino; aquí solo se enmascara en el log.
            return NO_IDENTITY;
        }
    }

    /**
     * Código de una restricción del contrato.
     *
     * <p>Una regla de forma que declare su código como carga dinámica de la violación lo aporta
     * desde allí. Si no lo trae, el elemento lleva {@code REQUEST_INVALID_VALUE} y se registra un
     * {@code ERROR}: nunca {@code VALIDATION_FAILED}, que es el código de la operación entera. Cada
     * restricción nueva del contrato debe declarar su código para que esa rama no se use.</p>
     */
    private static ErrorCode codeOf(FieldError failure) {
        if (failure.contains(ConstraintViolation.class)
                && failure.unwrap(ConstraintViolation.class) instanceof HibernateConstraintViolation<?> violation
                && violation.getDynamicPayload(ErrorCode.class) != null) {
            return violation.getDynamicPayload(ErrorCode.class);
        }
        logger.error("Restricción del contrato sin código de error: {}.{}", failure.getField(), failure.getCode());
        return ErrorCode.REQUEST_INVALID_VALUE;
    }

    /** Nombre de la restricción violada, buscado en la cadena de causas; nunca el mensaje. */
    private static String constraintNameOf(Throwable error) {
        Throwable actual = error;
        for (int level = 0; actual != null && level < MAX_CAUSES; level++, actual = actual.getCause()) {
            if (actual instanceof ConstraintViolationException hibernate && hibernate.getConstraintName() != null) {
                return hibernate.getConstraintName();
            }
        }
        return "desconocida";
    }

    /** SQLState de la primera excepción de JDBC en la cadena de causas; nunca su mensaje. */
    private static String sqlStateOf(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSES; depth++, current = current.getCause()) {
            if (current instanceof SQLException jdbc && jdbc.getSQLState() != null) {
                return jdbc.getSQLState();
            }
        }
        return "desconocido";
    }

    /** Nombre simple de la causa más profunda: dice qué falló sin citar el valor recibido. */
    private static String mostSpecificClass(Throwable error) {
        Throwable actual = error;
        for (int level = 0; actual.getCause() != null && level < MAX_CAUSES; level++) {
            actual = actual.getCause();
        }
        return actual.getClass().getSimpleName();
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        problem.setProperty("requestId", resolveRequestId());
        URI original = originalPath();
        if (original != null) {
            problem.setInstance(original);
        }
        return problem;
    }

    /**
     * Indica si este error debe registrarse. Una petición registra su error una sola vez: si el
     * contenedor la vuelve a despachar a {@code /error} después de que ya se registró, el segundo
     * despacho responde con el mismo identificador y no escribe otra línea.
     */
    private boolean shouldLog() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet)) {
            return true;
        }
        HttpServletRequest request = servlet.getRequest();
        boolean repeated = request.getDispatcherType() == DispatcherType.ERROR
                && request.getAttribute(LOGGED_ATTRIBUTE) != null;
        request.setAttribute(LOGGED_ATTRIBUTE, Boolean.TRUE);
        return !repeated;
    }

    /**
     * Ruta de la petición original cuando el error llega por el despacho de error del contenedor
     * ({@code /error}), para que {@code instance} señale la ruta que pidió la persona y no {@code /error}.
     *
     * @return la ruta, o {@code null} si no es un despacho de error o la ruta no es una referencia
     *         válida (por ejemplo una codificación inválida), en cuyo caso rige la ruta del despacho
     */
    private static URI originalPath() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet)
                || !(servlet.getRequest().getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String path)
                || !path.startsWith("/")) {
            return null;
        }
        try {
            return new URI(path);
        } catch (URISyntaxException invalid) {
            return null;
        }
    }

    /**
     * Identificador de trazabilidad de la petición: el del Gateway si cumple la forma
     * admitida; si no, uno nuevo. Se devuelve también en el encabezado de la respuesta.
     *
     * <p>Además fija el tipo de contenido de la respuesta en
     * {@code application/problem+json;charset=UTF-8}. Al resolver una excepción, Spring limpia el
     * tipo de contenido y el contenedor de servlets borra con él la codificación que había puesto
     * el filtro de codificación; sin esto, el {@code Content-Type} del error saldría sin
     * {@code charset} (ASVS 4.1.1).</p>
     */
    private String resolveRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return UUID.randomUUID().toString();
        }
        String requestId = servlet.getRequest().getAttribute(REQUEST_ID_ATTRIBUTE) instanceof String resolved
                ? resolved
                : newRequestId(servlet.getRequest());
        if (servlet.getResponse() != null) {
            servlet.getResponse().setHeader(REQUEST_ID_HEADER, requestId);
            servlet.getResponse().setContentType(PROBLEM_JSON_UTF8);
        }
        return requestId;
    }

    /** Identificador recibido si cumple la forma admitida, o uno nuevo; queda en la petición. */
    private static String newRequestId(HttpServletRequest request) {
        String received = request.getHeader(REQUEST_ID_HEADER);
        String requestId = received != null && VALID_REQUEST_ID.matcher(received).matches()
                ? received
                : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return requestId;
    }
}
