# CM-354 · Audio de la respuesta sin persistencia (HT-06)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-354 «HT-06 · Audio de respuesta sin persistencia». Tipo Tarea, sin tarea padre ni enlaces, Sprint 5 (cierra el 18-nov-2026, 11:30 p. m.), 3 puntos. Responsable: Paula Andrea Muñoz Delgado. «En curso» desde el 14-nov-2026. Sin comentarios |
| Historias | HU-5.8 (grabar y enviar la respuesta por voz) y HU-5.12 (transcribir la respuesta por voz), backlog v11.3 |
| Repositorio | `cameia-entrevista`. Base `origin/develop` en `0470493` |
| Rama | `CM-354-audio-sin-persistencia`, desde `origin/develop`. Un PR hacia `develop`; no depende de ninguna CM sin fusionar |
| Estado | **LISTA PARA EJECUTAR**, pendiente de la aprobación de Paula |

## 1. Objetivo

HT-06 pide que el audio de una respuesta por voz viva solo en la memoria de la petición y se descarte al terminar, con éxito o con error.
El código de `origin/develop` ya hace eso. Lo que falta es lo que el backlog llama «Terminado»:

> «una prueba en develop confirma que, tras procesar respuestas por voz válidas y fallidas, no queda ningún archivo de audio en CAMEIA
> (disco, bucket ni log)». (Backlog v11.3, hoja Backlog, fila 36; descripción de CM-354 en Jira.)

Esta CM agrega esas pruebas y deja dos guardias para que un cambio futuro no pueda escribir el audio sin que una prueba falle:

- una prueba de la configuración del multipart;
- una prueba de arquitectura.

No cambia el contrato de la API, el esquema de la base ni ningún mensaje.

Además lleva un commit de documentación con dos cosas:

- la renumeración de los ADR y de las bitácoras de IA que quedaron con números repetidos al fusionar la voz;
- la corrección del tiempo de evaluación en el ADR 0014.

Paula lo decidió el 14-nov-2026 (§8).

## 2. Hechos confirmados en `origin/develop` (`0470493`)

| # | Hecho | Fuente |
|---|---|---|
| H1 | Las partes del multipart viven en memoria: tamaño máximo de archivo y de petición 12 MB, y umbral de escritura en disco de 13 MB. Como el umbral es mayor que el máximo, Tomcat nunca escribe una parte en disco | `src/main/resources/application.properties:44-46` |
| H2 | El controlador lee el audio con `MultipartFile.getBytes()`. No usa `transferTo`, el nombre del archivo ni el tipo declarado | `presentation/controller/AudioAnswerController.java:155-175` |
| H3 | `SubmitAudioAnswerService.submit` sobrescribe con ceros el arreglo del audio en un `finally`, con éxito o con error. Todos los rechazos de forma, estado, tamaño y contenido ocurren dentro de ese `try` | `application/service/SubmitAudioAnswerService.java:175-184` |
| H4 | `AudioAnswer` y `AudioAnswerCommand` comparten el mismo arreglo (no hay copia defensiva) y su `toString()` muestra solo el tamaño | `domain/model/AudioAnswer.java`, `application/command/AudioAnswerCommand.java` |
| H5 | No hay columna ni tabla para el audio | `db/migration/V9__respuesta_por_voz.sql:8` |
| H6 | En `src/main/java` no hay uso de `java.nio.file`, `java.io.File`, `FileOutputStream`, archivos temporales ni clientes de almacenamiento (bucket) | `git grep` del 14-nov sin resultados |
| H7 | El log del caso exitoso registra formato, bytes, duración y la pregunta siguiente; nunca el nombre del archivo ni el texto | `SubmitAudioAnswerService.java:228` |
| H8 | El adaptador de AssemblyAI sube el audio a `/v2/upload`, crea la transcripción y la consulta; registra el identificador de la transcripción (`transcriptId`), nunca el audio ni la URL de subida. No pide el borrado anticipado | `infrastructure/client/assemblyai/AssemblyAiTranscriptionAdapter.java:114-196` |
| H9 | Pruebas que ya existen: bytes en cero con éxito y con transcripción vacía (unitarias); sin columna `bytea`, bytes en cero y nombre del archivo ausente del log y de la respuesta en el caso exitoso (de punta a punta); sin ruta para recuperar el audio | `SubmitAudioAnswerServiceTest:236,248`; `AudioAnswerEndToEndTest:494,508,522` |
| H10 | **Lo que falta para HT-06:** ninguna prueba recorre las respuestas **fallidas** comprobando disco y log; ninguna prueba vigila que el umbral siga por encima del máximo; los rechazos previos a la transcripción no tienen prueba de los bytes en cero | Lectura de las pruebas del 14-nov |

