# CM-372 · Escuchar la pregunta con voz (HU-5.15, Backend)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-372 «HU-5.15 – Backend: endpoint para escuchar la pregunta con Google Text-to-Speech». Subtarea de CM-371, Sprint 5 |
| Historia | HU-5.15, 30 criterios (backlog v11.6, hoja HE-05, filas 159 a 188) |
| Repositorio | `cameia-entrevista`. Base `origin/develop` en `25d0ba4` |
| Rama | `CM-372-escuchar-pregunta-con-voz`. Un PR hacia `develop`; no depende de CM-377 |
| Decisión | [ADR 0017](../../docs/adr/0017-voz-de-las-preguntas.md) |

## 1. Objetivo

Que la persona escuche cada pregunta ya mostrada con una voz natural y expresiva, hasta 3 veces por pregunta, sin que
CAMEIA guarde el audio. Cambio respecto de la tarea de Jira, decidido por el responsable de Backend: la voz es **Gemini
TTS** (no Google Cloud TTS) y un modelo coloca **etiquetas de voz** según el tono y el momento de la entrevista; la
salida es **WAV** (Gemini TTS no entrega MP3).

## 2. Contrato

`POST /api/v1/interviews/sessions/{sessionId}/questions/{number}/audio`, sin cuerpo, `X-User-Id` obligatorio.

