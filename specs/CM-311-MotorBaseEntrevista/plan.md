# Plan · CM-311 Motor base de Entrevista

Spec: `spec.md` (misma carpeta). Estado: **LISTA PARA EJECUTAR** tras la aprobación de Paula; condición de entrada: T0 (base de CM-312 fusionada o rama para apilar).

## 1. Enfoque

Se construye de abajo hacia arriba, un commit por capacidad, en orden de lectura. Cada commit compila y deja la suite en verde.

1. **Base técnica:** dependencias y configuración de Flyway iguales a Cuentas.
2. **Esquema:** una sola migración `V1` con todas las tablas y su prueba contra PostgreSQL real.
3. **Dominio:** estados de sesión y turno como enumerados con su tabla de transiciones; agregados `InterviewSession` e `InterviewTurn` que la aplican; `FirebaseUid`; excepciones y códigos.
4. **Persistencia:** entidades, conversores y adaptadores de sesión, turno y claves anti-duplicados, con la traducción de `@Version` y de los únicos parciales.
5. **Errores e identidad:** se extiende el manejador que sembró CM-312, sin renombrar nada; el filtro del `requestId` (T5b) va en su propio commit.
6. **Arquitectura, Postman y documentación.**

## 2. Patrones y por qué

| Patrón | Dónde | Por qué (problema presente) |
|---|---|---|
| Puerto y adaptador | `InterviewSessionRepository`, `InterviewTurnRepository`, `IdempotencyKeyRepository` | El dominio no conoce JPA (regla de ArchUnit) |
| Objeto de valor | `FirebaseUid` | La identidad del Gateway se valida en un solo lugar |
| Tabla de transiciones en el enumerado | `SessionStatus.canChangeTo`, `TurnStatus.canChangeTo` | Una regla, un lugar; la prueba recorre todas las combinaciones |
| Conversor JPA | Un `AttributeConverter` por enumerado | Código en inglés y valor del backlog en la base (decisión I-1) |
| Manejador común por código | `@ExceptionHandler(BusinessException.class)` con estado y título por `ErrorCode` | Evita un método por excepción para 7 códigos de dominio; los de CM-312 siguen con su método |

No se usa Strategy, Factory ni Builder: no hay dos variantes reales.

## 3. Reutilización

| Se reutiliza | De dónde | Cómo |
|---|---|---|
| `ErrorCode`, `BusinessException`, `BusinessExceptionHandler`, `ErrorCodeDocumentationTest` | CM-312 | Se agregan miembros y métodos; nada se renombra |
| Forma de `LayeredArchitectureTest`, `ProblemDetailTestSupport`, `FrameworkErrorsTest`, `CuentaSchemaMigrationTest`, `UntypedExceptionClassificationTest` | Cuentas `13055eb` | Se copian adaptadas al paquete `tech.cameia.entrevista` |
| Patrón de `FirebaseUid` (128, en blanco, control) | Perfil `domain/model/FirebaseUid.java` | Misma regla; clase propia (no se importa código de otro servicio) |
| `HealthController` | Entrevista | Sin cambios; es la ruta real de `FrameworkErrorsTest` y de Postman |

## 4. Archivos

### Se crean

```
pom.xml (modificado)
src/main/resources/application.properties (modificado)
src/main/resources/application-local.properties (modificado)
src/main/resources/application-production.properties (modificado)
src/main/resources/db/migration/V1__esquema_inicial_entrevista.sql
src/main/java/tech/cameia/entrevista/domain/model/{SessionStatus, TurnStatus, InterviewSession, SessionConfiguration, InterviewTurn, FirebaseUid, Modality, Tone, AnswerMode, InterviewLanguage}.java + package-info.java
src/main/java/tech/cameia/entrevista/domain/exception/{SessionTransitionNotAllowedException, TurnTransitionNotAllowedException, SessionConflictException, SessionAlreadyExistsException, IdempotencyKeyAlreadyExistsException, IdentityRequiredException, DatabaseUnavailableException}.java
src/main/java/tech/cameia/entrevista/domain/exception/ErrorCode.java (modificado: +14 miembros)
src/main/java/tech/cameia/entrevista/domain/model/IdempotencyKey.java
src/main/java/tech/cameia/entrevista/domain/port/{InterviewSessionRepository, InterviewTurnRepository, IdempotencyKeyRepository}.java + package-info.java
src/main/java/tech/cameia/entrevista/infrastructure/persistence/entity/{InterviewSessionEntity, InterviewTurnEntity, IdempotencyKeyEntity}.java
src/main/java/tech/cameia/entrevista/infrastructure/persistence/entity/converter/{SessionStatusConverter, TurnStatusConverter, ModalityConverter, ToneConverter, AnswerModeConverter, InterviewLanguageConverter}.java
src/main/java/tech/cameia/entrevista/infrastructure/persistence/repository/{InterviewSessionJpaRepository, InterviewSessionRepositoryAdapter, InterviewTurnJpaRepository, InterviewTurnRepositoryAdapter, IdempotencyKeyJpaRepository, IdempotencyKeyRepositoryAdapter}.java
src/main/java/tech/cameia/entrevista/infrastructure/persistence/mapper/{InterviewSessionMapper, InterviewTurnMapper, IdempotencyKeyMapper}.java
src/main/java/tech/cameia/entrevista/presentation/advice/BusinessExceptionHandler.java (modificado)
src/main/java/tech/cameia/entrevista/presentation/filter/{RequestIdFilter}.java + package-info.java
src/test/java/tech/cameia/entrevista/... (pruebas de spec §13)
postman/{cameia-entrevista.postman_collection.json, local.postman_environment.json, README.md}
docs/errores.md (modificado), docs/adr/0006-estados-y-esquema-del-motor-de-entrevista.md, docs/bitacora-ia/03_motor-base-entrevista_prompt.md, docs/bitacora-ia/README.md (modificado), docs/verificaciones/V-01-esquema-inicial-entrevista.md
CLAUDE.md, .env.example, docker-compose.yml (solo comentarios del perfil local)
```

