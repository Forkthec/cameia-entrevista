package tech.cameia.entrevista.infrastructure.config.ia;

import java.time.Duration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import io.micrometer.observation.ObservationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryTemplate;

/**
 * Arma los modelos de chat de los dos proveedores de IA: Gemini (principal) y Qwen en Groq (respaldo), cada uno
 * con el tiempo de espera de su operación, y apaga los reintentos de las librerías que intervienen en cada llamada
 * (D-03): los reintentos pertenecen al ciclo de la aplicación.
 *
 * <p>Los modelos se arman a mano y no se publican como beans {@code ChatModel}: la autoconfiguración de chat de
 * Spring AI está apagada ({@code spring.ai.model.chat=none}), porque con dos proveedores y dos tiempos habría
 * varios candidatos y ninguno elegible por tipo. Quien los necesita recibe {@link GeminiChatModels} o
 * {@link GroqChatModels}.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({LanguageModelProperties.class, AiRetryProperties.class,
        FallbackModelProperties.class})
class LanguageModelConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LanguageModelConfiguration.class);

    /** Un solo intento por llamada en el SDK de Google: el primero no se repite. */
    private static final int UN_SOLO_INTENTO = 1;

    /** Propiedad que cambia la dirección del proveedor principal; solo la usan las pruebas. */
    private static final String BASE_URL = "cameia.llm.base-url";

    /**
     * Modelos de chat de Gemini: uno con el tiempo de espera de la planificación y otro con el de la evaluación.
     *
     * <p>La propiedad {@code cameia.llm.base-url} cambia la dirección del proveedor. Solo las pruebas la fijan,
     * para apuntar a un servidor simulado; sin ella se usa la dirección por defecto del SDK. No está en
     * {@code .env.example} a propósito.</p>
     *
     * @param propiedades llave, modelo y tiempos ya validados
     * @param entorno entorno de Spring, del que se lee {@code cameia.llm.base-url}
     * @param tools gestor de herramientas de Spring AI, que el modelo exige aunque no se usen herramientas
     * @param retry plantilla sin reintentos
     * @return los dos modelos
     */
    @Bean
    GeminiChatModels geminiChatModels(LanguageModelProperties propiedades, Environment entorno,
            ToolCallingManager tools, RetryTemplate retry) {
        String baseUrl = entorno.getProperty(BASE_URL, "");
        return new GeminiChatModels(
                geminiModel(propiedades, propiedades.timeout(), baseUrl, tools, retry),
                geminiModel(propiedades, propiedades.evaluationTimeout(), baseUrl, tools, retry));
    }

    /**
     * Modelos de chat del respaldo en Groq, o ninguno si no hay llave.
     *
     * <p>Sin llave el servicio arranca igual y avisa una sola vez: el tercer intento de cada ciclo usará el
     * proveedor principal.</p>
     *
     * @param respaldo configuración del respaldo
     * @param propiedades tiempos de espera de cada operación
     * @return los dos modelos, o {@link GroqChatModels#absent()}
     */
    @Bean
    GroqChatModels groqChatModels(FallbackModelProperties respaldo, LanguageModelProperties propiedades) {
        if (!respaldo.hasKey()) {
            logger.warn("Proveedor de respaldo sin llave: el tercer intento usará el principal");
            return GroqChatModels.absent();
        }
        return new GroqChatModels(groqModel(respaldo, propiedades.timeout()),
                groqModel(respaldo, propiedades.evaluationTimeout()));
    }

    /**
     * Política de reintentos de Spring AI para el modelo de chat: ninguno. Los reintentos pertenecen al ciclo de
     * intentos de la aplicación.
     *
     * @return plantilla que ejecuta la llamada una vez y propaga su fallo
     */
    @Bean
    RetryTemplate languageModelRetryTemplate() {
        return new RetryTemplate(error -> false);
    }

    private static ChatModel geminiModel(LanguageModelProperties propiedades, Duration timeout, String baseUrl,
            ToolCallingManager tools, RetryTemplate retry) {
        HttpOptions.Builder opciones = HttpOptions.builder()
                .timeout(Math.toIntExact(timeout.toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(UN_SOLO_INTENTO).build());
        if (!baseUrl.isBlank()) {
            opciones.baseUrl(baseUrl);
        }
        Client cliente = Client.builder()
                .apiKey(propiedades.apiKey())
                .httpOptions(opciones.build())
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(cliente)
                .options(GoogleGenAiChatOptions.builder().model(propiedades.model()).build())
                .toolCallingManager(tools)
                .retryTemplate(retry)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    private static ChatModel groqModel(FallbackModelProperties respaldo, Duration timeout) {
        // maxRetries(0): el SDK de OpenAI reintenta por su cuenta si no se le apaga, y el ciclo ya reintenta.
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .model(respaldo.model())
                        .apiKey(respaldo.apiKey())
                        .baseUrl(respaldo.apiBaseUrl())
                        .timeout(timeout)
                        .maxRetries(0)
                        .build())
                .build();
    }
}
