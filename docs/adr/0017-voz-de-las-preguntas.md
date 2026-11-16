# ADR 0017 · Voz de las preguntas con Gemini TTS y etiquetas de voz

## Estado

Propuesta (CM-372, 15-nov-2026). Se ratifica en el PR.

## Contexto

HU-5.15 (backlog v11.5/v11.6, 30 criterios) pide escuchar cada pregunta ya mostrada con una voz natural, hasta 3 usos
de audio por pregunta, sin guardar el audio. El backlog nombra Google Cloud Text-to-Speech (Chirp 3 HD, MP3). El
responsable de Backend decidió usar **Gemini TTS** con la llave de Gemini que el servicio ya tiene, y que la pregunta
suene «viva»: antes de la voz, un modelo coloca etiquetas de voz (`<sigh>`, `<short pause>`…) según el tono y el
momento de la entrevista (bitácora, entrada 18). Una decisión anterior ya reservaba Gemini TTS para leer las preguntas
(bitácora, entrada 17).

## Decisión

- **Ruta y contrato.** `POST /api/v1/interviews/sessions/{sessionId}/questions/{number}/audio`, sin cuerpo, solo el
  dueño. Responde `audio/wav` (PCM 16 bits, 24 kHz, mono) con `Content-Length`, `Cache-Control: no-store` y
  `X-Content-Type-Options: nosniff`. Gemini TTS no entrega MP3 y convertirlo exigiría un codificador con licencia LGPL;
  el navegador reproduce el WAV desde un `Blob` igual. El cliente debe pedir `Accept: audio/wav` (o `audio/*`).
- **Dirección de la voz.** `gemini-3.5-flash-lite` (`SPEECH_DIRECTOR_MODEL`, tope `SPEECH_DIRECTOR_TIMEOUT` = 3 s)
  recibe la pregunta delimitada, el tono, el número y las etiquetas y estilos admitidos para ese tono, y devuelve
  `{estilo, texto}`. `SpeechDirectionPolicy` lo acepta solo si el estilo y cada etiqueta son del tono, hay de 1 a 4
  etiquetas, no hay dos seguidas y, al quitarlas, queda la pregunta palabra por palabra. Si no, o si el modelo falla, la
  pregunta se lee tal cual con el estilo por defecto del tono. Ningún tono admite gritos, llanto, gemidos, sarcasmo ni
  enojo; el estricto admite suspiros, carraspeos y gruñidos breves y el cálido risas suaves. El tono cambia la
  dirección, no la voz (CA-5.15.3).
- **Voz.** `gemini-3.8-flash-tts` (`SPEECH_MODEL`), etiquetas entre ángulos y el estilo en `speech_metadata`. Ante
  429, 404, 5xx o una conexión fallida, un intento con `gemini-3.1-flash-tts-preview` (`SPEECH_FALLBACK_MODEL`), que usa
  corchetes y no admite `speech_metadata` ni `response_format.mime_type` (los dos respondieron 400 el 15-11-2026): el
  estilo va como etiqueta inicial. Todo cabe en `SPEECH_TIMEOUT`.
- **Tiempo máximo: 15 s, no 10 s.** Una pregunta de 515 caracteres tardó 9,2 s y una de 200, 5–7 s (15-11-2026); con 10
  s muchas preguntas largas fallarían. DevOps lo ajusta con la medición de CM-374.
- **Datos.** Solo dos contadores (migración `V10`): `uso_audio_pregunta` (usos por pregunta; la tabla `pregunta` es
  inmutable) y `consumo_voz_mensual` (caracteres por mes UTC, tope `SPEECH_MONTHLY_CHARACTER_CAP` = 900.000). Cada
  reserva y devolución es una sola sentencia condicionada. El audio, el texto enviado a la voz y la dirección nunca se
  guardan ni se registran.
- **Condiciones de datos.** El texto de la pregunta (sin datos del perfil: la planificación ya lo redacta) sale hacia
  la API de Gemini de Google, que procesa fuera de Colombia. La política de datos debe nombrarlo (acción del PO).
- **Orden y límites.** 404 sesión → 404 número → 409 estado → 503 apagada → 409 no mostrada → 422 texto → 503 tope
  mensual → 503 ocupada (`SPEECH_MAX_CONCURRENT` = 10 por instancia, sin espera) → 409 sin usos (reserva atómica) →
  dirección y voz sin transacción → 409 si la sesión dejó de estar en curso → 200. Un fallo de la voz devuelve el uso y
  los caracteres; cerrar la pestaña no los devuelve (la voz ya procesó el texto). Escuchar no mueve la última
  actividad.
- **Estado.** `GET …/state` agrega `audioEnabled` y `audioPlaysRemaining` en la pregunta pendiente y en cada pregunta
  del historial.

## Alternativas descartadas

- **Google Cloud Text-to-Speech (Chirp 3 HD).** Pedía otra API habilitada, otra credencial y sin etiquetas
  expresivas; el responsable eligió Gemini TTS.
- **Convertir a MP3 en el servicio.** Exige una dependencia LGPL y CPU por petición, para un ahorro de tamaño que no
  cambia la experiencia.
- **Guardar la pregunta dirigida.** Ahorraría una llamada por escucha, pero CA-5.15.15 prohíbe guardar el texto enviado
  a la voz.
- **Que el modelo de voz decida las etiquetas solo.** La instrucción dentro del texto puede leerse en voz alta; un
  modelo de texto con esquema y una validación de dominio garantizan que la pregunta no cambia.
- **`Idempotency-Key`.** Cada generación cuenta como uso (CA-5.15.27); un almacén de claves no compensa su costo.
