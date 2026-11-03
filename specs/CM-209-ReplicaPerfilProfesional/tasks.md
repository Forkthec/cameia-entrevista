# Tarjetas · CM-209 Réplica del Perfil Profesional en Entrevista

Cada tarjeta se ejecuta sola y en orden; quien la ejecuta no lee la spec.

**Reglas para todas las tarjetas**

- Identificadores, métodos de prueba y razones de rechazo en inglés; Javadoc, comentarios, `@DisplayName`, logs y documentos en español. Tablas y columnas en español. Sin `CM-209` ni rutas a otros archivos o repos en comentarios.
- Javadoc en toda clase, `record` y método; `package-info.java` en cada paquete nuevo.
- Pruebas `metodo_shouldX_whenY` + `@DisplayName` en español; valores literales; sin `Thread.sleep` (para esperar un mensaje usa `Awaitility` si CM-311 lo trae; si no, un bucle con `rabbitTemplate.receive(cola, 5000)` o `RabbitAdmin.getQueueInfo` con plazo, nunca `sleep`).
- Logs: nunca el cuerpo, el nombre, el resumen ni ningún dato del perfil; sí `perfilId`, `firebaseUid`, `messageId` saneado y la razón.
- Prohibido: dependencias distintas de las de T1, editar `.github/`, `V1`, `ErrorCode` o el manejador HTTP; `catch` vacío.
- **Detenerse y reportar** con la salida real si un nombre de CM-311 no coincide, una prueba existente se rompe sin causa clara o falta un dato.
- Docker encendido: sin Docker las pruebas con Testcontainers se omiten y eso **no** es verde.

**Ejemplo válido del contrato** (constante `VALID_BODY` que usan varias pruebas; es el `examples[0]` del esquema de Perfil):

```json
{"usuarioId":"6f1d2c3b4a5e4f60718293a4b5c6d7e8","contextoProfesional":{"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7","nombre":"Analista de datos","estado":"COMPLETED","fecha_archivado":null,"resumen":"Analista con tres años de experiencia en SQL y Python.","experiencias":[{"empresa":"ACME","cargo":"Analista","descripcion":null,"inicio":"2022-01","fin":null,"estadoEmpleo":"CURRENT"}],"formaciones":[{"institucion":"Universidad del Cauca","titulo":"Ingeniería de Sistemas","nivel":"UNDERGRADUATE","inicio":"2017-02","fin":"2021-12","enCurso":false}],"habilidades":[{"nombre":"Python","nivel":"INTERMEDIATE"}]},"rolesAspirados":[{"codigo":"a0000001-0000-0000-0000-000000000002","nombre":"Desarrollador Backend"}],"actualizadoEn":"2026-10-15T12:00:00.123Z"}
```

Propiedades válidas: `message_id = 11111111-1111-4111-8111-111111111111`, `type = perfil.profesional-actualizado`, encabezado `x-event-version = 1`, `content_type = application/json`, `correlation_id = req-123`.

---

## T0 · Preparación

1. `git fetch`; `git status` limpio. Base: CM-311 fusionada → rama `CM-209-replica-perfil-profesional` desde `origin/develop`; si no, stack sobre `origin/CM-311-*` (anotar SHA y árbol); si no existe, **detenerse**.
2. Confirmar (si algo difiere, detenerse): esquema `microentrevista` y `V1` en `src/main/resources/db/migration/`; `FirebaseUid.MAX_LENGTH = 128`; cabecera de prueba con Testcontainers en `InterviewSchemaMigrationTest`; `LayeredArchitectureTest`; `UntypedExceptionClassificationTest`; `git grep -n "ProcessedMessageInbox\|spring-boot-starter-amqp"` vacío; siguiente ADR libre (`ls docs/adr`) y siguiente bitácora libre (`ls docs/bitacora-ia`).
3. Jira (lectura): descripción, comentarios, padre y enlaces de CM-209; anotar aquí si cambian algo. Si cambian un valor de la spec, detenerse.
4. Línea base: `./mvnw.cmd -B -ntp clean verify` verde, sin `Skipped` de Testcontainers. Anotar el número de pruebas.
5. Mover CM-209 a «En curso» si Paula es la responsable en Jira (hoy no tiene responsable: no se mueve y se anota).

---

## T1 · Base técnica de mensajería

**`pom.xml`** (copiar la forma de `cameia-perfil/pom.xml`; versiones del BOM de Spring Boot, sin número propio):

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

En el `maven-surefire-plugin` (crear el bloque `<plugin>` si CM-311 no lo dejó, sin cambiar otra configuración):

```xml
<configuration>
  <systemPropertyVariables>
    <!-- Las pruebas sin broker no arrancan el consumidor; las de mensajería lo encienden con @SpringBootTest(properties). -->
    <spring.rabbitmq.listener.simple.auto-startup>false</spring.rabbitmq.listener.simple.auto-startup>
  </systemPropertyVariables>
</configuration>
```

**`src/main/resources/application.properties`** (al final, con comentario de bloque en español):

```properties
# Mensajeria (consumidor de perfil.profesional-actualizado). La direccion del broker llega en
# SPRING_RABBITMQ_ADDRESSES (la monta el despliegue desde un secreto). Mismos valores que Perfil.
spring.rabbitmq.connection-timeout=2s
spring.rabbitmq.listener.simple.acknowledge-mode=auto
spring.rabbitmq.listener.simple.default-requeue-rejected=false
spring.rabbitmq.listener.simple.prefetch=10
spring.rabbitmq.listener.simple.concurrency=1
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-retries=2
spring.rabbitmq.listener.simple.retry.initial-interval=1s
spring.rabbitmq.listener.simple.retry.multiplier=2
spring.rabbitmq.listener.simple.retry.max-interval=2s
```

**Trampa:** en Spring Boot 4 la propiedad es `retry.max-retries` (reintentos después del primero), como en Perfil; si al arrancar aparece «unknown property», revisar `spring-boot-autoconfigure` y reportar el nombre real.

**`docker-compose.yml`:** servicio nuevo, solo bajo el perfil de compose `mensajeria`:

```yaml
  entrevista-rabbitmq:
    image: rabbitmq:4-management-alpine
    container_name: cameia-entrevista-rabbitmq
    profiles: ["mensajeria"]
    ports:
      - "127.0.0.1:5673:5672"
      - "127.0.0.1:15673:15672"
    networks:
      - cameia-entrevista
```

y en `entrevista.environment`: `SPRING_RABBITMQ_ADDRESSES: ${SPRING_RABBITMQ_ADDRESSES:-amqp://guest:guest@entrevista-rabbitmq:5672}`.

**`.env.example`:** sección «Mensajería (RabbitMQ)» con `SPRING_RABBITMQ_ADDRESSES=amqp://guest:guest@localhost:5673` y el comentario: «Broker local de desarrollo (`docker compose --profile mensajeria up -d`); `guest` es el usuario por defecto de la imagen y no es un secreto. En despliegue llega desde el gestor de secretos.»

**Comando:** `./mvnw.cmd -B -ntp clean verify` → verde y con el mismo número de pruebas que T0 (nada se rompe por el arranque sin broker).

**Commit:** `CM-209 | build(mensajeria): RabbitMQ con reintentos y broker local como en Perfil`.

---

## T2 · Migración `V2__replica_perfil_profesional.sql`

**Archivo:** `src/main/resources/db/migration/V2__replica_perfil_profesional.sql`:

