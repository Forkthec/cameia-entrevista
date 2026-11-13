# CM-345 · HU-5.8 Responder por voz — cómo probar

> **Estado tras la integración (12-nov-2026, `bc1f885`):** esta rama fusionó CM-349. La voz la transcribe
> **AssemblyAI** (adaptador de CM-349), no Gemini; Gemini solo planifica las preguntas. El endpoint lo atiende
> `AudioAnswerController`. Orden de fusión: CM-349 → CM-345 → CM-362 (CM-362 está apilada encima de esta rama).
> Qué quedó de cada tarea: `specs/CM-345-RespuestaPorVoz/spec.md` §10 y `docs/adr/0012-respuesta-por-voz.md`.

Endpoint: `POST /api/v1/interviews/sessions/{sessionId}/answers/audio` (multipart: `turnId` texto + `audio` archivo;
cabecera opcional `Idempotency-Key`; identidad `X-User-Id`). Respuesta 200:
`{turn{turnId,status}, progress{currentQuestion,totalQuestions}, nextQuestion|null, interviewFinished}`.

Esta guía usa **los proveedores reales** (Gemini planifica, AssemblyAI transcribe). Al final está cómo correrla con el
simulador.

## Variables

En el `.env` de la raíz de `cameia-entrevista` o como variables del proceso:

| Variable | Valor | Para qué |
|---|---|---|
| `ASSEMBLYAI_API_KEY` | Llave real de AssemblyAI | Transcripción. Sin ella el servicio arranca, pero cada respuesta por voz responde 503 `TRANSCRIPTION_UNAVAILABLE` |
| `GOOGLE_GENAI_API_KEY` | Llave real de Gemini | `start` planifica las 5 preguntas |
| `LLM_MODEL` | `gemini-3.5-flash-lite` | Obligatoria para arrancar |
| `ASSEMBLYAI_BASE_URL` | **Sin definir** (por defecto `https://api.assemblyai.com`; UE: `https://api.eu.assemblyai.com`) | Solo se cambia para el simulador |
| `CAMEIA_LLM_BASE_URL` | **Sin definir** (vacía: la dirección real de Google) | Solo se cambia para el simulador |

Opcionales (con sus valores por defecto): `ASSEMBLYAI_SPEECH_MODELS=universal-3-5-pro,universal-2`,
`TRANSCRIPTION_TIMEOUT=90s`, `TRANSCRIPTION_POLL_INTERVAL=1s`, `TRANSCRIPTION_LANGUAGE_CONFIDENCE_THRESHOLD=0.8`.
`TRANSCRIPTION_MODEL` y `AUDIO_MAX_SIZE` ya no existen: el modelo lo eligen `ASSEMBLYAI_SPEECH_MODELS` y el tope del audio
es fijo (10 MiB, CA-5.8.12).

## Probar con Postman (proveedores reales)

1. Base y broker: `docker compose --profile mensajeria up -d`.
2. Servicio con las variables de la tabla: `./mvnw.cmd -B -ntp clean package -DskipTests` y
   `java -jar target/entrevista-0.0.1-SNAPSHOT.jar`.
3. Importar `postman/cameia-entrevista.postman_collection.json` y `postman/local.postman_environment.json`. En el entorno
   agrega **`vozProveedores` = `real`**. En *Settings → General → Working directory* pon la raíz del repositorio (los
   audios están en `src/test/resources/audio/`).
4. Correr la carpeta **Responder por voz** con el Runner. Con `vozProveedores=real` un script de la carpeta salta las 8
   peticiones que dependen del simulador (las que llaman a `{{simuladorUrl}}`, «503 transcripción no disponible» y
   «422 transcripción sin voz»): corren 31. Con Newman, desde la raíz del repositorio:

   ```
   npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --env-var vozProveedores=real --folder "Responder por voz"
   ```

Cada corrida inicia una entrevista (una llamada a Gemini) y transcribe cinco audios en AssemblyAI (unos 4 minutos
facturables: `voz.webm` dura 14 s y la pregunta 3 envía 3:00).

