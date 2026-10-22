package tech.cameia.entrevista.infrastructure.ia;

import java.util.concurrent.TimeUnit;

import com.google.genai.errors.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;

/**
 * Implementa {@link LanguageModelPort} con el modelo de chat de Gemini que arma Spring AI.
 *
 * <p>Cada llamada lleva sus opciones (modelo, temperatura, nivel de pensamiento y tope de
 * tokens), así que el adaptador no depende de la configuración por defecto de Spring AI. El
 * tiempo de espera y la ausencia de reintentos los fija el cliente de
 * {@code LanguageModelConfiguration}.</p>
 *
 * <p>Por cada llamada se registra una sola línea de medición, con el resultado y la duración.
 * Nunca se registran la llave, el texto enviado ni el texto recibido (D-07).</p>
 */
@Component
class GeminiLanguageModelAdapter implements LanguageModelPort {

    private static final Logger logger = LoggerFactory.getLogger(GeminiLanguageModelAdapter.class);

    /** Resultado de la medición cuando el modelo devolvió un texto utilizable. */
    private static final String RESULTADO_OK = "ok";

    /** Estado HTTP con el que el proveedor rechaza una llamada por cuota agotada. */
    private static final int CUOTA_AGOTADA = 429;

    /**
     * Máximo de causas que se recorren: una cadena con un ciclo (A causada por B y B por A) no
     * debe dejar el hilo en un bucle.
     */
    private static final int MAX_CAUSAS = 32;

    private final ChatModel chatModel;

    private final LanguageModelProperties propiedades;

    /**
     * Crea el adaptador.
     *
     * @param chatModel modelo de chat de Gemini de Spring AI
     * @param propiedades modelo, temperatura y nivel de pensamiento de cada llamada
     */
    GeminiLanguageModelAdapter(ChatModel chatModel, LanguageModelProperties propiedades) {
        this.chatModel = chatModel;
        this.propiedades = propiedades;
    }

    /**
     * {@inheritDoc}
     *
     * <p>El modelo de la respuesta es el que informa Gemini ({@code modelVersion}), y el
     * configurado si no lo informa. Los tokens que el proveedor no informa valen {@code 0}.</p>
     *
     * <p>Clasificación de los fallos (D-05): un {@code 429} del proveedor, en cualquier punto de la
     * cadena de causas, es {@link LlmLimitReachedException}; cualquier otro fallo de la llamada
     * (tiempo agotado, error del proveedor, conexión cortada, cuerpo ilegible) y una respuesta sin
     * texto son {@link LlmUnavailableException}. La causa técnica se conserva para el log.</p>
     */
    @Override
    public LanguageModelResponse generate(LanguageModelPrompt prompt) {
        long inicio = System.nanoTime();
        String resultado = RESULTADO_OK;
        try {
            return aRespuesta(llamar(prompt));
        } catch (BusinessException fallo) {
            resultado = fallo.getErrorCode().name();
            throw fallo;
        } finally {
            logger.info("Llamada al modelo [resultado={}, duracionMs={}]", resultado,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
        }
    }

    /** Hace la llamada y traduce su fallo a la excepción de negocio que corresponde. */
    private ChatResponse llamar(LanguageModelPrompt prompt) {
        try {
            return chatModel.call(new Prompt(prompt.text(), opciones(prompt)));
        } catch (RuntimeException fallo) {
            throw traducir(fallo);
        }
    }

    /**
     * Busca un {@code 429} en la cadena de causas: Spring AI envuelve la excepción del SDK, y
     * el nivel en que queda depende de la versión.
     */
    private static BusinessException traducir(RuntimeException fallo) {
        Throwable causa = fallo;
        for (int nivel = 0; causa != null && nivel < MAX_CAUSAS; nivel++, causa = causa.getCause()) {
            if (causa instanceof ApiException api && api.code() == CUOTA_AGOTADA) {
                return new LlmLimitReachedException(fallo);
            }
        }
        return new LlmUnavailableException(fallo);
    }

    private GoogleGenAiChatOptions opciones(LanguageModelPrompt prompt) {
        return GoogleGenAiChatOptions.builder()
                .model(propiedades.model())
                .temperature(propiedades.temperature())
                .thinkingLevel(propiedades.thinkingLevel())
                .maxOutputTokens(prompt.maxOutputTokens())
                .build();
    }

    private LanguageModelResponse aRespuesta(ChatResponse respuesta) {
        Generation generacion = respuesta == null ? null : respuesta.getResult();
        String texto = generacion == null ? null : generacion.getOutput().getText();
        if (texto == null || texto.isBlank()) {
            // Una respuesta vacía nunca es un valor válido (REQ-12); la causa no lleva texto del modelo.
            throw new LlmUnavailableException(new IllegalStateException("El modelo devolvió una respuesta sin texto."));
        }
        ChatResponseMetadata metadatos = respuesta.getMetadata();
        Usage uso = metadatos.getUsage();
        String modelo = metadatos.getModel() == null || metadatos.getModel().isBlank()
                ? propiedades.model()
                : metadatos.getModel();
        return new LanguageModelResponse(texto, tokens(uso.getPromptTokens()), tokens(uso.getCompletionTokens()),
                modelo);
    }

    /** Tokens informados por el proveedor, o {@code 0} si no los informó. */
    private static int tokens(Integer informados) {
        return informados == null ? 0 : informados;
    }
}
