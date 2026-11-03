package tech.cameia.entrevista.presentation.filter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Da a cada petición su identificador de trazabilidad y lo deja disponible para todo lo que
 * escriba en el registro mientras se atiende.
 *
 * <p>Toma el {@code X-Request-Id} que pone el Gateway si tiene la forma admitida
 * ({@code ^[A-Za-z0-9._-]{1,64}$}); si falta o no la cumple, genera un UUID v4. Un valor con saltos
 * de línea o de tamaño arbitrario nunca llega al registro ni a la respuesta. El identificador queda en
 * tres lugares: el atributo de la petición que lee el manejador de errores (para que el cuerpo de un
 * error lleve el mismo valor), el MDC del hilo (para que cada línea del registro lo muestre) y el
 * encabezado de la respuesta. El MDC se limpia siempre al terminar, porque el hilo se reutiliza.</p>
 *
 * <p>No actúa en el despacho de error interno del contenedor: el manejador de errores resuelve ahí su
 * propio identificador a partir del mismo atributo.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** Encabezado con el identificador de la petición, que pone el Gateway. */
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Clave del MDC que el patrón del registro imprime como {@code [%X{requestId}]}. */
    static final String MDC_KEY = "requestId";

    /**
     * Atributo de la petición donde el manejador de errores busca el identificador ya resuelto; es el
     * nombre que el manejador construye con su propia clase.
     */
    static final String REQUEST_ID_ATTRIBUTE =
            "tech.cameia.entrevista.presentation.advice.BusinessExceptionHandler.requestId";

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(REQUEST_ID_HEADER));
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** El valor recibido si cumple la forma admitida; si no, un UUID nuevo. */
    private static String resolve(String received) {
        return received != null && VALID_REQUEST_ID.matcher(received).matches()
                ? received
                : UUID.randomUUID().toString();
    }
}
