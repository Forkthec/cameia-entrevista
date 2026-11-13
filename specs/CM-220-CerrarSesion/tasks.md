# Tarjetas · CM-220 · Cerrar la sesión al quinto turno

Cada tarjeta se ejecuta sola, en orden, sobre la rama `CM-220-cerrar-sesion` (sale de la punta de `CM-216-registrar-respuesta`). Prefijos: `M` = `src/main/java/tech/cameia/entrevista/`, `T` = `src/test/java/tech/cameia/entrevista/`.

## 0. Cobertura de requisitos

| Requisito / CA | Tarjeta |
|---|---|
| REQ-CLS-01 (CA-5.5.1, 5.5.8) | T2 (`closeIfComplete`), T3 |
| REQ-CLS-02 | T3 |
| REQ-CLS-03, 06 (CA-5.5.9) | T2 |
| REQ-CLS-04 (CA-5.5.10) | T2 |
| REQ-CLS-05 (CA-5.5.11) | T1, T4, T5 |
| REQ-CLS-07 (CA-5.5.4, 5.5.5) | T5 (prueba; el código es de CM-216) |
| REQ-CLS-08 (CA-5.5.2) | T5 |
| REQ-CLS-09 (CA-5.5.6, 5.5.7) | T5 |
| REQ-CLS-10, 11 | T5 |

## 1. Reglas comunes

Las mismas de `specs/CM-216-RegistrarRespuesta/tasks.md` §1, copiadas aquí: identificadores en inglés y documentación en español; prueba `method_shouldX_whenY` con `@DisplayName` en español; sin dependencias nuevas, sin tocar `V1` a `V8` ni `.github/`, sin `CM-NNN` en comentarios, sin registrar textos de respuesta, pregunta o evaluación, sin `Thread.sleep` en pruebas, sin `catch` vacío; métodos ≤ 20 líneas y ≤ 3 parámetros; Javadoc en todo. Comando por tarjeta `./mvnw.cmd -B -ntp -Dtest='<clases>' test` con Docker encendido. Terminado = pruebas nuevas en verde, `LayeredArchitectureTest` en verde, comando y salida en el informe. **Detente** si la tarjeta contradice el código real de CM-216 (nombres, firmas): reporta el nombre real y no adaptes el diseño por tu cuenta.

## T0 · Preparación (sin commit)

1. `git log --oneline -1 CM-216-registrar-respuesta` y anota el SHA; crea la rama desde ahí.
2. Comprueba que existen, con estos nombres: `TurnEvaluator.evaluate(EvaluationJob)`, `InterviewTurnRepository.countCompleted(UUID)`, `TurnFeedbackRepository`, `domain.port.Waiter`, `InterviewSession.requireAcceptsAnswers()`, `SessionAbandonedException`, `SessionAlreadyClosedException`. Si alguno cambió, detente y reporta.
3. `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`.

## T1 · Código de error

**Modificar** `ErrorCode`: agregar al final `SESSION_CONSOLIDATION_FAILED` (Javadoc: «La evaluación de la entrevista no se pudo reunir; la sesión sigue en evaluación con sus respuestas»).

**Crear** `M/domain/exception/SessionConsolidationFailedException.java` con la forma de las excepciones de CM-216 (`MESSAGE = "Ocurrió un error. Inténtalo de nuevo."`).

**Modificar** `BusinessExceptionHandler.DEFINITIONS`: `SESSION_CONSOLIDATION_FAILED` → `HttpStatus.SERVICE_UNAVAILABLE`, título «Cierre pendiente». `docs/errores.md`: fila nueva (503, mensaje, endpoint `POST /api/v1/interviews/sessions/{sessionId}/consolidation`) y `consolidation` en la columna de endpoints de `SESSION_NOT_FOUND`, `SESSION_TRANSITION_NOT_ALLOWED`, `IDENTITY_REQUIRED`.

**Pruebas:** `BusinessExceptionHandlerTest.handle_shouldMapConsolidationFailed` (503, `code`, `detail`, `Content-Type`); `ErrorCodeDocumentationTest`, `ErrorCodeTest` en verde.

**Verifica:** `-Dtest='BusinessExceptionHandlerTest,ErrorCodeDocumentationTest,ErrorCodeTest,HandlerBranchesTest'`.

## T2 · Dominio y servicio de consolidación

**Modificar** `InterviewSession`:

