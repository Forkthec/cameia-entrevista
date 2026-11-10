# Entrada 09 · Planificar las preguntas e iniciar la sesión

- **Fecha:** 2026-11-10 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-212](../../specs/CM-212-PlanificarPreguntas/spec.md), sección 17 (decisiones D-212-1 a D-212-7)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: iniciar la sesión llama a Gemini para planificar cinco preguntas. La llamada tarda hasta 9,5 s, el grupo de
conexiones es de dos, la persona puede repetir la petición y tiene como máximo tres intentos. El spike de planificación
midió el prompt v3 y recomendó reintentar una vez dentro de la misma petición si el plan llega inválido. El perfil y la
oferta son texto libre de la persona.

Tarea: decidir cómo impedir dos inicios simultáneos sin usar la IA dos veces, si `start` reintenta solo, cómo proteger el
prompt de texto hostil, de dónde sale el nombre del rol, si se aceptan preguntas repetidas en un plan y dónde viven el
estado y la cancelación; presentar cada punto con opciones, recomendación y consecuencias.

Condiciones: ningún error previsible termina en el 500 genérico; ninguna transacción abierta durante la llamada al modelo;
cada decisión que cambie el prompt se mide con el banco del spike antes de fusionar; ningún valor propuesto pasa a la spec
sin respuesta.

Formato: tabla de decisiones en la spec, con lo que pasa si la persona elige otra opción.
```

## Decisiones con peso humano

- Marca de planificación en `ejecucion_ia` con índice único y caducidad, en vez de un candado de base (Paula, 7-nov-2026; igual a la recomendación).
- `GET state` y `DELETE` van en esta tarea y el nombre del rol sale del guardado al crear la sesión (Paula, 7-nov-2026).
- Las preguntas repetidas en un plan se rechazan como salida inválida (Paula, 7-nov-2026; distinta de la recomendación, que era permitirlas).
- Una sola llamada por `start`, sin reintento automático, aunque el spike recomendaba reintentar una vez (Paula, 10-nov-2026).
- Prompt `planificacion.v4` con los datos de la persona delimitados, medido de nuevo antes de fusionar (Paula, 10-nov-2026).
