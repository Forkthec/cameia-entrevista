# Tarjetas · CM-311 Motor base de Entrevista

Cada tarjeta se ejecuta sola, en orden. Quien la ejecuta no lee la spec: todo lo que necesita está aquí.

**Reglas que aplican a todas las tarjetas**

- Identificadores, nombres de métodos de prueba y códigos de error en inglés. Javadoc, comentarios, `@DisplayName`, mensajes y documentos en español. Tablas y columnas en español.
- Javadoc en toda clase, enumerado, `record` y método (con `@param`, `@return` y `@throws`); `package-info.java` en cada paquete nuevo. Ningún comentario con `CM-311` ni rutas a otros archivos.
- Pruebas: `metodo_shouldX_whenY` con `@DisplayName` en español; Arrange-Act-Assert; valores literales; sin `Thread.sleep`; aserciones concretas.
- Prohibido: dependencias distintas de las de T1; editar `.github/`; tocar el cliente del modelo de CM-312; renombrar algo existente; registrar cuerpos, valores de columnas, prompts o tokens; `catch` vacío.
- **Detenerse y reportar a Paula** (con la salida real) si un archivo no está donde dice la tarjeta, una prueba existente se rompe sin causa clara o falta un dato.
- Al terminar cada tarjeta: `./mvnw.cmd -B -ntp test` del módulo y anotar el resultado real al final de la tarjeta.
- Docker Desktop debe estar encendido: sin Docker, las pruebas con `disabledWithoutDocker = true` se **omiten** y eso no cuenta como verde.

---

## T0 · Preparación y verificación de la base de CM-312

**Objetivo:** confirmar que la base que siembra CM-312 está fusionada (o es la punta de su rama) y que sus firmas son las que esta CM extiende. La comparación contra la spec de CM-312 ya está hecha (ver `spec.md` §20); aquí solo se confirma contra el código real.

**Firmas que esta CM usa (tomadas de la spec de CM-312, tarjetas T-03 a T-09):**

| Pieza | Firma esperada |
|---|---|
| `domain/exception/ErrorCode.java` | enum con `INTERNAL_ERROR`, `LLM_UNAVAILABLE`, `LLM_LIMIT_REACHED`, cada uno con Javadoc |
| `domain/exception/BusinessException.java` | `public abstract class`, constructor `protected BusinessException(ErrorCode errorCode, String mensaje)`, `getErrorCode()` |
| `presentation/advice/BusinessExceptionHandler.java` | clase de paquete (no pública) con `problema(...)`, `resolverRequestId()`, `nuevoRequestId(...)`, constantes `REQUEST_ID_HEADER`, `REQUEST_ID_ATTRIBUTE`, `LOGGED_ATTRIBUTE`, `PROBLEM_JSON_UTF8`, `REQUEST_ID_VALIDO`, `DETALLE_INTERNO` y los tres manejadores de CM-312 |
| `ErrorCodeDocumentationTest` | `@ParameterizedTest @EnumSource(ErrorCode.class)`; exige en `docs/errores.md` una fila que empiece con el código entre comillas invertidas |
| `docs/errores.md` | tabla `Código \| HTTP \| Endpoints \| Campo \| Mensaje \| Origen \| Prueba` |
| `application.properties` | `cameia.llm.api-key`, `cameia.llm.model` (obligatoria), `cameia.llm.timeout` |

**Pasos**

1. `git fetch`; `git status` limpio.
2. Buscar la base: `git log origin/develop --oneline --grep "CM-312"` y `git branch -r --list "origin/CM-312-*"`.
   - Fusionada: rama `CM-311-motor-base-entrevista` desde `origin/develop`.
   - Con rama y sin fusionar: rama desde la punta de `origin/CM-312-*` (stack: solo el PR de abajo apunta a `develop`). Anotar al final de esta tarjeta el SHA y el árbol (`git rev-parse <rama>^{tree}`) de la base.
   - Ni fusionada ni con rama (decisión D-1 = B de Paula, 20-oct): rama desde `origin/develop` y hacer **solo T1 y T2**, que no usan nada de CM-312. Anotar el SHA y el árbol de `origin/develop` como base vieja. Cuando aparezca `origin/CM-312-*` (o se fusione), rebasar con `git rebase --onto <base-nueva> <base-vieja> <punta-vieja>`, comprobar que T1 y T2 siguen en verde y seguir con T3. Detenerse antes de T3 si la base de CM-312 aún no existe.
3. Abrir cada archivo de la tabla y comparar con la firma esperada. Si algo difiere (nombre, visibilidad, paquete, columnas): **detenerse** y reportar la diferencia con la salida real. Las tarjetas siguientes usan los nombres de la tabla.
4. Llave y modelo en las pruebas: con CM-312, el contexto no arranca sin `cameia.llm.model`. Toda `@SpringBootTest` de esta CM (T2, T4, T5 y las demás) lleva `properties = "cameia.llm.model=modelo-de-prueba"`; la llave falsa llega por el perfil `local`, que es el perfil por defecto. Anotar si CM-312 lo resolvió distinto.
5. Línea base: Docker encendido; `docker compose up -d entrevista-db`; con `DB_HOST=localhost`, `DB_PORT=5433`, `DB_NAME=DB-entrevista`, `DB_USERNAME=postgres` y la `DB_PASSWORD` del `.env` local, `./mvnw.cmd -B -ntp verify`. Esperado: todas en verde (14 en `develop` a la fecha de esta spec, más las de CM-312). Si no, detenerse.
6. Mover la subtarea CM-311 de Jira a «En curso».

**Terminado:** rama creada, base anotada (SHA y árbol), firmas comparadas, línea base en verde.

**Resultado (21-oct):** `CM-312` aparece solo como rama remota `origin/CM-312-cliente-gemini` con la spec, el plan y las tarjetas (sin código y sin PR); no está fusionada. Se aplica la decisión D-1 = B: rama `CM-311-motor-base-entrevista` desde `origin/develop`, solo T1 y T2. Base vieja: SHA `bcc697d3fe7aa71793910fe23a7ba27da55f62d5`, árbol `d080391693e742eaad0e2f8fcef13631245f1174`. Paso 3 (firmas) no aplica todavía: no hay código de CM-312. Línea base: `verify` con PostgreSQL 16 temporal y llave falsa → 14 pruebas, 0 fallos, 0 omitidas. Sin `GOOGLE_GENAI_API_KEY` en el entorno el contexto no arranca (`Incomplete Google GenAI configuration`), por eso la llave falsa. Subtarea CM-311 en «En curso».

