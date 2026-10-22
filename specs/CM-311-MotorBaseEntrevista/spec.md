# CM-311 · Motor base de Entrevista: esquema, máquinas de estados y manejador de errores

| Dato | Valor |
|---|---|
| Tarea Jira | CM-311 (subtarea de CM-257, HT-01), Sprint 3, responsable Paula Andrea Muñoz Delgado |
| Historia | HT-01 «Motor de Entrevista base en cameia-entrevista» (backlog v8.3, hoja «Backlog», 7 criterios) |
| Repositorio | `cameia-entrevista`, base `origin/develop` (`bcc697d` al 20-oct) más la base de CM-312 |
| Depende de | CM-312 (Vela): siembra `ErrorCode`, `BusinessException`, `BusinessExceptionHandler` y la prueba del catálogo. Jira pide fusionar CM-311 después de CM-312. Mientras CM-312 no esté fusionada, esta rama se apila sobre la suya (§20) |
| Desbloquea | HU-4.3 (CM-263), HU-4.4 (CM-264), HU-4.5 (CM-265), HU-5.2 (CM-266), HT-03 (CM-209) |
| Estado | **LISTA PARA EJECUTAR**, aprobada por Paula el 20-oct y con una condición de entrada: la base de CM-312 (tarjeta T0). La comparación contra la spec de CM-312 está hecha (§20); la tarjeta T0 la confirma contra el código real |
| Aprobación | Solo Paula (P1). Vela recibe un resumen de una página y las correcciones a la spec de CM-312 (§20) |

## 1. Objetivo

Dejar en `cameia-entrevista` la base sobre la que se construye toda la entrevista:

1. Las tablas de la entrevista (sesión, pregunta, turno, intento de respuesta, retroalimentación, ejecución de IA y claves anti-duplicados), con las reglas en la base.
2. Las dos máquinas de estados (sesión y turno) en el dominio, con una prueba por transición.
3. La persistencia de la sesión y del turno, con control de edición simultánea.
4. El manejador de errores completo, con la identidad del Gateway.
5. Flyway, Testcontainers, ArchUnit y Postman, con la estructura alineada a Cuentas.

No agrega endpoints de negocio. Los endpoints llegan con HU-4.3, HU-4.4, HU-4.5 y HU-5.2.

## 2. Fuentes y vigencia

| Fuente | Qué aporta |
|---|---|
| Backlog v8.3, HT-01 | Los 7 criterios (sección 3) |
| Notas técnicas de HU-4.3, 4.4, 4.5, 5.2 y 5.12 | Las columnas (decisión Q4) |
| `informacion/P2/09102026_CAMEIA_maquinas_de_estados.drawio`, páginas 5 y 6 | Estados de sesión y turno; «sesión eliminada» es un borrado, no un estado |
| Decisiones Q1 a Q23 (17-oct) y P1 a P15 (17-oct) | `analisis/revisiones/17102026_preparacion-sprint3_entrevista.md` §7 y `17102026_preguntas-spec_CM-311_CM-201.md` §7 |
| `cameia-cuentas` `origin/develop` `13055eb` | Modelo de estructura (decisión D-27) |
| Spec, plan y tarjetas de CM-312 (Vela, 20-oct) | Lo que siembra CM-312 y lo que deja a CM-311 (§20) |

Ante una diferencia entre el diagrama (backlog v5) y HT-01 (v8.3), manda HT-01 (decisión P4).

## 3. Criterios de HT-01 y trazabilidad

HT-01 no tiene CA numerados; cada criterio se numera aquí como `HT01-Cn` con el texto literal del backlog.

| CA | Texto literal (backlog v8.3) | Requisitos | Pruebas (nombre exacto) | Postman |
|---|---|---|---|---|
| HT01-C1 | «tablas sesion, pregunta, turno, intento_respuesta, feedback_turno, ejecucion_ia y claves anti-duplicados, con restricciones con nombre, CHECK de cada estado e índices en cada clave foránea» | REQ-DB-01 a REQ-DB-14 | `InterviewSchemaMigrationTest.migration_shouldCreateAllTables_whenApplied`, `…_shouldNameEveryConstraint_whenApplied`, `…_shouldIndexEveryForeignKey_whenApplied`, `…_shouldRejectUnknownValue_whenCheckedColumn` (parametrizada), `ColumnLengthConsistencyTest` | No aplica: sin endpoint (§12) |
| HT01-C2 | «índices únicos parciales: una sesión CONFIGURADA y una EN_CURSO por Usuario» | REQ-DB-03, REQ-PER-03 | `InterviewSchemaMigrationTest.session_shouldRejectSecondConfigured_whenSameUser`, `…_shouldRejectSecondInProgress_whenSameUser`, `…_shouldAllowManyFinished_whenSameUser`, `InterviewSessionRepositoryAdapterTest.save_shouldThrowSessionAlreadyExists_whenSecondConfigured` | No aplica |
| HT01-C3 | «transiciones de sesión CONFIGURADA → EN_CURSO, CONFIGURADA → eliminada, EN_CURSO → ABANDONADA y EN_CURSO → EVALUANDO → FINALIZADA; cualquier otra se rechaza con un código propio y una prueba por transición» | REQ-SM-01 a REQ-SM-05 | `SessionStatusTest.transition_shouldBeAllowed_whenListed` (4 casos), `…_shouldBeRejected_whenNotListed` (21 casos), `InterviewSessionTest.requireDeletable_shouldPass_whenConfigured`, `…_shouldThrow_whenNotConfigured` (4 casos), `InterviewSessionTest.start_shouldThrowTransitionNotAllowed_whenInProgress` y una por método | No aplica |
| HT01-C4 | «transiciones de turno PENDING → PROCESSING → COMPLETED y PROCESSING → RETRYING → PROCESSING, iguales para texto y voz (el turno pasa a PROCESSING al recibir el audio)» | REQ-SM-06 a REQ-SM-08 | `TurnStatusTest.transition_shouldBeAllowed_whenListed` (4), `…_shouldBeRejected_whenNotListed` (12), `InterviewTurnTest` (una por método) | No aplica |
| HT01-C5 | «preguntas inmutables (texto, tipo y posición) con la sesión iniciada, comprobado en la base» | REQ-DB-05 | `InterviewSchemaMigrationTest.question_shouldRejectTextChange_whenSessionStarted`, `…_shouldRejectTypeChange_…`, `…_shouldRejectOrderChange_…`, `…_shouldAllowChange_whenSessionConfigured` | No aplica |
| HT01-C6 | «manejador de errores con code y requestId, identidad del Gateway (X-User-Id), Flyway, Testcontainers y prueba de arquitectura» | REQ-ERR-01 a REQ-ERR-15, REQ-ID-01 a REQ-ID-03, REQ-NF-01 a REQ-NF-04 | `BusinessExceptionHandlerTest` (una por código), `RequestIdFilterTest`, `FrameworkErrorsTest`, `IdentityHeaderTest`, `FirebaseUidTest`, `LayeredArchitectureTest`, `ErrorCodeDocumentationTest`, `ErrorCodeTest` | Colección `postman/` (§12): 4 peticiones |
| HT01-C7 | «intento_respuesta con origen_respuesta (TEXTO \| AUDIO) e idioma detectado, y ejecucion_ia con tipo_operacion TRANSCRIBIR» | REQ-DB-06, REQ-DB-08 | `InterviewSchemaMigrationTest.answer_shouldAcceptAudioWithDetectedLanguage`, `…_shouldRejectDetectedLanguage_whenTextOrigin`, `…_shouldAcceptTranscribeOperation` | No aplica |
| Terminado | «migraciones en main, transiciones inválidas rechazadas por pruebas, CI en verde» | REQ-NF-05 | `./mvnw.cmd clean verify` local con Docker; CI de la rama | — |

## 4. Requisitos funcionales (EARS)

### 4.1 Base de datos

Todo vive en la migración `V1__esquema_inicial_entrevista.sql`, dentro del esquema propio `microentrevista`, como Cuentas usa `microcuentas` (P16).

- **REQ-DB-01** El sistema debe crear `tipo_pregunta` con 4 filas fijas: `APERTURA` (`aplica_star` false), `TECNICA` (false), `CONDUCTUAL` (true) y `SITUACIONAL` (false).
- **REQ-DB-02** El sistema debe crear `sesion` con las columnas y restricciones de §5.1.
- **REQ-DB-03** El sistema debe impedir, con los índices únicos parciales `uq_sesion_configurada_por_usuario` y `uq_sesion_en_curso_por_usuario`, que un mismo `firebase_uid` tenga dos sesiones `CONFIGURADA` o dos sesiones `EN_CURSO`.
- **REQ-DB-04** El sistema debe exigir que una sesión `ABANDONADA` o `FINALIZADA` tenga `fecha_fin` y que ninguna otra la tenga (`ck_sesion_fecha_fin`).
- **REQ-DB-05** Cuando se intente cambiar `texto_original`, `tipo`, `orden` o `sesion_id` de una `pregunta`, o borrarla, mientras su sesión exista y no esté `CONFIGURADA`, el sistema debe rechazarlo en la base con los disparadores `trg_pregunta_inmutable` y `trg_pregunta_inmutable_borrado` (SQLSTATE `P0001`, restricción `trg_pregunta_inmutable`). CA-4.5.11 pide «conservar las 5 preguntas»: un borrado también las cambia. El borrado en cascada de la sesión sigue permitido, porque cuando la cascada llega a la pregunta la fila de la sesión ya no existe.
- **REQ-DB-06** El sistema debe crear `intento_respuesta` con `origen_respuesta` (`TEXTO` o `AUDIO`) e `idioma_detectado` (2 a 10 caracteres, nulo cuando el origen es `TEXTO`).
- **REQ-DB-07** Cuando se intente modificar una fila de `intento_respuesta`, el sistema debe rechazarlo con el disparador `trg_intento_append_only` (P11). El borrado en cascada desde la sesión sigue permitido.
- **REQ-DB-08** El sistema debe crear `ejecucion_ia` con `tipo_operacion` en `PLANIFICAR`, `EVALUAR` o `TRANSCRIBIR`, y `estado` en `EN_CURSO`, `EXITOSA` o `FALLIDA` (P9). Para la procedencia que exige IA-01 (P17), cada ejecución guarda `modelo` (obligatorio), `version_prompt` (obligatoria al planificar y evaluar; nula al transcribir) y `error_codigo` (presente solo si `FALLIDA`). Para el consumo (P18) guarda además, opcionales, `proveedor` y los tokens de entrada y salida.
- **REQ-DB-13** El sistema debe enlazar cada `pregunta` y cada `feedback_turno` con la `ejecucion_ia` que los produjo (`ejecucion_ia_id`, obligatorio), para saber de qué modelo y de qué versión del prompt sale cada salida de la IA (P17).
- **REQ-DB-09** El sistema debe exigir, para cada turno, una sola fila de `intento_respuesta` con `es_definitiva = true` (`uq_intento_definitivo_por_turno`).
- **REQ-DB-10** El sistema debe crear `feedback_turno` con una fila como máximo por turno y la retroalimentación en JSON (P8).
- **REQ-DB-11** El sistema debe crear `clave_idempotencia`, única por (`firebase_uid`, `operacion`, `clave`) (P10).
- **REQ-DB-12** Cuando se borre una `sesion`, el sistema debe borrar sus filas hijas (`ON DELETE CASCADE`).
- **REQ-DB-13** La migración debe dejar un `COMMENT ON` en cada tabla y en cada columna de estado o de valores cerrados (sección 5.4), como la migración inicial de Cuentas. Una prueba falla si falta alguno.
- **REQ-DB-14** El sistema debe crear el índice parcial `ix_sesion_en_curso_ultima_actividad` sobre `sesion (fecha_ultima_actividad) WHERE estado = 'EN_CURSO'` para buscar sesiones en curso inactivas sin recorrer la tabla (D-3 A).

