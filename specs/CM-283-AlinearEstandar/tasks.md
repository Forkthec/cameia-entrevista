# Tareas · CM-283 · cameia-entrevista

Spec: [spec.md](spec.md). Plan: [plan.md](plan.md).

## Cómo se usan estas tarjetas

Una tarjeta, una verificación con salida real y recién entonces la siguiente. Cada una dice qué hacer sin interpretar; si la realidad contradice la tarjeta, se detiene y se reporta (cláusula «Detenerse si»). Las rutas son relativas a la raíz del repositorio, en su `git worktree`; `<RAIZ>` es la carpeta de trabajo de Backend que contiene los cuatro repositorios y la herramienta de verificación. Marcar `[x]` en el momento de terminar cada tarjeta.

**Reglas que valen para todas.**

- Ramas y commits: la rama es `CM-283-alinear-estandar`. Título de commit `CM-283 | docs(scope): resultado`, sin `[IA-ASISTIDO]`; el cuerpo es opcional y lleva el trailer `Co-Authored-By` del modelo.
- Los archivos nuevos se escriben con la herramienta de escritura de archivos, nunca con un `heredoc` (pierde las barras invertidas), con fin de línea LF y salto de línea final.
- Todo texto de documentación va en español. Ningún documento nombra personas, hojas de bitácora ni rutas fuera del repositorio, ni afirma datos que envejecen (conteos de pruebas, porcentajes, fechas de adopción) ni algo que el código no tenga.
- **Prohibido:** tocar código (`src/**`), `pom.xml`, `Dockerfile`, `docker-compose.yml`, `.env.example` o `/.github/`; hacer `push` a `develop` o `main`; fusionar; agregar dependencias.
- **Una rama por pieza**, todas creadas desde `origin/develop` con `git switch -c <rama> origin/develop`: A `CM-283-alinear-estandar`, B `CM-283-estandar-comun`, C1 `CM-283-claude-md`, C2 `CM-283-retiros` (en Perfil, además, `CM-283-agents-md` para C1b, `CM-283-retiro-reglas` para C2a y `CM-283-retiro-traspaso` para C2b). Orden de fusión A, B, C1, C2: la pieza siguiente se prepara en local sobre la rama anterior y se abre, rebasada sobre `develop`, cuando la anterior está fusionada.
- La herramienta de verificación es `python -I <RAIZ>/.claude/skills/backend-estandar/verificar-documentos.py <ruta del worktree>`; imprime las sumas SHA-256 y cada hallazgo de V-03 a V-08, V-11 y V-13.
- Comprobación de enlaces (V-07): la hace la misma herramienta.
- Tamaño (V-10): `git diff --shortstat origin/develop` con los archivos nuevos agregados con `git add -N` (o `git add`): agregadas + eliminadas ≤ 1000.

## Pieza A · Spec, plan y tareas

### T-01 · Confirmar y comprometer la spec

- [ ] **Objetivo.** Dejar `specs/CM-283-AlinearEstandar/` comprometida en la rama.
- **Cubre.** REQ-DOC-15.
- **Archivos.** `specs/CM-283-AlinearEstandar/spec.md`, `plan.md`, `tasks.md` (ya escritos; se modifican solo si Backend pide cambios).
- **Hacer.**
  1. `git status --short` muestra solo esa carpeta.
  2. `git add specs/CM-283-AlinearEstandar`.
  3. V-10: la suma agregadas + eliminadas es ≤ 1000.
  4. `git commit -m "CM-283 | docs(specs): especificación para alinear el estándar"`.
- **Verificación.** `git log -1 --stat` lista los tres archivos; V-13 (solo `specs/**`).
- **Detenerse si** la carpeta ya existe en `origin/develop` con otro contenido.
- **Terminado cuando** el commit existe y el diff es solo de esa carpeta.

## Pieza B · Estándar común, errores, ADR, constitución y bitácora

### T-02 · `docs/estandar-backend.md`