```java
/**
 * Pasa la entrevista a evaluación si sus cinco preguntas tienen respuesta completa.
 *
 * @param completedTurns turnos completos de la sesión, contando el que acaba de completarse
 * @return verdadero si la sesión pasó a evaluación
 */
public boolean closeIfComplete(int completedTurns, Clock clock) {
    if (status != SessionStatus.IN_PROGRESS || completedTurns < InterviewQuestion.TOTAL) {
        return false;
    }
    startEvaluation(clock);
    return true;
}
```

**Crear** `M/domain/policy/ConsolidationRetryPolicy.java`:

```java
/** Ciclo de consolidación: hasta tres intentos con espera creciente entre ellos. */
public final class ConsolidationRetryPolicy {
    /** Esperas antes del segundo y del tercer intento. */
    public static final List<Duration> WAITS = List.of(Duration.ofMillis(200), Duration.ofMillis(400));
    /** Intentos en total. */
    public static final int MAX_ATTEMPTS = WAITS.size() + 1;
    /** Espera antes del intento dado (2 o 3). */
    public Duration waitBefore(int attempt) { return WAITS.get(attempt - 2); }
}
```

**Modificar** `TurnFeedbackRepository` (+`int countBySession(UUID sessionId)`) y su adaptador:

```sql
SELECT count(*) FROM microentrevista.feedback_turno f
  JOIN microentrevista.turno t ON t.id = f.turno_id
  JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
 WHERE p.sesion_id = :sessionId
```

**Crear** `M/application/service/ConsolidateSessionService.java` (`@Service`), dependencias `InterviewSessionRepository sessions`, `InterviewTurnRepository turns`, `TurnFeedbackRepository feedbacks`, `Waiter waiter`, `TransactionTemplate transaction`, `Clock clock`; campo `ConsolidationRetryPolicy policy = new ConsolidationRetryPolicy()`.

```java
/**
 * Ejecuta un ciclo de consolidación de una sesión en evaluación.
 *
 * @throws SessionConsolidationFailedException si los tres intentos fallan o faltan respuestas o retroalimentación
 */
public void consolidate(UUID sessionId, FirebaseUid owner) {
    for (int attempt = 1; attempt <= ConsolidationRetryPolicy.MAX_ATTEMPTS; attempt++) {
        if (attempt > 1) {
            waiter.pause(policy.waitBefore(attempt));   // fuera de toda transacción
        }
        try {
            transaction.executeWithoutResult(status -> finishOnce(sessionId, owner));
            return;
        } catch (DataAccessException | TransactionException | DatabaseUnavailableException
                | SessionConflictException transient) {
            log.warn("Intento de consolidación fallido [sessionId={}, intento={}, causa={}]",
                    sessionId, attempt, transient.getClass().getSimpleName());
        }
    }
    log.error("Consolidación fallida [code={}, sessionId={}]", ErrorCode.SESSION_CONSOLIDATION_FAILED, sessionId);
    throw new SessionConsolidationFailedException();
}
```

`finishOnce(sessionId, owner)`: relee la sesión con `sessions.findByIdAndOwner(sessionId, owner)` (método existente del puerto); `FINISHED` → retorna; distinto de `EVALUATING` → `SessionTransitionNotAllowedException`; `turns.countCompleted != 5 || feedbacks.countBySession != 5` → `log.error("Consolidación con datos incompletos [code={}, sessionId={}, turnosCompletos={}, retroalimentaciones={}]", …)` y `throw new SessionConsolidationFailedException()` (sale del bucle: no está en el `catch`); si no, `session.finish(clock)` y `sessions.save(session)`.

**Trampa:** `SessionConsolidationFailedException` y `SessionTransitionNotAllowedException` no se capturan en el bucle: verifica que no heredan de las del `catch` (`DatabaseUnavailableException`, `SessionConflictException`).

**Pruebas:** `InterviewSessionTest` (+`closeIfComplete_shouldStartEvaluation_whenFive`, `…_shouldStay_whenFour`, `…_shouldIgnore_whenNotInProgress`); `ConsolidationRetryPolicyTest` (`waitBefore(2) = 200 ms`, `waitBefore(3) = 400 ms`, `MAX_ATTEMPTS = 3`); `ConsolidateSessionServiceTest` (con `Waiter` falso que registra las esperas y un `TransactionTemplate` cuyo `PlatformTransactionManager` falla las veces que diga la prueba): `consolidate_shouldFinishAndSetEndDate` (reloj `2026-11-12T15:05:00Z` → `finishedAt` igual), `consolidate_shouldDoNothing_whenAlreadyFinished`, `consolidate_shouldRetryWithGrowingWait_whenTransientFailure` (falla 2, éxito 3; esperas `[200 ms, 400 ms]`), `consolidate_shouldThrow_whenThreeFailures` (esperas `[200 ms, 400 ms]`, 3 intentos), `consolidate_shouldFailWithoutRetry_whenFeedbackMissing` (conteos 5 y 4: 1 intento, 0 esperas), `consolidate_shouldRetry_whenVersionConflict` (`SessionConflictException` y luego `FINISHED`), `consolidate_shouldRejectNotEvaluating` (`IN_PROGRESS`), `consolidate_shouldNotHoldTransactionWhileWaiting` (el `Waiter` falso afirma `TransactionSynchronizationManager.isActualTransactionActive() == false`); `TurnFeedbackRepositoryAdapterTest` (+`countBySession_shouldCountOnlyThatSession`); `SessionClosureSchemaTest` (`FINALIZADA` sin `fecha_fin` y `EVALUANDO` con `fecha_fin` violan `ck_sesion_fecha_fin`).

