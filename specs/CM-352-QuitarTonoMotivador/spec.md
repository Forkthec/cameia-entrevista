# CM-352 · Quitar el tono Motivador (base de datos, enumerado y catálogo)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-352 (subtarea de Backend de CM-351, historia técnica HT-09), Sprint 4, asignada a Paula Andrea Muñoz Delgado, estado «Por hacer» (Jira leído el 7-nov-2026; sin comentarios) |
| Repositorio | `cameia-entrevista`, base `origin/develop` = `8e683cd` |
| Backlog | v10.2, hoja Backlog fila 32 (HT-09), criterios (1) y (2); el (3) es de Frontend (CM-353) y el (4) es del backlog |
| Estado de la spec | **LISTA PARA EJECUTAR** (la aprueba solo Paula). Decisiones: D-0, D-1 y D-2 cerradas (D-2 decidida por Paula el 7-nov-2026); queda D-3, una verificación previa que no bloquea |
| Estimación | 3 h según el plan del Sprint 4 (Jira manda); esta spec estima 4 h por la reescritura de la respuesta guardada del anti-duplicados (hallazgo H-1) |

## 1. Objetivo

Que ninguna parte de `cameia-entrevista` conozca el tono «Motivador» (`MOTIVADOR`):

1. La base de datos no lo admite (`ck_sesion_tono`) y las sesiones ya guardadas con ese tono pasan a «Profesional y neutro» (`PROFESIONAL_NEUTRO`), decisión de Paula del 2-nov-2026.
2. El enumerado `Tone` y el catálogo `GET /api/v1/interviews/config/catalog` ofrecen solo tres tonos, en este orden: `PROFESIONAL_NEUTRO`, `CALIDO`, `ESTRICTO`.
3. Crear una sesión con `MOTIVADOR` da 422 `TONE_INVALID_VALUE` «Elige un tono de la lista.» (ya existe; solo cambia que el valor deja de ser válido).
4. Documentación, OpenAPI, ejemplos, Postman y pruebas dicen tres tonos.

## 2. Fuentes y vigencia

| Fuente | Qué aporta |
|---|---|
| Backlog v10.2, hoja Backlog fila 32 (HT-09) | «migración que quita MOTIVADOR de ck_sesion_tono y resuelve las sesiones ya guardadas; MOTIVADOR sale del enum y del catálogo». Terminado: migración en develop, catálogo con 3 tonos en la API, pruebas en verde |
| Backlog v10.2, HE-04, CA-4.1.3 | El catálogo aprobado tiene tres tonos; Motivador sale con HT-09 |
| Glosario v4 (1-nov) | «Tono del entrevistador: Profesional y neutro, Cálido o Estricto» |
| Decisión de Paula, 2-nov-2026 (`comunicaciones/reuniones/02112026_daily.md` línea 16) | Las sesiones guardadas con Motivador pasan a Profesional y neutro |
| Jira CM-352 | Descripción igual al backlog; sin comentarios, sin enlaces |
| Código `origin/develop` (`8e683cd`) | Ver §3 |
| `cameia-web` `origin/develop` (solo lectura) | `NewInterviewWizardPage.test.tsx` línea 47: la pantalla ya no muestra «Motivador» aunque el catálogo lo entregue; `interviewSetup.dto.ts`, `interviewSetup.types.ts`, `interviewSetup.api.ts` y los mocks aún nombran `MOTIVADOR` (eso es CM-353, de Frontend) |

## 3. Hechos comprobados en el código (`origin/develop` `8e683cd`)

