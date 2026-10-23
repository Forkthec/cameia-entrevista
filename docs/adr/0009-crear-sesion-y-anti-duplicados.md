# ADR 0009 · Crear la sesión y evitar duplicados

## Estado

Aceptada. Decisiones de Backend ratificadas por Paula Andrea Muñoz Delgado, 22 de octubre de 2026.

## Contexto

`POST /api/v1/interviews/sessions` deja lista la sesión de una persona antes de iniciar la entrevista. Frontend lo llama al confirmar la configuración, puede reintentarlo si la red falla y puede enviarlo dos veces por un doble clic. La persona tiene a lo sumo una sesión configurada y a lo sumo una en curso (índices únicos parciales del esquema inicial), y el perfil que se copia a la sesión llega por evento, así que puede tardar unos segundos en existir.

## Decisión

- **Una sola sesión configurada por persona.** Si ya existe, se actualiza (200, mismo `sessionId`, reintentos de planificación en 0); si no, se crea (201). La entrevista en curso nunca se toca: solo se informa en `ongoingSession`.
- **Validación completa en el dominio.** `SessionConfigurationPolicy` revisa todos los campos y devuelve todas las causas a la vez en un `422 VALIDATION_FAILED` con un elemento por campo; el cuerpo no lleva `@Valid`, para que el contrato HTTP no decida reglas de negocio.
- **`Idempotency-Key` opcional.** Con clave, se calcula la huella SHA-256 de la configuración normalizada (no del cuerpo crudo) y se guarda con el estado y el cuerpo de la respuesta en la misma transacción que la sesión. La misma clave y la misma huella repite la respuesta; otra huella da `409 IDEMPOTENCY_KEY_CONFLICT`. Sin clave, la regla de una sola sesión configurada ya impide duplicados.
- **Solo se guardan respuestas 2xx.** Así el reintento con la misma clave, después de un `409 PROFILE_SYNC_PENDING`, funciona cuando la copia del perfil llega.
- **Las carreras se resuelven fuera de la transacción revertida.** Si dos peticiones con la misma clave llegan a la vez, la perdedora falla con la clave duplicada o con el índice de la sesión configurada; en los dos casos se relee la clave y se repite la respuesta guardada. Sin clave, la perdedora recibe `409 SESSION_ALREADY_EXISTS`; dos reconfiguraciones simultáneas, `409 SESSION_CONFLICT` (`@Version`).
- **Copia del perfil en la sesión.** `{perfilId, nombre, contextoProfesional, rolesAspirados, actualizadoEn}`, tomada de la copia local (ADR 0008) y congelada: un cambio posterior del perfil no altera la sesión.
- **Un perfil ausente, incompleto o archivado es `409 PROFILE_SYNC_PENDING`; el de otra persona, `404 PROFILE_NOT_FOUND`**, sin distinguirlo de uno inexistente.
- **El rol objetivo se valida contra la copia local del catálogo de roles** (ADR 0008), sin llamar a Perfil.
- **Sin preguntas ni llamadas al modelo.** Crear la sesión no genera preguntas, turnos ni ejecuciones de IA.

## Consecuencias

- Frontend puede reintentar sin riesgo con la misma `Idempotency-Key`; una clave nueva con la misma configuración reutiliza la sesión.
- La respuesta repetida es la guardada, no una recalculada: si entre el primer intento y la repetición la entrevista en curso cambió, la repetición muestra el valor original.
- Una persona que acaba de completar su perfil puede recibir `PROFILE_SYNC_PENDING` unos segundos; el mensaje le pide reintentar.
- La tabla `clave_idempotencia` crece con cada clave; su limpieza queda fuera de esta tarea.

## Alternativas descartadas

- `Idempotency-Key` obligatoria: obliga a clientes que no la necesitan (pruebas, Postman).
- Huella del cuerpo crudo: un espacio o el orden de los campos daría un conflicto falso.
- Guardar también los 409 y 422 bajo la clave: el reintento tras `PROFILE_SYNC_PENDING` repetiría el error para siempre.
- Validar con `@Valid` en el borde HTTP: dos capas de reglas y mensajes que se desalinean.
- Un 422 propio para el perfil archivado: el archivado aún no existe como flujo y el criterio solo prevé 404 y 409.
