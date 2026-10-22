package tech.cameia.entrevista.presentation.advice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.RequestPathInvalidException;

/**
 * Responde en el formato común del servicio el {@code 400} que Tomcat resuelve por su cuenta
 * cuando no puede interpretar la petición (por ejemplo, una ruta con codificación porcentual
 * inválida como {@code /%zz}), antes de que exista una ruta, un filtro o un controlador.
 *
 * <p>Tomcat llama a esta válvula para armar el cuerpo de cualquier error que no pasó por la
 * página de error de la aplicación. Solo el {@code 400} se responde aquí; los demás estados siguen
 * con el cuerpo de Tomcat. El cuerpo es fijo: no repite la ruta ni ningún dato de la petición.
 * Como la petición no se pudo interpretar, su {@code X-Request-Id} no es confiable y se genera
 * un identificador nuevo.</p>
 */
public class ContainerErrorReportValve extends ErrorReportValve {

    private static final Logger logger = LoggerFactory.getLogger(ContainerErrorReportValve.class);

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        if (response.getStatus() != 400 || response.isCommitted()) {
            super.report(request, response, throwable);
            return;
        }
        String requestId = UUID.randomUUID().toString();
        BusinessExceptionHandler.Definition definition =
                BusinessExceptionHandler.DEFINITIONS.get(ErrorCode.REQUEST_PATH_INVALID_FORMAT);
        byte[] body = ("{\"title\":\"" + definition.title() + "\",\"status\":400,\"detail\":\""
                + RequestPathInvalidException.MESSAGE + "\",\"code\":\"" + ErrorCode.REQUEST_PATH_INVALID_FORMAT
                + "\",\"requestId\":\"" + requestId + "\"}").getBytes(StandardCharsets.UTF_8);
        response.setHeader("X-Request-Id", requestId);
        response.setContentType("application/problem+json;charset=UTF-8");
        response.setContentLength(body.length);
        try {
            response.getOutputStream().write(body);
        } catch (IOException e) {
            // El cliente cerró la conexión antes de recibir la respuesta; no hay a quién responder.
            logger.warn("No se pudo escribir la respuesta 400 [code={}, requestId={}, causa={}]",
                    ErrorCode.REQUEST_PATH_INVALID_FORMAT, requestId, e.getClass().getSimpleName());
            return;
        }
        logger.warn("Petición rechazada [status=400, code={}, requestId={}, firebaseUid=-] el contenedor no pudo interpretarla",
                ErrorCode.REQUEST_PATH_INVALID_FORMAT, requestId);
    }
}
