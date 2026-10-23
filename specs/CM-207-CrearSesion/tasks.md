# Tarjetas · CM-207 Crear la sesión de entrevista

Cada tarjeta se ejecuta sola y en orden; quien la ejecuta no lee la spec.

**Reglas para todas las tarjetas**

- Identificadores, métodos de prueba y códigos en inglés; Javadoc, comentarios, `@DisplayName`, OpenAPI, mensajes y documentos en español. Sin `CM-207` ni rutas a otros archivos en comentarios.
- Javadoc en toda clase, `record` y método; `package-info.java` en paquetes nuevos.
- Pruebas `metodo_shouldX_whenY` + `@DisplayName`; valores literales; reloj fijo `Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneOffset.UTC)`; sin `Thread.sleep`.
- Logs: nunca `jobOffer`, la copia del perfil ni el cuerpo; sí `sessionId`, `firebaseUid`, `code`.
- Prohibido: dependencias nuevas, cambiar `V1`/`V2`, renombrar o reordenar `ErrorCode`, modificar el armado común del manejador (`problema`, `resolverRequestId`), editar `.github/`.
- **Detenerse y reportar** con la salida real si un nombre de CM-311/CM-204/CM-209 no coincide, una prueba existente se rompe sin causa clara o falta un dato.

**Datos de prueba comunes**

| Nombre | Valor |
|---|---|
| `OWNER` | `uid-a` |
| `OTHER` | `uid-b` |
| `PROFILE_ID` | `7c9e6679-7425-40de-944b-e07fc1f90ae7` (copia de `uid-a`, `COMPLETED`, sin archivar) |
| `ROLE_ID` | `a0000001-0000-0000-0000-000000000002` (Desarrollador Backend, `ACTIVE`) |
| `VALID_BODY` | `{"profileId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","targetRoleId":"a0000001-0000-0000-0000-000000000002","jobOffer":"Buscamos desarrollador backend.","mode":"SIMULACION","tone":"PROFESIONAL_NEUTRO","answerFormat":"AUDIO","sessionLanguage":"es-CO"}` |
| `KEY` | `6f9a1c2e-0b7d-4d1a-9a55-1d2c3b4a5e6f` |

---

## T0 · Preparación

1. `git fetch`; `git status` limpio. Base: `origin/develop` con CM-311, CM-204 y CM-209 fusionadas; si no, la punta del stack (`CM-209-*` sobre `CM-204-*` sobre `CM-311-*`). Anotar SHA y árbol.
2. Confirmar nombres (si difieren, detenerse): `InterviewSession.configure(UUID, FirebaseUid, SessionConfiguration, Clock)`, componentes de `SessionConfiguration`, `SessionStatus.CONFIGURED`/`IN_PROGRESS`, `InterviewSessionRepository` (`save`, `findById`, `deleteConfigured`), `IdempotencyKeyRepository` (`find`, `save`), `IdempotencyKey` (8 componentes), `IdempotencyKeyAlreadyExistsException`, `SessionAlreadyExistsException`, tabla `Definition` del manejador, `ErrorCodeTest`, `PersonalizationCatalog.isAvailable`, `DomainPolicyConfiguration`, `ProfileReplicaRepository.findById`, `ProfileReplica.isUsableForInterview`, bean `Clock`.
3. En `cameia-perfil` `origin/develop`: `git log --oneline -- src/main/resources/db/migration` y confirmar que ninguna migración posterior a `V5_1` cambia `rol_profesional` (si cambió, la semilla de T1 debe reflejarlo).
4. Jira (lectura) de CM-207: anotar cambios; detenerse si cambian un valor de la spec.
5. Línea base: `./mvnw.cmd -B -ntp clean verify` verde, sin `Skipped` de Testcontainers.

---

## T1 · Migración `V4__operacion_crear_sesion.sql`

Precondición: el criterio de catálogo de roles de CM-209 (`V3`, tabla `microentrevista.rol_profesional`) ya está en `develop`. Si no lo está, detenerse y avisar a Paula: esta tarea no crea la tabla ni la siembra.

