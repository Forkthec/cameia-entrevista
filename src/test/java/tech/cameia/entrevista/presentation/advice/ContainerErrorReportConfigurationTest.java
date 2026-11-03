package tech.cameia.entrevista.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.core.StandardHost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.TomcatContextCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

/**
 * Comprueba que la configuración cambia la válvula de reporte de errores del anfitrión de
 * Tomcat. La respuesta real la cubre {@code FrameworkErrorsTest}.
 */
class ContainerErrorReportConfigurationTest {

    private TomcatContextCustomizer customizer() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        new ContainerErrorReportConfiguration().containerErrorReportValve().customize(factory);
        return factory.getContextCustomizers().iterator().next();
    }

    @Test
    @DisplayName("El anfitrión de Tomcat queda con la válvula del servicio")
    void customize_shouldSetValveClass_whenParentIsStandardHost() {
        StandardHost host = new StandardHost();
        Context context = mock(Context.class);
        when(context.getParent()).thenReturn(host);

        customizer().customize(context);

        assertThat(host.getErrorReportValveClass()).isEqualTo(ContainerErrorReportValve.class.getName());
    }

    @Test
    @DisplayName("Si el padre del contexto no es un StandardHost no se toca nada")
    void customize_shouldDoNothing_whenParentIsNotStandardHost() {
        Context context = mock(Context.class);
        when(context.getParent()).thenReturn(mock(Container.class));

        customizer().customize(context);

        assertThat(context.getParent()).isNotInstanceOf(StandardHost.class);
    }
}