## 3. Criterios de aceptación y requisitos

### 3.1 Criterios que cubre (columna Técnico con B)

| CA | Texto que obliga (literal) | Requisito |
|---|---|---|
| HT-06 Terminado | «tras procesar respuestas por voz válidas y fallidas, no queda ningún archivo de audio en CAMEIA (disco, bucket ni log)» | REQ-HT06-01 a 05 |
| CA-5.8.18 | «el audio no debe quedar en disco, en un bucket ni en el log, y no debe poder transcribirse ni recuperarse» | REQ-HT06-01 a 06 |
| CA-5.8.17 | «el sistema no debe entregarlo ni ejecutarlo» | REQ-HT06-06 (ya probado) |
| CA-5.8.16 | «el nombre no debe aparecer en el registro (log) de la petición ni en la respuesta» | REQ-HT06-02, ampliado a las respuestas fallidas |
| CA-5.12.4 | «descartar el audio al terminar la petición y no debe conservar ninguna copia en CAMEIA (la copia temporal de AssemblyAI es la informada en CA-5.8.24)» | REQ-HT06-03 |
| CA-5.8.13, 5.8.14, 5.8.15, 5.8.20, 5.8.21, 5.8.28 | rechazos «sin guardarlo ni pedir su transcripción» | Ya probados con `assertNothingRegistered`. Esta CM agrega disco, log y bytes en esos mismos caminos |

### 3.2 Requisitos (EARS)

- **REQ-HT06-01 (disco).** Cuando termina una petición `POST /api/v1/interviews/sessions/{sessionId}/answers/audio`, con cualquier estado de §3.3 que llegue al servicio, el servicio no debe haber dejado ningún archivo en el directorio de partes del multipart.
- **REQ-HT06-02 (log).** Cuando termina una petición de §3.3, la salida del registro de esa petición no debe contener:
  - el nombre del archivo recibido (`../../respuesta final.webm`);
  - el texto transcrito;
  - el Base64 ni el hexadecimal de los primeros 48 bytes del audio.
- **REQ-HT06-03 (memoria de CAMEIA).** Cuando `SubmitAudioAnswerService.submit` termina, con éxito o con cualquier excepción de §3.3, todos los bytes del arreglo del audio deben valer 0.
- **REQ-HT06-04 (base).** El esquema `microentrevista` no debe tener ninguna columna de tipo `bytea` ni `oid`.
- **REQ-HT06-05 (guardias).** Dos condiciones deben quedar vigiladas por pruebas:
  - si alguien baja el umbral de escritura en disco a un valor menor o igual que el tamaño máximo de archivo, una prueba debe fallar;
  - si una clase de `tech.cameia.entrevista` empieza a usar `java.nio.file.Files`, `java.io.FileOutputStream`, `java.io.RandomAccessFile` o `java.io.File#createTempFile`, una prueba de arquitectura debe fallar.
- **REQ-HT06-06 (sin ruta de recuperación).** El servicio no debe exponer ninguna ruta que devuelva un audio. Ya lo prueba `AudioAnswerEndToEndTest#submit_shouldNotExposeAudio` (405 y 404) y no se cambia.

### 3.3 Caminos que recorre la prueba de punta a punta (datos literales)

Cada caso usa el nombre de archivo `../../respuesta final.webm` y una entrevista configurada con `answerFormat` `AUDIO`, salvo el caso 9. El tipo declarado es `audio/webm` salvo que la fila diga otro.