```sql
ALTER TABLE microentrevista.clave_idempotencia
    DROP CONSTRAINT ck_clave_idempotencia_operacion,
    ADD CONSTRAINT ck_clave_idempotencia_operacion CHECK (operacion IN ('CREAR_SESION'));
```

**Trampa:** si `clave_idempotencia` ya tiene filas con otra operación en alguna base (no debería: CM-311 no las escribe), el `ADD CONSTRAINT` falla; en una base nueva no pasa. Si pasa en el entorno, detenerse.

**Pruebas** (`CreateSessionSchemaMigrationTest`, cabecera de Testcontainers de CM-311):

| Prueba | Esperado |
|---|---|
| `idempotency_shouldRejectUnknownOperation` (`REGISTRAR`) / `…_shouldAcceptCreateSession` | violación de `ck_clave_idempotencia_operacion` / inserta |

**Commit:** `CM-207 | feat(datos): operación CREAR_SESION de la clave de idempotencia`.

---

## T2 · Dominio: validación total

**`domain/model/SessionConfigurationDraft`** (`record`): `String profileId, String targetRoleId, String jobOffer, String mode, String tone, String answerFormat, String sessionLanguage, String idempotencyKey` — todo puede ser `null`.

**`domain/model/FieldRejection`** (`record`): `String field, ErrorCode code, String message`.

**`domain/model/ValidatedSessionRequest`** (`record`): `UUID profileId, UUID targetRoleId, String jobOffer, Modality mode, Tone tone, AnswerMode answerFormat, InterviewLanguage sessionLanguage, String idempotencyKey`; método:

```java
/**
 * Huella de la configuración para reconocer una repetición con la misma clave: SHA-256 en hexadecimal (64 caracteres,
 * minúsculas) de una cadena canónica con los siete valores ya normalizados, separados por un salto de línea y con
 * {@code "-"} en lugar de una oferta nula. Dos peticiones que solo difieren en espacios u orden de campos dan la misma.
 */
public String fingerprint() { … MessageDigest.getInstance("SHA-256") … HexFormat.of().formatHex(...) }
```

**`domain/port/RoleCatalog`**: `boolean isActive(UUID roleId)` (Javadoc: copia local del catálogo de Perfil).

**`domain/policy/SessionConfigurationPolicy`** (`final`, constructor `(PersonalizationCatalog catalog, RoleCatalog roles)`):

```java
/**
 * Valida la configuración de una sesión y devuelve todas las causas a la vez, una por campo y en el orden del
 * formulario. Los textos de una línea se recortan; la oferta laboral se normaliza a NFC y se recorta, y vacía
 * queda nula.
 * @throws InvalidRequestException con la lista si algún campo falla
 */
public ValidatedSessionRequest validate(SessionConfigurationDraft draft)
```

Implementación: una lista `List<FieldRejection>`; un método privado por campo que devuelve el valor normalizado o agrega su rechazo (≤ 20 líneas cada uno). Reglas exactas (constantes con nombre):

| Campo | Orden de comprobación | Rechazos |
|---|---|---|
| `profileId` | en blanco → formato UUID canónico (`[0-9a-fA-F]{8}-…-[0-9a-fA-F]{12}`, se guarda en minúsculas) | `PROFILE_ID_REQUIRED`, `PROFILE_ID_INVALID_FORMAT` |
| `targetRoleId` | en blanco → UUID → `roles.isActive(id)` | `TARGET_ROLE_ID_REQUIRED`, `…_INVALID_FORMAT`, `…_INVALID_VALUE` |
| `jobOffer` | `null`/en blanco → `null`; `Normalizer.normalize(NFC).strip()`; `codePointCount > 3000` | `JOB_OFFER_TOO_LONG` |
| `mode` | en blanco → `Modality` por `code()` exacto → `catalog.isAvailable` | `MODE_REQUIRED`, `MODE_INVALID_VALUE`, `MODE_NOT_ALLOWED` |
| `tone` | en blanco → `Tone` por `code()` | `TONE_REQUIRED`, `TONE_INVALID_VALUE` |
| `answerFormat` | en blanco → `AnswerMode` por `code()` | `ANSWER_FORMAT_REQUIRED`, `ANSWER_FORMAT_INVALID_VALUE` |
| `sessionLanguage` | en blanco → `InterviewLanguage` por `code()` | `SESSION_LANGUAGE_REQUIRED`, `SESSION_LANGUAGE_INVALID_VALUE` |
| `idempotencyKey` | `null` → sin clave; si no, `^[A-Za-z0-9._-]{1,64}$` (vacío también falla) | `IDEMPOTENCY_KEY_INVALID_FORMAT`, campo `Idempotency-Key` |

