package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La marca de generación del reporte: sin turno, con su operación y en curso. */
class AiExecutionReportTest {

    @Test
    @DisplayName("reportStarted abre una ejecución GENERAR_REPORTE en curso y sin turno")
    void reportStarted_shouldOpenAnInProgressExecutionWithoutTurn() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        UUID session = UUID.randomUUID();

        AiExecution execution = AiExecution.reportStarted(session, 2, "modelo", "reporte.v1", "gemini", clock);

        assertThat(execution.operation()).isEqualTo(AiOperation.REPORT);
        assertThat(execution.operation().code()).isEqualTo("GENERAR_REPORTE");
        assertThat(execution.turnId()).isNull();
        assertThat(execution.sessionId()).isEqualTo(session);
        assertThat(execution.attempt()).isEqualTo(2);
        assertThat(execution.status()).isEqualTo(AiExecutionStatus.IN_PROGRESS);
        assertThat(execution.promptVersion()).isEqualTo("reporte.v1");
        assertThat(execution.startedAt()).isEqualTo(clock.instant());
    }
}
