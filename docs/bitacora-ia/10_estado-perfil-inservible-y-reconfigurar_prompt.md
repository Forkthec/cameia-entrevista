# Entrada 10 · Forma de respuesta en el estado, perfil inservible y reconfigurar durante el inicio

- **Fecha:** 2026-11-10 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-212](../../specs/CM-212-PlanificarPreguntas/spec.md), sección 17 (decisiones D-212-8 a D-212-10)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: la revisión de código de la planificación de preguntas encontró tres puntos. El objeto de estado de `start` y
`state` no trae la forma de respuesta de la sesión. Una copia congelada del perfil demasiado grande, ilegible o sin el rol
se confundía con un fallo del modelo y gastaba intentos aunque reintentar no lo arregla. Y `POST /sessions` podía
reconfigurar la sesión mientras la IA preparaba las preguntas, dejando un plan armado con otra configuración.

Tarea: para cada punto, proponer opciones con recomendación y consecuencias: si el estado incluye `answerFormat`; qué
responde `start` con un perfil inservible (código, estado HTTP, mensaje, intentos); y cómo cerrar el hueco de reconfigurar.

Condiciones: ningún error previsible termina en el 500 genérico; sin transacción abierta durante la llamada al modelo; el
log y la causa no citan texto del perfil; cada cambio con su prueba, OpenAPI, Postman y aviso a Frontend.

Formato: tabla de decisiones en la spec, con lo que pasa si la persona elige otra opción.
```

## Decisiones con peso humano

- El estado incluye `answerFormat` (`TEXTO` o `AUDIO`) en `start` y `state` (Paula, 10-nov-2026).
- Código propio `PROFILE_SNAPSHOT_INVALID` (503) con mensaje claro, sin llamar a la IA y sin gastar intentos, en vez de contarlo como intento fallido (Paula, 10-nov-2026).
- Las dos defensas al reconfigurar durante el inicio: 409 con la marca vigente y comparación de la configuración al guardar el plan (Paula, 10-nov-2026).
