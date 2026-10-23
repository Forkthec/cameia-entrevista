# CM-209 · Entrevista recibe el evento del perfil y guarda su copia (HT-03)

| Dato | Valor |
|---|---|
| Tarea Jira | CM-209 (HT-03). Título en Jira desactualizado («Llamada de Entrevistas a Perfil…»; pedido a Vela el 13-oct, sin cambio según `comunicaciones/seguimiento/respuestas-y-pendientes.md` fila del 13-oct). Sin responsable en Jira (`ESTADO.md`, «Pendiente de otros roles»); esta ola la asigna a Paula |
| Historia | HT-03 «Réplica del Perfil Profesional en Entrevistas» (backlog v8.3, hoja «Backlog», fila 31; sin CA numerados) |
| Sprint | Jira: Sprint 4 según la hoja «Backlog» (columna «Sprint (Jira)»). Jira no se pudo leer en esta sesión (brecha B-1). `cronograma/cronograma.md` §5: podría entrar al Sprint 3 tras CM-311 |
| Repositorio | `cameia-entrevista`, base `origin/develop` `bcc697d` **más CM-311 y CM-312 tratadas como implementadas** (§20) y, si ya se fusionó, CM-204 |
| Productor del evento | `cameia-perfil` `origin/develop` `1201943` (CM-67, fusionada): `docs/eventos/perfil-profesional-actualizado-v1.md`, su esquema JSON, `ProfileUpdatedPayloadV1`, `RabbitConfig` |
| Depende de | CM-311 (esquema `microentrevista`, `V1`, `FirebaseUid`, Testcontainers, ArchUnit). Topología del broker CM-293 (finalizada; cada servicio declara su topología al arrancar, como ya hace Perfil) |
| Desbloquea | CM-207 (crear la sesión lee la copia: 404 con otro dueño, 409 `PROFILE_SYNC_PENDING` si falta) |
| Estimación | Jira: 4 h (hoja «Backlog»). Backend: **16 a 20 h** (10 a 12 h de la copia del perfil más 6 a 8 h del catálogo de roles, §25) (migración, dominio, consumidor con cola de fallidos, pruebas contra RabbitMQ y PostgreSQL reales, documentación del evento). Se informa a Vela; no se cambia en Jira |
| Estado | **LISTA PARA EJECUTAR** con CM-311 fusionada (tarjeta T0), incluido el criterio de catálogo de roles (§25, detallado el 22-oct). Aprobada por Paula el 22-oct-2026. Decisiones por recomendación en §16 y §25.11 |

## 1. Objetivo

Que Entrevista tenga su propia copia de cada Perfil Profesional activo, alimentada por el evento `perfil.profesional-actualizado` que ya publica Perfil, para crear la sesión sin llamar a Perfil (v5, «Llamadas síncronas»). El consumidor:

1. Declara su cola, su exchange de fallidos y su cola de fallidos con los mismos argumentos que Perfil.
2. Valida el mensaje contra el contrato v1 y manda a la cola de fallidos lo que lo incumple, sin reintentar.
3. Guarda la copia en `contexto_profesional_replica` de forma idempotente (Inbox por `message_id`) y descarta lo que sea más viejo que la copia (`actualizadoEn`).
4. Hace el `ack` después del commit; los fallos técnicos se reintentan 3 veces y luego van a la cola de fallidos.
5. Deja el puerto `ProfileReplicaRepository.findById` y la regla `ProfileReplica.isUsableForInterview()` para que CM-207 responda 404 o 409.

No calcula el `embedding` (D-RPL-01), no publica eventos y no expone endpoints.

## 2. Fuentes y vigencia

| Fuente | Qué aporta | Marca |
|---|---|---|
| Backlog v8.3, hoja «Backlog» fila 31 (HT-03), leída con `openpyxl` (texto completo en `analisis/contexto/CM-209.md`, sección final) | Terminado y contrato | Hecho |
| Backlog v8.3, HU-4.4, nota técnica | «lee el perfil en contexto_profesional_replica, que mantiene el consumidor … (HT-03; COMPLETED → COMPLETE)»; 404 con otro dueño; 409 ausente | Hecho |
| `cameia-perfil/docs/eventos/perfil-profesional-actualizado-v1.md` y `.schema.json` (`1201943`) | Destino, cuerpo, límites, propiedades AMQP, garantías («al menos una vez», «sin orden», «descartar lo anterior»), privacidad | Hecho (contrato publicado: fuente de nivel 1) |
| `cameia-perfil` `RabbitConfig`, `AccountEventsRabbitConfig`, `AccountEventsRetryConfig`, `AccountEventsErrorHandler`, `AccountEventsRecoverer`, `EventLogValues`, `ProcessedMessageInbox`, `application.yml` (`spring.rabbitmq.*`) | Patrón de consumidor ya probado en el entorno (CM-279) | Hecho |
| `cameia-entrevista/.github/workflows/despliegue-continuo.yml` (`bcc697d`) líneas 101 a 127 | Ya monta `SPRING_RABBITMQ_ADDRESSES` desde el secreto `rabbitmq-amqps-url` (CM-292); `--min-instances=0` | Hecho |
| `comunicaciones/enviadas/15102026_v1_…-para-devops.md` 3.4 | En el entorno, la cola `entrevista.perfil-profesional-actualizado` ya existe (la declara Perfil) con `entrevista.dlx` y recibió 3 mensajes de unos 620 bytes | Hecho |
| Decisión del 11 y 13-oct (opción B) | Perfil publica sin `embedding`; «lo calcula Entrevista» | Hecho; su ubicación se decide en D-RPL-01 |
| Jira (descripción, comentarios, padre, enlaces de CM-209) | No leído (conector caído) | Brecha B-1 |

## 3. Criterios de HT-03 y trazabilidad

HT-03 no tiene CA numerados. Se numeran aquí con el texto literal del backlog.

| CA | Texto literal (v8.3) | Requisitos | Pruebas (nombre exacto) | Verificación con Postman (API de administración de RabbitMQ) |
|---|---|---|---|---|
| HT03-C1 | «exchange perfil.events · routing key perfil.profesional-actualizado · cola entrevista.perfil-profesional-actualizado» | REQ-RPL-01 | `ProfileEventsRabbitConfigTest` (argumentos declarados de la cola, el exchange y el enlace) | `GET cola con sus argumentos`, `GET enlace perfil.events → cola` (complemento manual contra el broker local; la topología real la prueba `ProfileEventsTopologyIT`; D-RPL-04) |
| HT03-C2 | «Perfil declara la cola; Entrevistas la declara con los mismos argumentos (x-dead-letter-exchange = entrevista.dlx)» | REQ-RPL-01, REQ-RPL-02 | `ProfileEventsRabbitConfigTest` (mismos argumentos que Perfil), `ProfileEventsErrorHandlerTest`, `ProfileEventsDeadLetterIT` | `GET cola de fallidos` (complemento manual; el enrutamiento real a la cola de fallidos lo prueba `ProfileEventsDeadLetterIT`, D-RPL-04) |
| HT03-C3 | «payload usuarioId, contextoProfesional (debe incluir id, nombre, estado y fecha_archivado), rolesAspirados (cada rol con el UUID del catálogo de Perfil), actualizadoEn (sin embedding…)» | REQ-RPL-03 a REQ-RPL-06 | `ProfileUpdatedEventReaderTest` (una por campo y límite, §8), `ProfileUpdatedContractTest.example_shouldBeValid_whenReadFromSchema`, `…limits_shouldMatchSchema` | `POST publicar evento válido` |
| HT03-C4 | «Entrevistas descarta eventos con actualizadoEn anterior al de su réplica» | REQ-RPL-08 | `ProfileReplicaRepositoryAdapterTest.upsert_shouldIgnore_whenOlderThanCopy`, `…_shouldApply_whenSameInstant`, `ProfileReplicationIntegrationTest.replication_shouldKeepNewest_whenOlderArrivesLater` | — |
| HT03-C5 | «el consumo es idempotente» | REQ-RPL-07 | `ReplicateProfileServiceTest.apply_shouldIgnore_whenMessageAlreadyProcessed`, `ProfileReplicationIntegrationTest.replication_shouldApplyOnce_when20Redeliveries` | — |
| HT03-C6 | «hoy estado es siempre COMPLETED y fecha_archivado siempre nula» (y el contrato admite los otros 3 estados y una fecha) | REQ-RPL-09 | `ProfileReplicaTest.isUsableForInterview_shouldBeTrue_onlyWhenCompletedAndNotArchived` (parametrizada, 5 casos) | — |
| HT03-C7 (Terminado) | «con Perfil detenido, Entrevistas crea la sesión con un perfil propio en estado Activo (COMPLETED) leyendo su réplica; … 404 … 409» | REQ-RPL-10 (la parte de CM-209: la copia y su puerto) | `ProfileReplicaRepositoryAdapterTest.findById_shouldReturnCopy_whenReplicated`, `…_shouldReturnEmpty_whenNeverReplicated`. El 404 y el 409 son de CM-207 (sus pruebas) | — |

Requisitos sin CA directo, con su razón: REQ-RPL-11 (reintentos y cola de fallidos), REQ-RPL-12 (`ack` tras el commit) y REQ-RPL-13 (logs) salen del estándar §F «Eventos» y de FIA-01, FIA-05 y SEG-04; REQ-RPL-14 (dueño que cambia) de RT-03 y API1.

## 4. Requisitos funcionales (EARS)

### 4.1 Topología

- **REQ-RPL-01** Al arrancar, el sistema debe declarar, con estos atributos exactos (los de Perfil): exchange `perfil.events` tipo `topic`, durable, sin borrado automático; cola `entrevista.perfil-profesional-actualizado` durable con `x-dead-letter-exchange = entrevista.dlx` y `x-dead-letter-routing-key = entrevista.perfil-profesional-actualizado`; enlace de esa cola a `perfil.events` con la clave `perfil.profesional-actualizado`. Si Perfil ya los declaró, la declaración no falla.
- **REQ-RPL-02** Al arrancar, el sistema debe declarar el exchange de fallidos `entrevista.dlx` (tipo `direct`, durable), la cola de fallidos `entrevista.perfil-profesional-actualizado.dlq` (durable, sin argumentos) y su enlace con la clave `entrevista.perfil-profesional-actualizado`.

### 4.2 Lectura y validación del mensaje

