# CM-220 · Cerrar la sesión al quinto turno sin exponer retroalimentación (HU-5.5)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-220 «HU-5.5 – Backend: cerrar la sesión al quinto turno sin exponer retroalimentación» (Por hacer, Paula Andrea Muñoz Delgado, Sprint 5, sin comentarios). Historia CM-302 (Por hacer). Hermana: CM-221 Frontend |
| Repositorio | `cameia-entrevista`, base `origin/develop` = `09bb7ff` más la rama de CM-216 (stack, §14) |
| Backlog | v11.3 (`informacion/P2/11112026_03_Backlog_v11_3.xlsx`), hoja HE-05, HU-5.5, CA-5.5.1 a CA-5.5.11 |
| Estado | **LISTA PARA EJECUTAR** sobre la punta de CM-216 (§19). Decisiones D-220-1 a D-220-4 cerradas por Paula el 7-nov-2026 |
| Migración | Ninguna (§9) |
| Rama | `CM-220-cerrar-sesion`, sale de la punta de `CM-216-registrar-respuesta`; su PR apunta a esa rama hasta que CM-216 se fusione |

## 1. Objetivo

1. Cuando el turno 5 queda `COMPLETED`, la sesión pasa `EN_CURSO → EVALUANDO` **en la misma transacción** que completa el turno (por `answers` o por `reprocess`).
2. Justo después, en la misma petición (D-220-1), una **consolidación** comprueba que los 5 turnos están `COMPLETED` con su `feedback_turno`, registra `fecha_fin` y pasa `EVALUANDO → FINALIZADA`, con hasta 3 intentos y espera creciente (200 ms y 400 ms).
3. `POST /api/v1/interviews/sessions/{sessionId}/consolidation` pide un nuevo ciclo de hasta 3 intentos (botón «Reintentar» de CA-5.5.11).
4. Ningún endpoint de la sesión devuelve retroalimentación, puntaje ni evaluación STAR, en ningún estado.

Los rechazos de `answers` por estado (`SESSION_ABANDONED`, `SESSION_ALREADY_CLOSED`) ya los implementa CM-216 (D-216-6); esta tarea los prueba con la sesión que ella misma deja en `EVALUANDO` o `FINALIZADA`.

## 2. Hechos comprobados

| # | Hecho | Ancla |
|---|---|---|
| F-1 | `SessionStatus.canChangeTo`: `IN_PROGRESS → ABANDONED|EVALUATING`, `EVALUATING → FINISHED`, `FINISHED` y `ABANDONED` sin salida | `domain/model/SessionStatus.java:58-63` (`09bb7ff`) |
| F-2 | `InterviewSession.startEvaluation(Clock)` y `finish(Clock)` existen (`finish` fija `finishedAt`) | `domain/model/InterviewSession.java:113` |
| F-3 | `V1` tiene `feedback_turno` (`uq_feedback_turno_turno`), `ck_sesion_estado` con `EVALUANDO` y `FINALIZADA`, y `ck_sesion_fecha_fin` (`fecha_fin` obligatoria en `ABANDONADA` y `FINALIZADA`, nula en las demás) | `V1__esquema_inicial_entrevista.sql` |
| F-4 | `GetSessionStateService.stateOf` ya responde `EVALUATING`/`FINISHED` con `currentQuestion = 5`, sin pendiente, y `SessionState.interviewFinished()` es `true` en ambos | `application/service/GetSessionStateService.java`, `SessionState.java` |
| F-5 | `StartSessionService` abandona solo la sesión `IN_PROGRESS` previa (`findInProgressByOwner`); una sesión `EVALUANDO` no se abandona | `StartSessionService.java:217` |
| F-6 | CM-216 (stack de abajo) deja: `TurnEvaluator.evaluate` completa el turno en una transacción compartida por `answers` y `reprocess`; `InterviewTurnRepository.countCompleted`; `TurnFeedbackRepository`; `Waiter`; `requireAcceptsAnswers` con los 409 por estado | `specs/CM-216-RegistrarRespuesta/tasks.md` T4, T5, T7 |
| F-7 | El despliegue no fija CPU siempre asignada y limita a 2 conexiones de base: el trabajo después de responder la petición no está garantizado | `.github/workflows/despliegue-continuo.yml` (de DevOps, solo lectura) |
| F-8 | Paula respondió a Frontend el 11-nov: llamar `consolidation` tras la respuesta 200 de la pregunta 5 es seguro | `comunicaciones/seguimiento/respuestas-y-pendientes.md` |

