# Tarjetas · CM-352 Quitar el tono Motivador

Cada tarjeta se ejecuta sola y en orden; quien la ejecuta no lee la spec.

**Reglas para todas**

- Identificadores, métodos de prueba y códigos en inglés; Javadoc, comentarios, `@DisplayName`, OpenAPI, mensajes y documentos en español. Sin el identificador de la tarea ni rutas a otros archivos dentro de comentarios de código.
- Prohibido: dependencias nuevas, editar migraciones ya aplicadas (`V1` a `V4`), tocar `.github/`, registrar datos de personas.
- Docker encendido. Sin Docker las pruebas de integración se omiten y eso **no** cuenta como verde.
- **Detenerse y reportar a Paula** si un archivo o nombre de esta lista no existe donde dice, una prueba existente se rompe sin causa clara o falta un dato.
- Al terminar cada tarjeta: `./mvnw.cmd -B -ntp test` del módulo tocado y anotar la salida real.
- Commits: `CM-352 | tipo(entrevista): resultado`, en español, sin `[IA-ASISTIDO]`.

---

## T0 · Preparación y hechos por confirmar

1. `git fetch`; `git status` limpio (los cambios locales ajenos del repo se guardan antes; no se tocan). Rama `CM-352-quitar-tono-motivador` desde `origin/develop`; anotar el SHA.
2. Migraciones libres: `git ls-tree --name-only origin/develop src/main/resources/db/migration/`. Si ya existe `V5`, usar la siguiente en todo este documento.
3. Línea base: `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`, ninguna clase de Testcontainers `Skipped`. Anotar el número de pruebas.
4. **Confirmar cómo se guarda el tono en `cuerpo_respuesta`.** Prueba temporal (no se commitea): en `CreateSessionEndToEndTest` crear una sesión con `Idempotency-Key: "t0-key"` y tono `MOTIVADOR`, leer `SELECT cuerpo_respuesta::text FROM microentrevista.clave_idempotencia` y anotar el valor de `configuration.tone` (esperado `"MOTIVATING"`) y el de la misma prueba con `CALIDO` (esperado `"WARM"`). Con esos dos valores se completan los marcadores de §9 de la spec. Descartar la prueba temporal.
5. Jira (solo lectura): CM-352, comentarios y padre CM-351; anotar si algo cambió. Si cambia un valor, detenerse.
6. Mover CM-352 a «En curso» en Jira (Paula es la responsable).

**Terminado:** rama, SHA, número de migración, línea base verde y los dos valores anotados.

---

## T1 · Migración (REQ-TON-01, 02, 03)

**Crear** `src/main/resources/db/migration/V5__quitar_tono_motivador.sql` con el SQL de la spec §9, con los marcadores sustituidos por los valores de T0.4.

**No se toca** ninguna migración anterior.

**Prueba primero** (T3, clase `RemoveMotivatingToneMigrationTest`): debe fallar con el archivo ausente.

**Verificación:** `./mvnw.cmd -B -ntp -Dtest=RemoveMotivatingToneMigrationTest test`.

**Trampa:** el `UPDATE` va antes del `ALTER`. `jsonb_set` con ruta inexistente no crea la clave (devuelve el original), y `#>>` sobre `{}` da `NULL`, por lo que la condición `= 'MOTIVATING'` no coincide: sin error.

**Terminado:** migración aplicada en Testcontainers sin error.

---

## T2 · Código y OpenAPI (REQ-TON-04, 05, 06, 08)

| Archivo (anclas de `8e683cd`) | Cambio |
|---|---|
| `domain/model/Tone.java` líneas 14–15 | Borrar la constante `MOTIVATING("MOTIVADOR")` y su comentario; `STRICT("ESTRICTO");` pasa a terminar con `;` |
| `presentation/dto/PersonalizationCatalogResponse.java` (l. 11 y 23) | «las cuatro listas» sigue siendo cierto; solo la descripción de `tones`: quitar `MOTIVADOR` de los valores |
| `presentation/dto/SessionConfigurationResponse.java` línea 27 | `allowableValues = {"PROFESIONAL_NEUTRO", "CALIDO", "ESTRICTO"}` |
| `presentation/dto/CreateSessionRequest.java` línea 37 | `allowableValues` sin `"MOTIVADOR"`; la descripción dice «uno de los tres tonos del catálogo» |
| `presentation/controller/InterviewConfigController.java` línea 39 | Ejemplo con tres tonos; quitar `,{"value":"MOTIVADOR","available":true}` |
| `domain/policy/PersonalizationCatalog.java` | Sin cambio de código; revisar que el Javadoc no diga cuatro tonos |

**No se tocan:** `SessionConfigurationPolicy`, `ToneConverter`, `ErrorCode`.

**Verificación:** `./mvnw.cmd -B -ntp -Dtest='PersonalizationCatalog*Test,EnumeratedValuesTest,CatalogSchemaConsistencyTest' test` (las pruebas viejas fallan hasta T3: es lo esperado).

