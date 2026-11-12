# Plan · CM-220 · Cerrar la sesión al quinto turno

Base: punta de `CM-216-registrar-respuesta` (stack sobre `origin/develop` = `09bb7ff`). Spec: `spec.md`. Tarjetas: `tasks.md`.

## 0. Foco de revisión

| # | Situación | Prueba que la vigila |
|---|---|---|
| 1 | La transición a `EVALUANDO` en otra transacción que la del turno 5 (ventana con 5 completos y sesión `EN_CURSO`) | `TurnEvaluatorTest.evaluate_shouldStartEvaluationInSameTransaction_whenFifthCompleted` (T3) |
| 2 | La consolidación dentro de la transacción de la evaluación (un fallo deshace la respuesta 5) | `TurnEvaluatorTest.evaluate_shouldKeepAnswer_whenConsolidationFails` (T3) |
| 3 | Reintentar un dato roto (5 completos sin retroalimentación) | `ConsolidateSessionServiceTest.consolidate_shouldFailWithoutRetry_whenFeedbackMissing` (T2) |
| 4 | Dos consolidaciones → dos `fecha_fin` o un 409 | `ConsolidationConcurrencyTest` (T5) |
| 5 | Un DTO o un puerto nuevo que deja pasar `feedback` | `NoFeedbackExposureTest`, `PresentationDoesNotReadFeedbackTest` (T5) |
| 6 | `SESSION_CONSOLIDATION_FAILED` sin fila en `DEFINITIONS` → 500 | `BusinessExceptionHandlerTest.handle_shouldMapConsolidationFailed` (T1) |
| 7 | La espera entre intentos con conexión tomada | `ConsolidateSessionServiceTest.consolidate_shouldNotHoldTransactionWhileWaiting` (T2) |

## 1. Enfoque

1. La transición vive en la transacción de éxito de `TurnEvaluator` (código de CM-216), con una regla de dominio: `InterviewSession.closeIfComplete(int completedTurns, Clock)`.
2. La consolidación es un servicio propio (`ConsolidateSessionService`) con un ciclo de 3 intentos que usa el `Waiter` de CM-216; lo llaman `TurnEvaluator` (automático) y el controlador (`consolidation`).
3. Reutiliza `GetSessionStateService` para la respuesta 200 y `findOwned` para el 404.

## 2. Archivos

| Capa | Crear | Modificar |
|---|---|---|
| Errores | `M/domain/exception/SessionConsolidationFailedException.java` | `ErrorCode` (al final), `BusinessExceptionHandler.DEFINITIONS`, `docs/errores.md` |
| Dominio | `M/domain/policy/ConsolidationRetryPolicy.java` | `InterviewSession` (+`closeIfComplete`), `TurnFeedbackRepository` (+`countBySession`) |
| Persistencia | — | `TurnFeedbackJpaRepository`, `TurnFeedbackRepositoryAdapter` |
| Aplicación | `M/application/service/ConsolidateSessionService.java` | `TurnEvaluator` |
| API | — | `InterviewSessionController` (+`consolidate`) |
| Pruebas | `ConsolidationRetryPolicyTest`, `ConsolidateSessionServiceTest`, `ClosureEndToEndTest`, `ConsolidationConcurrencyTest`, `AbandonVsCloseRaceTest`, `NoFeedbackExposureTest`, `ClosureLogContentTest`, `SessionClosureSchemaTest`, `T/architecture/PresentationDoesNotReadFeedbackTest` | `TurnEvaluatorTest`, `InterviewSessionTest`, `TurnFeedbackRepositoryAdapterTest`, `BusinessExceptionHandlerTest` |
| Documentos | `docs/adr/0013-cierre-de-la-sesion-y-consolidacion.md`, `docs/bitacora-ia/12_cierre-de-sesion_prompt.md` | Postman (carpeta «Cerrar la sesión») |

## 3. Decisiones técnicas

| Decisión | Razón | Descartado |
|---|---|---|
| `closeIfComplete` en el agregado | la regla «5 completos» es del dominio y se prueba sin Spring | condicional suelto en el servicio |
| Ciclo de reintento en un `ConsolidationRetryPolicy` con esperas `[200 ms, 400 ms]` | valores de D-220-4 en un solo lugar; probado con `Waiter` falso | Spring Retry (dependencia nueva) |
| `SessionConflictException` como transitorio | la otra consolidación ganó; releer da `FINALIZADA` → 200 | tratarlo como 409 (el botón mostraría un error falso) |
| La 200 de la respuesta 5 no depende de la consolidación | la respuesta y su evaluación ya quedaron confirmadas (FIA-01) | 503 en `answers` (la persona reenviaría algo ya guardado) |

## 4. Orden y commits

| Commit | Tarjetas | Mensaje |
|---|---|---|
| 1 | T1 | `CM-220 \| feat(errores): código de la consolidación fallida` |
| 2 | T2 | `CM-220 \| feat(dominio): regla de cierre y ciclo de consolidación` |
| 3 | T3 | `CM-220 \| feat(aplicacion): pasar a evaluación y consolidar al completar el turno cinco` |
| 4 | T4 | `CM-220 \| feat(api): endpoint de consolidación` |
| 5 | T5 | `CM-220 \| test(entrevista): cierre, concurrencia y ausencia de retroalimentación` |
| 6 | T6 | `CM-220 \| docs(entrevista): Postman, catálogo de errores y ADR` |

T7: verificación final, sin commit. Diff estimado: unas 900 líneas (dos tercios pruebas).

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Cambiar `TurnEvaluator` rompe pruebas de CM-216 | T3 corre `TurnEvaluatorTest`, `RegisterAnswerServiceTest`, `ReprocessTurnServiceTest` y `AnswerEndToEndTest` (la prueba de la respuesta 5 de CM-216 pasa a esperar `FINALIZADA`: se ajusta su aserción y se dice en el informe) |
| Rebase cuando CM-216 se fusione con squash | regla del stack: `--onto`, SHA y árbol guardados antes |
| Pool de 2 conexiones | transacciones cortas, espera sin conexión (foco 7) |

## 6. Autorrevisión

1. REQ-CLS-01 a 11 y CA con `B`: tarjeta en `tasks.md` §0. Sí.
2. Sin «manejar errores» genérico: cada excepción nombrada. Sí.
3. Nombres iguales en spec, plan y tarjetas (`ConsolidateSessionService`, `ConsolidationRetryPolicy`, `closeIfComplete`, `countBySession`). Revisado.
4. Cada fila del foco tiene prueba. Sí.
5. Ningún fallo previsible al genérico: `SESSION_CONSOLIDATION_FAILED`, `SESSION_CONFLICT`, `SESSION_TRANSITION_NOT_ALLOWED`, `SESSION_ABANDONED` (carrera con un inicio, de CM-216). Sí.
