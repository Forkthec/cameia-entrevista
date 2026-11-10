# ADR 0011 · Iniciar la sesión y planificar las preguntas

## Estado

Aceptada. Decisiones de Backend ratificadas por Paula Andrea Muñoz Delgado, 7 y 10 de noviembre de 2026.

## Contexto

`POST /api/v1/interviews/sessions/{sessionId}/start` convierte la sesión configurada en una entrevista en curso: pide a Gemini las cinco preguntas, las valida y las guarda con el primer turno. La llamada al modelo tarda hasta el tiempo máximo configurado (9,5 s), el grupo de conexiones a la base es de dos y Frontend puede repetir la petición (botón «Reintentar», doble clic). La persona tiene a lo sumo una sesión en curso (índice único parcial) y a lo sumo tres intentos de planificación por sesión configurada.

## Decisión

- **Marca de planificación en la base, no candado.** Antes de llamar al modelo se guarda una ejecución de IA `PLANIFICAR` en estado `EN_CURSO`; un índice único parcial (`uq_ejecucion_ia_planificacion_en_curso`, migración V6) permite una sola por sesión. Quien llega con una marca vigente recibe `409 SESSION_CONFLICT` sin usar la IA. La marca caduca al pasar el tiempo máximo del modelo más 15 s: una marca vieja (proceso que murió) se cierra como fallida y se planifica de nuevo.
- **Ninguna transacción abierta durante la llamada.** Tres fases: reserva (transacción corta), llamada y validación (sin transacción) y cierre (transacción corta). Un fallo en el cierre revierte todo y la marca se cierra como fallida en una transacción propia.
- **Una petición `start` hace como máximo una llamada a la IA.** Un plan inválido es `LLM_INVALID_OUTPUT` y la persona reintenta con el botón; el reintento automático queda para la tarea de reintentos del cliente del modelo.
- **Tres intentos por sesión configurada.** Los dos primeros fallos responden la causa (`LLM_UNAVAILABLE`, `LLM_LIMIT_REACHED` o `LLM_INVALID_OUTPUT`); el tercero y todo pedido posterior, `PLANNING_RETRIES_EXHAUSTED`, sin llamar a la IA. Reconfigurar la sesión reinicia el contador y borra su historial de planificación.
- **El plan se valida estrictamente.** Idioma igual al de la sesión, cinco preguntas en las posiciones 1 a 5, tipos según la plantilla de competencias (apertura, técnica, conductual, técnica, situacional), texto de 1 a 500 puntos de código y sin preguntas repetidas. Cualquier otra forma es `LLM_INVALID_OUTPUT`; el texto del modelo no se copia al error ni al registro.
- **El prompt es `planificacion.v4` con los datos de la persona delimitados.** El perfil y la oferta viajan entre marcas que se declaran datos y no instrucciones; si el texto trae la marca de cierre, se neutraliza. El contexto tiene un tope de 32.000 puntos de código: por encima responde `LLM_UNAVAILABLE` sin crear marca ni gastar un intento. `version_prompt` queda como `planificacion.v4+<TONO>.v<N>`.
- **El nombre del rol sale de la copia del perfil congelada en la sesión**, sin consultar otra fuente.
- **La entrevista anterior se abandona dentro de la misma transacción de cierre**, antes de iniciar la nueva, porque el índice único no admite dos en curso. Si el cierre falla, la anterior sigue en curso.
- **Repetir `start` sobre una sesión `EN_CURSO` responde 200 con su estado** y no llama a la IA. En `EVALUANDO`, `FINALIZADA` o `ABANDONADA` responde `409 SESSION_TRANSITION_NOT_ALLOWED`.
- **`GET .../state` y `DELETE .../{sessionId}` viven en esta misma tarea.** El estado nunca incluye retroalimentación; cancelar solo borra sesiones `CONFIGURADA`, con sus ejecuciones de IA en cascada.
- **Ajena, inexistente o con identificador que no es un UUID canónico: el mismo `404 SESSION_NOT_FOUND`**, con el mismo cuerpo, para no revelar qué sesiones existen.
- **La columna `pregunta.texto_original` baja a 500** (migración V7), igual que el límite validado.

## Consecuencias

- Dos inicios simultáneos nunca gastan dos llamadas: uno responde 200 y el otro 409.
- Un error de SQL que ninguna regla prevé durante el cierre responde el `500` genérico; la pérdida de conexión se traduce a `503 DATABASE_UNAVAILABLE`. En ambos casos la sesión sigue `CONFIGURADA`, la anterior en curso y la marca cerrada como fallida.
- Una de cada cincuenta peticiones puede superar los 9,5 s y responder `LLM_UNAVAILABLE`; es un riesgo aceptado y la persona puede reintentar.
- Frontend recibe cuatro códigos nuevos: `SESSION_NOT_FOUND` (404), `LLM_INVALID_OUTPUT`, `PLANNING_RETRIES_EXHAUSTED` y `PROFILE_SNAPSHOT_INVALID` (503).
- Reconfigurar la sesión (`POST /sessions`) mientras la IA prepara las preguntas responde `409 SESSION_CONFLICT` y no toca la marca: reconfigurar la borraría y dejaría pasar un segundo inicio. Si aun así la configuración cambió durante la llamada (por ejemplo, la marca caducó), el cierre compara la configuración con la que armó el prompt y responde `409 SESSION_CONFLICT` sin guardar nada.
- Si la copia del perfil congelada en la sesión supera 32.000 caracteres de contexto, no es legible o no trae el rol, `start` responde `503 PROFILE_SNAPSHOT_INVALID` antes de reservar: no llama a la IA ni gasta intentos, porque reintentar no lo arregla hasta que la persona actualice su perfil.
- El estado de la sesión (`start` y `state`) incluye `answerFormat` (`TEXTO` o `AUDIO`) en todos los estados, para que el cliente abra el chat de texto o de audio también al retomar la entrevista.
- El validador del plan rechaza como `LLM_INVALID_OUTPUT` cualquier carácter de control en el texto de una pregunta: la base lo rechazaría al guardar.

## Alternativas descartadas

- Candado de base abierto durante la llamada: con dos conexiones, dos pedidos agotan el grupo.
- No bloquear y dejar ganar al primero en guardar: el segundo gastaría una llamada a la IA.
- Reintentar automáticamente dentro de `start` un plan inválido: hasta seis llamadas por sesión, 19 s en el peor caso, y rompe el tope de tres.
- Usar el prompt `v3` sin delimitar los datos: no impide que un texto hostil sesgue las preguntas.
- Permitir preguntas repetidas en un plan: se rechazan como salida inválida.
- Responder 403 a una sesión ajena: revelaría que existe.
