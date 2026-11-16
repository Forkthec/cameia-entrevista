# Plan · CM-372 Escuchar la pregunta con voz

## Capas

| Capa | Piezas |
|---|---|
| Dominio | `SpeechDirection`, `SpeechAudio`; `SpeechDirectionPolicy`, `QuestionAudioPolicy`; puertos `SpeechSynthesisPort`, `SpeechDirectorPort`, `SpeechUsageRepository`; 10 excepciones y sus códigos |
| Aplicación | `ListenQuestionService`, `QuestionAudioAvailability`, `SpeechSettings`; `GetSessionStateService` y `SessionState` con la voz |
| Infraestructura | `GeminiSpeechSynthesisAdapter`, `GeminiSpeechDirectorAdapter` (`infrastructure.client.gemini`); `SpeechUsageRepositoryAdapter`; `SpeechProperties` y `SpeechConfiguration`; migración `V10`; prompt `voz.direccion.v1.txt` |
| Presentación | `QuestionAudioController`; `SessionStateResponse`, `PendingQuestionResponse`, `HistoryItemResponse`; filas del manejador |
| Documentación | ADR 0017, `docs/errores.md`, `CLAUDE.md`, `.env.example`, Postman (carpeta y simulador), bitácora 18 |

## Riesgos

| Riesgo | Mitigación |
|---|---|
| El modelo de dirección cambia la pregunta | Validación de dominio palabra por palabra; si falla, se lee tal cual |
| Demanda alta de Gemini TTS | Respaldo 3.1 dentro del mismo tiempo máximo |
| Latencia de preguntas largas | `SPEECH_TIMEOUT` 15 s configurable; medición de DevOps |
| Contadores en carrera | Sentencias condicionadas; pruebas de concurrencia |
| Fuga de texto al registro | `toString` sin texto; pruebas con `CapturedOutput` |

## Verificación

`./mvnw.cmd clean verify` (cobertura de lo nuevo ≥ 90 % de líneas y ramas), Newman de toda la colección con el
simulador y la prueba real manual `GeminiSpeechLiveTest` (`SPEECH_LIVE_TEST=true`).
