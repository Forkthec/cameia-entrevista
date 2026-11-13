# CM-349 · Respuesta por voz transcrita y avance (`POST /api/v1/interviews/sessions/{sessionId}/answers/audio`)

| Dato | Valor |
|---|---|
| Tarea Jira | CM-349 (HU-5.12, «Respuesta por voz transcrita y avance») |
| Repositorio | `cameia-entrevista`, base `origin/develop` `09bb7ff` (incluye CM-212: iniciar la sesión y planificar las 5 preguntas) |
| Depende de | CM-212 (sesión `EN_CURSO`, preguntas y turno 1), esquema de CM-311 (`intento_respuesta`, `ejecucion_ia` con `TRANSCRIBIR`, `clave_idempotencia`) |
| Consumidor | `cameia-web`, feature `interview-session` (CM-317, HU-5.8): ya envía el multipart y decide por los códigos `TRANSCRIPTION_*` |
| Estado | Implementada. La revisión previa a implementar se omitió por instrucción explícita del solicitante; las decisiones quedan en §9 para revisión en el PR |

## 1. Objetivo

Que la persona responda por voz la pregunta pendiente de una Simulación: el servicio transcribe el audio en el idioma
de la entrevista con AssemblyAI, valida la transcripción, la registra como respuesta igual que una escrita (sin
retroalimentación), descarta el audio y devuelve la siguiente pregunta.

## 2. Criterios de aceptación y trazabilidad

| CA | Qué exige | Dónde se cumple | Prueba |
|---|---|---|---|
| CA-5.12.1 | Audio válido de la pregunta k<5 → registra el texto como respuesta, sin retroalimentación, y devuelve la pregunta k+1 con «Pregunta k+1 de 5» | `SubmitAudioAnswerService`, `AnswerRegistrar` | `SubmitAudioAnswerServiceTest.submit_shouldRegisterTranscriptAndReturnNextQuestion…`, `VoiceAnswerEndToEndTest` |
| CA-5.12.2 | Transcribe en el idioma de la entrevista; términos técnicos del otro idioma tal cual | `AssemblyAiTranscriptionAdapter.transcriptRequest` (detección con idioma de la entrevista como respaldo, sin traducción) | `AssemblyAiTranscriptionAdapterTest.transcribe_shouldRequestDetection…`, `TranscriptionPolicyTest.accept_shouldKeepTechnicalTerms…` |
| CA-5.12.3 | Deja registrado el primer intento de transcripción asociado a la pregunta | Fila `ejecucion_ia` `TRANSCRIBIR`, `numero_intento = 1`, `turno_id`, con éxito o fallo | `SubmitAudioAnswerServiceTest.submit_shouldRecordFirstTranscriptionAttempt…`, `VoiceAnswerPersistenceTest` |
| CA-5.12.4 | Descarta el audio al terminar la petición; ninguna copia en CAMEIA; sin métricas acústicas (HU-6.2) | Multipart solo en memoria (`file-size-threshold` = tope), `Arrays.fill` en `finally`, ninguna columna de audio | `SubmitAudioAnswerServiceTest.submit_shouldWipeAudioBytes…` |
| CA-5.12.5 | Respuesta por voz a pregunta de seguimiento | **No aplica**: HU-5.9 no está disponible | — |
| CA-5.12.6 | Pregunta 5 por voz → completa, sin otra pregunta; sin HU-5.5, «Tu entrevista terminó.» y la sesión sigue `EN_CURSO` con 5 respuestas | `AnswerRegistrar.register`: `nextQuestion = null`, `interviewFinished = true`, sesión sin cambio | `submit_shouldFinishWithoutNextQuestion…`, `VoiceAnswerEndToEndTest.answeringFiveQuestionsByVoice…` |
| CA-5.12.7 | Transcripción vacía o solo espacios → «No detectamos tu voz, intenta de nuevo.», sin registrar | `TranscriptionPolicy` → `422 TRANSCRIPTION_EMPTY` | `TranscriptionPolicyTest`, `VoiceAnswerEndToEndTest.emptyTranscription…` |
| CA-5.12.8 | Fallo del servicio (error, tiempo, respuesta inválida) → no registra ni avanza; sin HU-5.13, descarta el audio y muestra «No pudimos transcribir…» | `503 TRANSCRIPTION_UNAVAILABLE`; los botones los pinta el Web | `AssemblyAiTranscriptionAdapterTest`, `VoiceAnswerEndToEndTest.transcriptionFailure…` |
| CA-5.12.9 | 4001+ caracteres → «Tu respuesta es demasiado larga…», sin gastar intento | `422 TRANSCRIPTION_TOO_LONG` | `TranscriptionPolicyTest.accept_shouldReject4001Characters` |
| CA-5.12.10 | Idioma distinto con confianza ≥ umbral → mensaje del idioma de la entrevista, sin gastar intento; mezcla técnica no cuenta | `422 TRANSCRIPTION_LANGUAGE_MISMATCH`; umbral `TRANSCRIPTION_LANGUAGE_CONFIDENCE_THRESHOLD` | `TranscriptionPolicyTest.accept_shouldRejectWith…Message…` |
| CA-5.12.11 | Exactamente 4000 caracteres → se registra | `TranscriptionPolicy` | `TranscriptionPolicyTest.accept_shouldAcceptExactly4000Characters` |