- **REQ-RPL-03** Cuando llegue un mensaje, el sistema debe exigir: propiedad `message_id` presente y UUID canónico (36 caracteres, minúsculas o mayúsculas); propiedad `type` = `perfil.profesional-actualizado`; encabezado `x-event-version` = `1` (entero). Si alguna falla, debe rechazarlo sin reintentar hacia la cola de fallidos con la razón `MESSAGE_ID_INVALID`, `EVENT_TYPE_INVALID` o `EVENT_VERSION_UNSUPPORTED`.
- **REQ-RPL-04** Cuando el cuerpo no sea JSON legible, esté vacío o no tenga la forma del contrato (tipo equivocado en un campo), el sistema debe rechazarlo sin reintentar con la razón `PAYLOAD_INVALID_FORMAT`.
- **REQ-RPL-05** Cuando el cuerpo sea JSON pero incumpla una regla del contrato v1 (§8), el sistema debe rechazarlo sin reintentar con la razón `PAYLOAD_CONTRACT_VIOLATION` y registrar en `WARN`, una sola vez, la ruta del primer campo que falla (por ejemplo `contextoProfesional.nombre`), nunca su valor.
- **REQ-RPL-06** El sistema debe ignorar los campos del cuerpo que no conoce (el contrato permite agregar campos opcionales en la misma versión) y no debe guardarlos.

### 4.3 Aplicación de la copia

- **REQ-RPL-07** Cuando un mensaje válido llegue, el sistema debe, en una sola transacción: (1) si ya existe una copia del mismo `perfil_id` con otro `firebase_uid`, rechazarlo (REQ-RPL-14) sin escribir nada; (2) anotar el `message_id` en `evento_procesado` y, si ya estaba, terminar sin cambios (idempotencia); (3) insertar o actualizar la copia según REQ-RPL-08.
- **REQ-RPL-08** El sistema debe actualizar una copia existente solo si el `actualizadoEn` del mensaje es igual o posterior al guardado («descarta eventos con actualizadoEn anterior»). Un mensaje anterior se anota en `evento_procesado` y no cambia la copia.
- **REQ-RPL-09** El sistema debe guardar `estado` y `fecha_archivado` tal como llegan (los cuatro valores del contrato y la fecha o nula). `ProfileReplica.isUsableForInterview()` debe ser verdadero solo con `estado = COMPLETED` y `fecha_archivado` nula; CM-207 lo usa para el 409.
- **REQ-RPL-10** El sistema debe ofrecer `ProfileReplicaRepository.findById(UUID perfilId)` que devuelve la copia o vacío, sin llamar a Perfil.
- **REQ-RPL-14** Cuando un mensaje traiga un `perfil_id` ya copiado con otro `usuarioId`, el sistema debe rechazarlo sin reintentar con la razón `PROFILE_OWNER_MISMATCH` y conservar la copia y su dueño.

### 4.4 Fiabilidad y registro

- **REQ-RPL-11** Cuando falle algo técnico (base caída, error de base, fallo inesperado), el sistema debe reintentar el mensaje 2 veces más (3 intentos, esperas de 1 s y 2 s) y, agotados, mandarlo a la cola de fallidos registrando una línea en `ERROR` con la cola, el `message_id` saneado y la clase de la causa, con traza sin mensajes.
- **REQ-RPL-12** El sistema debe confirmar (`ack`) el mensaje solo después del commit de la transacción de REQ-RPL-07; si la transacción se revierte, el mensaje no se confirma.
- **REQ-RPL-13** Mientras procesa un mensaje, el sistema debe poner en el `MDC` la clave `requestId` con el `correlation_id` del mensaje si cumple `^[A-Za-z0-9._-]{1,64}$` (si no, el `message_id` saneado) y quitarla al terminar. Ningún registro debe contener nombre, resumen, experiencias, formaciones, habilidades ni roles; sí `perfilId`, `firebaseUid`, `messageId` y la razón.

## 5. Contrato de entrada (consumido, no se modifica)

El contrato es el de Perfil (`docs/eventos/perfil-profesional-actualizado-v1.md`, `1201943`). Entrevista copia el esquema a su repo para la prueba de contrato (T5) y documenta su lado en `docs/eventos/consumo-perfil-profesional-actualizado.md`.

| Elemento | Valor | Lo comprueba Entrevista |
|---|---|---|
| Exchange / clave / cola | `perfil.events` / `perfil.profesional-actualizado` / `entrevista.perfil-profesional-actualizado` | REQ-RPL-01 |
| `message_id` | UUID v4 en texto | REQ-RPL-03 |
| `type` | `perfil.profesional-actualizado` | REQ-RPL-03 |
| `headers.x-event-version` | `1` | REQ-RPL-03 |
| `correlation_id` | `X-Request-Id` de Perfil o el `message_id` | REQ-RPL-13 |
| `content_type` | `application/json` | REQ-RPL-04 (otro tipo = `PAYLOAD_INVALID_FORMAT`) |
| Cuerpo | §8 | REQ-RPL-05 |

## 6. Diseño

| Capa y paquete | Pieza | Qué hace |
|---|---|---|
| `domain.model` | `ReplicatedProfileStatus` (enum `PENDING`, `IN_PROGRESS`, `IN_REVIEW`, `COMPLETED`, mismo nombre que el contrato) | Estado de la copia |
| `domain.model` | `ProfileReplica` (`record`: `UUID profileId`, `FirebaseUid owner`, `String name`, `ReplicatedProfileStatus status`, `Instant archivedAt`, `String professionalContextJson`, `String aspiredRolesJson`, `Instant updatedAt`) | La copia; `isUsableForInterview()`; `MAX_NAME_LENGTH = 255` |
| `domain.model` | `ReplicationOutcome` (enum `APPLIED`, `STALE_IGNORED`, `DUPLICATE_IGNORED`, `OWNER_MISMATCH`) | Resultado de aplicar un mensaje |
| `domain.port` | `ProfileReplicaRepository` (`Optional<ProfileReplica> findById(UUID)`, `boolean upsertIfNotOlder(ProfileReplica, Instant replicatedAt)`) | Persistencia de la copia |
| `domain.port` | `ProcessedMessageInbox` (`boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt)`) | Inbox, igual que Perfil |
| `application.command` | `ReplicateProfileCommand` (`record`: `UUID messageId`, `ProfileReplica replica`) | Entrada del caso de uso |
| `application.service` | `ReplicateProfileService` (`@Service`, `@Transactional`) | REQ-RPL-07 y REQ-RPL-08; devuelve `ReplicationOutcome` |
| `infrastructure.persistence.entity` | `ProfileReplicaEntity`, `ProcessedMessageEntity` | Solo para `ddl-auto=validate`; las escrituras son consultas nativas |
| `infrastructure.persistence.repository` | `ProfileReplicaJpaRepository` (+ adaptador), `ProcessedMessageJpaRepository` (+ adaptador) | Upsert condicionado e `INSERT … ON CONFLICT DO NOTHING` |
| `infrastructure.messaging.config` | `ProfileEventsRabbitConfig` (topología, convertidor JSON), `ProfileEventsRetryConfig` (excluye el rechazo por contrato del reintento) | REQ-RPL-01, 02, 11 |
| `infrastructure.messaging.payload` | `ProfileUpdatedPayloadV1` y sus `record` anidados con Bean Validation; `@TextLength` + `TextLengthValidator` (cuenta puntos de código) | Contrato v1 del lado del consumidor |
| `infrastructure.messaging.consumer` | `ProfileUpdatedListener`, `ProfileUpdatedEventReader` (valida y arma el comando), `ProfileEventsErrorHandler`, `ProfileEventsRecoverer`, `EventLogValues`, `ContractViolation` (excepción de paquete con la razón) | REQ-RPL-03 a 06, 11, 13, 14 |

**Por qué la validación vive en la infraestructura:** el contrato es un formato de cable (nombres en español, fechas en texto); el dominio recibe un `ProfileReplica` ya válido. La regla del dominio es solo `isUsableForInterview`. Así ningún `domain.exception` nuevo choca con la regla de CM-311 «toda excepción de dominio hereda de `BusinessException` con un `ErrorCode` HTTP» (un rechazo de mensaje no es una respuesta HTTP).

## 7. Base de datos (R4): `V2__replica_perfil_profesional.sql`

Esquema `microentrevista` (P16 de CM-311). `V2` queda reservada a CM-209 (§18).

### 7.1 `contexto_profesional_replica`

| Columna | Tipo | Nulo | Regla | Origen |
|---|---|---|---|---|
| `perfil_id` | `uuid` | no | `pk_contexto_profesional_replica` | `contextoProfesional.id` |
| `firebase_uid` | `varchar(128)` | no | `ck_contexto_profesional_replica_firebase_uid` (`char_length(btrim(firebase_uid)) > 0`) | `usuarioId` (1 a 128) |
| `nombre` | `varchar(255)` | no | `ck_contexto_profesional_replica_nombre` (`char_length(btrim(nombre)) > 0`) | `contextoProfesional.nombre` (1 a 255) |
| `estado` | `varchar(20)` | no | `ck_contexto_profesional_replica_estado` IN (`PENDING`, `IN_PROGRESS`, `IN_REVIEW`, `COMPLETED`) | `contextoProfesional.estado` |
| `fecha_archivado` | `timestamptz` | sí | — | `contextoProfesional.fecha_archivado` |
| `contexto_profesional` | `jsonb` | no | `ck_contexto_profesional_replica_contexto` (`jsonb_typeof = 'object'`) | `contextoProfesional` completo, serializado de nuevo (solo campos conocidos, REQ-RPL-06) |
| `roles_aspirados` | `jsonb` | no | `ck_contexto_profesional_replica_roles` (`jsonb_typeof = 'array' AND jsonb_array_length BETWEEN 1 AND 5`) | `rolesAspirados` |
| `actualizado_en` | `timestamptz` | no | — | `actualizadoEn` (milisegundos) |
| `fecha_replicacion` | `timestamptz` | no | — | Reloj del servicio al aplicar |

Sin clave foránea (MAN-03: `perfil_id` es de Perfil). Sin índice extra: la única consulta es por `perfil_id` (clave primaria); el borrado por usuario (cuenta eliminada) no existe todavía (§21, H-4).

### 7.2 `evento_procesado`

| Columna | Tipo | Nulo | Regla |
|---|---|---|---|
| `message_id` | `uuid` | no | `pk_evento_procesado` |
| `tipo` | `varchar(40)` | no | `ck_evento_procesado_tipo` IN (`perfil.profesional-actualizado`) |
| `procesado_en` | `timestamptz` | no | — |

### 7.3 Comentarios

`COMMENT ON TABLE` de las dos tablas y `COMMENT ON COLUMN` de `estado`, `contexto_profesional`, `roles_aspirados` y `actualizado_en`, en español sin tildes, como `V1`:

| Objeto | Texto |
|---|---|
| `contexto_profesional_replica` | Copia del Perfil Profesional que mantiene el consumidor de perfil.profesional-actualizado. Permite crear la sesion sin llamar a Perfil. Datos personales: solo lo que la entrevista usa. |
| `contexto_profesional_replica.estado` | Valores: PENDING, IN_PROGRESS, IN_REVIEW, COMPLETED. Solo COMPLETED sin fecha_archivado sirve para una entrevista. |
| `contexto_profesional_replica.contexto_profesional` | Bloque contextoProfesional del evento v1 (resumen, experiencias, formaciones, habilidades), sin campos desconocidos. |
| `contexto_profesional_replica.roles_aspirados` | Lista rolesAspirados del evento v1 (codigo UUID del catalogo de Perfil y nombre). |
| `contexto_profesional_replica.actualizado_en` | actualizadoEn del ultimo evento aplicado; un evento anterior se descarta. |
| `evento_procesado` | Inbox: identificadores de mensajes ya aplicados, para que una reentrega no tenga efecto. Sin datos personales. |

## 8. Matriz de validación por campo del mensaje (R2)

`ContractViolation` lleva la razón `PAYLOAD_CONTRACT_VIOLATION` y la ruta del campo. «Texto» cuenta **puntos de código** (`String.codePointCount`), igual que `char_length` de PostgreSQL y que la validación de Perfil; no se normaliza ni se recorta (Perfil ya publica el texto normalizado). Todas las filas se prueban en `ProfileUpdatedEventReaderTest` con el ejemplo del contrato modificado en un solo campo.

| Campo (ruta) | Regla | Caso inválido literal | Razón | Prueba |
|---|---|---|---|---|
| `message_id` (propiedad) | presente, UUID canónico | ausente; `"abc"`; `"1-1-1-1-1"` | `MESSAGE_ID_INVALID` | `read_shouldReject_whenMessageIdInvalid` (3) |
| `type` (propiedad) | `perfil.profesional-actualizado` | ausente; `"cuenta.creada"` | `EVENT_TYPE_INVALID` | `read_shouldReject_whenTypeInvalid` (2) |
| `x-event-version` (encabezado) | entero `1` | ausente; `2`; `"1"` (texto) | `EVENT_VERSION_UNSUPPORTED` | `read_shouldReject_whenVersionNotOne` (3) |
| cuerpo | JSON con la forma del contrato | `{"usuarioId":` (roto); vacío; `"[]"`; `contextoProfesional` como texto; `content_type: text/plain` | `PAYLOAD_INVALID_FORMAT` | `ProfileEventsErrorHandlerTest.handleError_shouldReject_whenConversionFails` (5) |
| `usuarioId` | texto 1 a 128, sin caracteres de control | `null`; `""`; `"   "`; `"u".repeat(129)`; `"uid\u0007"` | `PAYLOAD_CONTRACT_VIOLATION` (`usuarioId`) | `read_shouldReject_whenUserIdInvalid` (5); acepta `"u".repeat(128)` |
| `contextoProfesional` | objeto presente | `null` | ídem (`contextoProfesional`) | `read_shouldReject_whenContextMissing` |
| `contextoProfesional.id` | UUID canónico | `null`; `"no-uuid"` | ídem | `read_shouldReject_whenProfileIdInvalid` (2) |
| `contextoProfesional.nombre` | texto 1 a 255 | `null`; `""`; `"   "`; 256 × `"é"` | ídem | `read_shouldReject_whenNameInvalid` (4); acepta 255 × `"😀"` (255 puntos de código, 510 unidades UTF-16) |
| `contextoProfesional.estado` | `PENDING`, `IN_PROGRESS`, `IN_REVIEW`, `COMPLETED` | `null`; `"completed"`; `"ARCHIVED"`; `""` | ídem | `read_shouldReject_whenStatusInvalid` (4) |
| `contextoProfesional.fecha_archivado` | `null` o instante ISO-8601 | `"ayer"`; `"2026-02-31T00:00:00Z"` | ídem | `read_shouldReject_whenArchivedAtInvalid` (2); acepta `"2026-10-15T12:00:00.123Z"` |
| `contextoProfesional.resumen` | texto 1 a 2000 | `null`; `""`; 2001 caracteres | ídem | `read_shouldReject_whenSummaryInvalid` (3); acepta 2000 |
| `contextoProfesional.experiencias` | lista presente, 0 a 4 | `null`; 5 elementos | ídem | `read_shouldReject_whenExperiencesInvalid` (2); acepta `[]` y 4 |
| ↳ `empresa`, `cargo` | texto 1 a 100 | `""`; 101 | ídem | `read_shouldReject_whenExperienceTextInvalid` (4) |
| ↳ `descripcion` | `null` o 1 a 500 | `""`; 501 | ídem | ídem (2) |
| ↳ `inicio`, `fin` | `null` o `AAAA-MM` | `"2022-13"`; `"2022-1"` | ídem | `read_shouldReject_whenYearMonthInvalid` (2) |
| ↳ `estadoEmpleo` | `CURRENT`, `UNKNOWN_END`, `ENDED` | `"current"` | ídem | `read_shouldReject_whenEmploymentStatusInvalid` |
| `contextoProfesional.formaciones` | 1 a 5 | `[]`; 6 | ídem | `read_shouldReject_whenEducationsInvalid` (2) |
| ↳ `institucion`, `titulo` | 1 a 150 | 151 | ídem | ídem |
| ↳ `nivel` | `TECHNICAL`, `UNDERGRADUATE`, `POSTGRADUATE` | `"PHD"` | ídem | ídem |
| ↳ `inicio` | `AAAA-MM` obligatorio | `null` | ídem | ídem |
| ↳ `enCurso` | booleano presente | `null` | ídem | ídem |
| `contextoProfesional.habilidades` | 1 a 20 | `[]`; 21 | ídem | `read_shouldReject_whenSkillsInvalid` (2) |
| ↳ `nombre` | 1 a 60 | 61 | ídem | ídem |
| ↳ `nivel` | `BASIC`, `INTERMEDIATE`, `ADVANCED` | `"EXPERT"` | ídem | ídem |
| `rolesAspirados` | 1 a 5 | `null`; `[]`; 6 | ídem | `read_shouldReject_whenRolesInvalid` (3) |
| ↳ `codigo` | UUID | `"backend"` | ídem | ídem |
| ↳ `nombre` | 1 a 255 | `""` | ídem | ídem |
| `actualizadoEn` | `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$` y fecha real | `null`; `"2026-10-15T12:00:00Z"` (sin milisegundos); `"2026-02-30T12:00:00.000Z"` | ídem | `read_shouldReject_whenUpdatedAtInvalid` (3) |
| campo desconocido | se ignora | `"embedding":[0.1]`, `"plan":"PREMIUM"` en la raíz y dentro de `contextoProfesional` | aceptado; no aparece en `contexto_profesional` | `read_shouldIgnoreUnknownFields_whenPresent` |
| texto hostil | se guarda literal | `nombre = "<script>alert(1)</script>"`, comillas, `%`, `_`, emoji | aceptado tal cual | `read_shouldKeepTextLiteral_whenHostile` |

Orden de comprobación (determinista, para que el `WARN` sea estable): propiedades AMQP (`message_id`, `type`, versión) → cuerpo convertible → Bean Validation (se registra la ruta de la primera violación en orden alfabético de ruta) → fechas (`fecha_archivado`, `actualizadoEn`).

## 9. Casos borde (R5)

| Grupo | Caso | Datos | Resultado | Prueba |
|---|---|---|---|---|
| Presencia | cada campo obligatorio ausente o nulo | §8 | `PAYLOAD_CONTRACT_VIOLATION` → cola de fallidos | §8 |
| Texto | 255/256 en nombre; emoji y combinantes (`"é"` cuenta 2) | §8 | 255 acepta, 256 rechaza | §8 |
| Números | tamaños de listas 0, 1, máximo, máximo + 1 | §8 | según la tabla | §8 |
| Fechas | `actualizadoEn` sin milisegundos, 30 de febrero; `fecha_archivado` ISO sin milisegundos (`2026-10-15T12:00:00Z`) se acepta | §8 | — | §8 |
| Fechas | mismo instante que la copia | `actualizadoEn` igual | se aplica (no es «anterior») | `…upsert_shouldApply_whenSameInstant` |
| Fechas | 1 ms antes que la copia | `…12:00:00.122Z` frente a `.123Z` | se ignora y se anota en el Inbox | `…upsert_shouldIgnore_whenOlderThanCopy` |
| Enumerados | estado en minúsculas, desconocido, vacío | §8 | rechazo | §8 |
| Duplicados | mismo `message_id` 20 veces | ejemplo del contrato | 1 fila en la copia, 1 en `evento_procesado` | `…when20Redeliveries` |
| Duplicados | dos mensajes distintos con el mismo contenido y `actualizadoEn` creciente | — | ambos se aplican; la copia queda igual | `ReplicateProfileServiceTest.apply_shouldApplyBoth_whenSameContentNewerInstant` |
| Estado | `estado = IN_PROGRESS` (reactivación futura) | — | se guarda; `isUsableForInterview` falso | `ProfileReplicaTest` |
| Estado | `fecha_archivado` presente con `COMPLETED` | — | se guarda; `isUsableForInterview` falso | ídem |
| Propiedad | `perfil_id` existente con otro `usuarioId` | copia de `uid-a`, mensaje con `uid-b` | `PROFILE_OWNER_MISMATCH`; copia intacta; nada en el Inbox | `ReplicateProfileServiceTest.apply_shouldRejectOwnerChange_withoutWriting`, `ProfileReplicationIntegrationTest.replication_shouldReject_whenOwnerChanges` |
| Colecciones | `experiencias = []` | — | aceptado | §8 |
| Concurrencia | dos instancias con el mismo `message_id` a la vez | dos hilos, `CountDownLatch` | 1 aplicado, 1 `DUPLICATE_IGNORED`; 1 fila | `ProcessedMessageInboxAdapterTest.registerIfAbsent_shouldLetOnlyOneWin_whenConcurrent` |
| Concurrencia | dos mensajes del mismo perfil fuera de orden en dos hilos | `…:00.100Z` y `…:00.200Z` | queda el de `.200Z` | `ProfileReplicaRepositoryAdapterTest.upsert_shouldKeepNewest_whenConcurrent` |
| Falla parcial | la base falla después de anotar el Inbox y antes del upsert | adaptador que lanza `DataAccessResourceFailureException` en el upsert | se revierte el Inbox; el mensaje se reintenta; al volver, se aplica una vez | `ProfileReplicationIntegrationTest.replication_shouldRollBackAndApplyOnce_whenDatabaseFailsOnce` |
| Falla parcial | la base falla los 3 intentos | — | cola de fallidos; 1 línea `ERROR` | `ProfileEventsRecovererTest` (tras el último intento registra un `ERROR` y rechaza sin reencolar); el envío real a la cola de fallidos tras los 3 intentos lo prueba `ProfileEventsDeadLetterIT.deadLetter_shouldRouteToDlq_whenDatabaseFailsThreeTimes` con RabbitMQ real (D-RPL-04) |
| Dependencias | broker caído al arrancar | — | el servicio arranca y atiende HTTP; el consumidor reintenta la conexión (comportamiento del contenedor de Spring AMQP) | `ProfileEventsContextTest.context_shouldStart_whenBrokerUnavailable` |
| Dependencias | Perfil detenido | — | la copia sigue disponible (no se llama a Perfil) | por diseño; lo prueba CM-207 (HT03-C7) |
| Carga | cuerpo de 1 MiB | `resumen` con 1 048 576 caracteres | `PAYLOAD_CONTRACT_VIOLATION` (supera 2000) | `read_shouldReject_whenBodyHuge` |
| Carga | `content_type` distinto | `text/plain` | `PAYLOAD_INVALID_FORMAT` | §8 |