### 4.2 Máquinas de estados (dominio)

- **REQ-SM-01** El sistema debe permitir solo estas transiciones de sesión: `CONFIGURADA → EN_CURSO`, `EN_CURSO → ABANDONADA`, `EN_CURSO → EVALUANDO` y `EVALUANDO → FINALIZADA`.
- **REQ-SM-02** Cuando se pida una transición de sesión que no está en REQ-SM-01, incluida la de un estado a sí mismo, el sistema debe lanzar `SessionTransitionNotAllowedException` (`SESSION_TRANSITION_NOT_ALLOWED`, 409) y no debe cambiar la sesión.
- **REQ-SM-03** Cuando se pida borrar una sesión, el sistema debe permitirlo solo si está `CONFIGURADA`. En otro estado debe lanzar `SessionTransitionNotAllowedException` (CA-4.5.18).
- **REQ-SM-04** Cuando una sesión pase a `ABANDONADA` o `FINALIZADA`, el sistema debe registrar `fecha_fin` con el reloj inyectado, en UTC.
- **REQ-SM-05** Cuando una sesión pase a `EN_CURSO`, el sistema debe registrar `fecha_ultima_actividad` con el reloj inyectado.
- **REQ-SM-06** El sistema debe permitir solo estas transiciones de turno: `PENDING → PROCESSING`, `PROCESSING → COMPLETED`, `PROCESSING → RETRYING` y `RETRYING → PROCESSING` (P4). No existe `FAILED` (Q23).
- **REQ-SM-07** Cuando se pida otra transición de turno, el sistema debe lanzar `TurnTransitionNotAllowedException` (`TURN_TRANSITION_NOT_ALLOWED`, 409) y no debe cambiar el turno.
- **REQ-SM-08** Las transiciones del turno no dependen de la forma de respuesta: el turno no tiene campo de forma. El origen está en `intento_respuesta`.

### 4.3 Persistencia

- **REQ-PER-01** El sistema debe guardar y leer la sesión y el turno a través de los puertos `InterviewSessionRepository` e `InterviewTurnRepository`, con entidades JPA distintas del modelo de dominio y mapeo a mano.
- **REQ-PER-02** Cuando dos escrituras compitan por la misma sesión o el mismo turno, el sistema debe aceptar la primera y traducir la segunda (`ObjectOptimisticLockingFailureException`) a `SessionConflictException` (`SESSION_CONFLICT`, 409), sin cambiar la fila (Q10).
- **REQ-PER-06** Como cada `save` hace `saveAndFlush`, el orden de las llamadas es el orden en la base. Cuando una misma transacción abandone la sesión `EN_CURSO` anterior y pase la nueva a `EN_CURSO` (HU-4.5, CA-4.5.1), debe guardar **primero** la anterior: los índices únicos parciales no se pueden diferir al final de la transacción. CM-311 lo deja probado para que HU-4.5 lo use.
- **REQ-PER-03** Cuando guardar una sesión viole `uq_sesion_configurada_por_usuario` o `uq_sesion_en_curso_por_usuario`, el sistema debe lanzar `SessionAlreadyExistsException` (`SESSION_ALREADY_EXISTS`, 409).
- **REQ-PER-04** El sistema debe guardar y buscar claves anti-duplicados a través del puerto `IdempotencyKeyRepository`. Si se viola `uq_clave_idempotencia_usuario_operacion_clave`, debe lanzar `IdempotencyKeyAlreadyExistsException` (`IDEMPOTENCY_KEY_ALREADY_EXISTS`, 409). Las reglas de uso (alcance, caducidad, mismo cuerpo) son de HU-4.4 (Q11).
- **REQ-PER-05** Cuando se pida borrar una sesión, el adaptador debe borrar la fila solo si su estado sigue siendo `CONFIGURADA` (`DELETE … WHERE id = ? AND estado = 'CONFIGURADA'`). Si no borra ninguna fila, debe lanzar `SessionTransitionNotAllowedException`.

### 4.4 Identidad

- **REQ-ID-01** El sistema debe tomar la identidad solo del encabezado `X-User-Id` que pone el Gateway, a través del objeto de valor `FirebaseUid`.
- **REQ-ID-02** Si `X-User-Id` falta, viene vacío, solo con espacios, con más de 128 caracteres o con caracteres de control, el sistema debe responder 401 `IDENTITY_REQUIRED` con el mensaje «Tu sesión expiró. Inicia sesión de nuevo.» (P2). En el registro, el valor se anota como `-`.
- **REQ-ID-03** El sistema debe ignorar cualquier identidad que venga en el cuerpo o en la ruta (RT-03-CA04). En CM-311 se demuestra con el controlador de prueba.

### 4.5 Errores

- **REQ-ERR-01** Toda respuesta de error debe ser `application/problem+json;charset=UTF-8` con `type`, `title`, `status`, `detail`, `instance`, `code` y `requestId`, sin traza, SQL, nombre de clase ni mensaje de librería.
- **REQ-ERR-02** `requestId` es el `X-Request-Id` recibido si cumple `^[A-Za-z0-9._-]{1,64}$`; si no, un UUID nuevo. Va en el cuerpo y en el encabezado `X-Request-Id` de la respuesta. Viene sembrado por CM-312 y se conserva.
- **REQ-ERR-03** Cuando falle la validación de un cuerpo (`MethodArgumentNotValidException`), el sistema debe responder 422 `VALIDATION_FAILED` con `detail` «Revisa los campos marcados.» y un elemento por campo en `errors[{field, code, message}]`, todos a la vez y solo el primero de cada campo.
- **REQ-ERR-04** Cuando el cuerpo no se pueda leer (`HttpMessageNotReadableException`), el sistema debe responder 422 `REQUEST_BODY_INVALID_FORMAT` con «Revisa el formato de los datos enviados.» (P3). El mensaje de Jackson no se registra.
- **REQ-ERR-05** Cuando falte otro encabezado o parámetro obligatorio, o un valor de la ruta no tenga el tipo esperado, el sistema debe responder 422 `REQUEST_INVALID_VALUE` con «Revisa los datos enviados.».
- **REQ-ERR-06** Ruta inexistente → 404 `ROUTE_NOT_FOUND` «No existe la ruta solicitada.». Método no admitido → 405 `METHOD_NOT_ALLOWED` «Método no permitido.», con el encabezado `Allow`. Tipo de contenido no admitido → 415 `MEDIA_TYPE_NOT_ALLOWED` «Tipo de contenido no admitido.». `Accept` no atendible → 406 `MEDIA_TYPE_NOT_ACCEPTABLE` «Tipo de respuesta no admitido.».
- **REQ-ERR-07** Cuando la base no responda (`CannotCreateTransactionException`, `DataAccessResourceFailureException` o `CannotGetJdbcConnectionException`), el sistema debe responder 503 `DATABASE_UNAVAILABLE` con «Ocurrió un error. Inténtalo de nuevo.» y registrar en `ERROR` con traza (D-4).
- **REQ-ERR-08** Cada excepción de negocio debe responder con el estado y el mensaje de su código según la tabla de §6, a través de un manejador común para `BusinessException`. Los manejadores de CM-312 se conservan.
- **REQ-ERR-09** Una violación de restricción sin traducción propia debe responder 500 `INTERNAL_ERROR` y registrar solo el nombre de la restricción y el SQLSTATE, sin valores de columna.
- **REQ-ERR-10** Cualquier otra excepción debe responder 500 `INTERNAL_ERROR` con «Ocurrió un error. Inténtalo de nuevo.» y registrar en `ERROR` con traza.
- **REQ-ERR-11** Cada 4xx se registra una vez en `WARN`, sin traza, con `status`, `code`, `requestId` y `firebaseUid` (o `-`). Cada 5xx se registra en `ERROR` con traza. Nunca se registran cuerpos, textos de respuestas, prompts ni valores de encabezados que no sean `X-User-Id` saneado.
- **REQ-ERR-12** Todo valor de `ErrorCode` debe tener su fila en `docs/errores.md` (prueba sembrada por CM-312) y su prueba (`ErrorCodeTest`).
- **REQ-ERR-13** Cuando llegue cualquier petición, un filtro de máxima precedencia (`RequestIdFilter`) debe resolver el `requestId` con la regla de REQ-ERR-02, guardarlo donde el manejador de CM-312 lo lee, ponerlo en el `MDC` con la clave `requestId`, devolverlo en el encabezado `X-Request-Id` de la respuesta (también en las respuestas 2xx) y quitarlo del `MDC` al terminar, incluso si la petición falla (decisión D-2, P19).
- **REQ-ERR-14** El patrón de log debe incluir el `requestId` en cada línea: `logging.pattern.level=%5p [%X{requestId:-}]`.
- **REQ-ERR-15** Ningún registro de log debe contener contraseñas, tokens, correos, nombres, CV ni cuerpos de petición. Hoy `src/main` no tiene ninguna llamada a log (comprobado con `git grep -nE "log(ger)?\.(trace|debug|info|warn|error)\(" origin/develop -- src/main`); los registros del manejador cumplen REQ-ERR-11.

