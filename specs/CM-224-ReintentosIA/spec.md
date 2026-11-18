# CM-224 · Reintentos automáticos de la IA con proveedor de respaldo (HU-5.6)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-224 «HU-5.6 – Backend» (subtarea, Por hacer, Paula Andrea Muñoz Delgado, Sprint 5). Historia CM-303 (6,5 puntos, Por hacer, movida al Sprint 5 el 11-nov). Relacionadas: CM-225 (DevOps: llave y variables, Juan Diego Gómez Garcés), CM-226 (Frontend) |
| Repositorio | `cameia-entrevista`, base `origin/develop` = `09bb7ff` más CM-216 y CM-220 (stack, §16) |
| Backlog | v11.3 (`informacion/P2/11112026_03_Backlog_v11_3.xlsx`), hoja HE-05, HU-5.6, CA-5.6.1 a CA-5.6.14 |
| Estado | **LISTA PARA EJECUTAR**, con una verificación previa de la API de Spring AI (T0) y CA-5.6.13 **PENDIENTE de probar en el entorno** hasta que DevOps cargue la llave de Groq (CM-225) |
| Decisiones | D-224-1 a D-224-4 (Paula, 11-nov-2026) |
| Migración | Ninguna (§9) |
| Rama | `CM-224-reintentos-ia`, sale de la punta de `CM-220-cerrar-sesion` |

## 1. Objetivo

1. Toda llamada a la IA de **planificar** (`start`) y de **evaluar** (`answers`, `reprocess`) se hace en un **ciclo de hasta 3 intentos**: los 2 primeros con el proveedor principal (Gemini) y el 3.º con el de respaldo (Qwen en Groq), con espera creciente (500 ms y 1 s).
2. Un 429 (límite de uso) no se repite con el mismo proveedor: los intentos que quedan van al otro; si ningún proveedor queda disponible, el ciclo termina.
3. Cada operación tiene su tiempo máximo por llamada: planificar 20 s (`LLM_TIMEOUT`, existente) y evaluar 3 s (`LLM_EVALUATION_TIMEOUT`, nuevo).
4. Cada intento deja su `ejecucion_ia` (`numero_intento` 1 a 3, su `idempotency_key`, su proveedor y modelo). Agotado el ciclo, la API responde 503 con el `code` de la causa del último intento; el turno queda `RETRYING` o la sesión `CONFIGURADA`, igual que con un intento.

## 2. Hechos comprobados

| # | Hecho | Ancla |
|---|---|---|
| F-1 | `LanguageModelPort.generate(LanguageModelPrompt)`: una llamada; `GeminiLanguageModelAdapter` usa el `ChatModel` de Spring AI (starter `spring-ai-starter-model-google-genai`, Spring AI 2.0.2) sobre un `Client` de Google con `timeout` = `cameia.llm.timeout` y 1 intento del SDK; 429 → `LlmLimitReachedException`, lo demás → `LlmUnavailableException` | `infrastructure/ia/GeminiLanguageModelAdapter.java`, `infrastructure/config/ia/LanguageModelConfiguration.java` (`09bb7ff`) |
| F-2 | Propiedades: `cameia.llm.api-key=${GOOGLE_GENAI_API_KEY:}`, `model=${LLM_MODEL:}`, `timeout=${LLM_TIMEOUT:20000ms}`, `temperature=1.0`, `thinking-level=minimal`; `LanguageModelProperties` valida al arrancar | `application.properties:34-38`, `LanguageModelProperties.java` |
| F-3 | `StartSessionService.plan` hace un intento: reserva (`AiExecution.planningStarted` con `numero_intento = PlanningRetryPolicy.attemptNumber`, que cuenta los reintentos manuales), llamada, `PlanValidator`, cierre; `closeStaleMark` con `PlanningClaim` (`timeout + 15 s`) | `StartSessionService.java:125-215` |
| F-4 | `ejecucion_ia.numero_intento BETWEEN 1 AND 3`, `idempotency_key uuid UNIQUE`, `uq_ejecucion_ia_planificacion_en_curso` (V6) y `uq_ejecucion_ia_evaluacion_en_curso` (V8 de CM-216): una sola fila `EN_CURSO` por sesión al planificar y por turno al evaluar | `V1`, `V6`, `V8` |
| F-5 | CM-216 deja `TurnEvaluator` con una llamada y `EvaluationStaleness` con `cameia.llm.timeout + 15 s`; `RegisterAnswerService` espera la clave en proceso hasta `cameia.llm.timeout + 15 s` | `specs/CM-216-RegistrarRespuesta/tasks.md` T4, T7, T8 |
| F-6 | El spike de Groq en pruebas (`spike/planificacion/QwenGroq.java`) llama `https://api.groq.com/openai/v1/chat/completions` con `model = qwen/qwen3.8-27b` y `reasoning_effort` | `src/test/.../spike/planificacion/QwenGroq.java:20-64` |
| F-7 | `.env.example` tiene `GOOGLE_GENAI_API_KEY`, `LLM_MODEL`, `LLM_TIMEOUT`; ninguna variable de Groq | `.env.example:100-109` |
| F-8 | `CLAUDE.md` del repo (línea 137) dice «respaldo a Kimi»; el respaldo vigente es Qwen en Groq (CA-5.6.13) | `CLAUDE.md:137` |
| F-9 | La espera de Gateway es 120 s y Frontend espera 130 s en `start`, `answers`, `reprocess` y `consolidation` | decisión del 11-nov (`ESTADO.md`) |

