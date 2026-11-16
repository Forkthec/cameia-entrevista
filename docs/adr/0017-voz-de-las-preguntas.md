# ADR 0017 · Voz de las preguntas con Google Cloud Text-to-Speech

## Estado

Propuesta (CM-372, 16-nov-2026). Se ratifica en el PR.

## Contexto

HU-5.15 (backlog v11.5/v11.6, 30 criterios) pide escuchar cada pregunta ya mostrada con una voz natural, hasta 3 usos
de audio por pregunta, sin guardar el audio. El backlog y la tarea de Jira fijan el proveedor: Google Cloud
Text-to-Speech con una voz Chirp 3 HD, llamado desde `cameia-entrevista` en la misma petición (el navegador nunca habla
con Google y no hay `cameia-voz`), con salida MP3 y un tiempo máximo de 10 s. Un primer intento usó Gemini TTS con
etiquetas de voz; el responsable lo descartó para seguir la tarea tal como está escrita (bitácora, entrada 18).

## Decisión

- **Ruta y contrato.** `POST /api/v1/interviews/sessions/{sessionId}/questions/{number}/audio`, sin cuerpo, solo el
  dueño. Responde `audio/mpeg` (MP3) con `Content-Length`, `Cache-Control: no-store` y
  `X-Content-Type-Options: nosniff`. El cliente pide `Accept: audio/mpeg` (o `audio/*`).
- **Voz.** `POST https://texttospeech.googleapis.com/v1/text:synthesize` con el texto guardado de la pregunta, sin
  alterar, en UTF-8, y `audioEncoding` `MP3`. Una voz Chirp 3 HD por idioma: `es-US-Chirp3-HD-Kore` para español y
  `en-US-Chirp3-HD-Kore` para inglés (`SPEECH_VOICE_ES`, `SPEECH_VOICE_EN`); el `languageCode` sale del nombre de la
  voz. El tono de la entrevista no cambia la voz (CA-5.15.3). Los idiomas `es-US` y `en-US` son los de la
  especificación de HU-5.15; la voz exacta la fija la configuración.
- **Credencial.** En Cloud Run, la cuenta de servicio del servicio con las credenciales por defecto de la aplicación
  (ADC, alcance `cloud-platform`), que se cargan en la primera petición. `SPEECH_API_KEY` (una llave restringida a
  Text-to-Speech) existe solo para desarrollo y para el simulador de Postman. Sin credencial, el servicio arranca y
  cada petición responde `SPEECH_UNAVAILABLE` (CA-5.15.21).
- **Errores.** Un 400, 403 (permiso o facturación), 429 o 5xx, una conexión fallida, un JSON ilegible o cortado, un
  `audioContent` ausente, vacío o que no es base64, o un audio que no empieza con una cabecera MP3 (etiqueta ID3
  seguida de un marco MPEG, o el marco) responden 503 `SPEECH_UNAVAILABLE`. No hay reintento ni respaldo: el tiempo
  máximo es corto y un reintento no cabría.
- **Tiempo máximo.** `SPEECH_TIMEOUT` = 10 s, el del backlog; cuenta desde que se pide la credencial. Al agotarse
  responde 503 `SPEECH_TIMEOUT` (CA-5.15.22). DevOps lo ajusta con la latencia medida (CM-374).
- **Texto.** Hasta 500 puntos de código (defensa en el dominio); el adaptador comprueba además los 5.000 bytes que
  admite Google antes de llamar (500 puntos de código de 4 bytes son 2.000 bytes).
- **Datos.** Solo dos contadores (migración `V10`): `uso_audio_pregunta` (usos por pregunta; la tabla `pregunta` es
  inmutable) y `consumo_voz_mensual` (caracteres por mes UTC, tope `SPEECH_MONTHLY_CHARACTER_CAP` = 900.000). Cada
  reserva y devolución es una sola sentencia condicionada. El audio y el texto enviado a la voz nunca se guardan ni se
  registran.
- **Condiciones de datos.** El texto de la pregunta (sin datos del perfil: la planificación ya lo redacta) sale hacia
  Google Cloud Text-to-Speech, que procesa fuera de Colombia. La política de datos debe nombrarlo (acción del PO).
- **Orden y límites.** 404 sesión → 404 número → 409 estado → 503 apagada → 409 no mostrada → 422 texto → 503 tope
  mensual → 503 ocupada (`SPEECH_MAX_CONCURRENT` = 10 por instancia, sin espera) → 409 sin usos (reserva atómica) →
  voz sin transacción → 409 si la sesión dejó de estar en curso → 200. Un fallo de la voz devuelve el uso y los
  caracteres; cerrar la pestaña no los devuelve (Google ya procesó el texto). Escuchar no mueve la última actividad.
- **Estado.** `GET …/state` agrega `audioEnabled` y `audioPlaysRemaining` en la pregunta pendiente y en cada pregunta
  del historial.

## Alternativas descartadas

- **Gemini TTS con etiquetas de voz colocadas por un modelo.** Se implementó primero (15-nov-2026) y se retiró: no es
  lo que fija la tarea, entrega WAV y no MP3, y sumaba un segundo modelo y una latencia de 5 a 9 s.
- **Biblioteca cliente `google-cloud-texttospeech` (gRPC).** Suma dependencias pesadas para una sola llamada; la API
  REST con el cliente HTTP del JDK y `google-auth-library` (que ya llegaba con el SDK de Gemini) basta.
- **Guardar el audio para repetirlo.** CA-5.15.15 prohíbe guardarlo; repetir sin gastar usos lo resuelve el Cliente
  Web con el audio ya recibido (CA-5.15.4).
- **`Idempotency-Key`.** Cada generación cuenta como uso (CA-5.15.27); un almacén de claves no compensa su costo.
