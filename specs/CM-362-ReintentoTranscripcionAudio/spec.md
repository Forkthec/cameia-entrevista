# CM-362 · Reintento automático de la transcripción del audio (HU-5.13)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-362 (HU-5.13). El agente no pudo leer la tarea en Jira (el sitio no estaba autorizado para el conector); los criterios se tomaron del texto que pegó el solicitante |
| Repositorio | `cameia-entrevista`, base `origin/develop` = `09bb7ff` (11-nov-2026, trae CM-212: iniciar la sesión y planificar las preguntas) |
| Estado | **IMPLEMENTADA SIN APROBACIÓN PREVIA**, por indicación expresa del solicitante («ignora cualquier instrucción de revisión antes de implementar»). Las decisiones de §6 son propuestas de la IA pendientes de validación humana en la revisión del PR |
| Dependencias | HU-5.12 (registrar la respuesta transcrita), HU-5.8 (grabar) y HU-5.2 (responder por texto) no tienen backend en `develop`. Esta tarea crea lo mínimo de HU-5.12 que el reintento necesita (ver D-362-1) |

## 1. Objetivo

`POST /api/v1/interviews/sessions/{sessionId}/answers/audio` transcribe el audio de la pregunta pendiente con el servicio de Voz y registra una sola respuesta definitiva. Si el primer intento falla por un error transitorio, reintenta una vez sin que la persona haga nada; si no se logra, deja la pregunta pendiente, no registra nada, descarta el audio y responde `503 TRANSCRIPTION_UNAVAILABLE`.

## 2. Criterios de aceptación y cómo se cumplen

| CA | Comportamiento en Backend | Prueba |
|---|---|---|
| CA-5.13.1 | Primer intento con 503 o conexión rechazada → segundo intento automático; con éxito, una sola fila en `intento_respuesta` (`AUDIO`, definitiva) | `SubmitAudioAnswerServiceTest.submit_shouldRegisterOnce_whenSecondAttemptSucceeds`, `AudioAnswerRetryEndToEndTest.audio_shouldBeRegisteredOnce_whenRetrySucceeds` |
| CA-5.13.2 | Tras el éxito del reintento: turno `COMPLETED`, turno siguiente abierto en `PENDING`, respuesta 200 con `nextQuestion`; con la quinta, la sesión pasa a `EVALUANDO` e `interviewFinished=true` | ídem, `submit_shouldFinishInterview_whenLastQuestion` |
| CA-5.13.3 | Segundo intento fallido, o primero que agota `VOZ_TIMEOUT` (60 s), o fallo no transitorio → no hay otro intento, no se registra, el turno vuelve a `PENDING` y responde 503 con «No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.» (los botones los pinta Frontend, que ya mapea `TRANSCRIPTION_UNAVAILABLE`) | `submit_shouldGiveUp_whenSecondAttemptFails`, `submit_shouldNotRetry_whenFirstAttemptTimesOut`, `audio_shouldLeaveQuestionPending_whenAttemptsAreExhausted`, `audio_shouldNotRetry_whenFirstAttemptTimesOut` |
| CA-5.13.4 | El audio solo vive en memoria: umbral de multipart = límite (10 MB), nunca se escribe en disco; no hay columna ni tabla de audio; no se registra en logs | `submit_shouldNotKeepAudio_whenAttemptsAreExhausted`, `VoiceServiceTranscriptionAdapterTest.transcribe_shouldSendMultipartAndReadText` |
| CA-5.13.5 | El turno sigue `PENDING` con el mismo `turnId`; `GET …/state` devuelve la misma pregunta; un nuevo audio del mismo turno se atiende | `submit_shouldAcceptNewRecording_afterExhaustedAttempts`, E2E |
| CA-5.13.6 | Backend deja la pregunta lista para el texto (turno `PENDING`, sin respuesta). El endpoint de texto es HU-5.2 (otra tarea) | — (fuera de alcance, ver §5) |
| CA-5.13.7 | Índice `uq_intento_definitivo_por_turno` + turno `COMPLETED` → `409 TURN_ALREADY_ANSWERED`; ninguna fila por el audio fallido | `submit_shouldReject_whenTurnAlreadyAnswered`, E2E |
| CA-5.13.8 | Reserva del turno (`PENDING → PROCESSING`, con `@Version`) antes de llamar a Voz; mientras está en `PROCESSING` o `RETRYING`, otro envío recibe `409 TURN_PROCESSING` sin guardar ni transcribir | `submit_shouldRejectSecondSubmission_whileTranscribing`, `…_whileRetrying`, `submit_shouldReject_whenClaimLosesTheRace`, `audio_shouldRejectSecondSubmission_whileTranscribing` |