## 3. Hallazgos clasificados

| # | Hallazgo | Clase | Destino |
|---|---|---|---|
| H-1 | Jira (descripción de CM-224) dice planificación 9,5 s; el libro v11.3 dice 20 s (decisión del 10-nov) | Cambio sin alternativa: manda el libro | Paula ajusta la descripción si quiere; no se toca Jira |
| H-2 | Cómo llamar a Groq | Decisión de Paula | D-224-1 |
| H-3 | Esperas entre intentos sin valores en el libro («creciente») | Decisión de Paula | D-224-2 |
| H-4 | Qué hacer si falta la llave de Groq en un entorno | Decisión de Paula | D-224-3 |
| H-5 | `numero_intento` hoy cuenta los reintentos manuales de la planificación (F-3); el libro lo define como intento técnico dentro de la operación (1 a 3) | Cambio sin alternativa | REQ-RTY-05: `numero_intento` = intento del ciclo; los reintentos manuales siguen en `sesion.reintentos_planificacion` |
| H-6 | Las variables nuevas y la llave deben llegar al despliegue (`.github/`, Secret Manager): es de DevOps | Acción | CM-225 (Juan Diego Gómez Garcés): `GROQ_API_KEY` en Secret Manager, `LLM_EVALUATION_TIMEOUT=3000ms` y, si se quieren distintas a las de fábrica, `LLM_RETRY_FIRST_WAIT`, `LLM_RETRY_SECOND_WAIT`, `LLM_FALLBACK_MODEL` |
| H-7 | El esquema JSON estricto de Groq exige un esquema por operación; el de planificación no existe como archivo | Cambio sin alternativa | Se escribe a partir de lo que ya exigen `PlanOutputReader` y `PlanValidator` (no se inventan campos); si no cuadra, la tarjeta se detiene |
| H-8 | La evaluación ya lleva esquema JSON en Gemini desde CM-216 (como se midió en el spike CM-261); la planificación no lleva esquema en Gemini (así se midió y así está hoy) | Hecho | El esquema de evaluación viaja a los dos proveedores; el de planificación solo a Groq |
| H-9 | `CLAUDE.md` del repo desactualizado (F-8) | Corrección de documento | T8 lo corrige |
| H-10 | La matriz ASVS (2.3.1) y el monitoreo de cupo de Groq no son de esta tarea | Acción | Avisar a DevOps con la entrega de la ola (ver CM-216 H-15) |

## 4. Decisiones (Paula, 11-nov-2026)

| # | Asunto | Decisión | Alternativas descartadas |
|---|---|---|---|
| D-224-1 | Cliente de Groq | Starter `spring-ai-starter-model-openai` (Spring AI 2.0.2, el BOM ya está en el `pom.xml`); el `ChatModel` de Groq se arma a mano (sin la autoconfiguración de OpenAI) para no chocar con el de Gemini; URL `https://api.groq.com/openai`, modelo `qwen/qwen3.8-27b`, `reasoning_effort: none`, respuesta con `json_schema` estricto | Cliente HTTP propio como el del spike (más código que mantener); otro proveedor |
| D-224-2 | Esperas entre intentos | 500 ms antes del 2.º y 1 s antes del 3.º, configurables. Peor caso: evaluar ≈ 10,5 s (3 × 3 s + 1,5 s); planificar ≈ 61,5 s (3 × 20 s + 1,5 s), dentro de los 120 s del Gateway | 1 s y 2 s (más espera sin beneficio medido) |
| D-224-3 | Sin llave de Groq | El servicio arranca; registra un WARN una sola vez al arrancar («Proveedor de respaldo sin llave: el tercer intento usará el principal»); el 3.º intento usa Gemini. CA-5.6.13 queda PENDIENTE de probar en el entorno hasta que DevOps cargue la llave (CM-225) | No arrancar sin la llave (bloquearía el despliegue hasta CM-225) |
| D-224-4 | Prueba de la planificación justo antes del tiempo máximo (CA-5.6.6, columna técnica: «resultado válido a los 19,9 s») | **Prueba literal**: servidor local que responde a los 19.900 ms con corte de 20.000 ms, además de la versión escalada (1.900/2.000 ms). Cumple el CA al pie de la letra; la suite tarda unos 20 s más | Solo la escalada (más rápida, pero distinta del libro) |
| D-224-5 | Datos que recibe el respaldo en Groq (decisión de Paula, 12-nov-2026) | Se aprueba con condiciones: retención cero activa en la cuenta de la llave de despliegue, addendum de procesamiento de datos de Groq aceptado, entrenamiento prohibido por contrato (§4.2 de su acuerdo de servicios), llaves y proyectos de Groq distintos para pruebas locales y despliegue, y la llave no se carga en Secret Manager hasta tener ambas confirmaciones por escrito en la tarea CM-225. Detalle en el ADR 0014 | Enviar el respaldo sin contexto personal (cambia código y baja la calidad de la evaluación); no usar el respaldo (13 % de las llamadas a Gemini pasan de 3 s) |

