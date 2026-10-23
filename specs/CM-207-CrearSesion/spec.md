# CM-207 · Crear la sesión de entrevista (`POST /api/v1/interviews/sessions`)

| Dato | Valor |
|---|---|
| Tarea Jira | CM-207, subtarea de Backend de HU-4.4 (tarea padre CM-264). Sin responsable ni descripción en Jira (pedido a Vela el 15-oct para el 24-oct, `comunicaciones/seguimiento/respuestas-y-pendientes.md`); esta ola la asigna a Paula |
| Historia | HU-4.4 «Crear la sesión en CONFIGURADA con validación total y una sola sesión en curso» (backlog v8.3, hoja HE-04, 19 CA) |
| Sprint | Jira: no leído (brecha B-1). Hoja «Backlog»: Sprint 3; Vela recibió el 20-oct la propuesta de pasarla al Sprint 4 |
| Repositorio | `cameia-entrevista`, base `origin/develop` `bcc697d` **más CM-311, CM-312, CM-204 y CM-209 tratadas como implementadas** (§21) |
| Depende de | CM-311 (sesión, estados, `clave_idempotencia`, manejador), CM-204 (`PersonalizationCatalog`, `DomainPolicyConfiguration`), CM-209 (`ProfileReplicaRepository`, `ProfileReplica.isUsableForInterview`) y el **criterio de sincronización del catálogo de roles de CM-209** (§8.1): crea la tabla `rol_profesional` y la mantiene al día con el evento del catálogo que publica Perfil |
| Desbloquea | HU-4.5 (iniciar la sesión, CM-212 de Vela) y HU-5.2 (CM-216) |
| Estimación | Jira: 3,8 h (`cronograma/plan-sprint4_entrevista-voz-reporte.md` §1). Backend: **14 a 16 h** (validación total, lectura de la copia del catálogo de roles, anti-duplicados con repetición de respuesta, reutilización de la sesión configurada, entrevista en curso, pruebas y Postman). Se informa a Vela |
| Estado | **LISTA PARA EJECUTAR** con CM-311, CM-204 y CM-209 fusionadas (o en stack). Aprobada por Paula el 22-oct-2026, con las decisiones por recomendación en §17 ratificadas |

## 1. Objetivo

Que «Iniciar entrevista» cree la sesión de entrevista en `CONFIGURADA`, todavía sin preguntas:

1. Valida toda la configuración y devuelve **todos** los campos inválidos a la vez (422).
2. Lee el perfil en la copia local (CM-209): 404 si es de otra persona, 409 `PROFILE_SYNC_PENDING` si falta o no está activo.
3. Guarda una copia fija del perfil en la sesión (`contexto_perfil_snapshot`).
4. Si el usuario ya tenía una sesión configurada sin iniciar, la reutiliza con la configuración nueva (200); si no, crea una (201).
5. Informa si hay otra entrevista en curso y cuántas respuestas tiene, sin tocarla.
6. Con `Idempotency-Key`, una repetición devuelve la misma respuesta sin crear otra sesión.

No llama a Perfil ni a la IA y no aplica el cupo del plan (HU-4.6).

## 2. Fuentes y vigencia