| # | Hecho | Ancla |
|---|---|---|
| F-1 | `MOTIVADOR` aparece en `ck_sesion_tono` y en el comentario de la columna `tono` | `V1__esquema_inicial_entrevista.sql:56` y `:268` |
| F-2 | Constante `MOTIVATING("MOTIVADOR")` | `domain/model/Tone.java:15` |
| F-3 | El catálogo toma `Tone.values()`, así que se ajusta solo | `domain/policy/PersonalizationCatalog.java:35` |
| F-4 | Textos que nombran `MOTIVADOR`: `@Schema` de `PersonalizationCatalogResponse` (l. 23), `SessionConfigurationResponse` (l. 27), `CreateSessionRequest` (l. 37) y el ejemplo OpenAPI de `InterviewConfigController` (l. 39) | main |
| F-5 | Pruebas y datos que lo nombran: `CatalogTestData` (l. 10), `EnumeratedValuesTest` (l. 27), `PersonalizationCatalogTest` (l. 33, 49–52), `CreateSessionEndToEndTest` (l. 463: crea con `MOTIVADOR` y espera 201) | test |
| F-6 | Postman: la petición «200 catálogo completo» compara el catálogo con cuatro tonos (línea 332) y su prueba de orden exacto | `postman/cameia-entrevista.postman_collection.json` |
| F-7 | `docs/verificaciones/V-02-catalogo-personalizacion.md` y `docs/adr/0007-forma-del-catalogo-de-personalizacion.md` describen el catálogo (el ADR dice «cuatro listas», que sigue siendo cierto: modalidades, tonos, formas, idiomas; no se toca) | docs |
| F-8 | `SessionConfigurationPolicy` valida el tono con `Tone::fromCode` y rechaza lo desconocido con `TONE_INVALID_VALUE` (422, «Elige un tono de la lista.»); ausente: `TONE_REQUIRED` «Elige el tono del entrevistador.» | `SessionConfigurationPolicy.java:48,49,92` |
| F-9 | `CatalogSchemaConsistencyTest` ya compara los valores del catálogo con el `CHECK` de la base | test |
| F-10 | `ToneConverter.convertToEntityAttribute` lanza `IllegalArgumentException` si la base trae un valor que el enumerado no conoce | `ToneConverter.java` |
| F-11 | `CreateSessionService.create` valida **antes** de consultar la clave anti-duplicados (`policy.validate` y luego `replayIfKnown`) | `CreateSessionService.java` (método `create`) |
| F-12 | La respuesta guardada del anti-duplicados es `StoredBody(sessionId, status, configuration: ValidatedSessionRequest, ongoing)` serializado con Jackson sin `@JsonValue`: el tono queda con el **nombre de la constante** (se espera `"MOTIVATING"`; la tarjeta T0 lo confirma con una sesión real) | `CreateSessionService.java` (`StoredBody`, `toJson`, `fromJson`); columna `clave_idempotencia.cuerpo_respuesta jsonb` |
| F-13 | Ninguna otra tabla guarda el tono (`grep` de `MOTIVADOR` y `tono` en V1 a V4 solo toca `sesion`) | migraciones |
| F-14 | Migraciones hasta `V4__operacion_crear_sesion.sql`. La spec de CM-216 (ahora de Vela, sin aprobar) usa `V5__operacion_registrar_respuesta.sql` | `specs/CM-216-RegistrarRespuesta/spec.md` §9 |

## 4. Hallazgos (clasificados)

