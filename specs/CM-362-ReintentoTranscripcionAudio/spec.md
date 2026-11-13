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
| CA-5.13.3 | Segundo intento fallido, o primero que agota `TRANSCRIPTION_TIMEOUT` (60 s), o fallo no transitorio: no hay otro intento, no se registra, la pregunta sigue pendiente (turno `RETRYING`) y responde 503 `TRANSCRIPTION_UNAVAILABLE` con el mensaje del criterio | `submit_shouldGiveUpWithoutThirdAttempt…`, `submit_shouldNotRetry_whenFirstAttemptTimesOut`, `TranscriptionRetryPolicyTest`, E2E y Postman |
| CA-5.13.4 | El audio solo vive en memoria y se sobrescribe al terminar (de CM-345 y CM-349) | pruebas de CM-345 y CM-349 |
| CA-5.13.5, CA-5.13.6 | La misma pregunta y el mismo `turnId` siguen pendientes; se puede grabar de nuevo. Responder por texto es HU-5.2 (sin backend todavía) | E2E `submit_shouldGiveUpAfterSecondAttempt`, Postman |
| CA-5.13.7 | Una sola respuesta definitiva por pregunta (índice `uq_intento_definitivo_por_turno` y turno `COMPLETED`) | E2E, Postman (409 `TURN_TRANSITION_NOT_ALLOWED`) |
| CA-5.13.8 | Durante los dos intentos el turno sigue reservado en `PROCESSING`: otro envío del mismo turno responde 409 `SESSION_CONFLICT` sin transcribir ni guardar | `submit_shouldKeepTurnReservedDuringRetry…` |

Cada intento queda en `ejecucion_ia` (`TRANSCRIBIR`, `numero_intento` 1 o 2, `EXITOSA` o `FALLIDA`).

## 3. Cambios

- `TranscriptionFailure` (`TRANSIENT`, `TIMEOUT`, `REJECTED`) y `TranscriptionUnavailableException.failure()`.
- `AssemblyAiTranscriptionAdapter` clasifica: 503 y conexión rechazada o no abierta → `TRANSIENT`; tiempo agotado →
  `TIMEOUT`; el resto → `REJECTED`.
- `TranscriptionRetryPolicy`: 2 intentos; solo `TRANSIENT` del primero y solo si el reintento cabe en
  `TRANSCRIPTION_TOTAL_BUDGET` (110 s, por debajo de los 120 s del Gateway).
- `SubmitAudioAnswerService`: bucle de intentos sin transacción abierta, ejecución por intento, reserva del turno
  calculada sobre la duración máxima de todos los intentos.
- `TranscriptionExecution` admite el número de intento.
- Simulador de Postman: escenario `stuck`; un 503 único ya no basta para ver el 503 (la carpeta de CM-345 programa dos).

## 4. Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `TRANSCRIPTION_TIMEOUT` | `60s` (antes 90 s en CM-349) | Tope de **cada** intento (CA-5.13.3) |
| `TRANSCRIPTION_RETRY_PAUSE` | `1s` | Pausa antes del reintento |
| `TRANSCRIPTION_TOTAL_BUDGET` | `110s` | Tope de todos los intentos de un envío |

## 5. Decisiones (pendientes de ratificar)

| # | Decisión | Por qué |
|---|---|---|
| D-362-1 | AssemblyAI es el único proveedor de transcripción; se retiran el Servicio de voz provisional y su WireMock | Decisión humana del 12-nov-2026 |
| D-362-2 | Transitorio = 503, conexión rechazada o que no se abre; tiempo agotado y cualquier otro fallo no se reintentan | Lectura literal de CA-5.13.1 y CA-5.13.3 |
| D-362-3 | El turno se mantiene en `PROCESSING` entre intentos (no pasa a `RETRYING`) | `RETRYING` significa «pregunta pendiente tras un fallo» desde CM-345 y admite un envío nuevo; CA-5.13.8 exige rechazarlo |
| D-362-4 | Cada intento se registra en `ejecucion_ia` con su número | CA-5.12.3 ya registra el primero; el segundo deja evidencia del reintento |
| D-362-5 | Presupuesto total de 110 s | Un 503 tardío más un segundo intento de 60 s pasaría los 120 s del Gateway |
| D-362-6 | Doble envío responde 409 `SESSION_CONFLICT` (código de CM-345), no un código nuevo | La web trata cualquier 409 igual; se evita otro código |

## 6. Fuera de alcance

Responder por texto (HU-5.2), pasar las preguntas a voz con Gemini TTS (otra historia), cierre de la entrevista (HU-5.5).