Mensajes exactos: los de la spec §7 (copiarlos como constantes `static final String`). «En blanco» = `null` o `isBlank()` (incluye tabuladores y saltos de línea). Para `fromCode` usar un método que devuelva `Optional` en lugar de atrapar `IllegalArgumentException` si CM-311 lo ofrece; si solo existe `fromCode` que lanza, atrapar `IllegalArgumentException` en un único método privado `parse(...)`.

**Trampa:** `targetRoleId` solo consulta `roles.isActive` si el formato es válido (no llamar al puerto con basura).

**`InterviewSession.reconfigure(SessionConfiguration configuration, Clock clock)`** (en la clase de CM-311): si `status != CONFIGURED` → `SessionTransitionNotAllowedException` sin cambiar nada; si no, reemplaza la configuración, `planningRetries = 0`, `updatedAt = clock.instant()`; `createdAt` no cambia.

**Excepciones** (`domain/exception/`, `extends BusinessException`):

| Clase | Código | Mensaje / contenido |
|---|---|---|
| `InvalidRequestException` | `VALIDATION_FAILED` | «Revisa los campos marcados.»; `List<FieldRejection> rejections()` (copia inmutable) |
| `ProfileSyncPendingException` | `PROFILE_SYNC_PENDING` | «Tu perfil se está actualizando. Inténtalo en unos segundos.» |
| `ProfileNotFoundException` | `PROFILE_NOT_FOUND` | «No encontramos lo que buscabas.» |
| `IdempotencyKeyConflictException` | `IDEMPOTENCY_KEY_CONFLICT` | «Esta solicitud no coincide con la que enviaste antes. Inténtalo de nuevo.» |

**`ErrorCode`:** agregar al final, en este orden: `PROFILE_NOT_FOUND`, `PROFILE_SYNC_PENDING`, `IDEMPOTENCY_KEY_CONFLICT`, `PROFILE_ID_REQUIRED`, `PROFILE_ID_INVALID_FORMAT`, `TARGET_ROLE_ID_REQUIRED`, `TARGET_ROLE_ID_INVALID_FORMAT`, `TARGET_ROLE_ID_INVALID_VALUE`, `JOB_OFFER_TOO_LONG`, `MODE_REQUIRED`, `MODE_INVALID_VALUE`, `MODE_NOT_ALLOWED`, `TONE_REQUIRED`, `TONE_INVALID_VALUE`, `ANSWER_FORMAT_REQUIRED`, `ANSWER_FORMAT_INVALID_VALUE`, `SESSION_LANGUAGE_REQUIRED`, `SESSION_LANGUAGE_INVALID_VALUE`, `IDEMPOTENCY_KEY_INVALID_FORMAT` (Javadoc de una línea cada uno). **En el mismo commit**, sus 19 filas en `docs/errores.md` (si no, `ErrorCodeDocumentationTest` falla) y, en `ErrorCodeTest`, un conjunto `FIELD_CODES` con los 16 de campo.

**Pruebas** (`SessionConfigurationPolicyTest` con `RoleCatalog` falso que da `true` solo para `ROLE_ID` y `a0000001-0000-0000-0000-000000000001`):