## 3. Hallazgos clasificados

| # | Hallazgo | Clase | Destino |
|---|---|---|---|
| H-1 | CA-5.5.1 cambia el efecto de la respuesta 5 de CM-216 (que deja `EN_CURSO`) | Cambio sin alternativa | REQ-CLS-01 en `TurnEvaluator` (código de CM-216, misma dueña) |
| H-2 | Quién ejecuta la consolidación «en una operación posterior» con la CPU limitada (F-7) | Decisión de Paula | D-220-1 |
| H-3 | Códigos por estado en `answers` | Decisión de Paula (7-nov) | D-220-2; ya aplicada en CM-216 (D-216-6) |
| H-4 | CA-5.5.11 pide 503 sin código | Cambio sin alternativa | `SESSION_CONSOLIDATION_FAILED` (D-220-3) |
| H-5 | Esperas del reintento sin valores | Decisión de Paula | D-220-4 |
| H-6 | Si faltan turnos o retroalimentación, reintentar no sirve | Diseño | REQ-CLS-06 |
| H-7 | «Una entrevista abandonada no gasta la entrevista del Plan Free»: el conteo del Plan Free no existe en este servicio | Fuera de alcance, con destino | §18: lo cumple quien cuente entrevistas solo con `FINALIZADA` |
| H-8 | La sesión se queda `EVALUANDO` si el proceso muere entre la transición y la consolidación | Diseño | `POST …/consolidation` la recupera (REQ-CLS-05); Frontend la llama tras la pregunta 5 (F-8) |
| H-9 | Una sesión `EN_CURSO` con 5 turnos completos creada por CM-216 desplegada sin CM-220 no se cerraría nunca | Inferencia | El stack se despliega junto (riesgo H-16 de CM-216); si existiera una, `consolidation` responde 409 y se corrige a mano. Sin código extra |
| H-10 | La meta de rendimiento de la consolidación estaba «PENDIENTE (Paula)» | Cambio sin alternativa | El anexo ya fija DES-02 (p95 ≤ 2 s en operaciones JSON propias sin IA): §12 |
| H-11 | El reporte (HE-06) leerá `feedback_turno` solo con `FINALIZADA`; el formato de `feedback` lo fijó el spike CM-261 (4 claves) | Hecho | Sin efecto aquí: solo se cuentan filas |

## 4. Requisitos (EARS)

