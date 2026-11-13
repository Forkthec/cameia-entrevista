package tech.cameia.entrevista.infrastructure.ia;

import java.util.concurrent.TimeUnit;

import com.openai.errors.OpenAIServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.infrastructure.config.ia.FallbackModelProperties;
import tech.cameia.entrevista.infrastructure.config.ia.GroqChatModels;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;

/**
 * Proveedor de respaldo de IA: llama a Qwen en Groq por su API compatible con OpenAI.
 *
 * <p>Cada llamada pide el razonamiento apagado ({@code reasoning_effort=none}) y, si el prompt trae esquema, una
 * respuesta {@code json_schema} estricta. Los fallos se traducen igual que en el adaptador de Gemini: un 429 en
 * cualquier punto de la cadena de causas es {@link LlmLimitReachedException}; cualquier otro fallo y una respuesta
 * sin texto son {@link LlmUnavailableException}. Nunca se registran la llave, el texto enviado ni el recibido.</p>
 */
@Component
class GroqLanguageModelAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GroqLanguageModelAdapter.class);

    private static final String RESULTADO_OK = "ok";

    /** Estado HTTP con el que el proveedor rechaza una llamada por cuota agotada. */
    private static final int CUOTA_AGOTADA = 429;

    /** Máximo de causas que se recorren: una cadena con un ciclo no debe dejar el hilo en un bucle. */
    private static final int MAX_CAUSAS = 32;

    private final GroqChatModels modelos;

    private final FallbackModelProperties respaldo;

    private final LanguageModelProperties propiedades;

    /**
     * Crea el adaptador.
     *
     * @param modelos modelos de chat de Groq, o vacíos si no hay llave
     * @param respaldo modelo y dirección del respaldo
     * @param propiedades temperatura de cada llamada
     */
    GroqLanguageModelAdapter(GroqChatModels modelos, FallbackModelProperties respaldo,
            LanguageModelProperties propiedades) {
        this.modelos = modelos;
        this.respaldo = respaldo;
        this.propiedades = propiedades;
    }

    /**
     * Indica si el respaldo puede usarse.
     *
     * @return {@code true} si hay llave del respaldo
     */
    boolean isAvailable() {
        return modelos.available();
    }

    /**
     * Envía el prompt a Groq y devuelve su respuesta.
     *
     * @param prompt texto, tope de tokens, esquema y operación
     * @return texto de la respuesta con los tokens gastados y el modelo que respondió
     * @throws LlmUnavailableException si el proveedor falla, no responde a tiempo o responde vacío
     * @throws LlmLimitReachedException si el proveedor rechaza la llamada por cuota agotada
     */
    LanguageModelResponse generate(LanguageModelPrompt prompt) {
        long inicio = System.nanoTime();
        String resultado = RESULTADO_OK;
        try {
            return aRespuesta(llamar(prompt));
        } catch (BusinessException fallo) {
            resultado = fallo.getErrorCode().name();
            throw fallo;
        } finally {
            logger.info("Llamada al modelo [proveedor=groq, operacion={}, resultado={}, duracionMs={}]",
                    prompt.operation().code(), resultado,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
        }
    }

    /** Hace la llamada y traduce todo fallo a la excepción de negocio que corresponde. */
    private ChatResponse llamar(LanguageModelPrompt prompt) {
        try {
            ChatModel chatModel = prompt.operation() == AiOperation.PLAN ? modelos.planning() : modelos.evaluation();
            if (chatModel == null) {
                throw new IllegalStateException("Respaldo no disponible");
            }
            return chatModel.call(new Prompt(prompt.text(), opciones(prompt)));
        } catch (RuntimeException fallo) {
            throw traducir(fallo);
        }
    }

    private static BusinessException traducir(RuntimeException fallo) {
        Throwable causa = fallo;
        for (int nivel = 0; causa != null && nivel < MAX_CAUSAS; nivel++, causa = causa.getCause()) {
            if (causa instanceof OpenAIServiceException servicio && servicio.statusCode() == CUOTA_AGOTADA) {
                return new LlmLimitReachedException(fallo);
            }
        }
        return new LlmUnavailableException(fallo);
    }

    private OpenAiChatOptions opciones(LanguageModelPrompt prompt) {
        OpenAiChatOptions.Builder opciones = OpenAiChatOptions.builder()
                .model(respaldo.model())
                .maxTokens(prompt.maxOutputTokens())
                .temperature(propiedades.temperature())
                .reasoningEffort("none");
        if (prompt.jsonSchema() != null) {
            // Con esquema, Groq devuelve JSON que lo cumple de forma estricta en lugar de texto libre.
            opciones.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(prompt.jsonSchema())
                    .strict(true)
                    .build());
        }
        return opciones.build();
    }

    private LanguageModelResponse aRespuesta(ChatResponse respuesta) {
        Generation generacion = respuesta == null ? null : respuesta.getResult();
        String texto = generacion == null ? null : generacion.getOutput().getText();
        if (texto == null || texto.isBlank()) {
            // Una respuesta vacía nunca es un valor válido; la causa no lleva texto del modelo.
            throw new LlmUnavailableException(new IllegalStateException("El respaldo devolvió una respuesta sin texto"));
        }
        ChatResponseMetadata metadatos = respuesta.getMetadata();
        Usage uso = metadatos.getUsage();
        String modelo = metadatos.getModel() == null || metadatos.getModel().isBlank()
                ? respaldo.model()
                : metadatos.getModel();
        return new LanguageModelResponse(texto, tokens(uso.getPromptTokens()), tokens(uso.getCompletionTokens()),
                modelo);
    }

    private static int tokens(Integer informados) {
        return informados == null ? 0 : informados;
    }
}
