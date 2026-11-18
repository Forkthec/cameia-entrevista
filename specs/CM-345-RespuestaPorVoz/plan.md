# CM-345 · Plan

## Enfoque

Caso de uso `SubmitAudioAnswerService` con tres fases, el mismo patrón de `StartSessionService`: reserva corta del
turno, transcripción sin transacción y cierre corto. Las reglas de tamaño y duración viven en el dominio
(`AudioAnswerPolicy`); la lectura de los contenedores y el proveedor de voz son puertos (`AudioInspector`,
`TranscriptionPort`) implementados en infraestructura.

## Archivos

| Capa | Archivo | Qué |
|---|---|---|
| presentation | `controller/InterviewAnswerController` | `POST …/answers/audio`, solo lee los bytes de la parte |
| presentation | `dto/AnswerResponse` | Respuesta 200 común (`turn`, `progress`, `nextQuestion`, `interviewFinished`) |
| presentation | `advice/BusinessExceptionHandler` | 12 definiciones nuevas y manejador de `TranscriptionUnavailableException` (log en ERROR) |
| application | `command/SubmitAudioAnswerCommand`, `service/SubmitAudioAnswerService`, `service/AnswerResult` | Caso de uso |
| domain | `model/AudioFormat`, `AudioInspection`, `AudioClip`, `Transcription`, `AnswerAttempt` | Valores |
| domain | `policy/AudioAnswerPolicy` | 10 MiB, 181 s, 1,5 s |
| domain | `port/AudioInspector`, `TranscriptionPort`, `AnswerAttemptRepository` | Puertos |
| domain | `exception/*` | 12 excepciones y sus códigos |
| infrastructure | `media/ContainerAudioInspector`, `WebmAudioReader`, `Mp4AudioReader`, `ByteCursor` | Lectura acotada de EBML e ISO BMFF |
| infrastructure | `speech/GeminiTranscriptionAdapter`, `TranscriptionProperties` | Transcripción en línea, un intento, 90 s |
| infrastructure | `persistence/entity/AnswerAttemptEntity`, `repository/AnswerAttempt*` | `intento_respuesta` |
| resources | `V8__respuesta_por_voz.sql`, `application.properties` | Operación `RESPONDER_AUDIO`; multipart 12 MiB en memoria; `cameia.transcription.*` |

## Se reutiliza

`GetSessionStateService.findOwned` (mismo 404), `InterviewTurn` y su máquina de estados, `IdempotencyKeyRepository`,
el cliente `com.google.genai.Client` de `LanguageModelConfiguration` y el formato de error común.

## Matriz de pruebas

| Prueba | Qué cubre |
|---|---|
| `domain/policy/AudioAnswerPolicyTest` | Límites exactos 10 485 760/761 bytes, 180/181/182 s, 1,5 s |
| `infrastructure/media/ContainerAudioInspectorTest` | Audios reales de ffmpeg (WebM, MP4 fragmentado y plano, MP3, WAV, Vorbis, con video), WebM «en vivo» construido byte a byte, marcas de tiempo falsificadas, archivos truncados o corruptos, texto y ejecutables |
| `infrastructure/speech/GeminiTranscriptionAdapterTest` | Audio en línea sin API de archivos, sin voz, 400/429/500/503, sin candidatos, tiempo agotado, log sin texto |
| `infrastructure/speech/GeminiTranscriptionLiveTest` | Manual (`LLM_LIVE_TEST=true`): transcripción real de WebM y MP4 |
| `presentation/AudioAnswerEndToEndTest` | 27 casos con PostgreSQL real: todos los CA de backend, doble envío concurrente, idempotencia, fallos de transcripción y 5 respuestas |
| Postman, carpeta «Responder por voz» | 39 peticiones, 159 aserciones contra el servicio levantado con el simulador de Gemini |

## Riesgos

Ver §9 de la spec.