- **REQ-CLS-01** Cuando `TurnEvaluator` complete el turno de la pregunta 5 (`position = 5`) de una sesión `EN_CURSO` (comprobado al releerla, REQ-ANS-18 de CM-216), el sistema debe, en la misma transacción y después de `registerActivity`, contar los turnos `COMPLETED` de la sesión (`countCompleted`, que ya incluye este) y, si son 5, llamar `session.startEvaluation(clock)` y guardar (sin tocar `fecha_fin`). Si son menos de 5, la sesión sigue `EN_CURSO` (CA-5.5.8). La respuesta 200 no cambia de forma (`interviewFinished: true`, `nextQuestion: null`).
- **REQ-CLS-02** Después de confirmar esa transacción, si la sesión quedó `EVALUANDO`, el sistema debe ejecutar un ciclo de consolidación (REQ-CLS-03 y 04) en la misma petición, fuera de la transacción de la evaluación. Si el ciclo falla, la respuesta de `answers` o `reprocess` sigue siendo 200 (la respuesta quedó registrada y evaluada); se registra ERROR `"Consolidación fallida [code=SESSION_CONSOLIDATION_FAILED, sessionId={}, causa={}]"` y la sesión queda `EVALUANDO`.
- **REQ-CLS-03** Un intento de consolidación debe, en una transacción: releer la sesión; si está `FINALIZADA`, terminar sin cambios; si está `EVALUANDO`, comprobar `countCompleted = 5` y `feedbacks.countBySession = 5`, llamar `session.finish(clock)` (`fecha_fin` = ahora, UTC) y guardar. No cambia `fecha_ultima_actividad` ni llama a la IA.
- **REQ-CLS-04** Ante un fallo transitorio (`DataAccessException`, `TransactionException`, `DatabaseUnavailableException` o `SessionConflictException` por versión), el ciclo debe reintentar hasta 3 intentos en total, esperando 200 ms antes del 2.º y 400 ms antes del 3.º con `Waiter` (sin conexión tomada). Agotados, lanza `SessionConsolidationFailedException`.
- **REQ-CLS-05** `POST /api/v1/interviews/sessions/{sessionId}/consolidation` (sin cuerpo; ignora `Idempotency-Key`): sesión propia `EVALUANDO` → un ciclo; si consolida, 200 con el objeto de estado (`status: "FINALIZADA"`, `interviewFinished: true`, `pendingQuestion: null`, `history` con 5); si falla, `503 SESSION_CONSOLIDATION_FAILED` «Ocurrió un error. Inténtalo de nuevo.» y la sesión sigue `EVALUANDO` (CA-5.5.11). Sesión propia `FINALIZADA` → 200 con el estado (repetir es seguro). `CONFIGURADA`, `EN_CURSO` o `ABANDONADA` → `409 SESSION_TRANSITION_NOT_ALLOWED` «Esta entrevista ya no admite esa acción.». Ajena, inexistente o identificador mal escrito → `404 SESSION_NOT_FOUND`. Sin `X-User-Id` → 401.
- **REQ-CLS-06** Si en un intento faltan turnos `COMPLETED` o retroalimentación (conteo ≠ 5), el ciclo no debe reintentar: registra ERROR con `code`, `sessionId` y los dos conteos (números, sin contenido), deja la sesión `EVALUANDO` y lanza `SessionConsolidationFailedException` (CA-5.5.9).
- **REQ-CLS-07** `answers` sobre una sesión `ABANDONADA` → `409 SESSION_ABANDONED`; `EVALUANDO` o `FINALIZADA` → `409 SESSION_ALREADY_CLOSED`; sin guardar, sin IA, sin turno 6 y con el estado intacto (CA-5.5.4, CA-5.5.5). Implementado en CM-216; aquí se prueba de punta a punta con sesiones cerradas por esta tarea.
- **REQ-CLS-08** Ningún endpoint de sesión (`POST` crear, `start`, `answers`, `reprocess`, `state`, `consolidation`, `DELETE`) debe incluir `feedback_turno`, sus claves (`accion`, `clasificacion`, `motivo`, `feedback`), puntajes ni `feedback_star`, en ningún estado (CA-5.5.2). Garantía doble: DTO de lista blanca y regla de arquitectura que impide a `presentation` depender de `TurnFeedback`, `TurnFeedbackRepository` o la entidad.
- **REQ-CLS-09** `GET …/state` de una sesión propia `EVALUANDO` → 200 con `status: "EVALUANDO"`, `pendingQuestion: null`, `progress.currentQuestion: 5`, `history` con las 5 respuestas y sin retroalimentación; `FINALIZADA` → igual con `status: "FINALIZADA"` (CA-5.5.6). Ajena o inexistente → 404 (CA-5.5.7). Ya lo cumple el código de CM-212 y CM-216; aquí se prueba.
- **REQ-CLS-10** Solo las transiciones de F-1 (ASVS 2.3.1).
- **REQ-CLS-11** Dos consolidaciones a la vez (la automática de la respuesta 5 y la del botón, o dos botones) deben dejar una sola `fecha_fin`; la que pierde por versión reintenta, relee `FINALIZADA` y responde 200.

## 5. Criterios de aceptación → requisito → prueba → Postman