- 200 `audio/wav` con `Content-Length`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`.
- Errores en `application/problem+json`: 401 `IDENTITY_REQUIRED`; 404 `SESSION_NOT_FOUND`, `QUESTION_NOT_FOUND`; 405;
  406 (un `Accept` sin `audio/wav`); 409 `SESSION_TRANSITION_NOT_ALLOWED`, `SESSION_ABANDONED`, `SESSION_ALREADY_CLOSED`,
  `QUESTION_NOT_AVAILABLE`, `QUESTION_AUDIO_LIMIT_REACHED`; 422 `SPEECH_TEXT_EMPTY`, `SPEECH_TEXT_TOO_LONG`; 503
  `SPEECH_DISABLED`, `SPEECH_MONTHLY_LIMIT_REACHED`, `SPEECH_BUSY`, `SPEECH_TIMEOUT`, `SPEECH_UNAVAILABLE`.
- `GET …/state` agrega `audioEnabled` y `audioPlaysRemaining` (pregunta pendiente e historial). Aditivo.

**Aviso a Frontend (CM-373):** cambiar `Accept: audio/mpeg` por `audio/wav` en `fetchQuestionAudio`; con `audio/mpeg` el
servicio responde 406. El resto del contrato provisional se mantiene.

## 3. Decisiones

| # | Decisión | Por qué |
|---|---|---|
| D1 | Gemini TTS `gemini-3.8-flash-tts`, respaldo `gemini-3.1-flash-tts-preview` ante 429/404/5xx | Decisión del responsable; reutiliza la llave de Gemini. Pruebas reales del 15-11-2026: 3.8 respondió en 5–9 s; 3.1 en 6,5 s |
| D2 | Director `gemini-3.5-flash-lite` con esquema JSON y 3 s | `gemini-3.8-flash` no respondió en 45 s en dos pruebas; el ligero respondió en 0,9–1,6 s |
| D3 | La dirección se valida en el dominio; si cambia una palabra, se lee la pregunta tal cual | La pregunta nunca cambia por culpa de un modelo |
| D4 | Etiquetas y estilos por tono, de la lista de Gemini, sin las que no caben en una entrevista | Estricto: suspiros, carraspeos, gruñidos; cálido: risas suaves; neutro: pausas y respiraciones |
| D5 | WAV en lugar de MP3 | Gemini TTS no entrega MP3; convertir exige una dependencia LGPL |
| D6 | `SPEECH_TIMEOUT` = 15 s | Medido: 9,2 s para 515 caracteres; 10 s fallaría en preguntas largas (CA-5.15.22 se prueba con un tope configurado de 10 s) |
| D7 | Contadores en tablas propias (`V10`) con sentencias condicionadas | `pregunta` es inmutable; atomicidad de CA-5.15.7 y CA-5.15.28 |
| D8 | Los caracteres del tope mensual son los de la pregunta original | Las etiquetas son pocas y no se leen como texto |
| D9 | El tope mensual se mira sobre lo consumido (899.999 admite una pregunta más; 900.000 no) | Lectura literal de CA-5.15.28 |
| D10 | `;charset=UTF-8` en `audio/wav` | Lo agrega la regla global `force-response` (ASVS 4.1.1); el navegador lo ignora |

## 4. Criterios (CA-5.15.x) y su prueba

| CA | Cómo se cubre | Prueba |
|---|---|---|
| 1 | `audioEnabled`, `audioPlaysRemaining` 3 en pregunta nueva, por sesión | `QuestionAudioEndToEndTest`, `QuestionAudioAvailabilityTest` |
| 2 | 200 `audio/wav` con encabezados; sin cambios en la entrevista | `QuestionAudioEndToEndTest`, Postman |
| 3 | Voz por idioma; tono no cambia la voz | `GeminiSpeechSynthesisAdapterTest` |
| 4 | Repetir sin pedir de nuevo | Frontend (CM-373) |
| 5 | Pregunta futura 409 `QUESTION_NOT_AVAILABLE` | `QuestionAudioPolicyTest`, `QuestionAudioEndToEndTest`, Postman |
| 6 | 3 usos, el 4.º 409 | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest`, Postman |
| 7 | Paralelo: un 200 y un 409, contador 3 | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` |
| 8 | 503 `SPEECH_TIMEOUT`/`SPEECH_UNAVAILABLE` sin descontar | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest`, Postman |
| 9 | Apagada: `audioEnabled` falso y 503 `SPEECH_DISABLED` | `ListenQuestionServiceTest`, `QuestionAudioAvailabilityTest` |
| 10 | Estados: 409 con su código, sin voz | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` |
| 11 | Ajena, inexistente o mal escrita: el mismo 404 | `QuestionAudioEndToEndTest`, Postman |
| 12 | Números inválidos 404 `QUESTION_NOT_FOUND`; `%zz` 400 (cubierto por `BusinessExceptionHandlerDispatchTest`; el cliente HTTP de la prueba no deja enviar esa URL) | `QuestionAudioPolicyTest`, `QuestionAudioEndToEndTest` |
| 13 | 499/500 se envían; 501 no | `QuestionAudioPolicyTest` |
| 14 | Última actividad igual | `QuestionAudioEndToEndTest` |
| 15 | Sin audio ni texto guardados ni registrados | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` (registros) |
| 16 | Etiqueta y teclado | Frontend |
| 17 | Preguntas 1 y 5 | `QuestionAudioPolicyTest`, `ListenQuestionServiceTest` |
| 18 | Turno en reintento: 200 y el turno no cambia | `QuestionAudioEndToEndTest` |
| 19 | UTF-8 sin alterar, emojis, NFD | `QuestionAudioPolicyTest`, `GeminiSpeechSynthesisAdapterTest` |
| 20 | Texto en blanco 422 | `QuestionAudioPolicyTest`, `ListenQuestionServiceTest` |
| 21 | Errores de Google y audio inválido 503; sin llave arranca y avisa | `GeminiSpeechSynthesisAdapterTest`, `SpeechConfigurationTest` |
| 22 | 9,9 s entrega; 10 s `SPEECH_TIMEOUT` (tope configurado en 10 s) | `GeminiSpeechSynthesisAdapterTest` |
| 23 | Devolución del uso; fallo de la devolución registrado | `ListenQuestionServiceTest` |
| 24 | Cliente que cancela: el uso queda gastado | Diseño (el uso solo vuelve si falla la voz) y `listen_shouldNotDeliver_whenSessionChangesDuringSynthesis` |
| 25 | Sesión abandonada durante la generación: 409 | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` |
| 26 | 11 simultáneas: 503 `SPEECH_BUSY` | `ListenQuestionServiceTest` |
| 27 | Cada generación cuenta | `QuestionAudioEndToEndTest` |
| 28 | Tope mensual 899.999/900.000 y cambio de mes | `ListenQuestionServiceTest`, `QuestionAudioEndToEndTest` |
| 29, 30 | Aviso y escucha automática | Frontend |

## 5. Pendientes

- Política de datos: nombrar que el texto de la pregunta sale hacia Gemini (PO).
- DevOps (CM-374): `SPEECH_*` por ambiente y medición de la latencia para fijar `SPEECH_TIMEOUT`.
- Frontend (CM-373): `Accept: audio/wav`.
- La prueba real del respaldo 3.1 con el cuerpo final (sin `mime_type`) no se repitió: se agotaron las 15 llamadas
  reales autorizadas. La forma del cuerpo se comprobó el 15-11-2026 con la llamada directa que respondió 200.
