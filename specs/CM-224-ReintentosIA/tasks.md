# Tarjetas · CM-224 · Reintentos automáticos de la IA con respaldo

Rama `CM-224-reintentos-ia` desde la punta de `CM-220-cerrar-sesion`. Prefijos: `M` = `src/main/java/tech/cameia/entrevista/`, `T` = `src/test/java/tech/cameia/entrevista/`, `R` = `src/main/resources/`.

## 0. Cobertura de requisitos

| Requisito / CA | Tarjeta |
|---|---|
| REQ-RTY-01 (CA-5.6.13, 5.6.14) | T1, T2 |
| REQ-RTY-02, 03, 07 (CA-5.6.2, 5.6.4, 5.6.8, 5.6.12) | T2, T4, T5 |
| REQ-RTY-04 (CA-5.6.6, 5.6.8) | T3 |
| REQ-RTY-05, 06 (CA-5.6.1, 5.6.5, 5.6.7) | T2, T4, T5 |
| REQ-RTY-08 (CA-5.6.3, 5.6.9, 5.6.10, 5.6.11) | T4, T5 |
| REQ-RTY-09 | T3, T4 |
| REQ-RTY-10 | T4 |
| REQ-RTY-11 | T3 |
| REQ-RTY-12 | T2, T3, T5 |

## 1. Reglas comunes

Las de `specs/CM-216-RegistrarRespuesta/tasks.md` §1, repetidas: identificadores en inglés, documentación en español, `method_shouldX_whenY` con `@DisplayName` en español, sin `CM-NNN` en comentarios, sin `Thread.sleep` en pruebas salvo dentro del servidor HTTP local que simula la demora (es el objeto de la prueba de tiempo), sin `catch` vacío, métodos ≤ 20 líneas y ≤ 3 parámetros, Javadoc en todo. **La única dependencia nueva permitida** es `spring-ai-starter-model-openai` (D-224-1), sin versión propia (la da el BOM). Nunca se lee ni se imprime una llave real; en pruebas, `clave-de-prueba`. Comando por tarjeta `./mvnw.cmd -B -ntp -Dtest='<clases>' test`. Terminado = pruebas nuevas y las listadas en verde, comando y salida en el informe. **Detente** si el código de CM-216 o CM-220 no tiene los nombres que cita la tarjeta.

## T0 · Verificación previa de Spring AI (sin commit)

1. En una rama local de prueba, agrega al `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-model-openai</artifactId>
   </dependency>
   ```
   y ejecuta `./mvnw.cmd -B -ntp dependency:resolve`.
2. Con `javap -classpath <jar de spring-ai-openai 2.0.2> -public` comprueba y copia en el informe las firmas de: `org.springframework.ai.openai.OpenAiChatModel` (cómo se construye: `builder()` y sus métodos), `OpenAiChatOptions` (o su builder: `model`, `maxTokens`/`maxCompletionTokens`, `temperature`, `reasoningEffort`, `responseFormat`), la clase de `ResponseFormat` (tipo `JSON_SCHEMA`, `schema`, `strict`), y cómo se fijan `baseUrl`, `apiKey` y el tiempo de lectura (en `OpenAiApi.builder()` o en el cliente que use 2.0.2).
3. Igual con `org.springframework.ai.google.genai.GoogleGenAiChatModel` (cómo armar uno con un `com.google.genai.Client` propio y opciones por defecto).
4. Busca en el JAR de autoconfiguración la propiedad que elige el modelo de chat activo (`spring.ai.model.chat`) y confirma que con `google-genai` no se crea el `ChatModel` de OpenAI.
5. **Si cualquier punto no existe o no cuadra: detente y reporta a Paula** con las firmas reales. No sigas con T3.
6. Borra la rama de prueba (o déjala sin push) y anota las firmas exactas en el informe para T3.

## T1 · Proveedor y política de reintentos (dominio)

**Crear** `M/domain/model/AiProvider.java`:

```java
/** Proveedor de IA dentro de un ciclo de intentos. */
public enum AiProvider {
    /** Gemini: proveedor principal. */
    PRIMARY("gemini"),
    /** Qwen en Groq: proveedor de respaldo. */
    FALLBACK("groq");
    private final String code;
    AiProvider(String code) { this.code = code; }
    /** Valor que se guarda en {@code ejecucion_ia.proveedor}. */
    public String code() { return code; }
}
```

