# Entrada 18 · Voz de las preguntas con Gemini TTS y etiquetas de voz (HU-5.15)

- **Fecha:** 2026-11-15 · **Herramienta:** Claude Code (Claude Opus 5.5) · **Responsable:** la persona que pidió la tarea (revisión humana pendiente)
- **Salida:** [Spec de CM-372](../../specs/CM-372-EscucharPreguntaConVoz/spec.md), §3, y [ADR 0017](../adr/0017-voz-de-las-preguntas.md)

## Prompt (resumen saneado)

Implementar CM-372 (endpoint para escuchar la pregunta) y CM-377 en PR separados, en el orden que convenga. Para la voz:
antes de leer la pregunta con el text-to-speech de Gemini, modificarla con etiquetas que la hagan sentir viva
(risas, respiraciones, pausas, suspiros, gruñidos…, y estilos de emoción, ritmo, volumen y tono), usando un modelo
para decidir dónde van según el estado de la entrevista («si es un entrevistador exigente, más gruñidos»). Modelo
`gemini-3.8-flash-tts`; si tiene mucha demanda, `gemini-3.1-flash-tts-preview`, que usa corchetes. Hasta 15 pruebas
con la llave real. Probar, y luego commits, push y PR.

## Decisiones humanas

- Gemini TTS en lugar de Google Cloud Text-to-Speech (Chirp 3 HD), que nombraban el backlog y la tarea de Jira.
- Etiquetas de voz colocadas por un modelo según el tono y el momento de la entrevista.
- 3.8 como principal y 3.1 como respaldo ante demanda.
- Tope de 15 llamadas reales para las pruebas.

## Lo que la IA propuso y quedó

- Validar la dirección en el dominio (lista de etiquetas por tono, de 1 a 4, sin cambiar palabras) y leer la pregunta
  tal cual si falla.
- Excluir las etiquetas y estilos que no caben en una entrevista (gritos, llanto, gemidos, sarcasmo, enojo).
- Director `gemini-3.5-flash-lite` (el 3.8 de texto no respondió en 45 s), WAV en lugar de MP3 y `SPEECH_TIMEOUT` de
  15 s por la latencia medida. Pendientes de aprobación en el PR.
