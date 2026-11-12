package tech.cameia.entrevista.infrastructure.config.voz;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tech.cameia.entrevista.domain.policy.TranscriptionRetryPolicy;

/**
 * Ensambla el cliente HTTP hacia el servicio de Voz y la política de reintento de la transcripción.
 *
 * <p>El cliente HTTP del JDK no reintenta por su cuenta: cada llamada es un intento, y los reintentos los decide el
 * caso de uso con {@link TranscriptionRetryPolicy}.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VoiceServiceProperties.class)
class VoiceServiceConfiguration {

    /**
     * Cliente HTTP del JDK hacia Voz, con el tiempo de conexión configurado.
     *
     * @param properties configuración ya validada
     * @return cliente HTTP/1.1 sin redirecciones
     */
    @Bean
    HttpClient voiceServiceHttpClient(VoiceServiceProperties properties) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Política de reintento de la transcripción (HU-5.13).
     *
     * @return la política
     */
    @Bean
    TranscriptionRetryPolicy transcriptionRetryPolicy() {
        return new TranscriptionRetryPolicy();
    }
}
