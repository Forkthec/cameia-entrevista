# Plan · CM-212 Planificar las 5 preguntas y pasar a EN_CURSO

Spec: `spec.md` de esta carpeta. Un PR, un commit por capacidad, en este orden de lectura.

| # | Commit | Tarjetas | Depende de |
|---|---|---|---|
| 1 | `CM-212 \| feat(entrevista): códigos de error de la planificación` | T1 | — |
| 2 | `CM-212 \| feat(entrevista): modelo y persistencia de preguntas y ejecuciones de la IA` | T2 (y migraciones `V6` y `V7`) | T1 |
| 3 | `CM-212 \| feat(entrevista): dominio de la planificación (validador, política de reintentos, marca)` | T3, T5 | T2 |
| 3b | `CM-212 \| feat(entrevista): unir las partes de la respuesta de Gemini y ajustar la configuración del modelo` | T6b | — |
| 4 | `CM-212 \| feat(entrevista): iniciar, estado y cancelar la sesión` | T4, T6 | T3 y T6b |
| 5 | `CM-212 \| test(entrevista): pruebas de extremo a extremo y de base de datos` | T7 | T4 |
| 6 | `CM-212 \| docs(entrevista): OpenAPI, errores, Postman y bitácora` | T8, T9 | T7 |

## Qué mirar al revisar

| Archivo | Pide criterio | Mecánico |
|---|---|---|
| `StartSessionService` | Orden de la transacción (abandonar la anterior, luego iniciar), la marca de D-212-1 y el contador | Inyección de puertos |
| `PlanValidator` | Reglas de REQ-PLN-05 | Javadoc |
| Migración (si D-212-1 = A) | Índice parcial y caducidad | — |
| Pruebas de concurrencia | Que el modelo simulado cuente 1 llamada | Preparación de datos |

## Orden de ejecución por bloques

El spike CM-347 está fusionado: todo el contrato está en la spec §16. Orden: T0, T1, T2 (con `V6` y `V7`), T3, T5 (esquema y plantilla reales), T6b (adaptador y configuración), T6 (prompt, directamente con `v4`), T4, T7, T8, T9, **T6d (solo la neutralización de la marca de cierre; la medición de `v4` está hecha y aceptada el 10-nov) y T6e (solo la constante y la validación del tope de 32.000 caracteres; medido y aceptado el 10-nov)**, y cierre: `clean verify`, cobertura, Newman y comprobación en el entorno una vez sobre la punta. Decisiones de Paula (10-nov): D-212-6 A y D-212-7 B. Aparte, sin entrar en este PR: el PR de `LLM_MODEL` en `.github` (spec §14).

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Choque de migraciones con CM-216 (sin fusionar; `V5` ya es de CM-352) | T0 confirma `V6` y `V7` libres al empezar |
| `LLM_MODEL` o `LLM_TIMEOUT` del despliegue pisan la configuración del spike | T6b y V-NN lo comprueban; el cambio, si hace falta, lo pide Paula a DevOps |
| Dos conexiones máximas: la llamada a la IA no debe sostener una conexión | La llamada se hace fuera de toda transacción (T4) |
| `planificacion.v4` rinde peor que `v3` | Medido (informe de CM-212) y aceptado por Paula el 10-nov: mediana mayor y cerca de 1 de 50 peticiones sobre 9,5 s, cubiertas con «Reintentar». Si el uso real lo empeora, el prompt vive en un solo archivo y una sola clase (`PlanningPromptBuilder`) |
| Un perfil sin experiencia laboral recibe preguntas pensadas para quien trabajó (H-18) | `planificacion.v5` con una regla para ese caso (REQ-PLN-22, D-212-11), medida con 4 perfiles sin experiencia y 6 de control; la comprobación de la salida es manual porque el servicio no puede verificarla |
| La medición agota el cupo diario de Gemini (500) | Ya medido: 153 llamadas en total (T6d, T6e y controles) el 10-nov; no se repite |
