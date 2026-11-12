# Entrada 13 · Integración de la respuesta por voz (CM-349, CM-345 y CM-362)

- **Fecha:** 2026-11-12 · **Herramienta:** Claude Code (Claude Opus 5.5) · **Responsable:** la persona que pidió la integración (revisión humana pendiente)
- **Salida:** [Spec de CM-345](../../specs/CM-345-RespuestaPorVoz/spec.md), sección 10

## Contexto

Tres tareas hechas en paralelo implementaron el mismo `POST /api/v1/interviews/sessions/{sessionId}/answers/audio`
con clases homónimas y tres proveedores de transcripción distintos: Gemini (CM-345), AssemblyAI (CM-349) y un
Servicio de voz propio (CM-362).

## Decisión humana

- La transcripción del audio de la persona (voz a texto) la hace **AssemblyAI**.
- **Gemini TTS** se usa solo para pasar a audio las preguntas de la entrevista; no entra en ninguna de estas tres
  tareas.
- La IA resuelve el choque con commits en cada rama.

## Lo que propuso la IA (pendiente de ratificar en los PR)

- Pila de fusión: CM-349 (endpoint y AssemblyAI) → CM-345 (validación del audio de HU-5.8) → CM-362 (reintento de
  HU-5.13). Cada rama fusiona la anterior; no se reescribe historia.
- Se retiran el adaptador de Gemini de CM-345 y el del Servicio de voz de CM-362.
- Reglas que se conservan de cada lado: ver la tabla de la spec de CM-345, §10.
