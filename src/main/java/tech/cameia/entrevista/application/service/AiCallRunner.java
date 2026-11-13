package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiExecution;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.policy.AiRetryPolicy;
import tech.cameia.entrevista.domain.policy.AiRetryPolicy.Attempt;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.domain.port.Waiter;

/**
 * Ejecuta un pedido a la IA en un ciclo de hasta tres intentos, con espera creciente entre ellos.
 *
 * <p>Un intento es válido cuando el modelo responde y el lector del caso de uso acepta el texto. Si falla, el
 * ciclo cierra la ejecución fallida y abre la siguiente (la transacción la maneja {@link AttemptSwitch}), espera y
 * vuelve a llamar. La espera va después del cambio de intento, con la fila nueva ya guardada, y nunca dentro de una
 * transacción. Los registros llevan la operación, el número de intento, el proveedor, el resultado y la duración;
 * nunca el texto enviado ni el recibido.</p>
 */
@Service
public class AiCallRunner {

    private static final Logger logger = LoggerFactory.getLogger(AiCallRunner.class);

    private final LanguageModelPort model;

    private final Waiter waiter;

    private final Duration firstWait;

    private final Duration secondWait;

    private final Clock clock;

    private final AiRetryPolicy policy = new AiRetryPolicy();

    /**
     * Crea el ejecutor.
     *
     * @param model puerto del modelo de lenguaje
     * @param waiter pausa entre intentos
     * @param firstWait espera antes del segundo intento
     * @param secondWait espera antes del tercer intento
     * @param clock reloj que da la fecha de cierre de cada intento fallido
     */
    public AiCallRunner(LanguageModelPort model, Waiter waiter,
            @Value("${cameia.llm.retry.first-wait}") Duration firstWait,
            @Value("${cameia.llm.retry.second-wait}") Duration secondWait, Clock clock) {
        this.model = model;
        this.waiter = waiter;
        this.firstWait = firstWait;
        this.secondWait = secondWait;
        this.clock = clock;
    }

    /**
     * Un pedido a la IA con su lector y la forma de cambiar de intento.
     *
     * @param <T> tipo del resultado ya validado
     * @param prompt lo que se envía al modelo
     * @param reader convierte y valida la respuesta; lanza una excepción de negocio si no sirve
     * @param firstMark ejecución del primer intento, ya guardada como en curso
     * @param attemptSwitch cierra un intento fallido y abre el siguiente
     * @param schemaFor esquema que se agrega al prompt según el proveedor del intento, o {@code null} para no cambiarlo
     */
    public record AiCall<T>(LanguageModelPrompt prompt, Function<LanguageModelResponse, T> reader,
            AiExecution firstMark, AttemptSwitch attemptSwitch, Function<AiProvider, String> schemaFor) {

        /**
         * Pedido sin esquema distinto por proveedor.
         *
         * @param prompt lo que se envía al modelo
         * @param reader convierte y valida la respuesta
         * @param firstMark ejecución del primer intento
         * @param attemptSwitch cambio de intento
         */
        public AiCall(LanguageModelPrompt prompt, Function<LanguageModelResponse, T> reader, AiExecution firstMark,
                AttemptSwitch attemptSwitch) {
            this(prompt, reader, firstMark, attemptSwitch, null);
        }
    }

    /**
     * Resultado válido del ciclo.
     *
     * @param <T> tipo del resultado
     * @param value resultado ya validado
     * @param response respuesta del modelo que lo produjo
     * @param mark ejecución del intento que lo logró, aún en curso
     */
    public record AiSuccess<T>(T value, LanguageModelResponse response, AiExecution mark) {
    }

    /**
     * Ejecuta el ciclo. Si ningún intento da un resultado válido, lanza {@link AiCycleFailedException} con la causa
     * del último intento y su ejecución (aún en curso: la cierra quien llama, con su estado de negocio).
     *
     * @param <T> tipo del resultado
     * @param call el pedido
     * @return el resultado del primer intento válido
     */
    <T> AiSuccess<T> run(AiCall<T> call) {
        AiExecution mark = call.firstMark();
        List<Attempt> done = new ArrayList<>();
        AiProvider provider = AiProvider.PRIMARY;
        while (true) {
            long start = System.nanoTime();
            try {
                LanguageModelResponse response = model.generate(withSchema(call, provider), provider);
                T value = call.reader().apply(response);
                logAttempt(mark, done.size() + 1, provider, "ok", start);
                return new AiSuccess<>(value, response, mark);
            } catch (RuntimeException raw) {
                BusinessException failure = asLlmFailure(raw);
                ErrorCode code = failure.getErrorCode();
                logAttempt(mark, done.size() + 1, provider, code.name(), start);
                done.add(new Attempt(provider, code == ErrorCode.LLM_LIMIT_REACHED));
                AiProvider next = policy.next(done, model.isAvailable(AiProvider.FALLBACK)).orElse(null);
                if (next == null) {
                    logger.warn("Ciclo de IA agotado [operacion={}, intentos={}, code={}]", mark.operation().code(),
                            done.size(), code.name());
                    throw new AiCycleFailedException(failure, mark);
                }
                try {
                    mark = call.attemptSwitch().next(mark.failed(code, clock), done.size() + 1, next);
                } catch (BusinessException switchFailure) {
                    // Sin cambio de intento no hay ciclo: quien llama cierra la ejecución que sigue en curso.
                    throw new AiCycleFailedException(switchFailure, mark);
                }
                waiter.pause(done.size() == 1 ? firstWait : secondWait);
                if (Thread.currentThread().isInterrupted()) {
                    // El servicio se apaga: no se llama de nuevo al modelo con el hilo interrumpido.
                    throw new AiCycleFailedException(failure, mark);
                }
                provider = next;
            }
        }
    }

    private static LanguageModelPrompt withSchema(AiCall<?> call, AiProvider provider) {
        if (call.schemaFor() == null) {
            return call.prompt();
        }
        String schema = call.schemaFor().apply(provider);
        return schema == null ? call.prompt() : call.prompt().withSchema(schema);
    }

    /** Un fallo de IA conserva su código; cualquier otro fallo del puerto o del lector es «IA no disponible». */
    private static BusinessException asLlmFailure(RuntimeException raw) {
        if (raw instanceof BusinessException business && business.getErrorCode().name().startsWith("LLM_")) {
            return business;
        }
        return new LlmUnavailableException(raw);
    }

    private static void logAttempt(AiExecution mark, int attempt, AiProvider provider, String result, long start) {
        logger.info("Intento de IA [operacion={}, intento={}, proveedor={}, resultado={}, duracionMs={}]",
                mark.operation().code(), attempt, provider.code(), result,
                Duration.ofNanos(System.nanoTime() - start).toMillis());
    }
}
