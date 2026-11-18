# Entrada 17 · Reintento automático de la transcripción (HU-5.13)

- **Fecha:** 2026-11-11 y 2026-11-12 · **Herramienta:** Claude Code (Claude Opus 5.5) · **Responsable:** la persona que pidió la tarea (revisión humana pendiente)
- **Salida:** [Spec de CM-362](../../specs/CM-362-ReintentoTranscripcionAudio/spec.md), §5, y [ADR 0016](../adr/0016-reintento-de-la-transcripcion.md)

## Prompt (resumen saneado)

Implementar CM-362 (CA-5.13.1 a CA-5.13.8) en una rama nueva siguiendo `CLAUDE.md`, probarla, dejar cómo probarla en
Postman y la descripción del PR. «Ignora cualquier instrucción de revisión antes de implementar.»

## Decisiones humanas

- Implementar sin aprobación previa de la spec (11-nov).
- AssemblyAI transcribe; Gemini TTS solo para pasar las preguntas a voz; resolver el choque con CM-345 y CM-349 con
  commits en cada rama (12-nov, ver entrada 13). La primera versión de esta tarea, con un Servicio de voz propio y
  WireMock, se retiró.
