# Plan · CM-216 · Registrar la respuesta y avanzar

Base: `origin/develop` = `09bb7ff`. Spec: `spec.md` de esta carpeta. Tarjetas: `tasks.md`.

## 0. Foco de revisión (lo que más probablemente falle)

| # | Situación | Prueba que la vigila | En el plan |
|---|---|---|---|
| 1 | Un código nuevo de excepción sin fila en `BusinessExceptionHandler.DEFINITIONS` termina en 500 | `BusinessExceptionHandlerTest.handle_shouldMapEveryAnswerCode` (T2) y Newman «ningún INTERNAL_ERROR» (T13) | sí |
| 2 | La llamada a la IA dentro de una transacción (conexión retenida 20 s con un pool de 2) | `RegisterAnswerServiceTest.register_shouldCommitAnswerBeforeCallingModel` y `…_shouldNotHoldTransactionDuringModelCall` (T8) | sí |
| 3 | Respuesta de 4000 contada en unidades UTF-16 o recortada antes de contar | `AnswerRequestPolicyTest` filas de emoji, NFD y espacios (T3) | sí |
| 4 | Dos peticiones con la misma clave a la vez → dos llamadas a la IA o un 500 por la clave única | `AnswerConcurrencyTest` (T12) | sí |
| 5 | Dos `reprocess` juntos → dos evaluaciones o contador +2 | `ReprocessConcurrencyTest` (T12) | sí |
| 6 | Caída entre las dos transacciones deja el turno `PROCESSING` sin salida | `…_shouldLeaveRecoverableTurn_…` (T8) y `…_shouldRecoverStaleProcessing` (T12) | sí |
| 7 | El texto de la siguiente pregunta sale de la IA o de otra sesión | `…_shouldReturnOriginalNextQuestion_…` (T12) | sí |
| 8 | La retroalimentación se filtra en la respuesta o en un registro | `…_shouldNotExposeFeedback_…`, `AnswerLogContentTest` (T12) | sí |
| 9 | `turnId` de otra sesión aceptado (BOLA) | `…_shouldReturn404TurnNotFound_whenTurnOfOtherSession` (T12) | sí |
| 10 | El adaptador de ejecuciones sigue escribiendo `PLANIFICAR` para todo | `AiExecutionRepositoryAdapterTest.save_shouldPersistEvaluationWithTurn` (T5) | sí |
| 11 | Repetir con la misma clave cuando la evaluación ya cayó responde «se está procesando» (falso) | `RegisterAnswerServiceTest.register_shouldReturn503_whenSameKeyAndEvaluationStale` (T8) | sí |
| 12 | Otra entrevista iniciada mientras la IA evalúa deja un turno abierto en una sesión abandonada | `TurnEvaluatorTest.evaluate_shouldRejectAndCloseExecution_whenSessionAbandonedMeanwhile` (T7), `AnswerConcurrencyTest` (T12) | sí |
| 13 | Una llave queda en las salidas de la medición | `git grep --untracked` de T15 y `Secretos.enmascarar` | sí |

## 1. Enfoque

1. **Guardar, soltar la base, evaluar, guardar.** Dos transacciones con `TransactionTemplate` (patrón de `StartSessionService`); la llamada a la IA entre ambas, sin conexión tomada.
2. **Reutilizar la clave anti-duplicados** de crear la sesión (tabla, puerto, adaptador y huella SHA-256) con la operación `RESPONDER`. El resultado se guarda en la misma fila: primero 202 (en proceso) y al terminar 200 o 503.
3. **Una pieza de evaluación compartida** (`TurnEvaluator`) que usan `answers` y `reprocess`; CM-220 y CM-224 la extienden.
4. **El dominio decide**: `AnswerRequestPolicy` (forma), `InterviewTurn` (transiciones y tope de reintentos), `EvaluationStaleness` (caducidad), `InterviewSession.registerActivity` (fecha de actividad).
5. **La base es el respaldo**: `uq_intento_definitivo_por_turno`, `uq_ejecucion_ia_evaluacion_en_curso`, `ck_turno_reintentos_manuales`, traducidos a excepciones de dominio en los adaptadores.

## 2. Archivos

Prefijos: `M` = `src/main/java/tech/cameia/entrevista/`, `T` = `src/test/java/tech/cameia/entrevista/`, `R` = `src/main/resources/`.

