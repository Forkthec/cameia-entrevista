package tech.cameia.entrevista.infrastructure.config.ia;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Proveedor de respaldo de la IA (Qwen en Groq), enlazado al prefijo {@code cameia.llm.fallback} y validado al
 * arrancar.
 *
 * <p>La llave puede estar vacía: sin ella el respaldo no está disponible y el tercer intento de cada ciclo usa el
 * proveedor principal. {@link #toString()} la muestra enmascarada.</p>
 *
 * @param apiKey llave del proveedor; llega por {@code GROQ_API_KEY}; puede estar vacía
 * @param model identificador del modelo; llega por {@code LLM_FALLBACK_MODEL}
 * @param baseUrl dirección del proveedor, {@code https} o {@code http://localhost}; llega por
 *        {@code LLM_FALLBACK_BASE_URL}
 */
@Validated
@ConfigurationProperties("cameia.llm.fallback")
public record FallbackModelProperties(
        String apiKey,
        @NotBlank(message = "cameia.llm.fallback.model (LLM_FALLBACK_MODEL) es obligatorio") String model,
        @NotBlank(message = "cameia.llm.fallback.base-url (LLM_FALLBACK_BASE_URL) es obligatoria") String baseUrl) {

    /**
     * Indica si la dirección es segura. Una dirección vacía la reporta {@code @NotBlank}.
     *
     * @return {@code true} si la dirección falta, empieza por {@code https://} o es {@code http://localhost}
     */
    @AssertTrue(message = "cameia.llm.fallback.base-url (LLM_FALLBACK_BASE_URL) debe empezar por https:// "
            + "o ser http://localhost")
    public boolean isBaseUrlSecure() {
        return baseUrl == null || baseUrl.isBlank() || baseUrl.startsWith("https://")
                || baseUrl.equals("http://localhost") || baseUrl.startsWith("http://localhost:")
                || baseUrl.startsWith("http://localhost/");
    }

    /**
     * Indica si la dirección está sin la versión de la API: el cliente la agrega, y una dirección que ya la trae
     * produciría {@code /v1/v1} y un 404 en cada intento de respaldo.
     *
     * @return {@code true} si la dirección falta o no termina en {@code /v1}
     */
    @AssertTrue(message = "cameia.llm.fallback.base-url (LLM_FALLBACK_BASE_URL) no debe terminar en /v1: "
            + "la versión la agrega el servicio")
    public boolean isBaseUrlWithoutVersion() {
        return baseUrl == null || !baseUrl.replaceAll("/+$", "").endsWith("/v1");
    }

    /**
     * Indica si hay llave del respaldo.
     *
     * @return {@code true} si la llave no está vacía
     */
    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Dirección a la que el cliente de OpenAI envía las llamadas. El cliente agrega solo {@code /chat/completions},
     * y la API compatible de Groq cuelga sus rutas de {@code <dirección>/v1}; por eso se agrega la versión aquí y
     * la propiedad conserva la dirección base que se documenta.
     *
     * @return la dirección base sin barras finales y con {@code /v1}
     */
    public String apiBaseUrl() {
        return baseUrl.replaceAll("/+$", "") + "/v1";
    }

    /**
     * Representación con la llave enmascarada.
     *
     * @return los valores de la configuración, con {@code apiKey=****}
     */
    @Override
    public String toString() {
        return "FallbackModelProperties[apiKey=****, model=" + model + ", baseUrl=" + baseUrl + "]";
    }
}
