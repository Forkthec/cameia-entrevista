package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import jakarta.servlet.DispatcherType;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.TypeMismatchException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Ramas del manejador de errores que las peticiones HTTP normales no alcanzan: el segundo
 * despacho de una misma petición, las cadenas de causas largas y las excepciones de protocolo.
 */
@ExtendWith(OutputCaptureExtension.class)
class HandlerBranchesTest {

    private final BusinessExceptionHandler handler = new BusinessExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");

    private final MockMvc probe = MockMvcBuilders
            .standaloneSetup(new ErrorProbeController())
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .build();

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Sin X-User-Id obligatorio la respuesta es 401 IDENTITY_REQUIRED")
    void missingRequiredIdentityHeader_shouldReturn401() throws Exception {
        probe.perform(get("/test/errors/required-identity"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    @DisplayName("Un parámetro obligatorio ausente es 422 REQUEST_INVALID_VALUE")
    void missingRequiredParam_shouldReturn422() throws Exception {
        probe.perform(get("/test/errors/required-param"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_VALUE"));
    }

    @Test
    @DisplayName("La restricción que declara su código lo devuelve en el elemento del campo")
    void declaredConstraintCode_shouldBeReturnedPerField() throws Exception {
        probe.perform(post("/test/errors/coded").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"a\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("value"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUEST_BODY_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("Un campo rechazado sin mensaje usa el texto «Valor no válido»")
    void fieldWithoutMessage_shouldUseDefaultText() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "name", null, false, null, null, null));
        MethodParameter parameter = new MethodParameter(
                ErrorProbeController.class.getDeclaredMethod("body", ErrorProbeController.ProbeRequest.class), 0);

        var problem = handler.camposInvalidos(new MethodArgumentNotValidException(parameter, binding));

        assertThat(problem.getProperties().get("errors").toString()).contains("Valor no válido");
    }

    @Test
    @DisplayName("Un rechazo de protocolo que ningún manejador cubre se relanza sin registrar")
    void protocolRejections_shouldBeRethrown(CapturedOutput output) {
        assertThatThrownBy(() -> handler.falloInterno(new MissingServletRequestPartException("archivo")))
                .isInstanceOf(MissingServletRequestPartException.class);
        assertThatThrownBy(() -> handler.falloInterno(
                new HttpMessageNotReadableException("x", new MockHttpInputMessage(new byte[0]))))
                .isInstanceOf(HttpMessageNotReadableException.class);
        assertThatThrownBy(() -> handler.falloInterno(new TypeMismatchException("v", String.class)))
                .isInstanceOf(TypeMismatchException.class);
        assertThat(output).doesNotContain("Fallo no controlado");
    }

    @Test
    @DisplayName("Sin contexto de petición el rechazo se registra con firebaseUid «-»")
    void rejectionWithoutRequestContext_shouldLogDash(CapturedOutput output) {
        RequestContextHolder.resetRequestAttributes();

        handler.peticionIncompleta(new MissingServletRequestParameterException("page", "String"));

        assertThat(output).contains("firebaseUid=-");
    }

    @Test
    @DisplayName("El segundo despacho de error de una petición no repite la línea del log")
    void errorRedispatch_shouldLogOnce(CapturedOutput output) throws Exception {
        request.setDispatcherType(DispatcherType.ERROR);

        handler.falloInterno(new IllegalStateException("a"));
        handler.falloInterno(new IllegalStateException("b"));
        handler.baseNoDisponible(new DataAccessResourceFailureException("c"));
        handler.integridadDeDatos(new DataIntegrityViolationException("d"));
        handler.peticionIncompleta(new MissingServletRequestParameterException("page", "String"));

        // Solo el primer error de la petición se escribe; los siguientes la encuentran ya registrada.
        assertThat(output.getAll()).containsOnlyOnce("Fallo no controlado");
        assertThat(output).doesNotContain("Base de datos no disponible")
                .doesNotContain("Violación de restricción")
                .doesNotContain("Petición rechazada");
    }

    @Test
    @DisplayName("La restricción nombrada y el SQLState salen de la cadena de causas")
    void namedConstraint_shouldBeLogged(CapturedOutput output) {
        handler.integridadDeDatos(new DataIntegrityViolationException("x",
                new ConstraintViolationException("m", new SQLException("m", "23505"), "uq_x")));

        assertThat(output).contains("constraint=uq_x").contains("sqlState=23505");
    }

    @Test
    @DisplayName("Una cadena de causas de 40 niveles se recorta sin buscar más allá del tope")
    void deepCauseChain_shouldBeBounded(CapturedOutput output) {
        Throwable deep = new SQLException("hondo", "08001");
        for (int i = 0; i < 40; i++) {
            deep = new RuntimeException("nivel " + i, deep);
        }

        handler.baseNoDisponible(new DataAccessResourceFailureException("raíz", deep));
        handler.integridadDeDatos(new DataIntegrityViolationException("x", deep));

        assertThat(output).contains("sqlState=desconocido").contains("constraint=desconocida");
    }
}