## 5. Requisitos (EARS)

- **REQ-RTY-01 (ciclo).** Cuando se pida a la IA planificar o evaluar, el sistema debe hacer hasta 3 intentos. Intento 1: principal. Intento 2: principal, salvo que el principal haya devuelto 429 (entonces respaldo). Intento 3: respaldo, salvo que el respaldo no esté disponible (sin llave, D-224-3) o haya devuelto 429; en ese caso principal, salvo que el principal haya devuelto 429. Si para el intento siguiente no queda proveedor, el ciclo termina antes del 3.º.
- **REQ-RTY-02 (fallo de intento).** Cuentan como intento fallido: tiempo agotado, error del proveedor (5xx, red), 429, respuesta vacía, salida que no cumple el esquema o que `PlanValidator` / `EvaluationOutputReader` rechazan (`LLM_INVALID_OUTPUT`). Cualquier otra excepción no clasificada del adaptador se trata como `LLM_UNAVAILABLE` (como hoy en `StartSessionService`).
- **REQ-RTY-03 (esperas).** Antes del intento 2 el sistema debe esperar `cameia.llm.retry.first-wait` (500 ms) y antes del 3 `cameia.llm.retry.second-wait` (1 s), con el `Waiter` de CM-216, sin transacción abierta ni conexión tomada.
- **REQ-RTY-04 (tiempo por operación).** Cada llamada debe cancelarse al cumplir el tiempo de su operación: planificar `cameia.llm.timeout` (`LLM_TIMEOUT`, 20 s), evaluar `cameia.llm.evaluation-timeout` (`LLM_EVALUATION_TIMEOUT`, 3 s). Aplica a los dos proveedores. Una respuesta válida a los 2,9 s de una evaluación se acepta sin otro intento (CA-5.6.6).
- **REQ-RTY-05 (registro por intento).** Cada intento debe tener su `ejecucion_ia` con `numero_intento` = 1, 2 o 3 dentro del ciclo, una `idempotency_key` nueva, `proveedor` = `gemini` o `groq` y `modelo` = el modelo de ese proveedor. Al fallar el intento k y quedar otro, el sistema debe, en **una** transacción, cerrar k como `FALLIDA` con su `error_codigo` y crear k+1 `EN_CURSO` (sin hueco entre ambas: así ningún inicio o reintento concurrente toma la operación en medio). Un ciclo nuevo (otro `start` o `reprocess`) vuelve a empezar en 1.
- **REQ-RTY-06 (éxito).** Con el primer intento válido, el sistema debe seguir como hoy (CM-212 al planificar; CM-216 y CM-220 al evaluar), sin mostrar error, sin duplicar respuesta, turno ni pregunta (CA-5.6.1, 5.6.5, 5.6.7). El turno no pasa por `RETRYING` mientras el ciclo sigue.
- **REQ-RTY-07 (agotado).** Si el ciclo termina sin éxito, el sistema debe cerrar el último intento como `FALLIDA` y responder `503` con el `code` de la causa del **último** intento (`LLM_UNAVAILABLE`, `LLM_LIMIT_REACHED` o `LLM_INVALID_OUTPUT`) y «Ocurrió un error. Inténtalo de nuevo.». Evaluar: turno `RETRYING`, sesión `EN_CURSO`, respuesta intacta, sin cuarto intento (CA-5.6.2, 5.6.12). Planificar: sesión `CONFIGURADA` sin preguntas ni turnos (CA-5.6.4).
- **REQ-RTY-08 (reintento manual).** `reprocess` (CM-216) debe ejecutar un ciclo completo de hasta 3 intentos sobre la respuesta guardada, sin nuevo `intento_respuesta` (CA-5.6.3); los rechazos de CM-216 no cambian (409 `TURN_NOT_RETRYABLE`, 409 `TURN_RETRY_LIMIT_REACHED`, 404). `start` reintentado manualmente ejecuta también un ciclo completo; el tope de 2 reintentos manuales de la planificación no cambia.
- **REQ-RTY-09 (mismo pedido al respaldo).** El respaldo debe recibir el mismo texto de prompt y el mismo tope de salida que el principal, más el esquema JSON de la operación en `response_format` estricto (D-224-1, H-8).
- **REQ-RTY-10 (caducidad y espera).** La marca `EN_CURSO` de un intento se considera caída si pasan más de `tiempo de la operación + 15 s` desde su `fecha_inicio` (el margen cubre la espera de 1 s): `PlanningClaim` sin cambio; `EvaluationStaleness` pasa a usar `cameia.llm.evaluation-timeout`. La espera de la misma clave en `answers` (CM-216, REQ-ANS-11) pasa a `3 × evaluation-timeout + first-wait + second-wait + 15 s` (25,5 s con los valores de fábrica).
- **REQ-RTY-11 (configuración).** Propiedades nuevas, validadas al arrancar: `cameia.llm.evaluation-timeout=${LLM_EVALUATION_TIMEOUT:3000ms}` (> 0), `cameia.llm.retry.first-wait=${LLM_RETRY_FIRST_WAIT:500ms}` (≥ 0), `cameia.llm.retry.second-wait=${LLM_RETRY_SECOND_WAIT:1000ms}` (≥ first-wait), `cameia.llm.fallback.api-key=${GROQ_API_KEY:}` (puede estar vacía, D-224-3), `cameia.llm.fallback.model=${LLM_FALLBACK_MODEL:qwen/qwen3.8-27b}`, `cameia.llm.fallback.base-url=${LLM_FALLBACK_BASE_URL:https://api.groq.com/openai}`. Un valor inválido impide arrancar con un mensaje que nombra la variable (patrón de `LanguageModelProperties`). La llave nunca se registra (`toString` con `****`).
- **REQ-RTY-12 (registro).** Por intento: INFO «Intento de IA [operacion={}, intento={}, proveedor={}, resultado={}, duracionMs={}]»; al agotar: WARN «Ciclo de IA agotado [operacion={}, intentos={}, code={}]». Nunca el prompt, la respuesta del modelo, la llave ni el texto de la persona.