## 10. Errores y rechazos (R3)

No hay respuestas HTTP: no se agrega nada a `ErrorCode` ni a `docs/errores.md`. El equivalente de «código estable» en mensajería es la **razón** del rechazo, que va en el log (`WARN`, una vez) y en la documentación del consumidor:

| Razón | Cuándo | Reintenta | Destino | Log |
|---|---|---|---|---|
| `MESSAGE_ID_INVALID` | REQ-RPL-03 | no | cola de fallidos | `WARN` con cola, `messageId` saneado y razón |
| `EVENT_TYPE_INVALID` | REQ-RPL-03 | no | ídem | ídem |
| `EVENT_VERSION_UNSUPPORTED` | REQ-RPL-03 | no | ídem | ídem |
| `PAYLOAD_INVALID_FORMAT` | REQ-RPL-04 | no | ídem | ídem |
| `PAYLOAD_CONTRACT_VIOLATION` | REQ-RPL-05 | no | ídem | ídem + ruta del campo |
| `PROFILE_OWNER_MISMATCH` | REQ-RPL-14 | no | ídem | ídem + `perfilId` |
| (fallo técnico) | REQ-RPL-11 | 3 intentos | cola de fallidos | `WARN` sin traza por intento; `ERROR` con traza al agotarse |

Ningún fallo deja un `catch` vacío ni queda «tragado»: todo termina en confirmación, reintento o cola de fallidos con su línea de log. Cómo reprocesar la cola de fallidos (mover con la pala de RabbitMQ a la cola principal una vez corregida la causa) queda en `docs/eventos/consumo-perfil-profesional-actualizado.md`.

**Rutas al genérico revisadas (regla de errores visibles).** Este flujo no tiene respuesta HTTP: el «error visible» es la razón del rechazo (log y cola de fallidos), nunca un mensaje perdido.

| Ruta posible | Tratamiento | Prueba |
|---|---|---|
| Mensaje con cabecera, tipo, versión o cuerpo inválido (copia del perfil y catálogo de roles) | Razón propia de la tabla anterior, sin reintentos, a la cola de fallidos | pruebas de §9 |
| Dos mensajes a la vez para el mismo perfil o el mismo rol (`pk_`, `uq_`) | El `upsert` con la condición de fecha lo resuelve sin error; el más antiguo no pisa al más nuevo | E-01 a E-12 de §25.6 |
| Texto de más de 255 puntos de código (`varchar(255)` igual al límite validado) o más de 200 roles | `PAYLOAD_CONTRACT_VIOLATION` antes de tocar la base | §25.3 |
| Rol repetido dentro de un mismo mensaje | `CATALOG_ROLE_DUPLICATED` | §25.5 |
| Base caída o sin conexión | Fallo técnico: 3 intentos y cola de fallidos con `ERROR` y traza al agotarse | REQ-RPL-11 |
| Cola de destino inexistente en Perfil (publicador con `mandatory`) | El mensaje queda pendiente en la bandeja de salida y se reintenta; riesgo anotado en el plan | plan, riesgos |
| Cualquier otra excepción | Mismo camino que el fallo técnico: ningún `catch` vacío | pruebas del fallo técnico de §9 |

## 11. Seguridad (R6)

### 11.1 ASVS 5.0.0 nivel 1

| ID | Estado en la matriz | Qué hace CM-209 | Prueba |
|---|---|---|---|
| 1.2.4 | CUMPLE | Upsert e Inbox con consultas nativas **parametrizadas** (`:param`); ningún texto del mensaje se concatena | `ProfileReplicaRepositoryAdapterTest.upsert_shouldStoreHostileTextLiterally` |
| 1.3.2 | CUMPLE | El convertidor JSON ignora `__TypeId__` (`setAlwaysConvertToInferredType(true)`): el mensaje no elige la clase | `ProfileEventsRabbitConfigTest.converter_shouldIgnoreTypeIdHeader` |
| 2.1.1 | CUMPLE | §8 documenta cada regla | — |
| 2.2.1 / 2.2.2 | CUMPLE | Validación del mensaje en el servidor contra el contrato | §8 |
| 8.2.2 | CUMPLE | El dueño de la copia no cambia por un mensaje (REQ-RPL-14) | `…_whenOwnerChanges` |
| 15.3.1 | CUMPLE | Solo se guardan los campos del contrato que se conocen | `read_shouldIgnoreUnknownFields_whenPresent` |

Hallazgo para la matriz (DevOps, vía Vela): no hay fila sobre la confianza en mensajes entre servicios (V1/V2 cubren HTTP). No bloquea.

### 11.2 OWASP API Security Top 10 (aplicado al canal de mensajes)

| Riesgo | Aplica | Cómo |
|---|---|---|
| API1 | Sí | Un mensaje no puede cambiar el dueño de una copia |
| API2 | Parcial | La autenticidad del productor la da el broker (credenciales en `rabbitmq-amqps-url`, AMQPS); Entrevista no firma mensajes (igual que Perfil) |
| API3 | Sí | Campos desconocidos ignorados y no guardados |
| API4 | Sí | Límites del contrato; listas acotadas; `prefetch = 10`, `concurrency = 1` |
| API5, API6, API7, API9 | No | Sin funciones expuestas, sin flujos de usuario, sin URL de salida, sin rutas nuevas |
| API8 | Sí | Credenciales del broker solo por secreto; sin valores por defecto en `production` |
| API10 | Sí | Se valida todo lo que llega de Perfil antes de usarlo (§8) |

## 12. Atributos de calidad (R13)

HT-03 es un habilitador sin épica; se declaran los que toca.

| Atributo | Métrica | Límite | Cómo lo cumple | Prueba o comando | Resultado real |
|---|---|---|---|---|---|
| AC-0004 fiabilidad | FIA-01 | Conserva el último estado confirmado | Transacción única; `ack` tras el commit; reintento con la base caída | `…_whenDatabaseFailsOnce`, `…_whenDatabaseKeepsFailing` | PENDIENTE |
| AC-0004 fiabilidad | FIA-02 | 0 efectos extra en 20 repeticiones | Inbox por `message_id` | `…when20Redeliveries` | PENDIENTE |
| AC-0004 fiabilidad | FIA-05 | Recuperación de mensajería sin duplicados | Cola de fallidos + reproceso idempotente | `ProfileEventsErrorHandlerTest` + `ProfileEventsDeadLetterIT` (RabbitMQ real) + documento de reproceso | PENDIENTE |
| AC-0003 seguridad | SEG-01 | ASVS aplicable 100 % | §11.1 | ídem | PENDIENTE |
| AC-0003 seguridad | SEG-04 | 0 datos personales en logs | Solo identificadores y razones | `ProfileUpdatedListenerTest.logs_shouldNotContainPersonalData` (captura con `ListAppender`: el log no contiene `Analista de datos`, `Python`, `ACME` ni el resumen) | PENDIENTE |
| Transversal | MAN-01 | ≥ 90 % líneas y ramas | §14 | `jacoco.csv` | PENDIENTE |
| Transversal | MAN-03 | 0 FK entre servicios | `perfil_id` sin FK | `ReplicaSchemaMigrationTest.replica_shouldHaveNoForeignKeys` | PENDIENTE |
| Transversal | IOP-01 | Eventos con esquema versionado | Copia del JSON Schema v1 y prueba de contrato | `ProfileUpdatedContractTest` | PENDIENTE |
| Transversal | IOP-02 | Integración en un adaptador | Todo lo de RabbitMQ en `infrastructure.messaging` | `LayeredArchitectureTest` | PENDIENTE |
| Transversal | IOP-04 | Consumidor con Inbox | `evento_procesado` | `ProcessedMessageInboxAdapterTest` | PENDIENTE |
| AC-0002 desempeño | DES-02 (indirecto) | La copia no debe retrasar la creación de la sesión | Consulta por clave primaria | `findById` sobre `pk_` | PENDIENTE |
| Restricción | REST-0003 | Datos personales mínimos | Solo lo que el contrato trae y la entrevista usa; sin `embedding`; sin campos desconocidos | §7 | PENDIENTE |

## 13. Postman y Newman (R11)

No hay endpoint HTTP. Se verifica contra la API de administración del broker local (patrón ya usado en la colección de Perfil, carpeta del evento). En `postman/cameia-entrevista.postman_collection.json`, carpeta «Réplica del perfil (RabbitMQ)», con `{{rabbitManagementUrl}}` = `http://localhost:15673` y usuario `guest`/`guest` del broker local de desarrollo (no es un secreto):

| Petición | Espera |
|---|---|
| `GET cola con sus argumentos` (`/api/queues/%2F/entrevista.perfil-profesional-actualizado`) | 200; `durable = true`; argumentos `entrevista.dlx` y clave = nombre de la cola; `consumers >= 1` |
| `GET cola de fallidos` (`…/entrevista.perfil-profesional-actualizado.dlq`) | 200; `durable = true` |
| `GET enlace perfil.events → cola` (`/api/exchanges/%2F/perfil.events/bindings/source`) | un enlace a la cola con la clave `perfil.profesional-actualizado` |
| `POST publicar evento válido` (`/api/exchanges/%2F/perfil.events/publish`, ejemplo del contrato con `message_id` `{{$guid}}`, `type`, `x-event-version` 1) | 200; `routed = true` |
| `GET cola vacía tras consumir` (con espera de 2 s en el script previo) | `messages = 0` |
| `POST publicar evento con estado inválido` (`"estado":"ARCHIVED"`) | `routed = true` |
| `GET cola de fallidos con un mensaje` (2 s después) | `messages >= 1` |
| `GET cola del catálogo de roles` (`…/entrevista.catalogo-roles-actualizado`, §25.9) | 200; `durable = true`; argumentos `entrevista.dlx` y clave = nombre de la cola |
| `GET cola de fallidos del catálogo` (`…/entrevista.catalogo-roles-actualizado.dlq`) | 200; `durable = true`; `messages = 0` |

