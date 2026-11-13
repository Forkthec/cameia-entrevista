# ADR 0012 · Respuesta por voz: validación del contenido y transcripción sin almacenamiento

## Estado

Propuesta (CM-345, 11-nov-2026). Implementada sin aprobación previa por instrucción de la persona responsable; se ratifica en el PR. Ajustada al integrar con CM-349 el 12-nov-2026: la transcripción la hace AssemblyAI (ver «Transcripción»).

## Contexto

HU-5.8 pide recibir la grabación de una respuesta, rechazar lo que no sea un audio admitido sin revelar la causa, limitar
tamaño (10 MB) y duración (3 min), transcribir sin conservar el audio y no procesar dos veces el mismo envío. La web ya
envía multipart `turnId` + `audio` con una `Idempotency-Key` por turno y espera hasta 130 s; el Gateway corta a los 120 s.

## Decisión

- **Formato por contenido.** Se leen los contenedores (EBML para WebM, ISO BMFF para MP4) con un cursor acotado; se admiten solo pistas Opus (WebM) o `mp4a` de sonido (MP4). El nombre y el tipo declarado nunca se leen.
- **Duración robusta.** La mayor entre la última marca de tiempo, la duración declarada y la suma de los paquetes (TOC de Opus; 1024 tramas por muestra AAC). Tope 181 s, mínimo 1,5 s.
- **Tamaños.** Audio hasta 10 485 760 bytes (`AUDIO_TOO_LARGE` por encima); multipart hasta 12 MiB y umbral de escritura a disco de 13 MiB, de modo que las partes viven solo en memoria. Reemplaza el límite de 10 MB del ADR 0006.
- **Transcripción.** AssemblyAI (adaptador de CM-349), un intento, `TRANSCRIPTION_TIMEOUT` (90 s). Decisión humana del 12-nov-2026: AssemblyAI transcribe la voz de la persona y Gemini queda solo para pasar las preguntas a voz. Los bytes se sobrescriben con ceros al terminar.
- **Concurrencia.** El turno pasa a `PROCESSING` en una transacción corta antes de transcribir; la versión del turno deja pasar una sola reserva. Un fallo lo deja en `RETRYING` y la pregunta sigue pendiente; una reserva de más de `timeout + 30 s` se considera de un proceso muerto.
- **Anti-duplicados.** Operación `RESPONDER_AUDIO` en `clave_idempotencia`, huella SHA-256 de sesión, turno y audio; se guarda solo la respuesta 200.

## Consecuencias

- Nada del audio llega a la base, al disco ni al log; no hay ruta para recuperarlo.
- El turno se completa sin evaluación hasta que HU-5.2 agregue la evaluación entre `PROCESSING` y `COMPLETED`.
- Integrado con CM-349: el endpoint, la idempotencia y el registro común (`AnswerRegistrar`) son los de CM-349; esta decisión aporta la validación del contenido, la reserva del turno y los códigos de estado de la sesión.

## Alternativas descartadas

- Confiar en el `Content-Type` o la extensión: CA-5.8.15 lo prohíbe.
- API de archivos de Gemini o un bucket: el audio quedaría almacenado (CA-5.8.18).
- Transcribir con Gemini en línea (propuesta inicial de CM-345): descartado al integrar por la decisión de proveedor.
- Índice único de «transcripción en curso»: agrega esquema que choca con CM-349; la versión del turno basta.