- [ ] **Objetivo.** Dejar el estándar común, idéntico en los cuatro repositorios.
- **Cubre.** REQ-DOC-03, REQ-DOC-11.
- **Archivos.** Crear `docs/estandar-backend.md`.
- **Hacer en `cameia-cuentas` (lo redacta el modelo más capaz, una sola vez).**
  1. Leer completos: `<RAIZ>/.claude/skills/backend-estandar/estandar-estricto.md` (secciones A a G) y `SKILL.md` (secciones 3 a 5) de esa carpeta; `git show origin/develop:guidelines.md`; y, de Perfil, `git -C <RAIZ>/cameia-perfil show origin/develop:AGENTS.md` (secciones 5, 6 y 6.1).
  2. Escribir exactamente estas quince secciones de nivel 2, con estos títulos y en este orden: `## 1. Alcance y precedencia`, `## 2. Idioma y nombres`, `## 3. Arquitectura y dependencias`, `## 4. Código limpio y mantenible`, `## 5. Validación y casos borde`, `## 6. Errores`, `## 7. Base de datos`, `## 8. Seguridad`, `## 9. Pruebas y cobertura`, `## 10. Documentación`, `## 11. Reglas del proyecto`, `## 12. Desarrollo guiado por especificación`, `## 13. Contribución y entrega`, `## 14. Guía de Spring Boot: qué se adopta`, `## 15. Atributos de calidad`.
  3. El contenido de cada sección es el de la tabla de la sección 4.1 de la spec, desarrollado en listas y tablas breves, con un ejemplo de código mínimo donde ayude (por ejemplo, el cuerpo de error de la sección 6).
  4. Correcciones obligatorias frente a las fuentes: (a) los tipos de commit y de PR son ocho (`feat`, `fix`, `test`, `docs`, `refactor`, `build`, `ci`, `chore`), sin `perf`; (b) `EMAIL_NOT_VERIFIED`, `PLAN_LIMIT` y `LLM_UNAVAILABLE` son códigos reservados por el contrato del proyecto, aún sin implementar; (c) las pruebas nuevas se llaman `metodo_shouldResultado_whenCondicion`; (d) no hay `TODO` en el código; (e) `[IA-ASISTIDO]` solo va en el título del PR; (f) la verificación es `./mvnw.cmd clean verify`.
  5. Máximo 450 líneas.
- **Hacer en los otros tres repositorios.** `git -C <RAIZ>/cameia-cuentas show CM-283-estandar-comun:docs/estandar-backend.md > docs/estandar-backend.md` y comprobar que la suma coincide.
- **Verificación.** `grep -c "^## " docs/estandar-backend.md` da 15 y los títulos coinciden; `wc -l` ≤ 450; V-01 (misma suma comprometida en los cuatro); V-05 y V-06 sin coincidencias en el archivo.
- **Trampas.** En Windows el archivo de trabajo puede quedar con CRLF; la suma válida es la del contenido comprometido (`git show HEAD:docs/estandar-backend.md | sha256sum`), que es LF. No citar `.claude/`, `Contexto CAMEIA` ni ningún archivo fuera del repositorio. No poner cifras de cobertura actuales de ningún repositorio.
- **Detenerse si** dos fuentes se contradicen dentro de una sección: reportar cuál, dónde y las opciones; no elegir.
- **Terminado cuando** las comprobaciones pasan y el archivo está comprometido (`CM-283 | docs(estandar): estándar común de Backend`).

### T-03 · `docs/errores.md`

- [ ] **Objetivo.** Catálogo de errores que el servicio emite hoy.
- **Cubre.** REQ-DOC-05, REQ-DOC-11.
- **Archivos.** Crear `docs/errores.md`.
- **Hacer.** Escribir cuatro apartados con estos títulos: `# Errores del servicio`, `## Formato`, `## Códigos que el servicio emite`, `## Respuestas sin código` y `## Cómo se agrega un código`.
  - `Formato`: «Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores). Esta página lista lo que el servicio emite hoy.»
  - `Cómo se agrega un código`: «Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un código publicado no se reutiliza ni se renombra.»
  - `Códigos que el servicio emite` y `Respuestas sin código`: según el repositorio.
    - **Gateway.** Tabla `| Código | HTTP | Mensaje | Origen | Prueba |` con cada código de `GlobalErrorHandler` leído con `git grep -n "ErrorBody(" -- src/main/java/tech/cameia/gateway/exception/GlobalErrorHandler.java`, su mensaje literal y su prueba en `GatewayErrorMappingTest`; antes de la tabla: «El Gateway conserva su formato `{"code","message"}` con un catálogo cerrado.» Bajo `Respuestas sin código`: «Ninguna.»
    - **Cuentas y Perfil.** Bajo `Códigos que el servicio emite`: «Todavía no emite el campo `code`; se adopta en la primera tarea de código del servicio (ver [ADR 0001](adr/0001-codigo-de-error-y-request-id.md)).» Bajo `Respuestas sin código`: tabla `| HTTP | Título | Cuándo |` con cada respuesta que producen `BusinessExceptionHandler` (Cuentas) o `ApiExceptionHandler` y la respuesta de finalización (Perfil), leídas del manejador con `git grep -n "HttpStatus\|ProblemDetail" -- src/main`.
    - **Entrevista.** Bajo `Códigos que el servicio emite`: «El servicio todavía no tiene manejador de errores ni emite el campo `code`.» Bajo `Respuestas sin código`: «Ninguna: `GET /health` es su único endpoint.»