**Verifica:** `-Dtest='InterviewSessionTest,ConsolidationRetryPolicyTest,ConsolidateSessionServiceTest,TurnFeedbackRepositoryAdapterTest,SessionClosureSchemaTest,LayeredArchitectureTest'`.

## T3 · Pasar a evaluación y consolidar desde `TurnEvaluator`

**Modificar** `TurnEvaluator` (código de CM-216):

1. En la transacción de éxito, después de `session.registerActivity(clock)` y antes de `sessions.save(session)`: si `question.position() == InterviewQuestion.TOTAL`, `boolean closed = session.closeIfComplete(turns.countCompleted(session.id()), clock)`; si devuelve `false` en la pregunta 5, `log.warn("Pregunta 5 completada sin cinco turnos completos [sessionId={}, turnosCompletos={}]", …)`. El conteo se hace **después** de guardar el turno como `COMPLETED` (en la misma transacción, con `saveAndFlush` ya visible).
2. Devuelve junto al `AnswerResult` si la sesión quedó en evaluación (un `boolean` en un `record` interno `EvaluationOutcome(AnswerResult result, boolean evaluating)` privado de la clase, o un campo nuevo en `EvaluationJob`; elige el primero).
3. Después de confirmar la transacción: si `evaluating`, `try { consolidation.consolidate(session.id(), session.owner()); } catch (SessionConsolidationFailedException failed) { /* ya registrado con code y sessionId */ }`. El `catch` lleva ese comentario y nada más: la respuesta 200 no depende de la consolidación (REQ-CLS-02). Ninguna otra excepción se captura aquí.
4. Inyecta `ConsolidateSessionService consolidation` en el constructor.

**Pruebas** `TurnEvaluatorTest` (+`evaluate_shouldStartEvaluationInSameTransaction_whenFifthCompleted` — el `TransactionTemplate` falso registra que la sesión guardada dentro de la transacción ya tiene `EVALUATING`; `evaluate_shouldConsolidateAfterCommit_whenFifthCompleted` — `consolidate` se llama una vez y fuera de transacción; `evaluate_shouldKeepAnswer_whenConsolidationFails` — devuelve el `AnswerResult` y el turno sigue `COMPLETED`; `evaluate_shouldStayInProgress_whenQuestionFourCompleted` — sin llamar a `consolidate`; `evaluate_shouldStayInProgress_whenCountBelowFive`). Ajusta la aserción de CM-216 que esperaba `EN_CURSO` tras la pregunta 5 (`AnswerEndToEndTest.answer_shouldFinishWithoutSixthTurn_whenQuestionFive`): ahora espera `FINALIZADA`; dilo en el informe.

**Verifica:** `-Dtest='TurnEvaluatorTest,RegisterAnswerServiceTest,ReprocessTurnServiceTest,AnswerEndToEndTest,ReprocessEndToEndTest'`.

## T4 · Endpoint `consolidation`

**Modificar** `InterviewSessionController`: inyecta `ConsolidateSessionService`.

```java
@PostMapping(path = "/{sessionId}/consolidation", produces = MediaType.APPLICATION_JSON_VALUE)
ResponseEntity<SessionStateResponse> consolidate(
        @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
        @Parameter(description = "Identificador de la sesión (UUID)") @PathVariable String sessionId) {
    FirebaseUid owner = FirebaseUid.required(userId);
    SessionCommand command = SessionCommand.of(sessionId, owner);
    return ResponseEntity.ok(SessionStateResponse.from(consolidation.consolidateOwned(command)));
}
```