Comando: `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --folder "Réplica del perfil (RabbitMQ)" --reporters cli,junit` → 9 peticiones, 0 fallos. Requiere `docker compose --profile mensajeria up -d` y el jar arrancado (T6). La comprobación de la fila en la base la hacen las pruebas automáticas.

## 14. Pruebas, cobertura y comandos (R9, R10)

| Capa | Clase | Herramienta |
|---|---|---|
| Dominio | `ProfileReplicaTest` | JUnit 5 + AssertJ |
| Aplicación | `ReplicateProfileServiceTest` | fakes en memoria del puerto y del Inbox |
| Mensajería (unidad) | `ProfileUpdatedEventReaderTest`, `ProfileUpdatedListenerTest`, `ProfileEventsErrorHandlerTest`, `ProfileEventsRecovererTest`, `EventLogValuesTest`, `TextLengthValidatorTest`, `ProfileEventsRabbitConfigTest` | JUnit 5, Mockito, `ListAppender` |
| Contrato | `ProfileUpdatedContractTest` | Jackson sobre `docs/eventos/perfil-profesional-actualizado-v1.schema.json` |
| Persistencia | `ReplicaSchemaMigrationTest`, `ProfileReplicaRepositoryAdapterTest`, `ProcessedMessageInboxAdapterTest` | `@SpringBootTest` + Testcontainers PostgreSQL |
| Integración | `ProfileReplicationIntegrationTest`, `ProfileEventsContextTest` | `@SpringBootTest` + Testcontainers PostgreSQL; el mensaje se entrega al listener directamente |
| Integración con broker real | `ProfileEventsTopologyIT`, `ProfileEventsDeadLetterIT`, `ProfileEventsAckIT`, `ProfileReplicationBrokerIT` | `@SpringBootTest` + PostgreSQL y RabbitMQ reales (`GenericContainer` de Testcontainers con `rabbitmq:3.13-management-alpine`, como `MessagingIntegrationSupport` de Perfil; sin artefacto nuevo en el `pom.xml`) (D-RPL-04, opción A) |
| Arquitectura | `LayeredArchitectureTest` (de CM-311) | ArchUnit |

Clases a medir (≥ 90 % líneas y ramas): todas las de §6. Comandos:

```
docker version
./mvnw.cmd -B -ntp test -Dtest="ProfileReplicaTest,ReplicateProfileServiceTest,ProfileUpdatedEventReaderTest,ProfileUpdatedListenerTest,ProfileEventsErrorHandlerTest,ProfileEventsRecovererTest,EventLogValuesTest,TextLengthValidatorTest,ProfileEventsRabbitConfigTest,ProfileUpdatedContractTest"
./mvnw.cmd -B -ntp test -Dtest="ReplicaSchemaMigrationTest,ProfileReplicaRepositoryAdapterTest,ProcessedMessageInboxAdapterTest,ProfileReplicationIntegrationTest,ProfileEventsContextTest,LayeredArchitectureTest"
./mvnw.cmd -B -ntp clean verify
```

## 15. Configuración

| Propiedad | Valor | Por qué |
|---|---|---|
| `spring.rabbitmq.addresses` | `${SPRING_RABBITMQ_ADDRESSES}` (variable que Spring lee directo; ya la monta el despliegue) | Sin valor en el código |
| `spring.rabbitmq.connection-timeout` | `2s` | Igual que Perfil |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | `auto` | `ack` al volver el consumidor, después del commit |
| `…default-requeue-rejected` | `false` | Un fallido nunca vuelve a su cola |
| `…prefetch` / `…concurrency` | `10` / `1` | Igual que Perfil |
| `…retry.enabled`, `max-retries`, `initial-interval`, `multiplier`, `max-interval` | `true`, `2`, `1s`, `2`, `2s` | 3 intentos (REQ-RPL-11), igual que Perfil |
| `…auto-startup` | `true` en el código; las pruebas sin broker lo apagan por la configuración de Surefire (T1) | Que las demás pruebas no esperen un broker |
| `.env.example` | `SPRING_RABBITMQ_ADDRESSES=amqp://guest:guest@localhost:5673` con comentario (broker local de desarrollo, no es secreto) | R8 |
| `docker-compose.yml` | servicio `entrevista-rabbitmq` (`rabbitmq:4-management-alpine`, perfil de compose `mensajeria`, puertos `127.0.0.1:5673:5672` y `127.0.0.1:15673:15672`, contenedor `cameia-entrevista-rabbitmq`) | Postman local; nombre distinto del de Cuentas y Perfil (aviso de Frontend del 20-oct sobre el alias `rabbitmq`) |

## 16. Decisiones

| # | Decisión | Opciones | Por qué | Quién |
|---|---|---|---|---|
| D-RPL-01 | CM-209 **no** calcula el `embedding` | A) no lo calcula; la historia que lo use (planificación, CM-212, con el contrato del modelo de CM-200) agrega su columna y su cálculo; B) calcularlo al consumir | HT-03 no lo pide en su contrato ni en su «Terminado»; el puerto del modelo de CM-312 solo devuelve texto (no embeddings); una llamada al modelo dentro del consumo mete costo y una dependencia externa en la transacción (R7). B obliga a decidir modelo, dimensión y columna sin que nadie lo use todavía | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula).** Cambia la nota de memoria «CM-209 necesita el acceso al proveedor del modelo». Si elige B: tarjeta nueva con columna `vector`/`jsonb`, llamada fuera de la transacción y su prueba de fallo |
| D-RPL-02 | `estado` se guarda tal como llega (`COMPLETED`), no traducido a `COMPLETE` | A) tal cual; B) traducir `COMPLETED → COMPLETE` como sugiere la nota de HU-4.4 | La nota no define otro valor para los demás estados; traducir inventaría un vocabulario que nadie más usa. «COMPLETE en la réplica» se cumple con `isUsableForInterview()` | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula).** Aviso al PO en una línea (corrección de redacción del libro) |
| D-RPL-03 | El perfil se guarda en dos `jsonb` (`contexto_profesional`, `roles_aspirados`) y no en tablas hijas | A) `jsonb`; B) tablas de experiencia, formación, habilidad y rol | Entrevista solo lo lee entero para copiarlo a `contexto_perfil_snapshot` y armar el prompt; nunca filtra por esos datos. B son 4 tablas y 4 mapeos sin consulta que los use | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-RPL-04 | Pruebas **con PostgreSQL y RabbitMQ reales** (cola, exchange, cola de fallidos, reintentos y `ack` tras el commit), además de las unitarias | A) RabbitMQ real en las pruebas; B) solo PostgreSQL real y pruebas unitarias, sin broker | Elegida A por Paula (22-oct, segunda respuesta: «la que pruebe todo»; antes había elegido B). Perfil ya prueba así sus consumidores sin dependencia nueva: un `GenericContainer` con la imagen `rabbitmq:3.13-management-alpine` arrancado una sola vez para todas las clases (`MessagingIntegrationSupport`); Entrevista usa el mismo patrón, porque Testcontainers ya está en el `pom.xml` por PostgreSQL. Costo: de 3 a 4 h más y unos 15 a 20 s más en `clean verify` (arranque del contenedor, una vez). La verificación manual con Postman queda como complemento, no como prueba | **DECIDIDA POR PAULA (22-oct, opción A)** |
| D-RPL-05 | Un mensaje que cambia el dueño de una copia se rechaza | A) rechazar; B) aplicar | El dueño de un perfil no cambia en Perfil; aceptar daría a un mensaje erróneo el poder de pasar un perfil a otra persona (API1) | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-RPL-06 | `actualizadoEn` igual al guardado se aplica | A) aplicar (`>=`); B) ignorar (`>`) | El backlog dice descartar lo «anterior»; con igual instante el contrato garantiza el mismo contenido completo | Inferencia de Backend con el texto literal |
| D-RPL-07 | Entrevista declara su propia topología al arrancar (opción A de CM-293) | A) cada servicio declara; B) Terraform | Es lo que hace Perfil y lo que ya funciona en el entorno; CM-293 está finalizada sin topología en `cameia-infra` | Hecho (inferido del entorno real) |

## 17. Fuera de alcance

El `embedding` (D-RPL-01); el 404 y el 409 al crear la sesión (CM-207); borrar la copia cuando se elimine la cuenta (HU-1.9, fuera del MVP; §21 H-4); publicar eventos desde Entrevista; reprocesar automáticamente la cola de fallidos.

## 18. Integración con la ola en `cameia-entrevista`

| Recurso | CM-311 | CM-204 | CM-209 | CM-207 | CM-216 |
|---|---|---|---|---|---|
| Migración | `V1` | — | **`V2__replica_perfil_profesional.sql`** y **`V3__catalogo_roles_profesionales.sql`** (§25.4) | `V4` | `V5` |
| `ErrorCode` / `docs/errores.md` | crea | columna «Endpoints» | sin cambios | agrega | agrega |
| `pom.xml` | validación, Flyway, Testcontainers, ArchUnit | — | `spring-boot-starter-amqp`; `systemPropertyVariables` de Surefire (las pruebas con RabbitMQ real usan `GenericContainer`, ya disponible: sin artefacto nuevo) | — | — |
| `docker-compose.yml`, `.env.example` | comentarios | — | servicio `entrevista-rabbitmq`, `SPRING_RABBITMQ_ADDRESSES` | — | — |
| Postman | crea | carpeta | carpeta «Réplica del perfil (RabbitMQ)» y variable `rabbitManagementUrl` en el entorno | carpeta | carpeta |
| `docs/eventos/` | — | — | **crea** (`consumo-perfil-profesional-actualizado.md` y la copia del esquema) | — | — |
| ADR | `0006` | — | `0007-replica-del-perfil-por-evento.md` | siguiente | siguiente |
| Bitácora de IA | `03` | `04` | `05` | `06` | `07` |

Rama: `CM-209-replica-perfil-profesional` desde `origin/develop` con CM-311 fusionada; no depende de CM-204 (pueden ir en paralelo; si CM-204 se fusiona antes, se rebasa sin conflictos: archivos distintos salvo `docs/bitacora-ia/README.md` y la colección de Postman, que se resuelven agregando). Si CM-311 no está fusionada: stack sobre su rama. Diff estimado: ≈ 2 100 líneas (≈ 700 de código, ≈ 1 200 de pruebas, ≈ 200 de documentación y configuración).

## 19. Línea base

Antes de la tarjeta T1: con Docker encendido, `./mvnw.cmd -B -ntp clean verify` sobre la base de la rama → `BUILD SUCCESS` sin clases de Testcontainers omitidas (es la línea base que deja CM-311).

## 20. Revalidar cuando CM-311 y CM-312 estén implementadas

