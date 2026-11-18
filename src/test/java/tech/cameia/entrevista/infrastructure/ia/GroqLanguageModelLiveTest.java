package tech.cameia.entrevista.infrastructure.ia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.infrastructure.config.ia.FallbackModelProperties;
import tech.cameia.entrevista.support.WithLanguageModelProperties;

/**
 * Llamada real al proveedor de respaldo (Qwen en Groq) con la llave del ambiente: comprueba contra la API
 * verdadera que el respaldo responde, que acepta la respuesta {@code json_schema} estricta y que el modelo
 * configurado existe.
 *
 * <p>Es manual y nunca corre en la integración continua: solo se ejecuta con {@code LLM_LIVE_TEST=true}.
 * Toma {@code GROQ_API_KEY}, {@code LLM_FALLBACK_MODEL} y {@code LLM_FALLBACK_BASE_URL} del entorno o del
 * {@code .env}. No necesita base de datos: arma solo la configuración y los adaptadores de IA. Usa prompts fijos, cortos y ficticios, sin datos de ninguna persona, y un tope bajo de tokens.
 * No imprime la llave ni el texto recibido, solo el modelo, los tokens y la longitud del texto.</p>
 *
 * <p>Ejecución en PowerShell:
 * {@code $env:LLM_LIVE_TEST="true"; ./mvnw.cmd test "-Dtest=GroqLanguageModelLiveTest"}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = GroqLanguageModelLiveTest.SoloIa.class)
@WithLanguageModelProperties
@EnabledIfEnvironmentVariable(named = "LLM_LIVE_TEST", matches = "true")
class GroqLanguageModelLiveTest {

    private static final String PROMPT_LIBRE = "Responde OK y luego una frase tuya de máximo seis palabras.";

    private static final String PROMPT_CON_ESQUEMA =
            "Devuelve ok en verdadero y en frase una frase tuya de máximo seis palabras.";

    /** Esquema mínimo y estricto: dos campos obligatorios y ninguno más. */
    private static final String ESQUEMA = """
            {"type":"object","properties":{"ok":{"type":"boolean"},"frase":{"type":"string"}},\
            "required":["ok","frase"],"additionalProperties":false}""";

    private static final int TOPE_DE_TOKENS = 200;

    /** Contexto mínimo: solo la configuración y los adaptadores de IA, sin base de datos ni mensajería. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {"tech.cameia.entrevista.infrastructure.ia",
            "tech.cameia.entrevista.infrastructure.config.ia"})
    @ImportAutoConfiguration({ToolCallingAutoConfiguration.class, SpringAiRetryAutoConfiguration.class})
    static class SoloIa {
    }

    private final JsonMapper mapeador = JsonMapper.builder().build();

    @Autowired
    private LanguageModelPort modelo;

    @Autowired
    private FallbackModelProperties respaldo;

    @Test
    @DisplayName("El respaldo real devuelve contenido no vacío al planificar, con texto libre")
    void generate_shouldReturnNonBlankText_whenFallbackCalledWithRealKey() {
        exigirLlave();

        LanguageModelResponse respuesta = modelo.generate(
                new LanguageModelPrompt(PROMPT_LIBRE, TOPE_DE_TOKENS, AiOperation.PLAN), AiProvider.FALLBACK);

        // Sin el texto: solo lo necesario para anotar el resultado en el PR.
        System.out.println("Prueba real del respaldo (texto libre): " + respuesta);
        assertThat(respuesta.text()).isNotBlank();
        assertThat(respuesta.outputTokens()).isPositive();
    }

    @Test
    @DisplayName("El respaldo real cumple el esquema JSON estricto al evaluar")
    void generate_shouldReturnJsonMatchingSchema_whenStrictSchemaSent() {
        exigirLlave();

        LanguageModelResponse respuesta = modelo.generate(
                new LanguageModelPrompt(PROMPT_CON_ESQUEMA, TOPE_DE_TOKENS, AiOperation.EVALUATE)
                        .withSchema(ESQUEMA),
                AiProvider.FALLBACK);

        System.out.println("Prueba real del respaldo (esquema): " + respuesta);
        JsonNode json = mapeador.readTree(respuesta.text());
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("ok", "frase");
        assertThat(json.get("ok").isBoolean()).isTrue();
        assertThat(json.get("frase").asString()).isNotBlank();
    }

    /** Sin llave real el servicio arranca pero el respaldo no existe: la prueba falla con una causa clara. */
    private void exigirLlave() {
        assertThat(respaldo.hasKey())
                .as("Define GROQ_API_KEY con una llave real de Groq: sin ella el respaldo no está disponible")
                .isTrue();
    }
}
