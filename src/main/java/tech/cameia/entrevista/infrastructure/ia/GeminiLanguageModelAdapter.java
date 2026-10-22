package tech.cameia.entrevista.infrastructure.ia;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

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
     */
    @Override
    public LanguageModelResponse generate(LanguageModelPrompt prompt) {
        long inicio = System.nanoTime();
        String resultado = RESULTADO_OK;
        try {
            ChatResponse respuesta = chatModel.call(new Prompt(prompt.text(), opciones(prompt)));
            return aRespuesta(respuesta);
        } finally {
            logger.info("Llamada al modelo [resultado={}, duracionMs={}]", resultado,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
        }
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
        String texto = respuesta.getResult().getOutput().getText();
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
