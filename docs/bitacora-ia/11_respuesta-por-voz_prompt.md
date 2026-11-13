# Entrada 11 · Respuesta por voz transcrita y avance (HU-5.12)

- **Fecha:** 2026-11-11 · **Herramienta:** Claude Code (Claude Opus 5.5) · **Responsable:** quien abre el PR de CM-349
- **Salida:** [Spec de CM-349](../../specs/CM-349-TranscripcionRespuestaVoz/spec.md), sección 9 (decisiones D-01 a D-13)

## Prompt (resumen)

```
Implementar CM-349 (HU-5.12, 11 criterios de aceptación) en una rama propia, siguiendo el CLAUDE.md del
microservicio. Otros agentes trabajan en paralelo en la misma raíz. Sin ronda de revisión previa a implementar:
las decisiones se documentan para revisarlas en el PR. Entregar además un docker compose que levante todo lo
necesario, las variables de entorno, una colección de Postman y la descripción del PR.
```

## Decisiones con peso humano

Todas las de la spec §9 se tomaron por recomendación, sin ronda de preguntas (instrucción explícita del solicitante),
y quedan **pendientes de ratificar en la revisión del PR**. Las de mayor peso:

- D-01/D-02: una sola llamada a AssemblyAI con detección de idioma; el umbral de CA-5.12.10 se aplica en el dominio
  (`TRANSCRIPTION_LANGUAGE_CONFIDENCE_THRESHOLD`, 0,8 por defecto, valor provisional sin respaldo del backlog).
- D-06: la llave de AssemblyAI no es obligatoria para arrancar; sin ella la voz responde 503.
- D-08/D-09: sin HU-5.5 ni evaluación por turno, el turno queda `COMPLETED` al registrar y la sesión sigue `EN_CURSO`
  tras la quinta respuesta.
- D-13: no se borra la transcripción en AssemblyAI tras usarla.