```sql
-- Copia del Perfil Profesional que mantiene el consumidor de perfil.profesional-actualizado.
CREATE TABLE microentrevista.contexto_profesional_replica (
    perfil_id            uuid         NOT NULL,
    firebase_uid         varchar(128) NOT NULL,
    nombre               varchar(255) NOT NULL,
    estado               varchar(20)  NOT NULL,
    fecha_archivado      timestamptz  NULL,
    contexto_profesional jsonb        NOT NULL,
    roles_aspirados      jsonb        NOT NULL,
    actualizado_en       timestamptz  NOT NULL,
    fecha_replicacion    timestamptz  NOT NULL,
    CONSTRAINT pk_contexto_profesional_replica PRIMARY KEY (perfil_id),
    CONSTRAINT ck_contexto_profesional_replica_firebase_uid CHECK (char_length(btrim(firebase_uid)) > 0),
    CONSTRAINT ck_contexto_profesional_replica_nombre CHECK (char_length(btrim(nombre)) > 0),
    CONSTRAINT ck_contexto_profesional_replica_estado
        CHECK (estado IN ('PENDING', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED')),
    CONSTRAINT ck_contexto_profesional_replica_contexto CHECK (jsonb_typeof(contexto_profesional) = 'object'),
    CONSTRAINT ck_contexto_profesional_replica_roles
        CHECK (jsonb_typeof(roles_aspirados) = 'array' AND jsonb_array_length(roles_aspirados) BETWEEN 1 AND 5)
);

-- Inbox: un mensaje aplicado no vuelve a tener efecto.
CREATE TABLE microentrevista.evento_procesado (
    message_id   uuid        NOT NULL,
    tipo         varchar(40) NOT NULL,
    procesado_en timestamptz NOT NULL,
    CONSTRAINT pk_evento_procesado PRIMARY KEY (message_id),
    CONSTRAINT ck_evento_procesado_tipo CHECK (tipo IN ('perfil.profesional-actualizado'))
);
```

Al final, los `COMMENT ON` de la spec §7.3 con su texto literal (sin tildes).

**Trampa:** `jsonb_array_length` falla si el valor no es un arreglo; por eso el `CHECK` evalúa primero `jsonb_typeof(...) = 'array' AND …` (PostgreSQL no garantiza el orden del `AND` en todos los casos: si la prueba de «roles como objeto» devuelve un error distinto de `23514`, cambiar a `CASE WHEN jsonb_typeof(roles_aspirados) = 'array' THEN jsonb_array_length(roles_aspirados) BETWEEN 1 AND 5 ELSE false END`).

**Prueba nueva** `src/test/java/tech/cameia/entrevista/infrastructure/persistence/ReplicaSchemaMigrationTest.java` (cabecera de Testcontainers de CM-311; `JdbcTemplate`; inserta por SQL):

| Prueba | Datos | Esperado |
|---|---|---|
| `migration_shouldCreateBothTables` | `information_schema.tables` | las 2 tablas en `microentrevista` |
| `replica_shouldRejectUnknownStatus` (`@ValueSource`: `ARCHIVED`, `completed`, `''`) | fila válida con ese `estado` | `DataIntegrityViolationException` con `ck_contexto_profesional_replica_estado` |
| `replica_shouldRejectBlankName` y `…BlankFirebaseUid` (`'   '`) | — | `ck_…_nombre`, `ck_…_firebase_uid` |
| `replica_shouldReject256CharName` / `…_shouldAccept255CodePointName` (`"😀".repeat(255)`) | — | error de largo / se inserta |
| `replica_shouldRejectRolesNotArray` (`'{}'`), `…EmptyRoles` (`'[]'`), `…SixRoles` | — | `ck_…_roles` |
| `replica_shouldRejectContextNotObject` (`'[]'`) | — | `ck_…_contexto` |
| `replica_shouldRejectNullColumns` (parametrizada por cada columna `NOT NULL`) | — | violación `23502` |
| `replica_shouldHaveNoForeignKeys` | `information_schema.table_constraints` | 0 `FOREIGN KEY` en las 2 tablas |
| `inbox_shouldRejectUnknownType` (`cuenta.creada`) y `…DuplicateMessageId` | — | `ck_evento_procesado_tipo`; `pk_evento_procesado` |
| `columnLengths_shouldMatchDomain` | `character_maximum_length` | `firebase_uid` = `FirebaseUid.MAX_LENGTH`, `nombre` = `ProfileReplica.MAX_NAME_LENGTH` (la constante llega en T3: en este commit, comparar con `128` y `255` literales y cambiar a las constantes en T3) |
| `comments_shouldExist` | `obj_description`, `col_description` | las 6 de §7.3 no nulas |

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=ReplicaSchemaMigrationTest` → ejecutada y verde.

**Commit:** `CM-209 | feat(datos): tablas de la copia del perfil y de mensajes procesados`.

---

## T3 · Dominio, puertos, persistencia y caso de uso

**Dominio** (`domain/model/`):

```java
/** Estado del perfil tal como lo publica Perfil; solo {@link #COMPLETED} es un perfil activo. */
public enum ReplicatedProfileStatus { PENDING, IN_PROGRESS, IN_REVIEW, COMPLETED }

/** Resultado de aplicar un mensaje del perfil a la copia. */
public enum ReplicationOutcome { APPLIED, STALE_IGNORED, DUPLICATE_IGNORED, OWNER_MISMATCH }

/**
 * Copia del Perfil Profesional que guarda Entrevista para crear sesiones sin llamar a Perfil.
 *
 * @param profileId               identificador del perfil en Perfil
 * @param owner                   dueño del perfil
 * @param name                    nombre del perfil, 1 a 255 puntos de código
 * @param status                  estado publicado
 * @param archivedAt              instante de archivado o {@code null}
 * @param professionalContextJson bloque {@code contextoProfesional} en JSON
 * @param aspiredRolesJson        lista {@code rolesAspirados} en JSON
 * @param updatedAt               {@code actualizadoEn} del evento
 */
public record ProfileReplica(UUID profileId, FirebaseUid owner, String name, ReplicatedProfileStatus status,
                             Instant archivedAt, String professionalContextJson, String aspiredRolesJson, Instant updatedAt) {

    /** Largo máximo del nombre, igual que la columna y el contrato del evento. */
    public static final int MAX_NAME_LENGTH = 255;

    /** Exige todo salvo {@code archivedAt}; el contenido ya lo validó quien lee el mensaje. */
    public ProfileReplica { /* Objects.requireNonNull en cada componente salvo archivedAt */ }

    /** @return {@code true} solo si el perfil está activo y no archivado: el único que sirve para una entrevista */
    public boolean isUsableForInterview() {
        return status == ReplicatedProfileStatus.COMPLETED && archivedAt == null;
    }
}
```

**Puertos** (`domain/port/`):

```java
/** Copia local de los perfiles profesionales. */
public interface ProfileReplicaRepository {
    /** @return la copia del perfil o vacío si nunca llegó */
    Optional<ProfileReplica> findById(UUID profileId);
    /**
     * Inserta la copia o la reemplaza si la guardada no es más nueva y tiene el mismo dueño.
     * @return {@code true} si cambió la copia; {@code false} si la guardada era más nueva (o de otro dueño)
     */
    boolean upsertIfNotOlder(ProfileReplica replica, Instant replicatedAt);
}

