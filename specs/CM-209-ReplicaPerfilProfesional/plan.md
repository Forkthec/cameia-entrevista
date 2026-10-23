# Plan · CM-209 Réplica del Perfil Profesional en Entrevista

Spec: `spec.md`. Estado: **LISTA PARA EJECUTAR** con CM-311 fusionada.

## 1. Enfoque

Un commit por capacidad, cada uno compila y deja la suite en verde:

1. **Base técnica:** `spring-boot-starter-amqp`, propiedades `spring.rabbitmq.*` como Perfil, Surefire con el consumidor apagado por defecto, broker local en compose.
2. **Datos:** `V2__replica_perfil_profesional.sql` con su prueba de esquema.
3. **Dominio y persistencia:** `ProfileReplica`, puertos, adaptadores con upsert condicionado e Inbox.
4. **Caso de uso:** `ReplicateProfileService` con sus cuatro resultados.
5. **Mensajería:** contrato del lado del consumidor (payload y validación), lector, consumidor, manejador de errores, recuperador, topología y reintentos.
6. **Integración real:** topología y réplica de punta a punta con PostgreSQL y RabbitMQ en Testcontainers.
7. **Documentación y Postman.**

## 2. Patrones y por qué

| Patrón | Dónde | Problema presente |
|---|---|---|
| Inbox | `evento_procesado` + `ProcessedMessageInbox` | Entrega «al menos una vez» (contrato) |
| Upsert condicionado en una sentencia | `INSERT … ON CONFLICT DO UPDATE … WHERE actualizado_en <= EXCLUDED.actualizado_en AND firebase_uid = EXCLUDED.firebase_uid` | Entrega «sin orden» y varias instancias de Cloud Run: la base decide sin carreras |
| Puerto y adaptador | `ProfileReplicaRepository`, `ProcessedMessageInbox` | Dominio sin JPA (ArchUnit) |
| Manejador de errores y recuperador del consumidor | `ProfileEventsErrorHandler`, `ProfileEventsRecoverer` | Separar rechazo por contrato (sin reintentos) de fallo técnico (3 intentos), registrando cada cosa una vez — copia del patrón probado de Perfil |
| Validación declarativa del contrato | Bean Validation en los `record` del payload + `@TextLength` | Una regla por campo, igual que el esquema; prueba de contrato que compara límites |

## 3. Reutilización

| Se reutiliza | De dónde | Cómo |
|---|---|---|
| `AccountEventsErrorHandler`, `AccountEventsRecoverer`, `EventLogValues`, `AccountEventsRetryConfig`, `ProcessedMessageInbox` y su adaptador, `RabbitConfig.messageConverter` | `cameia-perfil` `1201943` | Se copian adaptados (no se importa código de otro servicio) |
| `RedactedException` (traza sin mensajes) | `cameia-perfil/domain/exception/RedactedException.java` | Se copia en `infrastructure/messaging/consumer` como clase de paquete (en Entrevista, `domain.exception` exige `BusinessException`) |
| Propiedades `spring.rabbitmq.*` | `cameia-perfil/src/main/resources/application.yml` líneas 47 a 67 | Mismos valores, en `application.properties` |
| Esquema JSON del evento | `cameia-perfil/docs/eventos/perfil-profesional-actualizado-v1.schema.json` | Copia literal en `docs/eventos/` de Entrevista |
| Arranque de pruebas con Testcontainers | CM-311 | Misma cabecera de PostgreSQL; las pruebas de broker agregan un `GenericContainer` de RabbitMQ (`rabbitmq:3.13-management-alpine`) como `MessagingIntegrationSupport` de Perfil (D-RPL-04, opción A) |

## 4. Archivos

### Se crean

```
src/main/resources/db/migration/V2__replica_perfil_profesional.sql
src/main/java/tech/cameia/entrevista/domain/model/{ProfileReplica, ReplicatedProfileStatus, ReplicationOutcome}.java
src/main/java/tech/cameia/entrevista/domain/port/{ProfileReplicaRepository, ProcessedMessageInbox}.java
src/main/java/tech/cameia/entrevista/application/command/ReplicateProfileCommand.java (+ package-info si falta)
src/main/java/tech/cameia/entrevista/application/service/ReplicateProfileService.java
src/main/java/tech/cameia/entrevista/infrastructure/persistence/entity/{ProfileReplicaEntity, ProcessedMessageEntity}.java
src/main/java/tech/cameia/entrevista/infrastructure/persistence/repository/{ProfileReplicaJpaRepository, ProfileReplicaRepositoryAdapter, ProcessedMessageJpaRepository, ProcessedMessageInboxAdapter}.java
src/main/java/tech/cameia/entrevista/infrastructure/messaging/config/{ProfileEventsRabbitConfig, ProfileEventsRetryConfig}.java (+ package-info)
src/main/java/tech/cameia/entrevista/infrastructure/messaging/payload/{ProfileUpdatedPayloadV1, TextLength, TextLengthValidator}.java (+ package-info)
src/main/java/tech/cameia/entrevista/infrastructure/messaging/consumer/{ProfileUpdatedListener, ProfileUpdatedEventReader, ContractViolation, ProfileEventsErrorHandler, ProfileEventsRecoverer, EventLogValues, RedactedException}.java (+ package-info)
src/test/java/tech/cameia/entrevista/... (spec §14)
docs/eventos/consumo-perfil-profesional-actualizado.md
docs/eventos/perfil-profesional-actualizado-v1.schema.json (copia literal)
docs/adr/0008-replica-del-perfil-por-evento.md
docs/bitacora-ia/06_replica-del-perfil_prompt.md

# Criterio de catálogo de roles (spec §25)
src/main/resources/db/migration/V3__catalogo_roles_profesionales.sql
src/main/java/tech/cameia/entrevista/domain/model/{ProfessionalRoleReplica, RoleStatus}.java
src/main/java/tech/cameia/entrevista/domain/port/RoleCatalogReplicaRepository.java
src/main/java/tech/cameia/entrevista/application/{command/ReplicateRoleCatalogCommand, service/ReplicateRoleCatalogService}.java
src/main/java/tech/cameia/entrevista/infrastructure/persistence/{entity/ProfessionalRoleEntity, repository/ProfessionalRoleJpaRepository, repository/RoleCatalogReplicaRepositoryAdapter}.java
src/main/java/tech/cameia/entrevista/infrastructure/messaging/{config/RoleCatalogRabbitConfig, payload/RoleCatalogPayloadV1, consumer/{RoleCatalogListener, RoleCatalogEventReader}}.java
docs/eventos/consumo-perfil-catalogo-roles-actualizado.md
docs/eventos/perfil-catalogo-roles-actualizado-v1.schema.json (copia literal)

# En cameia-perfil (PR aparte, spec §25.8)
V10__evento_catalogo_roles.sql · RoleCatalogUpdated · ProfessionalRoleNames · RoleCatalogEventsAppService · RoleCatalogPayloadV1 · RoleCatalogStartupAnnouncer · RabbitConfig (cola y enlace) · docs/eventos/perfil-catalogo-roles-actualizado-v1.{md,schema.json}
```

