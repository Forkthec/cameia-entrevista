package tech.cameia.entrevista.spike.planificacion;

import java.util.Map;
import java.util.Set;

import com.google.genai.errors.ApiException;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;

/**
 * Gemini 3.5 Flash Lite llamado directamente por el modelo de chat de Spring AI, uniendo el texto de
 * todas las partes de la respuesta ("ajuste propuesto").
 *
 * <p>El puerto del servicio entrega solo la primera parte; cuando Gemini reparte el JSON en dos partes,
 * el texto llega cortado. Esta clase mide qué pasaría si el adaptador uniera las partes. Código del
 * spike, no de producción.</p>
 */
final class GeminiLiteUnido implements ModeloDePrueba {

    private final ChatModel chat;

    private final String modelo;

    private final String llave;

    private final double temperatura;

    private final GoogleGenAiThinkingLevel pensamiento;

    GeminiLiteUnido(ChatModel chat, String modelo, String llave, double temperatura, String pensamiento) {
        this.chat = chat;
        this.modelo = modelo;
        this.llave = llave;
        this.temperatura = temperatura;
        this.pensamiento = GoogleGenAiThinkingLevel.valueOf(pensamiento.toUpperCase());
    }

    @Override
    public String alias() {
        return "GEMINI_LITE";
    }

    @Override
    public String nombreDelModelo() {
        return modelo;
    }

    @Override
    public Respuesta generar(String prompt, int maxTokensSalida) {
        try {
            ChatResponse r = chat.call(new Prompt(prompt, GoogleGenAiChatOptions.builder().model(modelo)
                    .temperature(temperatura).thinkingLevel(pensamiento).maxOutputTokens(maxTokensSalida).build()));
            StringBuilder texto = new StringBuilder();
            r.getResults().forEach(g -> texto.append(g.getOutput().getText() == null ? "" : g.getOutput().getText()));
            String motivo = r.getResult().getMetadata().getFinishReason();
            return new Respuesta(texto.toString(), r.getMetadata().getUsage().getPromptTokens(),
                    r.getMetadata().getUsage().getCompletionTokens(), r.getMetadata().getModel(), Map.of(),
                    "partes=" + r.getResults().size() + "; motivo=" + motivo);
        } catch (RuntimeException fallo) {
            Throwable raiz = fallo;
            boolean cuota = false;
            while (raiz != null) {
                cuota |= raiz instanceof ApiException api && api.code() == 429;
                if (raiz.getCause() == null || raiz.getCause() == raiz) {
                    break;
                }
                raiz = raiz.getCause();
            }
            String causa = Secretos.enmascarar(raiz.getClass().getSimpleName() + ": " + raiz.getMessage(), llave);
            causa = causa.length() > 1500 ? causa.substring(0, 1500) : causa;
            TipoDeFallo tipo = cuota ? (causa.contains("PerDay") ? TipoDeFallo.LIMITE_POR_DIA : TipoDeFallo.LIMITE_POR_MINUTO)
                    : TipoDeFallo.SOBRECARGA;
            throw new FalloDelProveedor(tipo, causa, -1, Map.of());
        }
    }

    /** Valores de nivel de pensamiento aceptados por el banco. */
    static Set<String> niveles() {
        return Set.of("MINIMAL", "LOW", "MEDIUM", "HIGH");
    }
}