**Crear** `M/domain/policy/AiRetryPolicy.java`:

```java
/** Decide el proveedor de cada intento de un ciclo de hasta tres, según la tabla de proveedores. */
public final class AiRetryPolicy {
    public static final int MAX_ATTEMPTS = 3;

    /** Un intento ya hecho: con qué proveedor y si terminó por límite de uso. */
    public record Attempt(AiProvider provider, boolean limitReached) { }

    /**
     * Proveedor del intento siguiente, o vacío si el ciclo termina.
     *
     * @param done intentos ya hechos, en orden
     * @param fallbackAvailable si el respaldo tiene llave
     */
    public Optional<AiProvider> next(List<Attempt> done, boolean fallbackAvailable) { … }
}
```

Regla de `next`: si `done.size() >= 3` → vacío. `limited(p)` = algún intento de `done` con proveedor `p` y `limitReached`. Preferido: intento 1 y 2 → `PRIMARY`; intento 3 → `FALLBACK`. Si el preferido es `PRIMARY` y `limited(PRIMARY)` → `FALLBACK`. Si el elegido es `FALLBACK` y (`!fallbackAvailable` o `limited(FALLBACK)`) → `PRIMARY`. Si el elegido final está limitado (`PRIMARY` con `limited(PRIMARY)`) → vacío.

**Pruebas** `T/domain/policy/AiRetryPolicyTest.java`: `@ParameterizedTest` con las 11 filas de la spec §7, más `next_shouldReturnPrimary_whenEmpty` y `next_shouldEnd_afterThree`. `AiProviderTest`: `code()` de cada uno.

**Verifica:** `-Dtest='AiRetryPolicyTest,AiProviderTest,LayeredArchitectureTest'`.

## T2 · Ciclo de intentos (aplicación)

**Modificar** `LanguageModelPrompt`: componentes `(String text, int maxOutputTokens, String jsonSchema, AiOperation operation)` (`jsonSchema` ya existe desde CM-216); `operation` obligatorio; `toString` sin el texto ni el esquema. Actualiza los constructores existentes (`PlanningPromptBuilder` → `AiOperation.PLAN`, `EvaluationPromptBuilder` → `AiOperation.EVALUATE`) y `LanguageModelPromptTest`.

**Modificar** `LanguageModelPort`:

```java
/** Pide una respuesta al proveedor dado, con el tiempo máximo de la operación del prompt. */
LanguageModelResponse generate(LanguageModelPrompt prompt, AiProvider provider);
/** Si el proveedor puede usarse (el respaldo puede no tener llave). */
boolean isAvailable(AiProvider provider);
```
Quita `generate(LanguageModelPrompt)`. Actualiza los dobles de prueba que lo implementan (`ScriptedModelConfiguration` y otros: búscalos con `git grep -n "implements LanguageModelPort"` en `src/test`); el guion recibe ahora `(prompt, provider)` y puede responder distinto por proveedor.

**Para que este commit compile** (cada commit compila): `GeminiLanguageModelAdapter` sigue implementando `LanguageModelPort` en este commit: `generate(prompt, PRIMARY)` hace lo de hoy; `generate(prompt, FALLBACK)` lanza `IllegalStateException("Respaldo no disponible")`; `isAvailable(p)` = `p == PRIMARY`. T3 lo reemplaza por `RoutingLanguageModelAdapter`.

**Todos los usos que cambian.** Corre `git grep -n -E "new LanguageModelPrompt\(|implements LanguageModelPort|\.generate\(" -- src` y actualiza cada resultado: `operation` = `AiOperation.PLAN` en planificación y `AiOperation.EVALUATE` en evaluación; las llamadas al puerto pasan a `generate(prompt, AiProvider.PRIMARY)`; los `when(model.generate(any()))` de Mockito pasan a `when(model.generate(any(), any()))`. Lista esperada (base `09bb7ff` más CM-216 y CM-220):