### 4.6 No funcionales

- **REQ-NF-01** `spring.jpa.hibernate.ddl-auto=validate` en todos los perfiles; el esquema lo instala solo Flyway, también en local.
- **REQ-NF-02** `LayeredArchitectureTest` con las tres reglas de Cuentas, más una cuarta: `domain` no depende de `org.springframework.ai..` ni de `com.google..`.
- **REQ-NF-03** Toda prueba con base usa PostgreSQL 16 real por Testcontainers (`postgres:16-alpine`) con `@ServiceConnection`; ninguna usa H2. Las pruebas de contexto completo se omiten sin Docker (`disabledWithoutDocker = true`), como en Cuentas.
- **REQ-NF-04** El dominio se prueba sin Spring, con reloj fijo (`Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneOffset.UTC)`).
- **REQ-NF-05** Cobertura de JaCoCo ≥ 90 % de líneas y de ramas en cada clase nueva o modificada.

## 5. Modelo de datos (V1)

Convenciones: tablas y columnas en español como el backlog (Q7); restricciones `pk_`, `fk_`, `uq_`, `ck_`, `ix_`, `trg_`, funciones `fn_`; instantes `timestamptz` en UTC; identificadores `uuid` generados por la aplicación.

### 5.1 `sesion`

| Columna | Tipo | Nulo | Regla | Origen |
|---|---|---|---|---|
| `id` | `uuid` | no | `pk_sesion` | HU-4.4 (`session_id`) |
| `firebase_uid` | `varchar(128)` | no | `ck_sesion_firebase_uid` (`char_length(btrim(firebase_uid)) > 0`) | Igual que Perfil |
| `perfil_id` | `uuid` | no | Sin clave foránea: es de Perfil (MAN-03) | HU-4.4 |
| `rol_objetivo_id` | `uuid` | no | Sin clave foránea: catálogo de Perfil | HU-4.4 (L-12) |
| `oferta_laboral` | `varchar(3000)` | sí | `ck_sesion_oferta_laboral` (nula o `char_length(btrim(…))` entre 1 y 3000) | HU-4.4 |
| `contexto_perfil_snapshot` | `jsonb` | no | `ck_sesion_contexto_perfil_snapshot` (`jsonb_typeof = 'object'`) | HU-4.4 |
| `modalidad` | `varchar(20)` | no | `ck_sesion_modalidad` IN (`SIMULACION`, `ENTRENO`) (P12 B) | HU-4.3 |
| `tono` | `varchar(20)` | no | `ck_sesion_tono` IN (`PROFESIONAL_NEUTRO`, `CALIDO`, `ESTRICTO`, `MOTIVADOR`) | HU-4.3 |
| `forma_respuesta` | `varchar(10)` | no | `ck_sesion_forma_respuesta` IN (`AUDIO`, `TEXTO`) | HU-4.3 |
| `idioma` | `varchar(5)` | no | `ck_sesion_idioma` IN (`es-CO`, `en`) | HU-4.3 |
| `estado` | `varchar(20)` | no, `DEFAULT 'CONFIGURADA'` | `ck_sesion_estado` IN (`CONFIGURADA`, `EN_CURSO`, `EVALUANDO`, `FINALIZADA`, `ABANDONADA`) | drawio pág. 5 |
| `reintentos_planificacion` | `smallint` | no, `DEFAULT 0` | `ck_sesion_reintentos_planificacion` BETWEEN 0 AND 2 | HU-4.5 (CA-4.5.15, 4.5.19) |
| `fecha_creacion` | `timestamptz` | no | — | — |
| `fecha_actualizacion` | `timestamptz` | no | La fija el código con el reloj inyectado en cada cambio (D-5); la base no tiene disparador para ella | — |
| `fecha_ultima_actividad` | `timestamptz` | sí | — | HU-4.5, HU-5.7 |
| `fecha_fin` | `timestamptz` | sí | `ck_sesion_fecha_fin`: `(estado IN ('ABANDONADA','FINALIZADA')) = (fecha_fin IS NOT NULL)` | HU-4.5, HU-5.5 |
| `version` | `bigint` | no, `DEFAULT 0` | `@Version` (Q10) | — |

Índices: `uq_sesion_configurada_por_usuario` (`firebase_uid`) `WHERE estado = 'CONFIGURADA'`; `uq_sesion_en_curso_por_usuario` (`firebase_uid`) `WHERE estado = 'EN_CURSO'`; `ix_sesion_firebase_uid` (`firebase_uid`); `ix_sesion_en_curso_ultima_actividad` (`fecha_ultima_actividad`) `WHERE estado = 'EN_CURSO'` (D-3 A).

#### 5.1.1 Revisión de índices frente a las consultas previstas

| Consulta prevista | Quién | Índice que la atiende |
|---|---|---|
| Sesión configurada o en curso del usuario | HU-4.4, HU-4.5 | `uq_sesion_configurada_por_usuario`, `uq_sesion_en_curso_por_usuario` |
| Todas las sesiones de un usuario | HU-4.x | `ix_sesion_firebase_uid` |
| Preguntas de una sesión en orden | HU-4.5, HU-5.2 | `uq_pregunta_sesion_orden` |
| Turno de una pregunta | HU-5.2 | `uq_turno_pregunta` |
| Respuestas de un turno; la definitiva | HU-5.2, HU-5.4 | `ix_intento_respuesta_turno_id`, `uq_intento_definitivo_por_turno` |
| Llamadas a la IA de una sesión o de un turno | HU-4.5, HU-5.2 | `ix_ejecucion_ia_sesion_id`, `ix_ejecucion_ia_turno_id` |
| Sesiones en curso sin actividad reciente, para abandonarlas | HU-5.7 | `ix_sesion_en_curso_ultima_actividad` (índice parcial, D-3 A) |

Un índice por estado no se crea: con una sola sesión en curso por usuario, el filtro por `estado` solo se usa junto con el usuario. La consulta de sesiones inactivas recorre todos los usuarios, y por eso es la única que lo pediría.

### 5.2 Demás tablas

| Tabla | Columnas (tipo, nulo) | Restricciones e índices |
|---|---|---|
| `tipo_pregunta` | `codigo varchar(20)` no; `aplica_star boolean` no | `pk_tipo_pregunta` (`codigo`); `ck_tipo_pregunta_codigo` IN (`APERTURA`, `TECNICA`, `CONDUCTUAL`, `SITUACIONAL`) |
| `pregunta` | `id uuid`; `sesion_id uuid` no; `orden smallint` no; `tipo varchar(20)` no; `texto_original varchar(1000)` no; `ejecucion_ia_id uuid` no; `fecha_creacion timestamptz` no | `pk_pregunta`; `fk_pregunta_ejecucion_ia` ON DELETE CASCADE; `ix_pregunta_ejecucion_ia_id`; `fk_pregunta_sesion` ON DELETE CASCADE; `fk_pregunta_tipo_pregunta` ON DELETE RESTRICT; `ck_pregunta_orden` BETWEEN 1 AND 5; `ck_pregunta_texto_original` (`char_length(btrim(…))` entre 1 y 1000, **provisional hasta el spike HU-4.1, P7**); `uq_pregunta_sesion_orden` (`sesion_id`, `orden`), que también sirve de índice de `sesion_id`; `ix_pregunta_tipo` (`tipo`); disparador `trg_pregunta_inmutable` |
| `turno` | `id uuid`; `pregunta_id uuid` no; `estado varchar(20)` no, `DEFAULT 'PENDING'`; `fecha_creacion`, `fecha_actualizacion timestamptz` no; `version bigint` no, `DEFAULT 0` | `pk_turno`; `fk_turno_pregunta` ON DELETE CASCADE; `uq_turno_pregunta` (`pregunta_id`), también índice; `ck_turno_estado` IN (`PENDING`, `PROCESSING`, `COMPLETED`, `RETRYING`) |
| `intento_respuesta` | `id uuid`; `turno_id uuid` no; `tipo_interaccion varchar(20)` no; `es_definitiva boolean` no; `origen_respuesta varchar(10)` no; `respuesta_texto varchar(4000)` no; `pregunta_mostrada varchar(1000)` no; `idioma_detectado varchar(10)` sí; `fecha_creacion timestamptz` no | `pk_intento_respuesta`; `fk_intento_respuesta_turno` ON DELETE CASCADE; `ix_intento_respuesta_turno_id`; `ck_intento_respuesta_tipo_interaccion` IN (`INICIAL`); `ck_intento_respuesta_origen_respuesta` IN (`TEXTO`, `AUDIO`); `ck_intento_respuesta_respuesta_texto` (1 a 4000 sin contar espacios de los extremos); `ck_intento_respuesta_pregunta_mostrada` (1 a 1000, provisional como `texto_original`); `ck_intento_respuesta_idioma_detectado` (nulo o 2 a 10); `ck_intento_respuesta_idioma_origen` (`origen_respuesta = 'AUDIO' OR idioma_detectado IS NULL`); `uq_intento_definitivo_por_turno` (`turno_id`) `WHERE es_definitiva`; disparador `trg_intento_append_only` |
| `feedback_turno` | `id uuid`; `turno_id uuid` no; `feedback jsonb` no; `feedback_star jsonb` sí; `ejecucion_ia_id uuid` no; `fecha_creacion timestamptz` no | `pk_feedback_turno`; `fk_feedback_turno_ejecucion_ia` ON DELETE CASCADE; `ix_feedback_turno_ejecucion_ia_id`; `fk_feedback_turno_turno` ON DELETE CASCADE; `uq_feedback_turno_turno` (`turno_id`); `ck_feedback_turno_feedback` (`jsonb_typeof = 'object'`); `ck_feedback_turno_feedback_star` (nulo u `object`) |
| `ejecucion_ia` | `id uuid`; `sesion_id uuid` no; `turno_id uuid` sí; `tipo_operacion varchar(20)` no; `numero_intento smallint` no; `idempotency_key uuid` no; `estado varchar(20)` no; `modelo varchar(120)` no; `version_prompt varchar(80)` sí; `error_codigo varchar(80)` sí; `proveedor varchar(60)` sí; `tokens_entrada bigint` sí; `tokens_salida bigint` sí; `fecha_inicio timestamptz` no; `fecha_fin timestamptz` sí | `pk_ejecucion_ia`; `fk_ejecucion_ia_sesion` y `fk_ejecucion_ia_turno` ON DELETE CASCADE; `ix_ejecucion_ia_sesion_id`; `ix_ejecucion_ia_turno_id`; `uq_ejecucion_ia_idempotency_key`; `ck_ejecucion_ia_tipo_operacion` IN (`PLANIFICAR`, `EVALUAR`, `TRANSCRIBIR`); `ck_ejecucion_ia_numero_intento` BETWEEN 1 AND 3; `ck_ejecucion_ia_estado` IN (`EN_CURSO`, `EXITOSA`, `FALLIDA`); `ck_ejecucion_ia_fecha_fin` (`(estado = 'EN_CURSO') = (fecha_fin IS NULL)`); `ck_ejecucion_ia_turno` (`(tipo_operacion = 'PLANIFICAR') = (turno_id IS NULL)`); `ck_ejecucion_ia_modelo` (`char_length(btrim(modelo)) > 0`); `ck_ejecucion_ia_version_prompt` (`(tipo_operacion = 'TRANSCRIBIR') = (version_prompt IS NULL)`); `ck_ejecucion_ia_error_codigo` (`(estado = 'FALLIDA') = (error_codigo IS NOT NULL)`); `ck_ejecucion_ia_proveedor` (nulo o `char_length(btrim(proveedor)) > 0`); `ck_ejecucion_ia_tokens_entrada` y `ck_ejecucion_ia_tokens_salida` (nulos o `>= 0`) |
| `clave_idempotencia` | `id uuid`; `firebase_uid varchar(128)` no; `operacion varchar(40)` no; `clave varchar(64)` no; `huella_peticion char(64)` no; `estado_respuesta smallint` no; `cuerpo_respuesta jsonb` no; `fecha_creacion timestamptz` no | `pk_clave_idempotencia`; `uq_clave_idempotencia_usuario_operacion_clave`; `ck_clave_idempotencia_clave` (1 a 64, `^[A-Za-z0-9._-]+$`); `ck_clave_idempotencia_operacion` (no vacía; HU-4.4 fija los valores con su migración); `ck_clave_idempotencia_huella_peticion` (`^[0-9a-f]{64}$`, SHA-256 en hexadecimal); `ck_clave_idempotencia_estado_respuesta` BETWEEN 200 AND 599 |