| Prueba | Entrada | Esperado |
|---|---|---|
| `validate_shouldReturnNormalized_whenValid` | `VALID_BODY` con `jobOffer = "  Buscamos  "` | `jobOffer = "Buscamos"`, enumerados correctos |
| `validate_shouldReportSixRequired_whenAllMissing` | todo `null` | 6 rechazos en orden `profileId, targetRoleId, mode, tone, answerFormat, sessionLanguage` |
| `validate_shouldRejectEveryOutOfCatalogValue_atOnce` | rol `00000000-…`, `ENTRENO`, `AMABLE`, `VIDEO`, `fr` | 5 rechazos con `TARGET_ROLE_ID_INVALID_VALUE`, `MODE_NOT_ALLOWED`, `TONE_INVALID_VALUE`, `ANSWER_FORMAT_INVALID_VALUE`, `SESSION_LANGUAGE_INVALID_VALUE` |
| Una `@ParameterizedTest` por fila de la spec §7 (`validate_shouldReject_whenProfileIdInvalid`, `…TargetRoleIdInvalid`, `…ModeInvalid`, `…ToneInvalid`, `…AnswerFormatInvalid`, `…SessionLanguageInvalid`, `…IdempotencyKeyInvalid`) | valores literales de §7 | `code` y `message` exactos |
| `validate_shouldAccept3000CodePointOffer` (`"😀".repeat(3000)`), `validate_shouldReject3001Offer`, `validate_shouldAcceptNfdThatFitsInNfc` (`"é".repeat(3000)`), `validate_shouldStripOfferEdges` | — | — |
| `validate_shouldAcceptActiveRoleNotInProfile` | `a0000001-0000-0000-0000-000000000001` | aceptado |
| `validate_shouldNotQueryRoles_whenFormatInvalid` | `"backend"` | el falso registra 0 llamadas |
| `validate_shouldAcceptUppercaseUuid` | UUID en mayúsculas | guardado en minúsculas |
| `ValidatedSessionRequestTest.fingerprint_shouldBeSha256Hex` / `…_shouldBeEqual_whenOnlyWhitespaceDiffers` / `…_shouldDiffer_whenToneDiffers` | — | 64 hex; igual; distinta |
| `InterviewSessionTest.reconfigure_shouldReplaceConfigAndResetRetries` / `reconfigure_shouldThrow_whenNotConfigured` (4 estados, sin cambios) | — | — |

**Commit:** `CM-207 | feat(dominio): validación total de la configuración de la sesión`.

---

## T3 · Persistencia

- `RoleCatalogAdapter` (package-private, `isActive(id)` → `existsByIdAndStatus(id, "ACTIVE")`) sobre la entidad y el repositorio de `rol_profesional` que crea el criterio de catálogo de roles de CM-209; si todavía no existen en `develop`, detenerse (no se duplican aquí).
- `InterviewSessionRepository` + `Optional<InterviewSession> findConfiguredByOwner(FirebaseUid owner)`, `Optional<InterviewSession> findInProgressByOwner(FirebaseUid owner)`, `int countDefinitiveAnswers(UUID sessionId)`; en el adaptador:

```sql
-- countDefinitiveAnswers (nativa, parametrizada)
SELECT count(*) FROM microentrevista.intento_respuesta ir
  JOIN microentrevista.turno t ON t.id = ir.turno_id
  JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
 WHERE p.sesion_id = :sessionId AND ir.es_definitiva
```

Las dos `find…ByOwner` por JPQL con el estado como parámetro (las atienden los índices únicos parciales de `V1`).

**Pruebas** (Testcontainers): `RoleCatalogAdapterTest.isActive_shouldBeTrue_forInsertedActiveRole`, `…_shouldBeFalse_forUnknown`, `…_shouldBeFalse_forInactive` (fila insertada por SQL); `InterviewSessionRepositoryAdapterTest.findConfiguredByOwner_shouldReturnOnlyOwnersConfigured`, `findInProgressByOwner_*`, `countDefinitiveAnswers_shouldCountOnlyDefinitive` (inserta por SQL una `ejecucion_ia`, 3 preguntas, 3 turnos y 3 intentos, 2 definitivos → 2), `…_shouldBeZero_whenNoAnswers`.

