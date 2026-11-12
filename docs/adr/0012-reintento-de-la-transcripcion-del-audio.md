# ADR 0012 · Reintento de la transcripción del audio

## Estado

Propuesta (CM-362, 11 de noviembre de 2026). Implementada sin aprobación previa por indicación del solicitante; la aprueba o la corrige la revisión humana del PR.

## Contexto

HU-5.13 pide que un fallo transitorio al transcribir el audio de una respuesta se reintente una vez sin intervención de la persona, que al agotarse los intentos la pregunta quede pendiente sin respuesta y sin audio guardado, y que un segundo envío del mismo turno durante los intentos se rechace. La llamada a Voz puede tardar hasta 60 s por intento y el grupo de conexiones a la base es de 2 en los despliegues.

## Decisión

- **Reintento en el caso de uso, no en el cliente HTTP.** El adaptador de Voz hace un solo intento y clasifica el fallo (`TRANSIENT`, `TIMEOUT`, `REJECTED`); `TranscriptionRetryPolicy` decide: como máximo dos intentos, y solo se reintenta un `TRANSIENT` (Voz 503 o conexión rechazada). Un intento que agota los 60 s no se reintenta.
- **Reserva del turno en la base.** Antes de llamar a Voz, el turno pasa `PENDING → PROCESSING` en una transacción corta con `@Version`; entre intentos queda en `RETRYING`. Otro envío del mismo turno en esos estados recibe `409 TURN_PROCESSING`. La llamada a Voz nunca ocurre con una transacción abierta.
- **Liberación.** Si la transcripción falla del todo, o el registro falla, el turno vuelve a `PENDING` (transiciones nuevas `PROCESSING → PENDING` y `RETRYING → PENDING`). Una reserva más vieja que 2 × `VOZ_TIMEOUT` + `VOZ_RETRY_PAUSE` + 30 s se libera en el siguiente envío.
- **Audio solo en memoria.** El multipart queda en memoria (umbral = límite de 10 MB) y no hay columna ni archivo de audio.

## Consecuencias

- La máquina de estados del turno del ADR 0006 gana dos transiciones de vuelta a `PENDING`, sin migración.
- HU-5.2 (texto) y HU-5.12 deben reutilizar la reserva del turno y la respuesta común para que «una sola respuesta por pregunta» valga para las dos formas.
- La evidencia de los dos intentos está en el log; `ejecucion_ia` no registra la transcripción todavía.

## Alternativas descartadas

- Reintentar dentro del cliente HTTP: oculta los intentos al caso de uso y no permite marcar `RETRYING`.
- Bloqueo pesimista (`SELECT … FOR UPDATE`) durante la transcripción: retiene una de las dos conexiones hasta 2 minutos.
- Guardar el audio para reintentar más tarde: contradice CA-5.13.4 y la regla de no persistir audio.
