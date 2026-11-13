# CM-345 | feat(entrevista): responder por voz con audio validado y transcrito sin guardarlo [IA-ASISTIDO]

Orden de fusión: CM-349 → **CM-345** → CM-362. Esta rama ya fusionó CM-349 (`bc1f885`) y CM-362 está apilada encima.

## Qué cambia
- `POST /api/v1/interviews/sessions/{sessionId}/answers/audio` (`AudioAnswerController`, de CM-349): multipart `turnId` + `audio`, `Idempotency-Key` opcional.
- Validación del audio de HU-5.8 sobre el endpoint de CM-349: formato por el contenido real WebM/Opus y MP4/AAC sin video (`infrastructure.media`), 10 MiB (`AUDIO_TOO_LARGE`), 1,5 s a 181 s (`AudioAnswerPolicy`), entrevista por texto (`AUDIO_NOT_ALLOWED`), estado de la sesión (`SESSION_ABANDONED`, `SESSION_ALREADY_CLOSED`).
- Reserva del turno en `PROCESSING` con su versión antes de transcribir (`AnswerRegistrar`): un doble envío responde 409 `SESSION_CONFLICT` y pide una sola transcripción; un fallo deja el turno en `RETRYING`.
- La transcripción la hace AssemblyAI (adaptador de CM-349); el audio no se guarda en disco, base, bucket ni log, y sus bytes se sobrescriben al terminar. Multipart de 12 MiB en memoria.
- `V8__respuesta_por_voz.sql` (una sola, idéntica a la de CM-349). Carpeta Postman «Responder por voz», que corre con el simulador (imita Gemini y AssemblyAI) o con los proveedores reales (`vozProveedores=real`); audio `voz-180s.webm` para la pregunta de 3:00.
- Documentación: `specs/CM-345-RespuestaPorVoz/` (spec con §10 de la integración, plan, tasks), ADR 0012, bitácora IA 12, `docs/entregas/CM-345-como-probar.md`, `postman/README.md`.

## Criterios cubiertos (backend)
CA-5.8.5, 5.8.6, 5.8.7, 5.8.8, 5.8.12–5.8.18, 5.8.20, 5.8.21, 5.8.27, 5.8.28. Los demás son de Frontend (ya en `cameia-web`). Tras la quinta respuesta la sesión sigue `EN_CURSO` (CA-5.12.6) hasta HU-5.5.

## Validación (sobre `bc1f885`)
- PASA: `./mvnw clean verify` (1502 pruebas, 0 fallos, 1 omitida).
- PASA: Newman, carpeta «Responder por voz» con el simulador (39 peticiones, 159 aserciones) y en modo `vozProveedores=real` (31 peticiones, 140 aserciones; salta lo que solo el simulador provoca).
- PENDIENTE: la corrida con llaves reales de AssemblyAI y Gemini; revisión humana.

## Atributos de calidad
Seguridad (formato por contenido, nombre ignorado, audio no persistido ni registrado), Fiabilidad (idempotencia y doble envío probados en E2E), Compatibilidad de contrato (forma 200 común que ya consume la web).

## Riesgos
Decisiones tomadas sin aprobación previa (spec §8 y §10) y CM-362 apilada encima: cualquier cambio de código en esta rama se coordina antes. Cómo probar y variables: `docs/entregas/CM-345-como-probar.md`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