## 3. Requisitos (EARS)

- **R-01.** Cuando llegue `POST …/{sessionId}/answers/audio` sin `X-User-Id` válido, el servicio responderá `401 IDENTITY_REQUIRED`.
- **R-02.** Cuando la sesión no exista, sea de otra persona o su identificador no sea un UUID canónico, el servicio responderá `404 SESSION_NOT_FOUND`.
- **R-03.** Cuando la sesión no esté `EN_CURSO`, el servicio responderá `409 SESSION_TRANSITION_NOT_ALLOWED` sin llamar a la transcripción.
- **R-04.** Cuando falten `turnId` o `audio`, `audio` tenga 0 bytes o `Idempotency-Key` no cumpla `[A-Za-z0-9._-]{1,64}`, el servicio responderá `422 VALIDATION_FAILED` con un elemento por campo (todos a la vez).
- **R-05.** Cuando la misma persona repita una `Idempotency-Key` de `RESPONDER_AUDIO` con la misma sesión, turno y bytes de audio, el servicio devolverá la respuesta guardada sin transcribir; con otra sesión, turno o audio, responderá `409 IDEMPOTENCY_KEY_CONFLICT`.
- **R-06.** Cuando `turnId` no sea el turno pendiente de la sesión, el servicio responderá `409 TURN_TRANSITION_NOT_ALLOWED`.
- **R-07.** Cuando el tipo de la parte `audio` no esté en la lista admitida, el servicio responderá `415 AUDIO_FORMAT_NOT_ALLOWED`.
- **R-08.** Cuando la sesión tenga forma de respuesta `TEXTO`, el servicio responderá `422 ANSWER_FORMAT_NOT_ALLOWED`.
- **R-09.** Cuando el cuerpo supere `AUDIO_MAX_SIZE` (10 MB), el servicio responderá `413 REQUEST_PAYLOAD_TOO_LARGE`.
- **R-10.** Mientras procesa una respuesta válida, el servicio hará una sola llamada a AssemblyAI con detección de idioma (`expected_languages` = idioma de la entrevista y el otro idioma del producto, `fallback_language` = idioma de la entrevista, `on_no_speech_detected = fallback`).
- **R-11.** Cuando AssemblyAI responda (con éxito o con fallo), el servicio guardará una fila `ejecucion_ia` `TRANSCRIBIR` con `turno_id`, `numero_intento = 1`, `proveedor = assemblyai`, `modelo` configurado y, si falló, `error_codigo = TRANSCRIPTION_UNAVAILABLE`.
- **R-12.** Cuando AssemblyAI falle, no responda dentro de `TRANSCRIPTION_TIMEOUT`, responda algo que no sea JSON, o falte la llave, el servicio responderá `503 TRANSCRIPTION_UNAVAILABLE` y no registrará la respuesta ni avanzará.
- **R-13.** Cuando el texto, normalizado a NFC y recortado, quede vacío, el servicio responderá `422 TRANSCRIPTION_EMPTY`.
- **R-14.** Cuando el idioma detectado sea distinto del de la entrevista y su confianza sea ≥ el umbral, el servicio responderá `422 TRANSCRIPTION_LANGUAGE_MISMATCH` con el mensaje del idioma de la entrevista.
- **R-15.** Cuando el texto tenga más de 4000 puntos de código, el servicio responderá `422 TRANSCRIPTION_TOO_LONG`.
- **R-16.** Cuando la transcripción sea válida, el servicio, en una transacción, guardará `intento_respuesta` (`INICIAL`, definitiva, origen `AUDIO`, texto, pregunta mostrada, idioma detectado), pasará el turno a `COMPLETED` y abrirá el turno de la pregunta siguiente; tras la quinta no abrirá otro y dejará la sesión `EN_CURSO`.
- **R-17.** El servicio borrará los bytes del audio al terminar la petición, con éxito o con error, y nunca los escribirá en disco ni en la base, ni registrará el audio ni el texto en los logs.

## 4. Contrato

`POST /api/v1/interviews/sessions/{sessionId}/answers/audio` · `Content-Type: multipart/form-data`

