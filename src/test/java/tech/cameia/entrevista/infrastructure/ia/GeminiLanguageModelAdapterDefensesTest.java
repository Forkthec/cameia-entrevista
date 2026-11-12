package tech.cameia.entrevista.infrastructure.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.google.genai.errors.ApiException;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;

import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;

/**
 * Comprobaciones defensivas del adaptador de Gemini con un modelo de chat doble: respuestas
 * nulas o sin candidatos, partes sin texto, datos de uso ausentes y cadenas de causas con la
 * cuota agotada en distintos niveles o con un ciclo. Complementa la prueba contra el servidor
 * simulado, que no puede provocar estos casos porque el SDK nunca los entrega.
 */
class GeminiLanguageModelAdapterDefensesTest {

    private static final LanguageModelPrompt PROMPT = new LanguageModelPrompt("Responde OK.", 16, AiOperation.PLAN);

    private final ChatModel chatModel = mock(ChatModel.class);

    private final GeminiLanguageModelAdapter adapter = new GeminiLanguageModelAdapter(chatModel,
            new LanguageModelProperties("clave-falsa", "modelo-configurado", Duration.ofSeconds(5), 1.0,
                    GoogleGenAiThinkingLevel.MINIMAL));

    private static Generation parte(String texto) {
        return new Generation(new AssistantMessage(texto));
    }

    private static ChatResponse respuesta(String modelo, DefaultUsage uso, Generation... partes) {
        return ChatResponse.builder().generations(List.of(partes))
                .metadata(ChatResponseMetadata.builder().model(modelo).usage(uso).build()).build();
    }

    @Test
    void generate_shouldThrowUnavailable_whenModelReturnsNull() {
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        Throwable fallo = catchThrowable(() -> adapter.generate(PROMPT, AiProvider.PRIMARY));

        assertThat(fallo).isInstanceOf(LlmUnavailableException.class);
        assertThat(fallo.getCause()).hasMessageContaining("sin respuesta");
    }

    @Test
    void generate_shouldDescribeMissingCandidates_whenResponseHasNoGenerations() {
        when(chatModel.call(any(Prompt.class))).thenReturn(respuesta("m", new DefaultUsage(5, 0)));

        Throwable fallo = catchThrowable(() -> adapter.generate(PROMPT, AiProvider.PRIMARY));

        assertThat(fallo).isInstanceOf(LlmUnavailableException.class);
        assertThat(fallo.getCause()).hasMessageContaining("finishReason=sin candidatos");
    }

    @Test
    void generate_shouldSkipPartsWithoutText_andJoinTheRest() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                respuesta("m", new DefaultUsage(5, 2), parte("Hola "), parte(null), parte("mundo")));

        LanguageModelResponse resultado = adapter.generate(PROMPT, AiProvider.PRIMARY);

        assertThat(resultado.text()).isEqualTo("Hola mundo");
    }

    @Test
    void generate_shouldUseConfiguredModelAndZeroTokens_whenProviderReportsNone() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                respuesta("  ", new DefaultUsage(null, null), parte("OK")));

        LanguageModelResponse resultado = adapter.generate(PROMPT, AiProvider.PRIMARY);

        assertThat(resultado.model()).isEqualTo("modelo-configurado");
        assertThat(resultado.inputTokens()).isZero();
        assertThat(resultado.outputTokens()).isZero();
    }

    @Test
    void generate_shouldUseConfiguredModel_whenModelIsNull() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                respuesta(null, new DefaultUsage(3, 4), parte("OK")));

        assertThat(adapter.generate(PROMPT, AiProvider.PRIMARY).model()).isEqualTo("modelo-configurado");
    }

    @Test
    void generate_shouldThrowLimitReached_whenQuotaErrorIsDeepInTheCauseChain() {
        ApiException cuota = new ApiException(429, "RESOURCE_EXHAUSTED", "cuota");
        when(chatModel.call(any(Prompt.class))).thenThrow(
                new IllegalStateException("envoltorio", new RuntimeException("medio", cuota)));

        assertThat(catchThrowable(() -> adapter.generate(PROMPT, AiProvider.PRIMARY))).isInstanceOf(LlmLimitReachedException.class);
    }

    @Test
    void generate_shouldThrowUnavailable_whenCauseChainHasOtherProviderError() {
        ApiException otro = new ApiException(500, "INTERNAL", "falla");
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("envoltorio", otro));

        assertThat(catchThrowable(() -> adapter.generate(PROMPT, AiProvider.PRIMARY))).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void generate_shouldStopFollowingCauses_whenChainIsLongerThanTheLimit() {
        Throwable cadena = new ApiException(429, "RESOURCE_EXHAUSTED", "demasiado hondo");
        for (int nivel = 0; nivel < 40; nivel++) {
            cadena = new RuntimeException("nivel " + nivel, cadena);
        }
        RuntimeException tope = (RuntimeException) cadena;
        when(chatModel.call(any(Prompt.class))).thenThrow(tope);

        assertThat(catchThrowable(() -> adapter.generate(PROMPT, AiProvider.PRIMARY))).isInstanceOf(LlmUnavailableException.class);
    }
}