## 6. Criterios de aceptación → requisito → prueba → Postman

| CA | Dueño | Requisito | Prueba | Postman (carpeta «Reintentos de la IA», simulador) |
|---|---|---|---|---|
| CA-5.6.1 | B | 01, 02, 05, 06 | `AiRetryEndToEndTest.answer_shouldSucceedOnSecondAttempt` (parametrizada por causa del 1.º: tiempo agotado, 500, 429 → 2.º al respaldo, JSON inválido); afirma 2 `ejecucion_ia` con `numero_intento` 1 y 2 y claves distintas, 1 `intento_respuesta` | «200 evaluación al 2.º intento» |
| CA-5.6.2 | F y B | 07 | `AiRetryEndToEndTest.answer_shouldReturn503WithLastCause_whenThreeFail` (causa del 3.º: `LLM_UNAVAILABLE`, `LLM_INVALID_OUTPUT`); 3 filas; turno `RETRYING`; sesión `EN_CURSO` | «503 tres intentos fallidos» |
| CA-5.6.3 | F y B | 08 | `AiRetryEndToEndTest.reprocess_shouldRunNewCycle` (falla 1, éxito 2; contador de reintentos 1; 0 `intento_respuesta` nuevos) | «200 reintentar con ciclo» |
| CA-5.6.4 | F y B | 07 | `AiRetryEndToEndTest.start_shouldReturn503AndStayConfigured_whenThreeFail` | «503 planificación agotada» |
| CA-5.6.5 | B | 06 | `AiRetryEndToEndTest.start_shouldStartOnSecondAttempt` (2 filas `PLANIFICAR`, 1 y 2) | «200 inicio al 2.º intento» |
| CA-5.6.6 | B | 04 | `GeminiLanguageModelAdapterTimeoutTest.generate_shouldAccept_whenAnswerBeforeTimeout` (servidor local que tarda 2900 ms, tiempo 3000 ms) y la de planificación literal del CA (D-224-4): `GeminiLanguageModelAdapterTimeoutTest.generate_shouldAcceptPlanning_whenAnswerAt19900ms` (tarda 19.900 ms, tiempo 20.000 ms), más la escalada `…_shouldUsePlanningTimeout_forPlan` (1.900/2.000 ms) | — |
| CA-5.6.7 | B | 01, 06 | `AiRetryEndToEndTest.answer_shouldSucceedOnThirdAttemptWithFallback` (3.ª fila `proveedor = groq`; el turno nunca pasó por `RETRYING`) | «200 evaluación con respaldo» |
| CA-5.6.8 | B | 03, 04 | `GeminiLanguageModelAdapterTimeoutTest.generate_shouldFailUnavailable_whenTimeout` (tarda 3300 ms, tiempo 3000 ms → `LLM_UNAVAILABLE`, la llamada vuelve antes de 3300 ms); `AiCallRunnerTest.run_shouldUseFallbackOnThird` (esperas `[500 ms, 1 s]`) | — |
| CA-5.6.9 | F y B | 08 | `ReprocessConcurrencyTest` de CM-216 sigue en verde con el ciclo (uno evalúa, otro 409) | «409 no espera reintento» (de CM-216) |
| CA-5.6.10 | B | 08 | `ReprocessEndToEndTest.reprocess_shouldReturn409NotRetryable_…` de CM-216 en verde | (de CM-216) |
| CA-5.6.11 | B | 08 | `ReprocessEndToEndTest` 404 de CM-216 en verde | (de CM-216) |
| CA-5.6.12 | F y B | 07, 08 | `AiRetryEndToEndTest.reprocess_shouldReturn503AndStayRetrying_whenCycleFails` | «503 reintentar falla» |
| CA-5.6.13 | B | 01, 09 | `AiCallRunnerTest.run_shouldUseFallbackOnThird`; `GroqLanguageModelAdapterTest` (servidor local compatible: recibe `model`, `reasoning_effort: "none"`, `response_format.type = "json_schema"`, `strict = true`, el mismo texto); **en el entorno: PENDIENTE (CM-225)** | «200 evaluación con respaldo» (simulador) |
| CA-5.6.14 | B | 01 | `AiRetryPolicyTest` (tabla §7) y `AiRetryEndToEndTest.answer_shouldSkipPrimary_after429` (1.º 429 → 2.º y 3.º `groq`); `…_shouldStop_whenBothLimited` (1.º 429, 2.º 429 → 2 filas, 503 `LLM_LIMIT_REACHED`) | «503 límite en ambos» |