| # | Caso | Datos | Estado y `code` esperados (los de hoy, no cambian) |
|---|---|---|---|
| 1 | Válido | `tono-3s.webm` | 200 |
| 2 | Más de 10 MiB | `paddedWebm(10_485_761)` | 413 `AUDIO_TOO_LARGE` |
| 3 | Más de 12 MiB (lo corta el contenedor) | **No se envía en esta prueba.** Con 12 MiB reales el contenedor cierra la conexión y la prueba sería inestable (Javadoc de `PayloadTooLargeTest`). El contenedor rechaza antes de leer la parte, así que no hay nada que escribir; el 413 `REQUEST_PAYLOAD_TOO_LARGE` ya lo prueba `PayloadTooLargeTest` | — |
| 4 | Formato no admitido | `tono-3s.mp3` declarado `audio/webm` | 415 `AUDIO_INVALID_FORMAT` |
| 5 | Contenido falso | `no-es-audio.webm` declarado `audio/webm` | 415 `AUDIO_INVALID_FORMAT` |
| 6 | Muy corto | `tono-1s.webm` | 422 `AUDIO_TOO_SHORT` |
| 7 | Transcripción vacía | `tono-3s.webm`; el transcriptor devuelve `"   "` | 422 `TRANSCRIPTION_EMPTY` |
| 8 | Transcripción caída | `tono-3s.webm`; el transcriptor lanza dos veces `transient503()` | 503 `TRANSCRIPTION_UNAVAILABLE` |
| 9 | Entrevista por texto | `tono-3s.webm`, entrevista con `TEXTO` | 422 `AUDIO_NOT_ALLOWED` |
| 10 | Entrevista ajena | `tono-3s.webm` con `X-User-Id` `uid-b` | 404 `SESSION_NOT_FOUND` |

Los códigos se copian de `docs/errores.md`.

- El backlog nombra `AUDIO_FORMAT_NOT_SUPPORTED`, `AUDIO_CONTENT_INVALID` y `ANSWER_MODE_MISMATCH`, pero el contrato publicado y la web (`cameia-web`, `src/features/interview-session/SPEC.md:792-794`) usan `AUDIO_INVALID_FORMAT` y `AUDIO_NOT_ALLOWED`.
- **Esta CM no cambia ningún código.** El backlog se corrige en la v11.4 (CM-370, fuera de esta spec; §9).

## 4. Validación, errores y base de datos

- **Validación por campo (R2):** no aplica. No hay campos nuevos y la validación del audio (`AudioAnswerPolicy`) no se toca.
- **Errores (R3):** no hay rutas nuevas. Cada caso de §3.3 ya tiene su código y su mensaje en `docs/errores.md`.
  - Ningún caso de §3.3 llega al 500 genérico. La prueba de punta a punta lo comprueba con una petición real por caso: afirma estado y `code`.
  - Ruta revisada: `AudioAnswerController.bytesOf` convierte un `IOException` de `getBytes()` en `UncheckedIOException`, que iría al 500. Con el umbral de H1 las partes están en memoria y `getBytes()` no lee disco, así que no es previsible. Se deja escrito, sin cambio.
- **Base de datos (R4):** sin migración. REQ-HT06-04 solo se comprueba.

## 5. Casos borde (R5)

| Categoría | Aplica | Cómo se cubre |
|---|---|---|
| Carga grande | Sí | Caso 2 de §3.3 (10 MiB + 1 byte). El caso 3 lo cubre `PayloadTooLargeTest` |
| `Content-Type` erróneo y contenido hostil | Sí | Casos 4 y 5 |
| Falla de una dependencia | Sí | Caso 8 (dos fallos transitorios) y caso 7 (respuesta vacía) |
| Falla a medias | Sí | El caso 8 comprueba también que no se guarda la respuesta (`answers()` en 0) |
| Recurso ajeno o sin identidad | Sí | Caso 10. Sin identidad ya lo prueba `submit_shouldRequireAudioAndIdentity` |
| Estado (entrevista cerrada) | Sí | Unitaria de REQ-HT06-03 con `ABANDONED`. La prueba de punta a punta de cerradas ya existe |
| Doble envío o concurrencia | No | Ya lo cubre `submit_shouldTranscribeOnceOnDoubleSubmit`. No cambia nada del descarte |
| Texto, números, fechas, enumerados, colecciones | No | No hay campos nuevos |

## 6. Seguridad (R6)

| Referencia | Aplica | Cómo se cumple | Prueba |
|---|---|---|---|
| ASVS 5.3.1 (un archivo subido no se ejecuta ni se sirve) | Sí | Sin ruta de recuperación y sin archivo en disco | `submit_shouldNotExposeAudio`, prueba nueva de §3.3 |
| ASVS 5.3.2 (rutas con datos confiables, no con el nombre recibido) | Sí | El nombre se ignora y no llega al log | Prueba nueva (REQ-HT06-02) |
| ASVS 5.2.1 y 5.2.2 (tamaño y tipo real) | Sí, sin cambios | Casos 2 a 5 | Prueba nueva y las existentes |
| OWASP API4 (consumo de recursos) | Sí, sin cambios | Límite de 12 MB del contenedor | `PayloadTooLargeTest` (existente) |
| OWASP API1 (autorización por objeto) | Sí, sin cambios | Caso 10 | Prueba nueva |
| SEG-04 (cero audio ni transcripciones en logs) | Sí | REQ-HT06-02 | Prueba nueva |

