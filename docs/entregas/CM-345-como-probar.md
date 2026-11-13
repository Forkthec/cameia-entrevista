# CM-345 · HU-5.8 Responder por voz — cómo probar

> **Estado tras la integración (12-nov-2026, `bc1f885`):** esta rama fusionó CM-349. La voz la transcribe
> **AssemblyAI** (adaptador de CM-349), no Gemini; Gemini solo planifica las preguntas. Se retiraron
> `GeminiTranscriptionAdapter`, `TranscriptionPort`, `TRANSCRIPTION_MODEL` y `InterviewAnswerController`: el endpoint lo
> atiende `AudioAnswerController`. Orden de fusión: CM-349 → CM-345 → CM-362 (CM-362 está apilada encima de esta rama).
> Qué quedó de cada tarea: `specs/CM-345-RespuestaPorVoz/spec.md` §10 y `docs/adr/0012-respuesta-por-voz.md`.

Endpoint: `POST /api/v1/interviews/sessions/{sessionId}/answers/audio` (multipart: `turnId` texto + `audio` archivo;
cabecera opcional `Idempotency-Key`; identidad `X-User-Id`). Respuesta 200:
`{turn{turnId,status}, progress{currentQuestion,totalQuestions}, nextQuestion|null, interviewFinished}`.

## Variables

Para probar con el simulador (`postman/simulador-gemini.py`, que imita Gemini y AssemblyAI), en el `.env` de la raíz de
`cameia-entrevista` o como variables del proceso:

| Variable | Valor con el simulador | Para qué |
|---|---|---|
| `ASSEMBLYAI_API_KEY` | `simulada` (cualquier valor no vacío) | Llave de AssemblyAI. Sin ella el servicio arranca, pero cada respuesta por voz responde 503 `TRANSCRIPTION_UNAVAILABLE` |
| `ASSEMBLYAI_BASE_URL` | `http://localhost:8099` | Apunta la transcripción al simulador (por defecto `https://api.assemblyai.com`) |
| `CAMEIA_LLM_BASE_URL` | `http://localhost:8099` | Apunta la planificación de Gemini al simulador |
| `GOOGLE_GENAI_API_KEY` | una llave falsa | Obligatoria para arrancar; con el simulador no se usa una real |
| `LLM_MODEL` | `gemini-3.5-flash-lite` | Obligatoria para arrancar |

Opcionales (con sus valores por defecto): `ASSEMBLYAI_SPEECH_MODELS=universal-3-5-pro,universal-2`,
`TRANSCRIPTION_TIMEOUT=90s`, `TRANSCRIPTION_POLL_INTERVAL=1s`, `TRANSCRIPTION_LANGUAGE_CONFIDENCE_THRESHOLD=0.8`.
`TRANSCRIPTION_MODEL` y `AUDIO_MAX_SIZE` ya no existen: el modelo lo eligen `ASSEMBLYAI_SPEECH_MODELS` y el tope del audio
es fijo (10 MiB, CA-5.8.12).

Con proveedores reales: `ASSEMBLYAI_API_KEY` con la llave de AssemblyAI y sin `ASSEMBLYAI_BASE_URL`;
`GOOGLE_GENAI_API_KEY` real y sin `CAMEIA_LLM_BASE_URL` para que `start` planifique de verdad.

## Probar con Postman

1. Base y broker: `docker compose --profile mensajeria up -d`.
2. Simulador: `python postman/simulador-gemini.py 8099`.
3. Servicio con las variables de la tabla: `./mvnw.cmd -B -ntp clean package -DskipTests` y
   `java -jar target/entrevista-0.0.1-SNAPSHOT.jar`.
4. Importar `postman/cameia-entrevista.postman_collection.json` y `postman/local.postman_environment.json`; carpeta
   **Responder por voz** (39 peticiones). Los audios están en `src/test/resources/audio/`; con Newman, desde la raíz del
   repositorio:
   `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --folder "Responder por voz"`
5. A mano: publicar el catálogo de roles y el perfil (dos primeras peticiones de la carpeta), `POST /api/v1/interviews/sessions`
   con `"answerFormat":"AUDIO"`, `POST /api/v1/interviews/sessions/{id}/start` y tomar `pendingQuestion.turnId`. Luego
   `POST /api/v1/interviews/sessions/{id}/answers/audio` con form-data `turnId=<turnId>` y `audio=<archivo>`. Con el
   simulador, `POST {{simuladorUrl}}/__script` con `{"scenario":"..."}` programa la siguiente transcripción
   (`transcript`, `transcript_en`, `no_speech`, `error`, `limit`, `unavailable`).

