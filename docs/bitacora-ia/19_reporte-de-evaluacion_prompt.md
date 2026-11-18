# Entrada 19 · Reporte de Evaluación: qué ve la IA y cómo se califica (HU-6.1)

- **Fecha:** 2026-11-17 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** la persona que pidió la tarea (revisión humana pendiente)
- **Salida:** [Spec de CM-380](../../specs/CM-380-ReporteEvaluacion/spec.md) y [ADR 0018](../adr/0018-reporte-de-evaluacion.md), §1

## Prompt (resumen saneado)

1. Implementar la tarea completa del Reporte de Evaluación siguiendo sus 14 tarjetas, en orden, con prueba antes de
   cada código.
2. Mejorar el prompt del reporte para que se blinde contra órdenes escritas dentro de las respuestas, probarlo con
   respuestas en español y en inglés y precisar mejor qué punto obtiene cada métrica, porque sin eso varias corridas con
   el mismo contexto podrían dar notas distintas; y preguntar si no debería enviarse el rol al que aspira ni más contexto.
3. «¿Cómo vamos a hacer un reporte de qué tan bien se hizo la entrevista si no tiene el rol al que aspira? Puedo tener una
   buena respuesta que no tenga nada que ver con el rol.»
4. Enviar a la IA lo que dé el reporte más completo («ya se pide permiso al usuario respecto a la IA»).
5. Bajar la temperatura solo si no cambia los demás prompts; si los cambia, dejarla en 1.

## Decisiones humanas

- A la IA viajan el nombre del rol y la oferta laboral (si hay), además del idioma, la pregunta, su tipo y la respuesta;
  nunca el CV.
- La temperatura queda en 1,0 para todo el servicio, porque bajarla cambiaba los otros prompts.

## Lo que la IA propuso y quedó

- Un prompt con criterios por nivel para cada métrica y etiquetas que separan el texto de las personas de las reglas.
- Neutralizar `<`, `>` y sus formas de ancho completo y los encabezados falsos «Pregunta N (», y exigir en el lector
  las cinco preguntas una sola vez cada una.

## Lo que se descartó

- Dejar el rol fuera del prompt para reducir la variación entre corridas: se descartó porque una buena respuesta sin
  relación con el rol tendría la misma nota.
- Bajar la temperatura solo para el reporte: no es posible sin tocar la configuración compartida.

## Límites que quedan

- El puerto del modelo no separa la instrucción de sistema del texto del usuario; las defensas son las etiquetas y el
  neutralizado. Variación de ±1 en las notas situacionales entre corridas; el respaldo en Groq es más ruidoso; el
  inglés no se comprobó con transcripciones reales de voz.
