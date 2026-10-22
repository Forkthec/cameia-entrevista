package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Comprueba la válvula que responde el 400 del contenedor en el formato común, sin levantar
 * Tomcat: la respuesta real de punta a punta la cubre {@code FrameworkErrorsTest}.
 */
@ExtendWith(OutputCaptureExtension.class)
class ContainerErrorReportValveTest {

    private final ContainerErrorReportValve valve = new ContainerErrorReportValve();

    private final Request request = mock(Request.class);

    private final Response response = mock(Response.class);

    @Test
    @DisplayName("Un 400 se responde como problem+json con un requestId nuevo y sin datos de la petición")
    void report_shouldWriteProblemJson_whenStatusIs400(CapturedOutput output) throws IOException {
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getStatus()).thenReturn(400);
        when(response.getOutputStream()).thenReturn(stream);

        valve.report(request, response, null);

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(stream).write(body.capture());
        String json = new String(body.getValue(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"code\":\"REQUEST_PATH_INVALID_FORMAT\"")
                .contains("\"detail\":\"La petición no es válida.\"")
                .contains("\"status\":400")
                .containsPattern("\"requestId\":\"[0-9a-f-]{36}\"");
        verify(response).setContentType("application/problem+json;charset=UTF-8");
        verify(response).setContentLength(body.getValue().length);
        assertThat(output).contains("status=400").contains("REQUEST_PATH_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Si el cliente cerró la conexión, se registra un aviso y no se propaga el fallo")
    void report_shouldWarn_whenClientClosedConnection(CapturedOutput output) throws IOException {
        when(response.getStatus()).thenReturn(400);
        when(response.getOutputStream()).thenThrow(new IOException("conexión cerrada por el cliente"));

        valve.report(request, response, null);

        assertThat(output).contains("No se pudo escribir la respuesta 400").contains("IOException");
        assertThat(output.getAll()).doesNotContain("conexión cerrada por el cliente");
    }

    @Test
    @DisplayName("Un estado distinto de 400 conserva el cuerpo de Tomcat")
    void report_shouldKeepTomcatBody_whenStatusIsNot400() throws IOException {
        when(response.getStatus()).thenReturn(431);

        valve.report(request, response, null);

        verify(response, never()).setContentType(anyString());
        verify(response, never()).setHeader(anyString(), any());
    }

    @Test
    @DisplayName("Una respuesta ya enviada no se vuelve a escribir")
    void report_shouldNotWrite_whenResponseAlreadyCommitted() {
        when(response.getStatus()).thenReturn(400);
        when(response.isCommitted()).thenReturn(true);

        valve.report(request, response, null);

        verify(response, never()).setContentType(anyString());
    }
}
