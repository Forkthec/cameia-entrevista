# CM-345 · HU-5.8 Responder por voz — cómo probar

Endpoint nuevo: `POST /api/v1/interviews/sessions/{sessionId}/answers/audio` (multipart: `turnId` texto + `audio` archivo; cabecera opcional `Idempotency-Key`; identidad `X-User-Id`). Respuesta 200: `{turn{turnId,status}, progress{currentQuestion,totalQuestions}, nextQuestion|null, interviewFinished}`.

## Variables (en el `.env` de la raíz de `cameia-entrevista`)
| Variable | Obligatoria | Valor |
|---|---|---|
| `GOOGLE_GENAI_API_KEY` | Sí (ya existía) | Llave real de Gemini (transcribe y planifica) |
| `LLM_MODEL` | Sí (ya existía) | `gemini-3.5-flash-lite` (probado en real con WebM y MP4) |
| `TRANSCRIPTION_MODEL` | No | Por defecto = `LLM_MODEL` |
| `TRANSCRIPTION_TIMEOUT` | No | Por defecto `90s` (máx. 110 s; Gateway corta a 120 s) |
| `CAMEIA_LLM_BASE_URL` | Solo con simulador | `http://localhost:8099` (simulador `postman/simulador-gemini.py`, ahora también transcribe) |

No hay variables nuevas obligatorias: el compose actual sirve tal cual (no fue necesario modificarlo; ver «Pendiente»).

## Probar con Postman
1. Levantar base + broker (`docker compose --profile mensajeria up -d`) y el servicio (`./mvnw.cmd spring-boot:run` o el compose).
2. Importar `postman/cameia-entrevista.postman_collection.json` y `postman/local.postman_environment.json`; carpeta **Responder por voz** (39 peticiones). Los audios están en `src/test/resources/audio/` (Newman: correr desde la raíz del repo).
3. A mano: publicar perfil y roles (dos primeras peticiones), `POST /sessions` con `"answerFormat":"AUDIO"`, `POST /sessions/{id}/start` → tomar `pendingQuestion.turnId`; luego `POST /sessions/{id}/answers/audio` form-data `turnId=<turnId>`, `audio=<archivo>` (`voz.webm` o `voz-safari.mp4` tienen voz real en español).

| Caso | Archivo / acción | Esperado |
|---|---|---|
| CA-5.8.5 | `voz.webm` | 200, «Pregunta 2 de 5» |
| CA-5.8.7 / 5.8.8 | `silencio-180s.webm` / `silencio-182s.webm` | 200 / 422 `AUDIO_TOO_LONG` |
| CA-5.8.13 | 10 485 761 bytes | 413 `AUDIO_TOO_LARGE` |
| CA-5.8.14 / 5.8.15 | `tono-3s.mp3`, `.wav`, `*-con-video.*`, `no-es-audio.webm` | 415 `AUDIO_INVALID_FORMAT` |
| CA-5.8.20 | sesión `TEXTO` | 422 `AUDIO_NOT_ALLOWED` |
| CA-5.8.21 | otro `X-User-Id` / id inexistente | 404 `SESSION_NOT_FOUND` |
| CA-5.8.27 | repetir con la misma `Idempotency-Key` | mismo 200, sin transcribir otra vez |
| CA-5.8.28 | sesión EVALUANDO/FINALIZADA/ABANDONADA | 409 `SESSION_ALREADY_CLOSED` / `SESSION_ABANDONED` |

## Decisiones tomadas (sin aprobación previa, a revisar)
1. Reparto con CM-349 (decidido por el usuario): CM-345 = endpoint + validaciones + adaptador mínimo de transcripción; idioma detectado y reintentos quedan para CM-349/CM-362.
2. `V8__respuesta_por_voz.sql` es **byte a byte igual** al borrador de CM-349 (operación `RESPONDER_AUDIO`) para que el merge no choque.
3. Concurrencia sin índice nuevo: el turno pasa a `PROCESSING` antes de transcribir (control de versión); doble envío → 409 `SESSION_CONFLICT`. Si la transcripción falla, el turno queda `RETRYING` (pregunta sigue pendiente).
4. Formato por contenido real (WebM/Opus, MP4/AAC, sin video); duración = máximo entre marcas de tiempo, duración declarada y suma de paquetes (Opus TOC / muestras AAC) para que no se pueda esconder un audio largo. Tolerancia 181 s; mínimo 1,5 s (`AUDIO_TOO_SHORT`).
5. Límite multipart subido a 12 MiB (umbral de disco 13 MiB: el audio nunca toca disco); entre 10 MiB+1 y 12 MiB responde `AUDIO_TOO_LARGE`. La petición Postman de 11 MB de `/health` se cambió a 13 MB.
6. Gemini con audio en línea (sin Files API ni bucket); bytes sobrescritos con ceros al terminar; nunca se registra nombre, audio ni texto.
7. Sin evaluación por IA del turno (es de HU-5.2): el turno queda `COMPLETED` y tras la 5.ª la sesión pasa a `EVALUANDO`.
8. Códigos `SESSION_ABANDONED`/`SESSION_ALREADY_CLOSED`/`TRANSCRIPTION_*` siguen el contrato que ya usa la web.

## Evidencia
`./mvnw clean verify`: 1437 pruebas, 0 fallos (incluye 27 E2E con PostgreSQL real en `AudioAnswerEndToEndTest`). Prueba real con Gemini (`GeminiTranscriptionLiveTest`, `LLM_LIVE_TEST=true`): WebM y MP4 transcritos.

## Pendiente (no alcanzado)
- Correr Newman de la carpeta nueva contra el servicio levantado (no ejecutado).
- Revisar el compose general del otro agente (stack `cameia-dev-*`) y el Gateway: confirmar que enruta `multipart` > 10 MB y espera ≥ 120 s en `/answers/audio`.
- spec/plan/tasks en `specs/CM-345-...`, ADR, bitácora IA, actualizar CLAUDE.md/README/.env.example; partir en un commit por capacidad.
