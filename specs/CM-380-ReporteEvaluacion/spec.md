# Spec — Reporte de Evaluación: datos de voz, generación con la IA y consulta (CM-380)

- **Tarea Jira:** CM-380 (subtarea de Backend de la historia CM-378, «HU-6.1 a HU-6.4 Reporte de Evaluación»). Hermana de
  Frontend: CM-379 (pantalla, Sofía).
- **Historias:** HU-6.1 (decisión escrita), HU-6.2 (métricas de contenido y de voz), HU-6.3 (Método STAR). Fuente de los
  criterios: backlog v11.8 `16112026_02_Backlog_v11_8.xlsx`, hoja HE-06. HU-6.4 (pantalla) es de Frontend; de ella se toma el
  contrato de la respuesta (observaciones de HU-6.4).
- **Repositorio y base:** `cameia-entrevista`, `origin/develop` en `0eec549` (incluye CM-372). Última migración: `V10`;
  esta tarea agrega `V11`. Último ADR: `0017`; esta tarea agrega `0018`.
- **Estado:** `APROBADA` por Paula Andrea Muñoz Delgado el 16-nov-2026 (noche), con P-1 a P-4 de §2.2 (ver §14).

Marcas usadas en esta spec: **[H]** hecho confirmado (con su fuente), **[I]** inferencia justificada, **[D]** decisión
humana (quién y cuándo).

---

## 1. Objetivo y alcance

Cuando una persona termina una Simulación (sesión `FINALIZADA`), puede pedir su Reporte de Evaluación. El servicio lo
genera la primera vez que se pide, dentro de esa misma petición, y lo guarda para siempre:

1. **Datos de voz al transcribir.** Cada respuesta por voz guarda 4 números calculados con los tiempos de cada palabra
   que ya devuelve AssemblyAI: palabras, tramo de habla, pausas y muletillas. Se calculan en la misma petición que
   registra la respuesta, antes de descartar el audio.
2. **Generación.** Una sola llamada a la IA (Gemini, con Groq de respaldo y hasta 3 intentos) califica de 1 a 5 las tres
   métricas de contenido de cada respuesta no conductual y marca los 4 componentes STAR de cada respuesta conductual.
   Las métricas de voz se calculan con los datos guardados. Una marca de generación con vencimiento de 2 minutos impide
   dos generaciones a la vez. El reporte emitido no cambia.
3. **Consulta.** `GET /api/v1/interviews/sessions/{sessionId}/evaluation-report` devuelve 200 con el reporte, 202 si
   otra petición lo está generando, o los errores de la tabla de §6.
4. **Decisión escrita.** ADR `0018` con el proveedor, las escalas, las fórmulas, las muletillas (con el resultado de la
   medición del 16-nov-2026) y los casos «sin resultado».

**Fuera de alcance:** la pantalla (CM-379), incluidos los textos de explicación y el bloque «Próximamente» de DP-3 y DP-4, que Backend solo redacta; el resumen por plan (HU-6.5 y HU-6.6); el Entusiasmo (F0), que queda siempre
«sin resultado»; la respuesta de seguimiento en el STAR (CA-6.3.7, no aplica mientras HU-5.9 no esté entregada); el
listado «Mis entrevistas» (HU-5.10); el modo Entreno; el análisis cualitativo y las recomendaciones (HU-6.6, DP-4). La retroalimentación por
respuesta que ya guarda la evaluación (`feedback_turno.feedback`) no se expone: es un diagnóstico de entrevistador, puede
contradecir el STAR y en una sesión en inglés sale en español (muestra real en
`Desarrollo/ejecucion/evidencia/CM-380/medicion/muestra-feedback-evaluacion.json`); ver H-7.

## 2. Decisiones

### 2.1 Decisiones de Paula Andrea Muñoz Delgado (16-nov-2026)

| # | Decisión | Alternativa descartada | Fuente |
|---|---|---|---|
| DP-1 | **Inglés:** la Tasa de Vacilación se calcula igual, aunque AssemblyAI solo conservó 9 de 16 muletillas (nunca «er», «erm» ni «hmm»). El ADR deja escrito que en inglés cuenta en la práctica solo «um» y «uh». | Tasa de Vacilación «sin resultado» en inglés (era la recomendación del asistente) | Respuesta en el chat de la spec, 16-nov |
| DP-3 | **Pantalla con explicación:** cada métrica y el Método STAR llevan un texto fijo que dice qué mide, de dónde sale y cómo se lee, más un aviso de que las de contenido las califica una IA. Lo muestra Frontend; Backend redacta los textos porque las fórmulas son suyas. Criterio nuevo propuesto CA-6.4.7 (para comentar con el equipo) | Pantalla solo con números | Chat de la spec, 16-nov |
| DP-4 | **Recomendaciones de pago:** no se generan ni se guardan en esta tarea; la pantalla muestra el bloque «Reporte completo · Próximamente» sin botón activo. Quedan para la historia del reporte por plan (HU-6.6) | Generarlas y guardarlas ya; mostrarlas a todos | Chat de la spec, 16-nov |
| DP-6 | **Preguntas y respuestas en el reporte:** el reporte trae, por cada pregunta, su número, su tipo, su texto, la respuesta guardada tal cual (con muletillas si fue por voz), si fue por texto o por voz y, si fue por voz, sus palabras, muletillas, Ritmo de Habla, Tiempo de Pausas y Tasa de Vacilación de esa sola respuesta. No trae las notas de contenido por respuesta ni la retroalimentación de la evaluación | Solo pregunta y respuesta; además las 3 notas por respuesta (tabla nueva, más tiempo) | Chat de la spec, 16-nov |
| DP-7 | **Encabezado:** el reporte trae la fecha de la entrevista (`sesion.fecha_fin`), su idioma y su tono | Solo la fecha de emisión | Chat de la spec, 16-nov |
| DP-8 | **Textos de ayuda:** cada métrica, el Método STAR, la respuesta y los datos de voz de cada respuesta tienen un botón «?» que abre su explicación (clic, toque, teclado y, en computador, cursor; se cierra con Esc, tocando fuera o seleccionándolo de nuevo; uno a la vez; con nombre accesible). El aviso sobre la IA, el texto de «Sin resultado» y los estados «Respondida por texto: sin datos de voz.» y «Sin respuesta registrada.» quedan visibles. Textos literales en español e inglés en el anexo para Frontend (`Desarrollo/comunicaciones/16112026_v1_anexo-textos-reporte-para-frontend.json`) | Solo al pasar el cursor (no sirve en celular ni con teclado); texto siempre visible debajo de cada métrica | Chat de la spec, 16-nov |
| DP-5 | **Sin rangos ideales:** los textos de voz explican el cálculo y no dicen si un valor es bueno o malo | Dar una referencia externa (por ejemplo 120 a 160 palabras por minuto) | Chat de la spec, 16-nov |
| DP-9 | **Tipo de pregunta en pantalla:** «Apertura», «Técnica», «Conductual» y «Situacional» (en: Opening, Technical, Behavioral, Situational). Los textos de ayuda dicen «preguntas conductuales» y explican en cada texto que son las que piden contar una experiencia vivida | «De comportamiento» como etiqueta | Revisión del backlog, 16-nov |
| DP-10 | **Respuesta por voz sin datos de voz** (`answerOrigin = VOICE` y `voice = null`): la pantalla muestra visible «Respondida por voz: sin datos de voz.» | Dejar el espacio vacío | Revisión del backlog, 16-nov |
| DP-11 | **Fecha del encabezado:** solo la fecha, en la hora local del navegador y con el formato largo del idioma de la pantalla («17 de noviembre de 2026») | Fecha y hora; hora de Colombia fija | Revisión del backlog, 16-nov |
| DP-12 | **Vuelta:** botón «Volver a inicio» que lleva al Tablero de inicio; «Ver mis entrevistas» cuando exista HU-5.10 | Solo el menú general | Revisión del backlog, 16-nov |
| DP-13 | **503 `DATABASE_UNAVAILABLE`:** el reporte usa el mensaje que ya tiene el servicio; cambiarlo en todo Entrevista queda como hallazgo H-8 | Cambiar el mensaje ahora en todos los endpoints | Revisión del backlog, 16-nov |
| DP-2 | **Texto de la respuesta por voz:** con la opción de muletillas encendida se guarda y se evalúa tal cual, con muletillas y repeticiones. Se ve así en el historial de la sesión. | Quitar las muletillas del texto antes de guardarlo | Respuesta en el chat de la spec, 16-nov |

### 2.2 Propuestas del libro v11.7 que esta spec adopta (se aprueban con la spec; hoja «Cambios v11.7», pendientes 1 a 5)

Las celdas en amarillo del libro son propuestas. Esta spec las adopta tal cual; si Paula rechaza alguna, la spec se
corrige antes de ejecutar.