**Terminado:** compila; `git grep -n -i "motivador\|MOTIVATING" -- src/main` solo devuelve la migración `V1` y `V5`.

---

## T3 · Pruebas (todas las filas de la spec §10)

1. `EnumeratedValuesTest.tone_shouldMapBacklogValues`: quitar la línea de `MOTIVADOR`; agregar `assertThatThrownBy(() -> Tone.fromCode("MOTIVADOR")).isInstanceOf(IllegalArgumentException.class)` y `assertThat(Tone.values()).hasSize(3)`.
2. `PersonalizationCatalogTest`: quitar `Arguments.of(Tone.MOTIVATING, true)`; renombrar `tones_shouldOfferFourTonesInOrder_whenRead` a `tones_shouldOfferThreeTonesInOrder_whenRead`, `@DisplayName("Los tres tonos salen en orden y todos disponibles")`, esperando `PROFESSIONAL_NEUTRAL, WARM, STRICT`.
3. `CatalogTestData`: quitar el tono `MOTIVADOR` del JSON esperado (el catálogo de prueba termina en `ESTRICTO`).
4. `PersonalizationCatalogEndToEndTest`: debe seguir verde con el nuevo `CatalogTestData`; agregar una aserción de que la lista `tones` tiene 3 elementos.
5. `CreateSessionEndToEndTest` línea 463: cambiar el tono de la prueba `create_shouldAcceptTextAndEnglish_withFreePlan` a `ESTRICTO`; agregar:
   - `create_shouldReturn422_whenToneIsRemovedMotivating`: `MOTIVADOR` → 422, `errors[]` con `{field:"tone", code:"TONE_INVALID_VALUE", message:"Elige un tono de la lista."}`, cero filas en `sesion` y en `clave_idempotencia`, y el cuerpo sin `IllegalArgument` ni `Tone`.
   - `create_shouldReturn422_whenReplayingOldMotivatingRequest`, `create_shouldReturn409_whenOldMotivatingKeyReusedWithNeutral` y `create_shouldReplay_whenOldKeyHasOtherTone`: insertar por JDBC una clave con la huella y el cuerpo exactos que produce la versión anterior (valores de T0.4; la huella se calcula con la cadena canónica de `ValidatedSessionRequest.fingerprint()` con `MOTIVADOR`), ejecutar la migración de datos equivalente ya aplicada (la base del test ya está en la última versión: insertar el cuerpo ya reescrito y comprobar que `fromJson` lo lee) y repetir la petición.
6. `SessionConfigurationPolicyTest`: agregar `"MOTIVADOR"` y `"motivador"` a la tabla de tonos inválidos (`TONE_INVALID_VALUE`).
7. **Nueva** `src/test/java/tech/cameia/entrevista/infrastructure/persistence/RemoveMotivatingToneMigrationTest.java`: `@Testcontainers(disabledWithoutDocker = true)`, PostgreSQL 16, **sin** `@SpringBootTest`: crea `Flyway.configure().dataSource(...).schemas("microentrevista")` con la misma configuración que la aplicación (leer `spring.flyway.*` de `application.properties`), `target("4")` → `migrate()`; inserta con `JdbcTemplate` (a) cinco sesiones `MOTIVADOR` (una por estado, respetando `ck_sesion_fecha_fin` y los índices únicos con `firebase_uid` distintos), (b) una `CALIDO`, (c) tres claves (cuerpo con tono de Motivador, con `CALIDO`, `{}`); luego `target(latest)` → `migrate()`. Métodos: `migration_shouldRewriteSessionsToNeutral_whenSavedWithMotivating` (parametrizado por estado), `migration_shouldKeepOtherTones`, `migration_shouldBumpVersion`, `migration_shouldRejectMotivating_afterMigration` (valores `MOTIVADOR`, `motivador`, `''`, `' CALIDO'`; mensaje contiene `ck_sesion_tono`), `migration_shouldRewriteStoredIdempotencyBody`, `migration_shouldKeepOtherStoredBodies`, `migration_shouldIgnoreBodiesWithoutConfiguration`, `migration_shouldSucceed_whenTablesEmpty` (segundo contenedor o base limpia), `migration_shouldLetApplicationReadRewrittenSession` (comprobar con `ToneConverter().convertToEntityAttribute` sobre el valor leído).
8. `InterviewSchemaMigrationTest`: si compara la lista del `CHECK`, actualizarla.

**Cada prueba debe poder fallar:** aserciones concretas; probar a mano que fallan sin T1/T2.

**Terminado:** `./mvnw.cmd -B -ntp test` verde, ninguna clase omitida.

---

## T4 · Documentos y Postman (REQ-TON-08)