**Agregar** a `ConsolidateSessionService`: `public SessionState consolidateOwned(SessionCommand command)` → `session = GetSessionStateService.findOwned(sessions, command)` (404); `FINISHED` → devuelve el estado; distinto de `EVALUATING` → `SessionTransitionNotAllowedException`; si no, `consolidate(session.id(), session.owner())` y devuelve `stateService.state(command)` (inyecta `GetSessionStateService stateService`).

OpenAPI: `@Operation(summary = "Reintenta reunir la evaluación de la entrevista")` y `@ApiResponse` 200 (`SessionStateResponse`), 401, 404, 409, 503 (ejemplo con `SESSION_CONSOLIDATION_FAILED`), 500, con constantes `EXAMPLE_*` como las existentes.

**Pruebas:** `ConsolidationControllerTest` (MockMvc `standaloneSetup`): 200 con `status: "FINALIZADA"`; 401 sin `X-User-Id`; cuerpo ignorado (`{"feedback":true}`); `ConsolidateSessionServiceTest` (+`consolidateOwned` por estado: `FINISHED` 200 sin ciclo, `EVALUATING` con ciclo, `IN_PROGRESS`/`CONFIGURED`/`ABANDONED` 409, ajena 404).

**Verifica:** `-Dtest='ConsolidationControllerTest,ConsolidateSessionServiceTest,SessionLifecycleControllerTest,ApiDocsFlagTest'`.

## T5 · Punta a punta, concurrencia y ausencia de retroalimentación

**Crear** (base: `SessionLifecycleEndToEndTest`, Testcontainers, puerto aleatorio, modelo guionado con evaluación `{"accion":"AVANZAR","clasificacion":"COMPLETA","motivo":"Responde lo pedido","follow_up_texto":"","feedback":"Buena estructura"}`, usuarios `uid-ana-001` y `uid-beto-002`):

- `ClosureEndToEndTest`: todos los métodos de la spec §5 y §8 con estos nombres; tras la respuesta 5, `SELECT estado, fecha_fin FROM microentrevista.sesion` = `FINALIZADA`, no nulo. Para `EVALUANDO` persistente, registra un `ConsolidateSessionService` espía o un `TransactionTemplate` que falla en la consolidación (bean `@Primary` en una configuración de prueba). CA-5.5.5: con la sesión `FINALIZADA` y `EVALUANDO`, `answers` → 409 `SESSION_ALREADY_CLOSED`, contador de llamadas al modelo sin cambios, `count(*)` de `intento_respuesta` sin cambios, sin turno 6.
- `ConsolidationConcurrencyTest`: sesión `EVALUANDO` (insertada por SQL con 5 turnos y 5 retroalimentaciones), dos hilos llaman `consolidation` con un `CountDownLatch` de salida; ambos 200; `fecha_fin` única (una sola actualización: `version` sube 1).
- `AbandonVsCloseRaceTest`: la evaluación de la pregunta 5 espera en un latch del modelo guionado; mientras, se crea e inicia otra sesión del mismo usuario; resultado: o `ABANDONADA` + 409 `SESSION_ABANDONED` en `answers` (turno 5 `RETRYING`, ejecución `FALLIDA` con `SESSION_ABANDONED`, 0 `feedback_turno` del turno 5, `fecha_fin` la del abandono), o `FINALIZADA` + la otra `start` sin abandonarla. Afirma que nunca queda `EVALUANDO` con la otra sesión `EN_CURSO` y esta sin `fecha_fin` tras la consolidación.
- `NoFeedbackExposureTest.endpoints_shouldNeverExposeFeedback`: con una sesión que tiene `feedback_turno` real, llama crear, `start`, `answers`, `reprocess` (con un turno en `RETRYING`), `state` (en `EN_CURSO`, `EVALUANDO`, `FINALIZADA`, `ABANDONADA`), `consolidation` y `DELETE` (sobre una `CONFIGURADA`); ningún cuerpo contiene las cadenas `"accion"`, `"clasificacion"`, `"motivo"`, `"feedback"`, `"score"`, `"star"`, `Buena estructura`, `Responde lo pedido`. Una petición con `?feedback=true` y cabecera `X-Include-Feedback: true` da lo mismo.
- `T/architecture/PresentationDoesNotReadFeedbackTest`: ArchUnit, ninguna clase de `..presentation..` depende de `TurnFeedback`, `TurnFeedbackRepository`, `EvaluationResult` ni `TurnFeedbackEntity`.
- `ClosureLogContentTest`: ningún evento de registro contiene `Buena estructura`, `Responde lo pedido` ni el texto de las respuestas.

