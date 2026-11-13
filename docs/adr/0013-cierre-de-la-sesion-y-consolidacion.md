# ADR 0013 · Cierre de la sesión y consolidación

## Estado

Aceptada. Decisiones de Backend ratificadas por Paula Andrea Muñoz Delgado, 7 de noviembre de 2026.

## Contexto

Cuando la persona responde la quinta pregunta, la entrevista termina: la sesión debe quedar `FINALIZADA` con su fecha de fin, y las cinco evaluaciones ya guardadas se reúnen en ese cierre. El cierre no llama a la IA, pero puede fallar (la base cae un instante, otra petición cierra la misma sesión a la vez) y la persona nunca debe perder su respuesta ni quedar con una entrevista que no avanza. Cloud Run limita la CPU de un contenedor cuando no hay una petición en curso, por lo que un trabajo «en segundo plano» después de responder no tiene garantía de ejecutarse.

## Decisión

- **D-220-1 · El cierre ocurre en la misma petición de la quinta respuesta.** Al confirmar la evaluación del turno cinco, la sesión pasa `EN_CURSO → EVALUANDO` en la misma transacción. Justo después, fuera de esa transacción, `ConsolidateSessionService` pasa `EVALUANDO → FINALIZADA` en transacciones propias: hasta tres intentos, cada uno corto, y la espera entre intentos no mantiene ninguna conexión tomada.
- **D-220-2 · Un código por estado de la sesión en `answers`.** `ABANDONADA` responde `SESSION_ABANDONED`; `EVALUANDO` y `FINALIZADA`, `SESSION_ALREADY_CLOSED`; `CONFIGURADA`, `SESSION_TRANSITION_NOT_ALLOWED`. Ya lo aplica el registro de la respuesta; esta decisión lo ratifica para el cierre.
- **D-220-3 · Código propio del cierre fallido.** `SESSION_CONSOLIDATION_FAILED` (503, «Ocurrió un error. Inténtalo de nuevo.»). Nunca termina en el error genérico.
- **D-220-4 · Esperas de 200 ms y 400 ms** antes del segundo y del tercer intento: la petición de la quinta respuesta añade como máximo 0,6 s al peor caso.
- **Regla de cierre en el dominio.** `InterviewSession.closeIfComplete` solo mueve una sesión `EN_CURSO` con cinco turnos completos; en cualquier otro estado no hace nada.
- **Datos incompletos no se reintentan.** Si faltan turnos completos o retroalimentación, el cierre responde `SESSION_CONSOLIDATION_FAILED` de inmediato y la sesión sigue `EVALUANDO`; reintentar no arregla un dato roto.
- **Una consolidación que pierde la carrera** (otra petición cerró la sesión primero) relee la sesión ya `FINALIZADA` y termina sin cambios.
- **Reintento de la persona.** `POST /api/v1/interviews/sessions/{sessionId}/consolidation` no recibe cuerpo (lo ignora), devuelve el estado de una sesión `FINALIZADA` (200) o la cierra si está `EVALUANDO`; sobre una finalizada es repetible y no cambia nada. `CONFIGURADA`, `EN_CURSO` o `ABANDONADA` responden 409 `SESSION_TRANSITION_NOT_ALLOWED`; una sesión ajena, inexistente o con identificador mal escrito, el mismo 404 `SESSION_NOT_FOUND`.
- **Ninguna respuesta lleva la retroalimentación.** Una regla de arquitectura impide que la capa de presentación dependa de la retroalimentación o de la evaluación.
- **Sin migración.** El esquema ya tenía `fecha_fin` con su restricción para `FINALIZADA`.

## Consecuencias

- Frontend recibe un código nuevo, `SESSION_CONSOLIDATION_FAILED`, y un endpoint nuevo, `consolidation`. Tras la 200 de la pregunta cinco llama a `consolidation`; con 503 muestra «Ocurrió un error. Inténtalo de nuevo.» y «Reintentar»; al recargar con la sesión `EVALUANDO` vuelve a llamarlo.
- Si el cierre falla tres veces, la sesión queda `EVALUANDO` con todas sus respuestas guardadas y se recupera con una llamada a `consolidation`; nada se pierde.
- La quinta respuesta puede tardar hasta 0,6 s más cuando el cierre necesita reintentos; el caso normal no espera.

## Alternativas descartadas

- Cerrar en segundo plano después de responder: con la CPU limitada sin petición en curso, el cierre podría no ejecutarse nunca.
- Un proceso periódico que cierre sesiones `EVALUANDO`: exige programación nueva y trabajo de DevOps para un caso raro.
- Un solo código para todos los estados de `answers`: Frontend no podría distinguir una entrevista abandonada de una ya cerrada.
- Esperas de 1 s y 2 s: la petición podría durar hasta 3 s.
- Reintentar los datos incompletos: un dato roto no mejora con el tiempo y ocuparía la petición.
