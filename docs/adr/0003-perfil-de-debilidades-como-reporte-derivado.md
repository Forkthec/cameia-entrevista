# ADR 0003 · El perfil de debilidades es un reporte derivado

## Estado

Aceptada. Decisión de Product Owner, 8 de septiembre de 2026.

## Contexto

El servicio soporta los modos de sesión entreno y simulación, y se planteó si el perfil de debilidades era un tercer modo.

## Decisión

El perfil de debilidades es un reporte derivado de entrevistas ya realizadas, en modo entreno o simulación. No es un tercer modo de sesión. Su mecánica (qué datos lo alimentan, cuándo se genera y cómo se expone) se define en una spec futura, cuando esta capacidad entre en alcance.

## Consecuencias

- No se crea un tercer modo de sesión ni lógica de generación del perfil sin una spec aprobada.
- Sin impacto en el código por ahora.