| Parte / cabecera | Tipo | Regla |
|---|---|---|
| `X-User-Id` | cabecera | Obligatoria (la pone el Gateway) |
| `Idempotency-Key` | cabecera | Opcional; 1-64 `[A-Za-z0-9._-]`; el Web envía una por turno y la reutiliza al reenviar |
| `turnId` | texto | UUID del turno pendiente |
| `audio` | archivo | `audio/webm`, `audio/ogg`, `audio/mp4`, `audio/x-m4a`, `audio/m4a`, `audio/aac`, `audio/mpeg`, `audio/mp3`, `audio/wav`, `audio/x-wav`, `audio/wave`, `video/webm` (se ignoran parámetros como `;codecs=opus`); hasta 10 MB. El nombre del archivo se ignora |

Respuesta 200 (la forma común que ya consume el Web; nunca retroalimentación):

```json
{"turn":{"turnId":"…","status":"COMPLETED"},
 "progress":{"currentQuestion":3,"totalQuestions":5},
 "nextQuestion":{"turnId":"…","number":3,"text":"…"},
 "interviewFinished":false}
```

Tras la pregunta 5: `"progress":{"currentQuestion":5,"totalQuestions":5},"nextQuestion":null,"interviewFinished":true`.

Orden de validación: 401 → 404 → 409 (sesión) → 422 (campos) → repetición por clave → 409 (turno) → 415 → 422 (forma) → transcripción (503) → 422 (vacía → idioma → largo). El 413 lo corta el contenedor antes del controlador.

## 5. Errores nuevos

| Código | HTTP | Mensaje |
|---|---|---|
| `TURN_ID_REQUIRED` (campo `turnId`) | 422 | Falta la pregunta a la que respondes. |
| `TURN_ID_INVALID_FORMAT` (campo `turnId`) | 422 | La pregunta a la que respondes no es válida. |
| `AUDIO_REQUIRED` (campo `audio`) | 422 | Graba tu respuesta antes de enviarla. |
| `AUDIO_FORMAT_NOT_ALLOWED` | 415 | Tipo de contenido no admitido. |
| `ANSWER_FORMAT_NOT_ALLOWED` | 422 | Esta entrevista no admite respuestas en esa forma. |
| `TRANSCRIPTION_EMPTY` | 422 | No detectamos tu voz, intenta de nuevo. |
| `TRANSCRIPTION_TOO_LONG` | 422 | Tu respuesta es demasiado larga. Graba una más corta o responde por texto. |
| `TRANSCRIPTION_LANGUAGE_MISMATCH` | 422 | Tu entrevista es en inglés. Responde en inglés. / Tu entrevista es en español. Responde en español. |
| `TRANSCRIPTION_UNAVAILABLE` | 503 | No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto. |

Los cuatro `TRANSCRIPTION_*` son los que el Web ya discrimina (`cameia-web` `SPEC.md` de `interview-session` §5).

## 6. Datos

- **Migración `V8__respuesta_por_voz.sql`**: amplía `ck_clave_idempotencia_operacion` a `('CREAR_SESION', 'RESPONDER_AUDIO')`. Nada más: `intento_respuesta` (origen `AUDIO`, `idioma_detectado`) y `ejecucion_ia` (`TRANSCRIBIR`, `turno_id`, `version_prompt` nula) ya existían desde `V1`.
- **Entidades nuevas**: `AnswerAttemptEntity` (`intento_respuesta`) y `TranscriptionExecutionEntity` (vista `TRANSCRIBIR` de `ejecucion_ia`, con `turno_id`). `AiExecutionEntity` (planificación) no cambia.
- **No se guarda**: el audio, la URL de subida de AssemblyAI ni el identificador de su transcripción.

## 7. Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `ASSEMBLYAI_API_KEY` | vacía | Llave de AssemblyAI. Sin ella el servicio arranca (aviso en el log) y responde `503 TRANSCRIPTION_UNAVAILABLE` |
| `ASSEMBLYAI_BASE_URL` | `https://api.assemblyai.com` | UE: `https://api.eu.assemblyai.com` |
| `ASSEMBLYAI_SPEECH_MODELS` | `universal-3-5-pro,universal-2` | `speech_models` en orden de preferencia |
| `TRANSCRIPTION_TIMEOUT` | `90s` | Subida + encolado + espera; por debajo de los 120 s del Gateway |
| `TRANSCRIPTION_POLL_INTERVAL` | `1s` | Espera entre consultas del estado |
| `TRANSCRIPTION_LANGUAGE_CONFIDENCE_THRESHOLD` | `0.8` | Umbral de CA-5.12.10 |
| `AUDIO_MAX_SIZE` | `10MB` | Tope del multipart y umbral para mantenerlo en memoria |

## 8. Fuera de alcance