/** Registro de los mensajes ya aplicados (Inbox). */
public interface ProcessedMessageInbox {
    /** @return {@code true} si no estaba y ahora sí; {@code false} si ya se había aplicado */
    boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt);
}
```

**Persistencia** (`infrastructure/persistence/`): entidades `ProfileReplicaEntity` (`@Table(name = "contexto_profesional_replica")`, columnas con `@Column(name, length, nullable)` iguales a T2, los dos `jsonb` como `String` con `@JdbcTypeCode(SqlTypes.JSON)`, `estado` como `String`) y `ProcessedMessageEntity` (`evento_procesado`); existen para `ddl-auto=validate`. Repositorios:

```java
interface ProfileReplicaJpaRepository extends JpaRepository<ProfileReplicaEntity, UUID> {
    /** Inserta o reemplaza la copia solo si no es más vieja que la guardada y conserva el dueño. @return filas cambiadas, 0 o 1 */
    @Modifying
    @Query(value = """
            INSERT INTO microentrevista.contexto_profesional_replica
                (perfil_id, firebase_uid, nombre, estado, fecha_archivado, contexto_profesional, roles_aspirados,
                 actualizado_en, fecha_replicacion)
            VALUES (:profileId, :owner, :name, :status, :archivedAt, CAST(:context AS jsonb), CAST(:roles AS jsonb),
                    :updatedAt, :replicatedAt)
            ON CONFLICT (perfil_id) DO UPDATE SET
                nombre = EXCLUDED.nombre, estado = EXCLUDED.estado, fecha_archivado = EXCLUDED.fecha_archivado,
                contexto_profesional = EXCLUDED.contexto_profesional, roles_aspirados = EXCLUDED.roles_aspirados,
                actualizado_en = EXCLUDED.actualizado_en, fecha_replicacion = EXCLUDED.fecha_replicacion
            WHERE microentrevista.contexto_profesional_replica.actualizado_en <= EXCLUDED.actualizado_en
              AND microentrevista.contexto_profesional_replica.firebase_uid = EXCLUDED.firebase_uid
            """, nativeQuery = true)
    int upsertIfNotOlder(/* @Param de cada valor; ≤ 3 parámetros no aplica a una consulta generada: se documenta */);
}
```

`ProcessedMessageJpaRepository.insertIfAbsent` igual que en Perfil, con la tabla calificada `microentrevista.evento_procesado`. Adaptadores package-private `ProfileReplicaRepositoryAdapter` (mapeo a mano `toDomain`/parámetros; `@Transactional(propagation = MANDATORY)` en `upsertIfNotOlder`: solo se llama dentro del caso de uso) y `ProcessedMessageInboxAdapter`.

**Trampa 1:** el parámetro `:archivedAt` nulo debe ir tipado (`Instant`) para que PostgreSQL no lo tome como `text`; si falla con «could not determine data type», usar `CAST(:archivedAt AS timestamptz)`.
**Trampa 2:** un `@Query` nativo con más de 3 parámetros es aceptable (es SQL, no un método de diseño); el adaptador expone el método de 2 parámetros del puerto.

**Caso de uso** `application/command/ReplicateProfileCommand` (`record UUID messageId, ProfileReplica replica`) y `application/service/ReplicateProfileService`:

```java
/**
 * Aplica un mensaje del perfil a la copia local, una sola vez y sin retroceder en el tiempo.
 *
 * <p>Orden dentro de una transacción: (1) si la copia existe con otro dueño, se devuelve
 * {@link ReplicationOutcome#OWNER_MISMATCH} sin escribir, para que el mensaje se pueda reprocesar si fue un error;
 * (2) se anota el mensaje en el Inbox y, si ya estaba, no hay efecto; (3) se inserta o reemplaza la copia si no es
 * más vieja que la guardada. El {@code ack} del broker ocurre después del commit.</p>
 */
@Service
public class ReplicateProfileService {
    static final String EVENT_TYPE = "perfil.profesional-actualizado";
    // dependencias: ProfileReplicaRepository, ProcessedMessageInbox, Clock
    @Transactional
    public ReplicationOutcome apply(ReplicateProfileCommand command) { … }
}
```

`Clock`: si `DomainPolicyConfiguration` (de CM-204) ya existe, agregar ahí `@Bean Clock clock() { return Clock.systemUTC(); }`; si no existe (CM-204 sin fusionar), crear `infrastructure/config/ClockConfiguration` con ese único bean y anotarlo en el resumen (CM-207 los unifica).

Logs del servicio (`INFO`): «Copia del perfil actualizada [perfilId={}, firebaseUid={}, messageId={}]», «Mensaje del perfil más viejo que la copia; se ignora […]», «Mensaje del perfil ya aplicado; se ignora [messageId={}]». `OWNER_MISMATCH` no se registra aquí (lo registra el consumidor una vez).

**Pruebas**

| Clase | Prueba | Esperado |
|---|---|---|
| `ProfileReplicaTest` | `isUsableForInterview_shouldBeTrue_onlyWhenCompletedAndNotArchived` (`@CsvSource`: `COMPLETED,null→true`; `COMPLETED,2026-10-15T12:00:00Z→false`; `PENDING,null→false`; `IN_PROGRESS,null→false`; `IN_REVIEW,null→false`) | según la fila |
| | `constructor_shouldThrow_whenRequiredNull` (parametrizada por componente) | `NullPointerException` |
| `ReplicateProfileServiceTest` (fakes en memoria `InMemoryProfileReplicaRepository`, `InMemoryProcessedMessageInbox` en `src/test`, reloj fijo `2026-10-20T15:00:00Z`) | `apply_shouldInsert_whenFirstMessage` | `APPLIED`; copia guardada; Inbox con el id |
| | `apply_shouldIgnore_whenMessageAlreadyProcessed` | `DUPLICATE_IGNORED`; copia sin cambio |
| | `apply_shouldIgnore_whenOlderThanCopy` | `STALE_IGNORED`; Inbox con el id; copia sin cambio |
| | `apply_shouldRejectOwnerChange_withoutWriting` | `OWNER_MISMATCH`; Inbox **sin** el id; copia de `uid-a` intacta |
| | `apply_shouldApplyBoth_whenSameContentNewerInstant` | dos `APPLIED` |
| `ProfileReplicaRepositoryAdapterTest` (Testcontainers; cada prueba en `TransactionTemplate`) | `upsert_shouldInsert_whenNew`, `upsert_shouldIgnore_whenOlderThanCopy` (`.122Z` tras `.123Z`), `upsert_shouldApply_whenSameInstant`, `upsert_shouldNotChangeOwner_whenOwnerDiffers`, `upsert_shouldStoreHostileTextLiterally` (`<script>alert(1)</script>`, `'`, `%`, `_`, `😀`), `upsert_shouldKeepNewest_whenConcurrent` (dos hilos con `CountDownLatch`, `.100Z` y `.200Z`; al final `.200Z`), `findById_shouldReturnCopy_whenReplicated`, `findById_shouldReturnEmpty_whenNeverReplicated`, `upsert_shouldFail_whenOutsideTransaction` (`IllegalTransactionStateException`) | según el nombre |
| `ProcessedMessageInboxAdapterTest` | `registerIfAbsent_shouldReturnTrueThenFalse`, `registerIfAbsent_shouldLetOnlyOneWin_whenConcurrent` (dos hilos) | una sola fila |

**Commit:** `CM-209 | feat(dominio): copia del perfil con su regla de uso y su persistencia`.

---

## T4 · Consumidor del evento

**Payload** (`infrastructure/messaging/payload/ProfileUpdatedPayloadV1.java`): `record` con nombres en inglés y `@JsonProperty` con el nombre del contrato, y Bean Validation según la spec §8:

```java
public record ProfileUpdatedPayloadV1(
        @JsonProperty("usuarioId") @NotNull @TextLength(min = 1, max = 128) @Pattern(regexp = "[^\\p{Cntrl}]+") String userId,
        @JsonProperty("contextoProfesional") @NotNull @Valid ProfessionalContext professionalContext,
        @JsonProperty("rolesAspirados") @NotNull @Size(min = 1, max = 5) List<@NotNull @Valid AspiredRole> aspiredRoles,
        @JsonProperty("actualizadoEn") @NotNull @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z") String updatedAt) {

    public record ProfessionalContext(
            @JsonProperty("id") @NotNull @Pattern(regexp = UUID_PATTERN) String id,
            @JsonProperty("nombre") @NotNull @TextLength(min = 1, max = 255) String name,
            @JsonProperty("estado") @NotNull @Pattern(regexp = "PENDING|IN_PROGRESS|IN_REVIEW|COMPLETED") String status,
            @JsonProperty("fecha_archivado") String archivedAt,
            @JsonProperty("resumen") @NotNull @TextLength(min = 1, max = 2000) String summary,
            @JsonProperty("experiencias") @NotNull @Size(max = 4) List<@NotNull @Valid WorkExperience> experiences,
            @JsonProperty("formaciones") @NotNull @Size(min = 1, max = 5) List<@NotNull @Valid Education> educations,
            @JsonProperty("habilidades") @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Skill> skills) { }

    // WorkExperience(empresa 1-100, cargo 1-100, descripcion null|1-500, inicio null|AAAA-MM, fin null|AAAA-MM,
    //                estadoEmpleo CURRENT|UNKNOWN_END|ENDED)
    // Education(institucion 1-150, titulo 1-150, nivel TECHNICAL|UNDERGRADUATE|POSTGRADUATE, inicio AAAA-MM obligatorio,
    //           fin null|AAAA-MM, enCurso Boolean @NotNull)
    // Skill(nombre 1-60, nivel BASIC|INTERMEDIATE|ADVANCED)
    // AspiredRole(codigo UUID, nombre 1-255)
    static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    static final String YEAR_MONTH_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";
}
```

`@TextLength(min, max)` (anotación de restricción en el mismo paquete) y `TextLengthValidator`: `null` es válido (lo exige `@NotNull` aparte); cuenta `value.codePointCount(0, value.length())`; `min = 1` exige además que no sea solo espacios (`!value.isBlank()`).

**Trampa:** `@Size` cuenta unidades UTF-16 (un emoji vale 2); por eso los textos usan `@TextLength` y las listas `@Size`.

**Lector** `infrastructure/messaging/consumer/ProfileUpdatedEventReader` (`@Component`, recibe `jakarta.validation.Validator` y el `JsonMapper` de Spring):

```java
/**
 * Comprueba un mensaje contra el contrato v1 y lo convierte en el comando del caso de uso.
 * @throws ContractViolation con la razón estable y, si aplica, la ruta del campo
 */
ReplicateProfileCommand read(ProfileUpdatedPayloadV1 payload, String messageId, String type, Object version)
```

Orden: `messageId` (UUID canónico, misma regla que Perfil) → `type` → `version` (`Integer` igual a 1; un `String "1"` no vale) → `payload != null` (si no, `PAYLOAD_INVALID_FORMAT`) → `validator.validate(payload)`: si hay violaciones, `PAYLOAD_CONTRACT_VIOLATION` con la ruta de la **primera en orden alfabético** de `propertyPath` traducida a los nombres del contrato (mapa fijo inglés→contrato, por ejemplo `professionalContext.name` → `contextoProfesional.nombre`) → `Instant.parse(updatedAt)` y `Instant.parse(archivedAt)` si no es nulo (`DateTimeParseException` → `PAYLOAD_CONTRACT_VIOLATION` con su ruta) → arma `ProfileReplica` serializando `professionalContext` y `aspiredRoles` con el `JsonMapper` (salen con los nombres del contrato gracias a `@JsonProperty`).

`ContractViolation`: `final class ContractViolation extends RuntimeException` de paquete, con `reason()` y `field()`; sin mensaje con datos.

**Consumidor** `ProfileUpdatedListener` (`@Component`):

```java
@RabbitListener(queues = ProfileEventsRabbitConfig.QUEUE, errorHandler = "profileEventsErrorHandler")
public void onProfileUpdated(ProfileUpdatedPayloadV1 payload,
                             @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
                             @Header(name = AmqpHeaders.TYPE, required = false) String type,
                             @Header(name = "x-event-version", required = false) Object version,
                             @Header(name = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {
    // requestId en el MDC durante el mensaje (patrón ^[A-Za-z0-9._-]{1,64}$; si no cumple, el messageId saneado)
    try {
        var outcome = service.apply(reader.read(payload, messageId, type, version));
        if (outcome == ReplicationOutcome.OWNER_MISMATCH) throw reject(messageId, "PROFILE_OWNER_MISMATCH", null);
    } catch (ContractViolation violation) {
        throw reject(messageId, violation.reason(), violation.field());
    } finally {
        MDC.remove("requestId");
    }
}
```

`reject(...)` registra una vez en `WARN`: «Evento del perfil rechazado hacia la cola de fallidos [cola={}, messageId={}, razón={}, campo={}]» (campo `-` si no hay) y devuelve `new AmqpRejectAndDontRequeueException(reason)`.

**Manejador y recuperador:** copiar `AccountEventsErrorHandler` → `ProfileEventsErrorHandler` (`@Component("profileEventsErrorHandler")`, textos «Evento del perfil…») y `AccountEventsRecoverer` → `ProfileEventsRecoverer` (registra el `MessageRecoverer` del reintento: en Spring Boot 4, bean `RabbitListenerRetrySettingsCustomizer` que fija el recuperador si la autoconfiguración no toma el bean `MessageRecoverer` por sí sola; comprobarlo con `…_whenDatabaseKeepsFailing` de T5), `EventLogValues` y `RedactedException` (clase de paquete en `consumer`). Mismas razones y mismos niveles de log.

**Topología** `infrastructure/messaging/config/ProfileEventsRabbitConfig`:

| Constante | Valor |
|---|---|
| `PROFILE_EXCHANGE` | `perfil.events` (`TopicExchange(…, true, false)`) |
| `ROUTING_KEY` | `perfil.profesional-actualizado` |
| `QUEUE` | `entrevista.perfil-profesional-actualizado` (`QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey(QUEUE)`) |
| `DEAD_LETTER_EXCHANGE` | `entrevista.dlx` (`DirectExchange(…, true, false)`) |
| `DEAD_LETTER_QUEUE` | `entrevista.perfil-profesional-actualizado.dlq` (durable) |

Beans: los dos exchanges, las dos colas, los dos enlaces y `JacksonJsonMessageConverter` con `setAlwaysConvertToInferredType(true)`. `ProfileEventsRetryConfig`: `RabbitListenerRetrySettingsCustomizer` con `setExceptionExcludes(List.of(AmqpRejectAndDontRequeueException.class))`, como Perfil.

**Pruebas unitarias**