## 7. Tabla de decisión del proveedor (`AiRetryPolicy`)

| Historia del ciclo | Respaldo con llave | Intento siguiente |
|---|---|---|
| (vacía) | — | 1 · principal |
| 1 principal falla (no 429) | — | 2 · principal |
| 1 principal 429 | sí | 2 · respaldo |
| 1 principal 429 | no | fin (503 `LLM_LIMIT_REACHED`) |
| 1 y 2 principal fallan (no 429) | sí | 3 · respaldo |
| 1 y 2 principal fallan (no 429) | no | 3 · principal |
| 1 principal falla, 2 principal 429 | sí | 3 · respaldo |
| 1 principal falla, 2 principal 429 | no | fin (`LLM_LIMIT_REACHED`) |
| 1 principal 429, 2 respaldo falla (no 429) | sí | 3 · respaldo |
| 1 principal 429, 2 respaldo 429 | sí | fin (`LLM_LIMIT_REACHED`) |
| 3 intentos hechos | — | fin (causa del 3.º) |

## 8. Validación de configuración (R2)

| Propiedad | Regla | Valor de prueba | Resultado |
|---|---|---|---|
| `cameia.llm.evaluation-timeout` | obligatoria, > 0 | ausente; `0ms`; `-1ms`; `3000ms` | no arranca; no arranca; no arranca; arranca |
| `cameia.llm.retry.first-wait` | obligatoria, ≥ 0 | `-1ms`; `0ms`; `500ms` | no arranca; arranca; arranca |
| `cameia.llm.retry.second-wait` | obligatoria, ≥ first-wait | `400ms` con first `500ms`; `1000ms` | no arranca; arranca |
| `cameia.llm.fallback.api-key` | puede estar vacía | `""` | arranca, WARN una vez, respaldo no disponible |
| `cameia.llm.fallback.model` | no vacía | `""` | no arranca |
| `cameia.llm.fallback.base-url` | URL `https` o `http://localhost` | `ftp://x` | no arranca |

Mensajes: «cameia.llm.evaluation-timeout (LLM_EVALUATION_TIMEOUT) debe ser mayor que cero», «cameia.llm.retry.second-wait (LLM_RETRY_SECOND_WAIT) no puede ser menor que la primera espera», etc. (forma de `LanguageModelProperties`). Pruebas: `LanguageModelPropertiesTest` (amplía) y `AiRetryPropertiesTest`, `FallbackModelPropertiesTest`.