**Verifica:** `-Dtest='Closure*Test,ConsolidationConcurrencyTest,AbandonVsCloseRaceTest,NoFeedbackExposureTest,PresentationDoesNotReadFeedbackTest,LayeredArchitectureTest'`.

**Foco:** el solape real (un contador demuestra que ambos hilos entraron a `consolidate` antes de que uno confirmara).

## T6 · Postman, ADR, bitácora

- Postman, carpeta «Cerrar la sesión»: «200 respuesta 5 → FINALIZADA» (sigue la carpeta «Responder» hasta la 5), «200 consolidación de una finalizada», «409 consolidar en curso», «404 consolidar ajena», «401 sin identidad», «GET estado FINALIZADA sin retroalimentación», «409 responder finalizada», «409 responder abandonada». Cada una con `pm.test` de estado, `code`, `detail`, `requestId`, `Content-Type` y ausencia de `accion`, `clasificacion`, `motivo`, `feedback`, `star`.
- `docs/adr/0013-cierre-de-la-sesion-y-consolidacion.md` con D-220-1 a D-220-4, alternativas y consecuencias.
- `docs/bitacora-ia/12_cierre-de-sesion_prompt.md` (entrada de D-220-1 a D-220-4; la salida es `specs/CM-220-CerrarSesion/spec.md` §16).

**Verifica:** JAR + `docker compose` + simulador; `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --reporters cli,junit` → 0 fallos.

## T7 · Verificación final (sin commit)

`./mvnw.cmd -B -ntp clean verify` sobre la punta (log en `ejecucion/evidencia/CM-220/verify.log` de la carpeta `Desarrollo`); cobertura ≥ 90 % líneas y ramas en `ConsolidateSessionService`, `ConsolidationRetryPolicy`, `InterviewSession` (método nuevo), `TurnEvaluator` (líneas nuevas), `TurnFeedbackRepositoryAdapter`, `SessionConsolidationFailedException`, `InterviewSessionController.consolidate`; Newman en `ejecucion/evidencia/CM-220/newman.xml`; medición DES-02 (spec §12) con su p95 real.

---

## Resultados de ejecución (12-nov-2026, sobre la punta de CM-216 `fb4e1b6`)

| Tarjeta | Comando | Resultado |
|---|---|---|
| T1 | `-Dtest='BusinessExceptionsTest,BusinessExceptionHandlerTest,ErrorCodeTest,ErrorCodeDocumentationTest'` | `BUILD SUCCESS` (commit `970fc64`) |
| T2 | pruebas de dominio, `ConsolidateSessionServiceTest`, `AnswerPersistenceTest`, `InterviewSchemaMigrationTest` | `BUILD SUCCESS` (commit `eaa24f8`); las pruebas de `ck_sesion_fecha_fin` se añadieron a `InterviewSchemaMigrationTest` en lugar de un `SessionClosureSchemaTest` nuevo |
| T3 | `TurnEvaluatorTest`, `RegisterAnswerServiceTest`, `ReprocessTurnServiceTest`, `AnswerEndToEndTest` | `BUILD SUCCESS` (commit `44225a8`); `AnswerEndToEndTest` espera ahora la sesión `FINALIZADA` tras la respuesta cinco |
| T4 | `ConsolidationControllerTest` y las pruebas de controlador adaptadas al nuevo constructor | `BUILD SUCCESS` (commit `0e3d48f`) |
| T5 | `Closure*Test`, `ConsolidationConcurrencyTest`, `AbandonVsCloseRaceTest`, `NoFeedbackExposureTest`, `PresentationDoesNotReadFeedbackTest`, `LayeredArchitectureTest` | `BUILD SUCCESS`, 19 pruebas, 0 fallos (commit `e5d1ce3`); la falla del cierre se fuerza con un disparador de PostgreSQL que rechaza el paso a `FINALIZADA` |
| T6 | Newman contra el JAR, PostgreSQL, RabbitMQ y el simulador | 159 peticiones, 713 aserciones, 0 fallos (commit `a42d97c`); dos peticiones de «Reintentar evaluación» pasan a esperar 409 `SESSION_ALREADY_CLOSED` |
| Corrección | `RegisterAnswerServiceTest`, `AnswerConcurrencyTest` ×6 | Carrera preexistente de la misma clave en espera corregida (commit `2393fce`) |
| T7 | `./mvnw.cmd -B -ntp clean verify` sobre `2393fce` | `BUILD SUCCESS`, 1710 pruebas, 0 fallos, 1 omitida; cobertura por clase, rendimiento (p95 21,6 ms y 56,1 ms) y rutas al 500 en `ejecucion/evidencia/CM-220/resumen.md` |