| Fuente | Qué aporta | Marca |
|---|---|---|
| Backlog v8.3, HU-4.4 (19 CA y nota técnica), HU-4.2 (CA-4.2.5, 4.2.9, 4.2.11, 4.2.12, 4.2.14 y nota), HU-4.3 (nota), RT-01, RT-03, RT-05, RT-06 (`analisis/contexto/CM-207.md`) | Requisitos y textos | Hecho |
| Hoja «Cambios v7» filas 184 y 185; «Cambios v8» filas 29 a 33 | `PROFILE_SYNC_PENDING` y su mensaje; sin cupo; sesión creada antes del aviso; `rol_objetivo_id`; `AUDIO \| TEXTO` | Hecho |
| HT-03 (hoja «Backlog» fila 31): «Ya no requiere permiso de invocación de Entrevistas hacia Perfil» | Entrevista no puede llamar a Perfil | Hecho |
| Specs de CM-311 (aprobada), CM-204 y CM-209 (esta ola) | Tablas, estados, manejador, catálogo, copia del perfil | Tratadas como implementadas |
| `cameia-web` `836d8e3` (solo lectura, 22-oct): `interviewSession.dto.ts`, `interviewSession.api.ts`, `useStartInterview.ts`, `SPEC.md` de `interview-setup`; respuesta de Frontend del 22-oct (sección 1) | Envía `profileId`, `targetRoleId`, `jobOffer`, `mode`, `tone`, `answerFormat`, `sessionLanguage` en `camelCase` (PR #100); espera `sessionId`, `status`, `ongoingSession {sessionId, answersCount}` y acepta 201 y 200; **envía siempre `Idempotency-Key`** y reutiliza la misma clave al reintentar con la misma configuración; ofrece como rol objetivo cualquier rol activo del catálogo (CA-4.2.14) | Hecho |
| Borrador sin enviar `comunicaciones/20102026_v1_respuesta-nueva-entrevista-para-frontend.md` §5 y §6.1 | Nombres en `camelCase` con los mismos términos de Frontend; 200 al reutilizar; pide reutilizar la misma `Idempotency-Key` en los reintentos | Hecho (propuesta de Backend aún no publicada) |
| `cameia-perfil` `1201943`, migraciones `V3`, `V4`, `V5_1` | Catálogo de 46 roles, `nombre`, `nombre_en`, `estado` (`ACTIVE` en todos) | Hecho |
| Jira | No leído | Brecha B-1 |

## 3. Criterios de aceptación y trazabilidad

| CA | Dueño | Lo que fija para Backend (literal) | Requisito | Prueba (nombre exacto) | Postman |
|---|---|---|---|---|---|
| CA-4.4.1 | F y B | «→ 201 Created con session_id y estado CONFIGURADA; oferta_laboral null si está vacía; no se crean preguntas» | REQ-SES-01, 05, 08 | `CreateSessionEndToEndTest.create_shouldReturn201Configured_whenValid`, `…_shouldStoreNullOffer_whenOfferBlank`, `…_shouldCreateNoQuestions` | `201 sesión nueva`, `201 oferta en blanco = null` |
| CA-4.4.2 | B | «los datos del perfil quedan en contexto_perfil_snapshot y no cambian después» | REQ-SES-07 | `…create_shouldKeepSnapshot_whenProfileChangesLater` | — |
| CA-4.4.3 | B | «422 … con la lista de campos rechazados (tono fuera del catálogo, modalidad ENTRENO, forma_respuesta fuera de AUDIO \| TEXTO, idioma fuera de es-CO \| en, rol no ACTIVE)» | REQ-SES-02, 03 | `SessionConfigurationPolicyTest.validate_shouldRejectEveryOutOfCatalogValue_atOnce`; `CreateSessionControllerTest.create_shouldReturn422WithFiveFields_whenOutOfCatalog` | `422 cinco valores fuera del catálogo` |
| CA-4.4.4 | F | Sin llamada | — | — | — |
| CA-4.4.5 | F | «500, 503 o tiempo agotado» | REQ-SES-14 | `…create_shouldReturn503_whenDatabaseDown` (de CM-311) | — |
| CA-4.4.6 | F y B | «La anterior sigue EN_CURSO hasta ese momento» | REQ-SES-09 | `…create_shouldLeaveInProgressUntouched` | — |
| CA-4.4.7 | F y B | «201 Created con entrevista_en_curso {session_id, respuestas_registradas}; sin ese dato si no hay ninguna» | REQ-SES-09 | `…create_shouldReportOngoingSession_withAnswersCount`, `…_shouldReportNoOngoing_whenNone` | `201 con entrevista en curso` |
| CA-4.4.8 | F | Sin llamada a `…/start` | — | — | — |
| CA-4.4.9 | B | «200 OK con el mismo session_id y la configuración actualizada (nuevo contexto_perfil_snapshot; contador de reintentos de planificación en 0); también cuando el Usuario tiene otra entrevista en curso» | REQ-SES-06 | `…create_shouldReuseConfigured_with200AndResetRetries`, `…_shouldReuseConfigured_whenAlsoInProgress`; `InterviewSessionTest.reconfigure_*` | `200 reutiliza la configurada` |
| CA-4.4.10 | B | «500; la transacción se revierte y la sesión anterior sigue EN_CURSO sin fecha_fin» | REQ-SES-13 | `…create_shouldRollbackAll_whenFailsAfterInsert` | — |
| CA-4.4.11 | B | «409 Conflict con code PROFILE_SYNC_PENDING: los perfiles en Borrador no se replican» | REQ-SES-04 | `…create_shouldReturn409SyncPending_whenProfileNotReplicated`, `…_whenReplicaNotUsable` | `409 perfil sin copia` |
| CA-4.4.12 | B | «404 Not Found si el perfil está en la réplica con otro dueño. Un identificador que no está en la réplica responde como CA-4.4.19 (409)» | REQ-SES-04 | `…create_shouldReturn404_whenProfileOfOtherUser` (dos usuarios) | `404 perfil de otra persona` |
| CA-4.4.13 | B | «con estado = EN_CURSO o un identificador de cuenta ajeno → 201; los campos no esperados se ignoran y el dueño es el del ID Token» | REQ-SES-10 | `…create_shouldIgnoreUnexpectedFields_andUseHeaderOwner` | `201 campos no esperados ignorados` |
| CA-4.4.14 | B | «422 … con la lista de todos los campos faltantes (perfil, rol objetivo, tono, modalidad, forma de respuesta e idioma)» | REQ-SES-02 | `…validate_shouldReportSixRequired_whenAllMissing`; `…create_shouldReturn422WithSixFields_whenEmptyBody` | `422 seis obligatorios` |
| CA-4.4.15 | B | «Oferta Laboral de exactamente 3000 caracteres → 201» | REQ-SES-05 | `…validate_shouldAccept3000CodePointOffer` (con `"😀"`), `…create_shouldStore3000Offer` | `201 oferta de 3000` |
| CA-4.4.16 | B | «3001 caracteres → 422 (campo oferta_laboral)»; mensaje literal de CA-4.2.12 | REQ-SES-05 | `…validate_shouldReject3001Offer` | `422 oferta de 3001` |
| CA-4.4.17 | F y B | «con Idempotency-Key: la primera → 201 Created; una repetición con la misma clave → 201 Created con la misma sesión, sin crear otra» | REQ-SES-11 | `…create_shouldReplaySameResponse_whenSameKey`, `…_whenSameKeyConcurrent`, `…_shouldApplyOnce_when20Repetitions` | `201 repetición con la misma clave` |
| CA-4.4.18 | F y B | «→ 201» con 5 respuestas guardadas | REQ-SES-09 | `…create_shouldReportOngoingWithFiveAnswers` | — |
| CA-4.4.19 | F y B | «409 Conflict con code PROFILE_SYNC_PENDING»; mensaje «Tu perfil se está actualizando. Inténtalo en unos segundos.» | REQ-SES-04 | `…create_shouldReturn409SyncPending_whenProfileNotReplicated` (comprueba `code` y `detail` literales) | `409 perfil sin copia` |
| CA-4.3.8, CA-4.3.9 | F y B | «forma_respuesta = TEXTO → 201»; «idioma = en aceptado con el plan FREE → 201» | REQ-SES-03, 12 | `…create_shouldAcceptTextAndEnglish_withFreePlan` | `201 texto e inglés con plan FREE` |
| CA-4.2.9, 4.2.11, 4.2.12 | F y B | oferta `null` → 201; 3000 → 201; 3001 → 422 «La oferta laboral no puede superar los 3000 caracteres.» | REQ-SES-05 | las de CA-4.4.1, 4.4.15, 4.4.16 | — |
| CA-4.2.5, 4.2.14 | F y B | `rol_objetivo_id` = UUID de un rol activo del catálogo, aunque no esté en el perfil | REQ-SES-03 | `…validate_shouldAcceptActiveRoleNotInProfile` | — |

### 3.1 Comparación literal

| Elemento | CA o nota | Spec | Nota |
|---|---|---|---|
| Ruta y método | `POST /api/v1/interviews/sessions` | igual | — |
| Encabezado | «con la cabecera Idempotency-Key» | opcional; si llega, se valida y se usa (D-SES-03) | `cameia-web` la envía siempre |
| Nombres de campos | backlog en español: `perfil`, `oferta_laboral`, `rol_objetivo_id`, `modalidad`, `tono`, `forma_respuesta`, `idioma`; L-12: `jobOffer`, `targetRoleId` | `profileId`, `targetRoleId`, `jobOffer`, `mode`, `tone`, `answerFormat`, `sessionLanguage` | D-SES-01 |
| Respuesta | `session_id`, estado, `entrevista_en_curso {session_id, respuestas_registradas}` | `sessionId`, `status`, `configuration`, `ongoingSession {sessionId, answersCount}` | D-SES-01 |
| 409 | `PROFILE_SYNC_PENDING`, «Tu perfil se está actualizando. Inténtalo en unos segundos.» | igual | — |
| 422 oferta | «La oferta laboral no puede superar los 3000 caracteres.» (CA-4.2.12) | igual | — |
| 404 | sin texto en el CA; RT-03 muestra «No encontramos lo que buscabas.» | `PROFILE_NOT_FOUND` con ese texto | — |
| 500 | «transacción revertida» | 500 `INTERNAL_ERROR` sin efectos | — |

## 4. Requisitos funcionales (EARS)

### 4.1 Validación (422, todos los campos a la vez)

- **REQ-SES-01** Cuando llegue `POST /api/v1/interviews/sessions` con `X-User-Id` válido (regla de CM-311) y cuerpo JSON, el sistema debe validar los siete campos y el encabezado `Idempotency-Key` y, si alguno falla, responder `422` `VALIDATION_FAILED`, `detail` «Revisa los campos marcados.», con un elemento por campo rechazado en `errors[{field, code, message}]`, **todos a la vez**, solo la primera causa de cada campo, en este orden: `profileId`, `targetRoleId`, `jobOffer`, `mode`, `tone`, `answerFormat`, `sessionLanguage`, `Idempotency-Key`. No debe guardar nada.
- **REQ-SES-02** Un campo obligatorio ausente, `null`, vacío o con solo espacios, tabuladores o saltos de línea debe dar su código `…_REQUIRED` (§7). Son obligatorios todos salvo `jobOffer` (CA-4.4.14).
- **REQ-SES-03** El sistema debe aceptar solo: `profileId` y `targetRoleId` como UUID canónico; `targetRoleId` de un rol con `estado = ACTIVE` en la copia local del catálogo de roles, que mantiene el evento del catálogo (§8.1); `mode` en las modalidades **disponibles** de `PersonalizationCatalog` (hoy solo `SIMULACION`; `ENTRENO` → `MODE_NOT_ALLOWED`); `tone`, `answerFormat` y `sessionLanguage` en los valores del catálogo, con mayúsculas exactas (`simulacion` → `…_INVALID_VALUE`).
- **REQ-SES-04** Cuando la configuración sea válida, el sistema debe buscar `profileId` en `contexto_profesional_replica` (CM-209) **sin llamar a Perfil**: si no está, o está pero `isUsableForInterview()` es falso, responder `409` `PROFILE_SYNC_PENDING` «Tu perfil se está actualizando. Inténtalo en unos segundos.»; si está con otro `firebase_uid`, responder `404` `PROFILE_NOT_FOUND` «No encontramos lo que buscabas.» sin revelar el dueño. La comprobación del dueño va **antes** que la de uso (un perfil ajeno nunca da 409).
- **REQ-SES-05** `jobOffer`: el sistema debe normalizarlo a NFC y recortar los espacios de los extremos; si queda vacío (o llegó `null` o ausente), guardar `null`; si tiene más de 3000 puntos de código, `JOB_OFFER_TOO_LONG` «La oferta laboral no puede superar los 3000 caracteres.». Con 3000 se guarda completo. El texto se guarda literal (sin interpretar HTML).

### 4.2 Creación o reutilización

- **REQ-SES-06** Si el usuario ya tiene una sesión `CONFIGURADA`, el sistema debe actualizarla con la configuración nueva, una copia nueva del perfil, `reintentos_planificacion = 0` y `fecha_actualizacion` del reloj, y responder `200` con el mismo `sessionId` (CA-4.4.9), también si tiene otra `EN_CURSO`.
- **REQ-SES-07** Si no la tiene, el sistema debe crear una sesión nueva en `CONFIGURADA` con un UUID nuevo, la configuración, la copia del perfil y `reintentos_planificacion = 0`, y responder `201`. La copia del perfil (`contexto_perfil_snapshot`) es el objeto `{ "perfilId", "nombre", "contextoProfesional", "rolesAspirados", "actualizadoEn" }` tomado de la copia local en ese instante; no cambia aunque llegue después otro evento del perfil (CA-4.4.2).
- **REQ-SES-08** El sistema no debe crear preguntas, turnos ni ejecuciones de IA en esta operación.
- **REQ-SES-09** Si el usuario tiene una sesión `EN_CURSO`, el sistema debe incluir `ongoingSession: { sessionId, answersCount }`, donde `answersCount` es el número de respuestas definitivas (`intento_respuesta.es_definitiva = true`) de esa sesión, de 0 a 5; si no la tiene, `ongoingSession: null`. La sesión en curso no cambia (pasa a `ABANDONADA` solo en HU-4.5).
- **REQ-SES-10** El sistema debe ignorar cualquier campo no esperado del cuerpo (`status`, `estado`, `firebaseUid`, `ownerId`, `plan`, `planningRetries`…) y tomar el dueño solo de `X-User-Id`.
- **REQ-SES-12** El sistema no debe leer `X-User-Plan` ni aplicar cupo (lo hace HU-4.6).

### 4.3 Anti-duplicados (`Idempotency-Key`)

- **REQ-SES-11** Si llega `Idempotency-Key` (1 a 64 caracteres `^[A-Za-z0-9._-]+$`; si no cumple, `IDEMPOTENCY_KEY_INVALID_FORMAT` en el 422 de REQ-SES-01), el sistema debe: calcular la huella SHA-256 (hex en minúsculas) de la configuración ya normalizada; si existe una clave igual para el mismo usuario y la operación `CREAR_SESION` con la **misma huella**, devolver el mismo estado (201 o 200) y el mismo cuerpo guardados, sin tocar nada; si existe con **otra huella**, responder `409` `IDEMPOTENCY_KEY_CONFLICT`; si no existe, procesar y guardar, en la misma transacción, la clave con el estado y el cuerpo de la respuesta. Solo se guardan respuestas 2xx: un 409 o un 422 no queda guardado, así el reintento con la misma clave tras `PROFILE_SYNC_PENDING` puede funcionar (solicitud 4.1 del borrador a Frontend).
- **REQ-SES-15** Cuando dos peticiones con la misma clave lleguen a la vez, la segunda que intente guardar la clave (violación de `uq_clave_idempotencia_usuario_operacion_clave`) debe, fuera de la transacción revertida, releer la clave y devolver la respuesta guardada (misma huella) o `409 IDEMPOTENCY_KEY_CONFLICT` (otra huella). Resultado: una sola sesión y dos respuestas iguales.
- **REQ-SES-16** Sin `Idempotency-Key`, el sistema procesa la petición sin guardar clave. Un doble envío sin clave no crea dos sesiones: la segunda reutiliza la configurada (200, mismo `sessionId`) o, si llega a la vez, recibe `409 SESSION_ALREADY_EXISTS` (código de CM-311) sin crear nada.

### 4.4 Fallos

- **REQ-SES-13** Si algo falla después de escribir (por ejemplo, al guardar la clave), el sistema debe revertir todo: ninguna sesión nueva, la configurada sin cambios, la `EN_CURSO` sin cambios y sin `fecha_fin` (CA-4.4.10). La respuesta es la del error que lo causó (500 `INTERNAL_ERROR` si es imprevisto).
- **REQ-SES-14** Base caída → 503 `DATABASE_UNAVAILABLE` (CM-311). Dos reconfiguraciones simultáneas de la misma sesión → 409 `SESSION_CONFLICT` (CM-311, `@Version`).

## 5. Contrato

**Petición**

```http
POST /api/v1/interviews/sessions
X-User-Id: <firebaseUid, lo pone el Gateway>
Idempotency-Key: 6f9a1c2e-0b7d-4d1a-9a55-1d2c3b4a5e6f      (opcional)
Content-Type: application/json

{
  "profileId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "targetRoleId": "a0000001-0000-0000-0000-000000000002",
  "jobOffer": "Buscamos desarrollador backend con Java…",
  "mode": "SIMULACION",
  "tone": "PROFESIONAL_NEUTRO",
  "answerFormat": "AUDIO",
  "sessionLanguage": "es-CO"
}
```

**Respuesta 201 (nueva) o 200 (reutilizada)**, `Content-Type: application/json;charset=UTF-8`:

```json
{
  "sessionId": "3b1f8f2a-6c1e-4d8b-9f0a-2c3d4e5f6a7b",
  "status": "CONFIGURADA",
  "configuration": {
    "profileId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "targetRoleId": "a0000001-0000-0000-0000-000000000002",
    "jobOffer": "Buscamos desarrollador backend con Java…",
    "mode": "SIMULACION",
    "tone": "PROFESIONAL_NEUTRO",
    "answerFormat": "AUDIO",
    "sessionLanguage": "es-CO"
  },
  "ongoingSession": { "sessionId": "0e2d…", "answersCount": 2 }
}
```

`ongoingSession` es `null` sin entrevista en curso. `configuration` es aditivo frente al borrador a Frontend (cumple «la configuración actualizada» de CA-4.4.9).

| Estado | `code` | Cuándo |
|---|---|---|
| 201 / 200 | — | §4.2 |
| 401 | `IDENTITY_REQUIRED` | identidad inválida (CM-311) |
| 404 | `PROFILE_NOT_FOUND` | perfil de otra persona |
| 409 | `PROFILE_SYNC_PENDING` | perfil sin copia o no activo |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | misma clave, otra configuración |
| 409 | `SESSION_ALREADY_EXISTS` | dos creaciones simultáneas sin clave (CM-311) |
| 409 | `SESSION_CONFLICT` | dos reconfiguraciones simultáneas (CM-311) |
| 415 | `MEDIA_TYPE_NOT_ALLOWED` | `Content-Type` no JSON (CM-311) |
| 422 | `VALIDATION_FAILED` | §7 |
| 422 | `REQUEST_BODY_INVALID_FORMAT` | JSON roto, vacío o un campo con objeto/arreglo (CM-311) |
| 503 | `DATABASE_UNAVAILABLE` | base caída (CM-311) |
| 500 | `INTERNAL_ERROR` | imprevisto |

## 6. Diseño

| Capa | Pieza | Qué hace |
|---|---|---|
| `domain.model` | `SessionConfigurationDraft` (`record` con los 7 textos crudos + `idempotencyKey`) | Lo que llegó, sin validar |
| `domain.model` | `FieldRejection` (`record String field, ErrorCode code, String message`) | Un campo rechazado |
| `domain.model` | `ValidatedSessionRequest` (`record`: `UUID profileId`, `UUID targetRoleId`, `String jobOffer`, `Modality`, `Tone`, `AnswerMode`, `InterviewLanguage`, `String idempotencyKey`) con `fingerprint()` (SHA-256 de una cadena canónica) | Configuración válida y normalizada |
| `domain.policy` | `SessionConfigurationPolicy` (construida con `PersonalizationCatalog` y `RoleCatalog`) | `ValidatedSessionRequest validate(SessionConfigurationDraft)`; reúne **todas** las `FieldRejection` y lanza `InvalidRequestException` |
| `domain.port` | `RoleCatalog` (`boolean isActive(UUID roleId)`) | Copia local del catálogo de roles |
| `domain.port` | `InterviewSessionRepository` (de CM-311) + `findConfiguredByOwner(FirebaseUid)`, `findInProgressByOwner(FirebaseUid)`, `countDefinitiveAnswers(UUID sessionId)` | Consultas nuevas |
| `domain.model` | `InterviewSession` (de CM-311) + `reconfigure(SessionConfiguration, Clock)` | Solo en `CONFIGURED`; reinicia `planningRetries`; si no, `SessionTransitionNotAllowedException` |
| `domain.exception` | `InvalidRequestException` (`VALIDATION_FAILED`, lleva la lista), `ProfileSyncPendingException`, `ProfileNotFoundException`, `IdempotencyKeyConflictException` | Errores nuevos (todas `extends BusinessException`) |
| `application.command` | `CreateSessionCommand` (`FirebaseUid owner`, `SessionConfigurationDraft draft`) | Entrada |
| `application.service` | `CreateSessionService` | Orquesta: valida → clave → transacción (`TransactionTemplate`) → guarda la clave → maneja la carrera de la clave |
| `application.service` | `CreateSessionResult` (`record`: `int httpStatus`, `UUID sessionId`, `SessionStatus status`, `ValidatedSessionRequest configuration`, `OngoingSession ongoing`) y `OngoingSession` (`UUID sessionId`, `int answersCount`) | Resultado; se guarda en `clave_idempotencia.cuerpo_respuesta` como JSON (Jackson) |
| `infrastructure.persistence` | `RoleCatalogAdapter` (lee con `ProfessionalRoleEntity` y `ProfessionalRoleJpaRepository`, que crea CM-209); métodos nuevos del adaptador de sesión | §8 |
| `infrastructure.config` | `DomainPolicyConfiguration` (de CM-204) + `SessionConfigurationPolicy` y `Clock` (si CM-209 no lo dejó) | Ensamblado |
| `presentation.controller` | `InterviewSessionController` (`POST /api/v1/interviews/sessions`) | Lee `X-User-Id` e `Idempotency-Key` (ambos `required = false`), arma el comando, responde 201/200 |
| `presentation.dto` | `CreateSessionRequest` (7 `String`, sin Bean Validation de negocio), `CreateSessionResponse`, `SessionConfigurationResponse`, `OngoingSessionResponse` | Contrato |
| `presentation.advice` | `BusinessExceptionHandler` (de CM-311) + un manejador para `InvalidRequestException` → 422 con `errors[]`; tres filas nuevas en la tabla de definiciones (404, 409, 409) | Errores |

**Por qué la validación vive en el dominio y no en Bean Validation:** el rol activo y la disponibilidad de la modalidad son reglas de negocio con datos (catálogo de roles y `PersonalizationCatalog`), y CA-4.4.3 pide reportarlas junto con los demás campos. Una sola política devuelve todas las causas a la vez, con su código, y se prueba sin Spring. Los campos del DTO son `String` para que un valor mal escrito sea un error de su campo y no un cuerpo ilegible.

## 7. Matriz de validación por campo (R2)

Mensajes: el de `jobOffer` es literal de CA-4.2.12; los demás no están en el backlog y siguen la regla de RT-01 («Elige {el dato}.» para listas) — DECIDIDOS POR RECOMENDACIÓN (§17, D-SES-05). Textos de una línea: se recortan antes de validar (RT-01, C-07). Todas las filas se prueban en `SessionConfigurationPolicyTest` y las marcadas con † también en `CreateSessionControllerTest`.

| Campo | Caso | Valor literal | `code` | Mensaje |
|---|---|---|---|---|
| `profileId` | ausente / `null` / `""` / `"   "` / `"\t\n"` † | — | `PROFILE_ID_REQUIRED` | «Elige un Perfil Profesional.» |
| | no UUID † | `"perfil-1"`, `"7c9e6679742540de944be07fc1f90ae7"` (sin guiones) | `PROFILE_ID_INVALID_FORMAT` | «El Perfil Profesional elegido no es válido.» |
| | UUID en mayúsculas | `"7C9E6679-7425-40DE-944B-E07FC1F90AE7"` | aceptado (se compara en minúsculas) | — |
| `targetRoleId` | ausente / vacío / espacios † | — | `TARGET_ROLE_ID_REQUIRED` | «Elige un rol objetivo.» |
| | no UUID | `"backend"` | `TARGET_ROLE_ID_INVALID_FORMAT` | «El rol objetivo elegido no es válido.» |
| | UUID que no está en el catálogo † | `"00000000-0000-0000-0000-000000000000"` | `TARGET_ROLE_ID_INVALID_VALUE` | «Elige un rol objetivo del catálogo.» |
| | rol `INACTIVE` | rol de prueba insertado con `estado = 'INACTIVE'` | `TARGET_ROLE_ID_INVALID_VALUE` | ídem |
| | rol activo fuera del perfil | `a0000001-0000-0000-0000-000000000001` con un perfil que tiene otro rol | aceptado (CA-4.2.14) | — |
| `jobOffer` | ausente / `null` / `""` / `"   "` | — | aceptado → `null` | — |
| | 3000 puntos de código † | `"😀".repeat(3000)`; `"á".repeat(3000)` | aceptado completo | — |
| | 3001 † | `"a".repeat(3001)` | `JOB_OFFER_TOO_LONG` | «La oferta laboral no puede superar los 3000 caracteres.» |
| | 3000 con espacios en los extremos | `"  " + "a".repeat(3000) + "  "` | aceptado, se guarda recortado (3000) | — |
| | combinantes en NFD que en NFC caben | `"é".repeat(3000)` (6000 en NFD, 3000 en NFC) | aceptado | — |
| | texto hostil | `"<script>alert(1)</script> ' % _"` | aceptado literal | — |
| `mode` | ausente / vacío † | — | `MODE_REQUIRED` | «Elige la modalidad.» |
| | desconocido / minúsculas | `"VIDEO"`, `"simulacion"`, `"5"` | `MODE_INVALID_VALUE` | «Elige una modalidad de la lista.» |
| | `ENTRENO` † | `"ENTRENO"` | `MODE_NOT_ALLOWED` | «Esta modalidad estará disponible próximamente.» |
| `tone` | ausente / vacío † | — | `TONE_REQUIRED` | «Elige el tono del entrevistador.» |
| | desconocido † | `"AMABLE"`, `"calido"` | `TONE_INVALID_VALUE` | «Elige un tono de la lista.» |
| `answerFormat` | ausente / vacío † | — | `ANSWER_FORMAT_REQUIRED` | «Elige la forma de respuesta.» |
| | desconocido † | `"VIDEO"`, `"texto"` | `ANSWER_FORMAT_INVALID_VALUE` | «Elige una forma de respuesta de la lista.» |
| `sessionLanguage` | ausente / vacío † | — | `SESSION_LANGUAGE_REQUIRED` | «Elige el idioma de la entrevista.» |
| | desconocido † | `"es"`, `"ES-CO"`, `"fr"` | `SESSION_LANGUAGE_INVALID_VALUE` | «Elige un idioma de la lista.» |
| `Idempotency-Key` (encabezado) | ausente | — | aceptado (sin clave, REQ-SES-16) | — |
| | 64 caracteres válidos | `"k".repeat(64)` | aceptado | — |
| | 65 / con espacio / con `/` / vacío † | `"k".repeat(65)`, `"a b"`, `"a/b"`, `""` | `IDEMPOTENCY_KEY_INVALID_FORMAT` | «Ocurrió un error. Inténtalo de nuevo.» |
| Cuerpo | vacío `{}` † | — | 6 elementos (`…_REQUIRED` de los 6 obligatorios) en el orden de REQ-SES-01 | — |
| | campo con número | `"tone": 5` | `TONE_INVALID_VALUE` (Jackson lo lee como `"5"`) | — |
| | campo con objeto o arreglo | `"tone": {"a":1}` | 422 `REQUEST_BODY_INVALID_FORMAT` (cuerpo entero, CM-311) | «Revisa el formato de los datos enviados.» |
| | JSON roto / sin cuerpo | `{"mode":` / vacío | 422 `REQUEST_BODY_INVALID_FORMAT` | ídem |
| | campos no esperados | `"status":"EN_CURSO","firebaseUid":"otro","plan":"PREMIUM"` | ignorados | — |
| `X-User-Id` | reglas de CM-311 | `""`, 129 caracteres, control | 401 `IDENTITY_REQUIRED` | «Tu sesión expiró. Inicia sesión de nuevo.» |

## 8. Base de datos (R4): `V4__operacion_crear_sesion.sql` y lectura de `rol_profesional`

### 8.1 `rol_profesional` (copia del catálogo de Perfil; la crea el criterio de catálogo de roles de CM-209, `V3`)

Esta tarea **no crea ni siembra** la tabla: la crea el criterio de catálogo de roles de CM-209 (D-SES-02), que consume el evento del catálogo de Perfil y hace un `upsert` por `id`. Esta tarea solo la lee (`RoleCatalog.isActive`). La forma que debe tener la tabla es:

| Columna | Tipo | Nulo | Regla |
|---|---|---|---|
| `id` | `uuid` | no | `pk_rol_profesional` |
| `nombre_es` | `varchar(255)` | no | `ck_rol_profesional_nombre_es` (no en blanco) |
| `nombre_en` | `varchar(255)` | no | `ck_rol_profesional_nombre_en` (no en blanco) |
| `estado` | `varchar(8)` | no, `DEFAULT 'ACTIVE'` | `ck_rol_profesional_estado` IN (`ACTIVE`, `INACTIVE`) |
| `actualizado_en` | `timestamptz` | no | — |
| `fecha_replicacion` | `timestamptz` | no | — |

Sin semilla en esta tarea: los 46 roles de `cameia-perfil` (`V3__catalogo_roles_profesionales.sql`, `V4__i18n_catalogo_roles.sql`, `V5_1`) llegan por el evento del catálogo, y un rol nuevo se crea solo en Perfil. Sin `categoria` (nadie la usa en Entrevista). Sin índice extra: la consulta es por clave primaria. El criterio de catálogo de roles de CM-209 (§25) fija el evento de Perfil (`perfil.catalogo-roles-actualizado`), la cola, esta tabla y su carga completa al arrancar Perfil; mientras la tabla esté vacía todo `targetRoleId` se rechaza con `TARGET_ROLE_ID_INVALID_VALUE`.

### 8.2 `clave_idempotencia.operacion`

CM-311 deja `ck_clave_idempotencia_operacion` como «no vacía» y encarga a HU-4.4 fijar los valores: `ALTER TABLE … DROP CONSTRAINT ck_clave_idempotencia_operacion, ADD CONSTRAINT ck_clave_idempotencia_operacion CHECK (operacion IN ('CREAR_SESION'))`. CM-216 la amplía en `V4`.

### 8.3 Sin cambios en `sesion`

El nombre del rol no se guarda en la sesión: HU-4.5 lo toma de `rol_profesional` en el idioma de la sesión (`es-CO` → `nombre_es`, `en` → `nombre_en`). Esto cierra el hallazgo A-2 de CM-311.

## 9. Casos borde (R5)

| Grupo | Caso | Resultado | Prueba |
|---|---|---|---|
| Presencia | §7 | §7 | §7 |
| Texto | 3000/3001, NFC, emoji, extremos, hostil | §7 | §7 |
| Números | `answersCount` 0, 2, 5 | se informa tal cual | `…_withAnswersCount` (0 y 2), `…WithFiveAnswers` |
| Fechas | `fecha_creacion` y `fecha_actualizacion` = reloj fijo `2026-10-20T15:00:00Z`; al reutilizar, `fecha_creacion` no cambia | — | `…_shouldReuseConfigured_with200AndResetRetries` |
| Enumerados | minúsculas, desconocido, vacío, `ENTRENO` | §7 | §7 |
| Duplicados | misma clave 20 veces; misma clave a la vez (2 hilos); otra clave con la misma configuración (doble clic sin clave) | 1 sesión; respuestas iguales; 200 o 409 `SESSION_ALREADY_EXISTS` | `…when20Repetitions`, `…whenSameKeyConcurrent`, `…_shouldNotDuplicate_whenTwoKeysConcurrent` |
| Duplicados | misma clave, otra configuración | 409 `IDEMPOTENCY_KEY_CONFLICT` | `…_shouldReturn409_whenSameKeyDifferentBody` |
| Duplicados | misma clave tras un 409 `PROFILE_SYNC_PENDING` y la copia llega | 201 (el 409 no se guardó) | `…_shouldSucceed_whenRetryWithSameKeyAfterSyncPending` |
| Duplicados | misma clave de otro usuario | no la ve: crea su propia sesión | `…_shouldIsolateKeysPerUser` |
| Estado | configurada + en curso a la vez | 200 reutiliza; la en curso intacta | `…_whenAlsoInProgress` |
| Estado | `reconfigure` en `IN_PROGRESS` | `SessionTransitionNotAllowedException` (no ocurre por el flujo; prueba de dominio) | `InterviewSessionTest.reconfigure_shouldThrow_whenNotConfigured` (4 estados) |
| Estado | copia `IN_PROGRESS` o archivada | 409 | `…_whenReplicaNotUsable` (2) |
| Propiedad | perfil de `uid-b` pedido por `uid-a` | 404 `PROFILE_NOT_FOUND`, nada creado | `…_whenProfileOfOtherUser` |
| Propiedad | `firebaseUid` en el cuerpo | ignorado | `…_andUseHeaderOwner` |
| Colecciones | catálogo de roles (lo llena el criterio de catálogo de roles de CM-209) | `rol_profesional` vacía | `RoleCatalogAdapterTest.isActive_shouldBeFalse_forUnknown` (cubre la tabla vacía) |
| Concurrencia | dos reconfiguraciones con `@Version` | 409 `SESSION_CONFLICT` | `…_shouldReturnConflict_whenConcurrentReconfigure` |
| Falla parcial | la base falla al guardar la clave después de crear la sesión | todo revertido; 503 `DATABASE_UNAVAILABLE` (CM-311). Un fallo de programación en ese punto: todo revertido y 500 `INTERNAL_ERROR` | `…_shouldRollbackAll_whenFailsAfterInsert` (parametrizada: caída de la base → 503; `IllegalStateException` → 500) |
| Dependencias | Perfil caído | no se llama: funciona igual | por diseño (HT03-C7) |
| Dependencias | base caída | 503 | CM-311 |
| Carga | `jobOffer` de 5 MB | 422 `JOB_OFFER_TOO_LONG` tras leerlo (sin tope de cuerpo global: hallazgo H-4) | `…_shouldReject5MbOffer` |

## 10. Errores (R3)

Nuevos en `ErrorCode` (al final, en este orden), con su fila en `docs/errores.md`:

| Código | HTTP | Título | Mensaje | Excepción | Prueba |
|---|---|---|---|---|---|
| `PROFILE_NOT_FOUND` | 404 | Perfil no encontrado | «No encontramos lo que buscabas.» | `ProfileNotFoundException` | `…_whenProfileOfOtherUser` |
| `PROFILE_SYNC_PENDING` | 409 | Perfil en actualización | «Tu perfil se está actualizando. Inténtalo en unos segundos.» | `ProfileSyncPendingException` | `…_whenProfileNotReplicated` |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | Solicitud distinta | «Esta solicitud no coincide con la que enviaste antes. Inténtalo de nuevo.» | `IdempotencyKeyConflictException` | `…_whenSameKeyDifferentBody` |
| 16 códigos de campo de §7 | 422 (dentro de `VALIDATION_FAILED`) | — | §7 | `InvalidRequestException` | `SessionConfigurationPolicyTest` |

**Rutas al genérico revisadas (regla de errores visibles).**

| Ruta posible | Tratamiento | Prueba |
|---|---|---|
| Base caída o sin conexión | 503 `DATABASE_UNAVAILABLE` (CM-311) | `…create_shouldReturn503_whenDatabaseDown` |
| Dos peticiones con la misma clave a la vez (`uq_clave_idempotencia_usuario_operacion_clave`) | REQ-SES-15: la perdedora relee la respuesta guardada; no llega al cliente | `…whenSameKeyConcurrent` |
| Dos reconfiguraciones a la vez (`@Version`) | 409 `SESSION_CONFLICT` | `…whenConcurrentReconfigure` |
| Dos sesiones configuradas a la vez para el mismo usuario (índice único de la sesión configurada) | 409 `SESSION_ALREADY_EXISTS` | `…_shouldNotDuplicate_whenTwoKeysConcurrent` |
| `jobOffer` de más de 3000 puntos de código (columna `varchar(3000)`, igual al límite del dominio) | 422 `JOB_OFFER_TOO_LONG` antes de tocar la base | §7 |
| Clave de idempotencia de más de 64 caracteres (columna `varchar(64)`, igual al límite) | 422 `IDEMPOTENCY_KEY_INVALID_FORMAT` | §7 |
| Fecha | Ninguna fecha entra por el cuerpo; las fechas las pone el reloj del servidor | — |
| Cualquier otra excepción | 500 `INTERNAL_ERROR`: solo fallo de programación, con el `requestId` en el log | `…_shouldRollbackAll_whenFailsAfterInsert` (caso de programación) |

Los errores de forma salen todos a la vez, un elemento por campo (§7, cuerpo `{}`: 6 elementos).

`ErrorCodeTest` (CM-311) se amplía con el conjunto «códigos de campo» para clasificar los 16. Ningún código existente se renombra. La ruta al 500 genérico queda solo para lo imprevisto; `IdempotencyKeyAlreadyExistsException` de CM-311 nunca llega al cliente desde esta ruta (REQ-SES-15).

## 11. Seguridad (R6)

### 11.1 ASVS 5.0.0 nivel 1

| ID | Estado | Qué hace CM-207 | Prueba |
|---|---|---|---|
| 1.2.3 | CUMPLE | DTO serializados | `CreateSessionControllerTest` |
| 1.2.4 | CUMPLE | Consultas JPA y nativas con parámetros (`countDefinitiveAnswers`) | `…_withAnswersCount` |
| 2.1.1 / 2.2.1 / 2.2.2 | CUMPLE | §7 en el servidor | `SessionConfigurationPolicyTest` |
| 2.3.1 | PENDIENTE → avanza | La sesión solo nace en `CONFIGURADA` y solo se reconfigura en ese estado | `InterviewSessionTest.reconfigure_*` |
| 4.1.1 | NO_CUMPLE (Gateway) | 201/200 y errores con `charset=UTF-8` | `…_shouldDeclareCharset` |
| 8.2.2 | CUMPLE | El perfil debe ser del usuario (404 si no); el dueño de la sesión es el del encabezado | `…_whenProfileOfOtherUser`, `…_andUseHeaderOwner` |
| 11.4.1 | PENDIENTE → CUMPLE para esta ruta | La huella usa SHA-256 (`MessageDigest.getInstance("SHA-256")`) | `ValidatedSessionRequestTest.fingerprint_shouldBeSha256Hex` |
| 14.2.1 | CUMPLE | Nada sensible en la URL | — |
| 15.3.1 | CUMPLE | Respuesta sin datos del perfil | `…_shouldNotExposeProfileData` (el cuerpo no contiene `resumen` ni `Python`) |

Hallazgo para la matriz (DevOps vía Vela): 11.4.1 cita solo Wompi; con CM-207 hay un uso real de SHA-256 en Entrevista.

### 11.2 OWASP API Top 10

| Riesgo | Cómo |
|---|---|
| API1 | Perfil propio comprobado en la copia; sesión del dueño del encabezado; prueba con dos usuarios |
| API2 | Solo `X-User-Id` |
| API3 | Campos no esperados ignorados; la respuesta no expone la copia del perfil |
| API4 | `jobOffer` ≤ 3000; claves ≤ 64; sin tope de cuerpo global (H-4) |
| API5 | Cualquier usuario autenticado crea sus sesiones; no hay función de administración |
| API6 | Doble envío controlado (clave + única configurada); sin cupo en el MVP (HU-4.6, decisión del PO del 15-oct) |
| API7 | Sin URL de salida |
| API8 | Errores sin trazas; OpenAPI solo en `local` |
| API9 | Ruta documentada en OpenAPI |
| API10 | La copia del perfil ya se validó al consumirse (CM-209) |

Pruebas de SEG-02: sin identidad, perfil ajeno con segundo usuario, identidad en el cuerpo ignorada.

## 12. Atributos de calidad (R13)

Épica HE-04.

| Atributo | Métrica | Límite | Cómo | Prueba | Resultado real |
|---|---|---|---|---|---|
| AC-0002 | DES-02 | p95 ≤ 2 s | Lecturas por clave o por índice único parcial; sin llamadas externas | `CreateSessionEndToEndTest.create_shouldMeetLatencyBudget_when30Requests` (30 creaciones de 30 usuarios; p95 ≤ 2000 ms) | PENDIENTE |
| AC-0002 | DES-04 | 5xx < 1 % | Todo fallo previsible con su 4xx/503 | §10 | PENDIENTE |
| AC-0003 | SEG-01, SEG-02 | 100 % | §11 | ídem | PENDIENTE |
| AC-0003 | SEG-04 | 0 datos personales en logs | Se registran `sessionId`, `firebaseUid`, `code`; nunca `jobOffer` ni la copia | `…logs_shouldNotContainOfferOrProfile` | PENDIENTE |
| AC-0004 | FIA-01 | Último estado confirmado | Transacción única; rollback total | `…_shouldRollbackAll_whenFailsAfterInsert` | PENDIENTE |
| AC-0004 | FIA-02 | 0 efectos en 20 repeticiones | `Idempotency-Key` | `…when20Repetitions` | PENDIENTE |
| AC-0006 | INT | `errors[]` con `field`, `code`, `message`, todos a la vez | §7 | `…WithSixFields`, `…WithFiveFields` | PENDIENTE |
| Transversal | MAN-01 | ≥ 90 % | §14 | `jacoco.csv` | PENDIENTE |
| Transversal | MAN-03 | 0 FK entre servicios | `perfil_id`, `rol_objetivo_id` sin FK a otro servicio; `rol_profesional` es copia local | prueba de esquema | PENDIENTE |
| Transversal | IOP-01 | OpenAPI | `@ApiResponse` de 201, 200, 401, 404, 409, 415, 422, 503, 500 | `…openApi_shouldDocumentCreateSession` | PENDIENTE |
| Restricción | REST-0003 | Datos mínimos | La copia en la sesión es la del perfil, sin datos de cuenta | revisión | PENDIENTE |

## 13. Postman y Newman (R11)

Carpeta «Crear sesión» de la colección. Preparación en la propia carpeta: publicar por la API de administración del broker (carpeta de CM-209) un evento del perfil `7c9e6679-7425-40de-944b-e07fc1f90ae7` de `uid-postman-a` y esperar 2 s. Peticiones a `{{baseUrl}}/api/v1/interviews/sessions` con `X-User-Id` a mano:

| Petición | Espera |
|---|---|
| `201 sesión nueva` (clave `{{$guid}}` guardada en variable) | 201; `status` `CONFIGURADA`; `sessionId` UUID; `ongoingSession` `null`; `configuration` igual al cuerpo |
| `201 repetición con la misma clave` | 201; mismo `sessionId` |
| `409 misma clave, otra configuración` (cambia `tone`) | 409 `IDEMPOTENCY_KEY_CONFLICT` |
| `200 reutiliza la configurada` (clave nueva, `tone = ESTRICTO`) | 200; mismo `sessionId`; `configuration.tone` `ESTRICTO` |
| `201 oferta en blanco = null` (otro usuario con su propio perfil publicado) | `configuration.jobOffer` `null` |
| `201 oferta de 3000` / `422 oferta de 3001` (texto generado en el script previo) | 201 / 422 con `errors[0].field = jobOffer` y el mensaje literal |
| `422 seis obligatorios` (`{}`) | 6 elementos en el orden de REQ-SES-01 |
| `422 cinco valores fuera del catálogo` (`ENTRENO`, `AMABLE`, `VIDEO`, `fr`, rol `0000…`) | 5 elementos: `targetRoleId`, `mode` (`MODE_NOT_ALLOWED`), `tone`, `answerFormat`, `sessionLanguage` |
| `409 perfil sin copia` (perfil aleatorio) | 409 `PROFILE_SYNC_PENDING` y el mensaje literal |
| `404 perfil de otra persona` (`uid-postman-b` con el perfil de `uid-postman-a`) | 404 `PROFILE_NOT_FOUND` |
| `201 campos no esperados ignorados` (`status`, `firebaseUid`) | 201; `status` `CONFIGURADA` |
| `201 texto e inglés con plan FREE` (`X-User-Plan: FREE`) | 201 |
| `401 sin identidad` | 401 `IDENTITY_REQUIRED` |
| `415 texto plano` | 415 `MEDIA_TYPE_NOT_ALLOWED` |
| `422 JSON roto` | 422 `REQUEST_BODY_INVALID_FORMAT` |
| `422 clave inválida` (`Idempotency-Key: a b`) | `errors[].field` contiene `Idempotency-Key` |

Cada error comprueba `problem+json` con charset, `requestId` y ausencia de trazas. Comando: `npx newman run … --folder "Crear sesión"` → 17 peticiones (con la preparación), 0 fallos.

## 14. Pruebas y cobertura (R9, R10)

| Capa | Clases |
|---|---|
| Dominio | `SessionConfigurationPolicyTest`, `ValidatedSessionRequestTest`, `InterviewSessionTest` (métodos nuevos de `reconfigure`) |
| Aplicación | `CreateSessionServiceTest` (fakes en memoria de los puertos, `TransactionTemplate` simulado) |
| Persistencia | `CreateSessionSchemaMigrationTest` (`V4`), `RoleCatalogAdapterTest`, `InterviewSessionRepositoryAdapterTest` (consultas nuevas) |
| Web | `CreateSessionControllerTest` (MockMvc con el manejador real) |
| Integración | `CreateSessionEndToEndTest` (`RANDOM_PORT`, Testcontainers PostgreSQL; copias del perfil insertadas con `ProfileReplicaRepository`) |
| Arquitectura y catálogo | `LayeredArchitectureTest`, `ErrorCodeTest`, `ErrorCodeDocumentationTest` |

Clases a medir: todas las nuevas de §6 y los métodos agregados a `InterviewSession`, al adaptador de sesión y al manejador. Comando final: `./mvnw.cmd -B -ntp clean verify`.

## 15. Dependencias y cómo se prueba mientras no existan

| Depende de | Si no está | Cómo se prueba |
|---|---|---|
| CM-311 | No se empieza (todo lo usa) | — |
| CM-204 | Stack sobre su rama | — |
| CM-209 | Stack sobre su rama | Las pruebas insertan la copia del perfil con su puerto; no hace falta RabbitMQ |
| HU-4.5 (iniciar) | No afecta | — |
| CM-216 (respuestas) | `answersCount` vale 0 | Las pruebas insertan `pregunta`, `turno` e `intento_respuesta` por SQL para 2 y 5 respuestas |

## 16. Fuera de alcance

Iniciar la sesión, abandonar la anterior y el nombre del rol en el prompt (HU-4.5); cupo del plan (HU-4.6); cancelar (`DELETE`, HU-4.5); `…/state`; reintentos automáticos del cliente (Frontend); vencimiento y limpieza de las claves anti-duplicados (H-5).

## 17. Decisiones

| # | Decisión | Opciones | Por qué | Quién |
|---|---|---|---|---|
| D-SES-01 | Nombres JSON: `profileId`, `targetRoleId`, `jobOffer`, `mode`, `tone`, `answerFormat`, `sessionLanguage`; respuesta `sessionId`, `status`, `configuration`, `ongoingSession {sessionId, answersCount}` | A) esos; B) los nombres de los enumerados (`modality`, `answerMode`, `language`); C) los del backlog en español | A son los de `cameia-web` en `camelCase` y los del borrador a Frontend; cumplen L-12 y el `CLAUDE.md` de Entrevista. `configuration` es aditivo y cumple CA-4.4.9 | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)**. Si elige otra, cambian DTO, pruebas y el borrador a Frontend |
| D-SES-02 | El rol objetivo se valida contra una **copia local del catálogo de Perfil que Entrevista mantiene al día con un evento del catálogo** (criterio agregado a CM-209, sin llamadas entre servicios) | A) copia por migración (cada rol nuevo exige otra migración en Entrevista); B) llamar a `GET /api/v1/profiles/professional-roles` de Perfil; C) validar contra los roles del propio perfil (descartada: CA-4.2.14 acepta cualquier rol activo del catálogo y la copia del perfil solo trae un nombre por rol); D) evento del catálogo | HT-03 dice que Entrevista ya no tiene permiso de invocación hacia Perfil (B exige IAM y red nuevas y una dependencia en línea, contra la decisión v5 de réplicas); C incumple CA-4.4.3. D no tiene dependencias en ejecución, da el nombre en los dos idiomas a HU-4.5 y escala: crear un rol solo toca Perfil. Costo: un evento y un consumidor nuevos en Perfil y Entrevista (unas 6 a 8 horas más, que se suman a CM-209 y no a esta tarea) | **DECIDIDA POR PAULA (22-oct, opción D, tras descartar A por no escalar y C por CA-4.2.14)**. Aviso al PO: la nota de HU-4.3 «lo traduce a es \| en al llamar al catálogo de roles» queda como «al leer su copia del catálogo» |
| D-SES-03 | `Idempotency-Key` **opcional** | A) opcional; B) obligatoria (422 si falta) | `cameia-web` la envía siempre desde el 22-oct, pero otros clientes (Postman, pruebas) no tienen por qué; sin clave, la regla de «una configurada por usuario» ya evita dos sesiones (REQ-SES-16). Con clave, CA-4.4.17 se cumple literal | **DECIDIDA POR PAULA (22-oct, «lo que sea mejor» = opción A)**. Frontend ya la envía y la reutiliza en los reintentos (respuesta del 22-oct, 4.1: confirmado) |
| D-SES-04 | Solo se guardan respuestas 2xx de la clave | A) solo 2xx; B) guardar también 409/422 | Con B, el reintento con la misma clave tras `PROFILE_SYNC_PENDING` (CA-4.4.19) repetiría el 409 para siempre | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-SES-05 | Mensajes de campo de §7 (salvo el de `jobOffer`, literal de CA-4.2.12) | A) los de §7, con la regla de RT-01; B) pedirlos al PO | El backlog no los define; RT-01 da el patrón; Frontend valida antes y casi nunca se ven | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)**; un CA futuro manda sobre ellos |
| D-SES-06 | La huella se calcula sobre la configuración **normalizada**, no sobre el cuerpo crudo | A) normalizada; B) bytes del cuerpo | Espacios o el orden de los campos no deben dar `IDEMPOTENCY_KEY_CONFLICT` | Inferencia de Backend |
| D-SES-07 | La copia del perfil en la sesión es `{perfilId, nombre, contextoProfesional, rolesAspirados, actualizadoEn}` | A) esa; B) solo `contextoProfesional` | El nombre del perfil y los roles aspirados sirven al prompt de HU-4.5; no hay datos de cuenta | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-SES-08 | Un perfil archivado o no activo da 409 `PROFILE_SYNC_PENDING` (como uno ausente) | A) 409; B) 422 propio | El CA solo prevé 404 y 409; el archivado no existe todavía (HU-2.14), que fijará su propio caso | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)**; hallazgo H-6 a HU-2.14 |

