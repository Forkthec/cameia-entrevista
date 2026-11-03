package tech.cameia.entrevista.infrastructure.ia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;

/**
 * Llamada real a Gemini con la llave del ambiente: el criterio 1 de CM-312 («una llamada de
 * prueba devuelve contenido no vacío»).
 *
 * <p>Es manual y nunca corre en la integración continua: solo se ejecuta con
 * {@code LLM_LIVE_TEST=true}. Usa la configuración real del servicio, así que toma
 * {@code GOOGLE_GENAI_API_KEY} y {@code LLM_MODEL} del entorno o del {@code .env}. Gasta muy
 * pocos tokens: un prompt fijo y corto y un tope de 64 tokens de salida, que incluye el pensamiento del modelo. No imprime la llave
 * ni el texto recibido, solo el modelo, los tokens y la longitud del texto.</p>
 *
 * <p>Ejecución en PowerShell:
 * {@code $env:LLM_LIVE_TEST="true"; ./mvnw.cmd test "-Dtest=GeminiLanguageModelLiveTest"}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "LLM_LIVE_TEST", matches = "true")
class GeminiLanguageModelLiveTest {

    /** Llave falsa del perfil {@code local}: con ella la llamada real no puede funcionar. */
    private static final String LLAVE_FALSA_LOCAL = "local-fake-key";

    private static final String PROMPT = "Responde OK y luego una frase tuya de máximo seis palabras.";

    private static final int TOPE_DE_TOKENS = 64;

    @Autowired
    private LanguageModelPort modelo;

    @Autowired
    private LanguageModelProperties propiedades;

    @Test
    @DisplayName("Una llamada real al modelo devuelve contenido no vacío")
    void generate_shouldReturnNonBlankText_whenCalledWithRealKey() {
        assertThat(propiedades.apiKey())
                .as("Define GOOGLE_GENAI_API_KEY con una llave real: el perfil local trae una falsa")
                .isNotEqualTo(LLAVE_FALSA_LOCAL);

        LanguageModelResponse respuesta = modelo.generate(new LanguageModelPrompt(PROMPT, TOPE_DE_TOKENS));

        // Sin el texto: solo lo necesario para anotar el resultado en el PR.
        System.out.println("Prueba real: " + respuesta);
        assertThat(respuesta.text()).isNotBlank();
        assertThat(respuesta.outputTokens()).isPositive();
    }
}
