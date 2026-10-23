# Entrada 06 · Réplica del perfil profesional

- **Fecha:** 2026-10-22 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-209](../../specs/CM-209-ReplicaPerfilProfesional/spec.md), sección 16 (decisiones D-RPL-01 a D-RPL-07)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: crear una sesión necesita el perfil profesional de la persona. Perfil publica un evento cada vez que el perfil queda
guardado. La historia pide una copia local alimentada por ese evento, que descarte lo más viejo y no dependa de Perfil al crear
la sesión.

Tarea: decidir cómo se guarda la copia (columnas o tablas hijas), si se calcula el embedding al consumir, cómo se traduce el
estado, qué hacer cuando un mensaje cambia el dueño de un perfil, cómo se prueba el consumidor (broker real o solo unitarias) y
quién declara las colas; presentar cada punto con opciones, recomendación y consecuencias.

Condiciones: contrato idéntico al que publica Perfil; sin datos del perfil en los registros; idempotente porque llega un mensaje
por cada arranque en frío de Perfil; ningún valor propuesto pasa a la spec sin respuesta.

Formato: tabla de decisiones en la spec, con lo que pasa si la persona elige otra opción.
```

## Decisiones con peso humano

- No se calcula el `embedding` al consumir (se recomendó y Paula lo ratificó).
- El estado se guarda tal como llega y «completo» se expresa con `isUsableForInterview()` (se recomendó y Paula lo ratificó).
- Contexto profesional y roles aspirados en dos `jsonb`, sin tablas hijas (se recomendó y Paula lo ratificó).
- Pruebas con RabbitMQ real además de las unitarias: Paula eligió esa opción en su segunda respuesta, después de haber elegido solo PostgreSQL real.
- Un mensaje que cambia el dueño de un perfil se rechaza (se recomendó y Paula lo ratificó).