### 5.3 Disparadores

```sql
-- Rechaza cambiar el texto, el tipo, la posición o la sesión de una pregunta cuando su sesión ya no está
-- CONFIGURADA. Las preguntas se insertan en la misma transacción que pasa la sesión a EN_CURSO (HU-4.5),
-- así que con la sesión iniciada quedan fijas para siempre.
CREATE FUNCTION microentrevista.fn_rechazar_cambio_pregunta() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.texto_original, NEW.tipo, NEW.orden, NEW.sesion_id)
           IS DISTINCT FROM (OLD.texto_original, OLD.tipo, OLD.orden, OLD.sesion_id)
       AND (SELECT s.estado FROM microentrevista.sesion s WHERE s.id = OLD.sesion_id) <> 'CONFIGURADA' THEN
        RAISE EXCEPTION 'pregunta inmutable' USING ERRCODE = 'P0001', CONSTRAINT = 'trg_pregunta_inmutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_pregunta_inmutable BEFORE UPDATE ON microentrevista.pregunta
    FOR EACH ROW EXECUTE FUNCTION microentrevista.fn_rechazar_cambio_pregunta();

-- Tampoco se borra una pregunta de una sesión iniciada. En la cascada desde la sesión, la fila de la sesión
-- ya no existe y la consulta no devuelve nada: el borrado de la sesión completa sigue permitido.
CREATE FUNCTION microentrevista.fn_rechazar_borrado_pregunta() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT s.estado FROM microentrevista.sesion s WHERE s.id = OLD.sesion_id) <> 'CONFIGURADA' THEN
        RAISE EXCEPTION 'pregunta inmutable' USING ERRCODE = 'P0001', CONSTRAINT = 'trg_pregunta_inmutable';
    END IF;
    RETURN OLD;
END $$;
CREATE TRIGGER trg_pregunta_inmutable_borrado BEFORE DELETE ON microentrevista.pregunta
    FOR EACH ROW EXECUTE FUNCTION microentrevista.fn_rechazar_borrado_pregunta();

-- Un intento de respuesta solo se inserta: es la evidencia de lo que la persona respondió.
CREATE FUNCTION microentrevista.fn_rechazar_cambio_intento() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'intento de respuesta de solo insercion' USING ERRCODE = 'P0001', CONSTRAINT = 'trg_intento_append_only';
END $$;
CREATE TRIGGER trg_intento_append_only BEFORE UPDATE ON microentrevista.intento_respuesta
    FOR EACH ROW EXECUTE FUNCTION microentrevista.fn_rechazar_cambio_intento();
```

Los dos disparadores no tienen todavía un código de error para el cliente: ningún adaptador de CM-311 escribe preguntas ni intentos. Los adaptadores que los escriban (HU-4.5 y HU-5.2) traducen la restricción a su código. Mientras tanto, si alguien llegara a dispararlos, el resultado sería REQ-ERR-09 (500, con el nombre de la restricción en el log). La prueba de esquema demuestra que la base rechaza el cambio.

### 5.4 Comentarios en la base (REQ-DB-13)

Al final de `V1`, en español sin tildes (como `V1` de Cuentas), un `COMMENT ON TABLE` por tabla y un `COMMENT ON COLUMN` por cada columna de estado o de valores cerrados.

| Objeto | Texto |
|---|---|
| `sesion` | Sesion de practica o simulacion de entrevista de un usuario. La identidad es el firebase_uid que pone el Gateway; perfil_id y rol_objetivo_id son de Perfil y no tienen clave foranea. |
| `sesion.estado` | CONFIGURADA al crearla, EN_CURSO al iniciarla, EVALUANDO al terminar las preguntas, FINALIZADA con la evaluacion lista, ABANDONADA si se deja. Una sesion configurada se elimina con DELETE: no tiene estado propio. |
| `sesion.version` | Contador de control de concurrencia optimista. |
| `sesion.contexto_perfil_snapshot` | Copia minima del perfil al crear la sesion; evita consultar Perfil en cada turno. |
| `pregunta` | Pregunta de una sesion. Texto, tipo y orden no cambian una vez iniciada la sesion (disparador trg_pregunta_inmutable). |
| `turno` | Avance de una pregunta; la misma maquina de estados sirve para texto y voz. |
| `turno.estado` | PENDING al crearse, PROCESSING al recibir la respuesta, COMPLETED al evaluarse, RETRYING si el modelo falla. |
| `intento_respuesta` | Respuesta del usuario a un turno. Solo se inserta: no admite cambios posteriores (disparador trg_intento_append_only). |
| `feedback_turno` | Retroalimentacion de un turno. Se guarda, pero no se expone hasta que la sesion finaliza. |
| `ejecucion_ia` | Cada llamada al modelo: operacion, intento, modelo, version del prompt, estado y causa del fallo. Base de la medicion de procedencia de la IA. |
| `clave_idempotencia` | Clave anti-duplicados por usuario y operacion, con la huella de la peticion y la respuesta guardada. |
| `tipo_pregunta` | Catalogo de tipos de pregunta. |
| Cada otra columna con `CHECK ... IN (...)` (`modalidad`, `tono`, `forma_respuesta`, `idioma`, `tipo`, `tipo_interaccion`, `origen_respuesta`, `tipo_operacion`, `estado` de `ejecucion_ia`) | `Valores: ` seguido de los valores del `CHECK`, separados por coma y terminado en punto. |

## 6. Catálogo de errores

Los códigos marcados «CM-312» los siembra CM-312 y no se modifican ni se renombran. Los demás los define esta spec y se aprueban con ella (Q12).

