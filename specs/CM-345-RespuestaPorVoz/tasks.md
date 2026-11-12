# CM-345 · Tareas

- [x] T1. Códigos y excepciones: 12 valores en `ErrorCode`, sus excepciones con el mensaje literal del criterio, definiciones en `BusinessExceptionHandler` y filas en `docs/errores.md` (`ErrorCodeDocumentationTest` en verde).
- [x] T2. Dominio: `AudioAnswerPolicy` con `AudioAnswerPolicyTest` (10 485 760 acepta, 10 485 761 rechaza; 181 s acepta, 181,001 s y 182 s rechazan; 1,499 s rechaza, 1,5 s acepta).
- [x] T3. Lectura del contenido: `ContainerAudioInspector` y sus lectores; audios de prueba en `src/test/resources/audio` generados con ffmpeg (tono, silencio de 180 y 182 s, voz sintetizada en español). Si un lector lanza otra excepción que `MalformedMediaException` o `ArithmeticException`, agregarla al inspector: nunca debe salir un 500.
- [x] T4. Transcripción: `GeminiTranscriptionAdapter` en el paquete `speech` (aparte de `ia`, cuyo contexto de prueba escanea el paquete) y `TranscriptionProperties`; `cameia.transcription.model=${TRANSCRIPTION_MODEL:${cameia.llm.model}}`.
- [x] T5. Persistencia: `AnswerAttemptEntity` y su adaptador; `V8__respuesta_por_voz.sql` copiada sin cambios de CM-349.
- [x] T6. Caso de uso y controlador con `AudioAnswerEndToEndTest` (27 pruebas).
- [x] T7. Configuración: multipart 12 MiB, umbral 13 MiB; Postman de `/health` de 11 a 13 MB.
- [x] T8. Postman: carpeta «Responder por voz» y simulador que transcribe; Newman 159/159.
- [x] T9. Prueba real con Gemini (`GeminiTranscriptionLiveTest`): WebM y MP4 transcritos.
- [x] T10. Documentación: spec, plan, ADR 0012, bitácora 11, `CLAUDE.md`, `.env.example`, `postman/README.md`.
- [ ] T11. Revisión humana y decisión de proveedor único con CM-349 (ver §9 de la spec).
