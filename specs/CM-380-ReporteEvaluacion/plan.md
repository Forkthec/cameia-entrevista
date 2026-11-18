# Plan — Reporte de Evaluación (CM-380)

Base: `origin/develop` `0eec549`. Rama: `CM-380-reporte-de-evaluacion`. Un PR hacia `develop` con 4 commits en orden de
lectura (spec §11). Requisitos y decisiones: `spec.md`.

## 0. Foco de revisión (lo que más probablemente salga mal)

| # | Riesgo | Prueba que lo vigila |
|---|---|---|
| F-1 | La marca del reporte choca con `ck_ejecucion_ia_turno` (exige turno salvo en `PLANIFICAR`) | `ReportSchemaMigrationTest.aiExecution_shouldAcceptReportWithoutTurn` |
| F-2 | La llamada del reporte usa el modelo de evaluación (3 s; 8 s en el despliegue) en lugar del de 20 s | `GeminiLanguageModelAdapterTest.reportOperation_shouldUseThePlanningModel`, ídem en Groq |
| F-3 | Dos consultas a la vez llaman dos veces a la IA | `EvaluationReportConcurrencyTest.twoReadsAtOnce_shouldCallTheAiOnceAndAnswer200And202` |
| F-4 | División entre 0 o un 0 donde va «sin resultado» | `VoiceMetricsTest.noVoiceAnswers_shouldGiveNullMetrics`, `VoiceSampleTest.zeroSpan_shouldGiveNoSample`, `StarResultTest.none_shouldGiveLevel0NotNull` |
| F-5 | El texto de la persona o la lista de palabras llega al log o a la base | `ReportLogContentTest`, `ReportSchemaMigrationTest.answerTable_shouldOnlyAddFourIntegerVoiceColumns`, `TranscriptionTest.toString_shouldNotPrintWords` |
| F-6 | Un fallo del lector o del modelo termina en 500 en vez de reporte con nulos | `ReportGeneratorTest.exhaustedCycle_shouldEmitWithNullContentAndStarAndKeepVoice`, `EvaluationReportEndToEndTest.threeFailedAttempts_shouldEmitWithNullContent` |
| F-7 | Marca vencida que nunca se retoma, o marca viva que se pisa | `EvaluationReportConcurrencyTest.staleClaim_shouldBeTakenOver` y `.liveClaim_shouldAnswer202` |
| F-8 | Cambiar `Transcription` o `AnswerAttempt.spoken` rompe las pruebas de voz existentes | Suite completa de `SubmitAudioAnswerServiceTest`, `VoiceAnswerEndToEndTest`, `AudioAnswerEndToEndTest` |

Todos tienen prueba en las tarjetas.

## 1. Enfoque

1. **Voz (commit 1).** El adaptador de AssemblyAI pide `disfluencies` y entrega las palabras con tiempos en
   `Transcription.words()`. El dominio calcula `VoiceSample` (con `FillerWords`). `AnswerRegistrar.startEvaluation`
   recibe un `AcceptedSpeech` y guarda la respuesta con sus 4 números. `V11` completa (las 5 partes de spec §5) va en
   este commit para que el esquema quede en una sola migración.
2. **Generación (commit 2).** Dominio del reporte (`VoiceMetrics`, `ContentScores`, `StarResult`, `EvaluationReport`),
   prompt y lector, `ReportGenerator` (reserva → ciclo → guardado) y la persistencia del reporte. Los adaptadores de IA
   usan el modelo largo para `REPORT`.
3. **Consulta (commit 3).** `GetEvaluationReportService`, controlador, DTO, `ErrorCode`, manejador, OpenAPI,
   `docs/errores.md`, Postman y el simulador.
4. **Documentación (commit 4).** ADR 0018 (borrador ya escrito en `docs/adr/0018-reporte-de-evaluacion.md`), `CLAUDE.md`,
   `.env.example`, `postman/README.md` y la bitácora de IA.

## 2. Qué se reutiliza

| Pieza | Ruta | Uso |
|---|---|---|
| `AiCallRunner`, `AttemptSwitch`, `AiCycleFailedException`, `AiRetryPolicy` | `application/service`, `domain/policy` | Ciclo de 3 intentos con respaldo, sin cambios |
| Patrón de reserva y marca vencida | `StartSessionService.reserve`, `closeStaleMark`, `switchPlanning` | Copiado en `ReportGenerator` con la operación `REPORT` |
| `AiExecution.isLive`, `failed`, `succeeded` | `domain/model/AiExecution` | Marca y cierre |
| `GetSessionStateService.findOwned` + `SessionCommand.of` | `application/service`, `application/command` | 404 idéntico para ajena, inexistente o mal escrita |
| `EvaluationPromptBuilder.replaceInOnePass` y lectura de `prompts/` | `application/service` | Armado del prompt sin reemplazos en cadena |
| `EvaluationOutputReader` (estilo de validación) | `application/service` | Modelo del `ReportOutputReader` |
| `ScriptedModel`, `Gate`, `CountingWaiter`, `Doubles` | `src/test/.../presentation/AnswerFlowSupport.java` | Pruebas de punta a punta y de concurrencia |
| `AbstractPersistenceTest` | `src/test/.../infrastructure/persistence` | Pruebas de esquema y persistencia |
| `ConstraintNames` | `infrastructure/persistence/repository` | Traducir `pk_reporte_evaluacion` |

No se crea nada que ya exista. Lo nuevo es lo propio del reporte (spec §7).

## 3. Orden y dependencias

```
T1 (V11) → T2 (palabras del adaptador) → T3 (VoiceSample/FillerWords) → T4 (guardar con la respuesta)   = commit 1
T5 (dominio del reporte) → T6 (prompt y lector) → T7 (adaptadores IA) → T8 (persistencia del reporte)
  → T9 (ReportGenerator)                                                                                    = commit 2
T10 (servicio de consulta + controlador + errores) → T11 (E2E, concurrencia, logs) → T12 (Postman/Newman) = commit 3
T13 (ADR y documentación)                                                                                   = commit 4
T14 (verificación final sobre la punta)
```

Sin paralelo: cada tarjeta usa lo de la anterior. Estimación honesta: 10 a 12 horas de implementación y verificación (incluye las preguntas, las respuestas y el encabezado del reporte).

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| El plazo (17-nov) | Tarjetas cerradas; la ADR y la medición ya están hechas |
| El simulador de Postman no distingue el prompt del reporte | T12 agrega la detección por `<entrevista>` antes de las reglas existentes |
| Gemini 503 en la prueba en vivo | El ciclo lo cubre; la prueba en vivo se repite si un proveedor está saturado |
| `LayeredArchitectureTest` falla por un import de `application` en `domain` | Las clases de dominio de §7 no importan nada de fuera de `domain` |

## 5. Línea base

Antes de tocar nada: `./mvnw.cmd -B -ntp clean verify` sobre `0eec549`, con Docker Desktop encendido (las pruebas de
Testcontainers se omiten sin Docker). Si no pasa, se detiene y se reporta: es un hallazgo con destino, no se arregla de
paso.