| # | Hallazgo | Clase | Destino |
|---|---|---|---|
| H-1 | **Quitar la constante rompe la repetición de claves viejas.** Si `clave_idempotencia.cuerpo_respuesta` de una creación con `MOTIVADOR` guarda `"MOTIVATING"`, `fromJson` falla al repetirla con la misma clave y el cliente recibe 500 genérico. Pasa solo con una repetición de la misma clave con un cuerpo de otro tono (la misma configuración da 422 antes, F-11) | Cambio sin alternativa | La migración también reescribe `cuerpo_respuesta` (REQ-TON-03) y una prueba lo demuestra |
| H-2 | **Ventana de despliegue.** Cloud Run corre la migración al arrancar la revisión nueva mientras la anterior aún atiende: una creación con `MOTIVADOR` en esa ventana viola `ck_sesion_tono` en la revisión vieja | Riesgo aceptado (D-2) | Se documenta; ver D-2 |
| H-3 | **Orden con Frontend: resuelto.** `cameia-web` ya oculta «Motivador» (CM-353, PR #122 fusionado; mensaje de Ana Sofía del 4-nov): ya no hay riesgo de que la pantalla envíe `MOTIVADOR`. Queda solo confirmar desde qué versión corre en preproducción y producción y que sus tipos y datos de prueba ya no nombren `MOTIVADOR` | Verificación para Frontend | Aviso actualizado en `comunicaciones/` |
| H-4 | **Dato irreversible.** La migración pierde el tono original de las sesiones antiguas; no hay migración «hacia abajo» | Decisión de Paula ya tomada (2-nov) | Registrado en la spec y en el ADR 0010 |
| H-5 | **Producción tiene datos reales desde el 2-nov.** Entrevista en producción (`v1.0.0`) pudo guardar sesiones con `MOTIVADOR` de personas reales; el 2-nov se habló de datos de prueba solo en preproducción | Verificación previa | Antes de promover, contar `SELECT tono, estado, count(*) FROM microentrevista.sesion GROUP BY 1,2` (solo lectura, lo hace quien tenga acceso: no hay acceso desde las herramientas). Resultado PENDIENTE (Paula o DevOps) |
| H-6 | Número de migración compartido con CM-216 (`V5`) | Coordinación | §14: quien fusiona segundo renumera; se comprueba `origin/develop` antes de cada push |

## 5. Criterios de aceptación y trazabilidad (R12)

HT-09 no tiene criterios con identificador `CA-`; sus criterios son numerados en la fila 32. Terminado de la fila: «migración en develop, catálogo con 3 tonos en la API y en la web, pruebas en verde».

| Criterio (literal) | Dueño | Requisito | Prueba (nombre exacto) | Postman |
|---|---|---|---|---|
| (1) «migración en cameia-entrevista que quita MOTIVADOR de ck_sesion_tono y resuelve las sesiones ya guardadas con ese tono» | B | REQ-TON-01, 02, 03 | `RemoveMotivatingToneMigrationTest.migration_shouldRewriteSessionsToNeutral_whenSavedWithMotivating`, `…migration_shouldRejectMotivating_afterMigration`, `…migration_shouldRewriteStoredIdempotencyBody` | — |
| (2) «MOTIVADOR sale del enum de tonos y del catálogo GET /api/v1/interviews/config/catalog» | B | REQ-TON-04, 05 | `EnumeratedValuesTest.tone_shouldMapBacklogValues`, `PersonalizationCatalogTest.tones_shouldOfferThreeTonesInOrder_whenRead`, `PersonalizationCatalogEndToEndTest` (catálogo con 3 tonos), `CatalogSchemaConsistencyTest` | «200 catálogo completo» (3 tonos) |
| (3) pantalla de `cameia-web` | F | fuera de alcance (CM-353) | — | — |
| (4) HU-4.3 con 3 tonos | Backlog | fuera de alcance de código; CA-4.1.3 ya dice tres tonos, pero HU-4.3 (hoja HE-04, filas 38, 39 y 47) aún nombra Motivador y lo corrige Paula | — | — |
| Terminado: pruebas en verde | B | REQ-TON-09 | suite completa | Newman |
| Decisión del 2-nov: sesiones guardadas pasan a Profesional y neutro | B | REQ-TON-02 | `…migration_shouldRewriteSessionsToNeutral_whenSavedWithMotivating` (las cuatro situaciones: configurada, en curso, finalizada y abandonada) | — |
| Crear con `MOTIVADOR` ya no es válido | B | REQ-TON-06 | `CreateSessionEndToEndTest.create_shouldReturn422_whenToneIsRemovedMotivating`, `SessionConfigurationPolicyTest` (caso `MOTIVADOR` en la tabla de valores inválidos) | «422 tono Motivador ya no existe» |

## 6. Requisitos (EARS)

- **REQ-TON-01** La migración Flyway `V5__quitar_tono_motivador.sql` debe reemplazar `ck_sesion_tono` por `CHECK (tono IN ('PROFESIONAL_NEUTRO', 'CALIDO', 'ESTRICTO'))`, sin tocar `varchar(20) NOT NULL`. Después de la migración, insertar o actualizar una sesión con `tono = 'MOTIVADOR'` debe fallar con la restricción `ck_sesion_tono`.
- **REQ-TON-02** Antes de reemplazar la restricción, la migración debe poner `tono = 'PROFESIONAL_NEUTRO'`, `version = version + 1` y `fecha_actualizacion = now()` en toda fila de `microentrevista.sesion` con `tono = 'MOTIVADOR'`, sin importar su estado (`CONFIGURADA`, `EN_CURSO`, `EVALUANDO`, `FINALIZADA`, `ABANDONADA`). No debe tocar ninguna otra columna ni ninguna fila con otro tono. `version + 1` hace que una escritura concurrente de la aplicación vieja sobre esa fila falle por bloqueo optimista en vez de pisar el cambio.
- **REQ-TON-03** En la misma migración, toda fila de `microentrevista.clave_idempotencia` cuya `cuerpo_respuesta` tenga `configuration.tone` igual al nombre guardado de `MOTIVADOR` (valor exacto confirmado en T0) debe quedar con `configuration.tone` igual al nombre guardado de `PROFESIONAL_NEUTRO`. No se toca `huella_peticion` (la huella sigue describiendo la petición original).
- **REQ-TON-04** `Tone` debe tener exactamente tres constantes, en este orden: `PROFESSIONAL_NEUTRAL("PROFESIONAL_NEUTRO")`, `WARM("CALIDO")`, `STRICT("ESTRICTO")`. `Tone.fromCode("MOTIVADOR")` debe lanzar `IllegalArgumentException`.
- **REQ-TON-05** `GET /api/v1/interviews/config/catalog` debe devolver `tones` = `[{"value":"PROFESIONAL_NEUTRO","available":true},{"value":"CALIDO","available":true},{"value":"ESTRICTO","available":true}]`, en ese orden y sin otros elementos. Las otras tres listas no cambian.
- **REQ-TON-06** `POST /api/v1/interviews/sessions` con `"tone":"MOTIVADOR"` (o cualquier otro valor fuera de los tres, también en minúsculas) debe responder 422 `VALIDATION_FAILED` con `errors[]` que incluya `{"field":"tone","code":"TONE_INVALID_VALUE","message":"Elige un tono de la lista."}`; no debe crear ni modificar sesión ni guardar clave anti-duplicados. Todos los errores de forma del cuerpo se devuelven a la vez (comportamiento existente que no cambia).
- **REQ-TON-07** Repetir una petición con una clave anti-duplicados guardada **antes** de la migración con tono `MOTIVADOR` debe: (a) con el cuerpo original (`MOTIVADOR`) responder 422 como en REQ-TON-06; (b) con la misma clave y un cuerpo de otro tono responder 409 `IDEMPOTENCY_KEY_CONFLICT` (la huella difiere); (c) nunca responder 500. Repetir una clave antigua de otro tono (`CALIDO`) debe seguir devolviendo su respuesta guardada.
- **REQ-TON-08** El OpenAPI (`@Schema` de `CreateSessionRequest.tone`, `SessionConfigurationResponse.tone`, `PersonalizationCatalogResponse.tones` y el ejemplo de `InterviewConfigController`), los comentarios de la columna `tono`, `docs/verificaciones/V-02`, `docs/errores.md` (fila `TONE_INVALID_VALUE`: causa «valor fuera de los tres tonos del catálogo») y la colección de Postman deben nombrar solo los tres tonos.
- **REQ-TON-09** La suite completa, `LayeredArchitectureTest`/`ArchUnit` del repo y Newman deben pasar; la cobertura de lo modificado debe ser ≥ 90 % de líneas y de ramas (lo modificado son constantes y textos: se espera 100 %).
- **REQ-TON-10** La migración debe correr sobre una base con datos (Testcontainers con PostgreSQL 16) llevando primero la base a la versión 4, insertando filas con `MOTIVADOR` y claves guardadas, y luego migrando a la última.

## 7. Contrato (compatibilidad con Frontend)

| Elemento | Antes de la tarea | Después |
|---|---|---|
| `GET …/config/catalog` → `tones` | 4 elementos | 3 elementos (se quita el último) |
| `POST …/sessions`, campo `tone` | acepta 4 valores | acepta 3 valores; `MOTIVADOR` → 422 `TONE_INVALID_VALUE` |
| `GET`/respuestas con `tone` | pueden traer `MOTIVADOR` | nunca lo traen (las filas antiguas pasan a `PROFESIONAL_NEUTRO`) |

Es un cambio de contrato **restrictivo**, no aditivo. Aplica la regla de «avisar a Frontend» (documento en `comunicaciones/`, §12). Ningún código nuevo de error; `TONE_INVALID_VALUE` ya existe y está documentado y probado.

## 8. Validación del campo `tone` (R2)

Se reutiliza `SessionConfigurationPolicy`; la tabla fija el comportamiento tras el cambio. Todas las filas, salvo la primera y las de `MOTIVADOR`, ya tienen prueba en `SessionConfigurationPolicyTest`.

| Entrada de `tone` | Resultado | `code` | Mensaje | Prueba |
|---|---|---|---|---|
| `"PROFESIONAL_NEUTRO"`, `"CALIDO"`, `"ESTRICTO"` | válido | — | — | existentes |
| `"MOTIVADOR"` | 422 | `TONE_INVALID_VALUE` | Elige un tono de la lista. | **nueva fila** en la tabla de inválidos de `SessionConfigurationPolicyTest` y `CreateSessionEndToEndTest.create_shouldReturn422_whenToneIsRemovedMotivating` |
| `"motivador"`, `"Calido"` | 422 | `TONE_INVALID_VALUE` | ídem | `SessionConfigurationPolicyTest` (existente para minúsculas; se agrega `motivador`) |
| ausente, `null`, `""`, `"   "` | 422 | `TONE_REQUIRED` | Elige el tono del entrevistador. | existentes |
| número, objeto, arreglo en vez de texto | 422 | `REQUEST_BODY_INVALID_FORMAT` (existente, CM-311) | — | existente |

Longitud: no cambia (`varchar(20)`; el valor más largo es `PROFESIONAL_NEUTRO`, 18 caracteres). El valor y la columna coinciden con `CatalogSchemaConsistencyTest`.

## 9. Base de datos (R4)

Migración `src/main/resources/db/migration/V5__quitar_tono_motivador.sql` (el número se confirma contra `origin/develop` en T0; ver H-6):

```sql
-- ---------------------------------------------------------------------------
-- El tono Motivador sale del catalogo. Las sesiones guardadas con ese tono pasan a
-- Profesional y neutro; la respuesta guardada de las claves anti-duplicados se ajusta
-- para que repetir una clave antigua nunca falle al leerla.
-- ---------------------------------------------------------------------------
UPDATE microentrevista.sesion
   SET tono = 'PROFESIONAL_NEUTRO', version = version + 1, fecha_actualizacion = now()
 WHERE tono = 'MOTIVADOR';

UPDATE microentrevista.clave_idempotencia
   SET cuerpo_respuesta = jsonb_set(cuerpo_respuesta, '{configuration,tone}', '"<NOMBRE_PROFESSIONAL_NEUTRAL_CONFIRMADO_EN_T0>"')
 WHERE cuerpo_respuesta #>> '{configuration,tone}' = '<NOMBRE_MOTIVATING_CONFIRMADO_EN_T0>';

ALTER TABLE microentrevista.sesion
    DROP CONSTRAINT ck_sesion_tono,
    ADD CONSTRAINT ck_sesion_tono CHECK (tono IN ('PROFESIONAL_NEUTRO', 'CALIDO', 'ESTRICTO'));

COMMENT ON COLUMN microentrevista.sesion.tono IS 'Valores: PROFESIONAL_NEUTRO, CALIDO, ESTRICTO.';
```

- Los dos `<…>` se reemplazan por los valores que T0 lea de una respuesta guardada real (esperados `PROFESSIONAL_NEUTRAL` y `MOTIVATING`). Si T0 muestra que el tono se guarda con el código (`MOTIVADOR`), el segundo `UPDATE` usa `MOTIVADOR` y `PROFESIONAL_NEUTRO`.
- Orden obligatorio: primero los `UPDATE`, después la restricción (al agregarla la base valida todas las filas).
- Flyway ejecuta cada migración en una transacción: si algo falla no queda nada a medias.
- Tablas pequeñas: el bloqueo de `ALTER TABLE` es breve. Sin índices nuevos (no hay filtro nuevo). Sin columnas nuevas, sin claves foráneas.
- Reversa: no hay. Volver a ofrecer `MOTIVADOR` exigiría una migración nueva; las filas reescritas no se recuperan (H-4).
- `trg_*` de V1 no tocan `sesion` ni `clave_idempotencia` (comprobado: solo `pregunta` e `intento_respuesta`).

## 10. Casos borde (R5)

| Dimensión | Caso | Resultado esperado | Prueba |
|---|---|---|---|
| Datos existentes | sesión en cada uno de los 5 estados con `MOTIVADOR` | las cinco pasan a `PROFESIONAL_NEUTRO`; `estado` y `fecha_fin` intactos; `version` +1 | `migration_shouldRewriteSessionsToNeutral_whenSavedWithMotivating` (parametrizada por estado) |
| Datos existentes | sesión con otro tono (`CALIDO`) | sin cambio: mismo `tono`, misma `version`, misma `fecha_actualizacion` | misma clase, `migration_shouldKeepOtherTones` |
| Datos existentes | tabla vacía | la migración termina sin error | `migration_shouldSucceed_whenTablesEmpty` |
| Restricción | insertar `MOTIVADOR`, `motivador`, `''`, `' CALIDO'` después | `ck_sesion_tono` | `migration_shouldRejectMotivating_afterMigration` (parametrizada) |
| Respuesta guardada | clave con `configuration.tone` de Motivador | reescrita; `huella_peticion`, `estado_respuesta` y demás campos del JSON intactos | `migration_shouldRewriteStoredIdempotencyBody` |
| Respuesta guardada | clave con `CALIDO` | sin cambio | `migration_shouldKeepOtherStoredBodies` |
| Respuesta guardada | cuerpo `{}` o sin `configuration` | sin cambio y sin error (`#>>` da `NULL`) | `migration_shouldIgnoreBodiesWithoutConfiguration` |
| Repetición | clave antigua + cuerpo `MOTIVADOR` | 422 `TONE_INVALID_VALUE` | `create_shouldReturn422_whenReplayingOldMotivatingRequest` |
| Repetición | clave antigua + cuerpo `PROFESIONAL_NEUTRO` | 409 `IDEMPOTENCY_KEY_CONFLICT` | `create_shouldReturn409_whenOldMotivatingKeyReusedWithNeutral` |
| Repetición | clave antigua de `CALIDO` | 200/201 igual que antes | `create_shouldReplay_whenOldKeyHasOtherTone` |
| Lectura | sesión reescrita se lee por la aplicación | `ToneConverter` devuelve `PROFESSIONAL_NEUTRAL` sin excepción | `migration_shouldLetApplicationReadRewrittenSession` (cargando la entidad con el repositorio) |
| Concurrencia | actualización con la `version` anterior de una fila reescrita | falla por bloqueo optimista | `migration_shouldBumpVersion` (comprueba `version` +1) |
| Ataque | `tone` con `<script>`, 10 000 caracteres, `%`, `_`, `;` | 422 `TONE_INVALID_VALUE`, nunca 500 | `SessionConfigurationPolicyTest` (ya cubre hostiles; se agrega la fila `MOTIVADOR`) |
| Estado final | migración falla a mitad (p. ej. `UPDATE` ok y `ALTER` falla) | Flyway revierte la transacción: nada queda cambiado | no se prueba (comportamiento de PostgreSQL/Flyway con DDL transaccional); se justifica por escrito |

No aplican: Unicode de longitud (valor de lista cerrada), fechas distintas de `fecha_actualizacion` (se fija con el reloj de la base), paginación.

## 11. Errores (R3)

Ninguna ruta nueva llega al error genérico. Rutas revisadas:

| Ruta | Antes de la tarea | Después |
|---|---|---|
| Crear con `MOTIVADOR` | 201 | 422 `TONE_INVALID_VALUE` (existente, catálogo `docs/errores.md`) |
| Repetir clave antigua con otro cuerpo | 409 o replay | 409 `IDEMPOTENCY_KEY_CONFLICT`; **sin H-1 sería 500** (cerrado por REQ-TON-03) |
| Leer una sesión con tono desconocido (`ToneConverter` lanza `IllegalArgumentException`) | n/a | imposible tras la migración por `ck_sesion_tono`; la ventana H-2 solo afecta a la revisión vieja |
| Insertar `MOTIVADOR` desde la revisión vieja durante el despliegue | n/a | viola `ck_sesion_tono`; lo recibe la revisión vieja como error de datos (no es del código nuevo; ver D-2) |

Comprobación con petición real (R3): Postman «422 tono Motivador ya no existe» y la prueba de extremo a extremo.

## 12. Aviso a Frontend y orden de despliegue

- Documento: `comunicaciones/07112026_v1_tono-motivador-sale-del-catalogo-para-frontend.md` (un documento por rol; destinatarios: Ana Sofía Arango y Juan José Arias Chacua).
- Orden recomendado (D-1): 1) Frontend publica CM-353 en preproducción; 2) Backend fusiona CM-352 en `develop` (se despliega a preproducción); 3) ambos promueven a producción juntos. Con ese orden `cameia-web` nunca envía `MOTIVADOR` cuando el servicio ya lo rechaza. Si Backend sale antes, una pantalla que tuviera «Motivador» fijo (sin leer el catálogo) lo enviaría y recibiría 422 con «Elige un tono de la lista.», un error por campo que la pantalla ya muestra. La pantalla de `cameia-web` en `develop` ya no lo ofrece (H-3).