| Clase | Pruebas (nombres exactos) |
|---|---|
| `ProfileUpdatedEventReaderTest` | Todas las de la spec §8 con sus valores literales, más `read_shouldBuildReplica_whenValid` (perfil `7c9e…`, dueño `6f1d…`, `COMPLETED`, `updatedAt = 2026-10-15T12:00:00.123Z`; el JSON guardado del contexto no contiene `embedding`), `read_shouldIgnoreUnknownFields_whenPresent`, `read_shouldKeepTextLiteral_whenHostile`, `read_shouldReject_whenBodyHuge`, `read_shouldReportFirstFieldAlphabetically_whenSeveralInvalid` (`nombre` y `resumen` vacíos → campo `contextoProfesional.nombre`) |
| `TextLengthValidatorTest` | `null` válido; `""` y `"   "` inválidos con `min = 1`; 255 × `"😀"` válido con `max = 255`; 256 × `"é"` inválido; `"é"` cuenta 2 |
| `ProfileUpdatedListenerTest` (servicio y lector simulados) | `onProfileUpdated_shouldApply_whenValid`; `…_shouldReject_whenContractViolation` (la excepción es `AmqpRejectAndDontRequeueException` con la razón; un `WARN`); `…_shouldReject_whenOwnerMismatch`; `…_shouldPropagate_whenServiceFailsTechnically` (`DataAccessResourceFailureException` sale tal cual, sin `WARN`); `…_shouldSetAndClearRequestId` (MDC `req-123` durante, nulo después, también si lanza); `…_shouldUseMessageIdAsRequestId_whenCorrelationInvalid` (`"a b"`); `logs_shouldNotContainPersonalData` |
| `ProfileEventsErrorHandlerTest` | las 5 de `PAYLOAD_INVALID_FORMAT` de §8; relanza sin tocar un rechazo ya hecho; relanza un fallo técnico con un `WARN` sin traza |
| `ProfileEventsRecovererTest` | fallo técnico → un `ERROR` con traza sin mensajes (el texto `secreto` de la excepción no aparece) y rechazo sin reencolar; rechazo por contrato → sin `ERROR` |
| `EventLogValuesTest` | `null` → `-`; 64 válidos → igual; 65 → `invalid`; `"a\nb"` → `invalid` |
| `ProfileEventsRabbitConfigTest` | `converter_shouldIgnoreTypeIdHeader` (mensaje con `__TypeId__ = java.lang.ProcessBuilder` se convierte al tipo del parámetro) |

**Commit:** `CM-209 | feat(mensajeria): consumidor de perfil.profesional-actualizado con cola de fallidos`.

---

## T5 · Integración contra PostgreSQL y RabbitMQ reales, y contrato

Cabecera común: `@SpringBootTest(properties = {"cameia.llm.model=modelo-de-prueba"})`, `@Testcontainers(disabledWithoutDocker = true)`, contenedor `postgres:16-alpine` con `@ServiceConnection`. En las pruebas de esta primera tabla el consumidor de RabbitMQ queda apagado (Surefire fija `auto-startup=false`) y el mensaje se entrega llamando directamente a `ProfileUpdatedListener` con un `Message` armado (cuerpo JSON del evento y cabeceras `message_id`, `correlation_id`). Las pruebas de la segunda tabla usan un RabbitMQ real.

| Clase y prueba | Qué hace | Esperado |
|---|---|---|
| `ProfileReplicationIntegrationTest.replication_shouldStoreCopy_whenValidEvent` | entrega `VALID_BODY` | `findById` devuelve la copia con `COMPLETED`; `evento_procesado` con el id |
| `…replication_shouldApplyOnce_when20Redeliveries` | el mismo mensaje 20 veces | 1 fila en cada tabla; `fecha_replicacion` igual a la del primero |
| `…replication_shouldKeepNewest_whenOlderArrivesLater` | primero `.200Z` con `nombre = "Nuevo"`, después `.100Z` con `nombre = "Viejo"` | `nombre = "Nuevo"` |
| `…replication_shouldReject_whenOwnerChanges` | copia de `uid-a`; mensaje nuevo del mismo perfil con `uid-b` | el listener lanza la excepción de rechazo definida en T4 (la que `ProfileEventsErrorHandlerTest` envía a la cola de fallidos); dueño sigue `uid-a` |
| `…replication_shouldRollBackAndApplyOnce_whenDatabaseFailsOnce` | `@MockitoSpyBean ProfileReplicaRepository` que lanza `DataAccessResourceFailureException("caida")` la primera vez; se entrega el mensaje dos veces seguidas | la primera entrega lanza y no deja filas parciales; la segunda guarda la copia y 1 fila en `evento_procesado` |
| `ProfileEventsContextTest.context_shouldStart_whenBrokerUnavailable` | contexto con `spring.rabbitmq.addresses=amqp://localhost:1` y `auto-startup=true` | el contexto carga; `GET /health` 200 |
| `ProfileUpdatedContractTest.example_shouldBeValid_whenReadFromSchema` | lee `docs/eventos/perfil-profesional-actualizado-v1.schema.json`, toma `examples[0]`, lo convierte a `ProfileUpdatedPayloadV1` y lo valida | 0 violaciones |
| `ProfileUpdatedContractTest.limits_shouldMatchSchema` | compara `maxLength`, `minItems`, `maxItems` y los `enum` del esquema con los de las anotaciones del payload (por reflexión) | iguales |

**Pruebas con RabbitMQ real (D-RPL-04, opción A).** Clase base `ProfileEventsBrokerSupport` (paquete `infrastructure.messaging`, `abstract`, `@SpringBootTest(properties = {"cameia.llm.model=modelo-de-prueba", "spring.rabbitmq.listener.simple.auto-startup=true"})`). Copia el patrón de `MessagingIntegrationSupport` de Perfil: PostgreSQL (`postgres:16-alpine`) y un `GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management-alpine")).withExposedPorts(5672, 15672).waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))`, ambos arrancados en un bloque `static` (no con `@Container`, para que el contexto reutilizado entre clases nunca apunte a un contenedor detenido) y las propiedades `spring.rabbitmq.host`, `port`, `username = guest`, `password = guest` por `@DynamicPropertySource`. Inyecta `RabbitTemplate`, `RabbitAdmin` y `JdbcTemplate`; las esperas con Awaitility (viene con `spring-boot-starter-test`), máximo 15 s. Sin artefacto nuevo en el `pom.xml`. Cada prueba vacía las dos colas y las tablas antes de empezar.

| Clase y prueba | Qué hace | Esperado |
|---|---|---|
| `ProfileEventsTopologyTest.topology_shouldDeclareContractNames_whenStarted` | arranque | existen la cola `entrevista.perfil-profesional-actualizado`, su cola `….dlq`, el exchange `perfil.events` y el `entrevista.dlx` (`getQueueInfo` no nulo) |
| `…topology_shouldRejectDifferentArguments_whenQueueIsRedeclared` | vuelve a declarar la cola con sus argumentos reales y después sin `x-dead-letter-exchange` | con los reales, sin error; sin ellos, `PRECONDITION_FAILED` (patrón de `AccountEventsTopologyIT` de Perfil) |
| `ProfileEventsDeadLetterTest.deadLetter_shouldRouteToDlq_whenContractViolated` | publica en `perfil.events` con clave `perfil.profesional-actualizado` un mensaje con `x-event-version = 2` | 1 mensaje en `….dlq` con la razón `EVENT_VERSION_UNSUPPORTED`, 0 en la cola principal, 1 sola entrega (sin reintentos), 0 filas |
| `…deadLetter_shouldRouteToDlq_whenDatabaseFailsThreeTimes` | `@MockitoSpyBean ProfileReplicaRepository` que siempre lanza `DataAccessResourceFailureException("caida")`; publica un mensaje válido | 3 intentos (esperas de 1 s y 2 s), después 1 mensaje en `….dlq`, 0 filas en cada tabla, 1 línea `ERROR` con la cola y el `message_id` |
| `…deadLetter_shouldRouteToDlq_whenOwnerChanges` | copia de `uid-a`; publica el mismo perfil con `uid-b` | 1 mensaje en `….dlq` con la razón `PROFILE_OWNER_MISMATCH`, sin reintentos; dueño sigue `uid-a` |
| `ProfileEventsAckTest.ack_shouldHappenAfterCommit_whenMessageApplied` | el spy bloquea la transacción con un `CountDownLatch` antes del commit; publica un mensaje válido | mientras está bloqueada, la API de administración (`GET /api/queues/%2F/<cola>`, usuario `guest`) da `messages_unacknowledged = 1` y 0 filas; al liberar, 0 sin confirmar y 1 fila |
| `…ack_shouldNotConfirm_whenTransactionRollsBack` | el spy lanza una vez después de escribir; publica un mensaje válido | el mensaje no se pierde: se reentrega, el segundo intento deja 1 fila en cada tabla y 0 mensajes sin confirmar |
| `ProfileReplicationBrokerTest.replication_shouldStoreCopy_whenPublishedToExchange` | publica un mensaje válido en `perfil.events` con la clave del contrato | en menos de 15 s, la copia con `COMPLETED` y 1 fila en `evento_procesado` |
| `…replication_shouldApplyOnce_whenSameMessagePublishedTwice` | publica dos veces el mismo `message_id` | 1 fila en cada tabla, 2 mensajes consumidos, 0 en la cola principal |

