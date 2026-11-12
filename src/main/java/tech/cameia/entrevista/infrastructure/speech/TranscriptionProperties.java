package tech.cameia.entrevista.infrastructure.speech;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la transcripción de las respuestas por voz, enlazada al prefijo {@code cameia.transcription} y
 * validada al arrancar. La llave es la del modelo de lenguaje ({@code GOOGLE_GENAI_API_KEY}): la transcripción usa el
 * mismo cliente de Gemini.
 *
 * @param model modelo de Gemini que transcribe; llega por {@code TRANSCRIPTION_MODEL} y, si falta, es {@code LLM_MODEL}
 * @param timeout tiempo máximo de cada transcripción; llega por {@code TRANSCRIPTION_TIMEOUT}. Debe quedar por debajo
 *                de los 120 s del Gateway y de los 130 s de la interfaz
 */
@Validated
@ConfigurationProperties("cameia.transcription")
public record TranscriptionProperties(
        @NotBlank(message = "cameia.transcription.model (TRANSCRIPTION_MODEL o LLM_MODEL) es obligatorio") String model,
        @NotNull(message = "cameia.transcription.timeout (TRANSCRIPTION_TIMEOUT) es obligatorio") Duration timeout) {

    /** Tope del tiempo de espera: el Gateway corta a los 120 s y aún hay que guardar la respuesta. */
    static final Duration MAX_TIMEOUT = Duration.ofSeconds(110);

    /**
     * Indica si el tiempo de espera es positivo y no supera {@link #MAX_TIMEOUT}.
     *
     * @return {@code true} si el valor falta (lo reporta {@code @NotNull}) o está en el rango
     */
    @AssertTrue(message = "cameia.transcription.timeout (TRANSCRIPTION_TIMEOUT) debe estar entre 1 ms y 110 s")
    public boolean isTimeoutInRange() {
        return timeout == null || (timeout.isPositive() && timeout.compareTo(MAX_TIMEOUT) <= 0);
    }
}