| # | Propuesta | Recomendación | Efecto si se rechaza |
|---|---|---|---|
| P-1 | Valores de HU-6.1 (HE-06 K8, K9, K10, K12): rúbrica 1–5 de CA-6.1.2, silencio de 500 ms, lista cerrada de muletillas, 20 s por intento, 2 min de vencimiento de la marca | Aprobar. La medición confirmó los 500 ms (detectó las 4 pausas hechas a propósito) y la lista; las 4 llamadas reales tardaron entre 1,3 y 2,6 s, muy por debajo de los 20 s | Cambian constantes de §4 y §5 |
| P-2 | Resultado de la medición (HE-06 K10, CA-6.1.4) | Aprobar con DP-1. Detalle en `ejecucion/evidencia/CM-380/medicion/resultado.md` | — |
| P-3 | Contrato propuesto (HE-06 O35): nombres de campos y los códigos nuevos `SESSION_NOT_FINISHED` y `REPORT_GENERATING` | Aprobar con el hallazgo H-1 (§12): los errores salen en el formato común del servicio (`detail`, no `message`); solo el 202 lleva `message` | Cambia §6 y el aviso a Frontend |
| P-4 | En el MVP todos los planes ven el mismo reporte (HE-08 K9 y regla «Beneficios por plan») | Aprobar. HU-6.5 y HU-6.6 siguen fuera y el Plan Premium no se puede activar en el Cliente Web. El servicio no lee `X-User-Plan` para el reporte | Habría que filtrar por plan: fuera del plazo del 17-nov |
| P-5 | Prioridad, valor y riesgo de HU-6.1 a HU-6.4 (Backlog E42, E43, E44 y E56) | Lo decide el equipo; no afecta a esta spec | — |

### 2.3 Decisiones técnicas de Backend (con su porqué)

| # | Decisión | Por qué | Alternativa descartada |
|---|---|---|---|
| DT-1 | La marca de generación es una fila de `ejecucion_ia` con `tipo_operacion = 'GENERAR_REPORTE'` y `estado = 'EN_CURSO'`, protegida por un índice único parcial por sesión. Es el mismo patrón que la marca de planificación (`V6`, `StartSessionService.reserve` y `closeStaleMark`) | Reutiliza la tabla, la auditoría por intento (CA-6.2.6: «cada intento queda en `ejecucion_ia` con una operación nueva de reporte y su número») y el ciclo `AiCallRunner` + `AttemptSwitch`, que ya trabaja con esas filas | Una columna «marca» en la tabla del reporte (lo sugiere el libro): duplicaría el estado y no da el registro por intento |
| DT-2 | El tope de 20 s por intento es `LLM_TIMEOUT` (`cameia.llm.timeout`, 20000 ms por defecto), el mismo de la planificación. Los adaptadores usan el modelo «largo» para toda operación que no sea `EVALUAR` | Ya existe con ese valor por defecto y no está fijado en el despliegue [H: `despliegue-continuo.yml` no define `LLM_TIMEOUT`]. Una variable nueva con el mismo valor no aporta nada | `REPORT_LLM_TIMEOUT` nueva |
| DT-3 | El vencimiento de 2 min es `REPORT_GENERATION_CLAIM_TTL` (`cameia.report.generation-claim-ttl`, `2m` por defecto) | CA-6.1.6 pide una variable con valor por defecto. Debe ser mayor que el peor ciclo (61,5 s); se valida al arrancar | Derivarlo del tiempo de la IA, como `PlanningClaim`: el libro fija 2 min |
| DT-4 | El cálculo de los 4 datos de voz y las 3 métricas de voz vive en el dominio (`VoiceSample`, `VoiceMetrics`, `FillerWords`). El adaptador de AssemblyAI solo entrega la lista de palabras con sus tiempos, en memoria | Las reglas de negocio van en `domain` y se prueban sin Spring; la lista de palabras nunca sale de la petición | Calcular en el adaptador: mezcla reglas con infraestructura |
| DT-5 | La lista de palabras viaja en `Transcription.words()` y nunca se guarda ni se registra; `Transcription.toString()` no la imprime | CA-6.1.3 y CA-6.2.3: se guardan los 4 números, nunca la lista | — |
| DT-6 | El reporte se guarda en dos tablas: `reporte_evaluacion` (una fila por sesión) y `reporte_star` (una fila por pregunta conductual) | Columnas tipadas con `CHECK` por rango (estándar de base de datos); la restricción única por sesión es la clave primaria | Un `jsonb` con todo: sin restricciones por campo |
| DT-7 | El 202 se responde con un DTO propio `{code, message, requestId}` en `application/json`, no con `ProblemDetail` | Es una respuesta correcta (no un error) y el libro fija esa forma, que Frontend ya simula | `ProblemDetail` con estado 202 |
| DT-8 | Redondeo `HALF_UP` en todas las métricas: promedios de contenido a 2 decimales; Ritmo de Habla a entero; Tiempo de Pausas y Tasa de Vacilación a 1 decimal | Es el redondeo «escolar» que espera quien lee el reporte; el ejemplo literal de CA-6.2.4 da exacto | `HALF_EVEN` |
| DT-9 | La IA recibe el texto de la pregunta (`pregunta.texto_original`) y la respuesta definitiva (`intento_respuesta.respuesta_texto`). Una pregunta sin respuesta definitiva no se envía: si es conductual, su STAR queda «sin resultado»; si no, no cuenta para el contenido | Una sesión `FINALIZADA` tiene las 5 respuestas [I: `ConsolidateSessionService` cierra al quinto turno], pero el caso se cubre para no caer en el 500 | — |
| DT-10 | `issuedAt` se guarda truncado a segundos | Coincide con el ejemplo del contrato (`2026-11-17T15:04:05Z`) y hace idénticas las lecturas | Microsegundos |
| DT-11 | El prompt del reporte es `prompts/reporte.v1.txt` y su esquema `prompts/esquema.reporte.v1.json`, con el mismo mecanismo de carga que `EvaluationPromptBuilder`; versión `reporte.v1` en `ejecucion_ia.version_prompt` | Probado en vivo el 16-nov con Gemini y Groq (`medicion/resultado.md` §4) | — |
| DT-12 | Tope de salida de la llamada: 1024 tokens | La salida real fue de 249 tokens (Gemini) y 171 (Groq) para 5 preguntas | — |

## 3. Criterios de aceptación → requisito → prueba → Postman

Las pruebas nombradas están en `src/test/java/tech/cameia/entrevista/`. Las peticiones de Postman están en la carpeta
nueva `Reporte de Evaluación` de `postman/cameia-entrevista.postman_collection.json`.

