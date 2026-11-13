# CM-362 · Reintento automático de la transcripción del audio (HU-5.13)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-362 (HU-5.13). No se pudo leer desde la sesión de IA; la fuente son los criterios CA-5.13.1 a CA-5.13.8 que entregó el solicitante |
| Repositorio | `cameia-entrevista`. Rama apilada: fusiona `CM-345-respuesta-por-voz`, que a su vez fusiona `CM-349-transcripcion-respuesta-voz` |
| Orden de fusión | CM-349 → CM-345 → CM-362 (decisión de integración del 12-nov-2026, bitácora 13 y 14) |
| Estado | Implementada sin aprobación previa por indicación del solicitante; las decisiones de §5 se ratifican en el PR |

## 1. Objetivo

Sobre `POST /api/v1/interviews/sessions/{sessionId}/answers/audio` (CM-349 y CM-345), reintentar una vez y sin
intervención de la persona la transcripción con AssemblyAI cuando el primer intento falla por un error transitorio.

## 2. Criterios y dónde se cumplen

| CA | Comportamiento | Prueba |
|---|---|---|
| CA-5.13.1, CA-5.13.2 | Un 503 o una conexión rechazada del primer intento se reintenta tras `TRANSCRIPTION_RETRY_PAUSE` (1 s); con éxito se registra una sola respuesta y se avanza | `SubmitAudioAnswerServiceTest.submit_shouldRetryOnce…`, `AudioAnswerEndToEndTest.submit_shouldRetryTransparently…`, Postman carpeta «Reintento de la transcripción (HU-5.13)» |
| CA-5.13.3 | Segundo intento fallido, o primero que agota `TRANSCRIPTION_TIMEOUT` (60 s), o fallo no transitorio: no hay otro intento, no se registra, la pregunta sigue pendiente (turno `PENDING`, ver §7) y responde 503 `TRANSCRIPTION_UNAVAILABLE` con el mensaje del criterio | `submit_shouldGiveUpWithoutThirdAttempt…`, `submit_shouldNotRetry_whenFirstAttemptTimesOut`, `TranscriptionRetryPolicyTest`, E2E y Postman |
| CA-5.13.4 | El audio solo vive en memoria y se sobrescribe al terminar (de CM-345 y CM-349) | pruebas de CM-345 y CM-349 |
| CA-5.13.5, CA-5.13.6 | La misma pregunta y el mismo `turnId` siguen pendientes; se puede grabar de nuevo. También se puede responder por texto (HU-5.2, CM-216) | E2E `submit_shouldGiveUpAfterSecondAttempt`, `submit_shouldAllowWrittenAnswerAfterTranscriptionFails` |
| CA-5.13.7 | Una sola respuesta definitiva por pregunta (índice `uq_intento_definitivo_por_turno` y turno `COMPLETED`) | E2E (409 `ANSWER_ALREADY_EXISTS`) |
| CA-5.13.8 | Durante los dos intentos el turno sigue reservado en `PROCESSING`: otro envío del mismo turno responde 409 `TURN_CONFLICT` sin transcribir ni guardar | `submit_shouldKeepTurnReservedDuringRetry…` |

Cada intento queda en `ejecucion_ia` (`TRANSCRIBIR`, `numero_intento` 1 o 2, `EXITOSA` o `FALLIDA`).

## 3. Cambios

- `TranscriptionFailure` (`TRANSIENT`, `TIMEOUT`, `REJECTED`) y `TranscriptionUnavailableException.failure()`.
- `AssemblyAiTranscriptionAdapter` clasifica: 503 y conexión rechazada o no abierta → `TRANSIENT`; tiempo agotado →
  `TIMEOUT`; el resto → `REJECTED`.
- `TranscriptionRetryPolicy`: 2 intentos; solo `TRANSIENT` del primero y solo si el reintento cabe en
  `TRANSCRIPTION_TOTAL_BUDGET` (100 s; con la evaluación queda por debajo de los 120 s del Gateway).
- `SubmitAudioAnswerService`: bucle de intentos sin transacción abierta, ejecución por intento, reserva del turno
  calculada sobre la duración máxima de todos los intentos.
- `TranscriptionExecution` admite el número de intento.
- Simulador de Postman: escenario `stuck`; un 503 único ya no basta para ver el 503 (la carpeta de CM-345 programa dos).