- **Verificación.** V-07 (enlaces); cada fila de la tabla tiene su línea de código (V-12): se anota en el PR el `git grep` de cada una; V-05 y V-06 sin coincidencias.
- **Trampas.** No listar códigos de otro servicio ni los reservados (`EMAIL_NOT_VERIFIED`, `PLAN_LIMIT`, `LLM_UNAVAILABLE`).
- **Detenerse si** un manejador produce un estado que no se puede describir con su título y su causa.
- **Terminado cuando** la verificación pasa.

### T-04 · `docs/adr/0001-codigo-de-error-y-request-id.md`

- [ ] **Objetivo.** Registrar la decisión del `code`, el `requestId` y `errors[].code`.
- **Cubre.** REQ-DOC-06.
- **Archivos.** Crear `docs/adr/0001-codigo-de-error-y-request-id.md`.
- **Hacer.** Escribir este texto exacto (en el Gateway, con la variante de abajo):

```
# ADR 0001 · Código de error y `requestId` en la respuesta de error

## Estado

Aceptada. Decisión de Backend, 5 de octubre de 2026.

## Contexto

Los servicios responden los errores con `ProblemDetail` (RFC 9457), una lista `errors` con `field` y `message` por campo rechazado y JSON en `camelCase`. Eso permite mostrar un mensaje, pero no deja un identificador estable para que el cliente actúe según la causa ni un hilo para depurar una petición entre el Gateway, el servicio y el registro.

## Decisión

Se agregan tres miembros de extensión, que la norma admite, sin reemplazar nada de lo ya publicado:

- `code`: el código de la causa, en `UPPER_SNAKE_CASE` y con la forma `<SUJETO>_<CAUSA>` (por ejemplo `EMAIL_ALREADY_REGISTERED`); `VALIDATION_FAILED` cuando hay `errors`; `INTERNAL_ERROR` en un fallo técnico.
- `requestId`: el valor de `X-Request-Id` que pone el Gateway; si falta, el servicio genera uno y lo devuelve en el encabezado.
- `errors[].code`: el código de cada campo rechazado.

El catálogo del servicio está en [errores.md](../errores.md) y las reglas, en la sección 6 del [estándar](../estandar-backend.md).

## Consecuencias

- El cambio es aditivo y compatible: ningún cliente que ya lea `type`, `title`, `status`, `detail` o `errors[].message` deja de funcionar.
- Frontend puede leer los campos nuevos cuando cada servicio los emita; mientras tanto no aparecen en la respuesta.
- Cada servicio los adopta en su primera tarea de código, con su excepción de negocio, su estado, su mensaje y su prueba.
- Un código publicado no se reutiliza ni se renombra.

## Alternativas descartadas

- `codigoCameia` y `correlationId`: nombres propios sin ventaja sobre los miembros de extensión de la norma.
- Un formato de error propio distinto de `ProblemDetail`: obligaría a cambiar todos los clientes.
```

  **Variante del Gateway.** Mismo título, `Estado` y `Alternativas descartadas`; `Contexto`: «El Gateway es el primer punto de la cadena y responde errores propios (token ausente, servicio caído, tiempo agotado) con `{"code","message"}` y un catálogo cerrado de códigos.» `Decisión`: «El Gateway conserva ese formato y su catálogo, que está en [errores.md](../errores.md). Genera el `X-Request-Id` cuando el cliente no lo envía y lo devuelve en el encabezado de cada respuesta; los servicios lo toman como `requestId`.» `Consecuencias`: «Los servicios adoptan `code`, `requestId` y `errors[].code` según la sección 6 del [estándar](../estandar-backend.md); el Gateway no cambia su formato. Los códigos `AUTH_REQUIRED` y los demás del catálogo se conservan tal cual.»