| Código | HTTP | Título | Mensaje (`detail`) | Excepción o disparador | Prueba |
|---|---|---|---|---|---|
| `INTERNAL_ERROR` (CM-312) | 500 | Error interno | «Ocurrió un error. Inténtalo de nuevo.» | cualquier otra | `BusinessExceptionHandlerTest.unexpected_shouldReturn500Generic_whenAnyException` |
| `LLM_UNAVAILABLE` (CM-312) | 503 | Servicio no disponible | «Ocurrió un error. Inténtalo de nuevo.» | de CM-312 | las de CM-312 |
| `LLM_LIMIT_REACHED` (CM-312) | 503 | Servicio no disponible | «Ocurrió un error. Inténtalo de nuevo.» | de CM-312 | las de CM-312 |
| `VALIDATION_FAILED` | 422 | Datos no válidos | «Revisa los campos marcados.» + `errors` | `MethodArgumentNotValidException` | `…validation_shouldReturn422WithEveryField_whenTwoFieldsInvalid` |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | Datos no válidos | «Revisa el formato de los datos enviados.» | `HttpMessageNotReadableException` | `…unreadableBody_shouldReturn422_whenJsonMalformed` |
| `REQUEST_INVALID_VALUE` | 422 | Datos no válidos | «Revisa los datos enviados.» | `MissingRequestHeaderException` (otro encabezado), `MethodArgumentTypeMismatchException` | `…missingHeader_shouldReturn422_whenNotIdentity`, `…typeMismatch_shouldReturn422_whenPathIsNotUuid` |
| `IDENTITY_REQUIRED` | 401 | Identidad requerida | «Tu sesión expiró. Inicia sesión de nuevo.» | `IdentityRequiredException`; `MissingRequestHeaderException` de `X-User-Id` | `IdentityHeaderTest` (6 casos) |
| `ROUTE_NOT_FOUND` | 404 | Ruta no encontrada | «No existe la ruta solicitada.» | `NoResourceFoundException` | `FrameworkErrorsTest.unknownRoute_shouldReturn404WithCode` |
| `METHOD_NOT_ALLOWED` | 405 | Método no permitido | «Método no permitido.» | `HttpRequestMethodNotSupportedException` | `FrameworkErrorsTest.wrongMethod_shouldReturn405WithAllow` |
| `MEDIA_TYPE_NOT_ALLOWED` | 415 | Tipo de contenido no admitido | «Tipo de contenido no admitido.» | `HttpMediaTypeNotSupportedException` | `…unsupportedMediaType_shouldReturn415` |
| `MEDIA_TYPE_NOT_ACCEPTABLE` | 406 | Tipo de respuesta no admitido | «Tipo de respuesta no admitido.» | `HttpMediaTypeNotAcceptableException` | `FrameworkErrorsTest.xmlAccept_shouldReturn406WithCode` |
| `DATABASE_UNAVAILABLE` | 503 | Servicio no disponible | «Ocurrió un error. Inténtalo de nuevo.» | `CannotCreateTransactionException`, `DataAccessResourceFailureException`, `CannotGetJdbcConnectionException` | `…databaseDown_shouldReturn503` (3 casos) |
| `SESSION_TRANSITION_NOT_ALLOWED` | 409 | Acción no permitida | «Esta entrevista ya no admite esa acción.» | `SessionTransitionNotAllowedException` | `…businessException_shouldUseCatalog` (parametrizada) |
| `TURN_TRANSITION_NOT_ALLOWED` | 409 | Acción no permitida | «Esta pregunta ya no admite esa acción.» | `TurnTransitionNotAllowedException` | ídem |
| `SESSION_CONFLICT` | 409 | Cambio simultáneo | «La entrevista cambió mientras se procesaba tu solicitud. Inténtalo de nuevo.» | `SessionConflictException` | ídem y `InterviewSessionRepositoryAdapterTest.save_shouldThrowConflict_whenStaleVersion` |
| `SESSION_ALREADY_EXISTS` | 409 | Entrevista existente | «Ya tienes una entrevista en preparación o en curso. Inténtalo de nuevo.» | `SessionAlreadyExistsException` | ídem y `…save_shouldThrowSessionAlreadyExists_whenSecondConfigured` |
| `IDEMPOTENCY_KEY_ALREADY_EXISTS` | 409 | Solicitud repetida | «Esta solicitud ya se registró.» | `IdempotencyKeyAlreadyExistsException` | ídem y `IdempotencyKeyRepositoryAdapterTest.save_shouldThrow_whenSameUserOperationAndKey` |

Los mensajes de los cinco códigos de dominio no están en el backlog. Los fija Backend en esta spec; HU-4.4, HU-4.5 y HU-5.2 pueden fijar un texto propio en su CA, y entonces manda el CA.

## 7. Matriz de validación por campo

CM-311 no tiene campos de entrada propios: solo el encabezado `X-User-Id`. Los campos de la sesión los valida HU-4.4.

| Entrada | Caso | Valor literal | Resultado | Prueba |
|---|---|---|---|---|
| `X-User-Id` | ausente | (sin encabezado) | 401 `IDENTITY_REQUIRED` | `IdentityHeaderTest.identity_shouldReturn401_whenHeaderMissing` |
| | vacío | `""` | 401 | `…_whenHeaderEmpty` |
| | solo espacios | `"   "` | 401 | `…_whenHeaderBlank` |
| | 128 caracteres | `"u".repeat(128)` | 200 en el controlador de prueba | `…_shouldAccept_when128Chars` |
| | 129 caracteres | `"u".repeat(129)` | 401 | `…_whenHeaderTooLong` |
| | carácter de control | `"uid\u0007"` | 401 | `…_whenHeaderHasControlChar` |
| | identidad en el cuerpo distinta | cuerpo `{"firebaseUid":"otro"}`, encabezado `uid-a` | se usa `uid-a` | `…_shouldIgnoreBodyIdentity` |
| `X-Request-Id` | válido | `abc-123` | se devuelve igual | `RequestIdTest.requestId_shouldEchoHeader_whenValid` |
| | inválido | `"<script>"` | UUID nuevo | `…_shouldGenerate_whenInvalid` |
| | 65 caracteres | `"a".repeat(65)` | UUID nuevo | `…_shouldGenerate_whenTooLong` |
| Cuerpo (controlador de prueba) | JSON mal formado | `{"name":` | 422 `REQUEST_BODY_INVALID_FORMAT` | `BusinessExceptionHandlerTest.unreadableBody_…` |
| | dos campos inválidos | `{}` con dos `@NotBlank` | 422 con 2 elementos en `errors` | `…validation_…` |
| | `Content-Type: text/plain` | `hola` | 415 | `…unsupportedMediaType_…` |

## 8. Casos borde (R5)

| Grupo | Caso | Cómo se cubre |
|---|---|---|
| Presencia | Columnas obligatorias nulas | `InterviewSchemaMigrationTest.notNull_shouldReject_whenColumnMissing` (parametrizada por tabla y columna) |
| Texto | `texto_original` de 1000 y 1001 caracteres, con tildes y emoji (`"é".repeat(1000)`, `"😀".repeat(1000)`) | `…question_shouldAccept1000CodePoints`, `…_shouldReject1001CodePoints` (`char_length` cuenta puntos de código) |
| Texto | `respuesta_texto` de 4000 y 4001; solo espacios `"   "` | `…answer_shouldAccept4000`, `…_shouldReject4001`, `…_shouldRejectBlank` |
| Texto | `oferta_laboral` de 3000 y 3001; `"   "` | `…session_shouldAcceptOffer3000`, `…_shouldRejectOffer3001`, `…_shouldRejectBlankOffer` |
| Números | `orden` 0, 1, 5 y 6; `numero_intento` 0, 1, 3 y 4; `reintentos_planificacion` -1, 0, 2 y 3 | Parametrizadas en `InterviewSchemaMigrationTest` |
| Fechas | `fecha_fin` en `ABANDONADA` sin valor; en `EN_CURSO` con valor | `…session_shouldRejectFinishedWithoutEndDate`, `…_shouldRejectInProgressWithEndDate` |
| Fechas | Reloj fijo en el dominio | REQ-NF-04 |
| Enumerados | Valor válido, desconocido (`PAUSADA`), minúsculas (`en_curso`), vacío (`''`) en cada `CHECK` | `…shouldRejectUnknownValue_whenCheckedColumn` |
| Duplicados | Segunda sesión `CONFIGURADA` o `EN_CURSO` del mismo usuario; dos `FINALIZADA` (permitidas); misma clave anti-duplicados para el mismo usuario y operación; misma clave para otro usuario (permitida); segunda respuesta definitiva del turno; segunda retroalimentación del turno; `idempotency_key` repetida en `ejecucion_ia` | Pruebas de esquema y de adaptador |
| Estado | Las 25 combinaciones de sesión y las 16 de turno; borrar en los 5 estados; borrar una sesión que otra transacción ya inició | `SessionStatusTest`, `TurnStatusTest`, `InterviewSessionTest`, `InterviewSessionRepositoryAdapterTest.delete_shouldThrow_whenStateChangedConcurrently` |
| Propiedad | Sin identidad; identidad en el cuerpo | §7 |
| Colecciones | Borrar una sesión borra sus hijas | `…session_shouldCascadeDelete_whenConfigured` |
| Concurrencia | Dos escrituras sobre la misma sesión con la misma versión | `…save_shouldThrowConflict_whenStaleVersion` |
| Concurrencia | Dos sesiones `CONFIGURADA` creadas a la vez | `…save_shouldThrowSessionAlreadyExists_whenSecondConfigured` |
| Falla parcial | Base caída | REQ-ERR-07 |
| Dependencias | Modelo de IA | Es de CM-312 |
| Carga | JSON mal formado, tipo de contenido erróneo, `Accept` XML | §7 y `FrameworkErrorsTest` |
| No aplica | Paginación, Unicode en campos de la API, fechas de calendario | No hay endpoints con esos datos en CM-311; los cubren HU-4.4 y HU-5.2 |

## 9. Configuración y estructura (tabla de brechas con Cuentas)

| Tema | Cuentas | Entrevista hoy | Lo que hace CM-311 |
|---|---|---|---|
| Dependencias | validación, Flyway + módulo PostgreSQL, ArchUnit 1.5.1, Testcontainers (PostgreSQL y JUnit), `spring-boot-testcontainers`, `spring-boot-resttestclient` | JPA, MVC, Spring AI, OpenAPI, `devtools`, Lombok | **Cierra:** agrega las mismas, con las mismas versiones que Cuentas, y quita `devtools`. Lombok queda declarado sin uso, como en Cuentas |
| Configuración de la base | `ddl-auto=validate`, esquema propio, `spring.flyway.*`, `spring.servlet.encoding.force-response=true` | sin Flyway; `local` con `ddl-auto=create` | **Cierra:** `ddl-auto=validate` y `default_schema=microentrevista` (P16 A); `spring.flyway.schemas`, `default-schema` y `create-schemas=true`; `force-response=true`; se quita `ddl-auto` de `local` y de `production` (lo hereda) |
| Perfil de despliegue | `prod` y `API_DOCUMENTATION_ENABLED` | `production` y `API_DOCS_ENABLED` | **No cierra (P5):** se conserva. La decisión de renombrarlo es de DevOps en CM-320; se le comunica por la tarea |
| Migraciones | `db/migration/V1__…` | ninguna | **Cierra:** `V1__esquema_inicial_entrevista.sql` |
| Manejador de errores | completo | lo siembra CM-312 | **Cierra:** completa según §6 |
| `requestId` en el `MDC` y en el patrón de log | filtro de máxima precedencia y `logging.pattern.level` (pieza de la alineación de Cuentas, aún no fusionada) | ninguno | **Cierra (D-2, P19):** `RequestIdFilter` y el patrón de log, con la clave `requestId` y el formato `^[A-Za-z0-9._-]{1,64}$` |
| Pruebas del manejador | `BusinessExceptionHandlerTest`, `FrameworkErrorsTest`, `ProblemDetailTestSupport`, `ErrorCodeTest`, `UntypedExceptionClassificationTest` | — | **Cierra:** crea las cinco (la de documentación la siembra CM-312) |
| Pruebas de esquema | `CuentaSchemaMigrationTest`, prueba de largos de columna | — | **Cierra:** `InterviewSchemaMigrationTest` y `ColumnLengthConsistencyTest` |
| Arquitectura | `LayeredArchitectureTest` | — | **Cierra** |
| Postman | `postman/` con colección, entorno y README | — | **Cierra (P6)** |
| `CLAUDE.md` del repo | sin tope de líneas | «un PR no pasa de 1000 líneas»; pendientes viejos | **Cierra:** un PR por CM sin tope; secciones 2, 5, 7 y 10 al día |
| `docs/eventos/`, mensajería | sí | no | **No cierra:** llega con CM-209 |
| Puerto | 8081 | 8080 interno, 8083 documentado | **No cierra:** espera D-13 |
| Pruebas de integración separadas (Failsafe) y plugin de formato | previstos en la alineación del estándar (CM-296, parte 2) | no | **No cierra:** destino CM-296 |
| `.env.example` en orden, propiedades tipadas con validación al arrancar, perfil `prod`, bandera `API_DOCUMENTATION_ENABLED` | previstos en CM-296 | no | **No cierra:** destino CM-296 (CM-312 agrega las propiedades del modelo con validación) |
| Umbral de cobertura de JaCoCo que no puede bajar | previsto en CM-296 | no | **No cierra:** destino CM-296 |
| `docs/verificaciones/` | existe en Cuentas (un solo documento, V-02) | no | **Cierra (D-4 A):** `V-01-esquema-inicial-entrevista.md` con la verificación única (tarjeta T9) y la regla nueva del estándar |
| Filtro del `requestId`, Testcontainers y ArchUnit en Entrevista | también listados en CM-296 | no | **Cierra aquí** (P19, REQ-NF-02, REQ-NF-03): CM-296 debe retirarlos de su parte de Entrevista (§19, A-10) |
| Paquete base | `tech.cameia.cuentas` | `tech.cameia.entrevista` | Igual; sin cambio |

