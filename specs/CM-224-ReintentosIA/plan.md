# Plan · CM-224 · Reintentos automáticos de la IA con respaldo

Base: punta de `CM-220-cerrar-sesion` (stack sobre `origin/develop` = `09bb7ff`, CM-216 y CM-220). Spec: `spec.md`. Tarjetas: `tasks.md`.

## 0. Foco de revisión

| # | Situación | Prueba que la vigila |
|---|---|---|
| 1 | Hueco entre cerrar el intento k y abrir el k+1 (un `start` o `reprocess` concurrente toma la operación) | `AiRetryPersistenceTest.cycle_shouldNeverLeaveGap` (T4) |
| 2 | Un 429 repetido con el mismo proveedor | `AiRetryPolicyTest` (11 filas de la spec §7) (T1) |
| 3 | El tiempo de 3 s no corta de verdad la llamada (se espera los 20 s del cliente compartido) | `GeminiLanguageModelAdapterTimeoutTest.generate_shouldFailUnavailable_whenTimeout` mide < 3300 ms (T3) |
| 4 | Dos `ChatModel` en el contexto → Spring no sabe cuál inyectar | `LanguageModelConfigurationTest.context_shouldHaveOneChatModelPerProviderAndOperation` (T3) |
| 5 | El 503 lleva la causa del primer intento y no la del último | `AiCallRunnerTest.run_shouldThrowLastCause` (T2) |
| 6 | La espera entre intentos con transacción abierta | `AiCallRunnerTest.run_shouldWaitOutsideTransaction` (T2) |
| 7 | La llave de Groq en un registro | `GroqLanguageModelAdapterTest.logs_shouldNotContainKey`, `FallbackModelPropertiesTest.toString_shouldMaskKey` (T3) |
| 8 | Sin llave el servicio no arranca | `FallbackAvailabilityTest.context_shouldStart_withoutFallbackKey` (T3) |

## 1. Enfoque

1. **Dominio:** `AiProvider` (`PRIMARY`, `FALLBACK`) y `AiRetryPolicy` (tabla de la spec §7, sin Spring).
2. **Puerto:** `LanguageModelPort` gana `generate(LanguageModelPrompt prompt, AiProvider provider)` y `isAvailable(AiProvider)`; `LanguageModelPrompt` gana `AiOperation operation` (el `jsonSchema` ya lo trae CM-216). El método de un argumento se elimina y se actualizan sus usos (dos: `StartSessionService` y `TurnEvaluator`).
3. **Aplicación:** `AiCallRunner` ejecuta el ciclo (proveedor, espera, cambio de intento en una transacción con un `AttemptSwitch` que da el que llama).
4. **Infraestructura:** `RoutingLanguageModelAdapter` (el único `LanguageModelPort`) delega en `GeminiLanguageModelAdapter` (ahora con un `ChatModel` por operación) y `GroqLanguageModelAdapter` (nuevo, `ChatModel` de OpenAI armado a mano).
5. **Configuración:** `LanguageModelProperties` (+`evaluationTimeout`), `AiRetryProperties` (`cameia.llm.retry`), `FallbackModelProperties` (`cameia.llm.fallback`).

## 2. Archivos

| Capa | Crear | Modificar |
|---|---|---|
| Dependencia | — | `pom.xml` (+`spring-ai-starter-model-openai`, versión del BOM); `application.properties` (+`spring.ai.model.chat=google-genai` u otra propiedad que T0 confirme, + propiedades de la spec REQ-RTY-11) |
| Dominio | `M/domain/model/AiProvider.java`, `M/domain/policy/AiRetryPolicy.java` | `LanguageModelPrompt.java`, `LanguageModelPort.java`, `EvaluationStaleness` (sin cambio de firma; cambia el valor que recibe) |
| Aplicación | `M/application/service/AiCallRunner.java`, `M/application/service/AttemptSwitch.java` | `StartSessionService.java`, `TurnEvaluator.java`, `RegisterAnswerService.java` (presupuesto de espera), `ReprocessTurnService.java` y `GetSessionStateService.java` (tiempo de evaluación), `PlanningPromptBuilder.java` y `EvaluationPromptBuilder.java` (operación y esquema), `PlanningRetryPolicy.java` (se borra `attemptNumber`, que queda sin uso; ver §3) |
| Infraestructura | `M/infrastructure/ia/{RoutingLanguageModelAdapter,GroqLanguageModelAdapter}.java`, `M/infrastructure/config/ia/{AiRetryProperties,FallbackModelProperties}.java`, `R/prompts/esquema.planificacion.v6.json` (el de evaluación, `esquema.evaluacion.v1.json`, ya lo crea CM-216: no se toca) | `GeminiLanguageModelAdapter.java`, `LanguageModelConfiguration.java`, `LanguageModelProperties.java` |
| Pruebas | ver spec §14 | `WithLanguageModelProperties`, `ScriptedModelConfiguration` de las pruebas punta a punta, `StartSessionServiceTest`, `TurnEvaluatorTest`, `PlanningRetryPolicyTest` |
| Documentos | `docs/adr/0014-reintentos-y-respaldo-de-la-ia.md`, `docs/bitacora-ia/13_reintentos-ia_prompt.md` | `.env.example`, `CLAUDE.md` del repo (§ respaldo), `docs/errores.md`, Postman y simulador |

