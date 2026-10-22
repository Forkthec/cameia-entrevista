package tech.cameia.entrevista.infrastructure.config.ia;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;

/**
 * Crea el cliente de Gemini con la llave y el tiempo de espera de {@link LanguageModelProperties}
 * y apaga los reintentos de las dos librerías que intervienen en cada llamada (D-03).
 *
 * <p>La autoconfiguración de Spring AI respeta un {@link Client} propio y toma el único
 * {@link RetryTemplate} del contexto, así que no hace falta construir el modelo de chat a mano:
 * basta con aportar estos dos beans. Sin ellos, el SDK de Google y Spring AI reintentarían por
 * su cuenta, y una llamada lenta podría tardar varias veces el tiempo de espera.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LanguageModelProperties.class)
class LanguageModelConfiguration {

    /** Un solo intento por llamada en el SDK de Google: el primero no se repite. */
    private static final int UN_SOLO_INTENTO = 1;

    /**
     * Cliente del SDK de Google GenAI que usa el modelo de chat de Spring AI.
     *
     * @param propiedades llave y tiempo de espera ya validados
     * @param baseUrl dirección del proveedor; solo las pruebas la fijan, para apuntar a un servidor
     *                simulado, y vacía usa la dirección por defecto del SDK
     * @return cliente con un solo intento por llamada y el tiempo de espera configurado
     */
    @Bean
    Client languageModelClient(LanguageModelProperties propiedades,
            @Value("${cameia.llm.base-url:}") String baseUrl) {
        HttpOptions.Builder opciones = HttpOptions.builder()
                .timeout(Math.toIntExact(propiedades.timeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(UN_SOLO_INTENTO).build());
        if (!baseUrl.isBlank()) {
            opciones.baseUrl(baseUrl);
        }
        return Client.builder()
                .apiKey(propiedades.apiKey())
                .httpOptions(opciones.build())
                .build();
    }

    /**
     * Política de reintentos de Spring AI para el modelo de chat: ninguno. Reemplaza la de
     * {@code SpringAiRetryAutoConfiguration}, que reintenta varias veces por defecto. Los
     * reintentos pertenecen a los casos de uso (HU-5.2 y HU-5.6).
     *
     * @return plantilla que ejecuta la llamada una vez y propaga su fallo
     */
    @Bean
    RetryTemplate languageModelRetryTemplate() {
        return new RetryTemplate(error -> false);
    }
}