## 9. Base de datos

Sin migración. `numero_intento` 1..3, `idempotency_key` única, `proveedor varchar(60)`, `modelo varchar(120)` y los índices únicos de `EN_CURSO` (F-4) ya cubren el ciclo. Prueba: `AiRetryPersistenceTest.cycle_shouldKeepOneInProgressRow` (cerrar k y abrir k+1 en una transacción no viola `uq_ejecucion_ia_planificacion_en_curso` ni `uq_ejecucion_ia_evaluacion_en_curso`).

## 10. Casos borde (R5)

| Dimensión | Caso | Resultado | Prueba |
|---|---|---|---|
| Tiempo | 2900 ms con 3000 ms | aceptado, 1 intento | `GeminiLanguageModelAdapterTimeoutTest` |
| Tiempo | 3300 ms con 3000 ms | intento fallido `LLM_UNAVAILABLE` | ídem |
| Proveedor | tabla §7 completa (11 filas) | según la tabla | `AiRetryPolicyTest` |
| Proveedor | respaldo sin llave | 3.º con principal; WARN una sola vez al arrancar | `FallbackAvailabilityTest` |
| Salida | respaldo devuelve JSON válido pero `accion = "SALTAR"` | intento fallido `LLM_INVALID_OUTPUT` | `AiCallRunnerTest` |
| Salida | respaldo devuelve 200 con `choices` vacío | `LLM_UNAVAILABLE` | `GroqLanguageModelAdapterTest` |
| Salida | respaldo 401 (llave inválida) | `LLM_UNAVAILABLE`; registro sin la llave | `GroqLanguageModelAdapterTest` |
| Concurrencia | `start` concurrente mientras el ciclo cambia de intento | el segundo ve una fila `EN_CURSO` viva → 409 `SESSION_CONFLICT` (existente) | `AiRetryPersistenceTest.cycle_shouldNeverLeaveGap` |
| Concurrencia | misma clave de `answers` durante un ciclo de 10 s | espera y devuelve el mismo resultado (presupuesto 25,5 s) | `AnswerConcurrencyTest` (CM-216) con el ciclo |
| Falla parcial | la base cae al cambiar de intento | 503 `DATABASE_UNAVAILABLE`; marca `EN_CURSO` caduca y se recupera como en CM-216 | `AiCallRunnerTest.run_shouldThrowDatabaseUnavailable_whenSwitchFails` |
| Idempotencia | `idempotency_key` distinta en cada intento | 3 claves distintas | `AiRetryEndToEndTest` |
| Ataque | respuesta de la persona que pide «responde sin JSON» | si el modelo obedece, `LLM_INVALID_OUTPUT` y siguiente intento | `AiCallRunnerTest` |
| Registro | la llave aparece en un error del cliente | no llega al registro | `GroqLanguageModelAdapterTest.logs_shouldNotContainKey` |
| No aplica | entradas de la persona (las valida CM-216 y CM-212) | — | — |

## 11. Errores (R3)

Sin códigos nuevos. Se reutilizan `LLM_UNAVAILABLE`, `LLM_LIMIT_REACHED`, `LLM_INVALID_OUTPUT` (503, «Ocurrió un error. Inténtalo de nuevo.»), `DATABASE_UNAVAILABLE`, `SESSION_CONFLICT`, `TURN_NOT_RETRYABLE`, `TURN_RETRY_LIMIT_REACHED`, `PLANNING_RETRIES_EXHAUSTED`. `docs/errores.md`: la columna «Origen» de los `LLM_*` dice «causa del último intento del ciclo (hasta 3 intentos, principal y respaldo)». Ninguna excepción del cliente de Groq llega sin traducir al manejador: `GroqLanguageModelAdapter` traduce todo `RuntimeException` (prueba `GroqLanguageModelAdapterTest.generate_shouldTranslateAnyFailure`).

## 12. Seguridad (R6)