## 10. Seguridad

### 10.1 ASVS 5.0.0 nivel 1 (matriz de `cameia-infra`, solo lectura)

| ID | Estado en la matriz | Qué hace CM-311 | Prueba |
|---|---|---|---|
| 1.2.3 | CUMPLE | Errores por `ProblemDetail` serializado, sin JSON a mano | `BusinessExceptionHandlerTest` |
| 1.2.4 | CUMPLE | JPA y `JdbcTemplate` con parámetros; el único SQL nativo es el `DELETE … WHERE id = ? AND estado = 'CONFIGURADA'` con parámetros | `InterviewSessionRepositoryAdapterTest` |
| 1.3.2 | CUMPLE | Sin SpEL ni `ScriptEngine` | revisión (`git grep`) |
| 2.1.1 | CUMPLE | Esta spec documenta las reglas | — |
| 2.2.1 / 2.2.2 | CUMPLE | `CHECK` de cada enumerado; identidad validada en el servidor | §7 y §8 |
| 2.3.1 | **PENDIENTE** | Las máquinas de estados quedan definidas y probadas. Pasa a CUMPLE cuando HU-4.5 y HU-5.2 las usen en sus endpoints | `SessionStatusTest`, `TurnStatusTest` |
| 4.1.1 | NO_CUMPLE (por el Gateway) | Toda respuesta de error de Entrevista lleva `charset=UTF-8` | `FrameworkErrorsTest.everyError_shouldDeclareCharset` |
| 8.2.2 | CUMPLE | La identidad solo sale de `X-User-Id`. La regla de dueño la aplica cada endpoint (Sprint 4) | `IdentityHeaderTest` |
| 11.4.1 | PENDIENTE | `huella_peticion` exige SHA-256 (`CHECK` de 64 hexadecimales). Su cálculo es de HU-4.4 | prueba de esquema |
| 14.2.1 | CUMPLE | Ningún dato sensible en la URL | — |
| 15.3.1 | CUMPLE | Entidades JPA separadas del dominio, mapeo a mano | `LayeredArchitectureTest` |

Hallazgo para DevOps (por Vela): la fila 2.3.1 cita el `CLAUDE.md` de Entrevista («máquina no definida»), que CM-311 deja desactualizado. Se informa en la tarea, sin editar la matriz.

### 10.2 OWASP API Security Top 10

| Riesgo | Aplica | Cómo |
|---|---|---|
| API1 | Base | `FirebaseUid` desde el Gateway; dueño en cada endpoint futuro. La sesión guarda `firebase_uid` |
| API2 | Sí | Solo `X-User-Id`; sin identidad en cuerpo ni ruta |
| API3 | Base | Entidades nunca expuestas; DTO en los endpoints futuros |
| API4 | Sí | Largos fijos en la base (3000, 4000, 1000, 128, 64) |
| API5 | No aplica | Sin funciones nuevas expuestas |
| API6 | Base | `clave_idempotencia` y únicos parciales contra el doble envío |
| API7 | No aplica | Ninguna URL sale de la entrada |
| API8 | Sí | `production` sin documentación de la API; errores sin trazas |
| API9 | Sí | Sin rutas nuevas; el controlador de errores vive solo en `src/test` |
| API10 | No aplica | El consumo del modelo es de CM-312 |

Pruebas negativas de SEG-02 que aplican hoy: sin identidad (401) e identidad en el cuerpo ignorada. Recurso ajeno con un segundo usuario: llega con el primer endpoint de lectura (HU-4.4).

## 11. Atributos de calidad (anexo AC-0001 a AC-0006)

HT-01 es un habilitador sin épica; se declaran los atributos que el cambio toca.

| Atributo | Métrica | Límite | Cómo lo cumple CM-311 | Prueba o comando |
|---|---|---|---|---|
| AC-0001 adecuación de la IA | IA-01 | 100 % de salidas con procedencia y esquema | Cada pregunta y cada retroalimentación apuntan a su `ejecucion_ia`, que guarda modelo, versión del prompt, estado y causa del fallo; la retroalimentación solo se guarda como objeto JSON. Que la ejecución enlazada sea `EXITOSA` y del tipo correcto lo comprueban HU-4.5 y HU-5.2 al escribir | `…question_shouldRejectMissingExecution`, `…execution_shouldRequireModel`, `…execution_shouldRequireErrorCode_whenFailed`, `…execution_shouldRejectPromptVersion_whenTranscribe` |
| AC-0002 desempeño | DES-02 | p95 ≤ 2 s | Índice en cada clave foránea y en `firebase_uid`; sin consultas N+1 (no hay listados) | `…_shouldIndexEveryForeignKey_whenApplied` |
| AC-0002 desempeño | DES-04 | 5xx propios < 1 % | Todo fallo previsible tiene su 4xx o 503 propio | `BusinessExceptionHandlerTest`, `FrameworkErrorsTest` |
| AC-0003 seguridad | SEG-01 | ASVS N1 aplicable 100 % | §10.1 | ídem |
| AC-0003 seguridad | SEG-02 | 100 % de accesos sin identidad rechazados | 401 en los 6 casos de §7 | `IdentityHeaderTest` |
| AC-0003 seguridad | SEG-03 | Cero vulnerabilidades críticas abiertas | Dependencias nuevas = mismas versiones que Cuentas | Dependabot y CodeQL de la rama |
| Transversal | Observabilidad (sin métrica propia en el anexo) | Todo registro lleva su `requestId` | `RequestIdFilter` y `logging.pattern.level` (REQ-ERR-13 y 14) | `RequestIdFilterTest.requestId_shouldAppearInLogLine_whenFilterRuns` |
| AC-0003 seguridad | SEG-04 | Cero secretos, prompts o transcripciones en logs | El manejador registra solo código, estado, `requestId`, `firebaseUid` y nombre de restricción | `BusinessExceptionHandlerTest.logs_shouldNotContainBodyOrValues` (captura con `OutputCaptureExtension`) |
| AC-0004 fiabilidad | FIA-01 | Conserva el último estado confirmado | Transición rechazada no cambia el agregado; choque de versión no cambia la fila | `InterviewSessionTest`, `…save_shouldThrowConflict_whenStaleVersion` |
| AC-0004 fiabilidad | FIA-02 | Cero efectos al repetir | Únicos parciales, `uq_intento_definitivo_por_turno`, `clave_idempotencia` | pruebas de esquema y de adaptador |
| AC-0006 interacción | INT | Errores con `code`, mensaje claro y `errors[]` | §6 | `BusinessExceptionHandlerTest` |
| Transversal | MAN-01 | ≥ 90 % líneas y ramas en lo nuevo | JaCoCo por clase (§13) | `./mvnw.cmd clean verify` |
| Transversal | MAN-03 | Cero FK entre servicios | `perfil_id` y `rol_objetivo_id` sin clave foránea | `…session_shouldHaveNoForeignKeyToOtherServices` |
| Transversal | IOP-01 | API documentada | Sin endpoints nuevos; OpenAPI del manejador en `docs/errores.md` | `ErrorCodeDocumentationTest` |
| Restricción | REST-0002 | Sin estado en memoria | Ninguna clase guarda estado entre peticiones | revisión |
| Restricción | REST-0003 | Datos personales mínimos | `contexto_perfil_snapshot` lo llena HU-4.4 con lo mínimo; el audio no tiene columna | prueba de esquema (ninguna columna `bytea`) |

## 12. Postman y Newman (P6)

Carpeta `postman/` con `cameia-entrevista.postman_collection.json` (v2.1, UTF-8), `local.postman_environment.json` (`baseUrl = http://localhost:8083`) y `README.md`.

| Petición | Espera |
|---|---|
| `GET {{baseUrl}}/health` con `X-Request-Id: postman-1` | 200, cuerpo de salud, encabezado `X-Request-Id: postman-1` |
| `GET {{baseUrl}}/ruta-que-no-existe` | 404, `code = ROUTE_NOT_FOUND`, `Content-Type` con `application/problem+json` y `charset=UTF-8`, `requestId` presente, sin `trace` ni `exception` |
| `POST {{baseUrl}}/health` | 405, `code = METHOD_NOT_ALLOWED`, encabezado `Allow` con `GET` |
| `GET {{baseUrl}}/health` con `Accept: application/xml` | 406, `code = MEDIA_TYPE_NOT_ACCEPTABLE` |

Se corre con `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --reporters cli,junit --reporter-junit-export ../../ejecucion/evidencia/CM-311/newman.xml` contra el jar (`java -jar target/entrevista-0.0.1-SNAPSHOT.jar`) con la base de `docker compose up -d entrevista-db` (decisión Q6: pruebas locales y base real de prueba).

