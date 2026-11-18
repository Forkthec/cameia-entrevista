package tech.cameia.entrevista.infrastructure.ia;

import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.port.LanguageModelPort;

/**
 * Implementación única de {@link LanguageModelPort}: envía cada llamada al proveedor que pide el ciclo de intentos,
 * Gemini como principal y Qwen en Groq como respaldo.
 */
@Component
class RoutingLanguageModelAdapter implements LanguageModelPort {

    private final GeminiLanguageModelAdapter gemini;

    private final GroqLanguageModelAdapter groq;

    /**
     * Crea el enrutador.
     *
     * @param gemini proveedor principal
     * @param groq proveedor de respaldo
     */
    RoutingLanguageModelAdapter(GeminiLanguageModelAdapter gemini, GroqLanguageModelAdapter groq) {
        this.gemini = gemini;
        this.groq = groq;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pedir el respaldo sin llave es un error de programación: la política de intentos nunca lo elige si
     * {@link #isAvailable(AiProvider)} responde que no.</p>
     */
    @Override
    public LanguageModelResponse generate(LanguageModelPrompt prompt, AiProvider provider) {
        if (provider == AiProvider.PRIMARY) {
            return gemini.generate(prompt);
        }
        if (!groq.isAvailable()) {
            throw new IllegalStateException("Respaldo no disponible");
        }
        return groq.generate(prompt);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAvailable(AiProvider provider) {
        return provider == AiProvider.PRIMARY || groq.isAvailable();
    }
}