**Hallazgo para DevOps.** La matriz `cameia-infra/docs/seguridad/matriz-asvs-nivel1.md` marca 5.2.1, 5.2.2, 5.3.1 y 5.3.2 como «Sí (Perfil)» y `PENDIENTE` porque el CV no está implementado. En Entrevista ya se cumplen para el audio. Destino: el documento a DevOps del 14-nov. La matriz es de DevOps y no se edita.

## 7. Atributos de calidad (R13)

La épica HE-05 declara AC-0003 (seguridad) y AC-0004 (fiabilidad). Esta CM toca:

| Atributo | Métrica | Límite | Cómo lo cumple | Prueba |
|---|---|---|---|---|
| AC-0003 Seguridad | SEG-04 | Cero audio o transcripciones en logs | El log de los 9 caminos de §3.3 que se envían no contiene nombre, texto ni bytes | Prueba nueva de punta a punta |
| AC-0003 Seguridad | SEG-01 | ASVS N1 aplicable al 100 % | 5.2.1 a 5.3.2 con prueba (§6) | §6 |
| Restricción REST-0003 | Datos personales mínimos y transitorios (Ley 1581) | El audio no persiste en CAMEIA | Disco, base, log y bytes en cero | REQ-HT06-01 a 04 |
| AC-0004 Fiabilidad | FIA-01 | Todo fallo conserva el último estado | Los caminos fallidos no guardan respuesta | `answers()` en 0 en los casos 2 y 4 a 10 |
| MAN-01 | Cobertura de lo nuevo o modificado | ≥ 90 % de líneas y de ramas | Solo se agregan pruebas: no hay código de producción nuevo que medir. Se informa la cobertura de `SubmitAudioAnswerService` y `AudioAnswerController`, que no debe bajar | JaCoCo |
| Testabilidad | Toda prueba debe poder fallar | — | Ensayo de cada guardia en la tarjeta (T1 y T3) | Salida del ensayo |

## 8. Decisiones

| # | Decisión | Clase | Quién y cuándo | Alternativas descartadas |
|---|---|---|---|---|
| D-354-1 | El log sigue registrando el `transcriptId` de AssemblyAI. Sin la llave no sirve, y sirve para rastrear un fallo | Decisión humana | Paula, 14-nov-2026 | Quitarlo (cuesta rastrear fallos); enmascararlo (cambio de código sin beneficio claro) |
| D-354-2 | No se pide a AssemblyAI el borrado anticipado: conserva el archivo 2 días y la transcripción 30 días | Hecho ya decidido | Equipo, 28-oct (CA-5.8.24, D-13 de CM-349) | — |
| D-354-3 | La copia interna de la parte que guarda Tomcat en memoria no se sobrescribe; la libera el recolector de memoria. No es un archivo, no persiste después de la petición y el servicio no la controla | Inferencia justificada | Spec | Leer la parte por flujo y sobrescribir el búfer: imposible sin reemplazar el procesamiento de multipart del contenedor |
| D-354-4 | La renumeración de ADR y bitácoras va en un commit de documentación de este PR | Decisión humana | Paula, 14-nov-2026 | Un PR aparte (sin clave Jira); dejarla para después del 18-nov |
| D-354-5 | El ADR 0014 se corrige a 8 s de evaluación, que es lo desplegado (`LLM_EVALUATION_TIMEOUT=8000ms` en `despliegue-continuo.yml`). El valor por defecto del código (3 s) no se cambia aquí | Decisión humana | Paula, 14-nov-2026 (aprobación del plan) | Cambiar también el valor por defecto (mete código ajeno a esta tarea) |
| D-354-6 | Se reutiliza `AudioAnswerEndToEndTest` en lugar de una clase nueva | Inferencia justificada | Spec | Una clase nueva duplicaría el contexto de Spring, los dobles y los ayudantes (unas 150 líneas) |

## 9. Fuera de alcance y hallazgos con destino

