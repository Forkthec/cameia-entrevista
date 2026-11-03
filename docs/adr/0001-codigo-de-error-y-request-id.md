# ADR 0001 · Código de error y `requestId` en la respuesta de error

## Estado

Aceptada. Decisión de Backend, 5 de octubre de 2026.

## Contexto

Los servicios responden los errores con `ProblemDetail` (RFC 9457), una lista `errors` con `field` y `message` por campo rechazado y JSON en `camelCase`. Eso permite mostrar un mensaje, pero no deja un identificador estable para que el cliente actúe según la causa ni un hilo para depurar una petición entre el Gateway, el servicio y el registro.

## Decisión

Se agregan tres miembros de extensión, que la norma admite, sin reemplazar nada de lo ya publicado:

- `code`: el código de la causa, en `UPPER_SNAKE_CASE` y con la forma `<SUJETO>_<CAUSA>` (por ejemplo `EMAIL_ALREADY_REGISTERED`); `VALIDATION_FAILED` cuando hay `errors`; `INTERNAL_ERROR` en un fallo técnico.
- `requestId`: el valor de `X-Request-Id` que pone el Gateway; si falta, el servicio genera uno y lo devuelve en el encabezado.
- `errors[].code`: el código de cada campo rechazado.

El catálogo del servicio está en [errores.md](../errores.md) y las reglas, en la sección 6 del [estándar](../estandar-backend.md).

## Consecuencias

- El cambio es aditivo y compatible: ningún cliente que ya lea `type`, `title`, `status`, `detail` o `errors[].message` deja de funcionar.
- Frontend puede leer los campos nuevos cuando cada servicio los emita; mientras tanto no aparecen en la respuesta.
- Cada servicio los adopta en su primera tarea de código, con su excepción de negocio, su estado, su mensaje y su prueba.
- Un código publicado no se reutiliza ni se renombra.

## Alternativas descartadas

- `codigoCameia` y `correlationId`: nombres propios sin ventaja sobre los miembros de extensión de la norma.
- Un formato de error propio distinto de `ProblemDetail`: obligaría a cambiar todos los clientes.