| CA | Requisito (§4) | Prueba | Postman |
|---|---|---|---|
| CA-6.1.1 | REQ-GEN-01, REQ-GEN-05, REQ-DOC-01 | `ReportGeneratorTest.generate_shouldMakeOneCallWithTheReportOperation`; prueba en vivo manual con `medicion/llamada_reporte.py` y el prompt final (§9) | R-01 |
| CA-6.1.2 | REQ-GEN-03, REQ-GEN-04 | `ContentScoresTest` (tabla), `ReportOutputReaderTest` | R-01 |
| CA-6.1.3 | REQ-VOZ-02 a REQ-VOZ-05, REQ-GEN-07 | `VoiceSampleTest`, `VoiceMetricsTest` | R-02 |
| CA-6.1.4 | REQ-VOZ-01, REQ-VOZ-04, REQ-DOC-01 | `FillerWordsTest`, `AssemblyAiTranscriptionAdapterTest.transcriptRequest_shouldAskForDisfluencies` | — |
| CA-6.1.5 | REQ-GEN-08 | `EvaluationReportTest.enthusiasm_shouldAlwaysBeNull` | R-01 |
| CA-6.1.6 | REQ-GEN-01, REQ-GEN-10, REQ-GEN-11 | `EvaluationReportConcurrencyTest`, `ReportPropertiesTest` | R-03 |
| CA-6.1.7 | REQ-GEN-06, REQ-GEN-07, REQ-GEN-09 | `VoiceMetricsTest`, `ReportGeneratorTest` | R-01, R-02 |
| CA-6.2.1 | REQ-GEN-02 a REQ-GEN-05, REQ-API-01 | `EvaluationReportEndToEndTest.textSession_shouldReturnContentAndStarAndNullVoice` | R-01 |
| CA-6.2.2 | REQ-GEN-07 | `EvaluationReportEndToEndTest.textSession_shouldReturnContentAndStarAndNullVoice`, `VoiceMetricsTest.noVoiceAnswers_shouldGiveNullMetrics` | R-01 |
| CA-6.2.3 | REQ-VOZ-01 a REQ-VOZ-06 | `VoiceSampleTest`, `VoiceAnswerPersistenceTest.save_shouldStoreTheFourVoiceNumbers`, `VoiceAnswerEndToEndTest.answeringByVoice_shouldStoreVoiceDataAndNeverTheWords` | R-02 (setup) |
| CA-6.2.4 | REQ-GEN-07 | `VoiceMetricsTest.bookExample_shouldGive120WordsPerMinute12PercentAnd2Hesitation` | R-02 |
| CA-6.2.5 | REQ-VOZ-05, REQ-GEN-07 | `VoiceSampleTest.zeroSpan_shouldGiveNoSample`, `VoiceSampleTest.oneTimedWord_shouldKeepItsSpan`, `VoiceMetricsTest.answersWithoutVoiceData_shouldNotCount` | — |
| CA-6.2.6 | REQ-GEN-06, REQ-GEN-12 | `ReportGeneratorTest.exhaustedCycle_shouldEmitWithNullContentAndStarAndKeepVoice`, `EvaluationReportEndToEndTest.threeFailedAttempts_shouldEmitWithNullContent`, `ReportLogContentTest` | R-04 |
| CA-6.2.7 | REQ-API-03, REQ-API-04 | `GetEvaluationReportServiceTest` (parametrizada por estado), `EvaluationReportEndToEndTest.notFinished_shouldReturn409` | R-05, R-06 |
| CA-6.2.8 | REQ-GEN-13, REQ-DB-03 | `EvaluationReportEndToEndTest.secondRead_shouldReturnTheSameBodyWithoutCallingTheAi`, `ReportPersistenceTest.secondReport_shouldBeRejected` | R-07 |
| CA-6.2.9 | REQ-GEN-06 | `ReportGeneratorTest.onlyBehavioralQuestions_shouldGiveNullContentAndKeepStar` | — |
| CA-6.2.10 | REQ-GEN-05 | `ReportGeneratorTest.invalidOutputThenValid_shouldUseTheSecondAttempt`, `EvaluationReportEndToEndTest.limitOnPrimary_shouldUseFallback` | R-04 |
| CA-6.2.11 | REQ-GEN-10, REQ-GEN-11 | `EvaluationReportConcurrencyTest.twoReadsAtOnce_shouldCallTheAiOnceAndAnswer200And202`, `EvaluationReportConcurrencyTest.staleClaim_shouldBeTakenOver`, `EvaluationReportConcurrencyTest.liveClaim_shouldAnswer202` | R-03 |
| CA-6.3.1 | REQ-GEN-04, REQ-API-01 | `EvaluationReportEndToEndTest.textSession_shouldReturnContentAndStarAndNullVoice` | R-01 |
| CA-6.3.2 | REQ-GEN-03 | `ReportGeneratorTest.nonBehavioralQuestions_shouldNotAppearInStar` | R-01 |
| CA-6.3.3 | REQ-GEN-04 | `StarResultTest.allFour_shouldGiveLevel4` | — |
| CA-6.3.4 | REQ-GEN-04 | `StarResultTest.withoutResult_shouldGiveLevel3AndResultFalse` | R-01 |
| CA-6.3.5 | REQ-GEN-04 | `StarResultTest.none_shouldGiveLevel0NotNull` | — |
| CA-6.3.6 | REQ-GEN-06 | `ReportGeneratorTest.exhaustedCycle_shouldEmitWithNullContentAndStarAndKeepVoice` | R-04 |
| CA-6.3.7 | Fuera de alcance (§1) | No aplica: hoy toda pregunta avanza sin seguimiento [H: CA-6.3.7, columna Técnico] | — |
| CA-6.4.1 (B) | REQ-API-01, REQ-API-09, REQ-API-10 | `EvaluationReportControllerTest.ready_shouldMapTheContract`, `EvaluationReportEndToEndTest.questions_shouldListFiveAnswersWithOriginAndVoice` | R-01, R-02 |
| CA-6.4.7 | Solo Frontend (textos y botón «?», DP-8) | — | — |
| CA-6.4.3 (B) | REQ-API-02 | `EvaluationReportConcurrencyTest.twoReadsAtOnce_shouldCallTheAiOnceAndAnswer200And202` | R-03 |
| CA-6.4.4 (B) | REQ-API-03, REQ-API-04 | `EvaluationReportEndToEndTest.abandoned_shouldReturn409`, `.notFinished_shouldReturn409` | R-05, R-06 |
| CA-6.4.5 (B) | REQ-API-05 | `EvaluationReportEndToEndTest.foreignMissingAndMalformed_shouldReturnTheSame404` | R-08, R-09, R-10 |
| CA-6.4.6 (B) | REQ-API-06 | `GetEvaluationReportServiceTest.databaseDown_shouldThrowDatabaseUnavailable` | — |

CA-6.4.2 es solo de Frontend (columna Técnico `F`). El servicio lo cumple devolviendo `null` o `0` según REQ-GEN-06 y
REQ-GEN-07.

## 4. Requisitos (EARS)

### 4.1 Datos de voz al transcribir (commit 1)

- **REQ-VOZ-01.** El servicio debe pedir a AssemblyAI cada transcripción con `"disfluencies": true`, además de los
  campos que ya envía `AssemblyAiTranscriptionAdapter.transcriptRequest` (detección de idioma, idiomas esperados,
  idioma de respaldo, `on_no_speech_detected` y `speech_models`). [D: DP-2; H: medición §3]
- **REQ-VOZ-02.** Cuando AssemblyAI devuelva la transcripción, el adaptador debe entregar en `Transcription.words()` cada
  elemento de `words` que tenga `text` de tipo cadena y `start` y `end` numéricos con `end >= start`, en el orden en que
  llegan. Un elemento sin esos campos se descarta. Si `words` no viene o no es arreglo, la lista queda vacía.
- **REQ-VOZ-03.** Cuando se registre una respuesta por voz, el servicio debe calcular con `words()`:
  - `palabras` = cantidad de palabras de la lista;
  - `tramoMs` = `end` de la última − `start` de la primera;
  - `pausasMs` = suma de `start(i) − end(i−1)` para cada par de palabras seguidas cuya diferencia sea ≥ 500;
  - `muletillas` = cantidad de palabras cuyo texto, normalizado, está en la lista del idioma de la entrevista (REQ-VOZ-04).
- **REQ-VOZ-04.** La normalización de una palabra para contar muletillas debe: pasar a minúsculas (`Locale.ROOT`),
  descomponer en NFD y quitar las marcas diacríticas, y quitar todo carácter que no sea letra ni dígito
  (`[^\p{L}\p{Nd}]`). Listas cerradas: español (`es-CO`): `eh, ehh, ehm, em, emm, mm, mmm, ah`; inglés (`en`):
  `um, umm, uh, uhh, er, erm, hmm, mm`. Ejemplos: `«Eh,»` → `eh` (cuenta); `«Mm-hmm.»` → `mmhmm` (no cuenta);
  `«ÉH»` → `eh` (cuenta); `«ahí»` → `ahi` (no cuenta).
- **REQ-VOZ-05.** Si la lista de palabras está vacía o `tramoMs` es 0, la respuesta debe guardarse con sus 4 datos de voz
  nulos, sin fallar el registro. Si `tramoMs > 0`, se guardan los 4 (también con una sola palabra).
- **REQ-VOZ-06.** El servicio debe guardar los 4 datos en la misma inserción de `intento_respuesta` que guarda la
  respuesta (la tabla es de solo inserción) y nunca debe guardar ni registrar en el log la lista de palabras, su texto ni
  el audio. Una respuesta por texto deja los 4 datos nulos. El contrato de `POST .../answers/audio` no cambia.

### 4.2 Generación (commit 2)

- **REQ-GEN-01.** Cuando se consulte el reporte de una sesión `FINALIZADA` propia que no tiene reporte y no tiene una
  marca viva, el servicio debe generarlo dentro de esa misma petición, sin proceso en segundo plano ni evento.
- **REQ-GEN-02.** El servicio debe enviar a la IA, en una sola llamada, el idioma de la sesión (`es-CO` o `en`), el
  cargo al que aspira la persona (el nombre del rol de la sesión), la oferta laboral en texto libre si la sesión la tiene
  y, por cada pregunta con respuesta definitiva y en orden de número: su número, su tipo, el texto de la pregunta y el
  texto de la respuesta. No envía el identificador de la persona, el contexto profesional del perfil ni los datos de voz.
  El cargo y la oferta sirven para juzgar si la respuesta es pertinente para el cargo (condición D de Precisión y
  Claridad); son los mismos datos que ya recibe la planificación de las preguntas y la persona ya aceptó el uso de IA.
  Decisión de Paula, 17-nov. [H: HU-6.2, Observaciones, «Privacidad»]
- **REQ-GEN-03.** La salida de la IA debe validarse (`ReportOutputReader`): JSON con exactamente las claves `contenido` y
  `star`; en `contenido` un elemento por cada pregunta no conductual enviada y en `star` uno por cada conductual enviada,
  cada número una sola vez y ninguno de más; en `contenido`, exactamente las claves `numero`, `precision_claridad`,
  `carisma_verbal` y `lenguaje_adecuado`, con enteros de 1 a 5; en `star`, exactamente `numero`, `situacion`, `tarea`,
  `accion` y `resultado`, con booleanos. Cualquier otra cosa lanza `LlmInvalidOutputException` (`LLM_INVALID_OUTPUT`) y
  cuenta como intento fallido. Una pregunta repetida o ausente es la señal de que un texto de la persona intentó
  fabricar o esconder una pregunta; el error es visible y nunca se emite una nota.
- **REQ-GEN-14.** Antes de armar el prompt, el servicio debe neutralizar los textos de la persona (respuestas, cargo y
  oferta): `<` y `>` y sus versiones de ancho completo (`＜` y `＞`) se reemplazan por `‹` y `›`, y una línea de una
  respuesta que empiece con `Pregunta <n>(` se antepone con `– `, para que ningún texto de la persona pueda cerrar un
  bloque, abrir otro ni fabricar el encabezado de una pregunta. El prompt es la versión `reporte.v1` (condiciones
  contadas por métrica, topes de pertinencia y de «solo una orden», temperatura global sin cambio).
- **REQ-GEN-04.** El nivel STAR de una pregunta debe ser la cantidad de componentes `true` (de 0 a 4), calculado por el
  servicio. 0 es un valor calculado, no «sin resultado».
