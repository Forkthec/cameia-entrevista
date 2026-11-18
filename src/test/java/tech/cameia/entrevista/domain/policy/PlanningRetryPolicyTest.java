package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.policy.PlanningRetryPolicy.Decision;

/** Reintentos manuales, caducidad de la marca y plantilla de tipos de la planificación. */
class PlanningRetryPolicyTest {

    private final PlanningRetryPolicy policy = new PlanningRetryPolicy();

    @Test
    void decide_shouldAllowFirstRequest() {
        assertThat(policy.decide(0, false)).isEqualTo(Decision.ALLOW);
    }

    @Test
    void decide_shouldAllowRetriesUnderTheLimit() {
        assertThat(policy.decide(0, true)).isEqualTo(Decision.ALLOW);
        assertThat(policy.decide(1, true)).isEqualTo(Decision.ALLOW);
    }

    @Test
    void decide_shouldExhaustAfterTwoRetries() {
        assertThat(policy.decide(2, true)).isEqualTo(Decision.EXHAUSTED);
        assertThat(policy.decide(2, false)).isEqualTo(Decision.ALLOW);
    }

    @Test
    void retriesAfterRequest_shouldRaiseOnlyAfterFailure() {
        assertThat(policy.retriesAfterRequest(0, false)).isZero();
        assertThat(policy.retriesAfterRequest(0, true)).isEqualTo(1);
        assertThat(policy.retriesAfterRequest(1, true)).isEqualTo(2);
        assertThat(policy.retriesAfterRequest(2, true)).isEqualTo(2);
    }

    @Test
    void isLastAttempt_shouldFollowCounter() {
        assertThat(policy.isLastAttempt(1)).isFalse();
        assertThat(policy.isLastAttempt(2)).isTrue();
    }

    @Test
    void claim_shouldExpireAfterTimeoutPlusMargin() {
        Instant start = Instant.parse("2026-11-10T10:00:00Z");
        AiExecution mark = AiExecution.planningStarted(UUID.randomUUID(), 1, "m", "p", "gemini",
                Clock.fixed(start, ZoneOffset.UTC));
        PlanningClaim claim = new PlanningClaim(Duration.ofMillis(20000));
        assertThat(claim.maxAge()).isEqualTo(Duration.ofMillis(35000));
        assertThat(claim.isLive(mark, start.plusMillis(34999))).isTrue();
        assertThat(claim.isLive(mark, start.plusMillis(35000))).isFalse();
    }

    @Test
    void template_shouldAssignTypesByPosition() {
        assertThat(CompetencyTemplate.typeAt(1).code()).isEqualTo("APERTURA");
        assertThat(CompetencyTemplate.typeAt(4).code()).isEqualTo("TECNICA");
        assertThat(CompetencyTemplate.typeAt(5).code()).isEqualTo("SITUACIONAL");
    }
}