### Se modifican

`pom.xml`, `src/main/resources/application.properties`, `docker-compose.yml`, `.env.example`, `postman/cameia-entrevista.postman_collection.json`, `postman/local.postman_environment.json`, `docs/bitacora-ia/README.md`, `CLAUDE.md` (secciones 2, 5, 6 y 10).

### No se tocan

`.github/**` (el despliegue ya monta `SPRING_RABBITMQ_ADDRESSES`; `--min-instances` es de DevOps, hallazgo H-1), `V1`, el manejador HTTP, `ErrorCode`, código de CM-312.

## 5. Orden, commits y ramas

| Tarjeta | Commit |
|---|---|
| T0 | — |
| T1 | `CM-209 \| build(mensajeria): RabbitMQ con reintentos y broker local como en Perfil` |
| T2 | `CM-209 \| feat(datos): tablas de la copia del perfil y de mensajes procesados` |
| T3 | `CM-209 \| feat(dominio): copia del perfil con su regla de uso y su persistencia` |
| T4 | `CM-209 \| feat(mensajeria): consumidor de perfil.profesional-actualizado con cola de fallidos` |
| T5 | `CM-209 \| test(mensajeria): topología, réplica e idempotencia contra RabbitMQ y PostgreSQL reales` |
| T6 | `CM-209 \| docs(eventos): consumo del evento del perfil, ADR 0008 y Postman` |
| T2b | `CM-209 \| feat(datos): tabla de la copia del catálogo de roles` |
| T3b | `CM-209 \| feat(dominio): copia del catálogo de roles con su persistencia` |
| T4b | `CM-209 \| feat(mensajeria): consumidor del catálogo de roles con cola de fallidos` |
| T5b | `CM-209 \| test(mensajeria): catálogo de roles contra PostgreSQL real y contrato` |
| T6b | `CM-209 \| docs(eventos): consumo del catálogo de roles y Postman` |
| T7 | — (verificación única) |
| P1 a P3 | En `cameia-perfil`, otro PR, se fusiona primero (commits en `tasks.md`) |

Rama `CM-209-replica-perfil-profesional` desde `origin/develop` con CM-311 fusionada (stack sobre CM-311 si no). Independiente de CM-204.

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| Arranque sin broker en las demás pruebas (ruido o espera) | Surefire fija `spring.rabbitmq.listener.simple.auto-startup=false`; las pruebas de mensajería lo encienden en `@SpringBootTest(properties)` |
| Consultas nativas sin el esquema `microentrevista` | Todas las tablas se escriben calificadas (`microentrevista.contexto_profesional_replica`); prueba de adaptador contra PostgreSQL real |
| Argumentos de la cola distintos a los de Perfil → `PRECONDITION_FAILED` | `ProfileEventsRabbitConfigTest` fija los mismos argumentos que Perfil; la comprobación con la cola creada primero por Perfil la reproduce `ProfileEventsTopologyIT` en un broker real, que vuelve a declarar la cola con otros argumentos y espera `PRECONDITION_FAILED` (D-RPL-04) |
| Perfil no declara la cola nueva → el broker devuelve el mensaje y queda pendiente para siempre | REQ-RPL-25: la declara también Perfil; `RabbitConfigTest` y `RoleCatalogEventsEndToEndIT` |
| Orden de migraciones de Perfil con la tarea de alinear el estándar | Decidido: esta tarea usa `V10` y CM-296 usa `V11` (spec §25.4) |
| `--min-instances=0` en el entorno | H-1 a DevOps; no bloquea el código |

## 7. Estimación

16 a 20 h (Jira: 4 h): 10 a 12 h de la copia del perfil y 6 a 8 h del catálogo de roles (spec §25.10).
