# Plan · CM-207 Crear la sesión de entrevista

Spec: `spec.md`. Estado: **LISTA PARA EJECUTAR** con CM-311, CM-204 y CM-209 fusionadas o en stack.

## 1. Enfoque

Un commit por capacidad, en orden de lectura; cada uno compila y deja la suite en verde.

1. **Datos:** `V4` con el `CHECK` de la operación anti-duplicados (la tabla de roles y su carga por evento son de el criterio de catálogo de roles de CM-209, `V3`).
2. **Dominio:** borrador, política de configuración (todas las causas a la vez), solicitud validada con su huella, `reconfigure` de la sesión, excepciones y códigos.
3. **Persistencia:** catálogo de roles y tres consultas nuevas de la sesión.
4. **Caso de uso:** creación o reutilización en una transacción, entrevista en curso, anti-duplicados con la carrera resuelta.
5. **API:** controlador, DTO, manejador del 422 con lista y las tres filas nuevas; OpenAPI.
6. **Integración, Postman y documentación.**

## 2. Patrones y por qué

| Patrón | Dónde | Problema presente |
|---|---|---|
| Policy con dependencias por constructor | `SessionConfigurationPolicy(PersonalizationCatalog, RoleCatalog)` | Reglas con datos que deben reportarse todas a la vez y probarse sin Spring |
| Command | `CreateSessionCommand` | El servicio no recibe el DTO HTTP |
| Puerto y adaptador | `RoleCatalog`, consultas nuevas del repositorio de sesión | Dominio sin JPA |
| Clave anti-duplicados con repetición de respuesta | `CreateSessionService` + `IdempotencyKeyRepository` (CM-311) | CA-4.4.17 y RT-06 |
| Transacción programática | `TransactionTemplate` en `CreateSessionService` | La carrera de la clave se resuelve fuera de la transacción revertida (REQ-SES-15); `@Transactional` en el mismo método no lo permite |

## 3. Reutilización

| Se reutiliza | De dónde |
|---|---|
| `InterviewSession`, `SessionConfiguration`, `InterviewSessionRepository`, `IdempotencyKeyRepository`, `IdempotencyKey`, `FirebaseUid`, manejador y su tabla | CM-311 |
| `PersonalizationCatalog`, `DomainPolicyConfiguration` | CM-204 |
| `ProfileReplicaRepository`, `ProfileReplica` | CM-209 |
| Catálogo de roles | Criterio de catálogo de roles de CM-209 (D-SES-02) |
| Forma del manejador de 422 con lista | `camposInvalidos` de CM-311/Cuentas |

## 4. Archivos

### Se crean

```
src/main/resources/db/migration/V4__operacion_crear_sesion.sql
src/main/java/tech/cameia/entrevista/domain/model/{SessionConfigurationDraft, FieldRejection, ValidatedSessionRequest}.java
src/main/java/tech/cameia/entrevista/domain/policy/SessionConfigurationPolicy.java
src/main/java/tech/cameia/entrevista/domain/port/RoleCatalog.java
src/main/java/tech/cameia/entrevista/domain/exception/{InvalidRequestException, ProfileSyncPendingException, ProfileNotFoundException, IdempotencyKeyConflictException}.java
src/main/java/tech/cameia/entrevista/application/command/CreateSessionCommand.java
src/main/java/tech/cameia/entrevista/application/service/{CreateSessionService, CreateSessionResult, OngoingSession}.java
src/main/java/tech/cameia/entrevista/infrastructure/persistence/repository/RoleCatalogAdapter.java (la entidad y el repositorio JPA de `rol_profesional` los crea CM-209)
src/main/java/tech/cameia/entrevista/presentation/controller/InterviewSessionController.java
src/main/java/tech/cameia/entrevista/presentation/dto/{CreateSessionRequest, CreateSessionResponse, SessionConfigurationResponse, OngoingSessionResponse}.java
src/test/java/tech/cameia/entrevista/... (spec §14)
docs/adr/0008-crear-sesion-y-anti-duplicados.md
docs/bitacora-ia/06_crear-sesion_prompt.md
```

### Se modifican

```
src/main/java/tech/cameia/entrevista/domain/model/InterviewSession.java        (+ reconfigure)
src/main/java/tech/cameia/entrevista/domain/port/InterviewSessionRepository.java (+ 3 métodos)
src/main/java/tech/cameia/entrevista/infrastructure/persistence/repository/InterviewSessionRepositoryAdapter.java y su JpaRepository
src/main/java/tech/cameia/entrevista/domain/exception/ErrorCode.java          (+ 19 al final)
src/main/java/tech/cameia/entrevista/presentation/advice/BusinessExceptionHandler.java (+ manejador 422, + 3 filas)
src/main/java/tech/cameia/entrevista/infrastructure/config/DomainPolicyConfiguration.java (+ policy, + Clock si falta)
src/test/java/tech/cameia/entrevista/domain/exception/ErrorCodeTest.java       (conjunto de códigos de campo)
docs/errores.md, postman/*, CLAUDE.md, docs/bitacora-ia/README.md
```

### No se tocan

`V1`, `V2`, enumerados de CM-311, consumidor de CM-209, `.github/**`.

## 5. Orden y commits

| Tarjeta | Commit |
|---|---|
| T0 | — |
| T1 | `CM-207 \| feat(datos): operación CREAR_SESION de la clave de idempotencia` |
| T2 | `CM-207 \| feat(dominio): validación total de la configuración de la sesión` |
| T3 | `CM-207 \| feat(persistencia): lectura del catálogo de roles y consultas de la sesión del usuario` |
| T4 | `CM-207 \| feat(aplicacion): crear o reutilizar la sesión con anti-duplicados` |
| T5 | `CM-207 \| feat(api): POST /api/v1/interviews/sessions con sus errores y su documentación` |
| T6 | `CM-207 \| test(entrevista): creación de punta a punta, concurrencia y Postman` |
| T7 | `CM-207 \| docs(entrevista): errores, ADR 0008 y CLAUDE.md` |
| T8 | — (verificación única) |

Rama `CM-207-crear-sesion` (stack lineal si hace falta: CM-311 ← CM-204 ← CM-209 ← CM-207).

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| La copia del catálogo se desalinea de Perfil o llega vacía | Evento del catálogo con publicación completa al arrancar (criterio de CM-209, pendiente de detallar); mientras tanto todo rol se rechaza con `TARGET_ROLE_ID_INVALID_VALUE` |
| Carrera de la clave mal resuelta (dos sesiones o un 500) | Prueba con dos hilos y `CountDownLatch` (T6) |
| `cameia-web` sin clave | Clave opcional (D-SES-03) y aviso |
| El manejador de CM-311 no tiene un punto de extensión para listas propias | T5 agrega un método nuevo sin tocar el armado común; si exige cambiarlo, detenerse |

## 7. Estimación

14 a 16 h (Jira: 3,8 h).