### No se tocan

`.github/**` (DevOps), `HealthController`, `OpenAPIConfiguration`, el adaptador del modelo y su configuración (CM-312), el puerto del servidor (D-13), `application-production.properties` salvo quitar `ddl-auto` duplicado.

## 5. Orden, commits y ramas

| Tarjeta | Commit | Tipo |
|---|---|---|
| T0 | — (preparación) | — |
| T1 | `CM-311 \| build(entrevista): Flyway, validación, Testcontainers y ArchUnit como en Cuentas` | build |
| T2 | `CM-311 \| feat(datos): esquema inicial de la entrevista con sus restricciones` | feat |
| T3 | `CM-311 \| feat(dominio): estados de la sesión y del turno con sus transiciones` | feat |
| T4 | `CM-311 \| feat(persistencia): sesión, turno y claves anti-duplicados con control de versión` | feat |
| T5 | `CM-311 \| feat(errores): manejador completo con identidad del Gateway` | feat |
| T5b | `CM-311 \| feat(logs): requestId en cada petición y en cada línea del registro` | feat |
| T6 | `CM-311 \| test(arquitectura): reglas de dependencias entre capas` | test |
| T7 | `CM-311 \| test(postman): colección de salud y errores del framework` | test |
| T8 | `CM-311 \| docs(entrevista): catálogo de errores, ADR 0006 y CLAUDE.md al día` | docs |
| T9 | `CM-311 \| docs(verificaciones): verificación del esquema inicial de la entrevista` (el único commit de T9, con el documento de la verificación única) | docs |

Rama `CM-311-motor-base-entrevista` desde `origin/develop` con CM-312 fusionada. Si CM-312 aún no está fusionada: desde la punta de `CM-312-*` (stack; solo el PR de abajo apunta a `develop`). Un PR por CM, sin tope de líneas. Orden de ejecución y de fusión: spec §20.

## 6. Integración con otras CM del repo

| Recurso compartido | CM-312 (Vela) | CM-311 |
|---|---|---|
| Migración | ninguna | `V1` |
| `ErrorCode` | 3 miembros | +14 al final, sin reordenar |
| `docs/errores.md` | tabla con 3 filas | +14 filas, mismas columnas |
| `BusinessExceptionHandler` | 3 manejadores + armado + `requestId` | +manejadores (§5 de la tarjeta T5) |
| Postman | ninguna | crea `postman/` |
| Siguiente migración libre | — | `V2` (HU-4.3/4.4) |

## 7. Riesgos

| Riesgo | Mitigación |
|---|---|
| CM-312 cambia la forma del manejador frente a su spec | T0 compara el código real con las firmas de la spec de CM-312 (spec §20.1); si difiere, se detiene y se reporta |
| Docker apagado: las pruebas con base se omiten y la suite «pasa» sin probar el esquema | T9 exige `Tests run` con las clases de Testcontainers ejecutadas (no `Skipped`) |
| El despliegue a la base del entorno aplica `V1` al fusionar | Consulta de solo lectura previa de Paula (pendiente 7 de `ESTADO.md`): la base de Entrevista debe estar vacía |
| `CHECK` de 1000 caracteres en preguntas queda corto para el spike | Provisional por decisión P7; se amplía con `V2+` |

## 8. Estimación

Jira: 12 h (CM-257). Estimación de Backend: **18 a 20 h** (incluye el filtro del `requestId`, unas 2 h) (7 tablas con pruebas de cada restricción, procedencia de la IA, dos máquinas, tres adaptadores y el manejador completo). Se informa a Vela en el resumen de la spec; no se cambia en Jira.