## 18. Integración con la ola

| Recurso | Qué hace CM-207 |
|---|---|
| Migración | `V4__operacion_crear_sesion.sql` (`V2` es de CM-209, `V3` de el criterio de catálogo de roles de CM-209 de roles; `V5` queda para CM-216) |
| `ErrorCode` / `docs/errores.md` | +19 al final; `ErrorCodeTest` con el conjunto de códigos de campo |
| `BusinessExceptionHandler` | + manejador de `InvalidRequestException`; +3 filas en la tabla de definiciones |
| `DomainPolicyConfiguration` | + `SessionConfigurationPolicy`; `Clock` si falta |
| `InterviewSessionRepository` | +3 métodos (las pruebas de CM-311 siguen en verde) |
| Postman | carpeta «Crear sesión» |
| ADR / bitácora | `0008-crear-sesion-y-anti-duplicados.md`; bitácora `06` |

Rama `CM-207-crear-sesion` desde `origin/develop` con CM-311, CM-204 y CM-209 fusionadas. Si no: stack lineal CM-311 ← CM-204 ← CM-209 ← CM-207 (solo el de abajo apunta a `develop`; rebase con `--onto` al fusionar cada uno). Diff estimado ≈ 2 600 líneas (≈ 900 de código, ≈ 1 400 de pruebas, ≈ 300 de semilla, documentación y Postman).