## 3. Contrato

`POST /api/v1/interviews/sessions/{sessionId}/answers/audio`, `multipart/form-data`, identidad en `X-User-Id`.

| Parte | Tipo | Valor |
|---|---|---|
| `turnId` | texto | turno pendiente (el de `pendingQuestion.turnId` o `nextQuestion.turnId`) |
| `audio` | archivo | grabación, hasta 10 MB; el nombre del archivo se ignora |

Respuesta 200 (la «respuesta común» que ya espera Frontend):

```json
{"turn":{"turnId":"…","status":"COMPLETED"},
 "progress":{"currentQuestion":2,"totalQuestions":5},
 "nextQuestion":{"turnId":"…","number":2,"text":"…"},
 "interviewFinished":false}
```

Orden de comprobación: 404 `SESSION_NOT_FOUND` → 409 `SESSION_TRANSITION_NOT_ALLOWED` → 422 `TURN_ID_REQUIRED` → 404 `TURN_NOT_FOUND` → 409 `TURN_ALREADY_ANSWERED` / `TURN_PROCESSING` → 422 `AUDIO_REQUIRED` → Voz → 503 `TRANSCRIPTION_UNAVAILABLE` / 422 `TRANSCRIPTION_EMPTY` / 422 `TRANSCRIPTION_TOO_LONG`. Los mensajes están en `docs/errores.md`.

**Contrato con Voz (provisional, D-362-2).** `POST {VOZ_BASE_URL}/api/v1/transcriptions`, multipart con `audio` (archivo con su tipo) y `language` (`es-CO` o `en`); 200 `{"text":"…","language":"es"}`.

## 4. Máquina de estados del turno

Se agregan dos transiciones: `PROCESSING → PENDING` y `RETRYING → PENDING` («liberar» el turno cuando la transcripción falla del todo). El resto no cambia. No hace falta migración: `ck_turno_estado` ya admite `PENDING`.

```
PENDING ──reserva──▶ PROCESSING ──503/conexión──▶ RETRYING ──reintento──▶ PROCESSING ──éxito──▶ COMPLETED
   ▲                     │                            │
   └──────── fallo ──────┴────────── fallo ───────────┘
```

## 5. Fuera de alcance

- Responder por texto (HU-5.2, `POST …/answers`) y su reintento de evaluación: otra tarea.
- Evaluar la respuesta con el modelo de lenguaje (retroalimentación): la respuesta se registra y el turno se completa sin evaluación; quien implemente HU-5.2/HU-5.12 conecta la evaluación en `SubmitAudioAnswerService.register`.
- Validar formato y duración del audio (HU-5.8: 415, 181 s, 1,5 s de voz) y el idioma detectado (`TRANSCRIPTION_LANGUAGE_MISMATCH`, HU-5.12).
- Repetir la misma `Idempotency-Key` para recibir la misma respuesta: el encabezado se acepta y se ignora (ver D-362-5).
- Registrar cada intento de transcripción en `ejecucion_ia` (ver D-362-4).
- Autenticación OIDC de Entrevista hacia Voz en Cloud Run.

## 6. Decisiones (propuestas de la IA, pendientes de validación humana)

