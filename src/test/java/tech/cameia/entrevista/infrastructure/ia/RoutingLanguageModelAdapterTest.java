package tech.cameia.entrevista.infrastructure.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;

/** Enrutamiento de cada llamada al proveedor que pide el ciclo de intentos. */
class RoutingLanguageModelAdapterTest {

    private static final LanguageModelPrompt PROMPT = new LanguageModelPrompt("Evalúa.", 16, AiOperation.EVALUATE);
    private static final LanguageModelResponse GEMINI = new LanguageModelResponse("g", 1, 1, "gemini");
    private static final LanguageModelResponse GROQ = new LanguageModelResponse("q", 1, 1, "groq");

    private final GeminiLanguageModelAdapter gemini = mock(GeminiLanguageModelAdapter.class);
    private final GroqLanguageModelAdapter groq = mock(GroqLanguageModelAdapter.class);
    private final RoutingLanguageModelAdapter router = new RoutingLanguageModelAdapter(gemini, groq);

    @Test
    @DisplayName("El proveedor principal llama a Gemini")
    void generate_shouldCallGemini_forPrimary() {
        when(gemini.generate(PROMPT)).thenReturn(GEMINI);

        assertThat(router.generate(PROMPT, AiProvider.PRIMARY)).isSameAs(GEMINI);
        verifyNoInteractions(groq);
    }

    @Test
    @DisplayName("El respaldo llama a Groq cuando hay llave")
    void generate_shouldCallGroq_forFallback() {
        when(groq.isAvailable()).thenReturn(true);
        when(groq.generate(PROMPT)).thenReturn(GROQ);

        assertThat(router.generate(PROMPT, AiProvider.FALLBACK)).isSameAs(GROQ);
        verify(groq).generate(PROMPT);
        verifyNoInteractions(gemini);
    }

    @Test
    @DisplayName("Pedir el respaldo sin llave es un error de programación")
    void generate_shouldFail_forFallbackWithoutKey() {
        when(groq.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> router.generate(PROMPT, AiProvider.FALLBACK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Respaldo no disponible");
    }

    @Test
    @DisplayName("El principal siempre está disponible y el respaldo solo con llave")
    void isAvailable_shouldFollowKey() {
        when(groq.isAvailable()).thenReturn(false, true);

        assertThat(router.isAvailable(AiProvider.PRIMARY)).isTrue();
        assertThat(router.isAvailable(AiProvider.FALLBACK)).isFalse();
        assertThat(router.isAvailable(AiProvider.FALLBACK)).isTrue();
    }
}