- **REQ-GEN-05.** La llamada debe usar `AiCallRunner` con la política `AiRetryPolicy` (2 intentos con Gemini, el tercero
  con Groq; esperas `LLM_RETRY_FIRST_WAIT` 500 ms y `LLM_RETRY_SECOND_WAIT` 1 s; un 429 no se repite con el mismo
  proveedor), con `LLM_TIMEOUT` (20 s) por intento y una fila de `ejecucion_ia` por intento con
  `tipo_operacion = 'GENERAR_REPORTE'`, `turno_id` nulo, su `numero_intento`, `modelo`, `proveedor` y
  `version_prompt = 'reporte.v1'`.
- **REQ-GEN-06.** Si el ciclo termina sin salida válida, el servicio debe emitir el reporte con las 3 métricas de
  contenido nulas y, en cada pregunta conductual, los 4 componentes y el nivel nulos; las métricas de voz se conservan. Si
  no hay preguntas no conductuales con respuesta, las 3 de contenido quedan nulas aunque la IA responda. Si no hay
  ninguna pregunta con respuesta, no se llama a la IA.
- **REQ-GEN-07.** Las métricas de voz se calculan con la suma de `palabras`, `tramoMs`, `pausasMs` y `muletillas` de las
  respuestas definitivas por voz que tienen datos de voz (las 4 columnas no nulas):
  - Ritmo de Habla = `palabras × 60000 ÷ tramoMs`, entero `HALF_UP`;
  - Tiempo de Pausas = `pausasMs × 100 ÷ tramoMs`, 1 decimal `HALF_UP`;
  - Tasa de Vacilación = `muletillas × 100 ÷ palabras`, 1 decimal `HALF_UP`.
  Si ninguna respuesta tiene datos de voz (sesión por texto, respuestas anteriores a `V11` o todas sin tramo), las 3
  quedan nulas. Nunca se divide entre 0: con datos de voz, `tramoMs > 0` y `palabras ≥ 1` por construcción (REQ-VOZ-05).
- **REQ-GEN-08.** El Entusiasmo (F0) debe guardarse y devolverse siempre nulo.
- **REQ-GEN-09.** Cada promedio de contenido debe ser la media de los enteros de las preguntas calificadas, con 2
  decimales `HALF_UP` (por ejemplo, 5, 1 y 3 → 3,00; 5, 2 y 3 → 3,33; 5, 3 y 2 → 3,33; 4 y 5 → 4,50).
- **REQ-GEN-10.** Antes de llamar a la IA, el servicio debe reservar la generación guardando la marca
  (`ejecucion_ia` `GENERAR_REPORTE`/`EN_CURSO`, intento 1) en una transacción. Si ya hay una marca de esa sesión con
  menos de `REPORT_GENERATION_CLAIM_TTL` (2 min) desde su `fecha_inicio`, o si el índice
  `uq_ejecucion_ia_reporte_en_curso` rechaza la marca nueva, debe responder 202 `REPORT_GENERATING` sin llamar a la IA.
- **REQ-GEN-11.** Si la marca existente tiene 2 min o más, el servicio debe cerrarla como `FALLIDA` con
  `error_codigo = 'LLM_UNAVAILABLE'`, registrar un `WARN` con `sessionId` y `executionId`, y reservar de nuevo (retoma
  la generación).
- **REQ-GEN-12.** Cuando el ciclo de la IA se agote, el servicio debe registrar un `WARN`
  `Reporte emitido sin calificación de la IA [sessionId={}, executionId={}, code={}]`; el `requestId` va en el MDC. Ningún
  log lleva el texto de preguntas ni respuestas, ni la salida del modelo.
- **REQ-GEN-13.** Al terminar, el servicio debe guardar en una transacción el reporte, sus filas STAR y el cierre de la
  marca (`EXITOSA` con los tokens, o `FALLIDA` con la causa del último intento si el ciclo se agotó). Si la inserción
  choca con `pk_reporte_evaluacion` (otro proceso lo emitió tras retomar una marca vencida), debe cerrar su marca y
  devolver el reporte ya guardado. Un reporte emitido no se modifica ni se recalcula.

### 4.3 Consulta (commit 3)

- **REQ-API-01.** `GET /api/v1/interviews/sessions/{sessionId}/evaluation-report` con identidad del Gateway debe
  responder `200` y `application/json;charset=UTF-8` con el cuerpo de §6.1.
- **REQ-API-02.** Cuando otra petición tenga la marca viva, debe responder `202` y `application/json;charset=UTF-8` con
  `{"code":"REPORT_GENERATING","message":"Tu reporte se está generando.","requestId":"<requestId>"}`.
- **REQ-API-03.** Si la sesión está `ABANDONADA`: `409` `SESSION_ABANDONED`, «Sesión abandonada: no genera evaluación.»
- **REQ-API-04.** Si la sesión está `CONFIGURADA`, `EN_CURSO` o `EVALUANDO`: `409` `SESSION_NOT_FINISHED`, «Tu entrevista
  aún no ha terminado.»
- **REQ-API-05.** Si la sesión es de otra persona, no existe o `sessionId` no es un UUID: `404` `SESSION_NOT_FOUND`, «No
  encontramos lo que buscabas.», con el mismo cuerpo en los tres casos (salvo `requestId`).
- **REQ-API-06.** Si la base de datos no responde en cualquier paso: `503` `DATABASE_UNAVAILABLE`, «Ocurrió un error.
  Inténtalo de nuevo.»
- **REQ-API-07.** Sin `X-User-Id` válido: `401` `IDENTITY_REQUIRED` (comportamiento existente del servicio).
- **REQ-API-08.** El servicio no lee `X-User-Plan` para el reporte: todos los planes reciben el mismo cuerpo. [D: P-4]
- **REQ-API-09.** El cuerpo 200 debe traer `interviewDate` (= `sesion.fecha_fin`, UTC a segundos) y `tone`
  (`PROFESIONAL_NEUTRO`, `CALIDO` o `ESTRICTO`, el valor guardado en `sesion.tono`). [D: DP-7]
- **REQ-API-10.** El cuerpo 200 debe traer `questions`: un elemento por cada pregunta de la sesión (5), en orden de
  número, con `number`, `type` (`APERTURA`, `TECNICA`, `CONDUCTUAL` o `SITUACIONAL`), `questionText`
  (`pregunta.texto_original`, el mismo texto que se le mostró a la persona), `answerText` (la respuesta definitiva tal como
  se guardó, o `null` si no hay), `answerOrigin` (`TEXT`, `VOICE` o `null` si no hay respuesta) y `voice`. `voice` es
  `null` si la respuesta fue por texto o no tiene datos de voz; si los tiene, es
  `{words, fillers, speechRate, pauseTime, hesitationRate}` con los 4 datos guardados de esa respuesta y las fórmulas y
  el redondeo de REQ-GEN-07 aplicados a esa sola respuesta. Las preguntas y respuestas se leen al consultar desde
  `pregunta` e `intento_respuesta`, que no cambian después de la sesión, así que el cuerpo es el mismo en cada consulta.
  [D: DP-6]

### 4.4 Documentación (commit 4)

- **REQ-DOC-01.** ADR `docs/adr/0018-reporte-de-evaluacion.md` con todo lo que pide HU-6.1 (CA-6.1.1 a CA-6.1.7), el
  resultado de la medición y DP-1 y DP-2.
- **REQ-DOC-02.** OpenAPI del endpoint (200, 202, 401, 404, 409, 503 con ejemplos), `docs/errores.md` con los dos
  códigos nuevos y el endpoint nuevo en las filas existentes, `postman/README.md` y la colección, y la sección de
  estructura de `CLAUDE.md` del repo.

## 5. Base de datos — migración `V11__reporte_de_evaluacion.sql`

