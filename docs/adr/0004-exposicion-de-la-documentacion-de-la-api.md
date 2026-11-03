# ADR 0004 · Exposición de la documentación de la API

## Estado

Aceptada. Decisión de Product Owner, 10 de septiembre de 2026.

## Contexto

Se planteó si la documentación de la API se publica en despliegue, si se retira solo la interfaz Swagger UI o también el documento OpenAPI en `/v3/api-docs`, y cómo se controla el interruptor.

## Decisión

La documentación se publica solo en desarrollo, con el JSON y la interfaz juntos y un interruptor por variable de entorno.

- En despliegue se retiran las dos rutas, `/v3/api-docs` y `/swagger-ui.html`, y responden `404`. Apagar solo la interfaz sería cosmético, porque el JSON ya expone el contrato completo.
- El valor por defecto lo fija el perfil activo: el de desarrollo publica la documentación y el de despliegue la retira. La configuración base la deja apagada, para que un perfil nuevo no publique el contrato sin decidirlo.
- Una variable de entorno invierte ese valor por defecto sin reconstruir la imagen y gobierna las dos rutas con un solo interruptor.

El nombre de la variable y de los perfiles está en el [CLAUDE.md](../../CLAUDE.md), sección 4.

## Consecuencias

- Es solo configuración: ningún endpoint de negocio cambia, y `GET /health` sigue respondiendo con la documentación retirada.
- Pruebas automáticas cubren el valor por defecto de cada perfil, el de un perfil sin configuración propia y las dos direcciones del interruptor.
- Toda spec futura que documente endpoints asume esta regla, como la [spec de salud](../../specs/CM-101-EndpointSalud/spec.md).