| Archivo | Qué cambia |
|---|---|
| `M/application/service/PlanningPromptBuilder.java`, `EvaluationPromptBuilder.java` | operación del prompt |
| `M/application/service/StartSessionService.java`, `TurnEvaluator.java` | llamada al puerto (T4 la pasa luego al ciclo) |
| `M/infrastructure/ia/GeminiLanguageModelAdapter.java` | ver el párrafo anterior |
| `T/domain/model/LanguageModelPromptTest.java` | constructor y `toString` |
| `T/application/service/StartSessionServiceTest.java`, `TurnEvaluatorTest.java` | `generate(any(), any())` |
| `T/infrastructure/ia/GeminiLanguageModelAdapterTest.java`, `GeminiLanguageModelAdapterDefensesTest.java`, `GeminiLanguageModelLiveTest.java` | constructor del prompt y, si llaman al puerto, el proveedor |
| `T/spike/planificacion/GeminiLite.java`, `MedicionFinalRunner.java`, `MedicionEvaluacionRunner.java` | constructor del prompt (`EVALUATE` en la medición del evaluador, `PLAN` en la de planificación) y `generate(prompt, PRIMARY)` |
| Dobles `ScriptedModelConfiguration` de las pruebas punta a punta (CM-212, CM-216, CM-220) | `generate(prompt, provider)` e `isAvailable` |

Si `git grep` muestra un archivo fuera de esta lista, actualízalo igual y nómbralo en el informe; si no sabes qué operación le toca, **detente** y repórtalo. Sin cambiar ninguna aserción salvo la firma.

**Crear** `M/application/service/AttemptSwitch.java`:

```java
/** Cambia de intento dentro de un ciclo: cierra el que falló y abre el siguiente, en una transacción del caso de uso. */
@FunctionalInterface
public interface AttemptSwitch {
    /**
     * @param failed intento que falló, ya marcado como fallido con su causa
     * @param attempt número del intento que empieza (2 o 3)
     * @param provider proveedor del intento que empieza
     * @return la ejecución nueva, ya guardada como en curso
     */
    AiExecution next(AiExecution failed, int attempt, AiProvider provider);
}
```

**Crear** `M/application/service/AiCallRunner.java` (`@Service`), dependencias `LanguageModelPort model`, `Waiter waiter`, `@Value("${cameia.llm.retry.first-wait}") Duration firstWait`, `@Value("${cameia.llm.retry.second-wait}") Duration secondWait` (las propiedades se declaran y validan en T3; en las pruebas de T2 se pasan por el constructor), `Clock clock`; campo `AiRetryPolicy policy = new AiRetryPolicy()`.

```java
/** Un pedido a la IA con su lector y la forma de cambiar de intento. */
public record AiCall<T>(LanguageModelPrompt prompt, Function<LanguageModelResponse, T> reader,
        AiExecution firstMark, AttemptSwitch attemptSwitch) { }

/** Resultado válido con la respuesta del modelo y el intento que lo logró. */
public record AiSuccess<T>(T value, LanguageModelResponse response, AiExecution mark) { }

/**
 * Ejecuta el ciclo. Si ningún intento da un resultado válido, lanza {@link AiCycleFailedException}
 * con la causa del último intento y su ejecución (aún en curso: la cierra quien llama, con su estado de negocio).
 */
public <T> AiSuccess<T> run(AiCall<T> call) { … }
```

**Crear** `M/application/service/AiCycleFailedException.java` (no es de negocio: `RuntimeException` de paquete con `BusinessException cause()` y `AiExecution lastMark()`; nunca sale de la capa de aplicación: quien llama la desenvuelve y relanza `cause`). Si `UntypedExceptionClassificationTest` la rechaza, detente y reporta.

Flujo de `run`: `mark = firstMark`, `done = []`. Bucle: `provider = policy.next(done, model.isAvailable(FALLBACK))` (para el 1.er intento, `PRIMARY`); `try { response = model.generate(prompt, provider); value = reader.apply(response); log INFO resultado "ok"; return new AiSuccess(value, response, mark); } catch (BusinessException failure)` con código `LLM_*` (cualquier otra `RuntimeException` del lector o del puerto se envuelve en `LlmUnavailableException`): log INFO con el código; `done.add(new Attempt(provider, code == LLM_LIMIT_REACHED))`; `nextProvider = policy.next(done, …)`; si vacío → WARN «Ciclo de IA agotado …» y `throw new AiCycleFailedException(failure, mark)`; si no → `mark = call.attemptSwitch().next(mark.failed(code, clock), done.size() + 1, nextProvider)`; `waiter.pause(done.size() == 1 ? firstWait : secondWait)`. La espera va **después** del cambio de intento (la fila nueva ya bloquea la operación) y fuera de transacción.