## 4. Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `TRANSCRIPTION_TIMEOUT` | `60s` (antes 90 s en CM-349) | Tope de **cada** intento (CA-5.13.3) |
| `TRANSCRIPTION_RETRY_PAUSE` | `1s` | Pausa antes del reintento |
| `TRANSCRIPTION_TOTAL_BUDGET` | `100s` (110 s antes de integrar `develop`) | Tope de todos los intentos de un envío |

## 5. Decisiones (pendientes de ratificar)

| # | Decisión | Por qué |
|---|---|---|
| D-362-1 | AssemblyAI es el único proveedor de transcripción; se retiran el Servicio de voz provisional y su WireMock | Decisión humana del 12-nov-2026 |
| D-362-2 | Transitorio = 503, conexión rechazada o que no se abre; tiempo agotado y cualquier otro fallo no se reintentan | Lectura literal de CA-5.13.1 y CA-5.13.3 |
| D-362-3 | El turno se mantiene en `PROCESSING` entre intentos (no pasa a `RETRYING`) | `RETRYING` significa «pregunta pendiente tras un fallo» desde CM-345 y admite un envío nuevo; CA-5.13.8 exige rechazarlo |
| D-362-4 | Cada intento se registra en `ejecucion_ia` con su número | CA-5.12.3 ya registra el primero; el segundo deja evidencia del reintento |
| D-362-5 | Presupuesto total de 110 s (100 s desde §7) | Un 503 tardío más un segundo intento de 60 s pasaría los 120 s del Gateway |
| D-362-6 | Doble envío responde 409 (hoy `TURN_CONFLICT`, el de la respuesta escrita; ver §7) | La web trata cualquier 409 igual; se evita otro código |

## 6. Fuera de alcance

Responder por texto (HU-5.2), pasar las preguntas a voz con Gemini TTS (otra historia), cierre de la entrevista (HU-5.5).

## 7. Integración con `develop` (CM-216, CM-220, CM-224)

CM-349, CM-345 y CM-362 se integran en un solo PR (rama `CM-349-345-362-respuesta-por-voz`) sobre `develop`, que ya
trae la respuesta escrita con evaluación (CM-216), el cierre de la sesión (CM-220) y el respaldo de la IA (CM-224).
`develop` manda; la voz se adapta a su flujo:

| # | Decisión | Por qué |
|---|---|---|
| D-INT-1 | La migración de voz pasa a `V9__respuesta_por_voz.sql` y la regla de la clave admite `CREAR_SESION`, `RESPONDER` y `RESPONDER_AUDIO` | `develop` ya usa V8 y las dos borran y recrean la misma regla |
| D-INT-2 | El texto transcrito entra al mismo registro y evaluación de la respuesta escrita (`AnswerRegistrar.startEvaluation` + `TurnEvaluator`): se guarda la retroalimentación y la quinta respuesta cierra y consolida la sesión | Sin evaluación la consolidación (5 turnos y 5 retroalimentaciones) nunca se cumpliría |
| D-INT-3 | Si la transcripción falla, el turno vuelve a `PENDING` (transición nueva `PROCESSING → PENDING`), no a `RETRYING` | En `develop`, `RETRYING` es «respuesta guardada con evaluación fallida» y habilita Reintentar; la respuesta escrita solo acepta `PENDING` (CA-5.13.6) |
| D-INT-4 | Una evaluación fallida tras la voz deja el turno en `RETRYING` con 503 `LLM_*`, como la escrita; se reintenta con `POST .../turns/{turnId}/reprocess` sin volver a transcribir | La respuesta ya está guardada |
| D-INT-5 | Reintentar un turno sin respuesta guardada responde 409 `TURN_NOT_RETRYABLE` (antes, error 500) | Una voz que se transcribe no tiene evaluación en curso y parecería abandonada |
| D-INT-6 | Códigos comunes: pregunta ya respondida 409 `ANSWER_ALREADY_EXISTS`, doble envío 409 `TURN_CONFLICT` | El Front maneja igual voz y texto |
| D-INT-7 | `TRANSCRIPTION_TOTAL_BUDGET` baja a 100 s y la reserva del turno suma el ciclo de evaluación (~10,5 s) | Transcripción + evaluación deben caber en los 120 s del Gateway |
| D-INT-8 | La respuesta del endpoint de voz usa `AnswerResultResponse`, la misma forma que la escrita | Un solo contrato de avance |

Pendiente para otra sesión: colección y simulador de Postman (se tomaron los de `develop`; faltan las carpetas de voz,
reintento y proveedores reales), ADR y bitácora (numeración 0012-0013 y 11-14 choca con `develop`) y la guía de pruebas.
