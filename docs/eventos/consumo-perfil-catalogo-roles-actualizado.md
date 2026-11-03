# Consumo de `perfil.catalogo-roles-actualizado`

Entrevista mantiene una copia local del catálogo de roles profesionales de Perfil para validar el rol objetivo de una sesión sin llamar a Perfil. La copia se alimenta con el evento que Perfil publica al arrancar: una instantánea completa del catálogo, con roles activos e inactivos y sus dos idiomas. Un servicio nuevo, con la tabla vacía, se llena con un solo mensaje. Este documento describe qué consume Entrevista, qué valida, qué guarda y cómo se opera.

## Qué consume y de dónde

| Elemento | Valor |
|---|---|
| Exchange de origen | `perfil.events` (tipo `topic`, durable) |
| Clave de enrutamiento | `perfil.catalogo-roles-actualizado` |
| Cola | `entrevista.catalogo-roles-actualizado` (durable; fallidos a `entrevista.dlx` con la clave igual al nombre de la cola) |
| Exchange de fallidos | `entrevista.dlx` (tipo `direct`, durable) |
| Cola de fallidos | `entrevista.catalogo-roles-actualizado.dlq` (durable, sin argumentos) |
| Contrato | [`perfil-catalogo-roles-actualizado-v1.schema.json`](perfil-catalogo-roles-actualizado-v1.schema.json), copia literal del esquema publicado por Perfil (`docs/eventos/perfil-catalogo-roles-actualizado-v1.schema.json`, `cameia-perfil`, commit `08480cd` de `develop`) |

Entrevista declara estos elementos al arrancar. Los argumentos de la cola son idénticos a los que declara Perfil: si difirieran, RabbitMQ respondería `PRECONDITION_FAILED` y el servicio no consumiría. Si el broker no está disponible el servicio arranca y atiende HTTP; el consumidor reintenta la conexión.

## Qué valida

Se comprueba en este orden, y el primer fallo rechaza el mensaje:

1. Propiedades AMQP: `message_id` (UUID canónico), `type` igual a `perfil.catalogo-roles-actualizado` y encabezado `x-event-version` entero igual a `1`.
2. Forma del cuerpo: JSON con los tipos del contrato.
3. Reglas del contrato v1: `actualizadoEn` con milisegundos y `Z` y fecha real; `roles` de 1 a 200 elementos; en cada rol, `codigo` UUID, `nombreEs` y `nombreEn` de 1 a 255 puntos de código Unicode sin carácter nulo, y `estado` `ACTIVE` o `INACTIVE`.
4. Que ningún `codigo` se repita dentro de la instantánea (se comparan como UUID, sin distinguir mayúsculas).

Los campos que el contrato no define se ignoran y no se guardan.

## Razones de rechazo

Un rechazo no se reintenta: el mensaje va a la cola de fallidos y queda una sola línea `WARN` con la cola, el `messageId` saneado, la razón y, cuando aplica, la ruta del campo. Nunca se escribe el cuerpo ni el valor de un campo.

| Razón | Cuándo |
|---|---|
| `MESSAGE_ID_INVALID` | `message_id` ausente o que no es un UUID canónico |
| `EVENT_TYPE_INVALID` | `type` ausente o distinto de `perfil.catalogo-roles-actualizado` |
| `EVENT_VERSION_UNSUPPORTED` | `x-event-version` ausente, distinta de `1` o que no es un entero |
| `PAYLOAD_INVALID_FORMAT` | cuerpo vacío, que no es JSON o con un tipo equivocado en un campo |
| `PAYLOAD_CONTRACT_VIOLATION` | cuerpo que incumple una regla del contrato; el `WARN` lleva la ruta del primer campo que falla (por ejemplo `roles[0].nombreEn`) |
| `CATALOG_ROLE_DUPLICATED` | dos roles de la misma instantánea con el mismo `codigo`; el `WARN` lleva la ruta del segundo (por ejemplo `roles[1].codigo`) |

Un fallo técnico (base caída, error de base de datos, cualquier otro fallo inesperado) se reintenta: 3 intentos en total, con esperas de 1 s y 2 s. Agotados, el mensaje va a la cola de fallidos con una línea `ERROR` (cola, `messageId` saneado y clase de la causa).

## Idempotencia y orden

- **Idempotencia.** Cada `message_id` se anota en la tabla `evento_procesado` en la misma transacción que la copia (la misma bandeja que usa el evento del perfil). Si el broker entrega otra vez el mismo mensaje, no tiene efecto. La confirmación al broker ocurre después del commit.
- **Orden por rol.** Cada fila lleva el `actualizadoEn` de la instantánea que la escribió y solo se reemplaza si el de la instantánea nueva es igual o posterior. Una instantánea anterior se anota y no cambia nada (`STALE_IGNORED`); una igual se aplica.
- **Concurrencia.** Todo el catálogo se escribe con una sola sentencia `INSERT … ON CONFLICT (id) DO UPDATE … WHERE`, así que dos instantáneas simultáneas se resuelven en la base sin error de clave duplicada.
- **Los roles ausentes no se borran.** Perfil nunca borra un rol: lo desactiva. Un rol que una instantánea no trae se conserva tal cual.

## Qué guarda y qué no

Guarda, en `rol_profesional` (una fila por rol): el `codigo` como clave, los dos nombres, el estado, el instante de la instantánea y la fecha de réplica. `ProfessionalRoleReplica.isActive()` es verdadero solo con estado `ACTIVE`. No guarda campos desconocidos ni tiene claves foráneas: la tabla es una copia, no una relación con otro servicio.

## Privacidad

Ningún registro contiene los nombres de los roles. Los registros llevan la cola, el `messageId` saneado, la razón y la ruta del campo. Durante el procesamiento el `MDC` lleva `requestId` con el `correlation_id` del mensaje si es seguro (`^[A-Za-z0-9._-]{1,64}$`) y, si no, el `messageId`.

## Cómo reprocesar la cola de fallidos

1. Leer en el registro la razón del rechazo y corregir la causa (el productor, un despliegue o la base).
2. En la consola de RabbitMQ, abrir la cola `entrevista.catalogo-roles-actualizado.dlq` y usar *Move messages* (la «pala», o *shovel*) hacia `entrevista.catalogo-roles-actualizado`.
3. Comprobar que la cola de fallidos queda vacía y que `rol_profesional` tiene los roles.

El reproceso es seguro: el consumo es idempotente y el orden por rol impide que un mensaje viejo pise a uno nuevo.

## Cómo se comprueba

- Pruebas automáticas con PostgreSQL y RabbitMQ reales: topología, 201 roles a la cola de fallidos sin reintentos, 46 roles guardados y confirmados, reentrega, orden, concurrencia de dos hilos y base caída.
- Una prueba compara el esquema copiado con el contrato que lee el servicio y con los largos de la columna.
- Postman: carpeta «Réplica del perfil (RabbitMQ)», contra la API de administración del broker local (`docker compose --profile mensajeria up -d`).
