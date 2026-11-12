# CM-345 · Responder por voz: recibir, validar y transcribir el audio de una respuesta (HU-5.8)

| Campo | Valor |
|---|---|
| Tarea Jira | CM-345 (HU-5.8). La tarea no se pudo leer desde la sesión de IA (sin acceso al sitio de Jira); la fuente son los criterios CA-5.8.1 a CA-5.8.29 que entregó la persona responsable |
| Repositorio | `cameia-entrevista`, base `origin/develop` = `09bb7ff` (CM-212, iniciar la sesión) |
| Tareas vecinas | CM-349 (HU-5.12, transcripción de la respuesta por voz) y CM-362 (reintento de la transcripción), en curso en paralelo; HU-5.2 (respuesta por texto) aún sin backend |
| Frontend | `cameia-web` ya consume `POST …/answers/audio` (CM-317): multipart `turnId` + `audio`, `Idempotency-Key` por turno, espera de 130 s y los códigos de §6 |
| Estado | Implementada sin la ronda de aprobación previa, por instrucción explícita de la persona responsable; las decisiones están en §8 para revisión en el PR |

## 1. Objetivo

`POST /api/v1/interviews/sessions/{sessionId}/answers/audio` recibe la grabación de la pregunta pendiente, comprueba
quién la envía, el estado de la entrevista, el tamaño, el formato real y la duración, la transcribe sin guardarla y la
registra como respuesta `AUDIO`, avanzando a la pregunta siguiente o dejando la entrevista en `EVALUANDO`.

## 2. Alcance por criterio

| CA | Backend | Dónde |
|---|---|---|
| 5.8.1, 5.8.2, 5.8.3, 5.8.4, 5.8.10 (detección), 5.8.19, 5.8.22, 5.8.23, 5.8.24, 5.8.25, 5.8.26, 5.8.29 | Solo Frontend (ya en `cameia-web`) | — |
| 5.8.5 | Registrar y avanzar sin texto escrito | REQ-VOZ-08 |
| 5.8.6 | Un solo audio procesado y una sola transcripción ante doble envío | REQ-VOZ-09 |
| 5.8.7, 5.8.8 | 3:00 se acepta; 3:02 → 422 `AUDIO_TOO_LONG` | REQ-VOZ-06 |
| 5.8.9, 5.8.10 | Defensa: menos de 1,5 s → 422 `AUDIO_TOO_SHORT`; transcripción vacía → 422 `TRANSCRIPTION_EMPTY` | REQ-VOZ-06, REQ-VOZ-07 |
| 5.8.12, 5.8.13 | 10 485 760 bytes se aceptan; 10 485 761 → 413 `AUDIO_TOO_LARGE` | REQ-VOZ-04 |
| 5.8.14, 5.8.15 | Formato decidido por el contenido → 415 `AUDIO_INVALID_FORMAT` | REQ-VOZ-05 |
| 5.8.16 | El nombre del archivo nunca se lee | REQ-VOZ-11 |
| 5.8.17, 5.8.18 | Sin ruta de lectura; audio solo en memoria y sobrescrito al terminar | REQ-VOZ-11 |
| 5.8.20 | Entrevista por texto → 422 `AUDIO_NOT_ALLOWED` | REQ-VOZ-03 |
| 5.8.21 | Ajena o inexistente → 404 `SESSION_NOT_FOUND` | REQ-VOZ-01 |
| 5.8.27 | Misma `Idempotency-Key` y mismo audio → misma respuesta sin transcribir | REQ-VOZ-10 |
| 5.8.28 | Abandonada → 409 `SESSION_ABANDONED`; en evaluación o finalizada → 409 `SESSION_ALREADY_CLOSED` | REQ-VOZ-02 |

## 3. Requisitos (EARS)