**Trampas:** la API de administración refresca sus cifras cada unos segundos, así que `messages_unacknowledged` se espera con Awaitility y no se lee una sola vez; `getQueueInfo` devuelve solo los mensajes listos, no los pendientes de confirmar; el estado de los contenedores se comparte entre clases, así que la limpieza inicial de colas es obligatoria. Si Docker no está encendido estas pruebas se omiten, y eso **no** es verde (T0).

La verificación manual de T6 con Postman (carpeta «Réplica del perfil (RabbitMQ)») y, al desplegar, contra el broker del entorno, queda como complemento de estas pruebas.

**Trampa:** la copia del esquema debe ser idéntica a la de Perfil (`git show <sha-de-perfil>:docs/eventos/perfil-profesional-actualizado-v1.schema.json > docs/eventos/...`); anotar en el documento del consumidor el SHA de Perfil de donde salió.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="ProfileReplicationIntegrationTest,ProfileEventsContextTest,ProfileUpdatedContractTest,ProfileEventsTopologyTest,ProfileEventsDeadLetterTest,ProfileEventsAckTest,ProfileReplicationBrokerTest"` → ejecutadas (no `Skipped`) y en verde.

**Commit:** `CM-209 | test(mensajeria): réplica e idempotencia contra PostgreSQL real y contrato del evento`.

---

## T6 · Documentación y Postman

1. `docs/eventos/consumo-perfil-profesional-actualizado.md`: qué consume y de dónde (exchange, clave, cola, cola de fallidos), qué valida (tabla de §8 resumida), las razones de rechazo (§10), idempotencia y orden, qué guarda y qué no (sin `embedding`, sin campos desconocidos), privacidad (qué nunca se registra), cómo reprocesar la cola de fallidos (pala de RabbitMQ de `….dlq` a la cola principal tras corregir la causa; el reproceso es idempotente), el SHA de Perfil del esquema copiado y que la versión 2 llegaría en paralelo.
2. `docs/eventos/perfil-profesional-actualizado-v1.schema.json`: copia literal (T5).
3. `docs/adr/0008-replica-del-perfil-por-evento.md` (estado, contexto, decisión, consecuencias, alternativas): D-RPL-01 a D-RPL-07 de la spec.
4. `docs/bitacora-ia/06_replica-del-perfil_prompt.md` + fila en el README (número siguiente libre).
5. `CLAUDE.md`: §2 (árbol: `infrastructure.messaging` existe), §5 (tabla `contexto_profesional_replica` y `evento_procesado`, `V2`), §6 (logs del consumidor sin datos del perfil), §10 (quitar «`docs/eventos/`, mensajería» de pendientes).
6. Postman: en el entorno `local`, variable `rabbitManagementUrl = http://localhost:15673`; carpeta «Réplica del perfil (RabbitMQ)» con las 7 peticiones de la spec §13 y las 2 de §25.9, 9 en total (autenticación básica `guest`/`guest` a nivel de carpeta).

**Comando Newman:** `docker compose --profile mensajeria up -d`; jar con `SERVER_PORT=8083` y `SPRING_RABBITMQ_ADDRESSES=amqp://guest:guest@localhost:5673`; `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --folder "Réplica del perfil (RabbitMQ)" --reporters cli,junit --reporter-junit-export ../../ejecucion/evidencia/CM-209/newman.xml` → 9/9.

**Commit:** `CM-209 | docs(eventos): consumo del evento del perfil, ADR 0008 y Postman`.

---

## Criterio de catálogo de roles (spec §25): tarjetas T2b a T6b y P1 a P3

Se ejecutan **después de T6** y antes de T7, salvo P1 a P3, que son otro repositorio y otro PR (se hacen primero y se fusionan antes). Valores, razones y nombres: spec §25. Todas las reglas de la cabecera de este archivo siguen vigentes.

### P1 a P3 · Perfil (repositorio `cameia-perfil`, rama `CM-209-catalogo-roles-evento` desde `origin/develop`)

**P0 (previo):** `git fetch`; `git status` limpio; `ls src/main/resources/db/migration` (la última debe ser `V9` y esta migración es `V10`; si ya existe una `V10` de otra tarea, detenerse y avisar a Paula: CM-296 usa `V11`, spec §25.4); `ls docs/adr docs/bitacora-ia` para los números libres. Línea base: `./mvnw.cmd -B -ntp clean verify` en verde y anotar el número de pruebas.

**P1 · Dominio y publicación.** Crear `ProfessionalRoleNames`, `RoleCatalogUpdated` (rechaza `roles` vacío o con más de 200; `AGGREGATE_ID = new UUID(0L, 0L)`), `RoleCatalogEventsAppService.announceCatalog()` (si `OutboxRepository.existsPendingOfType(RoleCatalogUpdated.TYPE)` → no hace nada; si no, lee `findAllWithBothLanguages()`, guarda con `saveRoleCatalog` y llama a `relayAfterCommit`), `RoleCatalogPayloadV1` y `RoleCatalogStartupAnnouncer`. Copiar la forma de `ProfileEventsAppService` y `ProfileUpdatedPayloadV1`. `correlationId = "perfil-catalogo-" + eventId.toString().substring(0, 8)`. `nombreEn` de respaldo = `nameEs`. En `application.properties` agregar `perfil.events.announce-role-catalog-on-startup=true` y ponerlo en `false` en las pruebas que arrancan el contexto completo salvo la de extremo a extremo (revisar `src/test/resources` y `MessagingIntegrationSupport`).
**Trampas:** el anunciador no puede romper el arranque (captura la excepción, `ERROR` con la clase de la causa, sin nombres de rol; el servicio toma antes un candado de la base, `tryLockAnnouncement`, para que dos instancias no anoten dos instantáneas); la carga nunca se escribe en el log; usar `String.codePointCount` para los límites de texto si se valida en el contrato.
**Pruebas (valores literales):** `RoleCatalogUpdatedTest` (0 roles → excepción; 200 pasa; 201 falla), `RoleCatalogPayloadV1Test` (`VALID_CATALOG_BODY` de la spec §25.2 serializa igual), `RoleCatalogEventsAppServiceTest` (hay uno pendiente → 0 escrituras; `nombre_en` nulo → `nombreEn` = español), `RoleCatalogStartupAnnouncerTest` (excepción del servicio → no se propaga).
**Commit:** `CM-209 | feat(eventos): publicar el catálogo de roles al arrancar`.

