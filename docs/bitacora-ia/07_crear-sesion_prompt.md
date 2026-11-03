# Entrada 07 · Crear la sesión de entrevista

- **Fecha:** 2026-10-22 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-207](../../specs/CM-207-CrearSesion/spec.md), sección 16 (decisiones D-SES-01 a D-SES-08)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: la historia pide crear la sesión de entrevista con la configuración que elige la persona (perfil, rol objetivo,
oferta, modalidad, tono, forma de respuesta e idioma). Frontend envía siempre una Idempotency-Key y reutiliza la clave al
reintentar. La persona tiene a lo sumo una sesión configurada y una en curso. El perfil llega por evento y puede no existir
todavía.

Tarea: decidir los nombres del contrato, de dónde sale el catálogo de roles para validar, si la clave es opcional u
obligatoria, qué respuestas se guardan bajo la clave, cómo se calcula la huella, qué se copia del perfil a la sesión y qué
responder con un perfil archivado o incompleto; presentar cada punto con opciones, recomendación y consecuencias.

Condiciones: todos los errores de campo a la vez; ningún error previsible termina en el 500 genérico; sin datos del perfil en
los registros ni en la respuesta; dos peticiones simultáneas no duplican la sesión; ningún valor propuesto pasa a la spec sin
respuesta.

Formato: tabla de decisiones en la spec, con lo que pasa si la persona elige otra opción.
```

## Decisiones con peso humano

- La clave de idempotencia es opcional (se recomendó y Paula lo ratificó).
- Solo se guardan respuestas 2xx bajo la clave (se recomendó y Paula lo ratificó).
- La huella se calcula sobre la configuración normalizada, no sobre el cuerpo crudo (inferencia de Backend que quedó escrita en la spec).
- El rol objetivo se valida contra una copia local del catálogo de Perfil mantenida por evento, criterio agregado a CM-209 (Paula).
- Un perfil archivado o incompleto responde 409 `PROFILE_SYNC_PENDING` (se recomendó y Paula lo ratificó).