## 19. Línea base

`./mvnw.cmd -B -ntp clean verify` verde sobre la base (lo que dejan CM-311, CM-204 y CM-209), sin clases de Testcontainers omitidas.

## 20. Lista de verificación

| # | Qué | Cómo | Esperado |
|---|---|---|---|
| V-01 | CA-4.4.1, 4.4.15, 4.4.16 | `CreateSessionEndToEndTest` + Newman | 201 / 422 literal |
| V-02 | CA-4.4.2 | `…_whenProfileChangesLater` | copia intacta |
| V-03 | CA-4.4.3, 4.4.14 | `SessionConfigurationPolicyTest`, controlador, Newman | 5 y 6 campos a la vez |
| V-04 | CA-4.4.7, 4.4.18, 4.4.6 | `…OngoingSession…` | `answersCount` 0, 2, 5; en curso intacta |
| V-05 | CA-4.4.9 | `…ReuseConfigured…` | 200, mismo id, reintentos 0 |
| V-06 | CA-4.4.10 | `…RollbackAll…` | nada cambia |
| V-07 | CA-4.4.11, 4.4.12, 4.4.19 | `…SyncPending…`, `…OtherUser` | 409/404 literales |
| V-08 | CA-4.4.13 | `…UnexpectedFields…` | ignorados |
| V-09 | CA-4.4.17 | `…SameKey…`, concurrente, 20 repeticiones | 1 sesión |
| V-10 | CA-4.3.8, 4.3.9 | `…TextAndEnglish_withFreePlan` | 201 |
| V-11 | R1–R14 | tabla de la revisión | cumple |
| V-12 | R4 | `CreateSessionSchemaMigrationTest` | `CHECK` de operación |
| V-13 | R10 | `jacoco.csv` | ≥ 90 % |
| V-14 | R11 | Newman | 17/17 |
| V-15 | R13 | §12 con resultados | llena |
| V-16 | Adversarial | ajeno, cuerpo con dueño, 5 MB, claves hostiles, carreras, base caída | según §9 |

