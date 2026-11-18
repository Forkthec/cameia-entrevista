package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tech.cameia.entrevista.application.service.AiCallRunner.AiCall;
import tech.cameia.entrevista.application.service.AiCallRunner.AiSuccess;
import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiExecutionStatus;
import tech.cameia.entrevista.domain.model.AiOperation;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.Waiter;

/** Ciclo de hasta tres intentos: proveedor de cada uno, esperas, cambios de intento y causa final. */
class AiCallRunnerTest {

    private static final Duration FIRST_WAIT = Duration.ofMillis(500);
    private static final Duration SECOND_WAIT = Duration.ofMillis(1000);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneOffset.UTC);
    private static final LanguageModelPrompt PROMPT = new LanguageModelPrompt("Evalúa.", 16, AiOperation.EVALUATE);
    private static final LanguageModelResponse OK = new LanguageModelResponse("{}", 10, 5, "modelo");

    private final ScriptedPort port = new ScriptedPort();
    private final RecordingWaiter waiter = new RecordingWaiter();
    private final List<String> switches = new ArrayList<>();
    private final AiCallRunner runner = new AiCallRunner(port, waiter, FIRST_WAIT, SECOND_WAIT, CLOCK);

    @Test
    @DisplayName("Un primer intento válido no espera ni cambia de intento")
    void run_shouldReturnFirst_whenValid() {
        port.script(OK);

        AiSuccess<String> success = runner.run(call());

        assertThat(success.value()).isEqualTo("ok");
        assertThat(success.response()).isSameAs(OK);
        assertThat(success.mark().attempt()).isEqualTo(1);
        assertThat(waiter.waits).isEmpty();
        assertThat(switches).isEmpty();
        assertThat(port.providers).containsExactly(AiProvider.PRIMARY);
    }

    @Test
    @DisplayName("Un fallo y un segundo intento válido esperan 500 ms y abren el intento 2 con el principal")
    void run_shouldSucceedOnSecond() {
        port.script(new LlmUnavailableException(null), OK);

        AiSuccess<String> success = runner.run(call());

        assertThat(waiter.waits).containsExactly(FIRST_WAIT);
        assertThat(switches).containsExactly("2:PRIMARY");
        assertThat(success.mark().attempt()).isEqualTo(2);
        assertThat(port.providers).containsExactly(AiProvider.PRIMARY, AiProvider.PRIMARY);
    }

    @Test
    @DisplayName("Dos fallos del principal llevan el tercer intento al respaldo tras 500 ms y 1 s")
    void run_shouldUseFallbackOnThird() {
        port.script(new LlmUnavailableException(null), new LlmUnavailableException(null), OK);

        AiSuccess<String> success = runner.run(call());

        assertThat(waiter.waits).containsExactly(FIRST_WAIT, SECOND_WAIT);
        assertThat(switches).containsExactly("2:PRIMARY", "3:FALLBACK");
        assertThat(port.providers).containsExactly(AiProvider.PRIMARY, AiProvider.PRIMARY, AiProvider.FALLBACK);
        assertThat(success.mark().attempt()).isEqualTo(3);
    }

    @Test
    @DisplayName("Un límite de uso del principal salta al respaldo sin repetir el principal")
    void run_shouldSkipPrimary_after429() {
        port.script(new LlmLimitReachedException(null), OK);

        runner.run(call());

        assertThat(switches).containsExactly("2:FALLBACK");
        assertThat(port.providers).containsExactly(AiProvider.PRIMARY, AiProvider.FALLBACK);
    }

    @Test
    @DisplayName("Con los dos proveedores limitados el ciclo termina en dos intentos con LLM_LIMIT_REACHED")
    void run_shouldStop_whenBothLimited() {
        port.script(new LlmLimitReachedException(null), new LlmLimitReachedException(null));

        Throwable thrown = catchThrowable(() -> runner.run(call()));

        assertThat(thrown).isInstanceOf(AiCycleFailedException.class);
        AiCycleFailedException cycle = (AiCycleFailedException) thrown;
        assertThat(cycle.cause().getErrorCode()).isEqualTo(ErrorCode.LLM_LIMIT_REACHED);
        assertThat(port.providers).hasSize(2);
        assertThat(switches).containsExactly("2:FALLBACK");
    }

    @Test
    @DisplayName("Sin llave del respaldo el tercer intento vuelve al principal")
    void run_shouldUsePrimaryOnThird_whenFallbackUnavailable() {
        port.fallbackAvailable = false;
        port.script(new LlmUnavailableException(null), new LlmUnavailableException(null), OK);

        runner.run(call());

        assertThat(switches).containsExactly("2:PRIMARY", "3:PRIMARY");
        assertThat(port.providers).containsOnly(AiProvider.PRIMARY);
    }

    @Test
    @DisplayName("Tres fallos terminan con la causa del último y su ejecución todavía en curso")
    void run_shouldThrowLastCause() {
        port.script(new LlmUnavailableException(null), new LlmUnavailableException(null),
                new LlmInvalidOutputException("sin forma"));

        Throwable thrown = catchThrowable(() -> runner.run(call()));

        AiCycleFailedException cycle = (AiCycleFailedException) thrown;
        assertThat(cycle.cause().getErrorCode()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT);
        assertThat(cycle.lastMark().attempt()).isEqualTo(3);
        assertThat(cycle.lastMark().status()).isEqualTo(AiExecutionStatus.IN_PROGRESS);
        assertThat(waiter.waits).containsExactly(FIRST_WAIT, SECOND_WAIT);
    }

    @Test
    @DisplayName("Una respuesta que el lector rechaza cuenta como fallo del intento")
    void run_shouldCountReaderRejectionAsFailure() {
        port.script(OK, OK);
        Function<LanguageModelResponse, String> reader = new Function<>() {
            private int calls;

            @Override
            public String apply(LanguageModelResponse response) {
                if (++calls == 1) {
                    throw new LlmInvalidOutputException("rechazada");
                }
                return "ok";
            }
        };

        AiSuccess<String> success = runner.run(call(reader));

        assertThat(success.value()).isEqualTo("ok");
        assertThat(switches).containsExactly("2:PRIMARY");
    }

    @Test
    @DisplayName("Un fallo inesperado del puerto se vuelve LLM_UNAVAILABLE")
    void run_shouldWrapUnexpected() {
        port.script(new IllegalStateException("raro"), new IllegalStateException("raro"),
                new IllegalStateException("raro"));

        Throwable thrown = catchThrowable(() -> runner.run(call()));

        AiCycleFailedException cycle = (AiCycleFailedException) thrown;
        assertThat(cycle.cause()).isInstanceOf(LlmUnavailableException.class);
        assertThat(cycle.cause().getErrorCode()).isEqualTo(ErrorCode.LLM_UNAVAILABLE);
    }

    @Test
    @DisplayName("Un fallo de negocio ajeno a la IA, lanzado por el lector, también se vuelve LLM_UNAVAILABLE")
    void run_shouldWrapNonLlmBusinessFailure() {
        port.script(OK, OK, OK);

        Throwable thrown = catchThrowable(() -> runner.run(call(response -> {
            throw new DatabaseUnavailableException(null);
        })));

        assertThat(((AiCycleFailedException) thrown).cause().getErrorCode()).isEqualTo(ErrorCode.LLM_UNAVAILABLE);
    }

    @Test
    @DisplayName("Si el hilo se interrumpe durante la espera, el ciclo termina sin llamar de nuevo al modelo")
    void run_shouldAbortCycle_whenThreadInterruptedDuringWait() {
        port.script(new LlmUnavailableException(null), OK);
        AiCallRunner interrupting = new AiCallRunner(port, duration -> Thread.currentThread().interrupt(),
                FIRST_WAIT, SECOND_WAIT, CLOCK);

        try {
            Throwable thrown = catchThrowable(() -> interrupting.run(call()));

            assertThat(thrown).isInstanceOf(AiCycleFailedException.class);
            assertThat(((AiCycleFailedException) thrown).cause().getErrorCode()).isEqualTo(ErrorCode.LLM_UNAVAILABLE);
            assertThat(((AiCycleFailedException) thrown).lastMark().attempt()).isEqualTo(2);
            assertThat(port.providers).hasSize(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Las esperas ocurren fuera de toda transacción")
    void run_shouldWaitOutsideTransaction() {
        port.script(new LlmUnavailableException(null), new LlmUnavailableException(null), OK);

        runner.run(call());

        assertThat(waiter.transactionActiveDuringWait).isFalse();
        assertThat(waiter.waits).hasSize(2);
    }

    @Test
    @DisplayName("Si el cambio de intento pierde la base, el ciclo termina con esa causa y la marca del intento fallido")
    void run_shouldThrowDatabaseUnavailable_whenSwitchFails() {
        port.script(new LlmUnavailableException(null), OK);
        DatabaseUnavailableException database = new DatabaseUnavailableException(null);
        AttemptSwitch failing = (failed, attempt, provider) -> {
            throw database;
        };

        Throwable thrown = catchThrowable(() -> runner.run(
                new AiCall<>(PROMPT, response -> "ok", firstMark(), failing)));

        assertThat(thrown).isInstanceOf(AiCycleFailedException.class);
        assertThat(((AiCycleFailedException) thrown).cause()).isSameAs(database);
        assertThat(((AiCycleFailedException) thrown).lastMark().attempt()).isEqualTo(1);
        assertThat(waiter.waits).isEmpty();
    }

    @Test
    @DisplayName("El peor caso suma 10,5 s con evaluación y 61,5 s con planificación, ambos bajo 120 s")
    void run_shouldRespectWorstCase() {
        assertThat(worstCase(Duration.ofSeconds(3))).isEqualTo(Duration.ofMillis(10_500));
        assertThat(worstCase(Duration.ofSeconds(20))).isEqualTo(Duration.ofMillis(61_500));
        assertThat(worstCase(Duration.ofSeconds(20))).isLessThan(Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("El esquema por proveedor solo se agrega a los intentos que lo piden")
    void run_shouldApplySchemaPerProvider() {
        port.script(new LlmUnavailableException(null), new LlmUnavailableException(null), OK);
        LanguageModelPrompt plan = new LanguageModelPrompt("Planifica.", 16, AiOperation.PLAN);

        runner.run(new AiCall<>(plan, response -> "ok", firstMark(), nextAttempt(),
                provider -> provider == AiProvider.FALLBACK ? "{\"type\":\"object\"}" : null));

        assertThat(port.schemas).containsExactly(null, null, "{\"type\":\"object\"}");
    }

    /** Suma lo que tardarían tres intentos que fallan, cada uno con la latencia dada, más las dos esperas. */
    private Duration worstCase(Duration latency) {
        AtomicLong elapsed = new AtomicLong();
        ScriptedPort slow = new ScriptedPort() {
            @Override
            public LanguageModelResponse generate(LanguageModelPrompt prompt, AiProvider provider) {
                elapsed.addAndGet(latency.toMillis());
                return super.generate(prompt, provider);
            }
        };
        slow.script(new LlmUnavailableException(null), new LlmUnavailableException(null),
                new LlmUnavailableException(null));
        Waiter clockWaiter = duration -> elapsed.addAndGet(duration.toMillis());
        AiCallRunner timed = new AiCallRunner(slow, clockWaiter, FIRST_WAIT, SECOND_WAIT, CLOCK);

        catchThrowable(() -> timed.run(call()));

        return Duration.ofMillis(elapsed.get());
    }

    private AiCall<String> call() {
        return call(response -> "ok");
    }

    private AiCall<String> call(Function<LanguageModelResponse, String> reader) {
        return new AiCall<>(PROMPT, reader, firstMark(), nextAttempt());
    }

    private AttemptSwitch nextAttempt() {
        return (failed, attempt, provider) -> {
            assertThat(failed.status()).isEqualTo(AiExecutionStatus.FAILED);
            switches.add(attempt + ":" + provider);
            return AiExecution.planningStarted(failed.sessionId(), attempt, "modelo", "v1", provider.code(), CLOCK);
        };
    }

    private static AiExecution firstMark() {
        return AiExecution.planningStarted(UUID.randomUUID(), 1, "modelo", "v1", "gemini", CLOCK);
    }

    /** Puerto con guion: cada llamada consume una respuesta o lanza una excepción, y anota el proveedor. */
    private static class ScriptedPort implements LanguageModelPort {

        private final Deque<Object> script = new ArrayDeque<>();
        final List<AiProvider> providers = new ArrayList<>();
        final List<String> schemas = new ArrayList<>();
        boolean fallbackAvailable = true;

        void script(Object... steps) {
            script.addAll(List.of(steps));
        }

        @Override
        public LanguageModelResponse generate(LanguageModelPrompt prompt, AiProvider provider) {
            providers.add(provider);
            schemas.add(prompt.jsonSchema());
            Object step = script.pollFirst();
            if (step instanceof RuntimeException failure) {
                throw failure;
            }
            return (LanguageModelResponse) step;
        }

        @Override
        public boolean isAvailable(AiProvider provider) {
            return provider == AiProvider.PRIMARY || fallbackAvailable;
        }
    }

    /** Espera falsa: anota cada pausa y si había una transacción abierta. */
    private static class RecordingWaiter implements Waiter {

        final List<Duration> waits = new ArrayList<>();
        boolean transactionActiveDuringWait;

        @Override
        public void pause(Duration duration) {
            waits.add(duration);
            transactionActiveDuringWait |= TransactionSynchronizationManager.isActualTransactionActive();
        }
    }
}