## 13. Pruebas, cobertura y comandos

| Capa | Clases de prueba | Herramienta |
|---|---|---|
| Dominio | `SessionStatusTest`, `TurnStatusTest`, `InterviewSessionTest`, `InterviewTurnTest`, `FirebaseUidTest`, `ErrorCodeTest` | JUnit 5 + AssertJ, sin Spring |
| Persistencia | `InterviewSchemaMigrationTest`, `ColumnLengthConsistencyTest`, `InterviewSessionRepositoryAdapterTest`, `InterviewTurnRepositoryAdapterTest`, `IdempotencyKeyRepositoryAdapterTest` | `@SpringBootTest` + Testcontainers |
| Web | `BusinessExceptionHandlerTest`, `IdentityHeaderTest`, `RequestIdTest`, `RequestIdFilterTest` (MockMvc `standaloneSetup` con el controlador de prueba `ErrorProbeController`); `FrameworkErrorsTest` (servidor real, puerto aleatorio) | MockMvc, `TestRestTemplate` |
| Arquitectura | `LayeredArchitectureTest`, `UntypedExceptionClassificationTest` | ArchUnit |
| Arranque | `EntrevistaApplicationTests` pasa a Testcontainers | — |

Clases a medir (≥ 90 % líneas y ramas): todo lo nuevo en `domain.model`, `domain.exception`, `domain.port` (sin código), `infrastructure.persistence.*`, `presentation.advice.BusinessExceptionHandler` (lo agregado) y `presentation.filter.RequestIdFilter`.

Comandos (PowerShell, en el worktree):

```
docker version                                  # Docker Desktop encendido (Testcontainers)
./mvnw.cmd -B -ntp clean verify                 # suite completa + JaCoCo
Get-Content target/site/jacoco/jacoco.csv       # cobertura por clase
docker compose up -d entrevista-db              # base real para Newman
./mvnw.cmd -B -ntp -DskipTests package
java -jar target/entrevista-0.0.1-SNAPSHOT.jar  # con DB_* del .env local, perfil local
npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --reporters cli,junit
```

## 14. Línea base (medida sobre `develop` `bcc697d`, vigente el 20-oct)

`./mvnw -B -ntp verify` **falla en local** sin base de datos: `EntrevistaApplicationTests.contextLoads` no puede crear el `entityManagerFactory` («Unable to determine Dialect without JDBC metadata»). Las otras 13 pruebas pasan. En la integración continua sí pasa, porque el flujo levanta un PostgreSQL de servicio. Además, Docker Desktop no estaba encendido en la máquina de Paula.

Destino: CM-311 convierte esa prueba a Testcontainers (T2). Línea base para la ejecución: con Docker encendido, `docker compose up -d entrevista-db` y `DB_HOST=localhost DB_PORT=5433 DB_NAME=DB-entrevista DB_USERNAME=postgres DB_PASSWORD=<la del .env local> ./mvnw.cmd -B -ntp verify` debe dar 14 pruebas en verde antes de tocar nada.

## 15. Decisiones

| # | Decisión | Por qué | Alternativas descartadas | Quién y cuándo |
|---|---|---|---|---|
| Q1–Q12, Q23 | Ver `17102026_preparacion-sprint3_entrevista.md` §7 | — | — | Paula, 17-oct |
| P1 | Aprueba Paula; Vela recibe un resumen y las correcciones de §20 | La regla de Backend: solo Paula aprueba | Esperar a Vela | Paula, 17-oct |
| P2 | Sin identidad: 401 `IDENTITY_REQUIRED` | Igual que Perfil y el estándar | 400 de Cuentas | Paula, 17-oct |
| P3 | Cuerpo ilegible: 422 | Igual que Cuentas y Perfil; el estándar se corrige | 400 | Paula, 17-oct |
| P4 | Turno solo con las transiciones de HT-01 | HT-01 es más reciente; HU-5.9 agrega lo suyo | Las del diagrama | Paula, 17-oct |
| P5 | Se conserva `production`; DevOps decide si lo renombra en CM-320 | El flujo de despliegue es de DevOps | Renombrar ya | Paula, 17-oct |
| P6 | Postman con salud y errores del framework | Estructura igual a Cuentas | Sin Postman | Paula, 17-oct |
| P7 | Pregunta de 1 a 1000 caracteres, **provisional hasta el spike HU-4.1** | El spike puede ampliarlo con una migración | 2000; sin tope | Paula, 17-oct |
| P8 | Retroalimentación en `jsonb` | El spike aún no fija la forma | Columnas sueltas | Paula, 17-oct |
| P9 | Nombres de `ejecucion_ia` | Mismo vocabulario que `TRANSCRIBIR` | Otros nombres | Paula, 17-oct |
| P10 | `clave_idempotencia` | Estructura lista para HU-4.4 | Otro diseño | Paula, 17-oct |
| P11 | `intento_respuesta` de solo inserción desde ya | Regla del estándar (§F) | Esperar a HU-5.4 | Paula, 17-oct |
| P12 | `CHECK` de modalidad con `SIMULACION` y `ENTRENO` | **Paula eligió la opción no recomendada:** la base repite el catálogo completo y el código impide Entreno (HU-4.4, 422) | Solo `SIMULACION` | Paula, 17-oct |
| P13 | `idioma_detectado` de 2 a 10 caracteres; **el valor exacto se valida cuando exista el contrato v0 de voz (CM-315), no antes** | El contrato aún no existe | Esperar al contrato | Paula, 17-oct |
| P17 | Procedencia de la IA en Entrevista: `modelo`, `version_prompt` y `error_codigo` en `ejecucion_ia`, y `ejecucion_ia_id` en `pregunta` y `feedback_turno`. Nombres y largos tomados del modelo de datos original de Entrevistas (anexo E/R de la Entrega 1) | En la arquitectura original, `ejecucion_ia` y su procedencia viven en Entrevista; Auditoría (fuera del MVP) solo consolida consumo, costo y cuota a partir de los eventos que publica Entrevista, y Langfuse (no desplegado) guarda las trazas técnicas completas. Así IA-01 se mide desde el primer endpoint | Dejarlo a HU-4.5 y HU-5.2 | Paula, 17-oct |
| P19 | CM-311 también hace el filtro del `requestId` en el `MDC` y el patrón de log (REQ-ERR-13 a 15) | CM-312 se lo asigna y la primera petición de Postman (`X-Request-Id` devuelto en un 200) lo exige; sin filtro, el identificador solo existe en las respuestas de error | No hacerlo y dejarlo para otra tarea (opción A) | Paula, 20-oct |
| P18 | `ejecucion_ia` guarda, opcionales, `proveedor varchar(60)`, `tokens_entrada bigint` y `tokens_salida bigint`, como el modelo original | Vigilar el gasto del crédito de Google Cloud y dejar el dato listo para cuando Auditoría consolide el consumo. Las llena quien llama a la IA (HU-4.5, HU-5.2, HU-5.12) si el proveedor las informa | No guardarlas | Paula, 17-oct |
| P16 | Esquema `microentrevista` | Igual que Cuentas (`microcuentas`), que funciona con el mismo módulo de Cloud SQL | `public`, como Perfil | Paula, 17-oct |
| I-1 | Enumerados con nombres en inglés y el valor del backlog en la base (`SessionStatus.CONFIGURED` ↔ `CONFIGURADA`), con un `AttributeConverter` por enumerado | R1 (código en inglés) y Q7 (base en español) a la vez | Constantes en español; valores en inglés en la base | Inferencia de Backend |
| I-2 | CM-311 crea entidades JPA solo para `sesion`, `turno` y `clave_idempotencia`; las demás tablas se prueban con SQL | Son las que necesitan `@Version` o el puerto anti-duplicados; las demás las mapean sus historias | Mapear todas ya (código sin uso) | Inferencia de Backend |
| I-3 | Turno sin `sesion_id`: se llega a la sesión por `pregunta` | Evita una columna que podría contradecir a la pregunta | Copiar `sesion_id` | Inferencia de Backend |
| I-4 | Los disparadores no tienen código de error todavía (§5.3) | Ningún adaptador de CM-311 escribe preguntas ni intentos | Códigos sin uso | Inferencia de Backend |

## 16. Fuera de alcance

Endpoints (`POST /sessions`, `…/start`, `…/answers`, `…/state`, `DELETE`); catálogo de personalización (HU-4.3); validación de la configuración (HU-4.4); reglas de uso de las claves anti-duplicados (HU-4.4); cliente del modelo (CM-312); réplica del perfil y mensajería (CM-209); `follow_up_texto` y `FOLLOW_UP` (HU-5.9); `REPREGUNTA` y `REINTENTO_USUARIO` (HU-5.3, HU-5.4); renombrar `production` (DevOps, CM-320); puerto (D-13).

## 17. Preguntas

| # | Pregunta | Estado |
|---|---|---|
| Q1–Q23, P1–P18 | Respondidas por Paula el 17-oct | Cerradas |
| D-1 | Cómo empezar mientras CM-312 no tiene rama ni PR (§20.3) | Respondida: B (Paula, 20-oct) |
| D-3 | Índice parcial `ix_sesion_en_curso_ultima_actividad` para la futura búsqueda de sesiones inactivas (HU-5.7) | Respondida: A, se crea (Paula, 20-oct) |
| D-4 | Entrevista adopta `docs/verificaciones/` y la regla se agrega al estándar y a las skills | Respondida: sí (Paula, 20-oct) |
| D-5 | `fecha_actualizacion` la fija el código con el reloj inyectado, no un disparador | Respondida: el código (Paula, 20-oct) |
| D-2 | Filtro del `requestId` en el `MDC` que CM-312 le asigna a CM-311 (§20.2) | Respondida: B, se hace en CM-311 (P19, Paula, 20-oct) |

## 18. Lista de verificación de la CM

