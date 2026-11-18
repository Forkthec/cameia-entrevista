# Plan · CM-354 Audio de la respuesta sin persistencia

## Foco de revisión (lo que más probablemente salga mal)

| # | Riesgo | Prueba que lo vigila | Estado |
|---|---|---|---|
| F1 | Una prueba de «no hay archivos» que pasa siempre: Tomcat borra sus archivos temporales al terminar la petición, así que un directorio vacío **después** no prueba que el audio no se escribió **durante** la petición | `submit_shouldKeepMultipartPartsInMemory` (umbral mayor que el máximo), que sí falla al bajar el umbral (ensayo V3) | En el plan (T1) |
| F2 | Un log que contiene el audio en otra forma (Base64 o hexadecimal) y no se detecta | `submit_shouldLeaveNoAudio_whenAnswersSucceedOrFail` busca el Base64 y el hexadecimal de los primeros 48 bytes | En el plan (T2) |
| F3 | Un rechazo antes de la transcripción que no sobrescribe los bytes | Cuatro unitarias nuevas en `SubmitAudioAnswerServiceTest` | En el plan (T2) |
| F4 | Una guardia de arquitectura que no puede fallar (regla mal escrita) | Ensayo V5 con una clase temporal | En el plan (T3) |
| F5 | Renumeración incompleta (un enlace roto) | V7 (`git grep` sin resultados) | En el plan (T4) |

## Cómo se aborda

- Solo se agregan pruebas y se corrige documentación. **No hay código de producción nuevo.**
- Se reutiliza `AudioAnswerEndToEndTest` con sus ayudantes:
  - `startInterview`, `sendAudio` (con nombre y tipo declarado), `audio`, `paddedWebm`, `code`, `answers`;
  - los dobles `ScriptedTranscriber` y `transient503()`.
- La captura del log usa `CapturedOutput`, como hoy.
- Las unitarias se agregan a `SubmitAudioAnswerServiceTest` con sus ayudantes `sessionWith`, `openTurnAt`, `command` y el `AudioAnswerPolicy` real.
- La guardia de arquitectura va en `LayeredArchitectureTest`, con el mismo `classes` importado (sin pruebas).

## Archivos

| Archivo | Cambio | Tarjeta |
|---|---|---|
| `src/test/java/tech/cameia/entrevista/presentation/AudioAnswerEndToEndTest.java` | Directorio de multipart propio por `@DynamicPropertySource`, `MultipartConfigElement` inyectado y dos pruebas nuevas | T1, T2 |
| `src/test/java/tech/cameia/entrevista/application/service/SubmitAudioAnswerServiceTest.java` | Cuatro pruebas nuevas de bytes en cero | T2 |
| `src/test/java/tech/cameia/entrevista/architecture/LayeredArchitectureTest.java` | Una regla nueva | T3 |
| `docs/adr/*`, `docs/bitacora-ia/*`, `docs/entregas/CM-345-*`, `specs/CM-345*/tasks.md`, `specs/CM-349*/plan.md`, `specs/CM-362*/{spec,tasks}.md` | Renumeración y ADR 0014 (spec §10) | T4 |

No se tocan: `src/main/**`, `postman/**`, `docs/errores.md`, migraciones, `pom.xml`, `CLAUDE.md`.

## Orden y dependencias

T1 → T2 → T3 → T4, en serie y en la misma rama. Cada tarjeta es un commit, salvo T1 y T2, que van en el mismo commit (spec §11). No hay nada para trabajar en paralelo.

## Riesgos

- Agregar `@DynamicPropertySource` crea un contexto de Spring distinto para `AudioAnswerEndToEndTest` (unos 10 s más). Se acepta.
- El directorio temporal se crea con `Files.createTempDirectory`. Es código de prueba; la guardia de T3 importa solo clases de producción (`DO_NOT_INCLUDE_TESTS`).

## Estimación

3 h (la de Jira): 1 h para T1 y T2, 0,5 h para T3, 0,5 h para T4 y 1 h de verificación final y PR.
