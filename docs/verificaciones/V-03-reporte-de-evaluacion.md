# V-03 · Reporte de Evaluación

## Qué se quería saber

Si `GET /api/v1/interviews/sessions/{sessionId}/evaluation-report` genera, guarda una sola vez y entrega el reporte con sus siete métricas y las cinco preguntas; si responde con su código estable cada caso previsible (entrevista sin terminar, abandonada, ajena, inexistente, sin identidad, generación en curso, base caída); y si nunca llega al error genérico de 500.

## Conclusión

Cumple. `clean verify` pasa (2436 pruebas, 0 fallos, 4 omitidas), las clases nuevas o tocadas tienen al menos 90 % de líneas y ramas, salvo el constructor que exige JPA de `StarResultEntity` (la entidad nunca se carga por JPA) las ramas previas de `AssemblyAiTranscriptionAdapter`, `AnswerRegistrar`, `GroqLanguageModelAdapter` y `AnswerAttempt`, y una rama de `toString` de `ReportQuestion` (en conjunto, 97,0 % de líneas y 92,2 % de ramas en las 49 clases tocadas), Newman pasa la colección completa (256 peticiones, 990 comprobaciones, 0 fallos) y las peticiones reales a cada ruta no devolvieron ningún 500.

## Entorno

| Elemento | Valor |
|---|---|
| Commit verificado | `17fe5c7` (rama `CM-380-reporte-de-evaluacion`, sobre `develop` en `0eec549`); el commit que agrega este documento solo suma documentación |
| Java / Maven | OpenJDK 21, Maven Wrapper `./mvnw.cmd -B -ntp clean verify` |
| Base de datos | `postgres:16-alpine` (Testcontainers en las pruebas; un contenedor desechable para Newman y las peticiones reales) |
| Mensajería | RabbitMQ en un contenedor desechable |
| Proveedores | Simulador local `postman/simulador-gemini.py` (modelo y voz); llaves y contraseñas inventadas y desechables, no se reproducen aquí |
| Sistema | Windows 11 |

## Peticiones reales (aplicación empaquetada, base real)

| Caso | Respuesta |
|---|---|
| Entrevista terminada con reporte | 200 |
| Entrevista terminada sin reporte (lo genera) | 200 |
| Entrevista en curso | 409 `SESSION_NOT_FINISHED` |
| Entrevista abandonada | 409 `SESSION_ABANDONED` |
| Entrevista ajena, inexistente o con id mal escrito | 404, el mismo mensaje en los tres |
| Sin `X-User-Id` o vacío | 401 `IDENTITY_REQUIRED` |
| Método POST | 405 |
| `Accept: application/xml` | 406 |
| Dos consultas a la vez (modelo lento) | 200 y 202 `REPORT_GENERATING`; una sola ejecución y un solo reporte |
| Base de datos detenida (lectura) | 503 `DATABASE_UNAVAILABLE` con su mensaje; al volver la base, 200 |
| Ruta con barra final | 404 de ruta inexistente |

Ninguna respuesta fue el 500 genérico; el registro de la aplicación no contiene `INTERNAL_ERROR`.

## Evidencia

Los registros de `clean verify`, de Newman y de la aplicación quedan en local, en poder de Backend; las cifras de este documento son las de esa corrida.