| Hallazgo | Destino |
|---|---|
| El backlog nombra `AUDIO_FORMAT_NOT_SUPPORTED`, `AUDIO_CONTENT_INVALID` (CA-5.8.14 y 5.8.15) y `ANSWER_MODE_MISMATCH` (CA-5.8.20); el contrato publicado usa `AUDIO_INVALID_FORMAT` y `AUDIO_NOT_ALLOWED` | Backlog v11.4 (CM-370), tabla de cambios del 14-nov |
| La spec de CM-349 §7 conserva valores viejos (`TRANSCRIPTION_TIMEOUT 90s`) | Es un documento ya fusionado de otra CM: no se edita. Vale el `CLAUDE.md` del repo y la spec de CM-362 (60 s) |
| El valor por defecto `cameia.llm.evaluation-timeout=3000ms` (`application.properties:51`) y la prueba en vivo de Groq (`WithLanguageModelProperties:27`) siguen en 3 s, mientras el despliegue usa 8 s | `analisis/revisiones/14112026_prueba-real-groq-respaldo.md`, opción A; propuesta para CM-296 |
| Matriz ASVS 5.2.1 a 5.3.2 desactualizada para Entrevista | Documento a DevOps del 14-nov |

## 10. Renumeración de ADR y bitácoras (commit de documentación)

**Por qué.** Los ADR y las bitácoras de Paula se fusionaron el 12-nov; los de Vela, el 13-nov en `ebce618`. Se renumeran los de Vela, con `git mv` para conservar la historia.

| Archivo actual | Archivo nuevo | Título interno (línea 1) |
|---|---|---|
| `docs/adr/0012-respuesta-por-voz.md` | `docs/adr/0015-respuesta-por-voz.md` | `# ADR 0015 · …` |
| `docs/adr/0013-reintento-de-la-transcripcion.md` | `docs/adr/0016-reintento-de-la-transcripcion.md` | `# ADR 0016 · …` |
| `docs/bitacora-ia/11_respuesta-por-voz_prompt.md` | `docs/bitacora-ia/14_respuesta-por-voz_prompt.md` | — |
| `docs/bitacora-ia/12_validacion-audio-respuesta-por-voz_prompt.md` | `docs/bitacora-ia/15_validacion-audio-respuesta-por-voz_prompt.md` | — |
| `docs/bitacora-ia/13_integracion-respuesta-por-voz_prompt.md` | `docs/bitacora-ia/16_integracion-respuesta-por-voz_prompt.md` | — |
| `docs/bitacora-ia/14_reintento-transcripcion_prompt.md` | `docs/bitacora-ia/17_reintento-transcripcion_prompt.md` | — |

Referencias que cambian (de `git grep` del 14-nov sobre `origin/develop`):

| Archivo y línea | Antes | Después |
|---|---|---|
| `docs/adr/0016-reintento-de-la-transcripcion.md:12` | `(CM-345, ADR 0012).` | `(CM-345, ADR 0015).` |
| `docs/bitacora-ia/17_reintento-transcripcion_prompt.md:4` | `[ADR 0013](../adr/0013-reintento-de-la-transcripcion.md)` | `[ADR 0016](../adr/0016-reintento-de-la-transcripcion.md)` |
| `docs/entregas/CM-345-como-probar.md:56` | `docs/adr/0012-respuesta-por-voz.md` | `docs/adr/0015-respuesta-por-voz.md` |
| `docs/entregas/CM-345-como-probar.md:67` | `ADR 0012, bitácora 11` | `ADR 0015, bitácora 14` |
| `docs/entregas/CM-345-descripcion-pr.md:8` | `ADR 0012, bitácora IA 11` | `ADR 0015, bitácora IA 14` |
| `specs/CM-345-RespuestaPorVoz/tasks.md:12` | `ADR 0012, bitácora 11` | `ADR 0015, bitácora 14` |
| `specs/CM-349-TranscripcionRespuestaVoz/plan.md:24` | `docs/bitacora-ia/11_respuesta-por-voz_prompt.md` | `docs/bitacora-ia/14_respuesta-por-voz_prompt.md` |
| `specs/CM-362-ReintentoTranscripcionAudio/spec.md:7` | `bitácora 13 y 14` | `bitácora 16 y 17` |
| `specs/CM-362-ReintentoTranscripcionAudio/tasks.md:9` | `ADR 0013, bitácora 14` | `ADR 0016, bitácora 17` |
| `docs/bitacora-ia/README.md` | sin filas para las cuatro bitácoras de voz | una fila por cada una (14 a 17), con el mismo formato que las filas 11 a 13, en orden |