**Commit:** `CM-207 | feat(persistencia): catálogo de roles y consultas de la sesión del usuario`.

---

## T4 · Caso de uso

`application/command/CreateSessionCommand` (`record FirebaseUid owner, SessionConfigurationDraft draft`); `application/service/OngoingSession` (`record UUID sessionId, int answersCount`); `CreateSessionResult` (`record int httpStatus, UUID sessionId, SessionStatus status, ValidatedSessionRequest configuration, OngoingSession ongoing`).

`CreateSessionService` (dependencias: `SessionConfigurationPolicy`, `ProfileReplicaRepository`, `InterviewSessionRepository`, `IdempotencyKeyRepository`, `TransactionTemplate`, `JsonMapper`, `Clock`; si pasa de 3 parámetros de constructor no importa: son dependencias, no parámetros de método):

```java
static final String OPERATION = "CREAR_SESION";

/**
 * Crea la sesión configurada o reutiliza la que el usuario ya tenía, una sola vez por clave.
 * <p>Orden: (1) valida todo; (2) con clave, si ya existe, repite su respuesta o rechaza otra configuración;
 * (3) en una transacción: comprueba el perfil en la copia local, crea o reutiliza, informa la entrevista en
 * curso y, con clave, la guarda con la respuesta; (4) si otra petición guardó la misma clave a la vez, se
 * relee fuera de la transacción revertida.</p>
 */
public CreateSessionResult create(CreateSessionCommand command) {
    ValidatedSessionRequest request = policy.validate(command.draft());
    String fingerprint = request.fingerprint();
    Optional<CreateSessionResult> replay = replayIfKnown(command.owner(), request, fingerprint);
    if (replay.isPresent()) return replay.get();
    try {
        return transaction.execute(status -> createOrReuse(command.owner(), request, fingerprint));
    } catch (IdempotencyKeyAlreadyExistsException race) {
        // Otra petición con la misma clave terminó primero: se repite su respuesta o se rechaza si era otra configuración.
        return replayIfKnown(command.owner(), request, fingerprint).orElseThrow(() -> race);
    }
}
```

Métodos privados (≤ 20 líneas cada uno): `replayIfKnown` (sin clave → vacío; con clave y misma huella → resultado deserializado del `cuerpo_respuesta` con el `estado_respuesta`; con otra huella → `IdempotencyKeyConflictException`); `requireUsableProfile(owner, profileId)` (vacío → `ProfileSyncPendingException`; otro dueño → `ProfileNotFoundException`; no usable → `ProfileSyncPendingException`); `snapshotOf(ProfileReplica)` (JSON `{"perfilId","nombre","contextoProfesional","rolesAspirados","actualizadoEn"}` armado con `JsonMapper`, reutilizando los dos `jsonb` como árboles, sin volver a escribir sus nombres); `createOrReuse` (configurada → `reconfigure` + `save` → 200; si no → `configure` con `UUID.randomUUID()` + `save` → 201; `ongoingOf(owner)`; si hay clave, `idempotency.save(new IdempotencyKey(UUID.randomUUID(), owner, OPERATION, key, fingerprint, httpStatus, json(result), clock.instant()))`).

Log `INFO` al terminar: «Sesión de entrevista configurada [sessionId={}, firebaseUid={}, estado={}]» (201 o 200), sin la configuración.

**Trampa 1:** la excepción de la carrera la lanza `saveAndFlush` dentro de `transaction.execute`; `TransactionTemplate` revierte y la propaga: por eso el `catch` va fuera.
**Trampa 2:** `SessionAlreadyExistsException` (dos creaciones sin clave a la vez) no se atrapa: llega al manejador como 409.

