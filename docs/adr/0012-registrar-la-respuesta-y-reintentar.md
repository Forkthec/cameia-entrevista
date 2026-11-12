# ADR 0012 · Registrar la respuesta, evaluarla sin mostrarla y reintentar

## Estado

Aceptada. Decisiones de Backend ratificadas por Paula Andrea Muñoz Delgado, 11 de noviembre de 2026.

## Contexto

`POST /api/v1/interviews/sessions/{sessionId}/answers` guarda la respuesta escrita de la persona, la evalúa con el modelo de lenguaje sin mostrarle el resultado, y abre la pregunta siguiente. `POST .../turns/{turnId}/reprocess` repite la evaluación cuando falló. La llamada al modelo tarda hasta el tiempo máximo configurado (20 s), el grupo de conexiones a la base es de dos y Frontend puede repetir la petición (doble clic, recarga, «Reintentar»). La respuesta nunca debe perderse: si la IA falla, queda guardada y la persona reintenta.

## Decisión

- **Dos transacciones cortas y la llamada a la IA fuera de ambas.** La primera guarda la respuesta, pasa el turno a `PROCESSING`, abre la ejecución de IA `EVALUAR` y deja la clave de idempotencia en 202 (en proceso). La llamada al modelo se hace sin transacción. La segunda guarda la retroalimentación, completa el turno, abre la pregunta siguiente (o termina la entrevista tras la quinta) y cierra la ejecución.
- **La clave se cierra en la misma transacción que la evaluación.** El resultado (200, o el 503 o 409 con que termina) se guarda en la clave dentro de la segunda transacción, nunca después: así una repetición con la misma clave no ve el turno resuelto con la clave todavía en proceso.
- **Misma clave mientras la primera espera a la IA:** la segunda petición espera sin ocupar la base y devuelve el mismo resultado; si la primera no termina dentro del tiempo máximo de evaluación, `409 TURN_CONFLICT`. Con la evaluación ya caída, `503 LLM_UNAVAILABLE` de inmediato.
- **El contador de reintentos vive en el turno** (`turno.reintentos_manuales`, 0 a 4, migración V8). Cada uso que llega a evaluar suma 1; el quinto se rechaza con `409 TURN_RETRY_LIMIT_REACHED` y la respuesta sigue guardada.
- **Un turno `PROCESSING` cuya ejecución caducó se trata como `RETRYING`** (tiempo máximo del modelo más 15 s), para que un proceso que murió a mitad de camino no deje la pregunta bloqueada.
- **La retroalimentación nunca sale en la respuesta.** `answers` y `reprocess` devuelven solo el turno, el avance, la pregunta siguiente y si la entrevista terminó. El estado agrega `status` y `retriesUsed` a la pregunta pendiente y un `history` con cada pregunta respondida (`number`, `text`, `answerText`).
- **Un código por estado de la sesión:** `ABANDONADA` → `SESSION_ABANDONED`; `EVALUANDO` o `FINALIZADA` → `SESSION_ALREADY_CLOSED`; `CONFIGURADA` → `SESSION_TRANSITION_NOT_ALLOWED`. Si la sesión se abandona mientras la IA evalúa, el turno no se completa, la ejecución se cierra como fallida y la clave guarda el 409.
- **Sin texto de la persona en registros, errores ni claves.** La clave guarda el cuerpo de la respuesta sin la respuesta escrita; el registro lleva solo identificadores y códigos.

## Consecuencias

- Frontend recibe trece códigos nuevos: `SESSION_ABANDONED`, `SESSION_ALREADY_CLOSED`, `TURN_NOT_FOUND`, `ANSWER_ALREADY_EXISTS`, `TURN_CONFLICT`, `TURN_NOT_RETRYABLE`, `TURN_RETRY_LIMIT_REACHED` y seis de validación de campo (`TURN_ID_REQUIRED`, `TURN_ID_INVALID_FORMAT`, `ANSWER_TEXT_REQUIRED`, `ANSWER_TEXT_TOO_LONG`, `ANSWER_TEXT_INVALID_CHARACTERS`, `IDEMPOTENCY_KEY_REQUIRED`).
- Una petición puede tardar hasta el tiempo máximo del modelo; el riesgo aceptado es el mismo que en iniciar la sesión.
- Si la segunda transacción falla (la base cae después de la IA), responde `503 DATABASE_UNAVAILABLE`; la respuesta queda en `PROCESSING` y a los 35 s el estado la muestra como `RETRYING`.
- El tope de cinco usos de «Reintentar» se mantiene al recargar o cambiar de dispositivo, porque lo cuenta el Backend.

## Alternativas descartadas

- Contar los reintentos solo en Frontend: se pierde al recargar y una llamada directa reevalúa sin fin y gasta cupo.
- `409` inmediato a la misma clave en proceso: no cumple que la repetición devuelva el mismo resultado.
- Guardar el resultado en la clave después de cerrar la evaluación: deja una ventana en la que la repetición recibe un 409 falso.
- Completar el turno en una sesión ya abandonada: deja turnos abiertos que no sirven.
- Una sola transacción que incluya la llamada a la IA: con dos conexiones, dos peticiones agotan el grupo.