| Lo que se toma prestado | Dónde debe existir | Cómo se confirma |
|---|---|---|
| Esquema `microentrevista`; Flyway con `spring.flyway.schemas`; `V1__esquema_inicial_entrevista.sql` | `src/main/resources/` | `git ls-tree`; arranque de `ReplicaSchemaMigrationTest` |
| `FirebaseUid` con `MAX_LENGTH = 128` y `required(String)` | `domain/model/FirebaseUid.java` | compila `ProfileReplica` |
| Arranque de pruebas: `@Testcontainers(disabledWithoutDocker = true)`, `postgres:16-alpine`, `@ServiceConnection`, `cameia.llm.model=modelo-de-prueba` | pruebas de CM-311 | copiar la cabecera de `InterviewSchemaMigrationTest` |
| `LayeredArchitectureTest` con la regla «domain sin `org.springframework..`» | `src/test/.../architecture/` | verde con las clases nuevas |
| Regla «toda excepción de `domain.exception` hereda de `BusinessException`» (`UntypedExceptionClassificationTest`) | CM-311 | CM-209 no crea excepciones de dominio (§6) |
| Colección `postman/` y entorno `local` | CM-311 T7 | abrir |
| `ProcessedMessageInbox` no existe aún en Entrevista | — | `git grep -n ProcessedMessageInbox` vacío antes de T3; si CM-311 o CM-312 lo crearon, reutilizar |
| Numeración de ADR (`0006` de CM-311) y de bitácora (`03` de CM-311, `04` de CM-204) | `docs/adr/`, `docs/bitacora-ia/` | `ls`; tomar el siguiente libre |

## 21. Hallazgos con destino

| # | Hallazgo | Tipo | Destino |
|---|---|---|---|
| B-1 | Jira no leído (descripción, comentarios, enlaces de CM-209) | Verificación previa | Tarjeta T0 |
| H-1 | **El despliegue de Entrevista usa `--min-instances=0`**: sin instancias vivas no hay consumidor y la copia se atrasa hasta la siguiente petición; tras escalar a cero, la primera creación de sesión responderá 409 `PROFILE_SYNC_PENDING` más a menudo. Perfil usa `--min-instances=1` por esta misma razón (comentario de su flujo de despliegue) | Acción para DevOps | Tarea de DevOps que abre Paula (título sugerido: «Entrevista: una instancia mínima para el consumidor de eventos»), o dentro de CM-320 (entorno único). Paula redacta; no se edita `.github/` |
| H-2 | `ProfileUpdatedPayloadV1` de Perfil deja salir `nombre` y `resumen` nulos («una fila antigua puede no tener nombre ni resumen»), contra su propio esquema (obligatorios). Entrevista los rechaza por contrato (cola de fallidos) | Corrección de código (Perfil, Backend) | CM-296 (pieza «`CHECK` de `perfil_profesional`»): con el `CHECK` que exige nombre y resumen en un perfil `COMPLETED`, la carga nula deja de ser posible. Anotado en la spec de CM-296 |
| H-3 | La tabla `evento_procesado` crece sin límite (igual que en Perfil) | Decisión técnica futura | Sin acción en el MVP (filas de 60 bytes); se anota en el documento del consumidor |
| H-4 | No hay borrado de la copia al eliminar la cuenta (Ley 1581) | Pendiente con destino | HU-1.9 (eliminar cuenta, «Próximamente»): su spec debe hacer que Entrevista consuma `cuenta.eliminada` y borre copia y sesiones |
| H-5 | Título de CM-209 y responsable vacíos en Jira | Acción para Vela | Ya pedido (13 y 15-oct); se repite en el resumen de la ola |
| H-6 | Estimación de Jira 4 h frente a 16 a 20 h con el catálogo de roles | Aviso a Vela | Se actualiza la estimación en el daily |
| H-7 | La nota de HU-4.4 dice «COMPLETED → COMPLETE» | Redacción del libro | Aviso al PO (D-RPL-02) |

## 22. Lista de verificación de la CM

| # | Qué | Cómo | Esperado |
|---|---|---|---|
| V-01 | HT03-C1, C2 | `ProfileEventsRabbitConfigTest`, `ProfileEventsTopologyIT`; Newman (3 GET, complemento manual) | colas, exchange y enlaces con los argumentos exactos |
| V-02 | HT03-C3 | `ProfileUpdatedEventReaderTest`, `ProfileUpdatedContractTest` | cada fila de §8 |
| V-03 | HT03-C4 | `…upsert_shouldIgnore_whenOlderThanCopy`, `…_whenOlderArrivesLater` | la copia conserva el más nuevo |
| V-04 | HT03-C5 | `…when20Redeliveries` | 1 fila y 1 Inbox |
| V-05 | HT03-C6 | `ProfileReplicaTest` | solo `COMPLETED` sin archivar es usable |
| V-06 | HT03-C7 | `ProfileReplicaRepositoryAdapterTest.findById_*` | copia o vacío |
| V-07 | R1 | revisión: identificadores en inglés, `@JsonProperty` con los nombres del contrato | cumple |
| V-08 | R2 | §8 completo | verde |
| V-09 | R3 | §10: cada razón con su prueba y su `WARN` | verde |
| V-10 | R4 | `ReplicaSchemaMigrationTest` (cada restricción violada; `ColumnLength` de `nombre` 255 y `firebase_uid` 128 contra las constantes) | verde |
| V-11 | R5 | §9 | verde |
| V-12 | R6 | §11; `…logs_shouldNotContainPersonalData` | verde |
| V-13 | R7 | sin estado en memoria; métodos ≤ 20 líneas; ArchUnit | verde |
| V-14 | R8 | ADR 0007, `docs/eventos/…`, Javadoc, `package-info` | revisión |
| V-15 | R9/R10 | `clean verify`; `jacoco.csv` | ≥ 90 % por clase |
| V-16 | R11 | Newman carpeta | 9/9 |
| V-17 | R13 | tabla §12 con resultados reales | llena |
| V-18 | Adversarial | dueño cambiado, `__TypeId__` malicioso, 1 MiB, `<script>`, 20 reentregas, dos hilos, base caída 1 y 3 veces | según §9 |

## 23. Hechos, inferencias y decisiones

- **Hechos:** contrato, topología y patrón de Perfil (§2); despliegue con `SPRING_RABBITMQ_ADDRESSES` y `--min-instances=0`; la cola ya existe en el entorno con mensajes.
- **Inferencias:** D-RPL-06, D-RPL-07; estimación.
- **Decisiones por recomendación (ratificadas por Paula el 22-oct, opción A):** D-RPL-01 a D-RPL-05.
- **PENDIENTE de persona:** ninguno que bloquee. H-1 (DevOps) no bloquea el código; afecta la latencia en el entorno.

## 24. Puerta de listo

Trazabilidad (§3), validación y bordes (§8, §9), errores (§10), seguridad (§11), atributos (§12), contraste con Perfil y con las specs de CM-311/CM-312 (§2, §20), integración (§18), línea base (§19) y lista de verificación (§22): hechos. **Veredicto: LISTA PARA EJECUTAR** con CM-311 fusionada. Aprobada por Paula el 22-oct-2026.

## 25. Criterio agregado: catálogo de roles profesionales (D-SES-02 de CM-207)

CM-207 valida el rol objetivo contra todos los roles activos del catálogo (CA-4.2.14), que solo vive en Perfil, y Entrevista no puede llamar a Perfil (HT-03). Por eso esta tarea, que ya consume eventos de Perfil, mantiene también una copia del catálogo. Paula y el equipo decidieron el 22-oct absorberlo como criterio de esta tarea y no crear otra.

- **REQ-RPL-20** Perfil debe publicar el catálogo completo de roles (`id`, nombre en español, nombre en inglés, estado `ACTIVE` o `INACTIVE`) al arrancar y cada vez que un rol cambie, por el mismo exchange de eventos y con su propia clave de enrutamiento.
- **REQ-RPL-21** Entrevista debe consumirlo con la misma topología, reintentos, cola de fallidos e idempotencia que el evento del perfil, y hacer un `upsert` por `id` en `microentrevista.rol_profesional` (forma en la spec de CM-207, §8.1; migración `V3`). Un rol nuevo se crea solo en Perfil y no exige migración en Entrevista.
- **REQ-RPL-22** Mientras la tabla esté vacía, `RoleCatalog.isActive` devuelve `false` y CM-207 rechaza el rol con `TARGET_ROLE_ID_INVALID_VALUE`; por eso Perfil publica el catálogo completo al arrancar y no solo los cambios.
### 25.1 Requisitos adicionales (EARS)

- **REQ-RPL-23** Perfil debe publicar el evento `perfil.catalogo-roles-actualizado` (versión 1) con **una sola instantánea** que contiene todos los roles del catálogo (hoy 46), activos e inactivos, por el exchange `perfil.events`, usando la tabla de salida `evento_saliente` y el relevo que ya existen. La clave de enrutamiento es el propio tipo del evento.
- **REQ-RPL-24** Perfil debe anotar la instantánea en la tabla de salida **cada vez que arranca** (evento `ApplicationReadyEvent`), salvo que ya haya una del catálogo pendiente de publicar. El catálogo de Perfil solo cambia con una migración y un despliegue (no existe ninguna ruta que cree, edite o desactive roles), así que «cada vez que un rol cambie» se cumple publicando al arrancar la versión nueva. La propiedad `perfil.events.announce-role-catalog-on-startup` (por omisión `true`; `false` en las pruebas que no la ejercen) permite apagarlo.
- **REQ-RPL-25** Perfil debe declarar la cola `entrevista.catalogo-roles-actualizado` (durable, `x-dead-letter-exchange = entrevista.dlx`, `x-dead-letter-routing-key = entrevista.catalogo-roles-actualizado`) y su enlace con la clave `perfil.catalogo-roles-actualizado`, igual que ya hace con la cola del perfil. Sin la cola el broker devuelve el mensaje (el publicador usa `mandatory`) y el evento queda pendiente para siempre.
- **REQ-RPL-26** Entrevista debe declarar al arrancar la misma cola con los mismos argumentos, su cola de fallidos `entrevista.catalogo-roles-actualizado.dlq` (durable, sin argumentos) y los enlaces de ambas (la de fallidos al exchange `entrevista.dlx` con la clave `entrevista.catalogo-roles-actualizado`), con la misma tolerancia a que Perfil las haya declarado antes (REQ-RPL-01 y 02).
- **REQ-RPL-27** Al llegar un mensaje de la cola del catálogo, Entrevista debe validar `message_id` (UUID), `type` = `perfil.catalogo-roles-actualizado` y `x-event-version` = `1` con las razones de REQ-RPL-03, y el cuerpo con la matriz de §25.3, rechazando sin reintentar con las razones de §25.5.
- **REQ-RPL-28** Entrevista debe aplicar el mensaje en **una sola transacción**: (1) anotar el `message_id` en `evento_procesado` y, si ya estaba, terminar sin cambios; (2) hacer el `upsert` de todos los roles en `microentrevista.rol_profesional`, actualizando una fila solo si `actualizadoEn` del mensaje es igual o posterior al `actualizado_en` guardado en esa fila. Los roles que la instantánea no trae **no se tocan ni se borran** (Perfil nunca borra un rol: lo desactiva).
- **REQ-RPL-29** El sistema debe confirmar (`ack`) solo después del commit, reintentar 2 veces más (esperas de 1 s y 2 s) ante un fallo técnico y mandar a la cola de fallidos al agotarlos, con la misma configuración, el mismo `MDC` y las mismas reglas de registro de REQ-RPL-11 a 13. Ningún registro lleva los nombres de los roles; sí `messageId` saneado, la cantidad de roles recibidos y aplicados, y la razón.