- **Verificación.** V-07; V-05 y V-06 sin coincidencias.
- **Detenerse si** el ADR de otro repositorio ya existe con otro número.
- **Terminado cuando** el archivo existe y la verificación pasa.

### T-05 · `docs/constitution.md`

- [ ] **Objetivo.** Constitución con los principios comunes y los propios del servicio.
- **Cubre.** REQ-DOC-04 y REQ-EN-06.
- **Archivos.** Crear (Gateway y Perfil) o reescribir (Cuentas y Entrevista) `docs/constitution.md`.
- **Hacer.**
  1. Primera línea: `# Constitución de <nombre del repositorio>`.
  2. Párrafo: «Principios no negociables. Toda spec y todo PR los cumple; en conflicto, esta lista prevalece sobre cualquier otro documento del repositorio. El detalle vive en el [CLAUDE.md](../CLAUDE.md) y en el [estándar](estandar-backend.md).»
  3. Lista numerada con los quince principios comunes de la sección 4.3 de la spec, con su texto y su `→` de comprobación. Gateway: los principios 2 y 7 se redactan sin base de datos («No tiene base de datos» y «No aplica: el Gateway no tiene base de datos»). Entrevista: sin la mención de Wompi; Cuentas: ninguna otra.
  4. A continuación, desde el 16, los principios propios del repositorio con el texto literal de su requisito (REQ-EN-06).
- **Verificación.** `grep -c "^[0-9]*\. \*\*" docs/constitution.md` da 15 más los propios (Cuentas 17, Gateway 18, Perfil 19, Entrevista 17); V-07; V-05, V-06 y V-08 sin coincidencias.
- **Trampas.** Los enlaces son `../CLAUDE.md` y `estandar-backend.md` (el archivo está en `docs/`); en Entrevista el enlace roto original apuntaba sin `../`.
- **Detenerse si** un principio propio del requisito contradice un principio común.
- **Terminado cuando** la verificación pasa.

### T-06 · `docs/bitacora-ia/`

- [ ] **Objetivo.** Carpeta con la guía y la entrada de la decisión de alinear los repositorios.
- **Cubre.** REQ-DOC-07.
- **Archivos.** Crear `docs/bitacora-ia/README.md` y `docs/bitacora-ia/01_alinear-estandar-backend_prompt.md`.
- **Hacer.**
  1. `README.md` con este texto exacto:

```
# Bitácora de IA

Esta carpeta guarda la **entrada** de cada decisión en la que una persona cambió el rumbo de una propuesta de la IA (alcance, arquitectura, seguridad, costo, calidad o proceso): el prompt armado con rol, contexto, tarea, condiciones y formato. La **salida** es la spec del repositorio (`specs/CM-NNN-*/spec.md`, sección de decisiones, con la decisión humana, el porqué y las alternativas descartadas); la bitácora la enlaza y no la copia.

Cada entrada es un archivo `NN_tema_prompt.md`, con `NN` consecutivo.

| Entrada | Decisión | Salida |
|---|---|---|
| [01_alinear-estandar-backend_prompt.md](01_alinear-estandar-backend_prompt.md) | Alinear los documentos y el estándar de los cuatro repositorios de Backend | [Spec de CM-283](../../specs/CM-283-AlinearEstandar/spec.md) |
```

  2. `01_alinear-estandar-backend_prompt.md`: copiar el bloque `## Prompt` (con su cerca de código) de `<RAIZ>/prompts/entradas/05_alinear-estandar-backend-en-los-cuatro-repos_prompt.md` y anteponer: `# Entrada 01 · Alinear el estándar y los documentos de los repositorios de Backend`, la línea `- **Fecha:** 2026-10-05 · **Herramienta:** Claude Code · **Responsable:** Backend`, la línea `- **Salida:** [Spec de CM-283](../../specs/CM-283-AlinearEstandar/spec.md), sección 2 (decisiones)` y la línea en blanco previa a `## Prompt`. No copiar la línea de «Salida asociada» original.
