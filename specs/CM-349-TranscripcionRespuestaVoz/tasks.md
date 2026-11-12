# CM-349 · Tareas

- [x] **T-01 Migración.** `V8__respuesta_por_voz.sql` agrega `RESPONDER_AUDIO` a `ck_clave_idempotencia_operacion`. Prueba: `VoiceAnswerPersistenceTest.migrationV8_*` (acepta `RESPONDER_AUDIO`, rechaza `RESPONDER_VIDEO`).
- [x] **T-02 Dominio.** `Transcription`, `AudioAnswer`, `TranscriptionExecution`, `AnswerAttempt`; puertos; 9 códigos en `ErrorCode` y sus excepciones. Prueba: `ErrorCodeTest` (todo código tiene emisor), `ErrorCodeDocumentationTest` (fila en `docs/errores.md`).
- [x] **T-03 Reglas.** `TranscriptionPolicy` (vacía → idioma → largo; NFC; umbral) y `AudioAnswerPolicy`. Prueba: `TranscriptionPolicyTest` con 4000 «a» aceptadas, 4001 rechazadas, 4000 «e» + U+0301 aceptadas, `es` con 0,95 en entrevista `en` rechazada, `en` con 0,79 en entrevista `es-CO` aceptada.
- [x] **T-04 Adaptador de AssemblyAI.** Subida, creación con `language_detection`, `expected_languages`, `fallback_language`, `on_no_speech_detected=fallback` y `speech_models`; espera por sondeo con tope total. Prueba: `AssemblyAiTranscriptionAdapterTest` contra `HttpServer` del JDK.
- [x] **T-05 Persistencia.** `AnswerAttemptEntity` y `TranscriptionExecutionEntity` con sus adaptadores; el índice `uq_intento_definitivo_por_turno` se traduce a `TURN_TRANSITION_NOT_ALLOWED`. Prueba: `VoiceAnswerPersistenceTest`.
- [x] **T-06 Caso de uso.** `SubmitAudioAnswerService` (tres fases, idempotencia, borrado del audio) y `AnswerRegistrar`. Prueba: `SubmitAudioAnswerServiceTest`.
- [x] **T-07 API.** `AudioAnswerController`, `AnswerResponse`, definiciones y manejador en `BusinessExceptionHandler`; multipart en memoria. Prueba: `AudioAnswerControllerTest`, `VoiceAnswerEndToEndTest`.
- [x] **T-08 Configuración.** `cameia.transcription.*`, `.env.example`, `docker-compose.yml`.
- [x] **T-09 Documentación.** `docs/errores.md`, `CLAUDE.md`, Postman, bitácora.
- [x] **T-10 Verificación.** `./mvnw clean verify` en verde (1445 pruebas, 0 fallos, 1 omitida).

Si algo no cuadra: si AssemblyAI cambia el nombre de un campo de la detección de idioma, se ajusta solo en
`AssemblyAiTranscriptionAdapter.transcriptRequest` y su prueba; si otra rama fusiona una `V8`, se renumera esta.
