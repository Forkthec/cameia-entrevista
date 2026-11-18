# CM-349 · Plan

## Enfoque

Un endpoint multipart nuevo que orquesta tres fases sin transacción abierta mientras responde el proveedor
(el mismo patrón de CM-212): validar con lecturas sueltas, transcribir con AssemblyAI y, en una transacción corta,
registrar la respuesta y abrir el turno siguiente. La transcripción se valida en el dominio (`TranscriptionPolicy`).

## Archivos

| Capa | Archivo | Qué hace |
|---|---|---|
| Migración | `db/migration/V8__respuesta_por_voz.sql` | Operación anti-duplicados `RESPONDER_AUDIO` |
| Dominio | `domain/model/Transcription`, `AudioAnswer`, `TranscriptionExecution`, `AnswerAttempt` | Valores del caso de uso; ninguno imprime audio ni texto en `toString` |
| Dominio | `domain/policy/TranscriptionPolicy`, `AudioAnswerPolicy` | Vacía, idioma, largo; tipos de audio admitidos |
| Dominio | `domain/port/SpeechTranscriptionPort`, `TranscriptionExecutionRepository`, `AnswerAttemptRepository` | Puertos nuevos |
| Dominio | `domain/exception/Transcription*Exception`, `AudioFormatNotAllowedException`, `AnswerFormatNotAllowedException`; `ErrorCode` (+9) | Errores nuevos |
| Aplicación | `application/command/AudioAnswerCommand`, `application/service/SubmitAudioAnswerService`, `AnswerRegistrar`, `AnswerResult` | Caso de uso y registro común de respuestas |
| Infraestructura | `infrastructure/client/assemblyai/AssemblyAiTranscriptionAdapter` | REST v2 con `java.net.http.HttpClient` (sin dependencias nuevas) |
| Infraestructura | `infrastructure/config/voz/TranscriptionProperties`, `TranscriptionConfiguration` | `cameia.transcription.*` y ensamble |
| Infraestructura | `infrastructure/persistence/entity/AnswerAttemptEntity`, `TranscriptionExecutionEntity` y sus repositorios y adaptadores | Persistencia |
| Presentación | `presentation/controller/AudioAnswerController`, `presentation/dto/AnswerResponse`; `BusinessExceptionHandler` (+6 definiciones, manejador de `TranscriptionUnavailableException`) | Contrato HTTP |
| Configuración | `application.properties`, `.env.example`, `docker-compose.yml` | Multipart en memoria y variables de AssemblyAI |
| Documentación | `docs/errores.md`, `CLAUDE.md`, `postman/`, `docs/bitacora-ia/14_respuesta-por-voz_prompt.md` | Catálogo, contexto, colección y bitácora |

## Qué se reutiliza

`GetSessionStateService.findOwned` (404 uniforme), `InterviewTurnRepository.findOpenBySession`,
`InterviewQuestionRepository.findBySession`, `InterviewTurn.open/startProcessing/complete`, `IdempotencyKeyRepository`
y el patrón de repetición de `CreateSessionService`, `ProblemDetailTestSupport` en las pruebas, `ConstraintNames`.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Choque de la migración `V8` con otra rama | Pendiente anotado en la spec §10: renumerar al rebasar |
| Choque con la tarea de respuesta escrita (HU-5.2) | Archivos propios; `AnswerRegistrar` queda listo para reutilizar |
| Latencia de AssemblyAI cerca del tope del Gateway | `TRANSCRIPTION_TIMEOUT = 90s` < 120 s del Gateway < 130 s del Web |
| Cambio de nombres de modelo en AssemblyAI | `ASSEMBLYAI_SPEECH_MODELS` configurable; vacío deja el valor por defecto del proveedor |
| Audio en disco por el multipart de Tomcat | `file-size-threshold` igual al tope: la parte nunca se escribe en disco |

## Matriz de pruebas

| Prueba | Tipo | Cubre |
|---|---|---|
| `domain/policy/TranscriptionPolicyTest` | Unitaria | CA-5.12.2, 5.12.7, 5.12.9, 5.12.10, 5.12.11; NFC y emojis; umbral |
| `domain/policy/AudioAnswerPolicyTest` | Unitaria | Tipos admitidos |
| `application/service/SubmitAudioAnswerServiceTest` | Unitaria con dobles | CA-5.12.1, 5.12.3, 5.12.4, 5.12.6, 5.12.8; validación; idempotencia |
| `infrastructure/client/assemblyai/AssemblyAiTranscriptionAdapterTest` | Servidor HTTP simulado del JDK | Cuerpo de la petición, espera, error, 401, tiempo agotado, JSON inválido, sin llave |
| `infrastructure/persistence/VoiceAnswerPersistenceTest` | PostgreSQL real (Testcontainers) | `intento_respuesta` `AUDIO`, índice de respuesta definitiva, `ejecucion_ia` `TRANSCRIBIR`, migración `V8` |
| `presentation/controller/AudioAnswerControllerTest` | MockMvc | Forma de la respuesta 200 y de cada error |
| `presentation/VoiceAnswerEndToEndTest` | Servidor real + PostgreSQL real, transcriptor simulado | Las 5 preguntas por voz, vacía, fallo, repetición por clave, entrevista por texto |
| `postman/` carpeta «Respuesta por voz (CM-349)» | Manual contra el servicio | Recorrido con AssemblyAI real |
