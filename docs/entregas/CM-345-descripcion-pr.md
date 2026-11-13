# CM-345 | feat(entrevista): responder por voz con audio validado y transcrito sin guardarlo [IA-ASISTIDO]

## Qué cambia
- `POST /api/v1/interviews/sessions/{sessionId}/answers/audio` (multipart `turnId` + `audio`, `Idempotency-Key` opcional).
- Lectura del contenido real WebM/Opus y MP4/AAC (`infrastructure.media`), límites 10 MiB / 3 min (`AudioAnswerPolicy`).
- Transcripción con Gemini en línea (`infrastructure.speech`), sin almacenamiento; 12 códigos nuevos en `docs/errores.md`.
- `V8__respuesta_por_voz.sql` (idéntica a la de CM-349). Carpeta Postman «Responder por voz» y simulador que transcribe.
- Documentación: `specs/CM-345-RespuestaPorVoz/` (spec, plan, tasks), ADR 0012, bitácora IA 11, `CLAUDE.md`, `.env.example` y `postman/README.md`.

## Criterios cubiertos (backend)
CA-5.8.5, 5.8.6, 5.8.7, 5.8.8, 5.8.12–5.8.18, 5.8.20, 5.8.21, 5.8.27, 5.8.28. Los demás son de Frontend (ya en `cameia-web`).

## Validación
- PASA: `./mvnw clean verify` (1438 pruebas, 0 fallos, 2 omitidas (las de llamada real a Gemini)) sobre el SHA del PR.
- PASA: `GeminiTranscriptionLiveTest` con llave real (manual).
- PASA: Newman, carpeta «Responder por voz» (39 peticiones, 159 aserciones) y «Errores del framework» (31/31).
- PENDIENTE: revisión humana y decisión de proveedor único con CM-349 (AssemblyAI).

## Atributos de calidad
Seguridad (formato por contenido, nombre ignorado, audio no persistido ni registrado), Fiabilidad (idempotencia y doble envío probados en E2E), Compatibilidad de contrato (forma 200 común que ya consume la web).

## Riesgos
Solapamiento con CM-349, que implementa el mismo endpoint con AssemblyAI; merge con CM-349/CM-362 (`ErrorCode`, `BusinessExceptionHandler`, `docs/errores.md`); decisiones en `docs/entregas/CM-345-como-probar.md`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