**Pruebas** (`CreateSessionServiceTest`, fakes en memoria y un `TransactionTemplate` con `PlatformTransactionManager` falso que solo ejecuta): una prueba por rama: nueva (201), reutiliza (200, mismo id, reintentos 0), con en curso (2 respuestas), perfil ausente (409), ajeno (404), no usable (409), clave repetida misma huella (repite, sin escribir), clave con otra huella (409), carrera de clave (el fake de claves lanza `IdempotencyKeyAlreadyExistsException` una vez y luego la encuentra → repite), sin clave (no guarda clave), validación (422 antes de cualquier lectura: los fakes registran 0 llamadas).

**Commit:** `CM-207 | feat(aplicacion): crear o reutilizar la sesión con anti-duplicados`.

---

## T5 · API, manejador y OpenAPI

**DTO** (`presentation/dto/`): `CreateSessionRequest` (7 `String` con `@Schema` en cada uno: descripción, ejemplo, valores permitidos, `maxLength = 3000` en `jobOffer`, `format = "uuid"` en los ids; sin Bean Validation de negocio); `CreateSessionResponse` (`UUID sessionId`, `String status`, `SessionConfigurationResponse configuration`, `OngoingSessionResponse ongoingSession` — este con `@JsonInclude(ALWAYS)` para que salga `null`), `SessionConfigurationResponse` (7 campos, valores `code()` de los enumerados), `OngoingSessionResponse` (`UUID sessionId`, `int answersCount`); `static from(CreateSessionResult)`.

**Controlador** `InterviewSessionController` (`@RestController @RequestMapping("/api/v1/interviews/sessions")`, clase de paquete):

```java
@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
ResponseEntity<CreateSessionResponse> create(
        @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId,
        @Parameter(description = "Clave única por intento de crear la sesión; repetirla devuelve la misma respuesta")
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody CreateSessionRequest body) {
    var owner = FirebaseUid.required(userId);
    var result = service.create(new CreateSessionCommand(owner, body.toDraft(idempotencyKey)));
    return ResponseEntity.status(result.httpStatus()).body(CreateSessionResponse.from(result));
}
```

**Trampa:** `@RequestBody` sin `@Valid` (la validación es del dominio). Un cuerpo vacío llega como `HttpMessageNotReadableException` → 422 `REQUEST_BODY_INVALID_FORMAT` (CM-311).

**OpenAPI:** `@Tag(name = "Sesiones de entrevista")`, `@Operation` y `@ApiResponse` para 201, 200, 401, 404, 409 (tres ejemplos: `PROFILE_SYNC_PENDING`, `IDEMPOTENCY_KEY_CONFLICT`, `SESSION_ALREADY_EXISTS`), 415, 422 (ejemplo con `errors`), 503 y 500.

**Manejador** (`BusinessExceptionHandler` de CM-311): agregar `@ExceptionHandler(InvalidRequestException.class)` que responde 422 `VALIDATION_FAILED`, `detail` «Revisa los campos marcados.», `errors` con cada `FieldRejection` (`field`, `code.name()`, `message`), usando el mismo armado común que el manejador de `MethodArgumentNotValidException` (`problema(...)` y el ayudante de `errors` de CM-311). Registro `WARN` una vez con `status`, `code`, `requestId`, `firebaseUid` y los **nombres** de los campos rechazados (nunca los valores). En la tabla `Definition`: `PROFILE_NOT_FOUND` 404 «Perfil no encontrado», `PROFILE_SYNC_PENDING` 409 «Perfil en actualización», `IDEMPOTENCY_KEY_CONFLICT` 409 «Solicitud distinta».

**Pruebas** (`CreateSessionControllerTest`, MockMvc `standaloneSetup` con el manejador real y el servicio simulado para los errores de la aplicación; para la validación, servicio real con fakes):