```sql
-- 1) Datos de voz por respuesta: 4 numeros, nunca la lista de palabras ni el audio.
ALTER TABLE microentrevista.intento_respuesta
    ADD COLUMN voz_palabras    integer NULL,
    ADD COLUMN voz_tramo_ms    integer NULL,
    ADD COLUMN voz_pausas_ms   integer NULL,
    ADD COLUMN voz_muletillas  integer NULL,
    ADD CONSTRAINT ck_intento_respuesta_voz_completa CHECK (
        (voz_palabras IS NULL AND voz_tramo_ms IS NULL AND voz_pausas_ms IS NULL AND voz_muletillas IS NULL)
        OR (voz_palabras IS NOT NULL AND voz_tramo_ms IS NOT NULL AND voz_pausas_ms IS NOT NULL AND voz_muletillas IS NOT NULL)),
    ADD CONSTRAINT ck_intento_respuesta_voz_valores CHECK (
        voz_palabras IS NULL OR (voz_palabras >= 1 AND voz_tramo_ms > 0 AND voz_pausas_ms >= 0
            AND voz_pausas_ms <= voz_tramo_ms AND voz_muletillas >= 0 AND voz_muletillas <= voz_palabras)),
    ADD CONSTRAINT ck_intento_respuesta_voz_origen CHECK (origen_respuesta = 'AUDIO' OR voz_palabras IS NULL);

-- 2) Operacion nueva de la IA: generar el reporte, sin turno.
ALTER TABLE microentrevista.ejecucion_ia
    DROP CONSTRAINT ck_ejecucion_ia_tipo_operacion,
    ADD CONSTRAINT ck_ejecucion_ia_tipo_operacion
        CHECK (tipo_operacion IN ('PLANIFICAR', 'EVALUAR', 'TRANSCRIBIR', 'GENERAR_REPORTE')),
    DROP CONSTRAINT ck_ejecucion_ia_turno,
    ADD CONSTRAINT ck_ejecucion_ia_turno
        CHECK ((tipo_operacion IN ('PLANIFICAR', 'GENERAR_REPORTE')) = (turno_id IS NULL));

-- 3) Marca de generacion: una sola ejecucion GENERAR_REPORTE en curso por sesion.
CREATE UNIQUE INDEX uq_ejecucion_ia_reporte_en_curso
    ON microentrevista.ejecucion_ia (sesion_id)
    WHERE tipo_operacion = 'GENERAR_REPORTE' AND estado = 'EN_CURSO';

-- 4) Reporte de Evaluacion: uno por sesion, inmutable.
CREATE TABLE microentrevista.reporte_evaluacion (
    sesion_id           uuid          CONSTRAINT pk_reporte_evaluacion PRIMARY KEY
                                      CONSTRAINT fk_reporte_evaluacion_sesion REFERENCES microentrevista.sesion (id) ON DELETE CASCADE,
    precision_claridad  numeric(3,2)  NULL,
    carisma_verbal      numeric(3,2)  NULL,
    lenguaje_adecuado   numeric(3,2)  NULL,
    ritmo_habla         integer       NULL,
    tiempo_pausas       numeric(4,1)  NULL,
    tasa_vacilacion     numeric(4,1)  NULL,
    entusiasmo          numeric(5,2)  NULL,
    fecha_emision       timestamptz   NOT NULL,
    CONSTRAINT ck_reporte_evaluacion_precision_claridad CHECK (precision_claridad IS NULL OR precision_claridad BETWEEN 1 AND 5),
    CONSTRAINT ck_reporte_evaluacion_carisma_verbal CHECK (carisma_verbal IS NULL OR carisma_verbal BETWEEN 1 AND 5),
    CONSTRAINT ck_reporte_evaluacion_lenguaje_adecuado CHECK (lenguaje_adecuado IS NULL OR lenguaje_adecuado BETWEEN 1 AND 5),
    CONSTRAINT ck_reporte_evaluacion_ritmo_habla CHECK (ritmo_habla IS NULL OR ritmo_habla >= 0),
    CONSTRAINT ck_reporte_evaluacion_tiempo_pausas CHECK (tiempo_pausas IS NULL OR tiempo_pausas BETWEEN 0 AND 100),
    CONSTRAINT ck_reporte_evaluacion_tasa_vacilacion CHECK (tasa_vacilacion IS NULL OR tasa_vacilacion BETWEEN 0 AND 100),
    CONSTRAINT ck_reporte_evaluacion_entusiasmo CHECK (entusiasmo IS NULL),
    CONSTRAINT ck_reporte_evaluacion_voz_completa CHECK (
        (ritmo_habla IS NULL) = (tiempo_pausas IS NULL) AND (ritmo_habla IS NULL) = (tasa_vacilacion IS NULL))
);

-- 5) Metodo STAR: una fila por pregunta conductual del reporte.
CREATE TABLE microentrevista.reporte_star (
    sesion_id    uuid      NOT NULL CONSTRAINT fk_reporte_star_reporte REFERENCES microentrevista.reporte_evaluacion (sesion_id) ON DELETE CASCADE,
    pregunta_id  uuid      NOT NULL CONSTRAINT fk_reporte_star_pregunta REFERENCES microentrevista.pregunta (id) ON DELETE CASCADE,
    situacion    boolean   NULL,
    tarea        boolean   NULL,
    accion       boolean   NULL,
    resultado    boolean   NULL,
    nivel        smallint  NULL,
    CONSTRAINT pk_reporte_star PRIMARY KEY (sesion_id, pregunta_id),
    CONSTRAINT ck_reporte_star_completo CHECK (
        (situacion IS NULL AND tarea IS NULL AND accion IS NULL AND resultado IS NULL AND nivel IS NULL)
        OR (situacion IS NOT NULL AND tarea IS NOT NULL AND accion IS NOT NULL AND resultado IS NOT NULL AND nivel IS NOT NULL)),
    CONSTRAINT ck_reporte_star_nivel CHECK (nivel IS NULL OR nivel =
        (situacion::int + tarea::int + accion::int + resultado::int))
);
CREATE INDEX ix_reporte_star_pregunta_id ON microentrevista.reporte_star (pregunta_id);
```

Más los `COMMENT ON` de cada tabla y columna en español, como en `V10`. Notas:

- **Inmutabilidad:** dos disparadores `BEFORE UPDATE` (`trg_reporte_evaluacion_inmutable` y `trg_reporte_star_inmutable`)
  rechazan toda modificación, con el mismo estilo que `trg_intento_append_only`
  (`RAISE EXCEPTION ... USING ERRCODE = 'P0001'`). El borrado solo ocurre en cascada desde la sesión.
- **Respuestas anteriores a `V11`:** quedan con las 4 columnas nulas (`ADD COLUMN ... NULL` sin valor por defecto, que no
  dispara `trg_intento_append_only`). No cuentan para las métricas de voz (CA-6.1.7).
- **Rangos:** `voz_tramo_ms` y `voz_pausas_ms` caben en `integer`: el audio dura como máximo 181 s
  (`AudioAnswerPolicy.MAX_DURATION`) = 181.000 ms. `numeric(3,2)` admite hasta 9,99; `numeric(4,1)` hasta 999,9.
- **Prueba de largos:** `ColumnLengthConsistencyTest` no aplica (no hay `varchar` nuevo).

## 6. Contrato

### 6.1 `200` — reporte emitido

```json
{
  "sessionId": "3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10",
  "issuedAt": "2026-11-17T15:04:05Z",
  "sessionLanguage": "es-CO",
  "interviewDate": "2026-11-17T14:58:40Z",
  "tone": "PROFESIONAL_NEUTRO",
  "metrics": [
    {"code": "PRECISION_CLARITY",  "group": "CONTENT", "value": 3.46, "unit": "SCORE_1_5"},
    {"code": "VERBAL_CHARISMA",    "group": "CONTENT", "value": 4.00, "unit": "SCORE_1_5"},
    {"code": "INCLUSIVE_LANGUAGE", "group": "CONTENT", "value": 5.00, "unit": "SCORE_1_5"},
    {"code": "SPEECH_RATE",        "group": "VOICE",   "value": 120,  "unit": "WORDS_PER_MINUTE"},
    {"code": "PAUSE_TIME",         "group": "VOICE",   "value": 12.0, "unit": "PERCENT"},
    {"code": "HESITATION_RATE",    "group": "VOICE",   "value": 2.0,  "unit": "PER_100_WORDS"},
    {"code": "ENTHUSIASM",         "group": "VOICE",   "value": null, "unit": null}
  ],
  "star": [
    {"questionNumber": 3, "questionText": "Cuéntame de una vez que resolviste un conflicto en equipo.",
     "situation": true, "task": true, "action": true, "result": false, "level": 3}
  ],
  "questions": [
    {"number": 2, "type": "TECNICA",
     "questionText": "¿Qué diferencia hay entre una transacción optimista y una pesimista en una base de datos?",
     "answerText": "Eh, pues las dos sirven para lo mismo, como para que no se dañen los datos.",
     "answerOrigin": "VOICE",
     "voice": {"words": 56, "fillers": 4, "speechRate": 110, "pauseTime": 28.3, "hesitationRate": 7.1}},
    {"number": 3, "type": "CONDUCTUAL",
     "questionText": "Cuéntame de una vez que resolviste un conflicto en equipo.",
     "answerText": "En el proyecto final de redes, dos compañeros querían usar tecnologías distintas…",
     "answerOrigin": "TEXT", "voice": null}
  ]
}
```

- Las 7 métricas vienen siempre, en ese orden. `value: null` = «sin resultado». `unit` es fijo por métrica y no depende
  del valor; `ENTHUSIASM` lleva siempre `unit: null`. [I: el libro solo muestra `null` en Entusiasmo]
- Los números salen con la escala guardada: 2 decimales en contenido (`4.00`), 1 en pausas y vacilación (`12.0`) y
  entero en ritmo. Frontend los formatea.
- `star`: un elemento por pregunta conductual, en orden de `questionNumber`; lista vacía si no hay. Sin resultado = los
  4 componentes y `level` en `null`.
- `sessionLanguage`: `es-CO` o `en`. `issuedAt` e `interviewDate`: UTC con `Z`, a segundos.
- `questions`: siempre las 5 preguntas (el ejemplo muestra 2). El STAR de una pregunta conductual sigue en `star` y se
  une por `questionNumber` = `number`. El ejemplo de `voice` sale del audio 02 de la medición:
  `(56, 30473, 8623, 4)` → 110 ppm, 28,3 %, 7,1.

### 6.2 Respuestas de error y 202

