package tech.cameia.entrevista.infrastructure.config.ia;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Esperas entre los intentos de un ciclo de IA, enlazadas al prefijo {@code cameia.llm.retry} y validadas al
 * arrancar: si un valor falta o no cumple su regla, el servicio no arranca.
 *
 * @param firstWait espera antes del segundo intento; no negativa; llega por {@code LLM_RETRY_FIRST_WAIT}
 * @param secondWait espera antes del tercer intento; no menor que la primera; llega por
 *        {@code LLM_RETRY_SECOND_WAIT}
 */
@Validated
@ConfigurationProperties("cameia.llm.retry")
public record AiRetryProperties(
        @NotNull(message = "cameia.llm.retry.first-wait (LLM_RETRY_FIRST_WAIT) es obligatorio") Duration firstWait,
        @NotNull(message = "cameia.llm.retry.second-wait (LLM_RETRY_SECOND_WAIT) es obligatorio")
        Duration secondWait) {

    /**
     * Indica si la primera espera es válida. Un valor nulo lo reporta {@code @NotNull}.
     *
     * @return {@code true} si la primera espera falta o no es negativa
     */
    @AssertTrue(message = "cameia.llm.retry.first-wait (LLM_RETRY_FIRST_WAIT) no puede ser negativo")
    public boolean isFirstWaitNotNegative() {
        return firstWait == null || !firstWait.isNegative();
    }

    /**
     * Indica si la segunda espera es válida. Un valor nulo lo reporta {@code @NotNull}.
     *
     * @return {@code true} si alguna espera falta o la segunda no es menor que la primera
     */
    @AssertTrue(message = "cameia.llm.retry.second-wait (LLM_RETRY_SECOND_WAIT) no puede ser menor que la primera espera")
    public boolean isSecondWaitNotBeforeFirst() {
        return firstWait == null || secondWait == null || secondWait.compareTo(firstWait) >= 0;
    }
}
