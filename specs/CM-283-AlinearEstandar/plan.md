# Plan · CM-283 · cameia-entrevista

Spec: [spec.md](spec.md). Tareas: [tasks.md](tasks.md).

## 1. Enfoque

La Parte 1 solo cambia documentos (REQ-DOC y V-13: ningún archivo de código). Se hace en este orden: primero el repositorio modelo, `cameia-cuentas`, donde se redacta una sola vez `docs/estandar-backend.md`; los otros tres lo copian y comprueban su suma SHA-256. Cada repositorio se parte en piezas de no más de 1000 líneas entre agregadas y eliminadas (REQ-DOC-15); cuántas y cuáles, en la sección 3.

La Parte 2 es una pieza por PR en las fechas de la sección 9. Las tarjetas de cada pieza de la Parte 2 se redactan el día anterior a su fecha, con el estado real del repositorio ese día, porque dependen de decisiones pendientes (PD-xx) y del código que cambie entre tanto.

## 2. Piezas y títulos de PR

| Pieza | Contenido | Título del PR (todos terminan en `[IA-ASISTIDO]`) |
|---|---|---|
| A | Spec, plan y tareas | `CM-283 \| docs(specs): especificación para alinear el estándar [IA-ASISTIDO]` |
| B | Estándar común, errores, ADR 0001, constitución y bitácora | `CM-283 \| docs(estandar): estándar común, constitución y errores [IA-ASISTIDO]` |
| C1 | `CLAUDE.md`, `AGENTS.md`, `CONTRIBUTING.md` y `README.md` | `CM-283 \| docs(claude): CLAUDE.md de diez secciones y contribución alineada [IA-ASISTIDO]` |
| C2 | Retiros y migraciones | `CM-283 \| docs(retiros): retirar documentos duplicados [IA-ASISTIDO]` |

Cada PR enlaza la tarea de Jira y declara en «IA» la herramienta y la validación; «Control humano» queda en pendiente. Los PR no se fusionan por la IA.

## 3. Archivos por pieza

Tamaños estimados en líneas agregadas más eliminadas; se miden con `git diff --shortstat origin/develop` antes de abrir cada PR.

| Pieza | Archivos | Estimado |
|---|---|---|
| A | `specs/CM-283-AlinearEstandar/spec.md`, `plan.md`, `tasks.md` | 805 |
| B | Copiar `docs/estandar-backend.md` (≈ 400); crear `docs/errores.md`, `docs/adr/0001-codigo-de-error-y-request-id.md`, `docs/bitacora-ia/README.md` y `01_alinear-estandar-backend_prompt.md`; reescribir `docs/constitution.md` | 620 |
| C1 | Reescribir `CLAUDE.md` (≈ +200, −230); crear `AGENTS.md`; modificar `README.md` y `CONTRIBUTING.md` | 480 |
| C2 | Crear `docs/adr/0002` a `0005`; mover `docs/specs/health/README.md` a `specs/CM-101-EndpointSalud/spec.md`; eliminar `docs/AMBIGUIDADES.md`, `guidelines.md` y `docs/specs/.gitkeep` | 420 |

Orden: A, B, C1, C2.

## 4. Qué se reutiliza

- Los documentos de agente actuales: su contenido se migra a `CLAUDE.md`, no se reescribe desde cero; lo que el estándar ya define se enlaza y se retira del documento del servicio.
- `.claude/skills/backend-estandar/estandar-estricto.md` y `SKILL.md` del espacio de trabajo como fuente de `docs/estandar-backend.md`; el archivo del repositorio es autosuficiente y no los enlaza.
- La entrada 05 del registro de IA como fuente de la entrada de `docs/bitacora-ia/`.
- El validador de PR del repositorio (`.github/scripts/pr_policy.py`) para comprobar título y rama sin escribir nada.

No se crea ninguna herramienta ni script nuevo en el repositorio: la verificación usa comandos de Git y un script de enlaces en línea.

## 5. Decisiones técnicas y alternativas descartadas

| Decisión | Porqué | Alternativa descartada |
|---|---|---|
| El estándar común es un archivo copiado y verificado por hash | Cada repositorio debe leerse solo y no depender de otro | Un submódulo de Git o un repositorio de documentos: añade una dependencia y un paso de sincronización que rompe la lectura aislada |
| `CLAUDE.md` es el documento de agente y `AGENTS.md` una línea que lo enlaza | Las herramientas leen uno u otro; con una línea no hay dos copias que diverjan | Un `CLAUDE.md` que importe `AGENTS.md` (como hoy en el Gateway): vuelve a poner el contenido en el archivo que no se lee primero |
| Eliminar los documentos duplicados | Una sola fuente de verdad; Git conserva el historial | Dejarlos marcados como «no vigentes» |
| Las decisiones resueltas de Entrevista pasan a ADR | El estándar pide un ADR por decisión de arquitectura o contrato | Dejar `AMBIGUIDADES.md`: es un registro aparte de la spec y de los ADR |
| Las pruebas nuevas de base de datos de Perfil usan Testcontainers desde que se declare la dependencia | Evita migrar dos veces las pruebas escritas para subir la cobertura | Escribirlas contra el PostgreSQL de `docker-compose` y migrarlas después |