No cambian, porque se refieren a los ADR de Paula, que conservan su número: `CLAUDE.md:89` y `:91`, y `specs/CM-216-RegistrarRespuesta/spec.md:411`.

**ADR 0014** (`docs/adr/0014-reintentos-y-respaldo-de-la-ia.md`):

| Línea | Antes | Después |
|---|---|---|
| 31 | `Peor caso: evaluar ≈ 10,5 s (3 × 3 s + 1,5 s)` | `Peor caso: evaluar ≈ 25,5 s (3 × 8 s + 1,5 s)` |
| 44 | `La evaluación espera como máximo 3 s por intento (`LLM_EVALUATION_TIMEOUT`)` | `La evaluación espera como máximo 8 s por intento en los ambientes desplegados (`LLM_EVALUATION_TIMEOUT=8000ms`; el valor por defecto del servicio es 3 s)` |
| 52 | `hasta ≈ 10,5 s al evaluar` | `hasta ≈ 25,5 s al evaluar` |

El ADR se edita en su sitio: el documento sigue siendo independiente y no lleva «antes/ahora».

## 11. Integración y línea base

- **Otras CM del repo:** ninguna en curso. No hay migración, cambios en `ErrorCode`, `docs/errores.md` ni Postman.
- **Colección de Postman y Newman (R11):** no hay endpoint nuevo ni cambiado. Se corre la colección existente una vez sobre la punta, como prueba de no regresión.
- **Línea base, antes de tocar nada** (en el worktree, sobre `origin/develop`): `./mvnw.cmd -B clean verify`, con Docker en marcha (Testcontainers). Debe terminar con `BUILD SUCCESS` y 0 fallos. Si no, se detiene y se reporta.
- **Commits, en orden de lectura:**
  1. `CM-354 | test(audio): comprobar que no queda audio en disco ni en el log tras respuestas válidas y fallidas` (T1 y T2);
  2. `CM-354 | test(arquitectura): impedir el uso de archivos en el servicio` (T3);
  3. `CM-354 | docs(adr): renumerar los ADR y bitácoras de la voz y fijar 8 s de evaluación en el ADR 0014` (T4).
- **Diff estimado:** unas 180 líneas de prueba y unas 40 de documentación (más los renombres).

## 12. Lista de verificación de la CM

| # | Qué | Comando o prueba | Resultado esperado |
|---|---|---|---|
| V1 | Línea base | `./mvnw.cmd -B clean verify` en `origin/develop` | `BUILD SUCCESS` |
| V2 | REQ-HT06-01 a 04, los 9 caminos que se envían | `./mvnw.cmd -B test "-Dtest=AudioAnswerEndToEndTest"` | Verde; en el informe aparece `submit_shouldLeaveNoAudio_whenAnswersSucceedOrFail` |
| V3 | Guardia del umbral | Ensayo de T1: bajar a mano `file-size-threshold` a `0` en `application.properties` | `submit_shouldKeepMultipartPartsInMemory` falla; se revierte el cambio y vuelve a verde |
| V4 | REQ-HT06-03 en unitarias | `./mvnw.cmd -B test "-Dtest=SubmitAudioAnswerServiceTest"` | Verde; 4 casos nuevos |
| V5 | Guardia de arquitectura | Ensayo de T3: clase temporal que usa `Files.write` | `services_shouldNotWriteFiles` falla; se borra la clase y vuelve a verde |
| V6 | Ningún 500 en §3.3 | V2 afirma estado y `code` de cada caso | 0 casos con `INTERNAL_ERROR` |
| V7 | Documentación | `git grep -nE "0012-respuesta-por-voz|0013-reintento-de-la-transcripcion|1[1-4]_(respuesta-por-voz|validacion-audio|integracion-respuesta|reintento-transcripcion)"` | Sin resultados |
| V8 | Verificación final | `./mvnw.cmd -B clean verify` y Newman con la colección del repo, una vez sobre la punta | `BUILD SUCCESS`; Newman sin fallos |
| V9 | Cobertura | `target/site/jacoco/jacoco.csv` | `SubmitAudioAnswerService` y `AudioAnswerController` no bajan respecto de V1 |
| V10 | R1 a R14 | Tabla regla → evidencia en `ejecucion/evidencia/CM-354/resumen.md` | Cada fila con evidencia |

## 13. Preguntas

Respondidas: D-354-1 y D-354-4 (Paula, 14-nov). No quedan preguntas abiertas.
