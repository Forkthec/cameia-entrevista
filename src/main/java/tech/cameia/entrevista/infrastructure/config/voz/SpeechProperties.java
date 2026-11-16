package tech.cameia.entrevista.infrastructure.config.voz;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la voz de las preguntas (HU-5.15) con Google Cloud Text-to-Speech, con prefijo
 * {@code cameia.speech}.
 *
 * @param enabled interruptor de la función ({@code SPEECH_ENABLED})
 * @param baseUrl URL base de la API de Text-to-Speech ({@code SPEECH_BASE_URL})
 * @param apiKey llave de API de Text-to-Speech ({@code SPEECH_API_KEY}); en blanco se usan las credenciales por
 *        defecto de la aplicación (la cuenta de servicio en Cloud Run)
 * @param voiceEs voz Chirp 3 HD de las entrevistas en español ({@code SPEECH_VOICE_ES})
 * @param voiceEn voz Chirp 3 HD de las entrevistas en inglés ({@code SPEECH_VOICE_EN})
 * @param timeout tiempo máximo de la generación ({@code SPEECH_TIMEOUT})
 * @param maxPlays usos de audio por pregunta ({@code SPEECH_MAX_PLAYS})
 * @param maxConcurrent síntesis simultáneas por instancia ({@code SPEECH_MAX_CONCURRENT})
 * @param monthlyCharacterCap tope de caracteres por mes UTC ({@code SPEECH_MONTHLY_CHARACTER_CAP})
 */
@Validated
@ConfigurationProperties("cameia.speech")
public record SpeechProperties(
        boolean enabled,
        @NotNull(message = "cameia.speech.base-url (SPEECH_BASE_URL) es obligatoria") URI baseUrl,
        String apiKey,
        @NotNull(message = "cameia.speech.voice-es (SPEECH_VOICE_ES) es obligatoria")
        @Pattern(regexp = VOICE, message = "cameia.speech.voice-es (SPEECH_VOICE_ES) debe tener la forma"
                + " idioma-PAIS-modelo-nombre, por ejemplo es-US-Chirp3-HD-Kore") String voiceEs,
        @NotNull(message = "cameia.speech.voice-en (SPEECH_VOICE_EN) es obligatoria")
        @Pattern(regexp = VOICE, message = "cameia.speech.voice-en (SPEECH_VOICE_EN) debe tener la forma"
                + " idioma-PAIS-modelo-nombre, por ejemplo en-US-Chirp3-HD-Kore") String voiceEn,
        @NotNull(message = "cameia.speech.timeout (SPEECH_TIMEOUT) es obligatorio") Duration timeout,
        @Min(value = 1, message = "cameia.speech.max-plays (SPEECH_MAX_PLAYS) debe estar entre 1 y 10")
        @Max(value = 10, message = "cameia.speech.max-plays (SPEECH_MAX_PLAYS) debe estar entre 1 y 10") int maxPlays,
        @Min(value = 1, message = "cameia.speech.max-concurrent (SPEECH_MAX_CONCURRENT) debe ser al menos 1")
        int maxConcurrent,
        @Min(value = 1, message = "cameia.speech.monthly-character-cap debe ser al menos 1")
        long monthlyCharacterCap) {

    /** Forma del nombre de una voz: idioma, país y el resto ({@code es-US-Chirp3-HD-Kore}). */
    static final String VOICE = "[a-z]{2,3}-[A-Z]{2}-[A-Za-z0-9-]+";

    /** @return {@code true} si se usará una llave de API en lugar de las credenciales por defecto */
    public boolean usesApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** @return {@code true} si el tiempo falta o es positivo */
    @AssertTrue(message = "cameia.speech.timeout (SPEECH_TIMEOUT) debe ser mayor que cero")
    public boolean isTimeoutPositive() {
        return timeout == null || timeout.isPositive();
    }

    /** No imprime la llave. */
    @Override
    public String toString() {
        return "SpeechProperties[enabled=" + enabled + ", baseUrl=" + baseUrl + ", apiKey="
                + (usesApiKey() ? "****" : "ADC") + ", voiceEs=" + voiceEs + ", voiceEn=" + voiceEn + ", timeout="
                + timeout + ", maxPlays=" + maxPlays + ", maxConcurrent=" + maxConcurrent + ", monthlyCharacterCap="
                + monthlyCharacterCap + "]";
    }
}
