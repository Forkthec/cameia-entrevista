# Entrada 04 · Dueño en los puertos y error 413

- **Fecha:** 2026-10-22 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-311](../../specs/CM-311-MotorBaseEntrevista/spec.md), decisiones P25 y P26

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: la revisión de código de la tarea del motor base dejó tres puntos sin decisión: qué responde el servicio a un multipart
de más de 10 MB, si los puertos de sesión reciben el dueño, y si el 400 del contenedor conserva su código de ruta.

Tarea: presentar cada punto con opciones, recomendación y consecuencias, y aplicar lo decidido.

Condiciones: un fallo previsible tiene su código propio; la autorización por objeto se comprueba en la consulta; no asumir nada.

Formato: tabla de decisiones en la spec y pruebas primero.
```

## Decisiones con peso humano

- Un multipart de más de 10 MB responde 413 con `REQUEST_PAYLOAD_TOO_LARGE`, no el código del multipart ilegible (se recomendó y Paula lo ratificó).
- El dueño entra ya a los puertos de sesión (`findByIdAndOwner` y `deleteConfigured(id, owner)`), en lugar de comprobarlo en cada caso de uso futuro (Paula lo pidió ahora).
- El 400 del contenedor conserva `REQUEST_PATH_INVALID_FORMAT` aunque también salga por encabezados mal formados (se recomendó y Paula lo ratificó).