| Caso | HTTP | `code` | Mensaje (`detail`; `message` en el 202) | Excepción |
|---|---|---|---|---|
| Otra petición lo está generando | 202 | `REPORT_GENERATING` | Tu reporte se está generando. | ninguna: resultado `Generating` del caso de uso (DT-7) |
| Sesión abandonada | 409 | `SESSION_ABANDONED` (existe) | Sesión abandonada: no genera evaluación. | `SessionAbandonedException` |
| Configurada, en curso o evaluando | 409 | `SESSION_NOT_FINISHED` (nuevo) | Tu entrevista aún no ha terminado. | `SessionNotFinishedException` (nueva) |
| Ajena, inexistente o identificador mal escrito | 404 | `SESSION_NOT_FOUND` (existe) | No encontramos lo que buscabas. | `SessionNotFoundException` |
| Base de datos caída | 503 | `DATABASE_UNAVAILABLE` (existe) | Ocurrió un error. Inténtalo de nuevo. | `DatabaseUnavailableException` |
| Sin identidad | 401 | `IDENTITY_REQUIRED` (existe) | Tu sesión expiró. Inicia sesión de nuevo. | `IdentityRequiredException` |
| Método distinto de GET | 405 | `METHOD_NOT_ALLOWED` (existe) | Método no permitido. | del marco |
| `Accept` no JSON | 406 | `MEDIA_TYPE_NOT_ACCEPTABLE` (existe) | Tipo de respuesta no admitido. | del marco |

Los errores siguen el formato común del servicio: `ProblemDetail` con `application/problem+json;charset=UTF-8`, `title`,
`status`, `detail`, `code` y `requestId` (ADR 0001). Ver el hallazgo H-1.

Los fallos de la IA (`LLM_UNAVAILABLE`, `LLM_LIMIT_REACHED`, `LLM_INVALID_OUTPUT`) nunca llegan al cliente en esta ruta:
el reporte se emite con los nulos de REQ-GEN-06.

**Ruta al 500 genérico:** ninguna previsible. Se cubren el identificador mal escrito (404), las restricciones de `V11`
(solo las viola un defecto de programación; la única que puede chocar de verdad, `pk_reporte_evaluacion`, se maneja en
REQ-GEN-13), la base caída (503) y el reloj (inyectado).

## 7. Diseño (clases)

| Capa | Clase | Nueva o cambia | Responsabilidad |
|---|---|---|---|
| domain.model | `TimedWord(String text, long startMs, long endMs)` | nueva | Palabra con tiempos, solo en memoria |
| domain.model | `Transcription` | cambia | Campo `List<TimedWord> words` (vacío en los constructores existentes); `toString` sin las palabras |
| domain.model | `VoiceSample(int words, int spanMs, int pauseMs, int fillers)` | nueva | Los 4 datos; `static Optional<VoiceSample> from(List<TimedWord>, InterviewLanguage)` |
| domain.policy | `FillerWords` | nueva | `static boolean isFiller(String token, InterviewLanguage)`, normalización y listas |
| domain.model | `AnswerAttempt` | cambia | Campo `VoiceSample voice` (nulo para texto) y fábrica `spoken(..., VoiceSample voice, Clock)` |
| domain.model | `VoiceMetrics(Integer speechRate, BigDecimal pauseTime, BigDecimal hesitationRate)` | nueva | `static VoiceMetrics of(List<VoiceSample>)` con REQ-GEN-07 |
| domain.model | `ContentScores` | nueva | `static ContentScores average(List<ContentGrade>)` con REQ-GEN-09 |
| domain.model | `ContentGrade(int number, int precision, int charisma, int language)` | nueva | Nota de una respuesta (1–5) |
| domain.model | `StarResult(int number, String questionText, Boolean situation, Boolean task, Boolean action, Boolean result)` | nueva | `Integer level()`; `static StarResult unavailable(int, String)` |
| domain.model | `EvaluationReport` | nueva | Agregado: sesión, idioma, `ContentScores`, `VoiceMetrics`, `List<StarResult>`, `issuedAt`; `enthusiasm()` siempre `null` |
| domain.model | `ReportQuestion(int number, QuestionType type, String questionText, String answerText, AnswerOrigin origin, VoiceSample voice)` | nueva | Entrada del reporte y fila de `questions` |
| domain.model | `AiOperation` | cambia | `REPORT("GENERAR_REPORTE")` |
| domain.model | `AiExecution` | cambia | `static reportStarted(UUID sessionId, int attempt, String model, String provider, Clock clock)` |
| domain.exception | `SessionNotFinishedException`, `ErrorCode.SESSION_NOT_FINISHED`, `ErrorCode.REPORT_GENERATING` | nuevas | — |
| domain.port | `EvaluationReportRepository` | nueva | `Optional<EvaluationReport> findBySession(UUID)`, `EvaluationReport saveIssued(EvaluationReport)` |
| domain.port | `AnswerAttemptRepository` | cambia | `List<ReportQuestion> reportInputsOf(UUID sessionId)` |
| domain.port | `AiExecutionRepository` | cambia | `Optional<AiExecution> findReportInProgress(UUID sessionId)` |
| application.service | `ReportPromptBuilder`, `ReportOutputReader` | nuevas | Prompt `reporte.v1` y lector (REQ-GEN-03) |
| application.service | `ReportGenerator` | nueva | Reserva, ciclo de IA, armado y guardado (REQ-GEN-01 a 13) |
| application.service | `GetEvaluationReportService` | nueva | Dueño, estado, reporte existente o generación; devuelve `ReportOutcome` |
| application.service | `ReportOutcome` | nueva | `sealed`: `Issued(EvaluationReport)` o `Generating` |
| application.service | `AnswerRegistrar.startEvaluation` | cambia | Recibe `AcceptedSpeech(String text, String detectedLanguage, VoiceSample voice)` en lugar de `text` y `detectedLanguage` |
| infrastructure | `AssemblyAiTranscriptionAdapter` | cambia | `disfluencies: true` y `words` (REQ-VOZ-01, REQ-VOZ-02) |
| infrastructure | `GeminiLanguageModelAdapter`, `GroqLanguageModelAdapter` | cambian | `operation == EVALUATE ? evaluation : planning` (DT-2) |
| infrastructure | `ReportProperties` (`cameia.report.generation-claim-ttl`) | nueva | `@ConfigurationProperties` + `@Validated`, > 61,5 s |
| infrastructure.persistence | `EvaluationReportEntity`, `StarResultEntity`, `EvaluationReportJpaRepository`, `EvaluationReportRepositoryAdapter` | nuevas | Persistencia |
| infrastructure.persistence | `AnswerAttemptEntity`, `AnswerAttemptRepositoryAdapter`, `AiExecutionRepositoryAdapter`, `ConstraintNames` | cambian | Columnas de voz, consulta del reporte, marca, `PK_REPORTE_EVALUACION` |
| presentation | `EvaluationReportController`, `EvaluationReportResponse`, `ReportMetricResponse`, `StarResponse`, `ReportQuestionResponse`, `AnswerVoiceResponse`, `ReportGeneratingResponse` | nuevas | Endpoint y DTO |
| presentation | `BusinessExceptionHandler.DEFINITIONS` | cambia | `SESSION_NOT_FINISHED` → 409 «Entrevista sin terminar» |

## 8. Casos borde (estándar §C), uno por uno