**No se tocan:** `.github/` (DevOps), migraciones, textos de prompts existentes.

## 3. Decisiones técnicas

| Decisión | Razón | Descartado |
|---|---|---|
| Un `ChatModel` por proveedor y operación (4) | el tiempo vive en el cliente HTTP; cortar de verdad la llamada (CA-5.6.8) | un solo cliente con tiempo largo y corte con `CompletableFuture` (no cancela la conexión) |
| `numero_intento` = intento del ciclo; los reintentos manuales en `sesion.reintentos_planificacion` y `turno.reintentos_manuales` | definición del libro (H-5) | seguir contando reintentos manuales en `numero_intento` |
| Se borra `PlanningRetryPolicy.attemptNumber` y **solo** su prueba (`PlanningRetryPolicyTest`, el método que lo prueba); se agrega `StartSessionServiceTest.start_shouldOpenFirstAttemptWithNumberOne` | queda sin uso; Paula autorizó borrar esa prueba (11-nov) | dejarlo como código muerto |
| Esquema de evaluación a los dos proveedores; el de planificación solo a Groq | así se midió cada operación (H-8) | esquema de planificación también en Gemini (cambiaría lo medido en CM-212) |
| Cambio de intento con un `AttemptSwitch` que recibe el que llama | `AiCallRunner` no conoce sesiones ni turnos; cada caso de uso cierra y abre sus filas en su transacción | que el runner escriba en `ejecucion_ia` directamente (mezcla responsabilidades) |

## 4. Orden y commits

| Commit | Tarjetas | Mensaje |
|---|---|---|
| — | T0 | verificación previa (sin commit; informe en el chat) |
| 1 | T1 | `CM-224 \| feat(dominio): proveedor y política de reintentos de la IA` |
| 2 | T2 | `CM-224 \| feat(aplicacion): ciclo de hasta tres intentos con espera creciente` |
| 3 | T3 | `CM-224 \| feat(ia): proveedor de respaldo en Groq y tiempo por operación` |
| 4 | T4 | `CM-224 \| feat(aplicacion): planificar y evaluar con el ciclo de reintentos` |
| 5 | T5 | `CM-224 \| test(entrevista): reintentos, respaldo y tiempos de punta a punta` |
| 6 | T6 | `CM-224 \| docs(entrevista): ADR, variables, catálogo de errores y Postman` |

T7: verificación final. Diff estimado: unas 1.800 líneas (mitad pruebas).

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| API de Spring AI 2.0.2 para OpenAI distinta de lo supuesto | T0 antes de todo; si no cuadra, se detiene y decide Paula |
| Pruebas de tiempo frágiles en Windows (2,9 s contra 3 s) | margen medido; si falla de forma intermitente, se reporta sin cambiar los valores del CA |
| Sin llave de Groq en el entorno | D-224-3; CM-225 abierta con Juan Diego |
| Cambiar el puerto rompe pruebas de CM-212/216/220 | T4 corre todas las de planificación, respuesta y cierre |

## 6. Autorrevisión

1. REQ-RTY-01 a 12 y CA-5.6.1 a 5.6.14 con `B`: tarjeta en `tasks.md` §0. Sí.
2. Firmas y valores literales en cada tarjeta. Sí.
3. Nombres iguales en spec, plan y tarjetas (`AiProvider`, `AiRetryPolicy`, `AiCallRunner`, `AttemptSwitch`, `RoutingLanguageModelAdapter`, `GroqLanguageModelAdapter`, `AiRetryProperties`, `FallbackModelProperties`). Revisado.
4. Foco con prueba. Sí.
5. Sin códigos nuevos: cada fallo del cliente de Groq se traduce a `LLM_*`. Sí.
