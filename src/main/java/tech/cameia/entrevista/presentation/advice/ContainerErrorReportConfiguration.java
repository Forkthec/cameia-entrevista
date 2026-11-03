package tech.cameia.entrevista.presentation.advice;

import org.apache.catalina.core.StandardHost;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra en Tomcat la válvula que responde en el formato común del servicio el {@code 400} que
 * el contenedor resuelve antes de llegar a la aplicación.
 */
@Configuration(proxyBeanMethods = false)
class ContainerErrorReportConfiguration {

    /**
     * Cambia la clase de la válvula de reporte de errores del anfitrión de Tomcat.
     *
     * @return personalizador de la fábrica del servidor web
     */
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> containerErrorReportValve() {
        return factory -> factory.addContextCustomizers(context -> {
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(ContainerErrorReportValve.class.getName());
            }
        });
    }
}