- **REQ-VOZ-01** Cuando la sesión no exista, sea de otra persona o su identificador no sea un UUID canónico, el sistema debe responder 404 `SESSION_NOT_FOUND` sin leer el audio ni transcribir.
- **REQ-VOZ-02** Cuando la sesión no esté `EN_CURSO`, el sistema debe responder 409: `SESSION_ABANDONED` (ABANDONADA), `SESSION_ALREADY_CLOSED` (EVALUANDO o FINALIZADA) o `SESSION_TRANSITION_NOT_ALLOWED` (CONFIGURADA).
- **REQ-VOZ-03** Cuando la sesión se configuró con respuesta `TEXTO`, el sistema debe responder 422 `AUDIO_NOT_ALLOWED` «Esta entrevista es por texto: escribe tu respuesta.».
- **REQ-VOZ-04** El sistema debe aceptar un audio de hasta 10 485 760 bytes; por encima, 413 `AUDIO_TOO_LARGE` «Tu audio no puede superar los 10 MB.»; sin la parte `audio` o vacía, 422 `AUDIO_REQUIRED`. Una petición de más de 12 MiB la rechaza el contenedor con 413 `REQUEST_PAYLOAD_TOO_LARGE`.
- **REQ-VOZ-05** El sistema debe aceptar solo WebM cuyas pistas sean todas `A_OPUS` y MP4 cuyas pistas sean todas de sonido con muestras `mp4a`, decididos por los bytes; cualquier otro contenido (MP3, WAV, Vorbis, video, texto, ejecutable, archivo ilegible) responde 415 `AUDIO_INVALID_FORMAT` «Este formato de audio no es compatible. Graba de nuevo o responde por texto.» sin revelar la causa.
- **REQ-VOZ-06** La duración es la mayor entre la última marca de tiempo, la duración declarada y la suma de los paquetes (TOC de Opus, 1024 tramas por muestra AAC). Más de 181 s → 422 `AUDIO_TOO_LONG` «Tu respuesta no puede durar más de 3 minutos.»; menos de 1,5 s → 422 `AUDIO_TOO_SHORT` «No detectamos tu voz, intenta de nuevo.».
- **REQ-VOZ-07** El sistema debe transcribir con un solo intento y como máximo `TRANSCRIPTION_TIMEOUT` (90 s). Fallo → 503 `TRANSCRIPTION_UNAVAILABLE`; texto en blanco → 422 `TRANSCRIPTION_EMPTY`; más de 4000 caracteres → 422 `TRANSCRIPTION_TOO_LONG`. En los tres casos la pregunta sigue pendiente (turno `RETRYING`).
- **REQ-VOZ-08** Con la transcripción válida, en una transacción: insertar `intento_respuesta` (`INICIAL`, definitiva, `AUDIO`, texto, pregunta mostrada), completar el turno, abrir el turno de la pregunta k+1 o, tras la 5.ª, pasar la sesión a `EVALUANDO`; responder 200 con `turn`, `progress`, `nextQuestion` e `interviewFinished`, nunca con el texto ni retroalimentación.
- **REQ-VOZ-09** Mientras un audio del mismo turno se transcribe, otro envío debe responder 409 `SESSION_CONFLICT` sin transcribir.
- **REQ-VOZ-10** Con `Idempotency-Key` (1 a 64 de `A-Za-z0-9._-`), la respuesta 200 se guarda con la huella SHA-256 de sesión, turno y audio; repetir la clave con la misma huella devuelve la misma respuesta, aun con la sesión ya `EVALUANDO`; con otra huella, 409 `IDEMPOTENCY_KEY_CONFLICT`; clave mal formada, 422 `VALIDATION_FAILED`.
- **REQ-VOZ-11** El sistema no debe guardar el audio en disco, base, bucket ni log, ni leer el nombre ni el tipo declarado del archivo; los bytes se sobrescriben con ceros al terminar la petición.
- **REQ-VOZ-12** Un `turnId` ausente, mal formado, inexistente o de otra sesión responde 404 `TURN_NOT_FOUND`; uno de la sesión ya respondido, 409 `TURN_TRANSITION_NOT_ALLOWED`.

## 4. Orden de las comprobaciones

401 identidad → 404 sesión → repetición de la clave → 409 estado → 422 forma Texto → 404/409 turno → 422/413 tamaño
→ 415 formato → 422 duración → reserva del turno → transcripción → 422/503 transcripción → cierre. La forma Texto va antes
del turno y del audio porque en esa entrevista ninguna otra comprobación del audio tiene sentido (diferencia con el orden
404 → 409 → 413 → 415 → 422 del backlog).

