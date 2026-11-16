package tech.cameia.entrevista.infrastructure.config.voz;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la voz de las preguntas (HU-5.15), con prefijo {@code cameia.speech}. La llave es la de Gemini
 * ({@code GOOGLE_GENAI_API_KEY}), compartida con el modelo de lenguaje.
 *
 * @param enabled interruptor de la función ({@code SPEECH_ENABLED})
 * @param baseUrl URL base de la API de Gemini ({@code SPEECH_BASE_URL})
 * @param model modelo de voz principal ({@code SPEECH_MODEL})
 * @param fallbackModel modelo de voz de respaldo ante 429 o 5xx ({@code SPEECH_FALLBACK_MODEL}); vacío, sin respaldo
 * @param voiceEs voz de las entrevistas en español ({@code SPEECH_VOICE_ES})
 * @param voiceEn voz de las entrevistas en inglés ({@code SPEECH_VOICE_EN})
 * @param timeout tiempo máximo de la generación, respaldo incluido ({@code SPEECH_TIMEOUT})
 * @param maxPlays usos de audio por pregunta ({@code SPEECH_MAX_PLAYS})
 * @param maxConcurrent síntesis simultáneas por instancia ({@code SPEECH_MAX_CONCURRENT})
 * @param monthlyCharacterCap tope de caracteres por mes UTC ({@code SPEECH_MONTHLY_CHARACTER_CAP})
 * @param director modelo que coloca las etiquetas de voz
 */
@Validated
@ConfigurationProperties("cameia.speech")
public record SpeechProperties(
        boolean enabled,
        @NotNull(message = "cameia.speech.base-url (SPEECH_BASE_URL) es obligatoria") URI baseUrl,
        @NotBlank(message = "cameia.speech.model (SPEECH_MODEL) es obligatorio") String model,
        String fallbackModel,
        @NotBlank(message = "cameia.speech.voice-es (SPEECH_VOICE_ES) es obligatoria") String voiceEs,
        @NotBlank(message = "cameia.speech.voice-en (SPEECH_VOICE_EN) es obligatoria") String voiceEn,
        @NotNull(message = "cameia.speech.timeout (SPEECH_TIMEOUT) es obligatorio") Duration timeout,
        @Min(value = 1, message = "cameia.speech.max-plays (SPEECH_MAX_PLAYS) debe estar entre 1 y 10")
        @Max(value = 10, message = "cameia.speech.max-plays (SPEECH_MAX_PLAYS) debe estar entre 1 y 10") int maxPlays,
        @Min(value = 1, message = "cameia.speech.max-concurrent (SPEECH_MAX_CONCURRENT) debe ser al menos 1")
        int maxConcurrent,
        @Min(value = 1, message = "cameia.speech.monthly-character-cap debe ser al menos 1") long monthlyCharacterCap,
        @Valid @NotNull(message = "cameia.speech.director es obligatorio") Director director) {

    /**
     * Modelo de texto que coloca las etiquetas de voz.
     *
     * @param model modelo, rápido ({@code SPEECH_DIRECTOR_MODEL})
     * @param timeout tiempo máximo; si se agota, la pregunta se lee tal cual ({@code SPEECH_DIRECTOR_TIMEOUT})
     * @param thinkingLevel nivel de pensamiento ({@code SPEECH_DIRECTOR_THINKING_LEVEL}); vacío, el del modelo
     */
    public record Director(
            @NotBlank(message = "cameia.speech.director.model (SPEECH_DIRECTOR_MODEL) es obligatorio") String model,
            @NotNull(message = "cameia.speech.director.timeout (SPEECH_DIRECTOR_TIMEOUT) es obligatorio")
            Duration timeout,
            String thinkingLevel) {

        /** @return {@code true} si el tiempo falta o es positivo */
        @AssertTrue(message = "cameia.speech.director.timeout (SPEECH_DIRECTOR_TIMEOUT) debe ser mayor que cero")
        public boolean isTimeoutPositive() {
            return timeout == null || timeout.isPositive();
        }
    }

    /** @return {@code true} si el tiempo falta o es positivo */
    @AssertTrue(message = "cameia.speech.timeout (SPEECH_TIMEOUT) debe ser mayor que cero")
    public boolean isTimeoutPositive() {
        return timeout == null || timeout.isPositive();
    }
}
