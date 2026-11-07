# ADR 0010 · Quitar el tono Motivador

## Estado

Aceptada. Decisiones ratificadas por Paula Andrea Muñoz Delgado: destino de las sesiones guardadas, 2 de noviembre de 2026; ventana de despliegue, 7 de noviembre de 2026.

## Contexto

El catálogo de personalización ofrecía cuatro tonos del entrevistador. El producto aprobó tres (Profesional y neutro, Cálido y Estricto) y el tono Motivador sale. El valor `MOTIVADOR` vivía en cuatro sitios: la restricción `ck_sesion_tono` de la tabla `sesion`, el enumerado `Tone`, el catálogo y la documentación de la API. Además, la respuesta que se guarda bajo cada `Idempotency-Key` (`clave_idempotencia.cuerpo_respuesta`) contiene la configuración serializada con el nombre de la constante del enumerado (`MOTIVATING`); si la constante desaparece, esa respuesta ya no se podría leer.

## Decisión

- **Migración `V5__quitar_tono_motivador.sql`, en una sola transacción de Flyway y en este orden:** (1) las sesiones con `MOTIVADOR` pasan a `PROFESIONAL_NEUTRO`, sin importar su estado, con `version + 1` y `fecha_actualizacion = now()`; (2) las respuestas guardadas cuya configuración trae `MOTIVATING` pasan a `PROFESSIONAL_NEUTRAL`, sin tocar la huella de la petición; (3) `ck_sesion_tono` se reemplaza por los tres tonos. Los `UPDATE` van antes del `ALTER` porque la base valida todas las filas al agregar la restricción.
- **`version + 1`** hace que una escritura concurrente de la revisión anterior sobre una fila reescrita falle por bloqueo optimista en vez de pisar el cambio.
- **`Tone` queda con tres constantes** y `Tone.fromCode("MOTIVADOR")` lanza `IllegalArgumentException`; el catálogo, que toma los valores del enumerado, ofrece tres tonos sin cambio de código.
- **Crear una sesión con `MOTIVADOR` responde `422 VALIDATION_FAILED`** con `tone` y `TONE_INVALID_VALUE` («Elige un tono de la lista.»). No se agrega ningún código de error.
- **Repetir una clave antigua:** con el cuerpo de Motivador, 422 (la validación va antes de consultar la clave); con la misma clave y otro tono, 409 `IDEMPOTENCY_KEY_CONFLICT`; con una clave de otro tono, la respuesta guardada de siempre.
- **Contrato restrictivo, avisado a Frontend:** `tones` pasa de cuatro a tres elementos y `tone` acepta tres valores. La pantalla de `cameia-web` ya oculta el tono.
- **Ventana de despliegue aceptada.** Cloud Run corre la migración al arrancar la revisión nueva mientras la anterior aún atiende; si en esos segundos alguien envía `MOTIVADOR`, la revisión anterior recibe una violación de `ck_sesion_tono`. Se acepta: un solo PR y un solo despliegue.

## Consecuencias

- El tono original de las sesiones antiguas se pierde. No hay migración hacia abajo; volver a ofrecer el tono exigiría una migración nueva y las filas reescritas no se recuperan.
- Antes de promover a producción conviene contar las sesiones con Motivador (`SELECT tono, estado, count(*) FROM microentrevista.sesion GROUP BY 1, 2`, solo lectura) para saber cuántas personas reales cambian de tono.
- Quien promueva debe confirmar con Frontend desde qué versión de la pantalla corre cada ambiente.
- Las migraciones siguientes toman los números `V6` en adelante.

## Alternativas descartadas

- **Borrar las claves anti-duplicados con el tono retirado:** pierde la respuesta que un reintento legítimo debería recibir.
- **Migración en dos pasos** (primero código que rechaza `MOTIVADOR`, después la migración): cierra la ventana, pero exige dos PR y dos despliegues por una ventana casi nula.
- **Borrar las sesiones con Motivador:** destruye historial de personas por un cambio de catálogo.
- **Dejar la restricción con cuatro valores y quitar el tono solo del enumerado:** la base seguiría admitiendo un valor que el código ya no lee, y `ToneConverter` lanzaría una excepción al cargar esas filas.