| Petición | Archivo / acción | Esperado |
|---|---|---|
| 10 | Sin la parte `audio` | 422 `VALIDATION_FAILED` con `errors[0].code = AUDIO_REQUIRED` |
| 11 | 10 485 761 bytes | 413 `AUDIO_TOO_LARGE` (CA-5.8.13) |
| 12-16 | `tono-3s.mp3`, `tono-3s.wav`, `*-con-video.*`, `no-es-audio.webm` | 415 `AUDIO_INVALID_FORMAT` (CA-5.8.14, 5.8.15) |
| 17 / 18 | `silencio-182s.webm` / `tono-1s.webm` | 422 `AUDIO_TOO_LONG` (CA-5.8.8) / 422 `AUDIO_TOO_SHORT` |
| 25 | `voz.webm` (WebM/Opus de Chrome) | 200, «Pregunta 2 de 5» (CA-5.8.5) |
| 27 | La misma `Idempotency-Key` y el mismo audio | El mismo 200, sin transcribir otra vez (CA-5.8.27) |
| 29 / 30 | La misma clave con otro audio / la pregunta 1 otra vez | 409 `IDEMPOTENCY_KEY_CONFLICT` / 409 `TURN_TRANSITION_NOT_ALLOWED` |
| 31 | `voz-safari.mp4` (MP4/AAC de Safari) | 200, «Pregunta 3 de 5» |
| 32 | `voz-180s.webm` (3:00 con voz) | 200, «Pregunta 4 de 5» (CA-5.8.7) |
| 33-34 | `voz.webm` | 200; tras la 5.ª, `interviewFinished: true` y `nextQuestion: null` |
| 35 | Otro audio con las cinco respondidas | 409 `TURN_TRANSITION_NOT_ALLOWED`: la sesión sigue `EN_CURSO` (CA-5.12.6, hasta HU-5.5) |
| 36-38 | Audio en una entrevista por texto | 422 `AUDIO_NOT_ALLOWED` (CA-5.8.20) |
| 7-8 | Otro `X-User-Id` o id inexistente | 404 `SESSION_NOT_FOUND` (CA-5.8.21) |

A mano, fuera del Runner:

- **Doble envío (CA-5.8.6):** el mismo `turnId` pendiente con `curl` en dos terminales casi a la vez: uno 200 y el otro
  409 `SESSION_CONFLICT` al instante, sin llamar a AssemblyAI.
- **Sesión abandonada o cerrada (CA-5.8.28):** cambiar `microentrevista.sesion.estado` a `ABANDONADA` (o `EVALUANDO`) y
  enviar un audio válido: 409 `SESSION_ABANDONED` / `SESSION_ALREADY_CLOSED`.
- **Sin voz con AssemblyAI real:** `silencio-180s.webm` en una pregunta pendiente responde 422 `TRANSCRIPTION_EMPTY`
  (pasó la validación de duración de CM-345; lo rechaza la de la transcripción de CM-349).

## Con el compose integrado (`product/local-dev`)

Con el perfil simulado comentado en `local-dev/.env` y las llaves reales (ver su `.env.example`). Para la pila completa,
`ENTREVISTA_DIR=../.worktrees/CM-362-entrevista` y `TRANSCRIPTION_TIMEOUT=60s`; para esta rama sola,
`ENTREVISTA_DIR=../cameia-entrevista-CM-345` y `TRANSCRIPTION_TIMEOUT=90s`. En el entorno de Postman:
`baseUrl=http://localhost:8083`, `rabbitManagementUrl=http://localhost:15680`, `rabbitUser=cameia`,
`rabbitPassword=cameia_local` y `vozProveedores=real`. El Gateway no necesita cambios: reenvía el multipart en streaming,
sin límite de tamaño, y espera 120 s en `/api/v1/interviews/**`.

## Con el simulador

`python postman/simulador-gemini.py 8099` y el servicio con `ASSEMBLYAI_API_KEY=simulada`,
`ASSEMBLYAI_BASE_URL=http://localhost:8099`, `CAMEIA_LLM_BASE_URL=http://localhost:8099` y una `GOOGLE_GENAI_API_KEY`
falsa. Sin `vozProveedores` (o con `simulado`) corren las 39 peticiones; el simulador programa además el 503
`TRANSCRIPTION_UNAVAILABLE` y el 422 `TRANSCRIPTION_EMPTY` (`POST {{simuladorUrl}}/__script` con
`{"scenario":"..."}`: `transcript`, `transcript_en`, `no_speech`, `error`, `limit`, `unavailable`).

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
6. La colección corre con proveedores reales (`vozProveedores=real`) y la pregunta 3 envía `voz-180s.webm` (14 s de
   `voz.webm` en bucle hasta 3:00) en lugar de silencio, para que AssemblyAI real también la acepte.

Dejaron de aplicar al integrar: transcribir con Gemini en línea (D-345-2) y pasar la sesión a `EVALUANDO` tras la 5.ª
respuesta (D-345-6). La V8 es una sola, idéntica en las dos ramas.

## Evidencia

- Sobre la rama integrada (`bc1f885`, 12-nov-2026): `./mvnw clean verify`, 1502 pruebas, 0 fallos, 1 omitida.
- Newman, carpeta «Responder por voz», contra el servicio con PostgreSQL 16, RabbitMQ y el simulador: 39 peticiones y
  159 aserciones sin fallos; con `vozProveedores=real` (saltos comprobados contra el simulador), 31 peticiones y 140
  aserciones sin fallos, también con `rabbitUser=cameia`.
- No se llamó a AssemblyAI ni a Gemini reales desde esta rama: no hay llaves en este entorno.

## Pendiente

- La corrida con llaves reales de AssemblyAI y Gemini.
- Revisión humana y ratificar las decisiones de la spec §8 y §10.
