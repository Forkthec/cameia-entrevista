package tech.cameia.entrevista.infrastructure.config.ia;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del modelo de lenguaje, enlazada al prefijo {@code cameia.llm} y validada al
 * arrancar: si un valor falta o no cumple su regla, el servicio no arranca.
 *
 * <p>Cada mensaje de validación nombra la propiedad y, cuando la hay, la variable de entorno que
 * la alimenta. Ninguno repite el valor de la llave, y {@link #toString()} la muestra enmascarada,
 * porque Spring usa esa representación en los mensajes de un arranque fallido.</p>
 *
 * @param apiKey llave del proveedor; llega por {@code GOOGLE_GENAI_API_KEY}
 * @param model identificador del modelo en la API del proveedor; llega por {@code LLM_MODEL}
 * @param timeout tiempo máximo de espera de cada llamada; llega por {@code LLM_TIMEOUT}
 * @param temperature temperatura del modelo, entre {@code 0.0} y {@code 2.0}
 * @param thinkingLevel nivel de pensamiento del modelo
 */
@Validated
@ConfigurationProperties("cameia.llm")
public record LanguageModelProperties(
        @NotBlank(message = "cameia.llm.api-key (GOOGLE_GENAI_API_KEY) es obligatoria") String apiKey,
        @NotBlank(message = "cameia.llm.model (LLM_MODEL) es obligatorio") String model,
        @NotNull(message = "cameia.llm.timeout (LLM_TIMEOUT) es obligatorio") Duration timeout,
        @NotNull(message = "cameia.llm.temperature es obligatoria")
        @DecimalMin(value = "0.0", message = "cameia.llm.temperature debe estar entre 0.0 y 2.0")
        @DecimalMax(value = "2.0", message = "cameia.llm.temperature debe estar entre 0.0 y 2.0") Double temperature,
        @NotNull(message = "cameia.llm.thinking-level es obligatorio") GoogleGenAiThinkingLevel thinkingLevel) {

    /**
     * Indica si el tiempo de espera es mayor que cero. Un valor nulo lo reporta {@code @NotNull}.
     *
     * @return {@code true} si el tiempo de espera falta o es positivo
     */
    @AssertTrue(message = "cameia.llm.timeout (LLM_TIMEOUT) debe ser mayor que cero")
    public boolean isTimeoutPositive() {
        return timeout == null || timeout.isPositive();
    }

    /**
     * Representación con la llave enmascarada.
     *
     * @return los valores de la configuración, con {@code apiKey=****}
     */
    @Override
    public String toString() {
        return "LanguageModelProperties[apiKey=****, model=" + model + ", timeout=" + timeout
                + ", temperature=" + temperature + ", thinkingLevel=" + thinkingLevel + "]";
    }
}