**Trampa:** el primer intento usa `firstMark` tal como la creó el caso de uso (con `numero_intento = 1` y `proveedor = gemini`); no se llama a `attemptSwitch` antes del primero.

**Pruebas** `AiCallRunnerTest` (puerto falso con guion por intento, `Waiter` falso que registra esperas y afirma que no hay transacción activa, `AttemptSwitch` falso que registra llamadas): `run_shouldReturnFirst_whenValid` (0 esperas, 0 cambios); `run_shouldSucceedOnSecond` (esperas `[500 ms]`, 1 cambio a intento 2 con `PRIMARY`); `run_shouldUseFallbackOnThird` (esperas `[500 ms, 1000 ms]`, cambios a 2 `PRIMARY` y 3 `FALLBACK`); `run_shouldSkipPrimary_after429`; `run_shouldStop_whenBothLimited` (2 intentos, causa `LLM_LIMIT_REACHED`); `run_shouldUsePrimaryOnThird_whenFallbackUnavailable`; `run_shouldThrowLastCause` (causas `UNAVAILABLE`, `UNAVAILABLE`, `INVALID_OUTPUT` → `INVALID_OUTPUT`); `run_shouldCountReaderRejectionAsFailure` (el lector lanza `LlmInvalidOutputException`); `run_shouldWrapUnexpected` (el puerto lanza `IllegalStateException` → `LLM_UNAVAILABLE`); `run_shouldWaitOutsideTransaction`; `run_shouldThrowDatabaseUnavailable_whenSwitchFails` (el `AttemptSwitch` lanza `DatabaseUnavailableException`: se propaga tal cual, sin envolver); `run_shouldRespectWorstCase` (con un puerto que «tarda» lo que dice un reloj simulado: 3 × 3 s + 1,5 s = 10,5 s y 3 × 20 s + 1,5 s = 61,5 s, ambos < 120 s).

**Verifica:** `-Dtest='AiCallRunnerTest,LanguageModelPromptTest,LayeredArchitectureTest,UntypedExceptionClassificationTest'`.

## T3 · Respaldo en Groq, tiempo por operación y configuración (infraestructura)

Usa las firmas exactas que T0 dejó en su informe.

**Modificar** `pom.xml`: la dependencia de T0. `application.properties`: la propiedad de T0 que deja activo solo el modelo de chat de Gemini, y:

```properties
cameia.llm.evaluation-timeout=${LLM_EVALUATION_TIMEOUT:3000ms}
cameia.llm.retry.first-wait=${LLM_RETRY_FIRST_WAIT:500ms}
cameia.llm.retry.second-wait=${LLM_RETRY_SECOND_WAIT:1000ms}
cameia.llm.fallback.api-key=${GROQ_API_KEY:}
cameia.llm.fallback.model=${LLM_FALLBACK_MODEL:qwen/qwen3.8-27b}
cameia.llm.fallback.base-url=${LLM_FALLBACK_BASE_URL:https://api.groq.com/openai}
```

**Modificar** `LanguageModelProperties`: componente `Duration evaluationTimeout` con `@NotNull(message = "cameia.llm.evaluation-timeout (LLM_EVALUATION_TIMEOUT) es obligatorio")` y `@AssertTrue isEvaluationTimeoutPositive()` («… debe ser mayor que cero»); `toString` lo incluye. Actualiza `WithLanguageModelProperties` y toda prueba que construya el record.

**Crear** `AiRetryProperties` (`@ConfigurationProperties("cameia.llm.retry")`, `@Validated`, record `firstWait`, `secondWait`; `@NotNull` y `@AssertTrue` para `firstWait >= 0` y `secondWait >= firstWait` con los mensajes de la spec §8) y `FallbackModelProperties` (`cameia.llm.fallback`: `apiKey` sin `@NotBlank`, `model` `@NotBlank`, `baseUrl` `@NotBlank` y `@AssertTrue` que empiece por `https://` o `http://localhost`; `toString` con `apiKey=****`; método `boolean hasKey()`).

