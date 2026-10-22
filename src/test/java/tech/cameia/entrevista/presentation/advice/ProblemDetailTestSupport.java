package tech.cameia.entrevista.presentation.advice;

import java.util.Set;

import tech.cameia.entrevista.domain.exception.ErrorCode;

/**
 * Da acceso al manejador de errores desde las pruebas de los controladores.
 *
 * <p>{@code BusinessExceptionHandler} es de alcance de paquete a propósito: nadie fuera de
 * {@code presentation.advice} debería instanciarlo. Esta clase vive en ese mismo paquete,
 * pero en el código de pruebas, y solo sirve para armar el {@code MockMvc} con el mismo
 * manejador que usa la aplicación. Sin ella, las pruebas comprobarían el formato de error
 * de un manejador distinto al real.</p>
 */
public final class ProblemDetailTestSupport {

    private ProblemDetailTestSupport() {
    }

    /**
     * Crea el manejador de errores real.
     *
     * @return manejador que traduce las excepciones a {@code application/problem+json}
     */
    public static Object errorHandler() {
        return new BusinessExceptionHandler();
    }

    /**
     * Códigos de negocio que el manejador sabe responder.
     *
     * @return los códigos de la tabla de definiciones del manejador
     */
    public static Set<ErrorCode> businessCodes() {
        return BusinessExceptionHandler.DEFINITIONS.keySet();
    }
}