## 13. Seguridad (R6)

| ID / fila | Aplica | Cómo | Prueba |
|---|---|---|---|
| ASVS 2.2.1 (validación contra lista permitida) | sí | `Tone.fromCode` + política: lista cerrada de 3 | `SessionConfigurationPolicyTest` |
| Manejo de errores sin detalle interno (la matriz ASVS de `cameia-infra` no tiene capítulo 16 ni fila de manejo de errores; se cubre con AC-0006) | sí | 422 con mensaje de CA, sin nombre de clase | `CreateSessionEndToEndTest…whenToneIsRemovedMotivating` comprueba que el cuerpo no contiene `IllegalArgument` ni `Tone` |
| ASVS 3.x / 8.2.x (dueño del recurso) | no cambia | la migración no toca dueños | — |
| OWASP API1 (BOLA) | no cambia | sin endpoints nuevos | — |
| OWASP API3 (asignación masiva) | no cambia | `tone` sigue siendo el único campo de configuración; campos extra se ignoran | existente |
| OWASP API4 (consumo de recursos) | no cambia | — | — |
| OWASP API8 (configuración) | sí | la migración corre al arrancar; sin secretos | revisión del SQL |
| Datos en logs | sí | la migración no registra datos; el servicio no registra el tono | revisión |

Los IDs exactos de la matriz de `cameia-infra` (`docs/seguridad/matriz-asvs-nivel1.md`) se contrastan en T0; si algún ID cambió de número se corrige en esta tabla.