| Prueba | Esperado |
|---|---|
| `create_shouldReturn201_whenNew` / `create_shouldReturn200_whenReused` | estado, `sessionId`, `configuration`, `ongoingSession: null` presente |
| `create_shouldReturn422WithSixFields_whenEmptyBody` (`{}`) | `errors[*].field` en el orden de REQ-SES-01; `detail` «Revisa los campos marcados.» |
| `create_shouldReturn422WithFiveFields_whenOutOfCatalog` | 5 elementos con sus códigos |
| las filas † de la spec §7 | `field`, `code`, `message` |
| `create_shouldReturn404_whenProfileOfOtherUser`, `…409SyncPending…`, `…409KeyConflict…` | `code`, `title`, `detail` literales |
| `create_shouldReturn401_whenNoIdentity` | `IDENTITY_REQUIRED` |
| `create_shouldReturn422_whenMalformedJson` / `create_shouldReturn422_whenToneIsObject` | `REQUEST_BODY_INVALID_FORMAT` |
| `create_shouldIgnoreUnexpectedFields_andUseHeaderOwner` | el comando lleva `uid-a` aunque el cuerpo diga `"firebaseUid":"uid-b"` |
| `logs_shouldNotContainOfferOrProfile` | con `jobOffer = "oferta-secreta-123"` inválida (3001) el log no la contiene |

**Commit:** `CM-207 | feat(api): POST /api/v1/interviews/sessions con sus errores y su documentación`.

---

## T6 · Punta a punta, concurrencia y Postman

`CreateSessionEndToEndTest` (`RANDOM_PORT`, Testcontainers, `cameia.llm.model=modelo-de-prueba`; antes de cada prueba, insertar la copia del perfil de `uid-a` con `ProfileReplicaRepository.upsertIfNotOlder` dentro de una transacción y limpiar `sesion` y `clave_idempotencia`):

| Prueba | Esperado |
|---|---|
| `create_shouldReturn201Configured_whenValid` | 201; fila en `sesion` con `CONFIGURADA`, `reintentos_planificacion = 0`, `fecha_creacion = 2026-10-20T15:00:00Z` (reloj fijo por `@TestConfiguration`) |
| `create_shouldStoreNullOffer_whenOfferBlank` (`"   "`) | `oferta_laboral IS NULL` |
| `create_shouldStore3000Offer` | 3000 en la base |
| `create_shouldCreateNoQuestions` | 0 filas en `pregunta`, `turno`, `ejecucion_ia` |
| `create_shouldKeepSnapshot_whenProfileChangesLater` | aplicar otra copia del perfil con `actualizadoEn` posterior y `nombre = "Otro"`; la sesión conserva el nombre anterior |
| `create_shouldReuseConfigured_with200AndResetRetries` | poner `reintentos_planificacion = 2` por SQL; 200, mismo id, 0, `fecha_creacion` igual |
| `create_shouldReuseConfigured_whenAlsoInProgress` | con otra `EN_CURSO` del usuario: 200 y la en curso intacta |
| `create_shouldReportOngoingSession_withAnswersCount` (2), `…WithFiveAnswers` (5), `…_shouldReportNoOngoing_whenNone` | `ongoingSession` según el caso |
| `create_shouldLeaveInProgressUntouched` | `EN_CURSO` y `fecha_fin` nula después |
| `create_shouldRollbackAll_whenFailsAfterInsert` | `@MockitoSpyBean IdempotencyKeyRepository` que lanza `IllegalStateException` en `save`: 500 `INTERNAL_ERROR`; 0 sesiones nuevas; la `EN_CURSO` intacta |
| `create_shouldReturn409SyncPending_whenProfileNotReplicated` / `…_whenReplicaNotUsable` (copia `IN_PROGRESS`; copia archivada) | 409 literal |
| `create_shouldReturn404_whenProfileOfOtherUser` | `uid-b` pide el perfil de `uid-a`: 404; 0 sesiones de `uid-b` |
| `create_shouldIgnoreUnexpectedFields_andUseHeaderOwner` | sesión de `uid-a`, `CONFIGURADA` |
| `create_shouldAcceptTextAndEnglish_withFreePlan` | 201 |
| `create_shouldReplaySameResponse_whenSameKey` | dos veces con `KEY`: mismo cuerpo y 201; 1 sesión; 1 clave |
| `create_shouldApplyOnce_when20Repetitions` | 20 veces: 1 sesión, 1 clave, 20 respuestas iguales |
| `create_shouldReplaySameResponse_whenSameKeyConcurrent` | 2 hilos con `CountDownLatch` y `KEY`: 1 sesión; dos 201 con el mismo `sessionId` |
| `create_shouldNotDuplicate_whenTwoKeysConcurrent` | 2 hilos, claves distintas: 1 sesión; respuestas {201, 200} o {201, 409 `SESSION_ALREADY_EXISTS`} |
| `create_shouldReturn409_whenSameKeyDifferentBody` | `IDEMPOTENCY_KEY_CONFLICT` |
| `create_shouldSucceed_whenRetryWithSameKeyAfterSyncPending` | sin copia → 409; se inserta la copia; misma clave → 201 |
| `create_shouldIsolateKeysPerUser` | `uid-a` y `uid-b` con la misma `KEY` y sus perfiles: dos sesiones |
| `create_shouldReturnConflict_whenConcurrentReconfigure` | dos reutilizaciones a la vez: una 200 y otra 200 o 409 `SESSION_CONFLICT`; nunca 500 |
| `create_shouldReject5MbOffer` | 422 `JOB_OFFER_TOO_LONG` |
| `create_shouldDeclareCharset` | 201 y 422 con `charset=UTF-8` |
| `create_shouldNotExposeProfileData` | el cuerpo no contiene `resumen`, `Python` ni `ACME` |
| `create_shouldMeetLatencyBudget_when30Requests` | 30 usuarios con su copia; p95 ≤ 2000 ms |
| `openApi_shouldDocumentCreateSession` | `/v3/api-docs` con las respuestas 200, 201, 401, 404, 409, 415, 422, 500, 503 |