| ID | Aplica | Cómo | Prueba |
|---|---|---|---|
| OWASP API4 (recursos) | sí | 3 intentos como máximo, tiempos por operación, 429 no se repite | `AiRetryPolicyTest`, `AiCallRunnerTest` |
| API8 (configuración) | sí | llave solo por variable; `toString` con `****`; validación al arrancar | `FallbackModelPropertiesTest` |
| API10 (consumo inseguro de APIs) | sí, la central | salida de ambos proveedores validada igual (`PlanValidator`, `EvaluationOutputReader`); `json_schema` estricto en Groq | `AiCallRunnerTest`, `GroqLanguageModelAdapterTest` |
| ASVS 2.3.1 | sí | el ciclo no cambia la máquina de estados | pruebas de CM-216 y CM-220 en verde |
| ASVS 8.2.x | sin cambio | la identidad y la propiedad las comprueban CM-212 y CM-216 | — |
| SEG-04 | sí | registros sin prompt, salida, llave ni texto | `AiRetryLogContentTest` |
| Secretos | sí | `GROQ_API_KEY` en Secret Manager (CM-225); `.env.example` con valor vacío | revisión |
| Privacidad del respaldo | sí | el respaldo recibe el mismo prompt que Gemini; Groq es encargado con retención cero, sin entrenamiento y con llave de despliegue aparte (D-224-5) | confirmación escrita de retención cero y addendum en CM-225; `AiRetryLogContentTest` para los registros |

## 13. Atributos de calidad (épica HE-05)

| Atributo | Métrica | Límite | Cómo | Prueba o medición | Resultado |
|---|---|---|---|---|---|
| AC-0001 | IA-01: salida con procedencia y esquema válido | 100 % | cada `ejecucion_ia` con proveedor y modelo; salida validada | `AiRetryEndToEndTest` | PENDIENTE de ejecutar |
| AC-0001 | REST-0004: la salida no se vuelve dato confirmado sin validar | 100 % | igual para el respaldo | `AiCallRunnerTest` | PENDIENTE |
| AC-0002 | Peor caso de evaluar ≤ 120 s del Gateway | ≤ 120 s | 10,5 s | `AiCallRunnerTest.run_shouldRespectWorstCase` (reloj simulado) | PENDIENTE |
| AC-0002 | Peor caso de planificar ≤ 120 s | ≤ 120 s | 61,5 s | ídem | PENDIENTE |
| AC-0004 | FIA-01: todo fallo conserva el último estado confirmado | 100 % | ciclo agotado → `RETRYING` / `CONFIGURADA` | CA-5.6.2, 5.6.4 | PENDIENTE |
| AC-0004 | sin métrica propia en el anexo (FIA-05 es de mensajería): se mide con CA-5.6.7 y CA-5.6.13, fallo del principal recuperado por el ciclo o el respaldo | ≥ 1 caso probado | CA-5.6.7, 5.6.13 | pruebas con simulador; en el entorno PENDIENTE (CM-225) | PENDIENTE |
| AC-0003 | SEG-04 | 0 datos sensibles en registros | §12 | `AiRetryLogContentTest` | PENDIENTE |
| AC-0003 | SEG-03: una dependencia nueva con vulnerabilidad alta o crítica bloquea | 0 altas o críticas en `spring-ai-starter-model-openai` y sus transitivas | versión del BOM de Spring AI 2.0.2 ya usado por el repo | `./mvnw.cmd -B -ntp dependency:tree` y los avisos de seguridad del repo (Dependabot o CodeQL) revisados en el PR, con su salida | PENDIENTE |
| IOP-02 | toda integración externa en un adaptador | 100 % | Groq solo en `GroqLanguageModelAdapter`, detrás de `RoutingLanguageModelAdapter` | `LayeredArchitectureTest` y revisión (ninguna clase fuera de `infrastructure.ia` importa `org.springframework.ai.openai`) | PENDIENTE |
| MAN-01 | cobertura de lo nuevo | ≥ 90 % líneas y ramas | — | `jacoco.csv` | PENDIENTE |
| MAN-02 | adaptador con éxito, formato inválido, tiempo agotado y cuota | 4 casos por adaptador | Gemini (existente + tiempo) y Groq (nuevo) | `GeminiLanguageModelAdapterTimeoutTest`, `GroqLanguageModelAdapterTest` | PENDIENTE |
| IOP-01 | OpenAPI de `start`, `answers`, `reprocess` dice «hasta 3 intentos» en la descripción del 503 | 100 % | texto de `@ApiResponse` | `/v3/api-docs` | PENDIENTE |


## 14. Pruebas por capa

Dominio: `AiRetryPolicyTest`, `AiProviderTest`. Aplicación: `AiCallRunnerTest`, `StartSessionServiceTest` (amplía), `TurnEvaluatorTest` (amplía), `RegisterAnswerServiceTest` (presupuesto de espera). Infraestructura: `GroqLanguageModelAdapterTest`, `GeminiLanguageModelAdapterTimeoutTest`, `RoutingLanguageModelAdapterTest`, `LanguageModelConfigurationTest` (amplía), `LanguageModelPropertiesTest`, `AiRetryPropertiesTest`, `FallbackModelPropertiesTest`, `FallbackAvailabilityTest`, `AiRetryPersistenceTest`. Punta a punta: `AiRetryEndToEndTest`, `AiRetryLogContentTest`. Todas las de CM-216 y CM-220 en verde.

