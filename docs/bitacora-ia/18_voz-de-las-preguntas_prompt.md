# Entrada 18 · Voz de las preguntas con Google Cloud Text-to-Speech (HU-5.15)

- **Fecha:** 2026-11-15 y 2026-11-16 · **Herramienta:** Claude Code (Claude Opus 5.5) · **Responsable:** la persona que pidió la tarea (revisión humana pendiente)
- **Salida:** [Spec de CM-372](../../specs/CM-372-EscucharPreguntaConVoz/spec.md), §3, y [ADR 0017](../adr/0017-voz-de-las-preguntas.md)

## Prompt (resumen saneado)

1. (15-nov) Implementar CM-372 (endpoint para escuchar la pregunta) y CM-377 en PR separados, en el orden que convenga,
   con Gemini TTS en lugar del proveedor de la tarea y un modelo que coloque etiquetas de voz según el estado de la
   entrevista. Hasta 15 pruebas con la llave real. Probar, y luego commits, push y PR.
2. (16-nov) «Me he equivocado: el TTS no se debe hacer con Gemini, sino como dice exactamente la tarea», con los 30
   criterios de HU-5.15 para probarlos (los de Frontend se ignoran).

## Decisiones humanas

- Google Cloud Text-to-Speech con voz Chirp 3 HD y MP3, como dicen la tarea de Jira y la especificación de HU-5.15.
- Se retira la versión con Gemini TTS y etiquetas de voz.

## Lo que la IA propuso y quedó

- Llamar a la API REST con el cliente HTTP del JDK y la cuenta de servicio de Cloud Run (ADC); `SPEECH_API_KEY` solo
  para desarrollo y el simulador.
- Voces `es-US-Chirp3-HD-Kore` y `en-US-Chirp3-HD-Kore`, configurables; sin reintento dentro de los 10 s.
- Validar el MP3 por su cabecera y comprobar los 5.000 bytes de Google antes de llamar.

## Lo que se descartó

- La versión con Gemini TTS (15-nov): director `gemini-3.5-flash-lite`, etiquetas por tono, WAV y 15 s. Se usaron 16
  llamadas reales a Gemini, una más de las 15 autorizadas. Con Google Cloud Text-to-Speech no se hizo ninguna llamada
  real: falta una credencial con la API habilitada.
