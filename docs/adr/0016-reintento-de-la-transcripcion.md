# ADR 0016 · Reintento automático de la transcripción

## Estado

Propuesta (CM-362, 12-nov-2026). Se ratifica en el PR.

## Contexto

HU-5.13 pide reintentar una vez, sin intervención de la persona, una transcripción que falló por un error
transitorio; no reintentar si el intento agotó sus 60 s; y rechazar otro envío de la misma pregunta mientras se
transcribe o reintenta. La transcripción la hace AssemblyAI (CM-349) y el turno ya se reserva antes de transcribir
(CM-345, ADR 0015).

## Decisión

- El adaptador hace un solo intento y clasifica el fallo (`TRANSIENT`, `TIMEOUT`, `REJECTED`); `TranscriptionRetryPolicy`
  decide: dos intentos como máximo, solo `TRANSIENT` del primero, y solo si el reintento cabe en 110 s.
- Entre intentos el turno sigue en `PROCESSING`; otro envío recibe 409 `SESSION_CONFLICT`. Agotados los intentos, el
  turno pasa a `RETRYING` (pregunta pendiente) y responde 503 `TRANSCRIPTION_UNAVAILABLE`.
- `TRANSCRIPTION_TIMEOUT` pasa a 60 s y es el tope de cada intento.
- Cada intento se registra en `ejecucion_ia` con su número.

## Alternativas descartadas

- Reintentar dentro del cliente HTTP: oculta los intentos y no deja registrar cada uno.
- Pasar el turno a `RETRYING` entre intentos: ese estado admite un envío nuevo y rompería CA-5.13.8.
- Un Servicio de voz propio: descartado por la decisión de proveedor (AssemblyAI).