## 14. Integración con otras CM de `cameia-entrevista`

| Archivo compartido | Quién lo toca | Regla |
|---|---|---|
| Migración `V5` | CM-352 (esta) y CM-216 (Vela) usan el número 5; CM-212 necesitará otra | Orden propuesto: CM-352 primero (es la más pequeña y del primer día), CM-216 pasa a `V6` y CM-212 a la siguiente. Quien fusiona segundo renumera y corre `InterviewSchemaMigrationTest`. Antes de cada push: `git fetch` y `git ls-tree origin/develop src/main/resources/db/migration/` |
| `ErrorCode` y `docs/errores.md` | CM-352 no agrega códigos; solo cambia la causa de `TONE_INVALID_VALUE` en `errores.md` | sin choque con CM-216 (agrega al final) ni con CM-212 |
| Postman | CM-352 edita «Catálogo» y agrega una petición al final de «Crear sesión» | CM-216 agrega su carpeta nueva; sin choque |
| `CreateSessionEndToEndTest` | solo esta tarea lo cambia | — |

Un PR para la CM, un commit por capacidad (§16). Sin apilar: no depende de otra CM sin fusionar.

## 15. Línea base verde

`./mvnw.cmd -B -ntp clean verify` sobre `origin/develop` con Docker encendido: `BUILD SUCCESS` y ninguna clase de Testcontainers omitida. Si hoy no pasa, es un hallazgo con destino a Paula antes de tocar nada (T0).