### 25.2 Contrato del evento `perfil.catalogo-roles-actualizado` v1

| Elemento | Valor |
|---|---|
| Exchange / clave / cola | `perfil.events` / `perfil.catalogo-roles-actualizado` / `entrevista.catalogo-roles-actualizado` |
| `message_id` | UUID v4 en texto (el `id` de la fila de `evento_saliente`) |
| `type` | `perfil.catalogo-roles-actualizado` |
| `headers.x-event-version` | `1` |
| `correlation_id` | `perfil-catalogo-` + 8 primeros caracteres del `message_id` en minúsculas (cumple `^[A-Za-z0-9._-]{1,64}$`; no hay petición HTTP que lo origine) |
| `content_type` | `application/json` |
| `agregado_id` en la tabla de salida | UUID constante `00000000-0000-0000-0000-000000000000` (el catálogo no pertenece a un perfil) |

Cuerpo (ejemplo válido, constante `VALID_CATALOG_BODY`):

```json
{"actualizadoEn":"2026-10-24T12:00:00.123Z","roles":[{"codigo":"a0000001-0000-0000-0000-000000000001","nombreEs":"Desarrollador Frontend","nombreEn":"Frontend Developer","estado":"ACTIVE"},{"codigo":"a0000001-0000-0000-0000-000000000002","nombreEs":"Desarrollador Backend","nombreEn":"Backend Developer","estado":"INACTIVE"}]}
```

Los nombres de campo siguen el estilo del evento del perfil (`codigo` es el mismo nombre que usa `rolesAspirados[].codigo` para el identificador del rol). Perfil rellena `nombreEn` con `nombre` (el español) cuando la columna `nombre_en` está vacía (la columna admite nulo, aunque hoy los 46 roles la tienen), para que la instantánea nunca viole su propio contrato. Perfil documenta el contrato en `docs/eventos/perfil-catalogo-roles-actualizado-v1.md` y `…-v1.schema.json`; Entrevista copia el esquema literal.

### 25.3 Matriz de validación del cuerpo (consumidor)

«Texto» cuenta **puntos de código** sin normalizar ni recortar. Cada fila se prueba en `RoleCatalogEventReaderTest` modificando un solo campo del `VALID_CATALOG_BODY`. La violación lleva la razón `PAYLOAD_CONTRACT_VIOLATION` y la ruta del campo, salvo la fila marcada.

| Campo | Regla | Caso que rechaza (ruta del campo en el registro) |
|---|---|---|
| (cuerpo) | JSON objeto, ≤ 1 MiB | vacío, no JSON, arreglo, cadena → `PAYLOAD_INVALID_FORMAT` |
| `actualizadoEn` | obligatorio; texto ISO-8601 UTC con `Z` | ausente, `null`, `"2026-10-24"`, `"2026-10-24T12:00:00+02:00"`, número → `actualizadoEn` |
| `roles` | obligatorio; arreglo de 1 a 200 elementos | ausente, `null`, `[]` (0), 201 elementos → `roles` |
| `roles[i]` | objeto | `null`, cadena → `roles[i]` |
| `roles[i].codigo` | obligatorio; UUID canónico (36 caracteres) | ausente, `null`, `"abc"`, 35 o 37 caracteres, sin guiones → `roles[i].codigo` |
| `roles[i].nombreEs` | obligatorio; 1 a 255 puntos de código; no solo espacios | ausente, `null`, `""`, `"   "`, 256 caracteres (255 pasa; 255 con `"😀"` también) → `roles[i].nombreEs` |
| `roles[i].nombreEn` | ídem | ídem → `roles[i].nombreEn` |
| `roles[i].estado` | obligatorio; exactamente `ACTIVE` o `INACTIVE` | `active`, `"Active"`, `""`, `null`, `"DELETED"` → `roles[i].estado` |
| `roles[*].codigo` | sin repetir dentro del mensaje | dos elementos con el mismo `codigo` → razón `CATALOG_ROLE_DUPLICATED` con la ruta del segundo (`roles[1].codigo`) |
| Campos desconocidos | se ignoran y no se guardan (REQ-RPL-06) | `roles[0].categoria`, `extra` en la raíz → el mensaje pasa |

### 25.4 Base de datos: `V3__catalogo_roles_profesionales.sql` (Entrevista) y `V10__evento_catalogo_roles.sql` (Perfil)

**Entrevista, esquema `microentrevista`.** Tabla nueva `rol_profesional` (la lee CM-207; su forma final es esta y reemplaza a la propuesta en la spec de CM-207, §8.1):

| Columna | Tipo | Nulo | Regla | Origen |
|---|---|---|---|---|
| `id` | `uuid` | no | `pk_rol_profesional` | `roles[].codigo` |
| `nombre_es` | `varchar(255)` | no | `ck_rol_profesional_nombre_es` (`char_length(btrim(nombre_es)) > 0`) | `nombreEs` |
| `nombre_en` | `varchar(255)` | no | `ck_rol_profesional_nombre_en` (`char_length(btrim(nombre_en)) > 0`) | `nombreEn` |
| `estado` | `varchar(8)` | no, `DEFAULT 'ACTIVE'` | `ck_rol_profesional_estado` IN (`ACTIVE`, `INACTIVE`) | `estado` |
| `actualizado_en` | `timestamptz` | no | — | `actualizadoEn` del último mensaje aplicado a esa fila |
| `fecha_replicacion` | `timestamptz` | no | — | Reloj del servicio al aplicar |

- Sin clave foránea (MAN-03: el rol es de Perfil) y sin índice extra (la consulta es por clave primaria; 46 filas). Sin `categoria` (nadie la usa en Entrevista).
- La misma migración reemplaza `ck_evento_procesado_tipo` de la tabla `evento_procesado` (creada en `V2`) por `CHECK (tipo IN ('perfil.profesional-actualizado', 'perfil.catalogo-roles-actualizado'))` y agrega los `COMMENT ON` de la tabla nueva y de `estado` y `actualizado_en` (español sin tildes, como `V1`).
- **Upsert** (una sola sentencia nativa, con el esquema calificado y los roles como un único parámetro JSON): `INSERT INTO microentrevista.rol_profesional (…) SELECT … FROM jsonb_to_recordset(CAST(:roles AS jsonb)) AS r(id uuid, nombre_es text, nombre_en text, estado text) ON CONFLICT (id) DO UPDATE SET nombre_es = EXCLUDED.nombre_es, nombre_en = EXCLUDED.nombre_en, estado = EXCLUDED.estado, actualizado_en = EXCLUDED.actualizado_en, fecha_replicacion = EXCLUDED.fecha_replicacion WHERE microentrevista.rol_profesional.actualizado_en <= EXCLUDED.actualizado_en`. Devuelve cuántas filas cambió.

**Perfil.** `V10__evento_catalogo_roles.sql` reemplaza `ck_evento_saliente_tipo` por `CHECK (tipo IN ('perfil.profesional-actualizado', 'perfil.catalogo-roles-actualizado'))`; no toca datos. **Numeración (decidida por Paula el 22-oct):** esta migración es `V10` (la última de Perfil hoy es `V9`) y la tarea de alinear el estándar (`CHECK` de `perfil_profesional`, CM-296) pasa a `V10`. Flyway falla si una versión menor llega después de una mayor ya aplicada, por eso este orden.

### 25.5 Errores y rechazos del mensaje (R3)

Se reutilizan las razones de §10 (`MESSAGE_ID_INVALID`, `EVENT_TYPE_INVALID`, `EVENT_VERSION_UNSUPPORTED`, `PAYLOAD_INVALID_FORMAT`, `PAYLOAD_CONTRACT_VIOLATION`) y se agrega **una propia del catálogo**; el registro de cada rechazo lleva el nombre de la cola, que distingue el evento. No hay respuesta HTTP: no se toca `ErrorCode`.

| Razón | Cuándo | Reintenta | Destino |
|---|---|---|---|
| `CATALOG_ROLE_DUPLICATED` (nueva) | dos roles con el mismo `codigo` en el mensaje (REQ-RPL-27) | no | `entrevista.catalogo-roles-actualizado.dlq` |
| las cinco de §10 | igual que en §10, con la matriz de §25.3 | no | ídem |
| (fallo técnico) | base caída, error de base o fallo inesperado | 3 intentos (1 s y 2 s) | ídem, con `ERROR` y traza al agotarse |

Una instantánea que llega **anterior** a la guardada no es un error: se anota en `evento_procesado`, no cambia ninguna fila y el registro dice `STALE_IGNORED`. Mientras la tabla esté vacía, `RoleCatalog.isActive` devuelve `false` (REQ-RPL-22).

### 25.6 Casos borde (R5) con datos literales

| # | Caso | Datos | Resultado |
|---|---|---|---|
| E-01 | Primera instantánea con la tabla vacía | `VALID_CATALOG_BODY` | 2 filas; `actualizado_en = 2026-10-24T12:00:00.123Z` |
| E-02 | Reentrega 20 veces | mismo `message_id` | 1 fila de Inbox; las filas no cambian de `fecha_replicacion` |
| E-03 | Llega una instantánea anterior después de una nueva | `actualizadoEn` 11:00 después de 12:00 | ninguna fila cambia; `STALE_IGNORED` |
| E-04 | Mismo `actualizadoEn` con otro `message_id` | 12:00:00.123 dos veces | se aplica la segunda (`<=`) |
| E-05 | Un rol pasa de `ACTIVE` a `INACTIVE` y luego vuelve | tres mensajes | la fila sigue al último; `isActive` cambia con ella |
| E-06 | La instantánea nueva no trae un rol ya guardado | 46 guardados, 45 en el mensaje | el rol ausente se conserva sin cambios |
| E-07 | Dos mensajes a la vez | dos hilos, mismos 46 roles | una fila por rol; ningún error de clave duplicada (el `ON CONFLICT` lo resuelve) |
| E-08 | Base caída | 1 intento y 3 intentos | con 1, se reintenta y se aplica; con 3, a la cola de fallidos con `ERROR` |
| E-09 | Texto de rol con marcado | `nombreEs = "<script>alert(1)</script>"` | se guarda literal; el registro no lo imprime |
| E-10 | 200 roles y 201 roles | cuerpos generados | 200 pasa; 201 → `PAYLOAD_CONTRACT_VIOLATION` en `roles` |
| E-11 | Llegan el evento del catálogo y el del perfil en cualquier orden | ambos mensajes | independientes; no se bloquean entre sí (colas, inbox y tablas distintas) |
| E-12 | Perfil arranca dos veces seguidas con el broker caído | 2 arranques | una sola fila pendiente del catálogo (REQ-RPL-24) |