**Modificar** `LanguageModelConfiguration`: dos `Client` de Google (uno con `timeout`, otro con `evaluationTimeout`, ambos con 1 intento del SDK y la `base-url` opcional como hoy) y dos `ChatModel` de Gemini armados a mano con esas firmas de T0; dos `ChatModel` de OpenAI para Groq (tiempo de lectura de cada operación, `baseUrl` y `apiKey` de `FallbackModelProperties`), creados solo si `hasKey()`. Ninguno se publica como bean de tipo `ChatModel` (para no chocar con la autoconfiguración): expón un bean `GeminiChatModels(ChatModel planning, ChatModel evaluation)` y otro `Optional`-like `GroqChatModels` (record con los dos o vacío). Si `!hasKey()`, `log.warn("Proveedor de respaldo sin llave: el tercer intento usará el principal")` una sola vez.

**Modificar** `GeminiLanguageModelAdapter`: deja de implementar `LanguageModelPort` (pasa a ser un componente de paquete con `LanguageModelResponse generate(LanguageModelPrompt prompt)`), elige el `ChatModel` por `prompt.operation()`. El registro de la llamada agrega `proveedor=gemini` y `operacion`.

**Crear** `GroqLanguageModelAdapter` (paquete `infrastructure.ia`, de paquete): `generate(LanguageModelPrompt prompt)` con opciones `model`, tope de salida, `temperature` = la de `cameia.llm.temperature`, `reasoningEffort = "none"`, y, si `prompt.jsonSchema() != null`, `responseFormat` `JSON_SCHEMA` estricto con nombre `"respuesta"` y el esquema; traduce: 429 (busca el código en la cadena de causas, como el adaptador de Gemini con su `ApiException`; T0 dice qué excepción lanza el cliente de OpenAI) → `LlmLimitReachedException`; todo otro `RuntimeException` → `LlmUnavailableException`; texto vacío o `choices` vacío → `LlmUnavailableException`. Registro: «Llamada al modelo [proveedor=groq, operacion={}, resultado={}, duracionMs={}]», nunca la llave ni el cuerpo.

**Crear** `RoutingLanguageModelAdapter` (`@Component`, implementa `LanguageModelPort`): `generate(prompt, PRIMARY)` → Gemini; `FALLBACK` → Groq si hay llave, si no `IllegalStateException("Respaldo no disponible")` (nunca ocurre: la política no lo elige); `isAvailable(PRIMARY) = true`, `isAvailable(FALLBACK) = groq presente`.

**Pruebas:** `GroqLanguageModelAdapterTest` (servidor `com.sun.net.httpserver.HttpServer` local como en `GeminiLanguageModelAdapterTest`): `generate_shouldSendSchemaAndReasoningNone` (lee el cuerpo recibido: `model = qwen/qwen3.8-27b`, `reasoning_effort = none`, `response_format.type = json_schema`, `strict = true`, el texto del prompt igual); `generate_shouldReturnText`; `generate_shouldThrowLimit_when429`; `generate_shouldThrowUnavailable_when500`, `…_whenEmptyChoices`, `…_when401`; `generate_shouldTranslateAnyFailure`; `logs_shouldNotContainKey` (llave `clave-de-prueba-secreta` no aparece en ningún evento). `GeminiLanguageModelAdapterTimeoutTest`: `generate_shouldAccept_whenAnswerBeforeTimeout` (servidor tarda 2900 ms, `evaluationTimeout` 3000 ms), `generate_shouldFailUnavailable_whenTimeout` (tarda 3300 ms → `LlmUnavailableException` y la llamada vuelve en < 3300 ms medidos con `System.nanoTime`), `generate_shouldUsePlanningTimeout_forPlan` (tiempos escalados 1900/2000 ms) y `generate_shouldAcceptPlanning_whenAnswerAt19900ms` (literal del CA-5.6.6, D-224-4: servidor que responde a los 19.900 ms, `cameia.llm.timeout` 20.000 ms → respuesta aceptada en un intento). `RoutingLanguageModelAdapterTest`. `LanguageModelPropertiesTest`, `AiRetryPropertiesTest`, `FallbackModelPropertiesTest` (spec §8 fila por fila; `toString_shouldMaskKey`). `FallbackAvailabilityTest.context_shouldStart_withoutFallbackKey` (contexto con `cameia.llm.fallback.api-key=` vacío: arranca, `isAvailable(FALLBACK) = false`, WARN exactamente una vez). `LanguageModelConfigurationTest.context_shouldHaveOneChatModelPerProviderAndOperation`. `GeminiLanguageModelAdapterTest` y `…DefensesTest` en verde.