## 5. Datos

Sin tablas ni columnas nuevas. `V8__respuesta_por_voz.sql` amplía `ck_clave_idempotencia_operacion` con
`RESPONDER_AUDIO`; es idéntica byte a byte a la de CM-349. Se usan `intento_respuesta` (`origen_respuesta = 'AUDIO'`,
`idioma_detectado` nulo: lo llena CM-349) y `turno`. No se registra `ejecucion_ia` `TRANSCRIBIR` (CA-5.12.3, de CM-349).

## 6. Códigos nuevos

`AUDIO_REQUIRED` 422, `AUDIO_TOO_LARGE` 413, `AUDIO_INVALID_FORMAT` 415, `AUDIO_TOO_LONG` 422, `AUDIO_TOO_SHORT` 422,
`AUDIO_NOT_ALLOWED` 422, `TURN_NOT_FOUND` 404, `SESSION_ABANDONED` 409, `SESSION_ALREADY_CLOSED` 409,
`TRANSCRIPTION_UNAVAILABLE` 503, `TRANSCRIPTION_EMPTY` 422, `TRANSCRIPTION_TOO_LONG` 422. Filas en `docs/errores.md`.
Causas nuevas del vocabulario: `TOO_LARGE`, `ABANDONED`, `ALREADY_CLOSED` y `EMPTY` (las tres últimas, del contrato que
ya consume la web).

## 7. Fuera de alcance

Evaluación de la respuesta por la IA (HU-5.2), idioma detectado y rechazo por idioma (HU-5.12, CM-349), reintento de la
transcripción (CM-362), respuesta por texto (HU-5.2), registro del consumo de minutos de voz (cola 2).

## 8. Decisiones (pendientes de ratificar en el PR)

| # | Decisión | Por qué | Alternativa descartada |
|---|---|---|---|
| D-345-1 | CM-345 hace el endpoint y un adaptador mínimo de transcripción; CM-349 el resto de la transcripción | Elegido por la persona responsable el 11-nov-2026 | Todo en CM-345; apilar sobre CM-349 |
| D-345-2 | Gemini con el audio en línea (`inline_data`) | La llave y el cliente ya existen; nada queda almacenado en el proveedor ni en un bucket | API de archivos de Gemini (guarda 48 h); AssemblyAI (lo usa CM-349: hay que unificar, ver §9) |
| D-345-3 | Concurrencia con el control de versión del turno (`PENDING/RETRYING → PROCESSING`) | Sin índice nuevo ni conexión abierta durante la transcripción | Índice único parcial en `ejecucion_ia` (choca con el esquema de CM-349) |
| D-345-4 | Duración = máximo de tres medidas; tolerancia 181 s | Un archivo manipulado no puede esconder un audio largo; 3:00 de reloj se acepta | Confiar solo en las marcas de tiempo o en la duración declarada |
| D-345-5 | Límite del multipart 12 MiB y umbral de disco 13 MiB | 10 MiB + 1 recibe el mensaje del criterio y el audio nunca toca el disco | Mantener 10 MB (el criterio recibiría el mensaje genérico) |
| D-345-6 | Sin evaluación: el turno queda `COMPLETED` | HU-5.2 no está en `develop`; el turno abierto se define como «no completado» | Dejar el turno en `PROCESSING` (la entrevista no avanzaría) |
| D-345-7 | `AUDIO_TOO_SHORT` con 1,5 s | Evita pagar una transcripción imposible; la interfaz exige 2 s de voz | No comprobar en el servidor |

## 9. Riesgos

- **Solapamiento con CM-349**: también implementa `…/answers/audio` y transcribe con AssemblyAI (ver `product/local-dev`). Hay que decidir un solo proveedor y una sola implementación al integrar; los conflictos previsibles están en `ErrorCode`, `BusinessExceptionHandler`, `docs/errores.md`, el controlador y `application.properties`.
- El nivel gratuito de Gemini puede usar el contenido para mejorar productos (hallazgo H-17 de CM-212); el aviso de CA-5.8.24 dice que el servicio externo elimina el audio a los 2 días: revisar con producto qué proveedor y nivel cumplen ese texto.