**P2 · Cola y migración.** `RabbitConfig`: constantes `ROLE_CATALOG_KEY = RoleCatalogUpdated.TYPE` e `INTERVIEW_ROLE_CATALOG_QUEUE = "entrevista.catalogo-roles-actualizado"`; beans `Queue` (durable, DLX `entrevista.dlx`, clave de fallidos igual al nombre de la cola) y `Binding` (clave `perfil.catalogo-roles-actualizado`). `V10__evento_catalogo_roles.sql`: `ALTER TABLE evento_saliente DROP CONSTRAINT ck_evento_saliente_tipo, ADD CONSTRAINT ck_evento_saliente_tipo CHECK (tipo IN ('perfil.profesional-actualizado', 'perfil.catalogo-roles-actualizado'));` y `COMMENT ON COLUMN evento_saliente.tipo`. Métodos nuevos en `OutboxRepositoryAdapter` y `ProfessionalRoleRepositoryAdapter` (consulta sin filtro de idioma que devuelve `nombre`, `nombre_en` y `estado`).
**Pruebas:** `RabbitConfigTest` (argumentos exactos de la cola nueva), `OutboxRepositoryAdapterIT` (PostgreSQL real: guarda el tipo nuevo; un tipo `otro.evento` viola `ck_evento_saliente_tipo`; `existsPendingOfType` verdadero y falso), `ProfessionalRoleRepositoryAdapterIT` (devuelve 46 roles con ambos idiomas).
**Commit:** `CM-209 | feat(datos): cola y restricción del evento del catálogo de roles`.

**P3 · Extremo a extremo y documentación.** `RoleCatalogEventsEndToEndIT` en el estilo de `ProfileEventsEndToEndIT` (broker real que Perfil ya usa en sus pruebas): arranque con la bandera en `true` → una fila publicada → la cola `entrevista.catalogo-roles-actualizado` recibe 1 mensaje con 46 roles, `message_id` igual al de la fila, `type`, `x-event-version = 1`, `correlation_id` según §25.2; un segundo arranque con la fila ya publicada agrega una fila nueva (no hay pendiente). `RoleCatalogEventContractTest` (cuerpo generado contra el esquema). Documentos: `docs/eventos/perfil-catalogo-roles-actualizado-v1.md` y `.schema.json` (con `examples[0]` = `VALID_CATALOG_BODY`, `maxItems = 200`, `maxLength = 255`, `enum` de `estado`), ADR siguiente libre, bitácora siguiente libre, y la fila en `docs/eventos/` si hay índice.
**Comandos:** `./mvnw.cmd -B -ntp clean verify`, cobertura de lo nuevo ≥ 90 % de líneas y de ramas.
**Commit:** `CM-209 | test(eventos): publicación del catálogo de roles contra el broker real y contrato`.

### T2b · Migración `V3__catalogo_roles_profesionales.sql` (Entrevista)

Crear la tabla de la spec §25.4 con el esquema `microentrevista` calificado, las restricciones con nombre (`pk_rol_profesional`, `ck_rol_profesional_nombre_es`, `ck_rol_profesional_nombre_en`, `ck_rol_profesional_estado`), el reemplazo de `ck_evento_procesado_tipo` (`DROP CONSTRAINT` y `ADD CONSTRAINT` con los dos tipos) y los `COMMENT ON` en español sin tildes.
**Prueba:** `RoleCatalogSchemaMigrationTest` (cabecera de T5): inserta `nombre_es = '   '` → viola `ck_rol_profesional_nombre_es`; `estado = 'DELETED'` → viola `ck_rol_profesional_estado`; `nombre_es` de 256 caracteres → falla por largo, de 255 con `"😀"` pasa; `evento_procesado.tipo = 'otro'` → viola el `CHECK`; el tipo nuevo pasa.
**Trampa:** el `ADD CONSTRAINT` del `CHECK` falla si hay filas con otro tipo; hoy solo existe el tipo del perfil.
**Commit:** `CM-209 | feat(datos): tabla de la copia del catálogo de roles`.

### T3b · Dominio, caso de uso y persistencia (Entrevista)

Crear `RoleStatus`, `ProfessionalRoleReplica` (el constructor rechaza nombre vacío o de más de 255 puntos de código), `RoleCatalogReplicaRepository`, `ReplicateRoleCatalogCommand`, `ReplicateRoleCatalogService` (en una transacción: `ProcessedMessageInbox.registerIfAbsent(messageId, "perfil.catalogo-roles-actualizado", now)` → si ya estaba, devuelve `DUPLICATE_IGNORED`; si no, `upsertSnapshot(...)`; devuelve `APPLIED` si cambió al menos una fila y `STALE_IGNORED` si ninguna), `ProfessionalRoleEntity`, `ProfessionalRoleJpaRepository` (consulta nativa de §25.4 con el esquema calificado y el parámetro `roles` como `String` JSON) y `RoleCatalogReplicaRepositoryAdapter` (arma el JSON con Jackson desde la lista).
**Pruebas:** `ReplicateRoleCatalogServiceTest` (mocks: inbox repetido → no llama al repositorio; 0 filas → `STALE_IGNORED`), `ProfessionalRoleReplicaTest` (255 pasa, 256 falla, `"   "` falla), `RoleCatalogReplicaRepositoryAdapterTest` (PostgreSQL real: E-01, E-03, E-04, E-06, E-07 con dos hilos y `CountDownLatch`, y 200 roles).
**Trampa:** la sentencia usa `CAST(:roles AS jsonb)`; con JPA hay que escribirla como `CAST(:roles AS jsonb)` y no con `::jsonb` (el `:` se interpreta como parámetro).
**Commit:** `CM-209 | feat(dominio): copia del catálogo de roles con su persistencia`.

### T4b · Consumidor (Entrevista)

Crear `RoleCatalogRabbitConfig` (cola, cola de fallidos y enlaces de REQ-RPL-26, con los mismos argumentos que Perfil), `RoleCatalogPayloadV1` (con `RoleItem`; `@TextLength` de T4), `RoleCatalogEventReader` (la matriz de §25.3; `CATALOG_ROLE_DUPLICATED` con la ruta del segundo repetido) y `RoleCatalogListener` (`@RabbitListener(queues = "entrevista.catalogo-roles-actualizado")`). Reutilizar `ProfileEventsRetryConfig`, `ProfileEventsErrorHandler`, `ProfileEventsRecoverer`, `ContractViolation`, `EventLogValues`; si alguno fija el nombre de la cola del perfil, parametrizarlo con el cambio mínimo y mantener verdes sus pruebas.
**Pruebas:** `RoleCatalogEventReaderTest` (una prueba por fila de §25.3, con `VALID_CATALOG_BODY` modificado en un campo), `RoleCatalogRabbitConfigTest`, `RoleCatalogListenerTest` (rechazo sin reintento; fallo técnico → relanza; `MDC` limpio al terminar), `RoleCatalogLoggingTest.logs_shouldNotContainRoleNames`.
**Commit:** `CM-209 | feat(mensajeria): consumidor del catálogo de roles con cola de fallidos`.

### T5b · Integración contra PostgreSQL y RabbitMQ reales, y contrato (Entrevista)

Con la cabecera de T5: `RoleCatalogReplicationIntegrationTest` (E-01 a E-08 entregando el mensaje directo al listener; E-08 con `@MockitoSpyBean` como en T5) y `RoleCatalogContractTest` (copia del esquema de Perfil: `examples[0]` válido y `maxItems`, `maxLength` y `enum` iguales a los del payload). Con la clase base `ProfileEventsBrokerSupport` de T5: `RoleCatalogBrokerTest` (D-RPL-04, opción A) con `topology_shouldDeclareCatalogQueues_whenStarted` (cola `entrevista.catalogo-roles-actualizado`, su `….dlq` y el enlace con la clave `perfil.catalogo-roles-actualizado`), `deadLetter_shouldRouteToDlq_whenContractViolated` (un mensaje con 201 roles, uno más que `maxItems = 200`, llega a la `….dlq` sin reintentos y 0 filas) y `replication_shouldStoreAllRolesAndAck_whenPublished` (un mensaje válido con 46 roles: 46 filas en `rol_profesional`, 0 mensajes sin confirmar).
**Trampa:** copiar el esquema de Perfil con `git show <sha>:docs/eventos/perfil-catalogo-roles-actualizado-v1.schema.json` y anotar el SHA.
**Comando:** `./mvnw.cmd -B -ntp test -Dtest="RoleCatalog*"` → ejecutadas (no `Skipped`) y en verde.
**Commit:** `CM-209 | test(mensajeria): catálogo de roles contra PostgreSQL real y contrato`.

