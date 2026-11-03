# Consumo de `perfil.profesional-actualizado`

Entrevista mantiene una copia local del Perfil Profesional de cada persona para crear una sesión sin llamar a Perfil. La copia se alimenta con el evento que Perfil publica cada vez que el perfil queda guardado. Este documento describe qué consume Entrevista, qué valida, qué guarda y cómo se opera.

## Qué consume y de dónde

| Elemento | Valor |
|---|---|
| Exchange de origen | `perfil.events` (tipo `topic`, durable) |
| Clave de enrutamiento | `perfil.profesional-actualizado` |
| Cola | `entrevista.perfil-profesional-actualizado` (durable; fallidos a `entrevista.dlx` con la clave igual al nombre de la cola) |
| Exchange de fallidos | `entrevista.dlx` (tipo `direct`, durable) |
| Cola de fallidos | `entrevista.perfil-profesional-actualizado.dlq` (durable, sin argumentos) |
| Contrato | [`perfil-profesional-actualizado-v1.schema.json`](perfil-profesional-actualizado-v1.schema.json), copia literal del esquema publicado por Perfil (`docs/eventos/perfil-profesional-actualizado-v1.schema.json`, `cameia-perfil`, commit `08480cd` de `develop`) |

Entrevista declara estos elementos al arrancar. Los argumentos de la cola son idénticos a los que declara Perfil: si difirieran, RabbitMQ respondería `PRECONDITION_FAILED` y el servicio no consumiría. Si el broker no está disponible el servicio arranca y atiende HTTP; el consumidor reintenta la conexión.

## Qué valida

Se comprueba en este orden, y el primer fallo rechaza el mensaje:

1. Propiedades AMQP: `message_id` (UUID canónico), `type` igual a `perfil.profesional-actualizado` y encabezado `x-event-version` entero igual a `1` (se acepta cualquier tipo entero del protocolo; el texto `"1"` se rechaza).
2. Forma del cuerpo: JSON con los tipos del contrato.
3. Reglas del contrato v1: largos en puntos de código Unicode (`nombre` 1 a 255, `resumen` 1 a 2000, `usuarioId` 1 a 128, textos de experiencias, formaciones y habilidades según el esquema), cantidades (0 a 4 experiencias, 1 a 5 formaciones, 1 a 20 habilidades, 1 a 5 roles aspirados), valores permitidos de `estado`, `nivel`, `estadoEmpleo`, meses `AAAA-MM`, `actualizadoEn` con milisegundos y `Z` y fecha real. Ningún texto puede contener el carácter nulo, que PostgreSQL no guarda.
4. Dueño: `usuarioId` debe cumplir las reglas de `firebaseUid` del servicio.

Los campos que el contrato no define se ignoran y no se guardan (el contrato permite agregar campos opcionales dentro de la misma versión).

## Razones de rechazo

Un rechazo no se reintenta: el mensaje va a la cola de fallidos y queda una sola línea `WARN` con la cola, el `messageId` saneado y la razón. Nunca se escribe el cuerpo ni el valor de un campo.

| Razón | Cuándo |
|---|---|
| `MESSAGE_ID_INVALID` | `message_id` ausente o que no es un UUID canónico |
| `EVENT_TYPE_INVALID` | `type` ausente o distinto de `perfil.profesional-actualizado` |
| `EVENT_VERSION_UNSUPPORTED` | `x-event-version` ausente, distinta de `1` o que no es un entero |
| `PAYLOAD_INVALID_FORMAT` | cuerpo vacío, que no es JSON o con un tipo equivocado en un campo |
| `PAYLOAD_CONTRACT_VIOLATION` | cuerpo que incumple una regla del contrato; el `WARN` lleva la ruta del primer campo que falla (por ejemplo `contextoProfesional.nombre`) |
| `PROFILE_OWNER_MISMATCH` | el perfil ya está copiado con otro `usuarioId` |

Un fallo técnico (base caída, error de base de datos, cualquier otro fallo inesperado) se reintenta: 3 intentos en total, con esperas de 1 s y 2 s. Agotados, el mensaje va a la cola de fallidos con una línea `ERROR` (cola, `messageId` saneado y clase de la causa) y traza sin los mensajes de las excepciones.

## Idempotencia y orden

- **Idempotencia.** Cada `message_id` se anota en la tabla `evento_procesado` en la misma transacción que la copia. Si el broker entrega otra vez el mismo mensaje (Perfil publica uno por cada arranque en frío), no tiene efecto. La confirmación al broker ocurre después del commit.
- **Orden.** Una copia existente solo se reemplaza si el `actualizadoEn` del mensaje es igual o posterior al guardado. Un mensaje más viejo se anota y no cambia la copia. Dos mensajes simultáneos del mismo perfil se resuelven en la base sin error.
- **Dueño.** Un perfil tiene un solo dueño. Un mensaje que lo cambia se rechaza sin escribir nada.

## Qué guarda y qué no

Guarda, en `contexto_profesional_replica` (una fila por perfil): identificador del perfil, `firebaseUid` del dueño, nombre, estado y fecha de archivado tal como llegan, el bloque `contextoProfesional` (resumen, experiencias, formaciones, habilidades) y los roles aspirados en dos columnas `jsonb`, el instante del evento y la fecha de réplica. `ProfileReplica.isUsableForInterview()` es verdadero solo con estado `COMPLETED` y sin fecha de archivado.

No guarda campos desconocidos ni calcula el `embedding`: el contrato de Perfil no lo lleva.

## Privacidad

Ningún registro contiene nombre, resumen, experiencias, formaciones, habilidades ni roles. Los registros llevan `perfilId`, `firebaseUid`, `messageId` saneado y la razón. Durante el procesamiento el `MDC` lleva `requestId` con el `correlation_id` del mensaje si es seguro (`^[A-Za-z0-9._-]{1,64}$`) y, si no, el `messageId`.

## Cómo reprocesar la cola de fallidos

1. Leer en el registro la razón del rechazo y corregir la causa (el productor, un despliegue o la base).
2. En la consola de RabbitMQ, abrir la cola `entrevista.perfil-profesional-actualizado.dlq` y usar *Move messages* (la «pala», o *shovel*) hacia `entrevista.perfil-profesional-actualizado`.
3. Comprobar que la cola de fallidos queda vacía y que existe la fila de `contexto_profesional_replica`.

El reproceso es seguro: el consumo es idempotente.

## Versión 2 del evento

Si Perfil publica una versión 2, llega en paralelo (otra clave o encabezado distinto). La versión 1 sigue funcionando; un mensaje con `x-event-version` distinto de `1` se rechaza con `EVENT_VERSION_UNSUPPORTED` hasta que Entrevista implemente el consumo de la nueva versión.

## Cómo se comprueba

- Pruebas automáticas con PostgreSQL y RabbitMQ reales: topología, rechazo sin reintentos, tres intentos y cola de fallidos, confirmación después del commit y aplicación una sola vez.
- Postman: carpeta «Réplica del perfil (RabbitMQ)», contra la API de administración del broker local (`docker compose --profile mensajeria up -d`).
