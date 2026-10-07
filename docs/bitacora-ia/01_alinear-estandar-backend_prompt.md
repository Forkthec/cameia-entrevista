# Entrada 01 · Alinear el estándar y los documentos de los repositorios de Backend

- **Fecha:** 2026-10-05 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-283](../../specs/CM-283-AlinearEstandar/spec.md), sección 2 (decisiones)

## Prompt

```
Rol: eres el arquitecto de calidad del Backend de CAMEIA y trabajas para la dueña del Backend.

Contexto: cameia-cuentas, -perfil, -entrevista y -gateway tienen documentos de agente distintos (CLAUDE.md o AGENTS.md) que se
contradicen en rama y commit, formato de error, nombre de JSON, Testcontainers, límites de código limpio, bitácora de IA y permiso
de publicar. Existe además la documentación de contexto del proyecto con reglas vigentes.

Tarea: (1) lista las contradicciones y, para cada una, propone la opción que menos cambios exija sobre el código actual; (2) revisa
la documentación de contexto y señala las reglas nuevas que el estándar aún no recoge; (3) propone una estructura común de documento
por repo.

Condiciones: no toques cameia-web, cameia-infra ni cameia-metricas (solo lectura); no asumas: cada contradicción queda como decisión
de la dueña; incluye la documentación formal (OpenAPI, Javadoc, comentarios) como requisito.

Formato: tabla de contradicciones con la opción recomendada y su costo, lista de reglas nuevas y una única tarea de Jira con su DoD.
```
