# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores). Esta página lista lo que el servicio emite hoy.

## Códigos que el servicio emite

El servicio todavía no tiene manejador de errores ni emite el campo `code`; se adopta en la primera tarea de código del servicio (ver [ADR 0001](adr/0001-codigo-de-error-y-request-id.md)).

## Respuestas sin código

Ninguna: `GET /health` es su único endpoint.

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un código publicado no se reutiliza ni se renombra.