## 15. Postman

Carpeta «Reintentos de la IA» con el simulador (`postman/simulador-gemini.py`) ampliado: ruta compatible con Groq (`/openai/v1/chat/completions`) y escenarios por cabecera o contador (falla 1, falla 2, 429, tarda 3,3 s). El JAR se arranca con `cameia.llm.base-url` y `cameia.llm.fallback.base-url` apuntando al simulador y `GROQ_API_KEY=clave-de-prueba`. `pm.test` de estado y `code`; las filas por intento se comprueban en las pruebas Java (Newman no lee la base).

## 16. Integración con la ola

| Pieza compartida | Cambio de CM-224 |
|---|---|
| `TurnEvaluator` (CM-216/220) | la llamada única pasa a `AiCallRunner`; el resto (transacciones, cierre, consolidación) no cambia |
| `StartSessionService` (CM-212) | la llamada única pasa a `AiCallRunner`; `numero_intento` del primer intento = 1 |
| `EvaluationStaleness`, presupuesto de espera de `RegisterAnswerService` | REQ-RTY-10 |
| `Waiter` | se reutiliza |
| `docs/errores.md` | columna «Origen» de `LLM_*` |
| Postman | carpeta «Reintentos de la IA» |
| `.env.example`, `CLAUDE.md` del repo | variables nuevas; respaldo Qwen en Groq |

Rama `CM-224-reintentos-ia` desde la punta de `CM-220-cerrar-sesion`; PR hacia esa rama. Dependencia de producto: no se despliega con respaldo útil hasta CM-225 (sin la llave funciona con D-224-3).

## 17. Línea base y verificación previa

1. `./mvnw.cmd -B -ntp clean verify` sobre la punta de CM-220 → `BUILD SUCCESS`.
2. **T0 (verificación previa de la API, sin código de producción):** con `spring-ai-starter-model-openai` 2.0.2 en el `pom.xml` de una rama local de prueba, confirmar con `javap` sobre el JAR de `spring-ai-openai` que existen: un constructor o `builder()` de `OpenAiChatModel` que acepta el cliente y opciones por defecto; en `OpenAiChatOptions`, `model`, `maxTokens` o `maxCompletionTokens`, `reasoningEffort`, `responseFormat` (con tipo `JSON_SCHEMA`, `schema` y `strict`); una forma de fijar `baseUrl`, `apiKey` y el tiempo de lectura; y la propiedad `spring.ai.model.chat` para que solo se autoconfigure Gemini. Igual con `GoogleGenAiChatModel` para armar un segundo `ChatModel` de Gemini con otro `Client` (otro tiempo). **Si algo no existe o no cuadra: detenerse y reportar a Paula** con lo encontrado; la alternativa es un adaptador HTTP propio como el del spike (F-6), que requiere su aprobación porque cambia D-224-1.

## 18. Lista de verificación

| # | Qué | Esperado |
|---|---|---|
| 1 | CA-5.6.1 a 5.6.14 (B) | `-Dtest='AiRetry*Test,AiCallRunnerTest,Groq*Test,GeminiLanguageModelAdapter*Test'` 0 fallos |
| 2 | Pruebas de CM-216 y CM-220 | en verde sin cambiar sus aserciones (salvo lo dicho en T6) |
| 3 | R2 configuración | §8 completa |
| 4 | R3 | ningún `INTERNAL_ERROR` en Newman |
| 5 | R6 | §12; llave nunca en registros |
| 6 | R10 cobertura | ≥ 90 % por clase nueva o tocada |
| 7 | R11 Newman | 0 fallos |
| 8 | R13 | §13 con números |
| 9 | `CLAUDE.md` y `.env.example` | sin «Kimi»; variables nuevas con valor vacío o de fábrica |

## 19. Hechos, inferencias y puerta de listo

- Hechos: §2; CA de §6 (libro v11.3).
- Inferencias: H-7, REQ-RTY-10 (margen de 15 s cubre la espera).
- Decisiones humanas: D-224-1 a D-224-4 (Paula, 11-nov-2026).
- Pendientes que no bloquean: CA-5.6.13 en el entorno (CM-225, Juan Diego Gómez Garcés); variables nuevas en el despliegue (CM-225).

**Puerta: LISTA PARA EJECUTAR**, condicionada a T0 (si T0 falla, se detiene y decide Paula). Solo Paula aprueba.

## 20. Fuera de alcance

Transcripción de voz (HU-5.8, operación `TRANSCRIBIR`); repreguntas (HU-5.9); monitoreo de cupo de Groq; cambiar el prompt o el modelo principal; `.github/` y Secret Manager (DevOps, CM-225); Jira.