## 16. Plan en commits (un PR)

1. `CM-352 | feat(entrevista): migración que quita el tono Motivador y reescribe lo guardado`
2. `CM-352 | feat(entrevista): tres tonos en el enumerado, el catálogo y el OpenAPI`
3. `CM-352 | test(entrevista): pruebas de los tres tonos y de la migración con datos`
4. `CM-352 | docs(entrevista): ADR, errores, verificación y Postman con tres tonos`

(Los commits no llevan `[IA-ASISTIDO]`; el título del PR sí.)

## 17. Atributos de calidad (R13)

| Atributo | Métrica | Límite | Cómo lo cumple | Prueba / comando | Resultado |
|---|---|---|---|---|---|
| AC-0001 adecuación funcional | contrato = backlog (HT-09, CA-4.1.3) | 3 tonos exactos | enumerado, catálogo y CHECK iguales | `CatalogSchemaConsistencyTest`, `PersonalizationCatalogEndToEndTest` | PENDIENTE de ejecutar |
| AC-0004 fiabilidad FIA-01 | ningún fallo deja estado intermedio | migración atómica | una transacción de Flyway; `UPDATE` antes de `ALTER` | `RemoveMotivatingToneMigrationTest` | PENDIENTE |
| AC-0004 FIA-02 | cero efectos al repetir | repetir clave antigua no crea sesión | `create_shouldReturn409_whenOldMotivatingKeyReusedWithNeutral` | PENDIENTE |
| AC-0003 SEG-01 | ASVS N1 aplicable | 100 % | §13 | pruebas citadas | PENDIENTE |
| AC-0003 SEG-04 | cero datos sensibles en logs | 0 | sin logs nuevos | revisión | PENDIENTE |
| AC-0002 DES-02 | p95 ≤ 2 s en operaciones JSON propias | sin cambio | el catálogo es una lista en memoria | Newman (tiempo de «200 catálogo completo») | PENDIENTE |
| MAN-01 cobertura | ≥ 90 % líneas y ramas en lo modificado | 90 % | solo constantes y textos | `jacoco.csv` de `Tone`, `PersonalizationCatalog` | PENDIENTE |
| IOP-01 contrato versionado | OpenAPI al día | 3 tonos | `@Schema` ajustados | Swagger local | PENDIENTE |
| AC-0006 interacción | error con `code`, `message` y campo | `TONE_INVALID_VALUE` | existente | Postman | PENDIENTE |