| Capa | Crear | Modificar |
|---|---|---|
| Migración | `R/db/migration/V8__responder_y_reintentar_turno.sql` | — |
| Errores | `M/domain/exception/{SessionAbandoned,SessionAlreadyClosed,TurnNotFound,AnswerAlreadyExists,TurnConflict,TurnNotRetryable,TurnRetryLimitReached}Exception.java` | `M/domain/exception/ErrorCode.java` (al final), `M/presentation/advice/BusinessExceptionHandler.java` (`DEFINITIONS`), `docs/errores.md` |
| Dominio | `M/domain/model/{AiOperation,AnswerAttempt,TurnFeedback,EvaluationResult,ValidatedAnswerRequest}.java`, `M/domain/policy/{AnswerRequestPolicy,EvaluationStaleness}.java`, `M/domain/port/{AnswerAttemptRepository,TurnFeedbackRepository,Waiter}.java` | `AiExecution.java` (+`operation`, `turnId`, `evaluationStarted`), `InterviewTurn.java` y `TurnSnapshot.java` (+`manualRetries`, `startManualRetry`), `InterviewSession.java` (+`registerActivity`, +`requireAcceptsAnswers`), `AiExecutionRepository.java` (+`findEvaluationInProgress`), `InterviewTurnRepository.java` (+`findByIdAndSession`, `countCompleted`); `InterviewQuestionRepository.java` sin cambios (se usa `findBySession`) |
| Persistencia | `M/infrastructure/persistence/entity/{AnswerAttemptEntity,TurnFeedbackEntity}.java`, `M/infrastructure/persistence/repository/{AnswerAttemptJpaRepository,AnswerAttemptRepositoryAdapter,TurnFeedbackJpaRepository,TurnFeedbackRepositoryAdapter}.java`, `M/infrastructure/time/ThreadSleepWaiter.java` | `AiExecutionEntity.java` (+`turno_id`), `AiExecutionRepositoryAdapter.java`, `AiExecutionJpaRepository.java`, `InterviewTurnEntity.java` (+`reintentos_manuales`), `InterviewTurnMapper.java`, `InterviewTurnJpaRepository.java`, `InterviewTurnRepositoryAdapter.java` |
| IA | `R/prompts/evaluacion.v1.txt`, `M/application/service/{EvaluationPromptBuilder,EvaluationOutputReader}.java` | — |
| Aplicación | `M/application/command/AnswerCommand.java`, `M/application/service/{RegisterAnswerService,ReprocessTurnService,TurnEvaluator,AnswerResult}.java` | `GetSessionStateService.java`, `SessionState.java` |
| API | `M/presentation/dto/{AnswerRequest,AnswerResultResponse,TurnResponse,NextQuestionResponse}.java` | `InterviewSessionController.java`, `PendingQuestionResponse.java` (+`status`, `retriesUsed`), `SessionStateResponse.java` (`history`) |
| Pruebas | ver §5 | `ColumnLengthConsistencyTest`, `InterviewTurnTest`, `GetSessionStateServiceTest`, `BusinessExceptionHandlerTest`, `AiExecutionTrimmingTest` |
| Medición (T15) | `T/spike/planificacion/{MedicionEvaluacionRunner,InformeEvaluacion,InformeEvaluacionTest}.java`, `src/test/resources/spike/evaluacion/casos.v1.json`, `docs/mediciones/CM-216-evaluacion/{README.md,informe.md,resultados.csv,salidas/}` | `T/spike/planificacion/{QwenGroq,GeminiLite}.java` |
| Documentos | `docs/adr/0012-registrar-la-respuesta-y-reintentar.md`, `docs/bitacora-ia/11_registrar-respuesta_prompt.md` | `docs/errores.md`, `postman/cameia-entrevista.postman_collection.json`, `postman/simulador-gemini.py` |

**No se tocan:** `.github/`, migraciones `V1` a `V7`, `CreateSessionService` (salvo lo que T5 diga), `StartSessionService` (CM-224 lo cambia), `cameia-web`.

## 3. Reutilización