- **Verificación.** V-07; V-05 sin coincidencias.
- **Trampas.** El original enlaza a `../salidas/...`, que no existe en el repositorio: no copiar ese enlace.
- **Detenerse si** la entrada original ya no existe en esa ruta.
- **Terminado cuando** la verificación pasa.

### T-07 · Retiros de la pieza B (solo `cameia-cuentas`)

- [ ] **Objetivo.** Retirar `guidelines.md` y la carpeta vacía `docs/specs/`.
- **Cubre.** REQ-DOC-13, decisión D-08.
- **Archivos.** Eliminar `guidelines.md` y `docs/specs/.gitkeep`.
- **Hacer.** `git rm guidelines.md docs/specs/.gitkeep`; comprobar con `git grep -n "guidelines.md"` que no queda ningún enlace (el `CLAUDE.md` de Cuentas todavía lo enlaza hasta T-09: se resuelve ahí). En los otros repositorios esta tarjeta no aplica (Entrevista lo hace en T-14).
- **Verificación.** `ls guidelines.md docs/specs` falla; V-11 solo reporta el `CLAUDE.md` pendiente de T-09.
- **Detenerse si** otro archivo, además del `CLAUDE.md`, enlaza `guidelines.md`.
- **Terminado cuando** los dos archivos están eliminados y comprometidos (`CM-283 | docs(estandar): retirar guidelines.md y la carpeta de specs vacía`).

### T-08 · Verificar y abrir el PR de la pieza B

- [ ] **Objetivo.** PR de la pieza B, verificado.
- **Cubre.** REQ-DOC-15.
- **Hacer.**
  1. Ejecutar la herramienta de verificación y V-10; guardar la salida.
  2. `git push -u origin CM-283-alinear-estandar` (permiso permanente de Backend para la rama `CM-*`; nunca a `develop` ni `main`).
  3. `gh pr create --base develop --title "CM-283 | docs(estandar): estándar común, constitución y errores [IA-ASISTIDO]" --body-file <archivo>` con los ocho campos de la plantilla del repositorio llenos y sin marcadores: **Jira** el enlace `https://f0rktech.atlassian.net/browse/CM-283`; **Responsable** el nombre completo de la persona responsable de Backend, `| Backend`; **Cambio** dos frases; **Evidencia** la salida de la herramienta y de V-10 con el SHA; **Impacto** `ninguno`; **Riesgo** `bajo` con su razón; **IA** `si` con herramienta (Claude Code) y validación; **Control humano** `pendiente —` el nombre completo de esa persona.
  4. No fusionar. Entregar el link del PR, qué archivos importa revisar (`docs/estandar-backend.md`, `docs/constitution.md`, `docs/errores.md`) y qué es mecánico (ADR, bitácora, retiros).
- **Verificación.** Los validadores de rama y de plantilla del PR en verde.
- **Detenerse si** un validador falla por un motivo que no se entiende: reportar la salida completa.
- **Terminado cuando** el PR está abierto, con su link en la respuesta.

## Pieza C1 · Documentos de agente

### T-09 · `CLAUDE.md` de diez secciones

- [ ] **Objetivo.** Reescribir `CLAUDE.md` con la estructura común, migrando su contenido actual.
- **Cubre.** REQ-DOC-01, REQ-EN-01 a REQ-EN-05, REQ-EN-11.
- **Archivos.** Reescribir `CLAUDE.md`. No se tocan `docs/**` ni `README.md` aquí.
- **Hacer.**
  1. Leer el `CLAUDE.md` actual completo y `docs/AMBIGUIDADES.md`.
  2. Empezar con `# cameia-entrevista` y escribir las diez secciones con los títulos exactos de REQ-DOC-01, migrando así:

| Sección | Fuente en el documento actual | Cambio |
|---|---|---|
| 1. Servicio | «Contexto del proyecto» y «Responsabilidad del servicio» | Conservar modos entreno y simulación, perfil de debilidades como reporte derivado, y lo que no hace |
| 2. Estructura y dependencias | «Estructura de carpetas» y «Regla de dependencias» | Conservar el árbol; **quitar «se valida con la prueba `LayeredArchitectureTest`»** y decir que está pendiente (REQ-EN-02); pila: Java 21, Spring Boot 4.1.1, Maven Wrapper, Spring AI |
| 3. Límites de confianza | «Límites de entrada y confianza» | Conservar las dos capas; sustituir «Contrato de entrada» por los encabezados reales (REQ-EN-03) |
| 4. Contrato y errores | «OpenAPI en español» | Rutas `/api/v1/...`; URL con el puerto **8083**; la bandera según el código actual (`API_DOCS_ENABLED`, perfil `production`), porque cambia en P2-07; remitir a `docs/errores.md` |
| 5. Datos | No existe | PostgreSQL con JPA, sin entidades ni migraciones todavía |
| 6. Seguridad | «Reglas de seguridad» | Conservar |
| 7. Pruebas | `docs/constitution.md` | Tipos de prueba; Testcontainers y la prueba de arquitectura como pendientes de CM-283 |
| 8. Verificación | «Verificación de cambios de código» | Frase común de la sección 4.2 de la spec; URL locales con el puerto 8083 |
| 9. Contribución | «Flujo de contribución» | **Sustituir por el texto literal de la sección 4.2 de la spec** (quita la rama `<tipo>/CM-…`) |
| 10. Pendientes | `AMBIGUIDADES.md` A-003, y lo abierto de la tarea | A-003 (máquina de estados, responsable Product Owner), prueba de arquitectura, Testcontainers y manejador de errores |

  3. No conservar nada de la lista de REQ-EN-11.
  4. Comprobar cada hecho y anotar el comando en el PR: `git grep -nE "SERVER_PORT|server.port" -- src/main/resources Dockerfile docker-compose.yml .env.example`; `git grep -nE "API_DOCS_ENABLED" -- src docker-compose.yml .env.example`; `git ls-files src/main | grep -v gitkeep`; `git grep -n "X-User-" -- src/main` (puede no tener resultados: el servicio aún no usa los encabezados; en ese caso la sección 3 los describe como el contrato que recibirá del Gateway y lo dice).
- **Verificación.** V-03, V-05, V-06, V-07, V-08, V-11 y V-12 sin hallazgos de este archivo; el enlace a la constitución es `docs/constitution.md`.
- **Trampas.** El `CLAUDE.md` actual dice puerto 8080: es el puerto interno del contenedor; el de uso es 8083. No afirmar que `LayeredArchitectureTest` existe.
- **Detenerse si** `AMBIGUIDADES.md` tiene una decisión que no cabe en ninguna sección ni en un ADR (T-14): reportarla.
- **Terminado cuando** la verificación pasa (`CM-283 | docs(claude): CLAUDE.md de Entrevista de diez secciones`).

### T-10 · `AGENTS.md` de una línea

- [ ] **Objetivo.** Que `AGENTS.md` solo remita a `CLAUDE.md`.
- **Cubre.** REQ-DOC-02.
- **Archivos.** Crear (Cuentas y Entrevista) o reemplazar (Gateway y Perfil) `AGENTS.md`. En Perfil esta tarjeta es la pieza C1b: va en un PR aparte, después de fusionar C1a.
- **Hacer.** El archivo contiene exactamente la línea `Las reglas de este repositorio están en [CLAUDE.md](CLAUDE.md).` y un salto de línea final. Antes de reemplazar (Gateway y Perfil), comprobar que cada regla vigente del `AGENTS.md` anterior ya está en `CLAUDE.md` o en `docs/estandar-backend.md`: abrir `git show origin/develop:AGENTS.md` y, sección por sección, anotar dónde quedó cada una en el PR.
- **Verificación.** V-04; V-07; V-10 (Perfil: −866, +1).
- **Trampas.** Una línea con retorno de carro rompe V-04: LF.
- **Detenerse si** una regla vigente del `AGENTS.md` anterior no está en ningún documento nuevo: agregarla donde corresponda antes de reemplazar, y reportarlo.
- **Terminado cuando** V-04 pasa (`CM-283 | docs(claude): AGENTS.md remite a CLAUDE.md`).

### T-11 · `CONTRIBUTING.md` igual en los cuatro

