# Plan · CM-372 Escuchar la pregunta con voz

## Capas

| Capa | Piezas |
|---|---|
| Dominio | `SpeechAudio`; `QuestionAudioPolicy`; puertos `SpeechSynthesisPort` y `SpeechUsageRepository`; 10 excepciones y sus códigos |
| Aplicación | `ListenQuestionService`, `QuestionAudioAvailability`, `SpeechSettings`; `GetSessionStateService` y `SessionState` con la voz |
| Infraestructura | `GoogleTextToSpeechAdapter` y `SpeechCredentials` (`infrastructure.client.googletts`); `SpeechUsageRepositoryAdapter`; `SpeechProperties` y `SpeechConfiguration`; migración `V10` |
| Presentación | `QuestionAudioController`; `SessionStateResponse`, `PendingQuestionResponse`, `HistoryItemResponse`; filas del manejador |
| Documentación | ADR 0017, `docs/errores.md`, `CLAUDE.md`, `.env.example`, Postman (carpeta y simulador), bitácora 18 |

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Latencia de Google por encima de 10 s | `SPEECH_TIMEOUT` configurable; 503 `SPEECH_TIMEOUT` sin gastar el uso; medición de DevOps (CM-374) |
| Cuenta de servicio sin permiso o API sin habilitar | 503 `SPEECH_UNAVAILABLE` con la causa en el registro; la entrevista sigue en texto |
| Contadores en carrera | Sentencias condicionadas; pruebas de concurrencia |
| Fuga de texto o credencial al registro | `toString` sin texto ni llave; pruebas con `CapturedOutput` |

## Verificación

`./mvnw.cmd clean verify` (cobertura de lo nuevo ≥ 90 % de líneas y ramas), Newman de toda la colección con el
simulador y la prueba real manual `GoogleTextToSpeechLiveTest` (`SPEECH_LIVE_TEST=true`).
