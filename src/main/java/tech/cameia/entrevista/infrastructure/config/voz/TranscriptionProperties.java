package tech.cameia.entrevista.infrastructure.config.voz;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del servicio de transcripción (AssemblyAI), enlazada al prefijo {@code cameia.transcription} y
 * validada al arrancar.
 *
 * <p>La llave no es obligatoria para arrancar: sin ella el servicio arranca, avisa en el log y cada respuesta por
 * voz responde {@code 503 TRANSCRIPTION_UNAVAILABLE}. Así un despliegue que todavía no tiene el secreto no se cae
 * entero. {@link #toString()} enmascara la llave.</p>
 *
 * @param apiKey llave de AssemblyAI; llega por {@code ASSEMBLYAI_API_KEY}
 * @param baseUrl URL base de la API; llega por {@code ASSEMBLYAI_BASE_URL} (la de la UE es
 *        {@code https://api.eu.assemblyai.com})
 * @param speechModels modelos en orden de preferencia; llegan por {@code ASSEMBLYAI_SPEECH_MODELS}, separados por
 *        coma; vacío deja el valor por defecto del proveedor
 * @param timeout tiempo máximo de toda la transcripción (subida, encolado y espera); llega por
 *        {@code TRANSCRIPTION_TIMEOUT}
 * @param pollInterval espera entre dos consultas del estado; llega por {@code TRANSCRIPTION_POLL_INTERVAL}
 * @param languageConfidenceThreshold confianza mínima para dar por distinto el idioma del audio (CA-5.12.10);
 *        llega por {@code TRANSCRIPTION_LANGUAGE_CONFIDENCE_THRESHOLD}
 */
@Validated
@ConfigurationProperties("cameia.transcription")
public record TranscriptionProperties(
        String apiKey,
        @NotNull(message = "cameia.transcription.base-url (ASSEMBLYAI_BASE_URL) es obligatoria") URI baseUrl,
        List<String> speechModels,
        @NotNull(message = "cameia.transcription.timeout (TRANSCRIPTION_TIMEOUT) es obligatorio") Duration timeout,
        @NotNull(message = "cameia.transcription.poll-interval (TRANSCRIPTION_POLL_INTERVAL) es obligatorio")
        Duration pollInterval,
        @NotNull(message = "cameia.transcription.language-confidence-threshold es obligatorio")
        @DecimalMin(value = "0.0", message = "cameia.transcription.language-confidence-threshold debe estar entre 0 y 1")
        @DecimalMax(value = "1.0", message = "cameia.transcription.language-confidence-threshold debe estar entre 0 y 1")
        Double languageConfidenceThreshold) {

    /** Normaliza la lista de modelos ausente a vacía y quita los elementos en blanco. */
    public TranscriptionProperties {
        speechModels = speechModels == null ? List.of()
                : speechModels.stream().map(String::strip).filter(model -> !model.isEmpty()).toList();
    }

    /** @return {@code true} si los dos tiempos faltan o son positivos */
    @AssertTrue(message = "cameia.transcription.timeout y poll-interval deben ser mayores que cero")
    public boolean isDurationsPositive() {
        return (timeout == null || timeout.isPositive()) && (pollInterval == null || pollInterval.isPositive());
    }

    /** @return {@code true} si hay una llave configurada */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** @return los valores, con la llave enmascarada */
    @Override
    public String toString() {
        return "TranscriptionProperties[apiKey=****, baseUrl=" + baseUrl + ", speechModels=" + speechModels
                + ", timeout=" + timeout + ", pollInterval=" + pollInterval + ", languageConfidenceThreshold="
                + languageConfidenceThreshold + "]";
    }
}
