package tech.cameia.entrevista.presentation.advice;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;

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
 * persona. Cada error se registra aquí y una sola vez; todos los que maneja hoy son fallos del
 * servicio (5xx), así que van en {@code ERROR} con traza. Ninguna línea del log ni ninguna
 * respuesta repite el texto enviado al modelo ni el recibido.</p>
 *
 * <p>Solo cubre los fallos del cliente del modelo y el fallo imprevisto; los de validación, de
 * protocolo y de base de datos los agrega el motor base de la entrevista (CM-311).</p>
 */
@RestControllerAdvice
class BusinessExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BusinessExceptionHandler.class);

    /** Encabezado con el identificador de la petición, que pone el Gateway. */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * Atributo de la petición con el identificador ya resuelto. El despacho de error del contenedor
     * ({@code /error}) reutiliza la misma petición: así la respuesta lleva el identificador que ya
     * quedó en el registro.
     */
    private static final String REQUEST_ID_ATTRIBUTE = BusinessExceptionHandler.class.getName() + ".requestId";

    /** Atributo de la petición que marca que su error ya se registró. */
    private static final String LOGGED_ATTRIBUTE = BusinessExceptionHandler.class.getName() + ".logged";

    /** Tipo de contenido de toodo error del servicio, con la codificación explícita (ASVS 4.1.1). */
    private static final String PROBLEM_JSON_UTF8 = "application/problem+json;charset=UTF-8";

    /**
     * Forma admitida para un identificador recibido. Lo que no la cumple se reemplaza por
     * uno nuevo: así un valor con saltos de línea o de tamaño arbitrario nunca llega al log
     * ni a la respuesta.
     */
    private static final Pattern REQUEST_ID_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /** Título de los fallos de una dependencia que la persona puede reintentar. */
    private static final String TITULO_NO_DISPONIBLE = "Servicio no disponible";

    /** {@code detail} de todo fallo que la persona no puede corregir. */
    private static final String DETALLE_INTERNO = "Ocurrió un error. Inténtalo de nuevo.";

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
    ProblemDetail modeloNoDisponible(LlmUnavailableException error) {
        return falloDelServicio(HttpStatus.SERVICE_UNAVAILABLE, TITULO_NO_DISPONIBLE, error.getErrorCode(),
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
    ProblemDetail limiteDelModeloAgotado(LlmLimitReachedException error) {
        return falloDelServicio(HttpStatus.SERVICE_UNAVAILABLE, TITULO_NO_DISPONIBLE, error.getErrorCode(),
                "Límite de uso del modelo de lenguaje agotado", error);
    }

    /**
     * Cualquier fallo que ningún otro manejador cubre.
     *
     * <p>Con la traza: es la única forma de diagnosticar un fallo que nadie previó. El
     * {@code requestId} de la respuesta lleva a esa línea del log.</p>
     *
     * @param error excepción no prevista
     * @return {@code 500 Internal Server Error} con un mensaje genérico
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail falloInterno(Exception error) {
        return falloDelServicio(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ErrorCode.INTERNAL_ERROR,
                "Fallo no controlado", error);
    }

    /** Fallo del servicio (5xx), registrado una sola vez en {@code ERROR} con traza. */
    private ProblemDetail falloDelServicio(HttpStatus estado, String titulo, ErrorCode codigo, String resumen,
            Exception error) {
        ProblemDetail problema = problema(estado, titulo, DETALLE_INTERNO, codigo);
        if (primerRegistro()) {
            logger.error("{} [code={}, requestId={}]", resumen, codigo, problema.getProperties().get("requestId"),
                    error);
        }
        return problema;
    }

    private ProblemDetail problema(HttpStatus estado, String titulo, String detalle, ErrorCode codigo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("code", codigo.name());
        problema.setProperty("requestId", resolverRequestId());
        URI original = rutaOriginal();
        if (original != null) {
            problema.setInstance(original);
        }
        return problema;
    }

    /**
     * Indica si este error debe registrarse. Una petición registra su error una sola vez: si el
     * contenedor la vuelve a despachar a {@code /error} después de que ya se registró, el segundo
     * despacho responde con el mismo identificador y no escribe otra línea.
     */
    private boolean primerRegistro() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet)) {
            return true;
        }
        HttpServletRequest request = servlet.getRequest();
        boolean repetido = request.getDispatcherType() == DispatcherType.ERROR
                && request.getAttribute(LOGGED_ATTRIBUTE) != null;
        request.setAttribute(LOGGED_ATTRIBUTE, Boolean.TRUE);
        return !repetido;
    }

    /**
     * Ruta de la petición original cuando el error llega por el despacho de error del contenedor
     * ({@code /error}), para que {@code instance} señale la ruta que pidió la persona y no {@code /error}.
     *
     * @return la ruta, o {@code null} si no es un despacho de error o la ruta no es una referencia
     *         válida (por ejemplo una codificación inválida), en cuyo caso rige la ruta del despacho
     */
    private static URI rutaOriginal() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet)
                || !(servlet.getRequest().getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String ruta)
                || !ruta.startsWith("/")) {
            return null;
        }
        try {
            return new URI(ruta);
        } catch (URISyntaxException invalida) {
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
    private String resolverRequestId() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (!(atributos instanceof ServletRequestAttributes servlet)) {
            return UUID.randomUUID().toString();
        }
        String requestId = servlet.getRequest().getAttribute(REQUEST_ID_ATTRIBUTE) instanceof String resuelto
                ? resuelto
                : nuevoRequestId(servlet.getRequest());
        if (servlet.getResponse() != null) {
            servlet.getResponse().setHeader(REQUEST_ID_HEADER, requestId);
            servlet.getResponse().setContentType(PROBLEM_JSON_UTF8);
        }
        return requestId;
    }

    /** Identificador recibido si cumple la forma admitida, o uno nuevo; queda en la petición. */
    private static String nuevoRequestId(HttpServletRequest request) {
        String recibido = request.getHeader(REQUEST_ID_HEADER);
        String requestId = recibido != null && REQUEST_ID_VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return requestId;
    }
}