| # | Qué | Cómo | Resultado esperado |
|---|---|---|---|
| V-01 | HT01-C1 | `./mvnw.cmd -Dtest=InterviewSchemaMigrationTest test` | verde; 7 tablas + `tipo_pregunta` con 4 filas |
| V-02 | HT01-C2 | `-Dtest=InterviewSchemaMigrationTest#session_*` y `InterviewSessionRepositoryAdapterTest` | segunda `CONFIGURADA`/`EN_CURSO` rechazada con `SESSION_ALREADY_EXISTS` |
| V-03 | HT01-C3 | `-Dtest=SessionStatusTest,InterviewSessionTest` | 25 combinaciones (4 válidas) y 5 borrados (1 válido) |
| V-04 | HT01-C4 | `-Dtest=TurnStatusTest,InterviewTurnTest` | 16 combinaciones (4 válidas) |
| V-05 | HT01-C5 | `-Dtest=InterviewSchemaMigrationTest#question_*` | 3 cambios rechazados con `trg_pregunta_inmutable` y 1 permitido |
| V-06 | HT01-C6 | `-Dtest=BusinessExceptionHandlerTest,FrameworkErrorsTest,IdentityHeaderTest,LayeredArchitectureTest` | verde |
| V-07 | HT01-C7 | `-Dtest=InterviewSchemaMigrationTest#answer_*` | `AUDIO` con idioma aceptado; `TEXTO` con idioma rechazado |
| V-08 | R1 | `git diff` sin identificadores en español en Java; columnas en español | cumple |
| V-09 | R3 | Cada fila de §6 con prueba; petición real a 404/405/406 | Newman 4/4 |
| V-10 | R4 | Cada restricción violada en una prueba | `InterviewSchemaMigrationTest` |
| V-11 | R6 | §10 | — |
| V-12 | R8 | Javadoc en toda clase; `package-info.java` por paquete nuevo; `docs/errores.md`; ADR 0006; `CLAUDE.md` | revisión |
| V-13 | R10 | `jacoco.csv` | ≥ 90 % líneas y ramas por clase tocada |
| V-14 | R11 | Newman | 4 peticiones, 0 fallos |
| V-15 | Adversarial | identidad en el cuerpo; encabezado de 129 caracteres; `X-Request-Id` hostil; `Accept` XML; choque de versión | rechazados según §7 y §8 |
| V-16 | Logs | `logs_shouldNotContainBodyOrValues` | sin cuerpos ni valores |
| V-18 | Verificación documentada (D-4) | `docs/verificaciones/V-01-esquema-inicial-entrevista.md` existe, con entorno, números reales y lo no ejecutado | revisión |
| V-17 | `requestId` (REQ-ERR-13 a 15) | `-Dtest=RequestIdFilterTest,RequestIdTest` y la primera petición de Newman | eco del encabezado en 200 y en errores; `MDC` vacío al terminar; línea de log con el `requestId` |

## 19. Hallazgos que anticipa esta spec (con destino)

| # | Hallazgo | Destino |
|---|---|---|
| A-1 | Dos inicios simultáneos de la misma sesión (CA-4.5.12): con `@Version`, el segundo recibe `SESSION_CONFLICT`. El CA pide 200 con el estado actual | Spec de HU-4.5: atrapar `SessionConflictException`, releer y responder el estado actual |
| A-2 | La planificación (CA-4.5.8) necesita el nombre del rol objetivo, pero la sesión solo guarda su UUID y Entrevista no tiene el catálogo de roles. HU-4.4 ya debe consultar el catálogo de Perfil para validar que el rol está activo (CA-4.4.3) | Spec de HU-4.4: decidir de dónde sale el nombre; si se guarda, `V2` agrega la columna |
| A-3 | Un identificador de sesión mal escrito en la ruta responde hoy 422 `REQUEST_INVALID_VALUE`; RT-03 trata lo inexistente como 404 | Specs de HU-4.4 y HU-4.5: decidir si responde 404 |
| A-4 | Una clave anti-duplicados puede guardar la respuesta de una sesión que después se canceló y borró | Spec de HU-4.4 (reglas de la clave) |
| A-5 | Borrar una sesión configurada borra también sus `ejecucion_ia` de planificaciones fallidas (Q5) | Se acepta: la sesión cancelada no tiene preguntas ni respuestas; se revisa con P17 |
| A-6 | Las respuestas por texto se guardan tal como llegan; la base cuenta puntos de código pero no normaliza a NFC | Spec de HU-5.2: normalizar a NFC antes de validar 4000 |
| A-7 | `intento_respuesta` es de solo inserción; HU-5.4 necesitará desmarcar `es_definitiva` | Spec de HU-5.4: migración que afloje el disparador solo para esa columna |
| A-8 | Los códigos nuevos de la entrevista del backlog v8.3 (`PLANNING_RETRIES_EXHAUSTED` y los `TRANSCRIPTION_*`) no son de CM-311: ningún endpoint los emite todavía | Specs de HU-4.5 (planificación) y de HU-5.8 y HU-5.12 (transcripción): cada una agrega su código a `ErrorCode` y a `docs/errores.md` |
| A-10 | CM-296 (parte 2 de la alineación) lista para Entrevista el filtro del `requestId`, el manejador base, Testcontainers y ArchUnit, que CM-311 y CM-312 ya hacen | Vela: ajustar la descripción de CM-296 (acción en el aviso de `comunicaciones/`) |
| A-9 | La descripción de CM-257 en Jira conserva la transición EN_CURSO → ABANDONADA por reintentos agotados (retirada en v8.3) y 12 h | Acción para Vela (§20.2) |

## 20. Contraste con CM-312 y orden de ejecución

Fuente: spec, plan y tarjetas de CM-312 entregadas por Vela el 20-oct, y `origin/develop` de `cameia-entrevista` (`bcc697d`, sin rama ni PR de CM-312 al 20-oct).

### 20.1 Lo que CM-312 siembra y esta CM extiende

| Pieza | Lo que dice CM-312 | Lo que hace CM-311 | Tipo |
|---|---|---|---|
| `ErrorCode` | Solo `INTERNAL_ERROR`, `LLM_UNAVAILABLE`, `LLM_LIMIT_REACHED` | Agrega los 14 restantes al final, sin reordenar ni renombrar | Cambio sin alternativa |
| `BusinessException` | Abstracta, igual que la de Cuentas: constructor `protected (ErrorCode, String)` | Las excepciones de dominio la extienden | Cambio sin alternativa |
| `BusinessExceptionHandler` | Clase de paquete con 3 manejadores y el armado común (`problema`, `resolverRequestId`, `nuevoRequestId`) | Agrega manejadores; no toca los tres ni el armado | Cambio sin alternativa |
| `ErrorCodeDocumentationTest` | Copiada de Cuentas | Se conserva; T3 agrega las filas de los 14 códigos en el mismo commit | Cambio sin alternativa |
| `docs/errores.md` | Columnas `Código \| HTTP \| Endpoints \| Campo \| Mensaje \| Origen \| Prueba` | CM-311 usa esas mismas columnas | Cambio sin alternativa |
| `application.properties` y pruebas de contexto | `cameia.llm.model` obligatoria | Toda `@SpringBootTest` de CM-311 lleva `cameia.llm.model=modelo-de-prueba` | Cambio sin alternativa |
| `hibernate-validator` | Dice que declararlo «le toca a CM-311» | T1 declara `spring-boot-starter-validation` | Cambio sin alternativa |
| `docs/bitacora-ia` | CM-312 usa `02_cliente-gemini-cm-312_prompt.md` | CM-311 usa `03_motor-base-entrevista_prompt.md` | Cambio sin alternativa |

### 20.2 Diferencias que se resuelven fuera de esta CM

| # | Hallazgo | Tipo | Destino |
|---|---|---|---|
| H-1 | CM-312 reparte el filtro del `requestId` en el `MDC` a CM-311 citando «P2-05 de CM-283». Esa pieza es de `cameia-cuentas` y Cuentas aún no la tiene en `develop` | Decisión de Paula (D-2): B | CM-311 la hace con REQ-ERR-13 a 15 y la tarjeta T5b; sigue el diseño de la pieza P2-05 de Cuentas |
| H-2 | El plan de CM-312 parte el trabajo en piezas A, B y B2 con tope de 1000 líneas | Acción para Vela | Un PR por CM, sin tope; pedir que quite el tope y las piezas B2 |
| H-3 | La tarjeta T-10 de CM-312 edita el flujo de despliegue y vuelve `LLM_MODEL` obligatoria en `docker-compose.yml` con `:?` | Decisión de Vela con DevOps (no se pide ni se discute) | Efecto en CM-311: con `:?`, `docker compose up -d entrevista-db` exige `LLM_MODEL` aunque solo se levante la base; la línea base de T0 la define en el `.env` local |
| H-4 | CM-312 no dice qué cierra CM-311 de perfiles, carpetas y `/health` | Acción para Vela | Las listas de §1.1 de CM-312 coinciden con §9 de esta spec; basta confirmarlo |
| H-5 | PD-01 a PD-06 de CM-312 (id del modelo, temperatura, límites, pensamiento, fecha de fusión, llave real) siguen sin respuesta del Product Owner | Pendiente del Product Owner | Bloquean a CM-312, no a CM-311 |
| H-6 | La tarea CM-312 pedía fusionar el 19-oct y no hay rama ni PR | Pendiente de Vela | Fecha real de fusión (PD-05) |
| H-7 | Descripción de CM-257 desactualizada (v4, 12 h) | Acción para Vela | Ver A-9 |

### 20.3 Orden de ejecución y de fusión

1. CM-312 se fusiona primero (Jira de CM-311: «Fusionar después de HT-02»).
2. CM-311 va en un solo PR hacia `develop`. Si CM-312 aún no está fusionada, la rama se apila sobre la suya: se crea desde la punta de `CM-312-*` y el PR de CM-311 apunta a esa rama hasta que CM-312 se fusione. Al fusionarse, se rebasa con `git rebase --onto origin/develop <base-vieja> <punta-vieja>`, guardando antes los SHA y los árboles viejos; si el árbol coincide con el ya verificado, hereda la verificación.
3. **D-1 = B (Paula, 20-oct).** T1 (dependencias y configuración) y T2 (migración y pruebas del esquema) no usan nada de CM-312 salvo la propiedad de prueba del modelo: se hacen desde `origin/develop` antes de que exista la rama de CM-312. Cuando la rama exista, se rebasa con `git rebase --onto <base-nueva> <base-vieja> <punta-vieja>` y se sigue con T3. Si CM-312 se fusionó antes de empezar, no hay rebase.