**Verifica:** `-Dtest='Groq*Test,GeminiLanguageModelAdapter*Test,RoutingLanguageModelAdapterTest,LanguageModelPropertiesTest,AiRetryPropertiesTest,FallbackModelPropertiesTest,FallbackAvailabilityTest,LanguageModelConfigurationTest,ApiDocsFlagTest'`.

**Foco:** la medición de < 3300 ms prueba que el corte es real; si la prueba de 2900 ms falla de forma intermitente, repítela 5 veces y reporta los tiempos medidos, sin cambiar los valores.

## T4 · Planificar y evaluar con el ciclo

**Esquemas:** el de evaluación ya existe (CM-216). Crea `R/prompts/esquema.planificacion.v6.json` escrito **solo** con lo que exigen `PlanOutputReader` y `PlanValidator` (nombres de campos, tipos, cantidad de preguntas, enumerados). Si un campo que exigen no se puede expresar en JSON Schema estricto de Groq, detente y reporta. El de planificación **no** se pone en `LanguageModelPrompt.jsonSchema` (Gemini planifica sin esquema, como se midió): `PlanningPromptBuilder` lo expone aparte (`PlanningPrompt.fallbackSchema()`) y `StartSessionService` lo pone en el prompt solo para el intento con `FALLBACK` (`prompt.withSchema(schema)`, método nuevo del record).

**Modificar** `StartSessionService.plan`: reemplaza la llamada única por `runner.run(new AiCall<>(prompt.prompt(), response -> validator.validate(language, reader.read(response.text())), mark, switchPlanning(session)))`. `switchPlanning` hace en `transaction.execute`: `executions.save(failed)` (ya con `FALLIDA`), y `executions.save(AiExecution.planningStarted(sessionId, attempt, modelOf(provider), promptVersion, provider.code(), clock))`. `modelOf(PRIMARY) = cameia.llm.model`; `modelOf(FALLBACK) = cameia.llm.fallback.model`. En `reserve`, la primera marca usa `numero_intento = 1` (no `retryPolicy.attemptNumber`). Borra `PlanningRetryPolicy.attemptNumber` y **solo** el método de prueba que lo prueba en `PlanningRetryPolicyTest` (autorizado por Paula el 11-nov); ninguna otra prueba se borra. `AiCycleFailedException` → `failPlanning(session, e.lastMark(), reservation, e.cause().getErrorCode(), e.cause())` (el método existente). `finish` usa `success.mark()` y `success.response()`.

**Modificar** `TurnEvaluator.evaluate`: igual, con `switchEvaluation(job)`. Para no pasar de 3 parámetros, crea en `M/domain/model/` `record EvaluationAttempt(UUID sessionId, UUID turnId, int number)` y `record ExecutionSource(String model, String promptVersion, String provider)`, y reemplaza la fábrica de CM-216 por `AiExecution.evaluationStarted(EvaluationAttempt attempt, ExecutionSource source, Clock clock)` (actualiza sus dos usos de CM-216: `RegisterAnswerService` y `ReprocessTurnService`, con `number = 1`). `planningStarted` no cambia de firma (ya existía con 6 parámetros). En fallo, `fail(job, e.lastMark(), code)` y relanza `e.cause()`.

**Modificar** tiempos: `EvaluationStaleness` se construye con `cameia.llm.evaluation-timeout` en `ReprocessTurnService`, `GetSessionStateService` y `RegisterAnswerService` (su comprobación de evaluación caducada al repetir la clave, REQ-ANS-11 de CM-216); `RegisterAnswerService.awaitResult` usa como plazo final `3 × evaluation-timeout + first-wait + second-wait + PlanningClaim.MARGIN`.

