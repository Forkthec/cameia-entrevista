package tech.cameia.entrevista.infrastructure.config.voz;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del cliente del servicio de Voz, enlazada al prefijo {@code cameia.voz} y validada al arrancar.
 *
 * @param baseUrl dirección base del servicio de Voz; llega por {@code VOZ_BASE_URL}
 * @param timeout tiempo máximo de cada intento de transcripción; llega por {@code VOZ_TIMEOUT} (60 s, CA-5.13.3)
 * @param connectTimeout tiempo máximo para abrir la conexión; llega por {@code VOZ_CONNECT_TIMEOUT}
 * @param retryPause pausa antes del reintento automático; llega por {@code VOZ_RETRY_PAUSE}
 */
@Validated
@ConfigurationProperties("cameia.voz")
public record VoiceServiceProperties(
        @NotNull(message = "cameia.voz.base-url (VOZ_BASE_URL) es obligatoria") URI baseUrl,
        @NotNull(message = "cameia.voz.timeout (VOZ_TIMEOUT) es obligatorio") Duration timeout,
        @NotNull(message = "cameia.voz.connect-timeout (VOZ_CONNECT_TIMEOUT) es obligatorio") Duration connectTimeout,
        @NotNull(message = "cameia.voz.retry-pause (VOZ_RETRY_PAUSE) es obligatoria") Duration retryPause) {

    /**
     * Los tiempos de espera deben ser positivos y la pausa no puede ser negativa. Un valor nulo lo reporta
     * {@code @NotNull}.
     *
     * @return {@code true} si los tiempos son válidos o faltan
     */
    @AssertTrue(message = "cameia.voz: los tiempos de espera deben ser mayores que cero y la pausa no negativa")
    public boolean isTimingValid() {
        return (timeout == null || timeout.isPositive())
                && (connectTimeout == null || connectTimeout.isPositive())
                && (retryPause == null || !retryPause.isNegative());
    }
}
