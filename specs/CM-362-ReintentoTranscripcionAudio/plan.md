# CM-362 · Plan

## Capas

| Capa | Pieza | Qué hace |
|---|---|---|
| `domain.model` | `TurnStatus`, `InterviewTurn` | Transiciones `PROCESSING/RETRYING → PENDING` (`release`) e `isBusy` (reserva vigente) |
| `domain.model` | `InterviewSession.recordActivity` | Marca la actividad al registrar una respuesta |
| `domain.model` | `AudioClip`, `Transcription`, `TranscriptionFailure`, `AnswerAttempt` | Audio en memoria (sin `toString` del contenido), texto transcrito (sin `toString` del texto), clase de fallo y respuesta registrada |
| `domain.policy` | `TranscriptionRetryPolicy` | Máximo 2 intentos; solo reintenta `TRANSIENT` del primero |
| `domain.port` | `SpeechToTextPort`, `AnswerAttemptRepository` | Un intento por llamada; inserción de la respuesta |
| `domain.exception` | `TurnNotFound`, `TurnProcessing`, `TurnAlreadyAnswered`, `TranscriptionUnavailable`, `TranscriptionEmpty`, `TranscriptionTooLong` | Con su código en `ErrorCode`, su fila en el manejador y en `docs/errores.md` |
| `application` | `SubmitAudioAnswerService`, `SubmitAudioAnswerCommand`, `AnswerResult` | Comprobaciones → reserva (tx) → transcripción con reintento (sin tx) → registro (tx); libera el turno en cualquier fallo |
| `infrastructure.client` | `VoiceServiceTranscriptionAdapter` | HTTP del JDK, multipart, tope total por intento, clasificación del fallo |
| `infrastructure.config.voz` | `VoiceServiceProperties`, `VoiceServiceConfiguration` | `cameia.voz.*` validado al arrancar |
| `infrastructure.persistence` | `AnswerAttemptEntity`, `AnswerAttemptJpaRepository`, `AnswerAttemptRepositoryAdapter` | Tabla `intento_respuesta` existente (sin migración) |
| `presentation` | `InterviewAnswerController`, `AnswerResponse` | `POST …/answers/audio` y la respuesta común |

## Configuración

| Propiedad | Variable | Defecto |
|---|---|---|
| `cameia.voz.base-url` | `VOZ_BASE_URL` | `http://localhost:8090` |
| `cameia.voz.timeout` | `VOZ_TIMEOUT` | `60s` |
| `cameia.voz.connect-timeout` | `VOZ_CONNECT_TIMEOUT` | `5s` |
| `cameia.voz.retry-pause` | `VOZ_RETRY_PAUSE` | `1s` |
| `spring.servlet.multipart.*` | — | 10 MB, umbral 10 MB (todo en memoria) |

## Pruebas

- Unidad: `TranscriptionRetryPolicyTest`, `TurnStatusTest`, `InterviewTurnTest`, `SubmitAudioAnswerServiceTest` (23 casos, repositorio de turnos en memoria con versión).
- Adaptador contra servidor HTTP simulado del JDK: `VoiceServiceTranscriptionAdapterTest` (9 casos).
- Punta a punta con PostgreSQL real y Voz simulado: `AudioAnswerRetryEndToEndTest` (5 casos).
- Sin migración nueva: Hibernate valida `AnswerAttemptEntity` contra `V1` en todo `@SpringBootTest`.
