# ADR 0008 · Réplica del perfil profesional por evento

## Estado

Aceptada. Decisiones de Backend ratificadas por Paula Andrea Muñoz Delgado, 22 de octubre de 2026.

## Contexto

Crear una sesión de entrevista necesita el perfil profesional de la persona. Llamar a Perfil en cada creación acopla la disponibilidad de Entrevista a la de Perfil y mete una dependencia de red en un flujo que debe responder rápido. Perfil ya publica `perfil.profesional-actualizado` cada vez que el perfil queda guardado, así que Entrevista puede mantener su propia copia y leerla localmente.

## Decisión

- **Copia local alimentada por el evento.** Entrevista consume `perfil.profesional-actualizado` y guarda la copia en `contexto_profesional_replica`; el contrato está en `docs/eventos/` y la operación en `consumo-perfil-profesional-actualizado.md`.
- **Sin `embedding`.** No se calcula al consumir: el contrato no lo lleva, el puerto del modelo solo devuelve texto y una llamada al modelo dentro de la transacción metería costo y una dependencia externa. Quien lo use agrega su columna y su cálculo.
- **`estado` tal como llega.** Se guarda `COMPLETED` y no una traducción; «completo» se expresa con `ProfileReplica.isUsableForInterview()`, que exige `COMPLETED` y fecha de archivado nula.
- **Dos `jsonb`, no tablas hijas.** El contexto profesional y los roles aspirados se guardan enteros: Entrevista solo los lee completos para copiarlos a la sesión y armar el prompt, nunca filtra por ellos.
- **Pruebas con PostgreSQL y RabbitMQ reales.** Además de las unitarias, la cola, el exchange, la cola de fallidos, los reintentos y la confirmación tras el commit se prueban contra un broker real, con el mismo patrón que Perfil.
- **Un cambio de dueño se rechaza.** El dueño de un perfil no cambia en Perfil; aceptar un mensaje que lo cambie daría a un error la capacidad de pasar un perfil a otra persona.
- **Fecha igual se aplica.** Se descarta solo un `actualizadoEn` anterior al guardado; con el mismo instante el contrato garantiza el mismo contenido.
- **Entrevista declara su propia topología al arrancar.** Como hace Perfil, con los mismos argumentos de cola para que la declaración sea idempotente.
- **Idempotencia con una bandeja de entrada.** La tabla `evento_procesado` registra cada `message_id` en la misma transacción que la copia; la confirmación al broker ocurre después del commit.

## Consecuencias

- Crear una sesión no depende de que Perfil esté disponible, pero sí de que el evento haya llegado: una persona que acaba de completar su perfil puede no tener copia durante unos segundos.
- Un mensaje inválido o con otro dueño termina en la cola de fallidos con su razón en el registro; reprocesarlo es seguro.
- Entrevista guarda datos personales (el perfil) en una segunda base; los registros nunca los contienen.
- Una versión 2 del evento exige trabajo nuevo: la 1 sigue funcionando y la 2 se rechaza hasta que se implemente.

## Alternativas descartadas

- Llamar a Perfil por HTTP al crear la sesión: acopla las disponibilidades y suma latencia.
- Calcular el `embedding` al consumir: costo, modelo y dimensión sin que nadie lo use todavía.
- Traducir `COMPLETED` a otro vocabulario: nadie más lo usa.
- Tablas hijas de experiencias, formaciones, habilidades y roles: cuatro tablas y cuatro mapeos sin una consulta que los necesite.
- Solo PostgreSQL real y pruebas unitarias, sin broker: no probaba la topología ni la confirmación tras el commit.
- Aplicar un cambio de dueño: un mensaje erróneo movería un perfil a otra persona.

## Catálogo de roles

Para validar el rol objetivo de una sesión, Entrevista también copia el catálogo de roles profesionales de Perfil, con el evento `perfil.catalogo-roles-actualizado` (operación en `consumo-perfil-catalogo-roles-actualizado.md`).

### Decisión

- **Una instantánea completa en un solo mensaje.** El catálogo son 46 filas (menos de 10 KB) y solo cambia con un despliegue; con una instantánea, la tabla vacía de un servicio nuevo se llena con un solo mensaje y una sola transacción.
- **Perfil publica en cada arranque** (si no hay otro mensaje pendiente). Es la forma más simple de cumplir la regla y es idempotente; el costo es un mensaje pequeño por arranque en frío.
- **`actualizadoEn` por fila.** Cada fila se reemplaza solo si el instante de la instantánea es igual o posterior al guardado, sin una tabla aparte con una versión global.
- **Los roles ausentes no se borran.** Perfil desactiva, nunca borra; borrar por omisión de un mensaje parcial dejaría sesiones con un rol inexistente.
- **Una sola sentencia nativa por instantánea** (`INSERT … SELECT … FROM jsonb_to_recordset … ON CONFLICT DO UPDATE … WHERE`): evita 46 viajes a la base y resuelve la concurrencia sin error. Por eso un `codigo` repetido dentro del mensaje se rechaza antes (`CATALOG_ROLE_DUPLICATED`): PostgreSQL no permite que una sentencia afecte dos veces la misma fila.
- **Mismas piezas que el evento del perfil.** Misma bandeja de entrada (`evento_procesado`, cuyo `CHECK` ahora admite los dos tipos), mismos reintentos y misma cola de fallidos por evento.

### Consecuencias

- Una sesión no puede validar un rol hasta que llegue la primera instantánea; con la tabla vacía, todo rol es desconocido.
- Un rol desactivado sigue en la copia con estado `INACTIVE`.
- Varias instancias de Perfil que arrancan a la vez pueden publicar una instantánea cada una; el orden por fila lo resuelve.

### Alternativas descartadas

- Un mensaje por rol cambiado: exige saber qué cambió y no llena una tabla vacía de una vez.
- Guardar una huella para publicar solo si el catálogo cambió: agrega estado para ahorrar un mensaje pequeño.
- Borrar los roles que una instantánea no trae: un mensaje parcial dejaría sesiones con un rol inexistente.
- Una fila por rol con `INSERT` individual: 46 viajes a la base por mensaje.

