# Plan · CM-204 Catálogo de personalización

Spec: `spec.md` (misma carpeta). Estado: **LISTA PARA EJECUTAR** con CM-311 fusionada (o su rama como base de un stack).

## 1. Enfoque

De adentro hacia afuera, un commit por capacidad, cada uno compila y deja la suite en verde:

1. **Dominio:** `CatalogOption` y `PersonalizationCatalog` (la única fuente de opciones y disponibilidad), con su prueba sin Spring.
2. **Aplicación y API:** `DomainPolicyConfiguration`, `GetPersonalizationCatalogService`, `InterviewConfigController` y los dos DTO, con OpenAPI completo; pruebas web, de integración y de coherencia con la base.
3. **Postman:** carpeta «Catálogo de personalización» en la colección de CM-311.
4. **Documentación:** `docs/errores.md` (columna «Endpoints»), `CLAUDE.md` (rutas), bitácora de IA.

## 2. Patrones y por qué

| Patrón | Dónde | Por qué (problema presente) |
|---|---|---|
| Policy de dominio construida con `new` y ensamblada por configuración | `PersonalizationCatalog` + `DomainPolicyConfiguration` | Es el patrón de Cuentas (`AgePolicy` + `DomainPolicyConfiguration`); la regla se prueba sin Spring y CM-207 la reutiliza |
| DTO `record` con mapeo a mano en el controlador | `PersonalizationCatalogResponse.from(...)` | El dominio no conoce JSON; contrato explícito (ASVS 15.3.1) |

No se usa Strategy ni Factory: hay una sola fuente y una sola variante.

## 3. Reutilización

| Se reutiliza | De dónde | Cómo |
|---|---|---|
| `Modality`, `Tone`, `AnswerMode`, `InterviewLanguage` y su `code()` | CM-311 `domain/model` | Fuente de los valores y del orden; no se modifican |
| `FirebaseUid.required` | CM-311 | Validación de identidad |
| Manejador de errores, `RequestIdFilter`, `ProblemDetailTestSupport` | CM-311 | 401/405/406/500 y `requestId` sin código nuevo |
| Arranque de pruebas con Testcontainers y `cameia.llm.model=modelo-de-prueba` | CM-311/CM-312 | Igual en las dos clases de integración |
| Colección y entorno de Postman | CM-311 T7 | Carpeta nueva dentro de la misma colección |

Se crea `DomainPolicyConfiguration` porque CM-311 no deja ninguna configuración de dominio (busca con `git grep -n "@Configuration" -- src/main`): la crea CM-204 y CM-207 le agrega el `Clock`.

## 4. Archivos

### Se crean

```
src/main/java/tech/cameia/entrevista/domain/model/CatalogOption.java
src/main/java/tech/cameia/entrevista/domain/policy/PersonalizationCatalog.java
src/main/java/tech/cameia/entrevista/domain/policy/package-info.java          (si CM-311 no lo creó)
src/main/java/tech/cameia/entrevista/application/service/GetPersonalizationCatalogService.java
src/main/java/tech/cameia/entrevista/application/service/package-info.java    (si CM-311 no lo creó)
src/main/java/tech/cameia/entrevista/infrastructure/config/DomainPolicyConfiguration.java
src/main/java/tech/cameia/entrevista/presentation/controller/InterviewConfigController.java
src/main/java/tech/cameia/entrevista/presentation/dto/PersonalizationCatalogResponse.java
src/main/java/tech/cameia/entrevista/presentation/dto/CatalogOptionResponse.java
src/test/java/tech/cameia/entrevista/domain/policy/PersonalizationCatalogTest.java
src/test/java/tech/cameia/entrevista/application/service/GetPersonalizationCatalogServiceTest.java
src/test/java/tech/cameia/entrevista/presentation/controller/InterviewConfigControllerTest.java
src/test/java/tech/cameia/entrevista/presentation/PersonalizationCatalogEndToEndTest.java
src/test/java/tech/cameia/entrevista/infrastructure/persistence/CatalogSchemaConsistencyTest.java
docs/bitacora-ia/04_catalogo-personalizacion_prompt.md
```

### Se modifican

```
postman/cameia-entrevista.postman_collection.json   (carpeta nueva)
docs/errores.md                                     (columna «Endpoints» de 4 filas)
docs/bitacora-ia/README.md                          (una fila)
CLAUDE.md                                           (sección 3: rutas con identidad; sección 4: catálogo)
```

### No se tocan

Enumerados y demás clases de CM-311; `BusinessExceptionHandler`; `ErrorCode`; migraciones; `pom.xml`; `.github/**`; `HealthController`.

## 5. Orden, commits y ramas

| Tarjeta | Commit |
|---|---|
| T0 | — (preparación y línea base) |
| T1 | `CM-204 \| feat(dominio): catálogo de personalización como única fuente de opciones` |
| T2 | `CM-204 \| feat(api): GET /api/v1/interviews/config/catalog con su documentación` |
| T3 | `CM-204 \| test(postman): peticiones del catálogo de personalización` |
| T4 | `CM-204 \| docs(entrevista): catálogo en errores, CLAUDE.md y bitácora` |
| T5 | — (verificación única sobre la punta) |

Rama `CM-204-catalogo-personalizacion` desde `origin/develop` con CM-311 fusionada. Si no lo está: desde la punta de `origin/CM-311-motor-base-entrevista` (stack) y rebase con `git rebase --onto origin/develop <base-vieja> <punta-vieja>` al fusionarse, guardando antes SHA y árbol. Un PR hacia `develop` (o hacia la rama de CM-311 mientras sea stack), en Draft, con `[IA-ASISTIDO]` al final del título.

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| CM-311 se implementa con nombres distintos a su spec | T0 compara la tabla de spec §18 con el código; si difiere, se actualiza la tarjeta antes de seguir |
| Docker apagado: las dos pruebas de integración se omiten | T5 exige que no aparezcan como `Skipped` |
| La prueba de latencia falla por una máquina lenta | Límite de 2000 ms (el del anexo), 5 de calentamiento; si aun así falla, se reporta con los números, no se sube el límite |
| `cameia-web` sigue enviando `snake_case` | No afecta a este GET; el aviso va con CM-207 |

## 7. Estimación

3 a 4 h (Jira: 1,1 h). Se informa a Vela; no se cambia en Jira.
