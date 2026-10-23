# Entrada 05 · Catálogo de personalización

- **Fecha:** 2026-10-22 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-204](../../specs/CM-204-CatalogoPersonalizacion/spec.md), sección 15 (decisiones D-CAT-01 a D-CAT-07)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: la historia de configurar la sesión pide un endpoint de solo lectura con las modalidades, los tonos, las formas de
respuesta y los idiomas, y la disponibilidad de cada uno. La creación de la sesión debe validar contra esas mismas opciones.
El frontend ya tiene las opciones fijas y espera el contrato real.

Tarea: decidir la forma del contrato (nombres, estructura, qué viaja y qué no), si el catálogo exige identidad, dónde vive la
disponibilidad y si el cliente puede guardarlo en caché; presentar cada punto con opciones, recomendación y consecuencias.

Condiciones: los nombres del contrato siguen a los que ya usa el frontend; las etiquetas visibles son del cliente; una sola
fuente en el dominio para lo que se ofrece y lo que se acepta; ningún valor propuesto pasa a la spec sin respuesta.

Formato: tabla de decisiones en la spec, con lo que pasa si la persona elige otra opción.
```

## Decisiones con peso humano

- El catálogo exige la identidad del Gateway (401 sin ella), en lugar de ser una ruta pública como el catálogo de roles de Perfil (se recomendó y Paula lo ratificó).
- La respuesta lleva cuatro listas (`modes`, `tones`, `answerFormats`, `languages`) de `{value, available}`, con los nombres que ya usa el frontend, sin etiquetas, sin opción preseleccionada y sin encabezado de caché (se recomendó y Paula lo ratificó).
- La disponibilidad vive como constante del dominio y no como propiedad de configuración: habilitar Entreno exige además lógica nueva, así que implica un despliegue en cualquier caso (se recomendó y Paula lo ratificó).