| Caso | Dato literal | Resultado | Prueba |
|---|---|---|---|
| Nulo / vacío | `words` ausente | 4 datos nulos, respuesta registrada | `VoiceSampleTest.noWords_shouldGiveNoSample` |
| Límite inferior | 1 palabra `{"text":"Sí","start":100,"end":400}` | palabras 1, tramo 300, pausas 0, muletillas 0 | `VoiceSampleTest.oneTimedWord_shouldKeepItsSpan` |
| Tramo 0 | 1 palabra `start=500,end=500` | nulos | `VoiceSampleTest.zeroSpan_shouldGiveNoSample` |
| Pausa en el límite | silencios de 499, 500 y 501 ms | cuentan 500 y 501 → 1001 ms | `VoiceSampleTest.pauseThreshold_shouldCountFrom500` |
| Solapamiento | `start` de la siguiente < `end` de la anterior | silencio 0, no cuenta | `VoiceSampleTest.overlappingWords_shouldNotAddPause` |
| Palabra sin tiempos | `{"text":"eh"}` sin `start` | se descarta; no cuenta como palabra ni como muletilla | `AssemblyAiTranscriptionAdapterTest.words_shouldSkipEntriesWithoutTimes` |
| Unicode | `«ÉH,»`, `«Mm-hmm.»`, `«ahí»` | cuenta, no cuenta, no cuenta | `FillerWordsTest` (tabla) |
| Idioma | `«um»` en una sesión `es-CO` | no cuenta | `FillerWordsTest` |
| División entre cero | sesión sin datos de voz | 3 métricas de voz nulas | `VoiceMetricsTest.noVoiceAnswers_shouldGiveNullMetrics` |
| Ejemplo del libro | 120+180 palabras, 60000+90000 ms, 6000+12000 ms, 2+4 | 120 ppm, 12,0 %, 2,0 | `VoiceMetricsTest.bookExample_...` |
| Cifras reales (español) | 5 respuestas = 227 palabras, 109.197 ms, 28.523 ms, 17 muletillas | 125 ppm, 26,1 %, 7,5 | `VoiceMetricsTest.measuredSpanishSession_shouldMatchTheMeasurement` |
| Cifras reales (inglés) | 202, 105.434, 30.429, 9 | 115 ppm, 28,9 %, 4,5 | `VoiceMetricsTest.measuredEnglishSession_shouldMatchTheMeasurement` |
| Cifras reales (un audio) | audio 03: 65 palabras, 36.310 ms, 15.734 ms, 6 | 107 ppm, 43,3 %, 9,2 | `VoiceMetricsTest.measuredAudio03_shouldMatch` |
| Redondeo | 1 palabra en 7 palabras de muletilla → 14,2857 | 14,3 | `VoiceMetricsTest` (audio 04 y 09: 7 palabras, 1 muletilla → 14,3) |
| Respuestas anteriores a V11 | 2 por voz sin datos + 1 con datos | solo cuenta la que tiene datos | `VoiceMetricsTest.answersWithoutVoiceData_shouldNotCount` |
| Mezcla texto y voz | 3 por texto, 2 por voz | voz solo con las 2 | `EvaluationReportEndToEndTest.mixedSession_shouldUseOnlyVoiceAnswers` |
| Duplicado | dos consultas a la vez | 1 llamada, un 200 y un 202 | `EvaluationReportConcurrencyTest.twoReadsAtOnce_...` |
| Idempotencia | 3 lecturas seguidas | 3 cuerpos idénticos, 1 llamada | `EvaluationReportEndToEndTest.secondRead_...` |
| Falla a medias | IA bien, base cae al guardar | 503; marca cerrada `FALLIDA` si se puede; la siguiente consulta genera | `ReportGeneratorTest.saveFails_shouldCloseTheMarkAndThrowDatabaseUnavailable` |
| Dependencia caída | Gemini 503, 503, Groq 503 | reporte con contenido y STAR nulos | `EvaluationReportEndToEndTest.threeFailedAttempts_...` |
| Dependencia lenta | intento de más de 20 s | cuenta como fallo (`LLM_UNAVAILABLE`) y pasa al siguiente | cubierto por `GeminiLanguageModelAdapterTimeoutTest` (existente) + `ReportGeneratorTest` con fallo |
| Marca vencida | marca con `fecha_inicio` = ahora − 120 s | se retoma: 200 | `EvaluationReportConcurrencyTest.staleClaim_shouldBeTakenOver` |
| Marca viva | ahora − 119 s | 202 | `EvaluationReportConcurrencyTest.liveClaim_shouldAnswer202` |
| Recurso ajeno | sesión de `uid-otro` | 404 | `EvaluationReportEndToEndTest.foreignMissingAndMalformed_...` |
| Inexistente | UUID aleatorio | 404 | idem |
| Identificador mal escrito | `no-es-uuid` | 404 | idem |
| Cada estado no finalizado | `CONFIGURADA`, `EN_CURSO`, `EVALUANDO` → 409 `SESSION_NOT_FINISHED`; `ABANDONADA` → 409 `SESSION_ABANDONED` | sin llamada a la IA | `GetEvaluationReportServiceTest.notFinished_shouldNotCallTheAi` (parametrizada) |
| Salida inválida | número repetido, número de más, falta uno, nota 0, nota 6, nota «4» (texto), `true` en nota, campo extra, clave raíz extra, JSON roto, `star` con un número no conductual | `LLM_INVALID_OUTPUT` | `ReportOutputReaderTest` (tabla) |
| Solo conductuales | 5 preguntas `CONDUCTUAL` (construidas en el dominio) | contenido nulo, STAR presente | `ReportGeneratorTest.onlyBehavioralQuestions_...` |
| Carga grande | 5 respuestas de 4.000 caracteres | prompt < tope del modelo; salida ≤ 1024 tokens | `ReportPromptBuilderTest.fiveLongAnswers_shouldBuildThePrompt` |
| Entrada de ataque | respuesta con `</respuesta_del_candidato>` e instrucciones | el texto va dentro de sus etiquetas, sin reemplazos en cadena (mismo `replaceInOnePass` que la evaluación); la salida igual se valida por esquema | `ReportPromptBuilderTest.answerWithClosingTag_shouldStayInsideItsBlock` |
| Concurrencia con la escritura | una consulta mientras se cierra la sesión | la sesión aún no está `FINALIZADA` → 409 | `GetEvaluationReportServiceTest` |
| Colección vacía | sin preguntas conductuales | `star: []` | `EvaluationReportControllerTest.noBehavioral_shouldReturnEmptyStar` |

No aplican: fechas de la persona y zonas horarias (solo `issuedAt` en UTC, del reloj inyectado); paginación (5
preguntas como máximo); campos de entrada (el endpoint no tiene cuerpo).

## 9. Seguridad, calidad y pruebas

**OWASP API Top 10 y ASVS 5.0.0 nivel 1** (matriz de `cameia-infra/docs/seguridad/matriz-asvs-nivel1.md`):

| Riesgo | Control | Prueba |
|---|---|---|
| API1 autorización por objeto (ASVS 8.2.2) | `findByIdAndOwner` con el `X-User-Id` del Gateway; ajena = inexistente = 404 | `EvaluationReportEndToEndTest.foreignMissingAndMalformed_...` |
| API2 autenticación | Identidad solo de los encabezados del Gateway (ADR 0002) | `IdentityHeaderTest` (existente) + Postman R-11 |
| API3 exposición de datos (ASVS 15.3.1) | Respuesta por DTO; las respuestas y sus datos de voz solo se devuelven a su dueño (mismo 404 para ajena); sin retroalimentación de la evaluación, sin salida del modelo, sin perfil ni oferta | `EvaluationReportControllerTest.ready_shouldMapTheContract` (compara el cuerpo completo) |
| API4 consumo de recursos | Una sola llamada a la IA por sesión para siempre; marca contra dobles; tope de 1024 tokens | `EvaluationReportConcurrencyTest` |
| API6 flujos sensibles | El reporte no se recalcula; nada lo dispara salvo la consulta del dueño | `EvaluationReportEndToEndTest.secondRead_...` |
| API10 consumo inseguro de APIs | Salida de la IA validada por esquema y por el lector antes de guardar | `ReportOutputReaderTest` |
| Privacidad en logs (ASVS 16.2.5) | Logs solo con `sessionId`, `executionId`, códigos y números | `ReportLogContentTest.generation_shouldNotLogQuestionOrAnswerText` |
| Encabezado de contenido (ASVS 4.1.1) | `application/json;charset=UTF-8` | `EvaluationReportEndToEndTest` |

**Atributos de calidad (anexo de restricciones y atributos de calidad, AC-0001 a AC-0006):**

| Atributo | Métrica | Cómo lo cumple | Prueba o evidencia |
|---|---|---|---|
| Fiabilidad (FIA) | Reporte emitido aunque la IA falle; ninguna ruta previsible al 500 | REQ-GEN-06, §6.2 | `threeFailedAttempts_...`, Newman R-04 |
| Desempeño (DES) | Peor caso de generación ≈ 61,5 s (< 120 s del Gateway); lectura de un reporte emitido < 200 ms | Ciclo con tope por intento; lectura por clave primaria | Medición real en las 4 llamadas: 1,3–2,6 s; Newman registra el tiempo de R-07 |
| Seguridad (SEG) | Sin acceso a reportes ajenos; sin texto de la persona en logs | Tabla OWASP | Pruebas de la tabla |
| Integridad (INT) | Un reporte por sesión; valores dentro de rango; inmutable | `pk_reporte_evaluacion`, `CHECK`, disparadores | `ReportPersistenceTest` |
| Mantenibilidad (MAN) | Reglas en el dominio sin Spring; ≥ 90 % de líneas y ramas en lo nuevo | §7 | JaCoCo |
| Interoperabilidad (IOP) | Contrato igual al del libro salvo H-1, publicado en OpenAPI | §6 | `EvaluationReportControllerTest`, OpenAPI |

**Pruebas por capa:** dominio (JUnit + AssertJ, parametrizadas): `FillerWordsTest`, `VoiceSampleTest`,
`VoiceMetricsTest`, `ContentScoresTest`, `StarResultTest`, `EvaluationReportTest`. Aplicación (Mockito y el
`ScriptedModel` de `AnswerFlowSupport`): `ReportPromptBuilderTest`, `ReportOutputReaderTest`, `ReportGeneratorTest`,
`GetEvaluationReportServiceTest`. Infraestructura (Testcontainers): `ReportSchemaMigrationTest`, `ReportPersistenceTest`,
`VoiceAnswerPersistenceTest` (ampliada), `AssemblyAiTranscriptionAdapterTest` (ampliada),
`GeminiLanguageModelAdapterTest` y `GroqLanguageModelAdapterTest` (ampliadas: `REPORT` usa el modelo largo),
`ReportPropertiesTest`. Presentación: `EvaluationReportControllerTest`, `EvaluationReportEndToEndTest`,
`EvaluationReportConcurrencyTest`, `ReportLogContentTest`, `VoiceAnswerEndToEndTest` (ampliada). Arquitectura:
`LayeredArchitectureTest`, `ErrorCodeDocumentationTest` y `ErrorCodeTest` siguen en verde.

**Prueba manual en vivo (Terminado de Jira):** ya hecha en la medición con el borrador del prompt y del esquema
(`medicion/llamada_reporte.py`): 3 llamadas válidas a Gemini y 1 a Groq. Se repite una vez con `prompts/reporte.v1.txt`
final al terminar el commit 2, con el mismo script apuntando al archivo del repo.