| CA | Dueño | Requisito | Prueba | Postman (carpeta «Cerrar la sesión») |
|---|---|---|---|---|
| CA-5.5.1 | B | 01, 02 | `ClosureEndToEndTest.answer_shouldFinishSession_whenQuestionFive` (tras la respuesta 5: sesión `FINALIZADA`, `fecha_fin` no nula); `TurnEvaluatorTest.evaluate_shouldStartEvaluationInSameTransaction_whenFifthCompleted` | «200 respuesta 5 → FINALIZADA» |
| CA-5.5.2 | B | 08 | `NoFeedbackExposureTest.endpoints_shouldNeverExposeFeedback` (7 endpoints × estados alcanzables, con `feedback_turno` real; el JSON no contiene `accion`, `clasificacion`, `motivo`, `feedback`, `score`, `star`); `PresentationDoesNotReadFeedbackTest` | las mismas peticiones con `pm.test` de ausencia |
| CA-5.5.3 | F | — | Frontend (CM-221) | — |
| CA-5.5.4 | F y B | 07 | `ClosureEndToEndTest.answer_shouldReturn409_whenSessionClosed` (caso `ABANDONADA`)` | «409 abandonada» |
| CA-5.5.5 | B | 07 | `ClosureEndToEndTest.answer_shouldReturn409_whenSessionClosed` (recorre `FINALIZADA` y `EVALUANDO`; 0 llamadas a la IA, 0 filas nuevas, sin turno 6, estado igual) | «409 ya cerrada» |
| CA-5.5.6 | F y B | 09 | `ClosureEndToEndTest.answer_shouldKeepEvaluating_whenClosingFailsThreeTimes` (consolidación forzada a fallar) | «GET estado EVALUANDO» |
| CA-5.5.7 | B | 09 | `ClosureEndToEndTest.consolidation_shouldReturn404_whenNotVisible` | «GET estado ajena» |
| CA-5.5.8 | B | 01 | `TurnEvaluatorTest.evaluate_shouldStayInProgress_whenQuestionFourCompleted`; `…_shouldStayInProgress_whenCountBelowFive` (pregunta 5 completada con un conteo simulado de 4: defensa ante dato roto); `ClosureEndToEndTest.consolidation_shouldReturn409_whenNotEvaluating` | — |
| CA-5.5.9 | B | 03, 06 | `ConsolidateSessionServiceTest.consolidate_shouldFinishAndSetEndDate`; `…_shouldFailWithoutRetry_whenFeedbackMissing` | «200 consolidación» |
| CA-5.5.10 | B | 04 | `ConsolidateSessionServiceTest.consolidate_shouldRetryWithGrowingWait_whenTransientFailure` (fallo en 1 y 2, éxito en 3; esperas registradas 200 ms y 400 ms) | — |
| CA-5.5.11 | F y B | 05 | `ClosureEndToEndTest.answer_shouldKeepEvaluating_whenClosingFailsThreeTimes`, `consolidation_shouldRecover_whenFailureDisappears` | «503 consolidación fallida» (con base detenida no se puede simular en Newman: se cubre con la prueba; ver §15) |

## 6. Contrato

Errores con `application/problem+json;charset=UTF-8`, `status`, `code`, `detail`, `requestId`. Objeto de estado: el de `state` (CM-212 y CM-216: `sessionId`, `status`, `answerMode`, `interviewFinished`, `progress`, `pendingQuestion`, `history`).

### 6.1 `POST /api/v1/interviews/sessions/{sessionId}/consolidation`

| Resultado | Cuándo | `code` | `detail` |
|---|---|---|---|
| 200 + objeto de estado `FINALIZADA` | consolidó ahora o ya estaba `FINALIZADA` | — | — |
| 401 | sin `X-User-Id` | `IDENTITY_REQUIRED` | (mensaje existente) |
| 404 | ajena, inexistente o mal escrita (mismo cuerpo) | `SESSION_NOT_FOUND` | No encontramos lo que buscabas. |
| 409 | `CONFIGURADA`, `EN_CURSO`, `ABANDONADA` | `SESSION_TRANSITION_NOT_ALLOWED` | Esta entrevista ya no admite esa acción. |
| 503 | 3 intentos fallidos, o faltan respuestas o retroalimentación | `SESSION_CONSOLIDATION_FAILED` | Ocurrió un error. Inténtalo de nuevo. |

Duración: un intento es una transacción corta; con las esperas, el peor caso ronda 0,6 s más tres transacciones.

### 6.2 `POST …/answers` y `…/reprocess` de la pregunta 5

Sin cambio de forma. Tras la 200, la sesión está `FINALIZADA` (camino normal) o `EVALUANDO` (consolidación fallida); Frontend llama `consolidation` tras la 200 de la pregunta 5 (F-8) y decide por su resultado.

## 7. Validación de entrada

`sessionId` de la ruta: UUID canónico propio; `abc`, vacío, `<script>`, 37 caracteres, `../x`, UUID de otro usuario → 404 `SESSION_NOT_FOUND` con el mismo cuerpo (`SessionCommand.of` + `findOwned`). Cuerpo y `Idempotency-Key` ignorados. Dueño solo de `X-User-Id`.

## 8. Casos borde

| Dimensión | Caso | Resultado | Prueba |
|---|---|---|---|
| Estado | turno 5 en `PENDING`, `PROCESSING` o `RETRYING` (el turno 5 solo existe con los 4 anteriores completos) | sigue `EN_CURSO`; `consolidation` → 409 | `ClosureEndToEndTest` |
| Estado | pregunta 5 completada pero el conteo da 4 (dato roto) | sigue `EN_CURSO`; WARN con `sessionId` y conteo | `TurnEvaluatorTest` |
| Estado | 5 completos pero uno sin `feedback_turno` (dato roto) | 503 sin reintento; `EVALUANDO` | `ConsolidateSessionServiceTest` |
| Estado | consolidar `CONFIGURADA`, `EN_CURSO`, `ABANDONADA` | 409 | `ClosureEndToEndTest` |
| Idempotencia | consolidar `FINALIZADA` 3 veces | 3 × 200, la misma `fecha_fin` | `ClosureEndToEndTest.consolidation_shouldBeRepeatable_whenAlreadyFinished` |
| Idempotencia | respuesta 5 repetida con la misma clave | el mismo 200; una sola transición; sin nueva consolidación | `ClosureEndToEndTest.answer_shouldReplay_whenQuestionFiveRepeated` |
| Concurrencia | dos consolidaciones a la vez | una `fecha_fin`; ambas 200 | `ConsolidationConcurrencyTest` |
| Concurrencia | iniciar otra entrevista mientras la respuesta 5 se evalúa | si el inicio gana, la sesión queda `ABANDONADA`; al guardar la evaluación, `TurnEvaluator` relee la sesión y la rechaza antes de escribir (REQ-ANS-18 de CM-216, D-216-10): `409 SESSION_ABANDONED`, turno 5 `RETRYING`, ejecución `FALLIDA` con `SESSION_ABANDONED`, sin `feedback_turno`, sin transición a `EVALUANDO` y sin consolidación. Si gana la respuesta, la sesión queda `EVALUANDO` y el inicio no la abandona (F-5) | `AbandonVsCloseRaceTest` |
| Falla a medias | la base cae entre la transición y la consolidación | sesión `EVALUANDO`; `consolidation` la recupera | `ClosureEndToEndTest.consolidation_shouldRecover_whenFailureDisappears` |
| Fechas | `fecha_fin` con reloj fijo `2026-11-12T15:05:00Z`, igual o posterior a `fecha_creacion` | cumple `ck_sesion_fecha_fin` | `ConsolidateSessionServiceTest` |
| Reintento | esperas | 200 ms y 400 ms exactos | `ConsolidateSessionServiceTest` |
| Ataque | `?feedback=true`, cuerpo `{"feedback":true}`, cabecera `X-Include-Feedback: true` | se ignoran | `NoFeedbackExposureTest` |
| Datos | sesión con 5 `feedback_turno` | nada aparece en ninguna respuesta | `NoFeedbackExposureTest` |
| No aplica | longitudes, Unicode, enumerados de entrada | el endpoint no recibe cuerpo | — |

## 9. Base de datos

Sin migración: `EVALUANDO`, `FINALIZADA`, `fecha_fin`, `feedback_turno` y sus restricciones existen (F-3); los índices y el contador son de `V8` (CM-216). `SessionClosureSchemaTest` confirma que `FINALIZADA` sin `fecha_fin` y `EVALUANDO` con `fecha_fin` violan `ck_sesion_fecha_fin`. Si una tarjeta necesitara migración, toma `V9` y se detiene para avisar.

## 10. Errores

Código nuevo, al final de `ErrorCode` (después de los de CM-216): `SESSION_CONSOLIDATION_FAILED`. Excepción `SessionConsolidationFailedException` (`MESSAGE` = «Ocurrió un error. Inténtalo de nuevo.»), fila en `DEFINITIONS` (503, título «Cierre pendiente») y en `docs/errores.md` (endpoint `consolidation`). Reutilizados: `SESSION_NOT_FOUND`, `SESSION_TRANSITION_NOT_ALLOWED`, `IDENTITY_REQUIRED`, `SESSION_CONFLICT` (consolidación que pierde por versión, REQ-CLS-04), `SESSION_ABANDONED` (carrera con un inicio, §8). Ninguna ruta de §6 y §8 llega al genérico: se prueba con petición real.

## 11. Seguridad

| ID | Aplica | Cómo | Prueba |
|---|---|---|---|
| OWASP API1 (BOLA) | sí | 404 idéntico para ajena e inexistente | CA-5.5.7 |
| API3 (propiedades) | **sí, la central** | DTO de lista blanca; ArchUnit | `NoFeedbackExposureTest`, `PresentationDoesNotReadFeedbackTest` |
| API4 / API6 | sí | ciclo acotado a 3 intentos y < 1 s; sin IA | `ConsolidateSessionServiceTest` |
| API5 | no | sin roles | — |
| ASVS 2.3.1 | sí | solo F-1 | `SessionStatusTest` (existente), `ClosureEndToEndTest` |
| ASVS 4.1.1 | sí | `Content-Type` con charset | Newman |
| ASVS 8.2.2 | sí | dueño de `X-User-Id` | CA-5.5.7 |
| ASVS 15.3.1 | sí | DTO | `NoFeedbackExposureTest` |
| SEG-04 | sí | registros con `code`, `sessionId`, conteos; sin contenido | `ClosureLogContentTest` |

## 12. Atributos de calidad (épica HE-05)

| Atributo | Métrica (anexo) | Límite | Cómo | Prueba o medición | Resultado |
|---|---|---|---|---|---|
| AC-0004 | FIA-01: todo fallo conserva el último estado confirmado | 100 % | `EVALUANDO` con 5 respuestas ante cualquier fallo; recuperable | `…_shouldRecover_…`, `answer_shouldKeepEvaluating_whenClosingFailsThreeTimes` | PENDIENTE de ejecutar |
| AC-0001 | IA-06: cero reportes de sesiones no finalizadas (lo que aporta esta tarea: ninguna sesión llega a `FINALIZADA` sin 5 turnos completos y 5 retroalimentaciones; el reporte en sí es de HE-06) | 0 | REQ-CLS-03 y REQ-CLS-06 | `ConsolidateSessionServiceTest.consolidate_shouldFinishAndSetEndDate`, `…_shouldFailWithoutRetry_whenFeedbackMissing` | PENDIENTE |
| AC-0004 | FIA-02: repetir no duplica efectos | 0 | consolidar `FINALIZADA` no cambia `fecha_fin` | `…_shouldBeRepeatable` | PENDIENTE |
| AC-0003 | SEG-02, SEG-04 | 100 % / 0 | §11 | §11 | PENDIENTE |
| AC-0002 | DES-02: p95 ≤ 2 s en operaciones JSON propias sin IA | ≤ 2 s | transacción corta; esperas 0,6 s solo con fallos | medición local: 50 `consolidation` sobre `FINALIZADA` y 20 sobre `EVALUANDO`, p95 reportado en el PR | PENDIENTE |
| AC-0002 | DES-04: cero turnos confirmados perdidos | 0 | la consolidación no borra ni reescribe turnos | `ConsolidateSessionServiceTest` | PENDIENTE |
| MAN-01 | cobertura de lo nuevo | ≥ 90 % líneas y ramas | §14 de tareas | `jacoco.csv` | PENDIENTE |
| MAN-03 | sin FK entre contextos | 0 | sin migración | revisión | PENDIENTE |
| IOP-01 | OpenAPI de `consolidation` | 100 % | `@Operation`, `@ApiResponse` 200/401/404/409/503/500 | `/v3/api-docs` | PENDIENTE |
| AC-0006 | errores con `code` | 100 % | §10 | `ErrorCodeDocumentationTest`, Newman | PENDIENTE |

## 13. Integración

CM-216 (stack de abajo: `TurnEvaluator`, `Waiter`, conteos, 409 por estado), CM-224 (stack de arriba: cambia la llamada al modelo dentro de `TurnEvaluator`, no la consolidación), CM-221 (Frontend: «Procesando tu respuesta…» en `EVALUANDO`, mensaje de cierre en `FINALIZADA`, «Reintentar» con `consolidation`), HE-06 (lee `feedback_turno` solo en `FINALIZADA`).

| Pieza compartida | Lo que agrega CM-220 |
|---|---|
| `ErrorCode` | `SESSION_CONSOLIDATION_FAILED` al final |
| `docs/errores.md` | fila de `SESSION_CONSOLIDATION_FAILED`; `consolidation` en la columna de endpoints de los reutilizados |
| Colección Postman | carpeta «Cerrar la sesión» |
| `TurnEvaluator` | transición a `EVALUANDO` dentro de la transacción de éxito y llamada al ciclo de consolidación después |

## 14. Dependencias de orden

1. Rama `CM-220-cerrar-sesion` desde la punta de `CM-216-registrar-respuesta` (stack); PR hacia esa rama.
2. Al fusionarse CM-216: rebase con `--onto develop <base-vieja> CM-220-cerrar-sesion`, guardando antes SHA y árbol; el PR cambia su base a `develop`.
3. Línea base: `./mvnw.cmd -B -ntp clean verify` sobre la punta de CM-216 en verde antes de T1.

## 15. Datos de otros

| Dato | Estado | Efecto |
|---|---|---|
| Formato de `feedback` | Fijado (spike CM-261, 4 claves, CM-216) | Solo se cuentan filas |
| Falla de la base en Newman | No simulable sin detener la base del JAR | CA-5.5.11 se cubre con `ClosureEndToEndTest` (la prueba bloquea el cierre con un disparador de PostgreSQL y recorre el camino real de tres intentos) |

## 16. Decisiones (Paula, 7-nov-2026)

| # | Asunto | Decisión | Alternativas descartadas |
|---|---|---|---|
| D-220-1 | Quién ejecuta la consolidación | **A:** en la misma petición de la respuesta 5, justo después de confirmar la transición, en transacciones propias con hasta 3 intentos | B: en segundo plano después de responder (la CPU de Cloud Run se limita sin petición); C: proceso periódico (programación nueva y DevOps) |
| D-220-2 | Códigos de `answers` según el estado | **A:** `SESSION_ABANDONED` y `SESSION_ALREADY_CLOSED`; `CONFIGURADA` con `SESSION_TRANSITION_NOT_ALLOWED` (aplicado en CM-216) | B: un solo código |
| D-220-3 | Código de la consolidación fallida | **A:** `SESSION_CONSOLIDATION_FAILED` | B: `EVALUATION_CONSOLIDATION_FAILED` |
| D-220-4 | Esperas entre intentos | **A:** 200 ms y 400 ms | B: 1 s y 2 s (la petición duraría hasta 3 s) |

## 17. Aviso a Frontend (para el documento único del rol, sin enviar)

CM-221: tras la 200 de la pregunta 5, llamar `POST …/consolidation`: 200 con `FINALIZADA` → mensaje de cierre; 503 `SESSION_CONSOLIDATION_FAILED` → «Ocurrió un error. Inténtalo de nuevo.» con «Reintentar» (vuelve a llamar `consolidation`). Al recargar, `state` con `EVALUANDO` → «Procesando tu respuesta…» y llamar `consolidation`. Ninguna respuesta trae retroalimentación. Ya respondido en parte el 11-nov (F-8); lo nuevo es solo el uso de `consolidation` al recargar en `EVALUANDO`.

## 18. Fuera de alcance

Reporte de Evaluación (HE-06); conteo del Plan Free (no existe en este servicio); reintentos automáticos de la evaluación por turno (CM-224); abandono por inactividad (HU-5.7); Jira.

## 19. Hechos, inferencias y puerta de listo

- Hechos: §2 con ancla; CA de §5 (libro v11.3).
- Inferencias: H-9; REQ-CLS-04 incluye `SessionConflictException` como transitorio (la otra consolidación ganó: releer resuelve).
- Decisiones humanas: D-220-1 a D-220-4 (Paula, 7-nov-2026).

**Puerta: LISTA PARA EJECUTAR** sobre la punta de CM-216: tabla CA → prueba → Postman (§5), atributos con métrica (§12), bordes (§8), genérico cerrado (§10), seguridad (§11), integración (§13), línea base (§14) y tarjetas con literales (`tasks.md`). Solo Paula aprueba.
