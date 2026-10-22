package tech.cameia.entrevista.presentation.advice;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tech.cameia.entrevista.domain.exception.RequestPathInvalidException;
import tech.cameia.entrevista.domain.exception.RequestPayloadTooLargeException;

/**
 * Atiende el despacho de error del contenedor ({@code /error}) para que lo que Tomcat reporta con
 * {@code sendError} salga con la forma común del servicio y no con el cuerpo por defecto de Spring
 * Boot ({@code timestamp}, {@code error}, {@code path}) ni con la página HTML del servidor.
 *
 * <p>No arma la respuesta: lanza la excepción que corresponde al estado original y la resuelve
 * {@link BusinessExceptionHandler}, de modo que el {@code requestId}, la codificación y el registro
 * salen del mismo lugar que el resto de errores.</p>
 */
@Hidden
@RestController
class ContainerErrorController implements ErrorController {

    /**
     * Traduce el estado que reportó el contenedor a la excepción equivalente.
     *
     * <p>Sin estado original (alguien pidió {@code /error} directamente) se responde como una ruta
     * inexistente. La ruta original no se pasa a ninguna excepción: puede traer una codificación
     * inválida ({@code %zz}) que no debe llegar a la respuesta ni al registro.</p>
     *
     * <p>Un estado sin código propio termina en el error genérico; si el contenedor informó la
     * excepción que lo causó, viaja como causa para que el registro la conserve.</p>
     *
     * @param request petición del despacho de error, con el estado original como atributo
     * @throws Exception la excepción que resuelve {@link BusinessExceptionHandler}
     */
    @RequestMapping("/error")
    void error(HttpServletRequest request) throws Exception {
        Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        switch (status == null ? 0 : status) {
            case 0, 404 -> throw new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), "/", "");
            case 405 -> throw new HttpRequestMethodNotSupportedException(request.getMethod());
            case 406 -> throw new HttpMediaTypeNotAcceptableException("");
            case 415 -> throw new HttpMediaTypeNotSupportedException("");
            case 413 -> throw new RequestPayloadTooLargeException();
            // Un 400 del contenedor es una petición que no se pudo interpretar, casi siempre por la ruta.
            case 400 -> throw new RequestPathInvalidException();
            default -> throw new IllegalStateException("Error del contenedor sin código propio: estado=" + status,
                    request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable cause ? cause : null);
        }
    }
}
