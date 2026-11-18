# CM-372 · Escuchar la pregunta con voz (HU-5.15, Backend)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-372 «HU-5.15 – Backend: endpoint para escuchar la pregunta con Google Text-to-Speech». Subtarea de CM-371, Sprint 5 |
| Historia | HU-5.15, 30 criterios (backlog v11.6, hoja HE-05, filas 159 a 188) |
| Repositorio | `cameia-entrevista`. Base `origin/develop` en `25d0ba4` |
| Rama | `CM-372-escuchar-pregunta-con-voz`. Un PR hacia `develop`; no depende de CM-377 |
| Decisión | [ADR 0017](../../docs/adr/0017-voz-de-las-preguntas.md) |

## 1. Objetivo

Que la persona escuche cada pregunta ya mostrada con una voz natural, hasta 3 veces por pregunta, sin que CAMEIA
guarde el audio. La voz es la que fija la tarea: Google Cloud Text-to-Speech con una voz Chirp 3 HD, llamada desde el
servicio, con salida MP3 y un tiempo máximo de 10 s.

## 2. Contrato

`POST /api/v1/interviews/sessions/{sessionId}/questions/{number}/audio`, sin cuerpo, `X-User-Id` obligatorio.

- 200 `audio/mpeg` con `Content-Length`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`.
- Errores en `application/problem+json`: 401 `IDENTITY_REQUIRED`; 404 `SESSION_NOT_FOUND`, `QUESTION_NOT_FOUND`; 405;
  406 (un `Accept` sin `audio/mpeg`); 409 `SESSION_TRANSITION_NOT_ALLOWED`, `SESSION_ABANDONED`,
  `SESSION_ALREADY_CLOSED`, `QUESTION_NOT_AVAILABLE`, `QUESTION_AUDIO_LIMIT_REACHED`; 422 `SPEECH_TEXT_EMPTY`,
  `SPEECH_TEXT_TOO_LONG`; 503 `SPEECH_DISABLED`, `SPEECH_MONTHLY_LIMIT_REACHED`, `SPEECH_BUSY`, `SPEECH_TIMEOUT`,
  `SPEECH_UNAVAILABLE`.
- `GET …/state` agrega `audioEnabled` y `audioPlaysRemaining` (pregunta pendiente e historial). Aditivo.

**Aviso a Frontend (CM-373):** el contrato provisional se mantiene (`Accept: audio/mpeg`); el cambio en el estado es
aditivo.

## 3. Decisiones

| # | Decisión | Por qué |
|---|---|---|
| D1 | Google Cloud Text-to-Speech, `POST /v1/text:synthesize`, MP3 | Lo fija la tarea de Jira y la especificación de HU-5.15 |
| D2 | Voz Chirp 3 HD por idioma: `es-US-Chirp3-HD-Kore` y `en-US-Chirp3-HD-Kore`, configurables | La especificación pide `es-US` y `en-US` y deja la voz exacta a la configuración; el tono no cambia la voz |
| D3 | Credencial: la cuenta de servicio (ADC) en Cloud Run; `SPEECH_API_KEY` solo para desarrollo y el simulador | Sin secretos nuevos en producción; las ADC se cargan en la primera petición, así el servicio arranca sin ellas |
| D4 | Sin reintento ni respaldo | El tiempo máximo es de 10 s; cualquier error de Google es 503 `SPEECH_UNAVAILABLE` (CA-5.15.21) |
| D5 | `SPEECH_TIMEOUT` = 10 s, contando la obtención de la credencial | El del backlog; DevOps lo ajusta con CM-374 |
| D6 | REST con el cliente HTTP del JDK y `google-auth-library` | Ya llegaba con el SDK de Gemini; la biblioteca gRPC suma dependencias para una sola llamada |
| D7 | Contadores en tablas propias (`V10`) con sentencias condicionadas | `pregunta` es inmutable; atomicidad de CA-5.15.7 y CA-5.15.28 |
| D8 | El tope mensual se mira sobre lo consumido (899.999 admite una pregunta más; 900.000 no) | Lectura literal de CA-5.15.28 |
| D9 | `;charset=UTF-8` en `audio/mpeg` | Lo agrega la regla global `force-response` (ASVS 4.1.1); el navegador lo ignora |

## 4. Criterios (CA-5.15.x) y su prueba

| CA | Cómo se cubre | Prueba |
|---|---|---|
| 1 | `audioEnabled`, `audioPlaysRemaining` 3 en pregunta nueva, por sesión | `QuestionAudioEndToEndTest`, `QuestionAudioAvailabilityTest` |
| 2 | 200 `audio/mpeg` con encabezados; sin cambios en la entrevista | `QuestionAudioEndToEndTest`, Postman |
| 3 | Voz por idioma (`es-US`, `en-US`); el tono no cambia la voz | `GoogleTextToSpeechAdapterTest`, `ListenQuestionServiceTest`, Postman |
| 4 | Repetir sin pedir de nuevo | Frontend (CM-373) |
| 5 | Pregunta futura 409 `QUESTION_NOT_AVAILABLE` | `QuestionAudioPolicyTest`, `QuestionAudioEndToEndTest`, Postman |
| 6 | 3 usos, el 4.º 409 | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest`, Postman |
| 7 | Paralelo: un 200 y un 409, contador 3 | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` |
| 8 | 503 `SPEECH_TIMEOUT`/`SPEECH_UNAVAILABLE` sin descontar | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest`, Postman |
| 9 | Apagada: `audioEnabled` falso y 503 `SPEECH_DISABLED` | `ListenQuestionServiceTest`, `QuestionAudioAvailabilityTest` |
| 10 | Estados: 409 con su código, sin voz | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` |
| 11 | Ajena, inexistente o mal escrita: el mismo 404 | `QuestionAudioEndToEndTest`, Postman |
| 12 | Números inválidos 404 `QUESTION_NOT_FOUND`. Excepciones, rechazadas por el servidor antes del caso de uso y sin el error genérico: vacío 404 `ROUTE_NOT_FOUND` («No existe la ruta solicitada.»), `%00` 400 `REQUEST_PATH_INVALID_FORMAT` y `%zz` 400 (`BusinessExceptionHandlerDispatchTest`) | `QuestionAudioPolicyTest`, `QuestionAudioEndToEndTest` |
| 13 | 499/500 se envían; 501 no | `QuestionAudioPolicyTest` |
| 14 | Última actividad igual | `QuestionAudioEndToEndTest` |
| 15 | Sin audio ni texto guardados ni registrados; la credencial nunca se imprime | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest`, `SpeechConfigurationTest` |
| 16 | Etiqueta y teclado | Frontend |
| 17 | Preguntas 1 y 5 | `QuestionAudioPolicyTest`, `ListenQuestionServiceTest` |
| 18 | Turno en reintento: 200 y el turno no cambia | `QuestionAudioEndToEndTest` |
| 19 | UTF-8 sin alterar, emojis de 4 bytes, URL; nunca más de 5.000 bytes | `QuestionAudioPolicyTest`, `GoogleTextToSpeechAdapterTest` |
| 20 | Texto en blanco 422 | `QuestionAudioPolicyTest`, `ListenQuestionServiceTest` |
| 21 | 400/403/429/5xx, JSON ilegible o cortado, sin audio, vacío o no MP3, sin credencial: 503 | `GoogleTextToSpeechAdapterTest`, `SpeechCredentialsTest`, `SpeechConfigurationTest`, Postman |
| 22 | 9,9 s entrega; 10 s `SPEECH_TIMEOUT` | `GoogleTextToSpeechAdapterTest` |
| 23 | Devolución del uso; fallo de la devolución registrado | `ListenQuestionServiceTest` |
| 24 | Cliente que cancela: el uso queda gastado | Diseño (el uso solo vuelve si falla la voz) y `listen_shouldNotDeliver_whenSessionChangesDuringSynthesis` |
| 25 | Sesión abandonada durante la generación: 409 | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` |
| 26 | 11 simultáneas: 503 `SPEECH_BUSY` | `ListenQuestionServiceTest` |
| 27 | Cada generación cuenta | `QuestionAudioEndToEndTest` |
| 28 | Tope mensual 899.999/900.000 y cambio de mes | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` |
| 29, 30 | Aviso y escucha automática | Frontend |

## 5. Pendientes

- CA-5.15.12 pide «No encontramos lo que buscabas.» también para el número vacío y `%00`; hoy los rechaza el servidor
  antes del caso de uso con su código común (`ROUTE_NOT_FOUND` y `REQUEST_PATH_INVALID_FORMAT`). Cambiarlo toca el
  manejo global de rutas; queda para decisión del PO y Backend.

- Política de datos: nombrar que el texto de la pregunta sale hacia Google Cloud Text-to-Speech (PO).
- DevOps (CM-374): habilitar `texttospeech.googleapis.com`, dar a la cuenta de servicio de Cloud Run el permiso de
  Text-to-Speech, definir `SPEECH_*` por ambiente y medir la latencia para fijar `SPEECH_TIMEOUT`.
- Prueba real con Google (`GoogleTextToSpeechLiveTest`): pendiente de una credencial con Text-to-Speech habilitado; no
  se usó ninguna credencial real en esta tarea.