**Resultado (22-oct, detenida):** rebase hecho (`git rebase --onto 6c3d79a bcc697d`; SHA y árboles viejos y nuevos en `ejecucion/evidencia/CM-311/respaldo-rebase.txt`; punta `5b4a7bd`, árbol `019e86c`). Comparación contra el código real de CM-312 (PR #31): `BusinessException`, `ErrorCode` (3 códigos con Javadoc) y el armado común del manejador coinciden. Diferencias que detienen la ejecución: (1) no existe `ErrorCodeDocumentationTest` ni la tabla de `docs/errores.md` (sigue el texto «todavía no tiene manejador»); (2) las propiedades del modelo son cinco obligatorias (`api-key`, `model`, `timeout`, `temperature`, `thinking-level`), sin valor en ningún `application*.properties`, `.env.example` ni `docker-compose.yml`, y no solo `cameia.llm.model`; (3) `falloInterno` relanza los rechazos de protocolo a Spring. Línea base: `verify` sin esas variables → 158 pruebas, 93 errores (el contexto no arranca: `Incomplete Google GenAI configuration` y `cameia.llm.model ... es obligatorio`); con las cinco variables → 156 verdes y 2 fallos causados por las propias variables (`LanguageModelPropertiesTest` espera que falten).

**Decisiones de Paula (22-oct):** A1 = cada prueba de contexto lleva las cinco propiedades falsas con la anotación `@WithLanguageModelProperties` (`src/test/.../support`); B1 = CM-311 crea `ErrorCodeDocumentationTest` (copia de Cuentas) y las 14 filas en T3; C = T5 cubre `ServletRequestBindingException` como Cuentas (encabezado `X-User-Id` → 401; cualquier otro enlace faltante → 422 `REQUEST_INVALID_VALUE`), sin código nuevo; D1 = spec en el commit `cb22665`. Línea base con A1: `verify` → 158 pruebas, 0 fallos, 0 omitidas, BUILD SUCCESS (`t0-linea-base-verde.log`).

---

## T1 · Dependencias y configuración (REQ-NF-01, brechas de §9)

**Archivos:** `pom.xml`, `src/main/resources/application.properties`, `application-local.properties`, `application-production.properties`, comentarios de `.env.example` y `docker-compose.yml`.

**`pom.xml`:** copiar de `cameia-cuentas/pom.xml` (`origin/develop`) estos bloques, **con las mismas versiones**:

| Dependencia | Alcance |
|---|---|
| `org.springframework.boot:spring-boot-starter-validation` | compile |
| `org.springframework.boot:spring-boot-starter-flyway` | compile |
| `org.flywaydb:flyway-database-postgresql` | compile |
| `com.tngtech.archunit:archunit-junit5:1.5.1` | test |
| `org.springframework.boot:spring-boot-testcontainers` | test |
| `org.springframework.boot:spring-boot-resttestclient` | test |
| `org.testcontainers:testcontainers-postgresql` | test |
| `org.testcontainers:testcontainers-junit-jupiter` | test |

Quitar el bloque `spring-boot-devtools`. No tocar Lombok ni Spring AI.

**`application.properties`:** agregar, después de `spring.jpa.open-in-view=false`:

```properties
# El esquema lo instala Flyway en todos los perfiles; Hibernate solo comprueba que el mapeo coincide.
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.default_schema=microentrevista
spring.flyway.schemas=microentrevista
spring.flyway.default-schema=microentrevista
spring.flyway.create-schemas=true
# Fuerza UTF-8 también en las respuestas de error (ASVS 4.1.1).
spring.servlet.encoding.force-response=true
```

(Esquema propio `microentrevista`, como Cuentas: decisión P16 del 17-oct.)

**`application-local.properties`:** quitar `spring.jpa.hibernate.ddl-auto=create` y poner el comentario «El esquema lo instala Flyway también en desarrollo; ddl-auto se hereda y vale validate.». **`application-production.properties`:** quitar la línea `ddl-auto=validate` (se hereda).

**`.env.example` y `docker-compose.yml`:** cambiar solo los comentarios que dicen que `local` recrea el esquema: «local -> Flyway instala el esquema y muestra SQL».

**Trampa:** `EntrevistaApplicationTests` seguirá exigiendo una base hasta T2: corre T1 con la base de compose (T0, paso 5).

**Prueba:** `./mvnw.cmd -B -ntp verify` con la base de compose → verde (Flyway sin migraciones arranca con el esquema vacío).

**Commit:** `CM-311 | build(entrevista): Flyway, validación, Testcontainers y ArchUnit como en Cuentas`.

**Resultado (21-oct):** hecho en el commit `d0f3954`. `verify` con la base temporal → 14 pruebas, 0 fallos, BUILD SUCCESS; Flyway arranca con el esquema vacío. Además de los comentarios de la tarjeta, se corrigieron dos líneas de `.env.example` que seguían diciendo que `production` exige un esquema ya creado y falla con la base vacía (con Flyway ya no es cierto).

---

## T2 · Migración V1 y su prueba (REQ-DB-01 a REQ-DB-14; HT01-C1, C2, C5, C7)

**Archivo nuevo:** `src/main/resources/db/migration/V1__esquema_inicial_entrevista.sql`.

Contenido: `CREATE SCHEMA IF NOT EXISTS microentrevista;` y las tablas, en este orden: `tipo_pregunta` (con sus 4 `INSERT`), `sesion`, `pregunta`, `turno`, `intento_respuesta`, `feedback_turno`, `ejecucion_ia`, `clave_idempotencia`; después los índices y los dos disparadores. Cada columna, tipo, nulo, valor por defecto, `CHECK`, clave foránea e índice exactamente como en la tabla de abajo. Encabezado de comentario en español que explique para qué sirve el esquema, sin `CM-311`.

| Tabla | Definición exacta |
|---|---|
| `tipo_pregunta` | `codigo varchar(20) NOT NULL CONSTRAINT pk_tipo_pregunta PRIMARY KEY`, `aplica_star boolean NOT NULL`, `CONSTRAINT ck_tipo_pregunta_codigo CHECK (codigo IN ('APERTURA','TECNICA','CONDUCTUAL','SITUACIONAL'))`. Filas: `('APERTURA',false),('TECNICA',false),('CONDUCTUAL',true),('SITUACIONAL',false)` |
| `sesion` | `id uuid CONSTRAINT pk_sesion PRIMARY KEY`; `firebase_uid varchar(128) NOT NULL`; `perfil_id uuid NOT NULL`; `rol_objetivo_id uuid NOT NULL`; `oferta_laboral varchar(3000) NULL`; `contexto_perfil_snapshot jsonb NOT NULL`; `modalidad varchar(20) NOT NULL`; `tono varchar(20) NOT NULL`; `forma_respuesta varchar(10) NOT NULL`; `idioma varchar(5) NOT NULL`; `estado varchar(20) NOT NULL DEFAULT 'CONFIGURADA'`; `reintentos_planificacion smallint NOT NULL DEFAULT 0`; `fecha_creacion timestamptz NOT NULL`; `fecha_actualizacion timestamptz NOT NULL`; `fecha_ultima_actividad timestamptz NULL`; `fecha_fin timestamptz NULL`; `version bigint NOT NULL DEFAULT 0`. `CHECK`: `ck_sesion_firebase_uid (char_length(btrim(firebase_uid)) > 0)`, `ck_sesion_oferta_laboral (oferta_laboral IS NULL OR char_length(btrim(oferta_laboral)) BETWEEN 1 AND 3000)`, `ck_sesion_contexto_perfil_snapshot (jsonb_typeof(contexto_perfil_snapshot) = 'object')`, `ck_sesion_modalidad (modalidad IN ('SIMULACION','ENTRENO'))`, `ck_sesion_tono (tono IN ('PROFESIONAL_NEUTRO','CALIDO','ESTRICTO','MOTIVADOR'))`, `ck_sesion_forma_respuesta (forma_respuesta IN ('AUDIO','TEXTO'))`, `ck_sesion_idioma (idioma IN ('es-CO','en'))`, `ck_sesion_estado (estado IN ('CONFIGURADA','EN_CURSO','EVALUANDO','FINALIZADA','ABANDONADA'))`, `ck_sesion_reintentos_planificacion (reintentos_planificacion BETWEEN 0 AND 2)`, `ck_sesion_fecha_fin ((estado IN ('ABANDONADA','FINALIZADA')) = (fecha_fin IS NOT NULL))`. Índices: `CREATE UNIQUE INDEX uq_sesion_configurada_por_usuario ON sesion (firebase_uid) WHERE estado = 'CONFIGURADA'`; `CREATE UNIQUE INDEX uq_sesion_en_curso_por_usuario ON sesion (firebase_uid) WHERE estado = 'EN_CURSO'`; `CREATE INDEX ix_sesion_firebase_uid ON sesion (firebase_uid)`; `CREATE INDEX ix_sesion_en_curso_ultima_actividad ON sesion (fecha_ultima_actividad) WHERE estado = 'EN_CURSO'` |
| `pregunta` | `id uuid CONSTRAINT pk_pregunta PRIMARY KEY`; `sesion_id uuid NOT NULL CONSTRAINT fk_pregunta_sesion REFERENCES sesion (id) ON DELETE CASCADE`; `orden smallint NOT NULL`; `tipo varchar(20) NOT NULL CONSTRAINT fk_pregunta_tipo_pregunta REFERENCES tipo_pregunta (codigo) ON DELETE RESTRICT`; `texto_original varchar(1000) NOT NULL`; `fecha_creacion timestamptz NOT NULL`. `ck_pregunta_orden (orden BETWEEN 1 AND 5)`; `ck_pregunta_texto_original (char_length(btrim(texto_original)) BETWEEN 1 AND 1000)` con el comentario SQL «Límite provisional: se ajusta con el esquema del spike de planificación.»; `CONSTRAINT uq_pregunta_sesion_orden UNIQUE (sesion_id, orden)`; `CREATE INDEX ix_pregunta_tipo ON pregunta (tipo)` |
| `turno` | `id uuid CONSTRAINT pk_turno PRIMARY KEY`; `pregunta_id uuid NOT NULL CONSTRAINT fk_turno_pregunta REFERENCES pregunta (id) ON DELETE CASCADE`; `estado varchar(20) NOT NULL DEFAULT 'PENDING'`; `fecha_creacion timestamptz NOT NULL`; `fecha_actualizacion timestamptz NOT NULL`; `version bigint NOT NULL DEFAULT 0`. `CONSTRAINT uq_turno_pregunta UNIQUE (pregunta_id)`; `ck_turno_estado (estado IN ('PENDING','PROCESSING','COMPLETED','RETRYING'))` |
| `intento_respuesta` | `id uuid CONSTRAINT pk_intento_respuesta PRIMARY KEY`; `turno_id uuid NOT NULL CONSTRAINT fk_intento_respuesta_turno REFERENCES turno (id) ON DELETE CASCADE`; `tipo_interaccion varchar(20) NOT NULL`; `es_definitiva boolean NOT NULL`; `origen_respuesta varchar(10) NOT NULL`; `respuesta_texto varchar(4000) NOT NULL`; `pregunta_mostrada varchar(1000) NOT NULL`; `idioma_detectado varchar(10) NULL`; `fecha_creacion timestamptz NOT NULL`. `ck_intento_respuesta_tipo_interaccion (tipo_interaccion IN ('INICIAL'))`; `ck_intento_respuesta_origen_respuesta (origen_respuesta IN ('TEXTO','AUDIO'))`; `ck_intento_respuesta_respuesta_texto (char_length(btrim(respuesta_texto)) BETWEEN 1 AND 4000)`; `ck_intento_respuesta_pregunta_mostrada (char_length(btrim(pregunta_mostrada)) BETWEEN 1 AND 1000)`; `ck_intento_respuesta_idioma_detectado (idioma_detectado IS NULL OR char_length(idioma_detectado) BETWEEN 2 AND 10)`; `ck_intento_respuesta_idioma_origen (origen_respuesta = 'AUDIO' OR idioma_detectado IS NULL)`. `CREATE INDEX ix_intento_respuesta_turno_id ON intento_respuesta (turno_id)`; `CREATE UNIQUE INDEX uq_intento_definitivo_por_turno ON intento_respuesta (turno_id) WHERE es_definitiva` |
| `feedback_turno` | `id uuid CONSTRAINT pk_feedback_turno PRIMARY KEY`; `turno_id uuid NOT NULL CONSTRAINT fk_feedback_turno_turno REFERENCES turno (id) ON DELETE CASCADE`; `feedback jsonb NOT NULL`; `feedback_star jsonb NULL`; `fecha_creacion timestamptz NOT NULL`. `CONSTRAINT uq_feedback_turno_turno UNIQUE (turno_id)`; `ck_feedback_turno_feedback (jsonb_typeof(feedback) = 'object')`; `ck_feedback_turno_feedback_star (feedback_star IS NULL OR jsonb_typeof(feedback_star) = 'object')` |
| `ejecucion_ia` | (además de lo que sigue: `modelo varchar(120) NOT NULL`, `version_prompt varchar(80) NULL`, `error_codigo varchar(80) NULL`, `proveedor varchar(60) NULL`, `tokens_entrada bigint NULL`, `tokens_salida bigint NULL`; `ck_ejecucion_ia_proveedor (proveedor IS NULL OR char_length(btrim(proveedor)) > 0)`, `ck_ejecucion_ia_tokens_entrada (tokens_entrada IS NULL OR tokens_entrada >= 0)`, `ck_ejecucion_ia_tokens_salida (tokens_salida IS NULL OR tokens_salida >= 0)`; `ck_ejecucion_ia_modelo (char_length(btrim(modelo)) > 0)`, `ck_ejecucion_ia_version_prompt ((tipo_operacion = 'TRANSCRIBIR') = (version_prompt IS NULL))`, `ck_ejecucion_ia_error_codigo ((estado = 'FALLIDA') = (error_codigo IS NOT NULL))`) `id uuid CONSTRAINT pk_ejecucion_ia PRIMARY KEY`; `sesion_id uuid NOT NULL CONSTRAINT fk_ejecucion_ia_sesion REFERENCES sesion (id) ON DELETE CASCADE`; `turno_id uuid NULL CONSTRAINT fk_ejecucion_ia_turno REFERENCES turno (id) ON DELETE CASCADE`; `tipo_operacion varchar(20) NOT NULL`; `numero_intento smallint NOT NULL`; `idempotency_key uuid NOT NULL CONSTRAINT uq_ejecucion_ia_idempotency_key UNIQUE`; `estado varchar(20) NOT NULL`; `fecha_inicio timestamptz NOT NULL`; `fecha_fin timestamptz NULL`. `ck_ejecucion_ia_tipo_operacion (tipo_operacion IN ('PLANIFICAR','EVALUAR','TRANSCRIBIR'))`; `ck_ejecucion_ia_numero_intento (numero_intento BETWEEN 1 AND 3)`; `ck_ejecucion_ia_estado (estado IN ('EN_CURSO','EXITOSA','FALLIDA'))`; `ck_ejecucion_ia_fecha_fin ((estado = 'EN_CURSO') = (fecha_fin IS NULL))`; `ck_ejecucion_ia_turno ((tipo_operacion = 'PLANIFICAR') = (turno_id IS NULL))`. `CREATE INDEX ix_ejecucion_ia_sesion_id ON ejecucion_ia (sesion_id)`; `CREATE INDEX ix_ejecucion_ia_turno_id ON ejecucion_ia (turno_id)` |
| `clave_idempotencia` | `id uuid CONSTRAINT pk_clave_idempotencia PRIMARY KEY`; `firebase_uid varchar(128) NOT NULL`; `operacion varchar(40) NOT NULL`; `clave varchar(64) NOT NULL`; `huella_peticion char(64) NOT NULL`; `estado_respuesta smallint NOT NULL`; `cuerpo_respuesta jsonb NOT NULL`; `fecha_creacion timestamptz NOT NULL`. `CONSTRAINT uq_clave_idempotencia_usuario_operacion_clave UNIQUE (firebase_uid, operacion, clave)`; `ck_clave_idempotencia_clave (clave ~ '^[A-Za-z0-9._-]{1,64}$')`; `ck_clave_idempotencia_operacion (char_length(btrim(operacion)) > 0)`; `ck_clave_idempotencia_huella_peticion (huella_peticion ~ '^[0-9a-f]{64}$')`; `ck_clave_idempotencia_estado_respuesta (estado_respuesta BETWEEN 200 AND 599)` |

**Procedencia (P17):** `pregunta` y `feedback_turno` llevan `ejecucion_ia_id uuid NOT NULL`. Como `ejecucion_ia` apunta a `turno` y `turno` a `pregunta`, hay un ciclo: crear las tablas sin esas dos claves foráneas y agregarlas al final con `ALTER TABLE microentrevista.pregunta ADD CONSTRAINT fk_pregunta_ejecucion_ia FOREIGN KEY (ejecucion_ia_id) REFERENCES microentrevista.ejecucion_ia (id) ON DELETE CASCADE;` (y la de `feedback_turno`), más `CREATE INDEX ix_pregunta_ejecucion_ia_id …` y `ix_feedback_turno_ejecucion_ia_id …`. En las pruebas, insertar primero la `ejecucion_ia` de `PLANIFICAR` (sin turno) y después las preguntas.

Disparadores: copiar literalmente el bloque SQL de `spec.md` §5.3 (`fn_rechazar_cambio_pregunta`, `trg_pregunta_inmutable`, `fn_rechazar_cambio_intento`, `trg_intento_append_only`).

**Trampa:** dentro de la migración, califica las tablas con `microentrevista.` o confía en `spring.flyway.default-schema`; el cuerpo de las funciones **siempre** califica (`microentrevista.sesion`), porque corre con el `search_path` de quien actualiza.

**Trampa en las pruebas:** la conexión de `JdbcTemplate` usa el `search_path` por defecto (`public`). Todo SQL de las pruebas califica las tablas (`INSERT INTO microentrevista.sesion …`), como hace Cuentas con `microcuentas.cuenta`. Sin eso, las pruebas fallan con «relation does not exist».

**Disparadores:** son tres, todos en el §5.3 de la spec: `trg_pregunta_inmutable` (cambio), `trg_pregunta_inmutable_borrado` (borrado) y `trg_intento_append_only`.

**Pruebas nuevas** (paquete `tech.cameia.entrevista.infrastructure.persistence`; `@SpringBootTest`, `@Testcontainers(disabledWithoutDocker = true)`, `@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")`; importaciones copiadas de `cameia-cuentas/.../CuentaSchemaMigrationTest.java`; `JdbcTemplate` para insertar):

`InterviewSchemaMigrationTest`, con un ayudante `insertSession(String uid, String estado, Instant fechaFin)` que inserta una sesión válida (`perfil_id` y `rol_objetivo_id` = `UUID.randomUUID()`, `contexto_perfil_snapshot` = `'{}'::jsonb`, `modalidad` `SIMULACION`, `tono` `CALIDO`, `forma_respuesta` `TEXTO`, `idioma` `es-CO`, fechas `now()`), y `insertQuestion(UUID sesion, int orden)` y `insertTurn(UUID pregunta)`.

| Prueba | Datos | Esperado |
|---|---|---|
| `migration_shouldCreateAllTables_whenApplied` | `information_schema.tables` del esquema | las 8 tablas |
| `migration_shouldSeedQuestionTypes_whenApplied` | `SELECT codigo, aplica_star FROM tipo_pregunta` | 4 filas; solo `CONDUCTUAL` con `true` |
| `migration_shouldNameEveryConstraint_whenApplied` | `pg_constraint` del esquema | ningún nombre que termine en `_pkey`, `_fkey`, `_key` ni `_check` |
| `migration_shouldIndexEveryForeignKey_whenApplied` | cada `fk_*` de `pg_constraint` contra `pg_index` | cada clave foránea es la primera columna de algún índice |
| `session_shouldHavePartialIndexOnLastActivity` | `pg_indexes` de `sesion` | existe `ix_sesion_en_curso_ultima_actividad` y su definición contiene `WHERE` con `EN_CURSO`; `EXPLAIN` no se usa |
| `session_shouldHaveNoForeignKeyToOtherServices` | `pg_constraint` de `sesion` | sin clave foránea en `perfil_id` ni `rol_objetivo_id` |
| `shouldRejectUnknownValue_whenCheckedColumn` (parametrizada) | `sesion.estado` = `PAUSADA`, `en_curso`, `''`; `turno.estado` = `FAILED`; `modalidad` = `PRESENCIAL`; `tono` = `AMABLE`; `forma_respuesta` = `VIDEO`; `idioma` = `es`; `intento_respuesta.tipo_interaccion` = `FOLLOW_UP`; `origen_respuesta` = `VOZ`; `ejecucion_ia.tipo_operacion` = `RESUMIR`; `ejecucion_ia.estado` = `CANCELADA` | `DataIntegrityViolationException` con el `ck_` de la columna en el mensaje de la causa |
| `session_shouldAcceptTrainingModality_whenChecked` | `modalidad` = `ENTRENO` | se inserta (P12 B) |
| `notNull_shouldReject_whenColumnMissing` (parametrizada) | una columna `NOT NULL` por tabla en `null` | rechazo |
| `session_shouldRejectSecondConfigured_whenSameUser` | dos `CONFIGURADA` con `uid-a` | segundo rechazado por `uq_sesion_configurada_por_usuario` |
| `session_shouldRejectSecondInProgress_whenSameUser` | dos `EN_CURSO` con `uid-a` | rechazo por `uq_sesion_en_curso_por_usuario` |
| `session_shouldAllowConfiguredAndInProgress_whenSameUser` | una de cada | se insertan |
| `session_shouldAllowManyFinished_whenSameUser` | dos `FINALIZADA` con `fecha_fin` | se insertan |
| `session_shouldAllowSameStateForDifferentUsers` | `CONFIGURADA` para `uid-a` y `uid-b` | se insertan |
| `session_shouldRejectFinishedWithoutEndDate` | `ABANDONADA`, `fecha_fin` nula | rechazo `ck_sesion_fecha_fin` |
| `session_shouldRejectInProgressWithEndDate` | `EN_CURSO` con `fecha_fin` | rechazo |
| `session_shouldAcceptOffer3000` / `…Reject3001` / `…RejectBlankOffer` | `"a".repeat(3000)`, `"a".repeat(3001)`, `"   "` | acepta / rechaza / rechaza |
| `session_shouldRejectPlanningRetries_whenOutOfRange` (parametrizada) | -1 y 3 (0 y 2 aceptados) | rechazo |
| `question_shouldAccept1000CodePoints` | `"😀".repeat(1000)` y `"é".repeat(1000)` | se insertan |
| `question_shouldReject1001CodePoints` | `"é".repeat(1001)` | rechazo |
| `question_shouldRejectOrder_whenOutOfRange` (parametrizada) | 0 y 6 | rechazo |
| `question_shouldRejectDuplicateOrder_whenSameSession` | orden 1 dos veces | rechazo `uq_pregunta_sesion_orden` |
| `question_shouldRejectTextChange_whenSessionStarted` | sesión `EN_CURSO`; `UPDATE pregunta SET texto_original = 'otra'` | excepción cuyo mensaje contiene `pregunta inmutable` |
| `question_shouldRejectTypeChange_whenSessionStarted` / `…OrderChange…` | `tipo` a `TECNICA`; `orden` a 2 | rechazo |
| `question_shouldRejectChange_whenSessionFinished` | sesión `FINALIZADA` | rechazo |
| `question_shouldAllowChange_whenSessionConfigured` | sesión `CONFIGURADA` | se actualiza |
| `question_shouldRejectDelete_whenSessionStarted` | sesión `EN_CURSO`; `DELETE FROM microentrevista.pregunta WHERE id = ?` | rechazo con `pregunta inmutable` |
| `question_shouldAllowCascadeDelete_whenSessionDeleted` | sesión `FINALIZADA` con preguntas; `DELETE FROM microentrevista.sesion` | 0 preguntas (la cascada no se bloquea) |
| `answer_shouldAccept4000` / `…Reject4001` / `…RejectBlank` | `"a".repeat(4000)`, 4001, `"   "` | acepta / rechaza / rechaza |
| `answer_shouldAcceptAudioWithDetectedLanguage` | `AUDIO`, `idioma_detectado = 'en'` | se inserta |
| `answer_shouldRejectDetectedLanguage_whenTextOrigin` | `TEXTO`, `'en'` | rechazo `ck_intento_respuesta_idioma_origen` |
| `answer_shouldRejectDetectedLanguage_whenTooShortOrLong` | `'e'`, `'abcdefghijk'` | rechazo |
| `answer_shouldRejectSecondDefinitive_whenSameTurn` | dos con `es_definitiva = true` | rechazo `uq_intento_definitivo_por_turno` |
| `answer_shouldAllowNonDefinitive_whenSameTurn` | una `true` y una `false` | se insertan |
| `answer_shouldRejectUpdate_always` | `UPDATE intento_respuesta SET respuesta_texto = 'x'` | mensaje `intento de respuesta de solo insercion` |
| `feedback_shouldRejectSecond_whenSameTurn`, `…RejectArray_whenNotObject` (`'[]'::jsonb`), `…AcceptNullStar` | — | según la tabla de T2 |
| `execution_shouldAcceptTranscribeOperation` | `TRANSCRIBIR`, `turno_id` presente, `EN_CURSO`, sin `fecha_fin` | se inserta |
| `execution_shouldRejectPlanWithTurn` / `…EvaluateWithoutTurn` | — | rechazo `ck_ejecucion_ia_turno` |
| `execution_shouldRejectAttempt_whenOutOfRange` (0 y 4) | — | rechazo |
| `execution_shouldRejectFinishedWithoutEndDate` | `EXITOSA` sin `fecha_fin` | rechazo |
| `execution_shouldRejectDuplicateIdempotencyKey` | misma UUID dos veces | rechazo |
| `execution_shouldRequireModel` | `modelo` = `'   '` | rechazo `ck_ejecucion_ia_modelo` |
| `execution_shouldRequireErrorCode_whenFailed` | `FALLIDA` sin `error_codigo`; `EXITOSA` con `error_codigo = 'LLM_UNAVAILABLE'` | ambos rechazados |
| `execution_shouldRejectPromptVersion_whenTranscribe` | `TRANSCRIBIR` con `version_prompt = 'v1'`; `PLANIFICAR` sin `version_prompt` | ambos rechazados |
| `execution_shouldRejectNegativeTokens` | `tokens_entrada = -1`; `tokens_salida = -1`; `proveedor = '  '` | los tres rechazados; con `null`, `0` y `'google-genai'` se insertan |
| `question_shouldRejectMissingExecution` | pregunta con `ejecucion_ia_id` nulo o inexistente | rechazo (`NOT NULL` / `fk_pregunta_ejecucion_ia`) |
| `feedback_shouldRejectMissingExecution` | igual en `feedback_turno` | rechazo |
| `idempotency_shouldRejectSameUserOperationKey` / `…AllowOtherUser` | `uid-a`, `CREAR_SESION`, `k-1` | rechazo / se inserta para `uid-b` |
| `idempotency_shouldRejectInvalidKeyOrFingerprint` (parametrizada) | clave `'a b'`, `"a".repeat(65)`; huella `'XYZ'` | rechazo |
| `session_shouldCascadeDelete_whenConfigured` | sesión con pregunta, turno, intento, feedback y ejecución; `DELETE FROM sesion` | 0 filas hijas |
| `schema_shouldCommentEveryTableAndClosedValueColumn` | `pg_description` sobre las 8 tablas del esquema `microentrevista` y sobre las columnas de la spec §5.4 | ninguna sin comentario; el de `sesion.estado` y el de `turno.estado` coinciden con el texto de la spec |
| `schema_shouldHaveNoBinaryColumns` | `information_schema.columns` con `data_type = 'bytea'` | ninguna (el audio no se guarda) |

`ColumnLengthConsistencyTest` (mismo arranque): para `sesion.firebase_uid` (128), `sesion.oferta_laboral` (3000), `pregunta.texto_original` (1000), `intento_respuesta.respuesta_texto` (4000), `intento_respuesta.pregunta_mostrada` (1000), `intento_respuesta.idioma_detectado` (10) y `clave_idempotencia.clave` (64), compara `character_maximum_length` de `information_schema.columns` con la constante del dominio (`FirebaseUid.MAX_LENGTH`; para las demás, las constantes de `InterviewSession.MAX_JOB_OFFER_LENGTH` y las que agreguen HU-4.5 y HU-5.2: en CM-311 se comparan con literales y un comentario que lo explica).

**Modificar** `src/test/java/tech/cameia/entrevista/EntrevistaApplicationTests.java`: agregar `@Testcontainers(disabledWithoutDocker = true)` y el contenedor con `@ServiceConnection`, igual que arriba.

**Comentarios en la base:** al final de `V1__esquema_inicial_entrevista.sql`, los `COMMENT ON TABLE` y `COMMENT ON COLUMN` de la spec §5.4, con el texto literal de la tabla (sin tildes, comillas simples duplicadas si hiciera falta). Para las columnas con `CHECK ... IN (...)`, `Valores: A, B, C.` con los valores de su `CHECK`.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="InterviewSchemaMigrationTest,ColumnLengthConsistencyTest,EntrevistaApplicationTests"` → todas ejecutadas (no `Skipped`) y en verde.

**Commit:** `CM-311 | feat(datos): esquema inicial de la entrevista con sus restricciones`.

**Resultado (21-oct):** `verify` completo, sin base externa: 106 pruebas, 0 fallos, 0 omitidas (85 de `InterviewSchemaMigrationTest`, 7 de `ColumnLengthConsistencyTest`, 1 de arranque y las 13 anteriores). Dos ajustes frente a la tarjeta, ambos en la prueba `migration_shouldNameEveryConstraint_whenApplied`: (1) la regla «ningún nombre termina en `_key`» choca con `uq_ejecucion_ia_idempotency_key`, que termina en `_key` porque la columna se llama `idempotency_key`; la prueba exige en su lugar que todo nombre empiece por `pk_`, `fk_`, `uq_` o `ck_` (índices: `pk_`, `uq_` o `ix_`), que además detecta los nombres automáticos de PostgreSQL; (2) se excluye `flyway_schema_history`, la tabla de control de Flyway que vive en el mismo esquema. Los disparadores devuelven `UncategorizedSQLException` (no `DataIntegrityViolationException`), así que sus pruebas esperan `DataAccessException` con el texto `pregunta inmutable` o `intento de respuesta de solo insercion`.

---

## T3 · Dominio: estados, agregados, identidad y códigos (REQ-SM-01 a 08, REQ-ID-01)

**Archivos nuevos** en `src/main/java/tech/cameia/entrevista/domain/model/`:

```java
/** Estados de una sesión de entrevista y las transiciones que admite la máquina de estados. */
public enum SessionStatus {
    CONFIGURED("CONFIGURADA"), IN_PROGRESS("EN_CURSO"), EVALUATING("EVALUANDO"),
    FINISHED("FINALIZADA"), ABANDONED("ABANDONADA");

    private final String code;  // valor guardado en la base y publicado en el contrato

    /** @return el valor del backlog que se guarda en {@code sesion.estado} */
    public String code() { … }

    /** @return el estado cuyo valor es {@code code}; @throws IllegalArgumentException si no existe */
    public static SessionStatus fromCode(String code) { … }

    /**
     * Indica si la máquina admite pasar de este estado a {@code target}. Solo hay cuatro transiciones;
     * borrar una sesión configurada no es una transición (ver {@link InterviewSession#requireDeletable()}).
     */
    public boolean canChangeTo(SessionStatus target) {
        return switch (this) {
            case CONFIGURED -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == ABANDONED || target == EVALUATING;
            case EVALUATING -> target == FINISHED;
            case FINISHED, ABANDONED -> false;
        };
    }
}
```

`TurnStatus` con `PENDING`, `PROCESSING`, `COMPLETED`, `RETRYING` (el `code()` es el mismo nombre) y `canChangeTo`: `PENDING → PROCESSING`; `PROCESSING → COMPLETED | RETRYING`; `RETRYING → PROCESSING`; `COMPLETED → nada`.

`Modality` (`SIMULATION("SIMULACION")`, `TRAINING("ENTRENO")`), `Tone` (`PROFESSIONAL_NEUTRAL("PROFESIONAL_NEUTRO")`, `WARM("CALIDO")`, `STRICT("ESTRICTO")`, `MOTIVATING("MOTIVADOR")`), `AnswerMode` (`AUDIO("AUDIO")`, `TEXT("TEXTO")`), `InterviewLanguage` (`SPANISH_COLOMBIA("es-CO")`, `ENGLISH("en")`): mismo patrón `code()`/`fromCode`.

`FirebaseUid` (`record FirebaseUid(String value)`): `public static final int MAX_LENGTH = 128`; `public static FirebaseUid required(String raw)` lanza `IdentityRequiredException` si `raw` es `null`, vacío, solo espacios, de más de 128 caracteres o con algún carácter `Character.isISOControl`. No recorta el valor.

`SessionConfiguration` (`record`): `UUID profileId, UUID targetRoleId, String jobOffer, String profileSnapshotJson, Modality modality, Tone tone, AnswerMode answerMode, InterviewLanguage language`; constructor compacto con `Objects.requireNonNull` en todo salvo `jobOffer`. La validación de contenido (catálogo, 3000 caracteres) es de HU-4.4. Constante `InterviewSession.MAX_JOB_OFFER_LENGTH = 3000`.

`InterviewSession` (clase, agregado):

```java
public final class InterviewSession {
    public static final int MAX_JOB_OFFER_LENGTH = 3000;
    // campos: UUID id; FirebaseUid owner; SessionConfiguration configuration; SessionStatus status;
    // int planningRetries; Instant createdAt, updatedAt, lastActivityAt, finishedAt; Long version (null si es nueva)

    /** Crea una sesión nueva en CONFIGURED. */
    public static InterviewSession configure(UUID id, FirebaseUid owner, SessionConfiguration configuration, Clock clock)
    /** Reconstruye desde la persistencia, sin validar transiciones. */
    public static InterviewSession reconstitute(SessionSnapshot snapshot)   // record con todos los campos
    public void start(Clock clock)            // CONFIGURED → IN_PROGRESS; lastActivityAt = now
    public void abandon(Clock clock)          // IN_PROGRESS → ABANDONED; finishedAt = now
    public void startEvaluation(Clock clock)  // IN_PROGRESS → EVALUATING
    public void finish(Clock clock)           // EVALUATING → FINISHED; finishedAt = now
    public void requireDeletable()            // solo CONFIGURED; si no, SessionTransitionNotAllowedException
    private void changeTo(SessionStatus target, Clock clock) {
        // Se valida antes de tocar ningún campo: una transición rechazada deja el agregado igual.
        if (!status.canChangeTo(target)) throw new SessionTransitionNotAllowedException();
        status = target; updatedAt = clock.instant();
    }
    // getters de todos los campos
}
```

`SessionSnapshot` (`record`): `id, owner, configuration, status, planningRetries, createdAt, updatedAt, lastActivityAt, finishedAt, version`. **`version` es `Long` y vale `null` en una sesión recién configurada**: así el adaptador sabe que es nueva (T4). Lo mismo en `InterviewTurn` y `TurnSnapshot`.

`InterviewTurn`: campos `UUID id`, `UUID questionId`, `TurnStatus status`, `Instant createdAt`, `Instant updatedAt`, `Long version`; `static InterviewTurn reconstitute(...)` (≤ 3 parámetros: un `record TurnSnapshot`); métodos `startProcessing(Clock)`, `complete(Clock)`, `retry(Clock)`, `resumeProcessing(Clock)` con el mismo `changeTo` y `TurnTransitionNotAllowedException`.

`IdempotencyKey` (`record`): `UUID id, FirebaseUid owner, String operation, String key, String requestFingerprint, int responseStatus, String responseBodyJson, Instant createdAt`; sin reglas (Q11).

**Excepciones nuevas** en `domain/exception/`, todas `extends BusinessException`, con constante `MESSAGE` y constructor sin parámetros:

| Clase | Código | Mensaje |
|---|---|---|
| `SessionTransitionNotAllowedException` | `SESSION_TRANSITION_NOT_ALLOWED` | «Esta entrevista ya no admite esa acción.» |
| `TurnTransitionNotAllowedException` | `TURN_TRANSITION_NOT_ALLOWED` | «Esta pregunta ya no admite esa acción.» |
| `SessionConflictException` | `SESSION_CONFLICT` | «La entrevista cambió mientras se procesaba tu solicitud. Inténtalo de nuevo.» |
| `SessionAlreadyExistsException` | `SESSION_ALREADY_EXISTS` | «Ya tienes una entrevista en preparación o en curso. Inténtalo de nuevo.» |
| `IdempotencyKeyAlreadyExistsException` | `IDEMPOTENCY_KEY_ALREADY_EXISTS` | «Esta solicitud ya se registró.» |
| `IdentityRequiredException` | `IDENTITY_REQUIRED` | «Tu sesión expiró. Inicia sesión de nuevo.» |
| `DatabaseUnavailableException` | `DATABASE_UNAVAILABLE` | «Ocurrió un error. Inténtalo de nuevo.» (constructor con `Throwable cause`) |

**`ErrorCode`:** agregar **al final**, sin reordenar lo de CM-312, con un comentario Javadoc de una línea cada uno: `VALIDATION_FAILED`, `REQUEST_BODY_INVALID_FORMAT`, `REQUEST_INVALID_VALUE`, `IDENTITY_REQUIRED`, `ROUTE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `MEDIA_TYPE_NOT_ALLOWED`, `MEDIA_TYPE_NOT_ACCEPTABLE`, `DATABASE_UNAVAILABLE`, `SESSION_TRANSITION_NOT_ALLOWED`, `TURN_TRANSITION_NOT_ALLOWED`, `SESSION_CONFLICT`, `SESSION_ALREADY_EXISTS`, `IDEMPOTENCY_KEY_ALREADY_EXISTS`.

**Trampa:** `ErrorCodeDocumentationTest` (de CM-312) fallará hasta T8, porque los códigos nuevos aún no están en `docs/errores.md`. Para que este commit compile y pase, agrega en T3 las 14 filas de la tabla de T8 a `docs/errores.md` (T8 solo revisa su redacción).

**Pruebas** (`src/test/java/tech/cameia/entrevista/domain/model/`, sin Spring, reloj fijo `Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneOffset.UTC)`):

| Clase | Prueba | Esperado |
|---|---|---|
| `SessionStatusTest` | `transition_shouldBeAllowed_whenListed` con `@CsvSource` de las 4 válidas | `true` |
| | `transition_shouldBeRejected_whenNotListed`: las 21 restantes de las 25 combinaciones (`@MethodSource` que recorre `values() × values()` menos las válidas) | `false` |
| | `fromCode_shouldMapEveryValue` y `fromCode_shouldThrow_whenUnknown` (`"PAUSADA"`) | — |
| `TurnStatusTest` | igual: 4 válidas y 12 inválidas (incluye `RETRYING → COMPLETED` y `PROCESSING → PENDING`, P4) | — |
| `InterviewSessionTest` | `configure_shouldStartConfigured` | `CONFIGURED`, `planningRetries` 0, fechas = reloj |
| | `start_shouldMoveToInProgress_andTouchActivity` | `IN_PROGRESS`, `lastActivityAt = 2026-10-20T15:00:00Z` |
| | `abandon_shouldSetFinishedAt`, `finish_shouldSetFinishedAt`, `startEvaluation_shouldMoveToEvaluating` | — |
| | `start_shouldThrowTransitionNotAllowed_whenInProgress` y una por método inválido (`abandon` en `CONFIGURED`, `finish` en `IN_PROGRESS`, `startEvaluation` en `FINISHED`) | excepción y el estado **sin cambio** (`assertThat(s.status()).isEqualTo(antes)`) |
| | `requireDeletable_shouldPass_whenConfigured`; `requireDeletable_shouldThrow_whenNotConfigured` (parametrizada en 4 estados) | — |
| `InterviewTurnTest` | una por método válido y una por inválido, comprobando que el estado no cambia | — |
| `FirebaseUidTest` | `null`, `""`, `"   "`, `"u".repeat(129)`, `"uid\u0007"` → excepción; `"u".repeat(128)` y `"uid-a"` → aceptados con el mismo valor | — |
| `ErrorCodeTest` (`domain/exception`) | cada miembro tiene una fila en la tabla de códigos de `BusinessExceptionHandler` (T5) o es de CM-312 | se completa en T5 |

**Commit:** `CM-311 | feat(dominio): estados de la sesión y del turno con sus transiciones`.

**Resultado (22-oct):** hecho. Pruebas del dominio (`SessionStatusTest` 34, `TurnStatusTest` 22, `InterviewSessionTest` 16, `InterviewTurnTest` 9, `FirebaseUidTest` 14, `EnumeratedValuesTest` 5, `BusinessExceptionsTest` 8, `ErrorCodeDocumentationTest` 17) → 125 pruebas, 0 fallos, BUILD SUCCESS. Ajustes frente a la tarjeta: `FirebaseUid` cuenta puntos de código (como `varchar`), no unidades UTF-16; las filas de `docs/errores.md` incluyen también los 3 códigos de CM-312, que la prueba exige; `ErrorCodeTest` se completa en T5.

---

## T4 · Persistencia (REQ-PER-01 a 05)

**Puertos** (`domain/port/`, con Javadoc):

```java
public interface InterviewSessionRepository {
    /** Guarda la sesión. @throws SessionConflictException si otra escritura la cambió antes;
     *  @throws SessionAlreadyExistsException si el usuario ya tiene otra CONFIGURED o IN_PROGRESS */
    InterviewSession save(InterviewSession session);
    Optional<InterviewSession> findById(UUID id);
    /** Borra la sesión solo si sigue CONFIGURED. @throws SessionTransitionNotAllowedException si no */
    void deleteConfigured(UUID id);
}
public interface InterviewTurnRepository {
    InterviewTurn save(InterviewTurn turn);   // @throws SessionConflictException
    Optional<InterviewTurn> findById(UUID id);
}
public interface IdempotencyKeyRepository {
    Optional<IdempotencyKey> find(FirebaseUid owner, String operation, String key);
    IdempotencyKey save(IdempotencyKey key);   // @throws IdempotencyKeyAlreadyExistsException
}
```

**Entidades** (`infrastructure/persistence/entity/`): `InterviewSessionEntity` (`@Table(name = "sesion")`), `InterviewTurnEntity` (`"turno"`), `IdempotencyKeyEntity` (`"clave_idempotencia"`). Cada columna con `@Column(name = …, length = …)` igual a T2; `@Version` en `version`; `contexto_perfil_snapshot` y `cuerpo_respuesta` con `@JdbcTypeCode(SqlTypes.JSON)` sobre un `String`; los enumerados con `@Convert(converter = …)` (no `@Enumerated`). Un conversor por enumerado en `entity/converter/`, `autoApply = false`, que usa `code()` y `fromCode()`.

**Adaptadores** (`infrastructure/persistence/repository/`, clases de paquete con `@Repository`), con `XJpaRepository extends JpaRepository<XEntity, UUID>`:

- `save`: `jpa.saveAndFlush(mapper.toEntity(...))` dentro de `try`:
  - `catch (ObjectOptimisticLockingFailureException e)` → `throw new SessionConflictException()`.
  - `catch (DataIntegrityViolationException e)`: si la causa nombra `uq_sesion_configurada_por_usuario` o `uq_sesion_en_curso_por_usuario`, `SessionAlreadyExistsException`; en el de claves, si nombra `uq_clave_idempotencia_usuario_operacion_clave`, `IdempotencyKeyAlreadyExistsException`; si no, `throw e`.
  - Copiar de Perfil `ProfessionalProfileRepositoryAdapter` el recorrido de causas acotado (`MAX_CAUSES = 32`) para leer el nombre de la restricción de `org.hibernate.exception.ConstraintViolationException#getConstraintName`.
- `deleteConfigured`: `@Modifying @Query("delete from InterviewSessionEntity s where s.id = :id and s.status = :status")` con `SessionStatus.CONFIGURED`; si devuelve 0, `SessionTransitionNotAllowedException`.
- Los mapeadores (`infrastructure/persistence/mapper/`) son clases `final` con métodos estáticos `toEntity` y `toDomain`, a mano.

**Trampa:** con `@Version` y `saveAndFlush` de una entidad nueva, Spring Data usa `merge` si el id ya viene puesto: marca la entidad como nueva con `Persistable#isNew()` basado en `version == null` (usa `Long version`, no `long`) para que la primera inserción sea `persist`.

**Pruebas** (`infrastructure/persistence/`, Testcontainers como en T2; `@Transactional(propagation = NOT_SUPPORTED)` para ver los choques reales):

| Prueba | Datos | Esperado |
|---|---|---|
| `InterviewSessionRepositoryAdapterTest.save_shouldRoundTrip_whenNew` | `configure(...)` con `jobOffer = null`, `ENGLISH`, `TEXT` | `findById` devuelve los mismos valores; `version` 0 |
| `…save_shouldAllowStartAfterAbandon_inSameTransaction` (REQ-PER-06) | `uid-a` con una sesión `EN_CURSO` (A) y otra `CONFIGURADA` (B); en un `TransactionTemplate`: `abandon` y `save` de A, luego `start` y `save` de B | confirma; A `ABANDONADA` con `fecha_fin`, B `EN_CURSO` |
| `…save_shouldRejectStartBeforeAbandon_inSameTransaction` | mismo caso con el orden inverso | `SessionAlreadyExistsException`; tras el rollback, A sigue `EN_CURSO` y B `CONFIGURADA` |
| `…save_shouldThrowConflict_whenStaleVersion` | leer dos veces la misma sesión, `start` y guardar la primera, `start`… guardar la segunda | `SessionConflictException`; en la base, `version` = 1 |
| `…save_shouldThrowSessionAlreadyExists_whenSecondConfigured` | dos sesiones nuevas de `uid-a` | segunda: `SessionAlreadyExistsException`; 1 fila |
| `…deleteConfigured_shouldDelete_whenConfigured` | — | 0 filas |
| `…deleteConfigured_shouldThrow_whenInProgress` | sesión pasada a `EN_CURSO` por SQL | excepción; la fila sigue |
| `…delete_shouldThrow_whenStateChangedConcurrently` | leer la sesión `CONFIGURADA`; pasarla a `EN_CURSO` por SQL; llamar `deleteConfigured` | excepción |
| `InterviewTurnRepositoryAdapterTest.save_shouldPersistTransition` y `…_shouldThrowConflict_whenStaleVersion` | turno insertado por SQL (con sesión y pregunta) | — |
| `IdempotencyKeyRepositoryAdapterTest.save_shouldThrow_whenSameUserOperationAndKey`, `…find_shouldReturnEmpty_whenOtherUser`, `…save_shouldRoundTrip` | `uid-a`, `CREAR_SESION`, `k-1`, huella `"a".repeat(64)`, 201, `{"id":"x"}` | — |

**Commit:** `CM-311 | feat(persistencia): sesión, turno y claves anti-duplicados con control de versión`.

**Resultado (22-oct):** hecho. `InterviewSessionRepositoryAdapterTest` 15, `InterviewTurnRepositoryAdapterTest` 4, `IdempotencyKeyRepositoryAdapterTest` 5 → 24 pruebas, 0 fallos, 0 omitidas, BUILD SUCCESS, con `ddl-auto=validate` contra PostgreSQL 16 real. Ajustes frente a la tarjeta: (1) el recorrido de causas acotado vive en `ConstraintNames` y lo comparten los tres adaptadores, en vez de copiarse en cada uno; (2) `save` devuelve la sesión leída de lo guardado (con su versión); (3) no hizo falta `Persistable`: Spring Data trata la entidad con `version` nula como nueva; (4) cada clase de prueba declara su propio contenedor (el contexto de Spring queda en caché y un contenedor compartido en una clase base dejaba el contexto apuntando a uno detenido: la primera ejecución se colgó con `Connection refused`); (5) pruebas extra: oferta de 3000 caracteres y JSON con Unicode, versión borrada, segunda sesión en curso, borrado con hijos en cascada, restricción distinta que se propaga sin traducir.

---

## T5 · Manejador de errores e identidad (REQ-ERR-01 a 12, REQ-ID-01 a 03)

**Archivo:** `src/main/java/tech/cameia/entrevista/presentation/advice/BusinessExceptionHandler.java` (sembrado por CM-312). Se agregan métodos; los tres manejadores de CM-312 (`LlmUnavailableException`, `LlmLimitReachedException` y `Exception`, todos 5xx) y el armado común (`problema(...)`, `resolverRequestId()`, `nuevoRequestId(...)`) **no se modifican**.

1. Una tabla `static final Map<ErrorCode, Definition>` (`record Definition(HttpStatus status, String title)`) para los códigos de dominio: `SESSION_TRANSITION_NOT_ALLOWED` 409 «Acción no permitida»; `TURN_TRANSITION_NOT_ALLOWED` 409 «Acción no permitida»; `SESSION_CONFLICT` 409 «Cambio simultáneo»; `SESSION_ALREADY_EXISTS` 409 «Entrevista existente»; `IDEMPOTENCY_KEY_ALREADY_EXISTS` 409 «Solicitud repetida»; `IDENTITY_REQUIRED` 401 «Identidad requerida» (mismo título que Perfil).
2. `@ExceptionHandler(BusinessException.class) ProblemDetail negocio(BusinessException e)`: busca la definición; si no existe, registra `ERROR` «Código sin definición: {}» y responde 500 `INTERNAL_ERROR` (una prueba impide que pase). Usa `e.getMessage()` como `detail` (los mensajes de negocio están escritos para la persona). 4xx en `WARN`.
3. `MethodArgumentNotValidException` → 422 `VALIDATION_FAILED`, igual que Cuentas (`camposInvalidos`): un elemento por campo, el primero de cada uno; el `code` de cada campo sale de la carga dinámica `ErrorCode` del `ConstraintViolation` o, si no la tiene, `REQUEST_INVALID_VALUE` registrado en `ERROR`.
4. `HttpMessageNotReadableException` → 422 `REQUEST_BODY_INVALID_FORMAT`; registra solo la clase más específica de la causa.
5. `MissingRequestHeaderException`: si el encabezado es `X-User-Id` → 401 `IDENTITY_REQUIRED` con el mensaje de `IdentityRequiredException.MESSAGE`; si es otro → 422 `REQUEST_INVALID_VALUE` «Revisa los datos enviados.». Se registra el nombre del encabezado, nunca su valor.
6. `MethodArgumentTypeMismatchException` → 422 `REQUEST_INVALID_VALUE`, registrando solo el nombre del parámetro.
7. `NoResourceFoundException` 404, `HttpRequestMethodNotSupportedException` 405 (con `Allow`), `HttpMediaTypeNotSupportedException` 415, `HttpMediaTypeNotAcceptableException` 406: copiar los cuatro métodos de Cuentas (`rutaInexistente`, `metodoNoPermitido`, `tipoDeContenidoNoAdmitido`, `responseTypeNotAcceptable`) con los códigos y textos de la spec §6.
8. `@ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class, CannotGetJdbcConnectionException.class})` → 503 `DATABASE_UNAVAILABLE`, «Ocurrió un error. Inténtalo de nuevo.», `ERROR` con traza.
9. `DataIntegrityViolationException` → 500 `INTERNAL_ERROR`; log con el nombre de la restricción y el SQLState, nunca el mensaje (copiar `integridadDeDatos`, `restriccionDe`, `sqlStateOf` de Cuentas).
10. Cada 4xx se registra en `WARN` con `status`, `code`, `requestId` y `firebaseUid` (REQ-ERR-11). Agregar un método privado `firebaseUidParaLog()` (valor de `X-User-Id` si `FirebaseUid.required` lo acepta, si no `-`) y usarlo solo en los manejadores nuevos de 4xx. Los 5xx de CM-312 no llevan `firebaseUid`, así que no se tocan.

**Identidad:** no hay ruta de negocio en CM-311. El patrón para los controladores futuros (Javadoc del `package-info` de `presentation.controller`): `@Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid` y `FirebaseUid.required(uid)` como primera línea del caso de uso.

**Pruebas** (`src/test/java/tech/cameia/entrevista/presentation/advice/`):

- `ProblemDetailTestSupport` (copiado de Cuentas) y `ErrorProbeController` (`@RestController @RequestMapping("/test/errors")`, **solo en `src/test`**), con: `GET /identity` (lee `X-User-Id` con `required = false`, llama `FirebaseUid.required` y devuelve `{"uid": …}`); `POST /body` con `@Valid ProbeRequest(@NotBlank String name, @NotBlank String city)`, `consumes = application/json`; `GET /business/{code}` que lanza la excepción del código; `GET /database/{kind}` que lanza cada una de las tres excepciones de base; `GET /integrity` que lanza `DataIntegrityViolationException("x")`; `GET /boom` que lanza `IllegalStateException("detalle interno")`; `GET /ok` que responde 200; `GET /typed/{id}` con `@PathVariable UUID id`; `GET /header` con `@RequestHeader("X-Trace")`.
- `BusinessExceptionHandlerTest` (MockMvc `standaloneSetup(new ErrorProbeController()).setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())`):

| Prueba | Petición | Esperado |
|---|---|---|
| `businessException_shouldUseCatalog` (parametrizada por los 6 códigos de la tabla 1) | `GET /test/errors/business/{code}` | estado, `title`, `detail`, `code` de la tabla; `Content-Type` `application/problem+json;charset=UTF-8`; `requestId` no vacío |
| `validation_shouldReturn422WithEveryField_whenTwoFieldsInvalid` | `POST /body` con `{}` | 422, `VALIDATION_FAILED`, `detail` «Revisa los campos marcados.», `errors` con `name` y `city` |
| `unreadableBody_shouldReturn422_whenJsonMalformed` | `{"name":` | 422 `REQUEST_BODY_INVALID_FORMAT` |
| `unsupportedMediaType_shouldReturn415` | `text/plain` | 415 `MEDIA_TYPE_NOT_ALLOWED` |
| `missingHeader_shouldReturn422_whenNotIdentity` | `GET /header` sin `X-Trace` | 422 `REQUEST_INVALID_VALUE` |
| `typeMismatch_shouldReturn422_whenPathIsNotUuid` | `GET /typed/abc` | 422 `REQUEST_INVALID_VALUE`; el cuerpo no contiene `abc` |
| `databaseDown_shouldReturn503` (3 casos) | `GET /database/{kind}` | 503 `DATABASE_UNAVAILABLE` |
| `integrity_shouldReturn500Generic` | `GET /integrity` | 500 `INTERNAL_ERROR` |
| `unexpected_shouldReturn500Generic_whenAnyException` | `GET /boom` | 500; el cuerpo no contiene `detalle interno`, `Exception` ni `trace` |
| `logs_shouldNotContainBodyOrValues` (`@ExtendWith(OutputCaptureExtension.class)`) | `POST /body` con `{"name":"secreto-123"}` y `X-User-Id: uid-a` | el log contiene `VALIDATION_FAILED` y `uid-a`, no contiene `secreto-123` |
| `everyDomainCode_shouldHaveDefinition` | cada `ErrorCode` que no es de framework ni de CM-312 | está en la tabla del punto 1 |

- `IdentityHeaderTest` (mismo MockMvc): los 6 casos de §7 de la spec contra `GET /test/errors/identity` (401 `IDENTITY_REQUIRED` con «Tu sesión expiró. Inicia sesión de nuevo.»; 200 con 128 caracteres) y `shouldIgnoreBodyIdentity` (un `POST` de prueba con `{"firebaseUid":"otro"}` devuelve `uid-a`).
- `RequestIdTest`: los 3 casos de `X-Request-Id` de §7 (resolución del identificador por el manejador, sin filtro: MockMvc `standaloneSetup`).
- `FrameworkErrorsTest` (servidor real, `RANDOM_PORT`, Testcontainers; copiar la forma de Cuentas): `unknownRoute_shouldReturn404WithCode` (`GET /ruta-que-no-existe`), `wrongMethod_shouldReturn405WithAllow` (`POST /health`, `Allow` contiene `GET`), `xmlAccept_shouldReturn406WithCode` (`GET /health`, `Accept: application/xml`), `everyError_shouldDeclareCharset`, `noError_shouldRevealInternals` (sin `trace`, `exception`, `org.springframework`).
- `UntypedExceptionClassificationTest` (ArchUnit, copiado de Cuentas): toda clase de `domain.exception` salvo `ErrorCode` hereda de `BusinessException`.
- `ErrorCodeTest`: cada miembro de `ErrorCode` está en la tabla del manejador, en el conjunto de códigos de framework (`VALIDATION_FAILED`, `REQUEST_BODY_INVALID_FORMAT`, `REQUEST_INVALID_VALUE`, `ROUTE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `MEDIA_TYPE_NOT_ALLOWED`, `MEDIA_TYPE_NOT_ACCEPTABLE`, `DATABASE_UNAVAILABLE`, `INTERNAL_ERROR`) o en el de CM-312.

**Commit:** `CM-311 | feat(errores): manejador completo con identidad del Gateway`.

---

## T5b · `requestId` en cada petición y en cada línea del registro (REQ-ERR-13 a 15; decisión P19)

**Antes:** abrir `BusinessExceptionHandler` (de CM-312) y anotar de dónde lee el `requestId` en `resolverRequestId()` (atributo de petición, encabezado o ambos) y cómo se llama ese atributo. El filtro guarda el valor **en ese mismo lugar**. Si el manejador no lee ningún atributo y solo lee el encabezado de la petición, **detenerse y reportar**: no se modifica el armado común de CM-312 sin acordarlo.

**Archivos nuevos**

- `src/main/java/tech/cameia/entrevista/presentation/filter/RequestIdFilter.java` y `package-info.java`.
- `src/test/java/tech/cameia/entrevista/presentation/filter/RequestIdFilterTest.java`.

**`RequestIdFilter`:** `@Component @Order(Ordered.HIGHEST_PRECEDENCE)`, extiende `OncePerRequestFilter`. Constantes: `REQUEST_ID_HEADER = "X-Request-Id"`, `MDC_KEY = "requestId"` y el patrón `^[A-Za-z0-9._-]{1,64}$` precompilado. En `doFilterInternal`:

1. Leer el encabezado; si es nulo, no cumple el patrón o está en blanco, generar `UUID.randomUUID().toString()`.
2. Guardarlo como atributo de petición (el nombre anotado en «Antes»).
3. `MDC.put(MDC_KEY, valor)`.
4. `response.setHeader(REQUEST_ID_HEADER, valor)` antes de continuar la cadena.
5. `try { chain.doFilter(request, response); } finally { MDC.remove(MDC_KEY); }`.

El filtro no registra nada. Javadoc con el motivo y el formato; sin referencias a CM-311.

**`application.properties`:** agregar `logging.pattern.level=%5p [%X{requestId:-}]`.

**Pruebas** (`RequestIdFilterTest`, `@SpringBootTest(webEnvironment = RANDOM_PORT)` con Testcontainers y la propiedad del modelo, `@ExtendWith(OutputCaptureExtension.class)`; usa el `ErrorProbeController` de T5, que debe tener `GET /test/errors/boom` y `GET /test/errors/ok` con 200):

| Prueba | Entrada | Espera |
|---|---|---|
| `requestId_shouldBeEchoed_whenHeaderValid` | `GET /health` con `X-Request-Id: abc-123` | 200; encabezado de respuesta `abc-123` |
| `requestId_shouldBeGenerated_whenHeaderMissing` | `GET /health` sin encabezado | encabezado de respuesta con forma de UUID |
| `requestId_shouldBeReplaced_whenHeaderInvalid` (parametrizada) | 65 caracteres `a`; `con espacio`; `ñandú`; `a%0d%0ab`; vacío | encabezado distinto del enviado y con forma de UUID |
| `requestId_shouldAcceptBoundary_when64Characters` | 64 caracteres `a` | encabezado igual al enviado |
| `requestId_shouldMatchBody_whenErrorResponse` | `GET /test/errors/boom` con `X-Request-Id: abc-123` | cuerpo `requestId` = `abc-123` = encabezado |
| `requestId_shouldAppearInLogLine_whenFilterRuns` | `GET /test/errors/boom` con `X-Request-Id: abc-123` | la salida capturada tiene una línea con `[abc-123]` |
| `mdc_shouldBeEmpty_afterRequest` | llamada directa al filtro con `MockHttpServletRequest` y una cadena que termina bien | `MDC.get("requestId")` es nulo después |
| `mdc_shouldBeEmpty_whenChainThrows` | misma llamada con una cadena que lanza `ServletException` | `MDC.get("requestId")` es nulo después |

**Trampa:** el filtro no actúa en el despacho de errores internos de la plataforma (`ERROR dispatch`); eso es correcto, porque el manejador resuelve su propio identificador. Si `requestId_shouldMatchBody_whenErrorResponse` falla porque cuerpo y encabezado difieren, el atributo de «Antes» está mal elegido.

**Esperado:** `./mvnw.cmd -B -ntp test -Dtest=RequestIdFilterTest,RequestIdTest,BusinessExceptionHandlerTest` en verde (anotar el resultado real).

**Commit:** `CM-311 | feat(logs): requestId en cada petición y en cada línea del registro`.

---

## T6 · Prueba de arquitectura (REQ-NF-02)

**Archivo nuevo:** `src/test/java/tech/cameia/entrevista/architecture/LayeredArchitectureTest.java`: copiar el de Cuentas cambiando `BASE_PACKAGE` a `"tech.cameia.entrevista"` y la lista de tecnologías prohibidas en `domain` a `"jakarta.persistence.."`, `"org.springframework.."`, `"com.google.."`, `"org.hibernate.."`. Nombres de método en inglés: `domain_shouldNotDependOnOuterLayers`, `domain_shouldNotKnowTechnology`, `application_shouldNotDependOnInfrastructureOrPresentation`.

**Esperado:** verde. Si falla por una clase de CM-312, detenerse y reportar (no se corrige código de Vela sin acordarlo).

**Commit:** `CM-311 | test(arquitectura): reglas de dependencias entre capas`.

---

## T7 · Postman (P6)

**Archivos nuevos:** `postman/cameia-entrevista.postman_collection.json` (v2.1, UTF-8, carpeta «Salud» y carpeta «Errores del framework»), `postman/local.postman_environment.json` (`baseUrl` = `http://localhost:8083`), `postman/README.md` (cómo levantar la base, empaquetar, arrancar y correr Newman; en español).

Peticiones y `pm.test` exactos: las 4 de la spec §12. La primera (`GET /health` con `X-Request-Id: postman-1`) comprueba que el encabezado de respuesta `X-Request-Id` vale `postman-1` (lo garantiza `RequestIdFilter`). Cada error comprueba `pm.response.code`, `json.code`, `json.requestId` presente, `Content-Type` que contiene `application/problem+json` y `charset=UTF-8`, y que el cuerpo no contiene `trace` ni `exception`.

**Comando:** con la base de compose y el jar arrancado (`SERVER_PORT=8083`): `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --reporters cli,junit --reporter-junit-export ../../ejecucion/evidencia/CM-311/newman.xml` → 4 peticiones, 0 fallos. Guardar la salida en `ejecucion/evidencia/CM-311/newman.txt`.

**Commit:** `CM-311 | test(postman): colección de salud y errores del framework`.

---

## T8 · Documentación

1. `docs/errores.md`: las 14 filas de la spec §6 con las columnas de CM-312 (`Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba`) y la fila empezando por el código entre comillas invertidas, que es lo que lee `ErrorCodeDocumentationTest`; sección «Respuestas sin código»: ninguna.
2. `docs/adr/0006-estados-y-esquema-del-motor-de-entrevista.md` (estado, contexto, decisión, consecuencias, alternativas descartadas): las máquinas de estados (P4, Q23), borrar no es un estado (Q5), sin identidad 401 (P2), cuerpo ilegible 422 (P3), enumerados en inglés con valor del backlog (I-1), `jsonb` para la retroalimentación (P8) y el límite provisional de 1000 caracteres (P7).
3. `docs/bitacora-ia/03_motor-base-entrevista_prompt.md` + fila en el README: la entrada de la decisión con peso humano de esta CM (P12: Paula eligió que la base admita `ENTRENO`, contra la recomendación; y P5, P7 y P13 con sus matices). La salida es esta spec.
4. `CLAUDE.md`: sección 2 (qué existe ahora en el árbol), 4 (el manejador existe), 5 (esquema `microentrevista`, Flyway, `ddl-auto=validate`), 7 (Testcontainers y ArchUnit existen), 9 (un PR por CM, sin tope de líneas), 10 (quitar los pendientes cumplidos; dejar el renombre del perfil como decisión de DevOps en la tarea del entorno único).
5. `package-info.java` en cada paquete nuevo de `src/main`.

**Commit:** `CM-311 | docs(entrevista): catálogo de errores, ADR 0006 y CLAUDE.md al día`.

---

## T9 · Verificación única sobre la punta

1. Docker encendido. `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`; en `target/surefire-reports`, ninguna clase de Testcontainers con `Skipped`.
2. `target/site/jacoco/jacoco.csv`: ≥ 90 % de líneas y ramas por cada clase nueva o modificada; cada línea o rama sin cubrir, con su razón.
3. Newman (T7).
4. Recorrer la lista de verificación de la spec §18 fila por fila.
5. `docs/verificaciones/V-01-esquema-inicial-entrevista.md` (en el repo, sin secretos ni valores de variables): qué se quería saber (si el esquema, las máquinas de estados y el manejador cumplen los 7 criterios contra PostgreSQL 16 real), conclusión, versión y entorno (SHA verificado, Java, imagen `postgres:16-alpine`, Docker, Maven, Newman), números reales (pruebas ejecutadas por clase, cobertura por clase, Newman 4/4), lo que no se pudo ejecutar, y el plan de consulta del índice `ix_sesion_en_curso_ultima_actividad` (`EXPLAIN` con `SET enable_seqscan = off` para comprobar que el índice es utilizable). Commit: `CM-311 | docs(verificaciones): verificación del esquema inicial de la entrevista`.
6. `ejecucion/evidencia/CM-311/resumen.md`: qué se hizo por tarjeta, cobertura por clase, commits, tabla R1 a R14 → evidencia, y la tabla de atributos de la spec §11 con el resultado real.
7. Explicar a Paula en lenguaje llano y detenerse (sin push ni PR).

## Resultados

| Tarjeta | Resultado real | Fecha |
|---|---|---|
| T0 a T9 | Pendientes | — |