**Pruebas:** `StartSessionServiceTest` (+`start_shouldOpenFirstAttemptWithNumberOne`, +`start_shouldSwitchAttemptInOneTransaction`, +`start_shouldFailWithLastCause_whenCycleFails`, +`start_shouldStoreFallbackProviderAndModel`); `TurnEvaluatorTest` (+las mismas cuatro para evaluar); `RegisterAnswerServiceTest` (+`register_shouldWaitWholeCycleBudget`: 25,5 s); `AiRetryPersistenceTest.cycle_shouldKeepOneInProgressRow` y `cycle_shouldNeverLeaveGap` (Testcontainers: un segundo hilo intenta insertar su `EN_CURSO` durante el cambio; siempre ve una fila viva). Todas las de CM-212, CM-216 y CM-220 en verde.

**Verifica:** `-Dtest='StartSessionServiceTest,TurnEvaluatorTest,RegisterAnswerServiceTest,ReprocessTurnServiceTest,GetSessionStateServiceTest,AiRetryPersistenceTest,PlanningPersistenceTest,SessionLifecycleEndToEndTest,Answer*Test,Reprocess*Test,Closure*Test'`.

## T5 · Punta a punta

**Crear** `T/presentation/AiRetryEndToEndTest.java` (base de `SessionLifecycleEndToEndTest`; el modelo guionado responde por intento y proveedor; `Waiter` de prueba sin dormir): todos los nombres de la spec §6, comprobando con SQL las filas de `ejecucion_ia` (`numero_intento`, `proveedor`, `estado`, `error_codigo`, `idempotency_key` distintas). `AiRetryLogContentTest`: ningún evento contiene el prompt, la salida, la llave ni el texto de la persona.

**Verifica:** `-Dtest='AiRetryEndToEndTest,AiRetryLogContentTest,ReprocessConcurrencyTest,AnswerConcurrencyTest'`.

## T6 · Documentación, variables y Postman

- `.env.example`: `GROQ_API_KEY=` (vacía), `LLM_EVALUATION_TIMEOUT=3000ms`, `LLM_RETRY_FIRST_WAIT=500ms`, `LLM_RETRY_SECOND_WAIT=1000ms`, `LLM_FALLBACK_MODEL=qwen/qwen3.8-27b`, `LLM_FALLBACK_BASE_URL=https://api.groq.com/openai`, cada una con su comentario en español como las existentes.
- `CLAUDE.md` del repo, fila de la línea 137: el respaldo es Qwen 3.8 27B en Groq, implementado en `GroqLanguageModelAdapter` detrás de `RoutingLanguageModelAdapter`; sin «Kimi».
- `docs/errores.md`: columna «Origen» de `LLM_*`.
- `docs/adr/0014-reintentos-y-respaldo-de-la-ia.md` (D-224-1 a D-224-3, tabla de proveedores, tiempos, alternativas) y `docs/bitacora-ia/13_reintentos-ia_prompt.md`.
- Postman y simulador: spec §15.
- OpenAPI: en la descripción de cada 503 de `start`, `answers` y `reprocess`: «tras hasta 3 intentos (2 con el proveedor principal y 1 con el de respaldo); `code` = causa del último».

**Verifica:** Newman sobre el JAR con el simulador → 0 fallos.

## T7 · Verificación final (sin commit)

`./mvnw.cmd -B -ntp clean verify` sobre la punta (log en `ejecucion/evidencia/CM-224/verify.log` de la carpeta `Desarrollo`); cobertura ≥ 90 % líneas y ramas en `AiRetryPolicy`, `AiProvider`, `AiCallRunner`, `GroqLanguageModelAdapter`, `RoutingLanguageModelAdapter`, `GeminiLanguageModelAdapter`, `LanguageModelConfiguration`, las tres clases de propiedades y las líneas nuevas de `StartSessionService` y `TurnEvaluator`; Newman en `ejecucion/evidencia/CM-224/newman.xml`; borrador del pedido a DevOps para CM-225 (variables y llave) en `comunicaciones/`, si el documento único de DevOps aún no lo trae (revisar `comunicaciones/README.md` §índice antes).