**Postman:** carpeta «Crear sesión» con las peticiones de la spec §13 (preparación por la API del broker de CM-209). Newman → 17/17.

**Commit:** `CM-207 | test(entrevista): creación de punta a punta, concurrencia y Postman`.

---

## T7 · Documentación

1. `docs/errores.md`: revisar las 19 filas (columna «Endpoints» = `POST /api/v1/interviews/sessions`; campo en las de 422) y agregar la ruta a `IDENTITY_REQUIRED`, `VALIDATION_FAILED`, `REQUEST_BODY_INVALID_FORMAT`, `MEDIA_TYPE_NOT_ALLOWED`, `SESSION_ALREADY_EXISTS`, `SESSION_CONFLICT`, `DATABASE_UNAVAILABLE`, `INTERNAL_ERROR`.
2. `docs/adr/0008-crear-sesion-y-anti-duplicados.md`: D-SES-01 a D-SES-08.
3. `CLAUDE.md` (§4 y §5): la ruta y la lectura de la copia del catálogo de roles (la mantiene el evento del catálogo; un rol nuevo se crea solo en Perfil).
4. Bitácora `06_crear-sesion_prompt.md` + fila del README.
5. Nada para otro repo en esta tarea: el evento del catálogo lo publica Perfil dentro de el criterio de catálogo de roles de CM-209.

**Commit:** `CM-207 | docs(entrevista): errores, ADR 0008 y CLAUDE.md`.

---

## T8 · Verificación única

`clean verify` sin omitidas; `jacoco.csv` ≥ 90 % por clase tocada; Newman de la carpeta y de la colección completa; lista de verificación de la spec §20; atributos §12 con resultados reales en `ejecucion/evidencia/CM-207/resumen.md` y la tabla R1 a R14; prueba real con un `jobOffer` de 5 MB en el entorno desplegado cuando esté disponible (hallazgo H-4 de la spec; se anota el resultado en `resumen.md`, y mientras no se pueda hacer queda como pendiente con su destino); push y PR en Draft según `CLAUDE.md` raíz §2; explicar a Paula y detenerse.

## Resultados

| Tarjeta | Resultado real | Fecha |
|---|---|---|
| T0 a T8 | Pendientes | — |
