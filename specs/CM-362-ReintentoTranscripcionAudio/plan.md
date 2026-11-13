# CM-362 · Plan

1. Dominio: `TranscriptionFailure`, clase de fallo en `TranscriptionUnavailableException`, `TranscriptionRetryPolicy`, número de intento en `TranscriptionExecution`.
2. Infraestructura: clasificación en `AssemblyAiTranscriptionAdapter`; bean de la política en `TranscriptionConfiguration`; `cameia.transcription.retry-pause` y `total-budget`.
3. Aplicación: bucle de intentos en `SubmitAudioAnswerService` con el turno reservado.
4. Pruebas: política, adaptador (503, conexión rechazada, tiempo, otros), caso de uso (reintento, agotado, tiempo, rechazo, doble envío durante el reintento) y punta a punta con PostgreSQL.
5. Postman: escenario `stuck` del simulador, segundo 503 en la carpeta de CM-345 y carpeta «Reintento de la transcripción (HU-5.13)».
