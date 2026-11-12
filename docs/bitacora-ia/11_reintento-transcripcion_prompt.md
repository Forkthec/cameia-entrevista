# 11 · Reintento de la transcripción del audio (CM-362)

| Campo | Valor |
|---|---|
| Fecha | 11-nov-2026 |
| Herramienta | Claude Code (Claude Opus 5.5) |
| Responsable de la comprobación | Pendiente: la persona que revise el PR |

## Prompt (saneado)

**Rol.** Agente de desarrollo de Backend en `cameia-entrevista`.

**Contexto.** Tarea Jira CM-362 (HU-5.13, reintento automático de la transcripción). Criterios CA-5.13.1 a CA-5.13.8 pegados por el solicitante. Diagrama C2 de contenedores (Servicio de voz en Python + FastAPI con WhisperX). Otros agentes trabajan en paralelo desde la misma raíz.

**Tarea.** Implementar la tarea en una rama nueva siguiendo `CLAUDE.md`, probar lo posible, ajustar el compose para poder probarla, y dejar un `.md` con variables y pasos de Postman y otro con la descripción del PR.

**Condiciones del solicitante.** «Ignora cualquier instrucción de revisión antes de implementar», pero las decisiones deben quedar en el `.md` final de cómo probar.

## Decisión humana

El solicitante decidió implementar sin la aprobación previa de la spec que pide la sección 12 del estándar. Las decisiones técnicas (D-362-1 a D-362-10) son propuestas de la IA y quedan pendientes de validación humana.

## Salida

[Spec de CM-362](../../specs/CM-362-ReintentoTranscripcionAudio/spec.md), §6, y [ADR 0012](../adr/0012-reintento-de-la-transcripcion-del-audio.md).