### T6b · Documentación y Postman (Entrevista)

`docs/eventos/consumo-perfil-catalogo-roles-actualizado.md` (como el de T6: cola, validación, razones, idempotencia y orden, qué no se borra, cómo reprocesar la cola de fallidos), sección nueva en el ADR 0008 con D-RPL-08 a D-RPL-12, `CLAUDE.md` §5 (tabla `rol_profesional`, `V3`) y las dos peticiones de Postman de §25.9 (9 en total en la carpeta).
**Commit:** `CM-209 | docs(eventos): consumo del catálogo de roles y Postman`.

---

## T7 · Verificación única sobre la punta

1. `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`; ninguna clase de Testcontainers `Skipped`.
2. `jacoco.csv`: ≥ 90 % líneas y ramas en cada clase de la spec §6; razones de lo no cubierto.
3. Newman (T6) y colección completa.
4. Lista de verificación de la spec §22 fila por fila; tabla de atributos §12 con resultados reales en `ejecucion/evidencia/CM-209/resumen.md`, con la tabla R1 a R14.
5. Redactar para Paula la tarea de DevOps del hallazgo H-1 (título, descripción y fecha), sin crearla.
6. Push y PR en Draft según `CLAUDE.md` raíz §2 (solo con spec y plan aprobados en `ESTADO.md`). Explicar a Paula y detenerse.

## Resultados

| Tarjeta | Resultado real | Fecha |
|---|---|---|
| T0 | Hecha. Línea base `clean verify` verde. Contrato publicado por Perfil comparado con la spec: sin diferencias | 22-oct-2026 |
| T1 | Hecha, commit `b1435d8` (RabbitMQ, reintentos y broker local) | 22-oct-2026 |
| T2 | Hecha, commit `8edb07c` (`V2__replica_perfil_profesional.sql`, 31 pruebas de esquema) | 22-oct-2026 |
| T3 | Hecha, commit `36b4f5a`. 33 pruebas del módulo verdes (dominio, caso de uso, adaptadores con PostgreSQL real, concurrencia de dos hilos) | 23-oct-2026 |
| T4 | Hecha, commit `478a0a8`. 160 pruebas del módulo verdes. Desviaciones: (1) se rechaza el carácter nulo en todo texto (`PAYLOAD_CONTRACT_VIOLATION`), porque PostgreSQL no lo guarda y sin la regla terminaría en 3 reintentos y la cola de fallidos; Perfil no puede publicarlo, solo un mensaje forjado; (2) el dueño se valida con `FirebaseUid.required` (también rechaza caracteres de formato y separadores) y no con un patrón de control; (3) el registro del rechazo por cambio de dueño lleva `perfilId`; (4) `Instant.parse` acepta `24:00:00` como fin de día y no se rechaza; (5) los ejes de lectura del convertidor, exchanges y colas de fallidos viven en `ProfileEventsRabbitConfig` y el catálogo de roles los reutiliza | 23-oct-2026 |
| T5 | Hecha, commit `01baff3` (tras el autosquash). Pruebas con PostgreSQL y RabbitMQ reales. Desviaciones: (1) un decorador de prueba (`ReplicaRepositoryProbe`) instalado por un `BeanPostProcessor` reemplaza a `@MockitoSpyBean`, que no funciona sobre proxies transaccionales; (2) las clases con RabbitMQ real se llaman `*Test`, porque Surefire solo ejecuta `*Test`; (3) el lector acepta una versión entera de cualquier tipo del protocolo (`x-event-version` llega como `Long` desde RabbitMQ); (4) el consumidor recibe los encabezados con `@Headers`; (5) `InterviewSchemaMigrationTest` listaba 8 tablas y fallaba con la migración `V2`; se corrigió con un `fixup` de T2 | 23-oct-2026 |
| T6 | Hecha, commit `c66d637` (documento del consumo, ADR 0008 y 7 peticiones de Postman) | 23-oct-2026 |
| T2b | Hecha, commit `264681b` (`V3__catalogo_roles_profesionales.sql`, 20 pruebas de esquema; el `CHECK` de `evento_procesado` admite los dos tipos) | 23-oct-2026 |
| T3b | Hecha, commit `a3963b1` (`ProfessionalRoleReplica`, `RoleCatalogReplicaRepository`, caso de uso y adaptador con una sola sentencia nativa) | 23-oct-2026 |
| T4b | Hecha, commit `fc83e33`. 51 pruebas del lector, oyente, topología y registros sin nombres de roles. Se agregó la razón `CATALOG_ROLE_DUPLICATED` | 23-oct-2026 |
| T5b | Hecha, commit `c2c919a`. 126 pruebas de los módulos tocados verdes, ninguna omitida. Desviación: la lógica de comparar un esquema JSON con el payload se extrajo de `ProfileUpdatedContractTest` a `EventSchemaComparator` y la usan las dos pruebas de contrato; `ProfileEventsBrokerSupport` limpia también las colas y la tabla de roles | 23-oct-2026 |
| T6b | Hecha, commit `3d4029d` (documento del consumo del catálogo, sección de catálogo en el ADR 0008, `CLAUDE.md` y 2 peticiones de Postman: 9 en la carpeta) | 23-oct-2026 |
| T7 | Hecha. `clean verify`: 894 pruebas, 0 fallos, 1 omitida (`GeminiLanguageModelLiveTest`, manual por diseño). Newman: 23 peticiones y 94 verificaciones, 0 fallos, contra el jar empaquetado con PostgreSQL 16 y RabbitMQ 3.13 reales; un mensaje real de 46 roles dejó 46 filas y 1 registro en la bandeja. Evidencia en `ejecucion/evidencia/CM-209/` | 23-oct-2026 |
| P1 (Perfil) | Hecha en `cameia-perfil`, rama `CM-209-catalogo-roles-evento`, commit `38c2dc9`. Desviación: los métodos nuevos de los dos adaptadores y de los puertos se escribieron aquí y no en P2, para que el commit compile. `RoleCatalogEventsAppService`, `RoleCatalogPayloadV1`, `RoleCatalogStartupAnnouncer` con 100 % de líneas y ramas. Desviación añadida: el perfil `profile-events-relay` pone `announce-role-catalog-on-startup: false`, porque el Job arranca la aplicación cada 5 minutos y habría anotado el catálogo en cada corrida | 22-oct-2026 |
| P2 (Perfil) | Hecha, commit `fbfd2db`. Migración `V10__evento_catalogo_roles.sql` (la nota de la spec §25.4 que dice que CM-296 usa `V10` queda superada: CM-296 usa `V11`). `nombre_en` es `NOT NULL` en la tabla (la spec decía que admitía nulo); el respaldo al español se conserva | 22-oct-2026 |
| P3 (Perfil) | Hecha, commit `41b6eb4`. `clean verify`: `BUILD SUCCESS`, 1071 unitarias + 398 de integración, 0 fallos. Aplicación empaquetada con PostgreSQL y RabbitMQ reales: 1 mensaje con 46 roles al arrancar, segundo arranque agrega otro, broker caído deja 1 sola pendiente y el Job la publica sin anotar otra, 655 peticiones de Newman con 0 fallos. Evidencia y hallazgos en `ejecucion/evidencia/CM-209-perfil/resumen.md` | 22-oct-2026 |