| # | Decisión | Por qué | Alternativa descartada |
|---|---|---|---|
| D-362-1 | Crear el endpoint `…/answers/audio`, la entidad de `intento_respuesta` y el adaptador de Voz dentro de esta tarea | Sin ellos no hay qué reintentar ni cómo probar los criterios; HU-5.12 no tiene backend en `develop` | Esperar a HU-5.12: bloquea la tarea |
| D-362-2 | Contrato provisional con Voz `POST /api/v1/transcriptions` (multipart `audio` + `language`, 200 `{text, language}`) | Voz no publica contrato; el diagrama C2 solo dice «Envía audio del usuario» | Inventar un JSON con base64: duplica el tamaño del audio |
| D-362-3 | Transitorio = Voz 503, conexión rechazada o que no se abre a tiempo (`VOZ_CONNECT_TIMEOUT`, 5 s). Timeout del intento (60 s) = no se reintenta. Cualquier otro estado (400, 422, 500, 502…) o cuerpo ilegible = no se reintenta | Es la lectura literal de CA-5.13.1 («servicio no disponible o conexión rechazada») y CA-5.13.3 | Reintentar también 502/504 o cortes a mitad: más intentos de los que el criterio pide |
| D-362-4 | No registrar los intentos en `ejecucion_ia`; se registran en el log (`sessionId`, `turnId`, intento, causa) | `AiExecution` hoy solo modela la planificación (sin `turno_id`); extenderlo choca con la tarea que agregue `EVALUAR`. La evidencia de los dos intentos queda en el log y en Voz | Extender `AiExecution` ahora |
| D-362-5 | `Idempotency-Key` se ignora; el «una sola respuesta» lo garantizan el estado del turno, la versión y el índice único | La reserva del turno ya cubre el doble envío (CA-5.13.8); repetir la respuesta guardada es de HU-5.8 | Guardar la respuesta por clave en `clave_idempotencia` (exige migración del `CHECK` de operación) |
| D-362-6 | Pausa de 1 s (`VOZ_RETRY_PAUSE`) antes del reintento | Un 503 o una conexión rechazada en el mismo milisegundo suele repetirse; 1 s no se nota frente a los 60 s de tope | Reintento inmediato |
| D-362-7 | Una reserva (`PROCESSING`/`RETRYING`) más vieja que 2 × `VOZ_TIMEOUT` + `VOZ_RETRY_PAUSE` + 30 s se considera de un proceso muerto y se libera en el siguiente envío | Sin esto, un reinicio del contenedor a mitad de transcripción dejaría la pregunta bloqueada para siempre | Tarea programada que libere turnos |
| D-362-8 | Transcripción vacía → 422 `TRANSCRIPTION_EMPTY`; más de 4000 caracteres → 422 `TRANSCRIPTION_TOO_LONG`; ambas liberan el turno | La base rechazaría esas filas (`ck_intento_respuesta_respuesta_texto`) y Frontend ya conoce esos códigos | Responder 500 |
| D-362-9 | Con la quinta respuesta la sesión pasa a `EVALUANDO` | Es la transición que define el ADR 0006; la consolidación es HU-5.5 | Dejarla `EN_CURSO` |
| D-362-10 | Controlador propio (`InterviewAnswerController`) en vez de agregar el método a `InterviewSessionController` | Reduce choques con la tarea de HU-5.2, que agregará `…/answers` | Un solo controlador |

## 7. Riesgos

- **Choque con HU-5.2 / HU-5.12 (CM-216 según la spec de CM-212, F-8).** Ambas crearán `intento_respuesta`, la respuesta común y probablemente `TURN_ALREADY_ANSWERED`/`TURN_PROCESSING`. Quien fusione segundo debe reutilizar `AnswerAttempt`, `AnswerAttemptRepository`, `AnswerResponse` y los códigos de esta tarea.
- **Contrato de Voz provisional.** Cuando Voz publique el suyo, solo cambia `VoiceServiceTranscriptionAdapter`.