- [ ] **Objetivo.** Que el `CONTRIBUTING.md` quede byte a byte igual al de Cuentas.
- **Cubre.** REQ-DOC-08, decisión D-01.
- **Archivos.** Reemplazar `CONTRIBUTING.md` (Gateway, Perfil y Entrevista). En Cuentas no hay cambios: es el modelo y la tarjeta se marca sin acción.
- **Hacer.** `git -C <RAIZ>/cameia-cuentas show origin/develop:CONTRIBUTING.md > CONTRIBUTING.md`. Comprobar con `git diff` que solo cambian las líneas del commit: «siempre incluyen la clave Jira…», la línea de `NNN` sin ceros y el ejemplo `Commit: CM-123 | docs(web): documentar configuracion` (y un espacio final del Gateway).
- **Verificación.** V-02 (misma suma en los cuatro); `git diff --stat CONTRIBUTING.md` de unas 5 líneas.
- **Trampas.** Se compara el contenido comprometido (`git show HEAD:CONTRIBUTING.md | sha256sum`), que es LF aunque Windows convierta el archivo de trabajo.
- **Detenerse si** el diff muestra más de esas líneas: reportar cuáles, porque el repositorio tendría una regla propia que esta spec no previó.
- **Terminado cuando** V-02 pasa (`CM-283 | docs(claude): CONTRIBUTING.md igual en los cuatro repositorios`).

### T-12 · `README.md`

- [ ] **Objetivo.** Alinear el README.
- **Cubre.** REQ-DOC-09, REQ-EN-10.
- **Archivos.** Modificar `README.md` (77 líneas).
- **Hacer.** Reemplazar las líneas 73 a 77 (`main` estable, `develop` integra ramas `<tipo>/CM-NNN…`, revisión y «Tipos admitidos…`perf`…») por: «La rama, el commit, los tipos, el título del PR y la revisión están en [CONTRIBUTING.md](CONTRIBUTING.md).»; comandos de verificación a `./mvnw.cmd clean verify` si los menciona.
- **Verificación.** V-05, V-07 y V-08 sin hallazgos de este archivo.
- **Detenerse si** el README tiene otra sección que contradiga el estándar: reportarla.
- **Terminado cuando** la verificación pasa (`CM-283 | docs(claude): alinear el README de Entrevista`).

### T-13 · Verificar y abrir el PR de la pieza C1

- [ ] **Objetivo.** PR de la pieza C1 (en Perfil, C1a), verificado.
- **Cubre.** REQ-DOC-15 y los casos V-02 a V-13.
- **Hacer.**
  1. Ejecutar la herramienta de verificación sobre el worktree y V-10; guardar la salida. Los hallazgos que queden deben ser solo los de documentos que otra pieza de este repositorio todavía retira (por ejemplo, el `AGENTS.md` largo de Perfil hasta C1b); cada uno se nombra en el PR con la pieza que lo resuelve.
  2. `git push -u origin CM-283-alinear-estandar` y `gh pr create --base develop` con el título `CM-283 | docs(claude): CLAUDE.md de diez secciones y contribución alineada [IA-ASISTIDO]` y los ocho campos de la plantilla llenos como en T-08 (en **Evidencia**, la salida de la herramienta y los comandos de comprobación de hechos de T-09).
  3. No fusionar. Entregar el link, qué archivos importa revisar (`CLAUDE.md` completo y `CONTRIBUTING.md`) y qué es mecánico (README, `AGENTS.md`).
- **Verificación.** Los validadores de rama y de plantilla del PR en verde.
- **Detenerse si** la herramienta reporta un hallazgo que no pertenece a una pieza pendiente: corregirlo antes de abrir el PR.
- **Terminado cuando** el PR está abierto, con su link en la respuesta.

### T-14 · Retiros y migraciones de la pieza C2