- `postman/cameia-entrevista.postman_collection.json` (línea 332): quitar `MOTIVADOR` del objeto `esperado` de «200 catálogo completo». Agregar al final de la carpeta «Crear sesión» la petición «422 tono Motivador ya no existe» (cuerpo válido con `tone: "MOTIVADOR"`; `pm.test` de estado 422, `Content-Type` `application/problem+json` con charset, `code = VALIDATION_FAILED`, `errors[0].field = "tone"`, `errors[0].code = "TONE_INVALID_VALUE"`, `errors[0].message = "Elige un tono de la lista."`, sin traza ni nombres de clase, `requestId` presente).
- `postman/README.md`: una línea con la petición nueva.
- `docs/errores.md` fila `TONE_INVALID_VALUE`: causa «Valor que no es uno de los tres tonos del catálogo (`PROFESIONAL_NEUTRO`, `CALIDO`, `ESTRICTO`)».
- `docs/verificaciones/V-02-catalogo-personalizacion.md`: tres tonos en las tablas.
- **ADR nuevo** `docs/adr/0010-quitar-el-tono-motivador.md`: contexto, decisión (migración, reescritura de sesiones y claves, aviso a Frontend), alternativas descartadas (borrar claves; migración en dos pasos), consecuencias (dato irreversible, ventana de despliegue).
- `docs/bitacora-ia/`: una entrada para la decisión humana del 2-nov (prompt armado), según el README de esa carpeta.

**Verificación:** `git grep -n -i "motivador"` fuera de `V1`, `V5`, su prueba, el ADR y `specs/` → sin resultados.

---

## T5 · Cierre (una vez, sobre la punta)

1. `git fetch`; si `origin/develop` tomó `V5`, renumerar la migración y re-ejecutar `RemoveMotivatingToneMigrationTest` e `InterviewSchemaMigrationTest`.
2. `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`; leer `target/site/jacoco/jacoco.csv` de `Tone`, `PersonalizationCatalog` y las clases tocadas.
3. Empaquetar y levantar la aplicación con PostgreSQL real; `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --reporters cli,junit`; pegar la salida.
4. Tabla regla → evidencia (R1 a R14) y tabla de atributos de la spec §17 con resultados reales.
5. Cuerpo del PR con la plantilla del repo (`Impacto`: `contrato, datos`; líneas `Contrato:` y `Datos:`), validado con `pr_policy.py`; título `CM-352 | feat(entrevista): quitar el tono Motivador del catálogo y de la base [IA-ASISTIDO]`. Push y PR en borrador solo con spec y plan aprobados.
6. Guía de revisión y tabla «requisito → prueba» a Paula en el chat.
7. En Jira, `En revisión` solo con PR abierto y DoD cumplido; comentario con el formato del `CLAUDE.md` raíz.

---

## Resultados de la ejecución (7-nov-2026)

Logs en `Desarrollo/ejecucion/evidencia/CM-352/`; resumen completo en `resumen.md` de esa carpeta.

| Tarjeta | Resultado |
|---|---|
| T0 | Rama `CM-352-quitar-tono-motivador` desde `8e683cd`; migraciones libres hasta `V4`, se usa `V5`. Línea base `clean verify`: `BUILD SUCCESS`, 1113 pruebas, 1 omitida (`GeminiLanguageModelLiveTest`, manual por diseño). T0.4: el tono se guarda como `"MOTIVATING"` y `"WARM"` (`01-t0-tono-guardado.log`). Jira sin cambios; CM-352 pasó a «En curso». |
| T1 | `V5__quitar_tono_motivador.sql` con el SQL de la spec §9 y los valores de T0.4. Prueba en rojo sin `V5` (9 fallos, `02-migracion-ROJO-sin-V5.log`) y en verde con `V5` (23 pruebas, `03-migracion-VERDE.log`). Commit `b173419`. |
| T2 | `Tone` con tres constantes y cuatro textos de OpenAPI ajustados; `git grep` de `src/main` solo devuelve `V1` y `V5`. Commit `7bd16c5`. |
| T3 | Pruebas actualizadas y nuevas (ver `resumen.md`); 279 pruebas de las clases tocadas en verde. Una prueba nueva falló la primera vez por un error de la propia prueba (huella calculada sin la oferta) y se corrigió. Commit `9dfdccb`. |
| T4 | Postman (catálogo con tres tonos y petición 21), `postman/README.md`, `docs/errores.md`, ADR 0010, entrada 08 de la bitácora y una línea en `CLAUDE.md`. `V-02` no lista los tonos en ninguna tabla: sin cambio. Commit `d6c741d`. |
| T5 | `git fetch`: `origin/develop` sigue en `8e683cd`, sin `V5`. `clean verify`: `BUILD SUCCESS`, 1142 pruebas, 1 omitida. Newman contra el jar empaquetado y PostgreSQL 16 y RabbitMQ reales: 44 peticiones, 183 aserciones, 0 fallos. Exploración adversarial: 0 respuestas 5xx. Sin push, sin PR, Jira sin pasar a «En revisión» (no hay PR). |