**Cobertura:** ≥ 90 % de líneas y ramas en cada clase nueva o modificada, medida con
`./mvnw.cmd -B clean verify` → `target/site/jacoco/jacoco.csv`.

**Postman (carpeta `Reporte de Evaluación`)** — R-01 sesión por texto terminada → 200 con el cuerpo completo; R-02
sesión por voz terminada → métricas de voz con valor; R-03 consulta con la marca viva (el simulador demora la IA 5 s y
una segunda petición sale a la vez) → 202; R-04 simulador con 503, 503 y 503 → 200 con contenido nulo; R-05 sesión
abandonada → 409; R-06 sesión en curso → 409 `SESSION_NOT_FINISHED`; R-07 segunda lectura → mismo cuerpo, sin llamada
nueva (`GET /__calls`); R-08 sesión ajena, R-09 UUID inexistente y R-10 `no-es-uuid` → 404 idéntico; R-11 sin
`X-User-Id` → 401; R-12 `POST` → 405; R-13 `Accept: application/xml` → 406.

## 10. Dependencias

- **CM-379 (Frontend, Sofía):** consume el contrato; trabaja con respuestas simuladas mientras tanto. Aviso del hallazgo
  H-1 antes del 17-nov a las 12:00 m.
- **AssemblyAI y Gemini/Groq:** llaves existentes; sin variables nuevas obligatorias. Variable nueva opcional:
  `REPORT_GENERATION_CLAIM_TTL` (por defecto `2m`), que va a `.env.example`. Sin tarea de DevOps [H: CM-378,
  «Sin tarea de DevOps»].
- **Sin dependencia de otra CM sin fusionar:** un solo PR contra `develop`.

## 11. Integración en el repo

- Migración `V11__reporte_de_evaluacion.sql` (V10 es la última en `0eec549`).
- `ErrorCode`: agrega `SESSION_NOT_FINISHED` y `REPORT_GENERATING` al final, con su Javadoc;
  `ErrorCodeDocumentationTest` exige su fila en `docs/errores.md`.
- `docs/errores.md`: 2 filas nuevas y el endpoint nuevo en `SESSION_NOT_FOUND`, `SESSION_ABANDONED`,
  `DATABASE_UNAVAILABLE` e `IDENTITY_REQUIRED`.
- Colección: carpeta nueva al final; `simulador-gemini.py` reconoce el prompt del reporte (contiene `<entrevista>`) y
  responde un reporte válido para los números que trae; escenarios `report_valid`, `report_slow` (5 s) y `report_invalid`.
- Commits (rama `CM-380-reporte-de-evaluacion` desde `develop`), en orden de lectura:
  1. `CM-380 | feat(voz): guardar palabras, tramo, pausas y muletillas de cada respuesta por voz`
  2. `CM-380 | feat(reporte): generar el Reporte de Evaluación con la IA, reintentos y marca de generación`
  3. `CM-380 | feat(reporte): consultar el Reporte de Evaluación con sus errores, OpenAPI y Postman`
  4. `CM-380 | docs(reporte): decisión escrita del Reporte de Evaluación (ADR 0018) y documentación`
  Cada commit compila y pasa las pruebas del módulo tocado.

## 12. Hallazgos

| # | Hallazgo | Clase | Destino |
|---|---|---|---|
| H-1 | El libro dice «Errores en el formato común (code, message, requestId)», pero el formato común de Entrevista es `ProblemDetail` con `detail` (no `message`), `title`, `status`, `code` y `requestId` [H: `docs/errores.md` §Formato, `BusinessExceptionHandler`]. El 202 sí lleva `message` (DT-7). El `errorMap.ts` de `cameia-web` ya lee los dos formatos [H: `src/services/http/errorMap.ts` en `origin/develop`] | Cambio sin alternativa (el formato común ya está publicado) + **acción para Frontend** | Aviso a Sofía (CM-379): las respuestas simuladas de 404, 409 y 503 deben usar la forma `ProblemDetail`. Texto listo en `comunicaciones/` antes de implementar; Paula decide si lo envía |
| H-2 | Con la opción de muletillas, el texto guardado en voz y el historial muestran «Eh,», «um» [D: DP-2]. Además, la voz reconocida (`speechDuration`, mínimo de 1,5 s de CM-377) suma lo que duran las muletillas | Efecto aceptado | ADR 0018 lo documenta |
| H-3 | La columna `feedback_turno.feedback_star` existe y no se usa; HU-6.3 confirma que queda nula | Sin cambio | — |
| H-4 | `LLM_MODEL` del equipo de Paula vale `gemini-3.8-flash` y el despliegue `gemini-3.5-flash-lite` | Verificación | La medición usó el del despliegue; sin cambio de código |
| H-5 | Los adaptadores eligen el modelo con `operation == PLAN ? planning : evaluation`: una operación nueva caería en el modelo de 3 s (8 s en el despliegue) | Corrección de código dentro de la tarea | Commit 2 (DT-2), con prueba |
| H-7 | El prompt de la evaluación por respuesta (`prompts/evaluacion.v1.txt`) no recibe el idioma de la sesión: en una sesión en inglés su `feedback` sale en español (comprobado el 16-nov con Gemini). Hoy no se muestra a nadie | Hallazgo fuera de esta tarea | Pendiente con destino: la historia del reporte por plan (HU-6.6) o del Modo Entreno (HU-5.11), que son las que lo mostrarían |
| H-8 | `DATABASE_UNAVAILABLE` (503, base de datos caída) responde con el mensaje genérico «Ocurrió un error. Inténtalo de nuevo.» en todos los endpoints de Entrevista [H: `docs/errores.md` en `origin/develop`], aunque es un fallo previsible de una dependencia [D: DP-13] | Hallazgo fuera de esta tarea | Pendiente con destino: tarea aparte de Backend que cambia el mensaje en todo el servicio y avisa a Frontend; la propone Paula al equipo |
| H-6 | `ck_ejecucion_ia_turno` exige `turno_id` en toda operación que no sea `PLANIFICAR`: rechazaría la marca del reporte | Corrección dentro de la tarea | `V11` §5.2, con prueba |

## 13. Lista de verificación de la CM

| Fila | Cómo se demuestra | Esperado |
|---|---|---|
| Cada CA de §3 | La prueba nombrada | Verde |
| R1 idioma | Revisión del diff | Identificadores en inglés; Javadoc, logs y mensajes en español |
| R2 validación | `ReportOutputReaderTest`, `SessionCommandTest` (existente) | Cada causa con su código |
| R3 errores | §6.2 + petición real a cada ruta | Ningún previsible en 500 |
| R4 base de datos | `ReportSchemaMigrationTest`: cada `CHECK`, índice, disparador y FK con una inserción que pasa y otra que falla | Verde |
| R5 casos borde | §8 | Cada fila con su prueba |
| R6 seguridad | §9 OWASP | Verde |
| R7 código lean | Revisión: reutiliza `AiCallRunner`, `AttemptSwitch`, `AiRetryPolicy`, `SessionCommand`, `findOwned`, `replaceInOnePass` | Sin duplicados |
| R8 documentación | OpenAPI con ejemplos; Javadoc; ADR 0018; `docs/errores.md` | Completo |
| R9 pruebas por capa | §9 | Verde |
| R10 cobertura | JaCoCo por clase tocada | ≥ 90 % líneas y ramas |
| R11 Postman y Newman | `newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json` | 0 fallos |
| R12 exactitud con los CA | Mensajes literales comparados en las pruebas | Iguales al libro |
| R13 atributos | §9 | Cada métrica con evidencia |
| R14 sin inventar | Salidas reales en `ejecucion/evidencia/CM-380/` | — |
| Adversarial: dos a la vez | `EvaluationReportConcurrencyTest` | 1 llamada, 200 y 202 |
| Adversarial: ajeno | Postman R-08 | 404 |
| Adversarial: datos hostiles | `answerWithClosingTag_...` | La salida se valida igual |
| Adversarial: falla a medias | `saveFails_...` | 503 y marca cerrada |
| Sin lista de palabras | `ReportSchemaMigrationTest.answerTable_shouldOnlyAddFourIntegerVoiceColumns` y `VoiceAnswerEndToEndTest.answeringByVoice_shouldStoreVoiceDataAndNeverTheWords` | Solo 4 enteros |
| Logs sin texto | `ReportLogContentTest` | Ningún fragmento de pregunta ni respuesta |
| `tipo_operacion` nuevo | `ReportSchemaMigrationTest.aiExecution_shouldAcceptReportWithoutTurn` | Acepta `GENERAR_REPORTE` sin turno; rechaza con turno y valores desconocidos |
| Línea base | `./mvnw.cmd -B -ntp clean verify` sobre `0eec549` antes de tocar nada | Verde (se ejecuta al empezar el 17-nov; si falla, hallazgo con destino) |

## 14. Puerta de listo

`LISTA PARA EJECUTAR`:

- Sin preguntas abiertas: DP-1 a DP-13 respondidas; P-1 a P-4 se aprueban con la spec.
- Cada CA tiene requisito, prueba y petición (o la razón de no tenerla).
- Contraste con `origin/develop` `0eec549` hecho: clases, firmas, migraciones y restricciones citadas existen como se
  dice (§12, H-5 y H-6 salen de ese contraste).
- La lista de verificación existe (§13).

Aprobada por Paula Andrea Muñoz Delgado el 16-nov-2026 (noche).