## 21. Revalidar cuando CM-311 y CM-312 estén implementadas

| Lo prestado | Dónde | Cómo |
|---|---|---|
| `InterviewSession.configure`, `SessionConfiguration` (orden de componentes: `profileId, targetRoleId, jobOffer, profileSnapshotJson, modality, tone, answerMode, language`), `SessionStatus.CONFIGURED`, `MAX_JOB_OFFER_LENGTH = 3000` | `domain/model/` | compilar |
| `InterviewSessionRepository.save/findById` y su adaptador con `saveAndFlush` y traducción de `uq_sesion_*` a `SessionAlreadyExistsException` | CM-311 T4 | sus pruebas |
| `IdempotencyKeyRepository.find(owner, operation, key)` / `save(...)` e `IdempotencyKey` (`requestFingerprint`, `responseStatus`, `responseBodyJson`) | CM-311 T3/T4 | compilar |
| Manejador: tabla `Definition`, método de `MethodArgumentNotValidException` como modelo del de `InvalidRequestException`, `firebaseUidParaLog()` | `presentation/advice/BusinessExceptionHandler.java` | `BusinessExceptionHandlerTest` |
| `ErrorCodeTest` con conjuntos por tipo | CM-311 T5 | ampliar |
| `PersonalizationCatalog.isAvailable` y `DomainPolicyConfiguration` | CM-204 | compilar |
| `ProfileReplicaRepository.findById`, `ProfileReplica` | CM-209 | compilar |
| Columnas `sesion.contexto_perfil_snapshot jsonb object`, `oferta_laboral varchar(3000)`, `reintentos_planificacion` | `V1` | `CreateSessionEndToEndTest` |