La épica de HT-09 no declara atributos propios (habilitador técnico sin épica, fila 32); se declaran los que el cambio toca.

## 18. Decisiones y preguntas

| # | Tipo | Decisión | Opciones, consecuencia y recomendación | Estado |
|---|---|---|---|---|
| D-0 | Decisión humana | Las sesiones guardadas con Motivador pasan a Profesional y neutro | Decidido por Paula el 2-nov-2026 | Cerrada |
| D-1 | Resuelta por el hecho | Orden con Frontend | La opción A (Frontend primero) ya se cumplió: CM-353 está fusionado (PR #122). Backend puede fusionar sin esperar; una pantalla vieja aún en caché que enviara `MOTIVADOR` recibiría 422 «Elige un tono de la lista.» | Resuelta |
| D-2 | Decisión de Paula | Ventana de despliegue (H-2) | **A (recomendada):** aceptarla; segundos o minutos, tráfico bajo, y la revisión vieja solo falla si alguien manda `MOTIVADOR` justo entonces. **B:** desplegar en dos pasos (primero código que rechaza `MOTIVADOR`, después la migración): cierra la ventana pero exige dos PR y dos despliegues por una ventana casi nula | **Cerrada: opción A (Paula, 7-nov-2026).** Se acepta la ventana; un solo PR y un solo despliegue |
| D-3 | Verificación previa | Contar sesiones con `MOTIVADOR` en preproducción y producción antes de promover (H-5) | Solo lectura; lo hace quien tenga acceso a la base | **PENDIENTE (Paula o DevOps)** |
| D-4 | Cambio | Reescribir `cuerpo_respuesta` en vez de borrar las claves | Alternativa descartada: borrar las filas. Pierde la respuesta que un reintento legítimo recibiría (FIA-02) | Aplicada |

## 19. Fuera de alcance

- Pantalla de `cameia-web` y sus tipos (CM-353, Sofía).
- Texto de HU-4.3 en el backlog (sigue nombrando Motivador en la v10.2; lo corrige Paula).
- Prompts del modelo y banco del spike (CM-347): el spike prueba tres tonos.
- Migración hacia abajo.
- Cualquier cambio en Jira.

## 20. Lista de verificación de la CM

| # | Qué | Comando o petición | Esperado |
|---|---|---|---|
| 1 | Línea base | `./mvnw.cmd -B -ntp clean verify` en `origin/develop` | `BUILD SUCCESS` |
| 2 | Tono guardado en `cuerpo_respuesta` | prueba temporal de T0 | valor exacto anotado |
| 3 | Migración con datos | `./mvnw.cmd -B -ntp -Dtest=RemoveMotivatingToneMigrationTest test` | verde, sin omitidas |
| 4 | Catálogo | `./mvnw.cmd -B -ntp -Dtest='PersonalizationCatalog*Test,CatalogSchemaConsistencyTest,EnumeratedValuesTest' test` | verde |
| 5 | Crear con Motivador | `-Dtest=CreateSessionEndToEndTest` | 422 y sin sesión creada |
| 6 | Repeticiones antiguas | mismas pruebas, tres casos | 422 / 409 / replay |
| 7 | Suite y cobertura | `./mvnw.cmd -B -ntp clean verify` | verde; JaCoCo ≥ 90 % |
| 8 | Postman | `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --reporters cli,junit` contra la aplicación empaquetada | 0 fallos |
| 9 | Nada nombra Motivador | `git grep -n -i "motivador\|MOTIVATING" -- . ':!src/main/resources/db/migration' ':!specs' ':!src/test/java/**/RemoveMotivatingToneMigrationTest.java'` | solo quedan V1, V5, su prueba y esta spec, más los rechazos documentados: 4 clases de prueba (`EnumeratedValuesTest`, `SessionConfigurationPolicyTest`, `InterviewSchemaMigrationTest`, `CreateSessionEndToEndTest`) y `PersonalizationCatalogEndToEndTest`, Postman y su README, `docs/errores.md`, el ADR 0010, la bitácora de IA (entrada 08 y su índice) y `CLAUDE.md` del repo |
| 10 | R1 a R14 | tabla regla → evidencia de `reglas-obligatorias.md` | `cumple`/`no aplica` justificada |

## 21. Hechos, inferencias y decisiones

| Afirmación | Tipo |
|---|---|
| F-1 a F-11, F-13, F-14 | Hecho (ruta en §3) |
| F-12 el tono se guarda como nombre de constante | **Inferencia** (comportamiento por defecto de Jackson); T0 la convierte en hecho |
| Un `UPDATE` previo al `ALTER` en una sola transacción es atómico | Hecho (PostgreSQL con DDL transaccional, Flyway por defecto) |
| `cameia-web` ya oculta «Motivador» | Hecho (prueba en su `develop`), no comprobado qué versión corre desplegada |
| Producción pudo guardar sesiones reales con `MOTIVADOR` | Inferencia (D-3 la confirma) |
| Sesiones antiguas → Profesional y neutro | Decisión humana (Paula, 2-nov) |

## 22. Puerta de listo

`LISTA PARA EJECUTAR`: CA y requisitos con prueba; plan de seguridad hecho; contraste con el código real hecho (§3, ensayo de tarjetas en `tasks.md`); integración con CM-216 y CM-212 resuelta (§14); línea base y lista de verificación definidas. D-1 y D-2 están cerradas; D-3 (contar las sesiones con Motivador en preproducción y producción antes de promover) queda pendiente de Paula o DevOps y no bloquea. Texto de HU-4.3 en el backlog v10.2: sigue nombrando Motivador (hoja HE-04, filas 38, 39 y 47) y lo corrige Paula en las historias de Backend, fuera de esta tarea. **Solo Paula aprueba.**
