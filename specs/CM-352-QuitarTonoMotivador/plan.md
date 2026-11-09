# Plan · CM-352 Quitar el tono Motivador

## Foco de revisión (lo que más probablemente falle)

| # | Situación | Prueba que la vigila | ¿Está en el plan? |
|---|---|---|---|
| 1 | La respuesta guardada del anti-duplicados conserva el nombre de la constante vieja y la repetición de una clave antigua da 500 | `migration_shouldRewriteStoredIdempotencyBody` y `create_shouldReturn409_whenOldMotivatingKeyReusedWithNeutral` | Sí (T1, T3) |
| 2 | `ALTER` antes del `UPDATE`: la base rechaza la restricción por filas viejas | `migration_shouldRewriteSessionsToNeutral_whenSavedWithMotivating` (corre contra una base con datos) | Sí (T1, T3) |
| 3 | Una sesión en cada estado distinto de `CONFIGURADA` pierde su `fecha_fin` o su estado al reescribirse | misma prueba parametrizada por los 5 estados | Sí (T3) |
| 4 | Una constante quitada deja un `@Schema`/ejemplo/Postman con cuatro tonos | paso 9 de la lista de verificación (`git grep`) y Newman | Sí (T2, T4) |
| 5 | El número `V5` lo toma CM-216 antes | `git ls-tree origin/develop …/db/migration` antes de cada push | Sí (T0, T5) |
| 6 | La prueba de la migración no corre sin Docker y parece verde | la lista exige «ninguna clase omitida» | Sí (T0) |

## Enfoque

- Reutilizar: `Tone`, `PersonalizationCatalog` (usa `Tone.values()`, no se toca su código), `SessionConfigurationPolicy` (rechaza lo desconocido), `TONE_INVALID_VALUE` (existe), `CatalogSchemaConsistencyTest` (ya vigila enumerado = CHECK), `CreateSessionEndToEndTest` y sus ayudantes. No se crea ninguna clase de producción.
- Una clase de prueba nueva, `RemoveMotivatingToneMigrationTest`, porque las existentes migran siempre a la última versión y aquí hace falta insertar datos en la versión 4 y migrar después (Flyway con `target("4")` sobre un contenedor propio).
- Sin patrón nuevo: constantes y SQL.

## Orden (un PR, cuatro commits)

| Commit | Tarjetas | Depende de |
|---|---|---|
| 1 migración | T0, T1 | — |
| 2 código y OpenAPI | T2 | T1 |
| 3 pruebas | T3 | T1, T2 |
| 4 documentos y Postman | T4 | T2 |
| cierre | T5 | todas |

T3 se escribe junto con T1 y T2 (cada prueba falla antes de cambiar el código); el commit las separa por capacidad.

## Dependencias externas

Ninguna para ejecutar. Para promover: aviso a Frontend (documento en `comunicaciones/`), conteo de filas (D-3), confirmar con Frontend la versión desplegada (el orden de despliegue ya está resuelto: CM-353 fusionado).

## Riesgos

H-2 (ventana de despliegue) y H-4 (dato irreversible); ver la spec.