| Caso | Archivo / acción | Esperado |
|---|---|---|
| CA-5.8.5 | `voz.webm` | 200, «Pregunta 2 de 5» |
| CA-5.8.7 / 5.8.8 | `silencio-180s.webm` / `silencio-182s.webm` | 200 / 422 `AUDIO_TOO_LONG` |
| CA-5.8.13 | 10 485 761 bytes | 413 `AUDIO_TOO_LARGE` |
| CA-5.8.14 / 5.8.15 | `tono-3s.mp3`, `tono-3s.wav`, `*-con-video.*`, `no-es-audio.webm` | 415 `AUDIO_INVALID_FORMAT` |
| CA-5.8.20 | Sesión con forma `TEXTO` | 422 `AUDIO_NOT_ALLOWED` |
| CA-5.8.21 | Otro `X-User-Id` o id inexistente | 404 `SESSION_NOT_FOUND` |
| CA-5.8.27 | Repetir con la misma `Idempotency-Key` | El mismo 200, sin transcribir otra vez |
| CA-5.8.28 | Sesión ABANDONADA / EVALUANDO o FINALIZADA | 409 `SESSION_ABANDONED` / `SESSION_ALREADY_CLOSED` |
| Sin la parte `audio` | — | 422 `VALIDATION_FAILED` con `errors[0].code = AUDIO_REQUIRED` (también `TURN_ID_REQUIRED` o `TURN_ID_INVALID_FORMAT` a la vez si falla `turnId`) |
| Tras la 5.ª respuesta | Enviar otro audio | La sesión sigue `EN_CURSO` (CA-5.12.6, hasta el cierre de HU-5.5): 409 `TURN_TRANSITION_NOT_ALLOWED` (petición 35 de la carpeta) |

## Con el compose integrado (`product/local-dev`)

El compose compila Entrevista desde `ENTREVISTA_DIR`. Para probar esta pila: `ENTREVISTA_DIR=../cameia-entrevista-CM-345`
en `local-dev/.env`, con `ASSEMBLYAI_API_KEY` (real o, apuntando `ASSEMBLYAI_BASE_URL` al simulador, simulada) y
`docker compose up --build -d entrevista`. Avisa antes a quien use el stack `cameia-dev`. El Gateway no necesita cambios:
reenvía el multipart en streaming, sin límite de tamaño, y espera 120 s en `/api/v1/interviews/**`.

## Decisiones de CM-345 que siguen vigentes (a revisar)

1. Formato por contenido real (WebM/Opus, MP4/AAC, sin video), nunca por el nombre ni el tipo declarado. La duración es
   el máximo entre marcas de tiempo, duración declarada y suma de paquetes, para que no se pueda esconder un audio largo.
   Tolerancia 181 s; mínimo 1,5 s (`AUDIO_TOO_SHORT`).
2. Multipart de 12 MiB con umbral de disco de 13 MiB: entre 10 MiB + 1 y 12 MiB responde `AUDIO_TOO_LARGE` y el audio
   nunca toca el disco. La petición de Postman de `/health` pasó de 11 a 13 MB.
3. Doble envío: el turno se reserva en `PROCESSING` con su versión antes de transcribir → 409 `SESSION_CONFLICT`. Si la
   transcripción falla, el turno queda `RETRYING` y la pregunta sigue pendiente. Tras la integración vive en
   `AnswerRegistrar` para que la respuesta escrita la reutilice.
4. Los bytes del audio se sobrescriben con ceros al terminar; nunca se registran el nombre, el audio ni el texto.
5. Códigos `AUDIO_NOT_ALLOWED`, `SESSION_ABANDONED` y `SESSION_ALREADY_CLOSED` con los mensajes literales de los criterios.

Dejaron de aplicar al integrar: transcribir con Gemini en línea (D-345-2) y pasar la sesión a `EVALUANDO` tras la 5.ª
respuesta (D-345-6). La V8 es una sola, idéntica en las dos ramas.

## Evidencia

Sobre la rama integrada (`bc1f885`, 12-nov-2026):

- `./mvnw clean verify`: 1502 pruebas, 0 fallos, 1 omitida.
- Newman, carpeta «Responder por voz», contra el servicio levantado con PostgreSQL 16, RabbitMQ y el simulador
  (`ASSEMBLYAI_API_KEY=simulada`, `ASSEMBLYAI_BASE_URL` y `CAMEIA_LLM_BASE_URL` apuntando al simulador): 39 peticiones,
  159 aserciones, 0 fallos.
- No se llamó a AssemblyAI real desde esta rama.

## Pendiente

- Una prueba manual con una llave real de AssemblyAI.
- Revisión humana y ratificar las decisiones de la spec §8 y §10.