| Existente | Uso | Por qué no se crea otro |
|---|---|---|
| `clave_idempotencia` + `IdempotencyKeyRepository.save/find` | clave de `answers`; `save` con el mismo `id` actualiza el resultado (merge de JPA) | misma necesidad que crear la sesión |
| `ValidatedSessionRequest.sha256Hex` (paquete `domain.model`) | huella de la respuesta | mismo algoritmo, mismo paquete |
| `SessionConfigurationPolicy.IDEMPOTENCY_KEY_FIELD` y patrón `[A-Za-z0-9._-]{1,64}` | validar la cabecera | mismo formato publicado |
| `SessionCommand.of` (UUID canónico de 36) | `sessionId` y `turnId` de la ruta | ya rechaza formas laxas |
| `GetSessionStateService.findOwned` | 404 único por ajena/inexistente/mal escrita | ya cumple RT-03 |
| `InterviewTurn.startProcessing/complete/retry/resumeProcessing` | transiciones | ya validan con `TurnStatus.canChangeTo` |
| `AiExecution.succeeded/failed` | cierre de la ejecución | se amplía el record, no se duplica |
| `LanguageModelPort` + `GeminiLanguageModelAdapter` | llamada única | traduce 429 y fallos a `LLM_*` |
| `PlanOutputReader` (bloque ```` ```json ````) y `PlanningPromptBuilder.DATA_MARK` | lectura de la salida y limpieza de marcas | mismo problema, mismo método |
| `ConstraintNames.violates` | traducir restricciones | patrón del repo |
| `SessionLifecycleEndToEndTest.ScriptedModelConfiguration`, `AbstractPersistenceTest`, `WithLanguageModelProperties`, `ProblemDetailTestSupport` | dobles y bases de prueba | evitan una segunda infraestructura de pruebas |

## 4. Decisiones técnicas

| Decisión | Razón | Descartado |
|---|---|---|
| Clave con estado 202 «en proceso» en la misma tabla | sin tabla nueva; la clave existe desde la primera transacción y bloquea duplicados | tabla aparte de «envíos en curso» (más esquema sin beneficio) |
| `Waiter` como puerto de dominio con `ThreadSleepWaiter` | las pruebas no duermen; CM-220 y CM-224 lo reutilizan | `Thread.sleep` directo (pruebas lentas); bucle activo |
| Caducidad = `cameia.llm.timeout` + 15 s | mismo margen que `PlanningClaim`; con CM-216 sola, una evaluación dura como máximo 20 s | valor fijo nuevo (otra variable que DevOps debería cargar) |
| `feedback` jsonb con 4 claves, sin `follow_up_texto` | CA-5.2.16 | guardar la salida entera |
| `numero_intento = 1` en cada ejecución de evaluación | CM-216 hace un intento por petición; CM-224 numera 1 a 3 dentro del ciclo | contar los `reprocess` en `numero_intento` (lo limita a 3 por el `CHECK`) |
| Sesión `EN_CURSO` tras la pregunta 5 | CA-5.2.5; el cierre es de CM-220 | cerrar aquí (invade HU-5.5) |

## 5. Matriz de pruebas (resumen; detalle en las tarjetas)

| Capa | Clases | Caminos |
|---|---|---|
| Dominio | `AnswerRequestPolicyTest` (24 filas de §7 de la spec), `ValidatedAnswerRequestTest`, `InterviewTurnTest` (+6), `InterviewSessionTest` (+5), `EvaluationStalenessTest` (4), `AiExecutionTrimmingTest` (+2) | cada regla y límite |
| Aplicación | `RegisterAnswerServiceTest` (14), `ReprocessTurnServiceTest` (10), `TurnEvaluatorTest` (6), `EvaluationOutputReaderTest` (12), `EvaluationPromptBuilderTest` (4), `GetSessionStateServiceTest` (+6) | feliz, cada error, cada estado, fallo de cada transacción, espera |
| Persistencia | `AnswerSchemaMigrationTest` (5), `AnswerAttemptRepositoryAdapterTest` (4), `TurnFeedbackRepositoryAdapterTest` (3), `AiExecutionRepositoryAdapterTest` (4), `InterviewTurnRepositoryAdapterTest` (+4), `ColumnLengthConsistencyTest` (+1) | restricciones con nombre |
| Web y punta a punta | `AnswerEndToEndTest` (≈30), `ReprocessEndToEndTest` (8), `AnswerConcurrencyTest` (2), `ReprocessConcurrencyTest` (1), `SessionStateEndToEndTest` (4), `AnswerLogContentTest` (1), `BusinessExceptionHandlerTest` (+7) | CA de la spec §6 |

## 6. Orden, commits y paralelo

| Commit | Tarjetas | Mensaje |
|---|---|---|
| 1 | T1 | `CM-216 \| feat(db): migración para responder y reintentar un turno` |
| 2 | T2 | `CM-216 \| feat(errores): códigos y excepciones de responder y reintentar` |
| 3 | T3 | `CM-216 \| feat(validacion): reglas de la respuesta escrita` |
| 4 | T4 | `CM-216 \| feat(dominio): respuesta, retroalimentación, reintentos y caducidad` |
| 5 | T5 | `CM-216 \| feat(persistencia): adaptadores de respuesta, retroalimentación y evaluación` |
| 6 | T6 | `CM-216 \| feat(ia): prompt y lectura de la evaluación` |
| 7 | T7, T8, T9, T10 | `CM-216 \| feat(aplicacion): registrar, reintentar y mostrar el historial` |
| 8 | T11 | `CM-216 \| feat(api): endpoints answers y reprocess` |
| 9 | T12 | `CM-216 \| test(entrevista): punta a punta, concurrencia y registros` |
| 10 | T13 | `CM-216 \| docs(entrevista): Postman, catálogo de errores y ADR` |
| 11 | T15 (código) | `CM-216 \| test(ia): medición propia del evaluador` (runner, informe, casos y `InformeEvaluacionTest`; sin resultados) |
| 12 | T15 (resultados) | `CM-216 \| docs(mediciones): resultados de la medición del evaluador` (informe, CSV y salidas; solo con el sí de Paula) |

T15 depende solo de T6, va en paralelo con T7 a T13 y no los bloquea; su corrida contra los proveedores espera la revisión de los casos y la autorización de Paula de cada día. **Orden del stack (D-216-8):** el commit 12 entra **antes de publicar** la rama `CM-220-cerrar-sesion`; mientras tanto, las ramas de CM-220 y CM-224 viven solo en local y, si ya existían, se rebasan con `--onto` sin force-push. Si la corrida se atrasa, el PR de CM-216 se abre en Draft sin el commit 12 y lo dice en «Hallazgos que aplican a esta tarea». T2, T3 y T6 no dependen entre sí (pueden ir en paralelo tras T1). T4 antes de T5. T7 antes de T8 y T9. T14 es la verificación final, sin commit.

## 7. Riesgos

| Riesgo | Efecto | Mitigación |
|---|---|---|
| Pool de 2 conexiones en Cloud Run (`despliegue-continuo.yml`) | una espera con conexión tomada bloquea el servicio | IA y espera fuera de transacción (foco 2) |
| CM-216 sola en producción sin CM-220 | iniciar otra entrevista abandona una sesión con 5 respuestas (H-16) | el stack se fusiona en orden y se despliega con CM-220 |
| Evaluación con el tiempo de 20 s | la persona espera hasta 20 s por respuesta mientras CM-224 no llegue | CM-224 (3 s) en el mismo stack |
| La medición propia (T15) contradice al spike CM-261 (p. ej. peor sin dificultad, p95 > 3 s) | cambia H-11 o el tiempo de CM-224 | la spec §8b fija el destino de cada resultado; decide Paula antes de tocar el prompt |
| T15 choca con los límites por minuto (Gemini 15 llamadas; Groq 8.000 tokens) | latencias y fallos contaminados por rechazos | pausas por modelo de la spec §8b (Gemini 5 s; Qwen 8 s y 18 s), ventana de 60 s y 65 s entre rondas de G5 |
| T15 agota su cupo diario | grupos incompletos | dos días, 228 llamadas a Gemini (de 500) y 228 a Qwen (de 1.000); topes por modelo `SPIKE_PRESUPUESTO_GEMINI=200` y `SPIKE_PRESUPUESTO_QWEN=400`. La llave de Gemini no es la de preproducción: no resta cupo al equipo |
| El prompt del spike no está en el repo | copia infiel | T6 lo copia literal del anexo A y la prueba compara el inicio y el fin |

## 8. Autorrevisión del plan (resultado)

1. Cada REQ-ANS-01 a 17 y cada CA con `B` tiene tarjeta: sí (tabla en `tasks.md` §0).
2. Ninguna tarjeta dice «manejar errores» sin decir cuál: sí.
3. Mismos nombres en todas las tarjetas (clases, métodos, códigos): revisado con búsqueda de cada nombre.
4. Cada fila del foco tiene prueba: sí (§0).
5. Cada fallo previsible que iría al genérico tiene código o destino: sí (spec §11; la caída de la base sigue en `DATABASE_UNAVAILABLE` existente).