- Evaluación por turno con el modelo de lenguaje (HU-5.2 en Backend): hoy el turno pasa a `COMPLETED` al registrar la respuesta. `AnswerRegistrar` es la pieza común para que la respuesta escrita la reutilice.
- Reintentos automáticos de la transcripción (HU-5.13), pregunta de seguimiento (HU-5.9), cierre y evaluación (HU-5.5), métricas acústicas (HU-6.2).
- Validaciones de HU-5.8 que exigen leer el audio (más de 181 s, menos de 1,5 s de voz): el Web ya las aplica antes de enviar.
- Cambios en el Gateway: ya enruta `/api/v1/interviews/**` con 120 s y sin límite de cuerpo.

## 9. Decisiones (para revisión en el PR)

| # | Decisión | Por qué | Alternativa descartada |
|---|---|---|---|
| D-01 | Una sola llamada a AssemblyAI con detección de idioma entre los dos idiomas del producto y el de la entrevista como respaldo | Cumple CA-5.12.2 y CA-5.12.10 con un costo; el idioma dominante ignora términos técnicos sueltos | Forzar `language_code` (no detecta el otro idioma) o dos llamadas (doble costo y latencia) |
| D-02 | No se usa `language_confidence_threshold` de AssemblyAI; el umbral se aplica en el dominio | El del proveedor convierte la baja confianza en error (sería un 503 en vez de aceptar la respuesta) | Delegar el umbral al proveedor |
| D-03 | `on_no_speech_detected = fallback` | Sin voz el proveedor devuelve texto vacío → `TRANSCRIPTION_EMPTY` (CA-5.12.7) y no un error → 503 | Valor por defecto (`error`) |
| D-04 | Si la confianza está por debajo del umbral, se acepta el texto aunque el idioma detectado sea otro | CA-5.12.10 solo rechaza con confianza ≥ umbral | Rechazar toda diferencia de idioma |
| D-05 | Orden de las reglas: vacía → idioma → largo | Decirle a quien habló en otro idioma que habló de más no le ayuda | Largo antes que idioma |
| D-06 | Llave de AssemblyAI opcional para arrancar | Un despliegue sin el secreto no tumba todo el servicio; la voz responde 503 y se ve en el log | Llave obligatoria (el servicio no arranca) |
| D-07 | Cada llamada registra `numero_intento = 1` | CA-5.12.3 pide el primer intento; cada grabación nueva es un primer intento; 2 y 3 quedan para HU-5.13 | Contar las regrabaciones como intentos (agotaría el `CHECK` 1-3) |
| D-08 | La sesión sigue `EN_CURSO` tras la quinta respuesta | CA-5.12.6 mientras HU-5.5 no esté disponible | Pasarla a `EVALUANDO` |
| D-09 | Turno `PENDING → PROCESSING → COMPLETED` en la misma transacción | No hay evaluación por turno en Backend todavía (§8) | Dejarlo en `PROCESSING` (rompería `findOpenBySession` y el estado) |
| D-10 | `Idempotency-Key` con operación nueva `RESPONDER_AUDIO` y huella = SHA-256(sesión, turno, bytes del audio) | El Web reutiliza la clave al reenviar tras perder la conexión (CA-5.8.22); si la primera petición sí se registró, la repetición devuelve el mismo 200 en vez de un 409 | Sin idempotencia |
| D-11 | Controlador y servicios nuevos en archivos propios (`AudioAnswerController`, `SubmitAudioAnswerService`, `AnswerRegistrar`) | Otros equipos trabajan en paralelo en `InterviewSessionController` y en la respuesta escrita; así se reducen los choques | Agregar el endpoint al controlador existente |
| D-12 | Orden de validación con los 422 de campo antes del 409 del turno | Sin `turnId` válido no se puede comprobar el turno | Orden estricto 404→409→413→415→422 del backlog |
| D-13 | No se borra la transcripción en AssemblyAI tras usarla | CA-5.12.4 acepta la copia temporal del proveedor (CA-5.8.24) | `DELETE /v2/transcript/{id}` tras leer (posible mejora de privacidad) |

## 10. Pendientes

| Pendiente | Responsable | Qué bloquea |
|---|---|---|
| Secreto `ASSEMBLYAI_API_KEY` en el gestor de secretos y en los despliegues de Cloud Run | DevOps | Respuesta por voz en preproducción y producción |
| Conectar la respuesta escrita (HU-5.2) a `AnswerRegistrar` y añadir la evaluación por turno | Tarea de HU-5.2 en Backend | Que texto y voz se procesen exactamente igual |
| Número de migración `V8`: si otra rama fusiona antes otra `V8`, renumerar al rebasar | Quien fusione segundo | Arranque de Flyway |
| Registrar actividad de la sesión (`fecha_ultima_actividad`) al responder | Tarea de abandono por inactividad (HU-5.5) | Abandono automático |
