package tech.cameia.entrevista.spike.planificacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Diagnóstico manual (solo con {@code SPIKE_DIAGNOSTICO=true}): llama al modelo de chat de Gemini
 * directamente y muestra cuántas partes trae la respuesta y cómo termina. Sirve para saber por qué
 * el texto del puerto a veces llega cortado. No imprime la llave ni el texto completo del modelo.
 */
@EnabledIfEnvironmentVariable(named = "SPIKE_DIAGNOSTICO", matches = "true")
class GeminiDiagnostico {

    @Test
    void diagnosticar() {
        String llave = System.getenv("GOOGLE_GENAI_API_KEY");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, GoogleGenAiChatAutoConfiguration.class))
                .withPropertyValues("spring.ai.google.genai.api-key=" + llave,
                        "spring.ai.google.genai.chat.options.model=" + System.getenv("LLM_MODEL"))
                .run(ctx -> {
                    ChatModel modelo = ctx.getBean(ChatModel.class);
                    String prompt = "Responde SOLO con este JSON, sin texto alrededor y con 5 preguntas de entrevista para un "
                            + "desarrollador backend en español: {\"preguntas\":[{\"posicion\":1,\"texto\":\"...\"}]}";
                    for (int i = 1; i <= Integer.parseInt(System.getenv().getOrDefault("SPIKE_N", "6")); i++) {
                        ChatResponse r = modelo.call(new Prompt(prompt, GoogleGenAiChatOptions.builder()
                                .model(System.getenv("LLM_MODEL")).temperature(1.0).thinkingLevel(org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel.LOW)
                                .maxOutputTokens(4096).build()));
                        StringBuilder partes = new StringBuilder();
                        r.getResults().forEach(g -> partes.append(g.getOutput().getText() == null ? 0
                                : g.getOutput().getText().length()).append("/")
                                .append(g.getMetadata().getFinishReason()).append(" "));
                        String t = r.getResult().getOutput().getText();
                        System.out.println("DIAG " + i + " generaciones=" + r.getResults().size() + " largos/motivo=" + partes
                                + " termina=" + t.substring(Math.max(0, t.length() - 15)).replace("\n", "\n")
                                + " tokensSalida=" + r.getMetadata().getUsage().getCompletionTokens());
                        Thread.sleep(3000);
                    }
                });
    }
}