### 25.7 Seguridad (R6)

La instantánea no contiene datos personales (el catálogo es público dentro del sistema). Aun así: sin nombres de rol en los registros (REQ-RPL-29); `__TypeId__` de AMQP ignorado (el convertidor usa el tipo fijo del oyente, como en §11); límite de 1 MiB y de 200 roles contra el consumo de recursos (API4); solo el servicio de Perfil publica en `perfil.events` (la credencial del broker es compartida, riesgo ya anotado en §11). Una instantánea falsa con un rol `ACTIVE` ajeno al catálogo habilitaría ese rol como objetivo de una sesión: efecto limitado a crear una sesión con un nombre de rol inexistente; se acepta mientras el broker sea compartido (se reevalúa con CM-320).

### 25.8 Cambios en `cameia-perfil` (PR propio, rama `CM-209-catalogo-roles-evento`, se fusiona **antes** que el de Entrevista)

| Capa y paquete | Pieza | Qué hace |
|---|---|---|
| `domain.model` | `ProfessionalRoleNames` (`record`: `UUID id`, `String nameEs`, `String nameEn`, `boolean active`) | Rol con sus dos idiomas; `nameEn` nunca vacío (usa `nameEs` si falta) |
| `domain.event` | `RoleCatalogUpdated` (`record`: `UUID eventId`, `List<ProfessionalRoleNames> roles`, `Instant updatedAt`, `CorrelationId correlationId`; constantes `TYPE`, `VERSION = 1`, `AGGREGATE_ID`) | Hecho «el catálogo queda así»; exige 1 a 200 roles |
| `domain.port` | `ProfessionalRoleRepository.findAllWithBothLanguages()`; `OutboxRepository.saveRoleCatalog(RoleCatalogUpdated)` y `existsPendingOfType(String)` | Lectura de los dos idiomas y escritura en la tabla de salida |
| `application.service` | `RoleCatalogEventsAppService.announceCatalog()` | Si no hay uno pendiente: lee, arma, guarda en una transacción y pide el relevo tras el commit (`OutboxRelayAppService.relayAfterCommit`) |
| `infrastructure.messaging.payload` | `RoleCatalogPayloadV1` (+ `RoleItem`) | Cuerpo del contrato v1 |
| `infrastructure.messaging.config` | `RabbitConfig`: constantes `ROLE_CATALOG_KEY`, `INTERVIEW_ROLE_CATALOG_QUEUE` y los beans de cola y enlace | REQ-RPL-25 |
| `infrastructure.config` | `RoleCatalogStartupAnnouncer` (`@EventListener(ApplicationReadyEvent)`, propiedad de REQ-RPL-24; un fallo se registra en `WARN` sin traer abajo el arranque) | REQ-RPL-24 |
| `infrastructure.persistence` | métodos nuevos de `ProfessionalRoleRepositoryAdapter` y `OutboxRepositoryAdapter`; `V10` | §25.4 |
| `docs` | `docs/eventos/perfil-catalogo-roles-actualizado-v1.md` y `.schema.json`; ADR siguiente libre (`docs/adr`); bitácora de IA siguiente libre | Contrato y decisión |

El relevo (`OutboxRelayAppService`) y el Job `profile-events-relay` publican **cualquier** tipo pendiente, así que no cambian. Pruebas de Perfil (la infraestructura de RabbitMQ real ya existe allí, así que no se agrega ninguna dependencia): `RoleCatalogUpdatedTest`, `RoleCatalogPayloadV1Test`, `RoleCatalogEventContractTest` (cuerpo contra el esquema JSON), `RoleCatalogEventsAppServiceTest` (no duplica si hay uno pendiente; `nombreEn` de respaldo), `OutboxRepositoryAdapterIT` (la `V10` acepta el tipo nuevo y rechaza otro), `RoleCatalogEventsEndToEndIT` (arranque → fila → cola `entrevista.catalogo-roles-actualizado` con 46 roles) y `RabbitConfigTest` (argumentos exactos de la cola).

### 25.9 Cambios en `cameia-entrevista` (mismo PR de CM-209)

| Capa y paquete | Pieza | Qué hace |
|---|---|---|
| `domain.model` | `ProfessionalRoleReplica` (`record`: `UUID id`, `String nameEs`, `String nameEn`, `RoleStatus status`) y `RoleStatus` (`ACTIVE`, `INACTIVE`) | Copia de un rol |
| `domain.port` | `RoleCatalogReplicaRepository` (`int upsertSnapshot(List<ProfessionalRoleReplica>, Instant snapshotAt, Instant replicatedAt)`) | Escritura; la lectura `RoleCatalog.isActive` es de CM-207 |
| `application.command` / `service` | `ReplicateRoleCatalogCommand`, `ReplicateRoleCatalogService` (`@Transactional`; reutiliza `ProcessedMessageInbox` y `ReplicationOutcome`) | REQ-RPL-28 |
| `infrastructure.persistence` | `ProfessionalRoleEntity` (solo `ddl-auto=validate`), `ProfessionalRoleJpaRepository` (consulta nativa de §25.4), `RoleCatalogReplicaRepositoryAdapter` | CM-207 reutiliza la entidad y el repositorio JPA y agrega solo su `RoleCatalogAdapter` de lectura |
| `infrastructure.messaging` | `RoleCatalogRabbitConfig`, `RoleCatalogPayloadV1`, `RoleCatalogListener`, `RoleCatalogEventReader`; reutiliza `ProfileEventsRetryConfig`, `ProfileEventsErrorHandler`, `ProfileEventsRecoverer`, `ContractViolation` y `EventLogValues` (si alguno tiene fija la cola del perfil, se parametriza con el cambio mínimo) | REQ-RPL-26, 27, 29 |
| `docs` | `docs/eventos/consumo-perfil-catalogo-roles-actualizado.md` y la copia del esquema; sección nueva en el ADR 0007 | Documentación |

**Pruebas de Entrevista (las de broker, con RabbitMQ real, D-RPL-04 opción A):** `RoleCatalogEventReaderTest` (toda la matriz de §25.3), `ReplicateRoleCatalogServiceTest` (E-02 a E-06), `RoleCatalogReplicaRepositoryAdapterIT` (PostgreSQL real: E-01, E-03 a E-07 y E-10), `RoleCatalogSchemaMigrationTest` (cada restricción violada; `ColumnLength` de `nombre_es`/`nombre_en` = 255 contra la constante; el `CHECK` nuevo de `evento_procesado`), `RoleCatalogRabbitConfigTest` (argumentos exactos de la cola, la de fallidos y los enlaces), `RoleCatalogListenerTest` (reintento, rechazo sin reintento, `MDC`), `RoleCatalogBrokerIT` (RabbitMQ real: cola, exchange y cola de fallidos declarados con sus argumentos, un mensaje con contrato roto llega a `entrevista.catalogo-roles-actualizado.dlq` sin reintentos, un mensaje válido se copia y se confirma tras el commit) y `…logs_shouldNotContainRoleNames`. Cobertura de lo nuevo: ≥ 90 % de líneas y de ramas por clase. Con la opción A de D-RPL-04, la cola, su cola de fallidos y el `ack` del catálogo de roles tienen prueba automática en Entrevista (`RoleCatalogBrokerIT`) y, del lado de Perfil, `RoleCatalogEventsEndToEndIT`.

**Postman (Newman):** dos peticiones más en la carpeta «Réplica del perfil (RabbitMQ)» (consulta a la API de administración del broker): cola `entrevista.catalogo-roles-actualizado` con los argumentos exactos y cola de fallidos `….dlq` vacía.

### 25.10 Tarjetas nuevas (ver `tasks.md`) y estimación

| Tarjeta | Qué | Horas |
|---|---|---|
| T2b | `V3` de Entrevista y su prueba de esquema | 1 |
| T3b | Dominio, caso de uso y persistencia del catálogo en Entrevista | 1,5 |
| T4b | Consumidor, validación y configuración de la cola | 2 |
| T5b | Pruebas de integración contra PostgreSQL y contrato | 1 |
| T6b | Documentación, ADR y Postman | 0,5 |
| P1 a P3 | Cambios en Perfil: dominio y publicación, cola y `V10`, pruebas y documentación (PR aparte) | 3 a 4 |
| | **Total del criterio** | **6 a 8** |

### 25.11 Decisiones del criterio (D-RPL-08 a D-RPL-12)

| # | Decisión | Opciones | Por qué | Quién |
|---|---|---|---|---|
| D-RPL-08 | Una instantánea completa en un solo mensaje | A) instantánea; B) un mensaje por rol cambiado | El catálogo son 46 filas (< 10 KB) y solo cambia con un despliegue; con A la tabla vacía de un servicio nuevo se llena con un solo mensaje y una sola transacción. B exige saber qué cambió | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-RPL-09 | Perfil publica en cada arranque (si no hay otro pendiente) | A) cada arranque; B) solo cuando el catálogo difiere del último publicado (guardar huella); C) tarea programada | A es la forma más simple de cumplir REQ-RPL-22 y es idempotente. Costo: un mensaje de < 10 KB por cada arranque en frío. B agrega estado y una tabla para ahorrar ese mensaje | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)**; se reevalúa si DevOps sube los arranques en frío (CM-321) |
| D-RPL-10 | `actualizadoEn` por fila en lugar de una versión global | A) por fila; B) tabla de una fila con la última versión | Es el mismo criterio de REQ-RPL-08 y no exige una tabla más | **DECIDIDA POR RECOMENDACIÓN** |
| D-RPL-11 | Los roles que faltan en una instantánea no se borran | A) conservar; B) borrar los ausentes | Perfil nunca borra roles (`ON DELETE RESTRICT` desde `rol_objetivo`) y borrar por omisión de un mensaje parcial dejaría sesiones con un rol inexistente | **DECIDIDA POR RECOMENDACIÓN** |
| D-RPL-12 | Dos PR: Perfil primero y luego Entrevista | A) dos PR; B) uno solo | Son dos repositorios y dos despliegues; Perfil declara la cola, así que los mensajes esperan aunque Entrevista aún no esté | Regla del repositorio (un PR por repositorio) |

**Estado de este criterio:** detallado el 22-oct. Aprobado por Paula el 22-oct-2026 junto con el resto de la spec (D-RPL-04 = A).