- [ ] **Objetivo.** Pasar las decisiones de `AMBIGUIDADES.md` a ADR, mover la spec de salud y retirar los documentos duplicados.
- **Cubre.** REQ-DOC-13, REQ-EN-08, REQ-EN-09.
- **Archivos.** Crear `docs/adr/0002` a `0005`; mover `docs/specs/health/README.md`; eliminar `docs/AMBIGUIDADES.md`, `guidelines.md` y `docs/specs/.gitkeep`.
- **Hacer.**
  1. Leer `docs/AMBIGUIDADES.md` completo. Para cada registro resuelto crear su ADR con este esqueleto: `# ADR 000N · <título>`, `## Estado` («Aceptada. Decisión de <Product Owner o Backend, según el registro>, <fecha del registro>.»), `## Contexto`, `## Decisión`, `## Consecuencias`, tomando el texto del registro sin perder ningún dato y **sin nombres de personas** ni referencias a `AMBIGUIDADES.md`:
     - `docs/adr/0002-autenticacion-entre-gateway-y-entrevista.md` ← A-001;
     - `docs/adr/0003-perfil-de-debilidades-como-reporte-derivado.md` ← A-002;
     - `docs/adr/0004-exposicion-de-la-documentacion-de-la-api.md` ← A-004: **no nombra la variable ni el perfil** (cambian en P2-07); dice que se publica solo en desarrollo y se retira en despliegue, y remite al `CLAUDE.md`;
     - `docs/adr/0005-reparto-de-puertos-y-resolucion-del-puerto.md` ← A-005: el reparto 8080 Gateway, 8081 Cuentas, 8082 Perfil y 8083 Entrevista, y que Cloud Run inyecta `PORT`, que se resuelve primero.
  2. Pasar la sección «Decisiones confirmadas» a su ADR o a la sección que corresponda del `CLAUDE.md`, sin pérdida; cada dato queda en un solo lugar.
  3. `git mv docs/specs/health/README.md specs/CM-101-EndpointSalud/spec.md` (crear antes la carpeta); en la línea «Responsable» escribir `Product Owner`; actualizar los enlaces de otros documentos a esa ruta.
  4. `git rm docs/AMBIGUIDADES.md guidelines.md docs/specs/.gitkeep`.
- **Verificación.** V-07, V-10, V-11 y V-13; `git grep -n "AMBIGUIDADES\|docs/specs/health"` sin resultados; el `CLAUDE.md` enlaza `docs/adr/0002` a `0005` donde corresponde.
- **Trampas.** `git mv` de la spec conserva su historial; el resto del texto de la spec no se modifica.
- **Detenerse si** un registro de `AMBIGUIDADES.md` no se puede pasar sin perder un dato: reportarlo.
- **Terminado cuando** la verificación pasa (`CM-283 | docs(retiros): ADR de Entrevista, spec de salud en specs y retiro de documentos duplicados`).

### T-15 · Verificar y abrir los PR de la pieza C2

- [ ] **Objetivo.** PR de retiros y migraciones, verificado (solo Perfil y Entrevista; en Cuentas y Gateway no aplica).
- **Cubre.** REQ-DOC-13, REQ-DOC-15.
- **Hacer.**
  1. Ejecutar la herramienta de verificación sobre el repositorio completo: **no debe quedar ningún hallazgo** (V-03 a V-08, V-11 y V-13) y V-10 de cada PR ≤ 1000.
  2. `git push` y `gh pr create --base develop` con título `CM-283 | docs(retiros): <resultado> [IA-ASISTIDO]` y los ocho campos de la plantilla.
  3. No fusionar. Entregar el link, y decir que son eliminaciones completas de archivos: se revisan comprobando que ningún documento los enlaza (V-11).
- **Verificación.** Los validadores de rama y de plantilla del PR en verde; la herramienta sin hallazgos.
- **Detenerse si** queda un hallazgo: no se abre el PR; se corrige o se registra con su responsable.
- **Terminado cuando** los PR están abiertos con su link.

### T-16 · Cierre del bloque

- [ ] **Objetivo.** Dejar el seguimiento al día después de abrir los PR.
- **Hacer.**
  1. Comentar en CM-283 en Jira (hito: PR) con: Acción · Repositorio · Resultado · Evidencia (link del PR y salida de la herramienta) · Decisión o bloqueo · Siguiente paso · Responsable (nombre completo de quien responde por Backend). Mover la tarjeta a `En revisión` solo cuando estén abiertos **todos** los PR de la Parte 1; mientras tanto sigue `En curso`.
  2. Esperar el visto bueno de Backend antes de seguir con otra pieza.
- **Terminado cuando** el comentario está en Jira y el PR tiene su link.
