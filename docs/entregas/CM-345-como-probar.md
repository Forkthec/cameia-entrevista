# CM-345 · HU-5.8 Responder por voz — cómo probar

Endpoint nuevo: `POST /api/v1/interviews/sessions/{sessionId}/answers/audio` (multipart: `turnId` texto + `audio` archivo; cabecera opcional `Idempotency-Key`; identidad `X-User-Id`). Respuesta 200: `{turn{turnId,status}, progress{currentQuestion,totalQuestions}, nextQuestion|null, interviewFinished}`.

## Variables (en el `.env` de la raíz de `cameia-entrevista`)

| Variable | Obligatoria | Valor |
|---|---|---|
| `GOOGLE_GENAI_API_KEY` | Sí (ya existía) | Llave real de Gemini (transcribe y planifica) |
| `LLM_MODEL` | Sí (ya existía) | `gemini-3.5-flash-lite` (probado en real con WebM y MP4) |
| `TRANSCRIPTION_MODEL` | No | Por defecto = `LLM_MODEL`. No la dejes definida en blanco: impide arrancar |
| `TRANSCRIPTION_TIMEOUT` | No | Por defecto `90s` (máx. 110 s; el Gateway corta a 120 s) |
| `CAMEIA_LLM_BASE_URL` | Solo con simulador | `http://localhost:8099` (`postman/simulador-gemini.py`, que ahora también transcribe) |

No hay variables nuevas obligatorias.

## Probar con Postman

1. Levantar base y broker: `docker compose --profile mensajeria up -d`. Arrancar el servicio (`./mvnw.cmd spring-boot:run` o el compose). Con llave real no hace falta el simulador.
2. Importar `postman/cameia-entrevista.postman_collection.json` y `postman/local.postman_environment.json`; carpeta **Responder por voz** (39 peticiones). Los audios están en `src/test/resources/audio/`. Con Newman, correr desde la raíz del repositorio:
   `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --folder "Responder por voz"`
3. A mano: publicar el catálogo de roles y el perfil (dos primeras peticiones de la carpeta), `POST /api/v1/interviews/sessions` con `"answerFormat":"AUDIO"`, `POST /api/v1/interviews/sessions/{id}/start` y tomar `pendingQuestion.turnId`. Luego `POST /api/v1/interviews/sessions/{id}/answers/audio` con form-data `turnId=<turnId>` y `audio=<archivo>`. `voz.webm` y `voz-safari.mp4` traen voz real en español.

| Caso | Archivo / acción | Esperado |
|---|---|---|
| CA-5.8.5 | `voz.webm` | 200, «Pregunta 2 de 5» |
| CA-5.8.7 / 5.8.8 | `silencio-180s.webm` / `silencio-182s.webm` | 200 / 422 `AUDIO_TOO_LONG` |
| CA-5.8.13 | 10 485 761 bytes | 413 `AUDIO_TOO_LARGE` |
| CA-5.8.14 / 5.8.15 | `tono-3s.mp3`, `tono-3s.wav`, `*-con-video.*`, `no-es-audio.webm` | 415 `AUDIO_INVALID_FORMAT` |
| CA-5.8.20 | Sesión con forma `TEXTO` | 422 `AUDIO_NOT_ALLOWED` |
| CA-5.8.21 | Otro `X-User-Id` o id inexistente | 404 `SESSION_NOT_FOUND` |
| CA-5.8.27 | Repetir con la misma `Idempotency-Key` | El mismo 200, sin transcribir otra vez |
| CA-5.8.28 | Sesión EVALUANDO, FINALIZADA o ABANDONADA | 409 `SESSION_ALREADY_CLOSED` / `SESSION_ABANDONED` |

## Con el compose integrado (`product/local-dev`)

Ese compose (del agente de CM-349) compila Entrevista desde `ENTREVISTA_DIR`, que por defecto es la rama de CM-349 (transcribe con AssemblyAI). Para probar CM-345:

1. En `local-dev/.env`: `ENTREVISTA_DIR=../cameia-entrevista-CM-345` y `GOOGLE_GENAI_API_KEY` con una llave real. `TRANSCRIPTION_MODEL` es opcional (el compose la pasa con `LLM_MODEL` por defecto). Las variables `ASSEMBLYAI_*` no se usan en esta rama.
2. `docker compose up --build -d entrevista` reconstruye solo Entrevista. Avisa antes al agente de CM-349: comparten el stack `cameia-dev`.
3. Llamar por el Gateway (`http://localhost:8080`, con token del emulador) o directo a `http://localhost:8083` con `X-User-Id`. El Gateway no necesita cambios: reenvía el multipart en streaming, sin límite de tamaño, y espera 120 s en `/api/v1/interviews/**`.

Cambio en el compose compartido (aditivo; no reinicié nada): la variable `TRANSCRIPTION_MODEL` en el servicio `entrevista`, la opción comentada `ENTREVISTA_DIR=../cameia-entrevista-CM-345` en `local-dev/.env.example` y una sección en `local-dev/README.md`.

## Decisiones tomadas sin aprobación previa (a revisar)

1. Reparto con CM-349 (lo elegiste tú): CM-345 = endpoint + validaciones + adaptador mínimo de transcripción; idioma detectado y reintentos quedan para CM-349 y CM-362.
2. `V8__respuesta_por_voz.sql` es idéntica byte a byte al borrador de CM-349 (operación `RESPONDER_AUDIO`) para que el merge no choque.
3. Concurrencia sin índice nuevo: el turno pasa a `PROCESSING` antes de transcribir (control de versión); un doble envío recibe 409 `SESSION_CONFLICT`. Si la transcripción falla, el turno queda `RETRYING` y la pregunta sigue pendiente.
4. Formato por contenido real (WebM/Opus, MP4/AAC, sin video). La duración es el máximo entre marcas de tiempo, duración declarada y suma de paquetes (TOC de Opus o muestras AAC), para que no se pueda esconder un audio largo. Tolerancia 181 s; mínimo 1,5 s (`AUDIO_TOO_SHORT`).
5. Límite del multipart subido a 12 MiB (umbral de disco 13 MiB: el audio nunca toca el disco). Entre 10 MiB + 1 y 12 MiB responde `AUDIO_TOO_LARGE`. La petición de Postman de 11 MB a `/health` pasó a 13 MB.
6. Gemini con el audio en línea (sin API de archivos ni bucket); los bytes se sobrescriben con ceros al terminar; nunca se registran el nombre, el audio ni el texto.
7. Sin evaluación del turno por la IA (es de HU-5.2): el turno queda `COMPLETED` y tras la 5.ª respuesta la sesión pasa a `EVALUANDO`.
8. Los códigos `SESSION_ABANDONED`, `SESSION_ALREADY_CLOSED` y `TRANSCRIPTION_*` siguen el contrato que ya usa la web.

Detalle completo: `specs/CM-345-RespuestaPorVoz/spec.md` §8 y `docs/adr/0012-respuesta-por-voz.md`.

## Riesgo de integración con CM-349

CM-349 también implementa `POST …/answers/audio`, pero con AssemblyAI y con audios WAV de prueba (CA-5.8.14 exige rechazar WAV). Antes de fusionar hay que elegir un proveedor y una implementación. Conflictos previsibles: `ErrorCode`, `BusinessExceptionHandler`, `docs/errores.md`, el controlador y `application.properties`. La V8 es idéntica en las dos ramas.

## Evidencia

- `./mvnw clean verify`: 1438 pruebas, 0 fallos, 2 omitidas (las de llamada real a Gemini) (27 E2E con PostgreSQL real en `AudioAnswerEndToEndTest`).
- Newman, carpeta «Responder por voz», contra el servicio levantado con PostgreSQL, RabbitMQ y el simulador: 39 peticiones, 159 aserciones, 0 fallos. Carpeta «Errores del framework» (multipart de 13 MB): 31/31.
- Transcripción real con Gemini (`GeminiTranscriptionLiveTest`, `LLM_LIVE_TEST=true`): WebM y MP4 de voz en español, unos 465 tokens de entrada por 13 s.
- Documentación: `specs/CM-345-RespuestaPorVoz/`, ADR 0012, bitácora 11, `CLAUDE.md`, `.env.example`, `postman/README.md`.

## Pendiente

- Revisión humana, ratificar D-345-1 a D-345-7 y decidir un proveedor único con CM-349.
- No se probó contra el stack `cameia-dev` (habría que reconstruir la Entrevista del otro agente).