## 6. Riesgos

| Riesgo | Cómo se controla |
|---|---|
| Una contradicción que esta spec no vio | V-05, V-06, V-08 y V-11 buscan los patrones conocidos; V-12 comprueba cada afirmación del `CLAUDE.md` contra el código; cualquier hallazgo nuevo se agrega a la tabla de hallazgos con su destino antes del PR |
| Un PR supera 1000 líneas | V-10 en cada PR; si pasa, se parte antes de abrirlo |
| Un enlace roto tras mover o eliminar archivos | V-07 y V-11 |
| La suma del estándar difiere entre repositorios | V-01 en cada PR de la pieza B |
| Un hecho del `CLAUDE.md` envejece (puertos, rutas, tablas) | Solo se afirma lo que se comprobó en el SHA de la spec; V-12 |
| Un cambio de la Parte 2 rompe el despliegue | Las condiciones de la sección 7 de la spec, la secuencia segura de P2-07 y el pedido 14 con fecha |

## 7. Matriz de pruebas

| Pieza | Casos |
|---|---|
| A | V-10, V-13 |
| B | V-01, V-05, V-06, V-07, V-10, V-12 (los códigos de `docs/errores.md` frente al código), V-13 |
| C1 | V-02, V-03, V-04, V-05, V-06, V-07, V-08, V-09, V-10, V-12, V-13 |
| C2 | V-07, V-10, V-11, V-13 |

## 8. Orden, dependencias y paralelismo

1. **Una rama por pieza, todas desde `origin/develop`:** A en `CM-283-alinear-estandar`, B en `CM-283-estandar-comun`, C1 en `CM-283-claude-md` y C2 en `CM-283-retiros` (en Perfil, además, `CM-283-agents-md`, `CM-283-retiro-reglas` y `CM-283-retiro-traspaso` para C1b, C2a y C2b). Las ramas no se apilan en un PR: un PR apilado arrastraría el diff de la anterior y pasaría de 1000 líneas. Las piezas B y C van en un solo PR, desde `CM-283-estandar-comun`, cuando entre las dos no pasan de 1000 líneas (D-16); cada pieza sigue siendo uno o más commits propios.
2. **Orden de fusión: A, B, C1, C2.** La spec se fusiona primero (la bitácora de B la enlaza). C1 enlaza lo que crea B (`docs/estandar-backend.md`, `docs/errores.md`, `docs/bitacora-ia/`), y C2 retira lo que C1 ya dejó de enlazar. Una pieza posterior se prepara en local sobre la rama de la anterior y se abre cuando esa se fusiona, después de rebasarla sobre `develop`.
3. Las piezas A de los cuatro repositorios se abren el mismo día. Dentro de un repositorio el orden de apertura sigue al de fusión; entre repositorios, las piezas B y siguientes avanzan en paralelo.
4. En `cameia-cuentas` se redacta el estándar común una sola vez (pieza B); los otros tres lo copian de la rama `CM-283-estandar-comun` de Cuentas y su suma debe coincidir.
5. En Perfil, `docs/constitution.md` va en la pieza C1b y no en B, porque enlaza `CLAUDE.md`, que Perfil no tiene hasta C1a.

## 9. Parte 2: calendario, horas y dependencias

Estimación honesta de la tarea completa (los cuatro repositorios): 34 h, cinco más que las 29 h de la tarea, por las decisiones D-09 y D-10, el manejador de errores base de Entrevista y los `TODO` de Perfil (la tarea completa pasa de 35 h a 40 h).

| Pieza | Fecha | Repositorios | Horas | Depende de |
|---|---|---|---|---|
| P2-02 Failsafe | 13 oct | los cuatro | 2 | — |
| P2-03 Plugin de formato | decisión 13 oct; aplicación 21 oct | los cuatro | 2 | PD-01 |
| P2-05 `requestId` y logs | 15 oct | los cuatro | 4 | PD-02 |
| P2-11 Dependabot | 16 y 23 oct | los cuatro | 1 | — |
| P2-07 Configuración | 19 oct | los cuatro | 3 | pedido 14 a DevOps |
| P2-09 Testcontainers | 20 y 21 oct | perfil, entrevista | 5 | — |
| P2-13 ArchUnit | 20 oct | entrevista | 1,5 | — |
| P2-10 Umbral de JaCoCo | 22 oct | los cuatro | 1,5 | PD-06; después de P2-09 |
| P2-14 Verificación final | 23 oct | los cuatro | dentro de la revisión del 23 | todas; pedido 14 desplegado |

Dependencia con DevOps: el pedido 14 (perfil `prod` de Entrevista) se despliega después del 19 de octubre; P2-14-2 retira `production` el 23. Si el pedido 14 no está desplegado el 23, `production` se conserva y se registra como pendiente de DevOps en la sección 10 del `CLAUDE.md` de Entrevista.

## 10. Bloqueos

| Qué | Bloqueado por |
|---|---|
| P2-03 | PD-01 |
| P2-05 | PD-02 |
| P2-10 | PD-06 |