## 22. Hallazgos con destino

| # | Hallazgo | Tipo | Destino |
|---|---|---|---|
| B-1 | Jira no leído | Verificación | T0 |
| H-1 | `cameia-web` (`836d8e3`) ya envía `camelCase` y `Idempotency-Key` | Ninguna | Cerrado con la respuesta de Frontend del 22-oct |
| H-2 | El borrador a Frontend §6.1 no menciona `configuration` y dice que la repetición devuelve «201» (devuelve el estado guardado: 201 o 200) | Corrección de un documento sin enviar | Paula, antes de enviarlo (no se edita `comunicaciones/` en esta sesión) |
| H-3 | La nota de HU-4.3 habla de «llamar al catálogo de roles»; HT-03 prohíbe la llamada | Redacción del libro | Aviso al PO (D-SES-02: la copia se mantiene con un evento) |
| H-4 | Sin tope global de tamaño del cuerpo (un `jobOffer` de 5 MB se lee entero antes del 422) | Decidido por Paula (22-oct): se deja como está, sin límite propio en esta tarea | Pendiente de DevOps (`cronograma/plan-sprint4_entrevista-voz-reporte.md` §4). **Debe probarse con una petición real en el entorno desplegado**: `POST` con un `jobOffer` de 5 MB; el resultado (código y estado devueltos, sin 500 genérico ni caída del servicio) se anota en la verificación del entorno de CM-207. Si responde un 500 genérico, es un defecto con su propio código |
| H-5 | `clave_idempotencia` sin vencimiento ni limpieza | Pendiente | Sin acción en el MVP; revisar con HU-5.2 (más claves) |
| H-6 | Perfil archivado responde 409 «se está actualizando» | Pendiente | Spec de HU-2.14 |
| H-7 | Jira 3,8 h frente a 14 a 16 h | Aviso a Vela | Resumen |

## 23. Hechos, inferencias y decisiones

- **Hechos:** §2; 46 roles activos en Perfil; `cameia-web` con `camelCase` y clave siempre.
- **Inferencias:** D-SES-06; que `configuration` es aditivo; estimación.
- **Decisiones por recomendación (ratificadas por Paula el 22-oct, opción A; D-SES-02 = D):** D-SES-01 a D-SES-05, D-SES-07, D-SES-08.
- **PENDIENTE de persona:** ninguno bloqueante.

## 24. Puerta de listo

Hechos: trazabilidad (§3), validación (§7), datos (§8), bordes (§9), errores (§10), seguridad (§11), atributos (§12), contraste (§2, §21), integración (§18), línea base (§19), lista (§20). **Veredicto: LISTA PARA EJECUTAR** con CM-311, CM-204 y CM-209 fusionadas o en stack. Aprobada por Paula el 22-oct-2026.
