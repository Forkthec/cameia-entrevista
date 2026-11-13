package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.model.AiExecution;

/** Caducidad de una evaluación en curso: tiempo de espera del modelo más el margen. */
class EvaluationStalenessTest {

    private static final Instant START = Instant.parse("2026-11-12T15:00:00Z");
    private static final Clock START_CLOCK = Clock.fixed(START, ZoneOffset.UTC);
    private final EvaluationStaleness staleness = new EvaluationStaleness(Duration.ofSeconds(20));

    private static AiExecution started() {
        return AiExecution.evaluationStarted(UUID.randomUUID(), UUID.randomUUID(), "modelo", "evaluacion.v1",
                "proveedor", START_CLOCK);
    }

    @Test
    @DisplayName("El plazo es el tiempo de espera más el margen de la planificación")
    void maxAge_shouldBeTimeoutPlusMargin() {
        assertThat(staleness.maxAge()).isEqualTo(Duration.ofSeconds(20).plus(PlanningClaim.MARGIN));
    }

    @Test
    @DisplayName("Sin ejecución en curso la evaluación se considera caída")
    void isStale_shouldBeTrue_whenNoExecution() {
        assertThat(staleness.isStale(Optional.empty(), START)).isTrue();
    }

    @Test
    @DisplayName("A 34 segundos con tiempo de espera de 20 s sigue viva")
    void isStale_shouldBeFalse_atThirtyFourSeconds() {
        assertThat(staleness.isStale(Optional.of(started()), START.plusSeconds(34))).isFalse();
    }

    @Test
    @DisplayName("A 35 segundos ya está caída")
    void isStale_shouldBeTrue_atThirtyFiveSeconds() {
        assertThat(staleness.isStale(Optional.of(started()), START.plusSeconds(35))).isTrue();
    }

    @Test
    @DisplayName("Una ejecución fallida no está viva")
    void isStale_shouldBeTrue_whenFailed() {
        AiExecution failed = started().failed(ErrorCode.LLM_UNAVAILABLE, START_CLOCK);

        assertThat(staleness.isStale(Optional.of(failed), START)).isTrue();
    }
}
