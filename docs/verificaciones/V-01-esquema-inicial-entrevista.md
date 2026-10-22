# V-01 · Esquema inicial, máquinas de estados y manejador de errores de Entrevista

## Qué se quería saber

Si el esquema `microentrevista` (migración V1), las máquinas de estados de la sesión y del turno, y el manejador de errores con
`requestId` e identidad del Gateway cumplen los siete criterios del habilitador HT-01 contra un PostgreSQL 16 real, y si el
servicio empaquetado responde con el formato de error común.

## Conclusión

Cumple. Las 465 pruebas pasan (0 fallos; 1 omitida, la llamada real al modelo, que solo corre con `LLM_LIVE_TEST=true`), las
clases tocadas superan 90 % de líneas y de ramas, Newman pasa 7 de 7 peticiones (34 aserciones) contra el jar empaquetado con una base real, y ninguna de las pruebas con petición real produjo un
`500` ni una línea `ERROR` en el log de la aplicación.

## Entorno

| Elemento | Valor |
|---|---|
| Commit verificado | `c35b259` (rama `CM-311-motor-base-entrevista`) |
| Java | OpenJDK 21.0.12.1 |
| Maven | 3.9.16 (Maven Wrapper, `./mvnw.cmd -B -ntp`) |
| Base de datos | `postgres:16-alpine` en Docker 29.7.2 (Testcontainers en las pruebas; un contenedor desechable para Newman) |
| Newman | 6.2.3 |
| Sistema | Windows 11 |

Las credenciales y las claves del modelo usadas en la comprobación son valores inventados y desechables; no se reproducen aquí.

## Números reales

Pruebas por clase (`clean verify`):

| Clase | Pruebas |
|---|---|
| `InterviewSchemaMigrationTest` | 90 |
| `SessionStatusTest` | 34 |
| `BusinessExceptionHandlerTest` | 30 |
| `TurnStatusTest` | 22 |
| `ErrorCodeTest` | 20 |
| `ErrorCodeDocumentationTest` | 20 |
| `InterviewSessionTest` | 16 |
| `InterviewSessionRepositoryAdapterTest` | 16 |
| `FirebaseUidTest` | 18 |
| `RequestIdFilterTest` | 13 |
| `HandlerBranchesTest` | 9 |
| `InterviewTurnTest` | 9 |
| `IdentityHeaderTest` | 8 |
| `ColumnLengthConsistencyTest` | 7 |
| `BusinessExceptionHandlerDispatchTest` | 6 |
| `FrameworkErrorsTest` | 8 |
| `InterviewTurnRepositoryAdapterTest` | 4 |
| `LayeredArchitectureTest`, `RequestIdTest` | 3 cada una |
| `ContainerErrorControllerTest` | 12 |
| `PayloadTooLargeTest` | 1 |
| Total del módulo | 465 |

Cobertura de las clases nuevas o modificadas (JaCoCo): todas con 100 % de líneas; ramas 100 % salvo `BusinessExceptionHandler`
(96,3 %: 79 de 82). Las 3 ramas sin cubrir son guardas defensivas del manejador: una restricción de validación que no es de
Hibernate, una `ConstraintViolationException` sin nombre de restricción y una `SQLException` sin SQLState; ninguna es alcanzable
desde una petición hoy. Total del módulo: 703 de 706 líneas (99,6 %) y 251 de 261 ramas (96,2 %); lo que queda sin cubrir fuera
del manejador no lo toca esta tarea (`EntrevistaApplication.main`, `LanguageModelProperties` y el adaptador de Gemini).

Newman: 7 peticiones, 34 aserciones, 0 fallos. Incluye un `multipart/form-data` real de 11 MB que responde 413
`REQUEST_PAYLOAD_TOO_LARGE` en `application/problem+json`.

Esquema en la base real (tras arrancar el jar): 8 tablas del dominio más `flyway_schema_history`, `tipo_pregunta` con 4 filas y la
migración `1` aplicada.

Plan de consulta del índice de inactividad, con `SET LOCAL enable_seqscan = off` para comprobar que es utilizable:

```
Limit  (cost=0.12..8.14 rows=1 width=24)
  ->  Index Scan using ix_sesion_en_curso_ultima_actividad on sesion
```

La consulta con un filtro de fecha sin orden eligió `uq_sesion_en_curso_por_usuario`, otro índice parcial de las mismas filas;
el índice de inactividad se usa cuando la consulta ordena por `fecha_ultima_actividad`. Con la tabla vacía el costo no es
representativo: solo prueba que el índice existe y es elegible.

## Comprobaciones con petición real al servicio empaquetado

| Petición | Respuesta |
|---|---|
| `X-Request-Id` de 64 caracteres | 200, se devuelve igual |
| `X-Request-Id` de 65 caracteres, con espacio o con caracteres no ASCII | 404 con un UUID nuevo en `requestId` |
| `X-User-Id` de 129 caracteres en una ruta inexistente | 404 `ROUTE_NOT_FOUND` |
| `POST`/`PUT /health` con cuerpo con campos extra, JSON roto o `Content-Type` absurdo | 405 `METHOD_NOT_ALLOWED` |
| Ruta con codificación Unicode y ruta de 5000 caracteres | 404 `ROUTE_NOT_FOUND` |
| Ruta con codificación porcentual inválida (`/%zz`, `/%`), encabezado de 20 KB o byte de control en un encabezado | 400 `REQUEST_PATH_INVALID_FORMAT` en `application/problem+json` |
| `GET /error` directo | 404 `ROUTE_NOT_FOUND` |
| `POST` con `multipart/form-data` sin límite | 415 `REQUEST_MULTIPART_INVALID_FORMAT` |
| `POST` con `multipart/form-data` de 11 MB | 413 `REQUEST_PAYLOAD_TOO_LARGE`, con `code`, mensaje, `requestId` y `application/problem+json;charset=UTF-8` |
| `Accept: application/xml` | 406 `MEDIA_TYPE_NOT_ACCEPTABLE` |
| 60 peticiones en paralelo | 60 respuestas 404 con 60 `requestId` distintos |

## Lo que no se pudo ejecutar o queda abierto

- No existe ninguna ruta de negocio, así que el cuerpo con validación, los límites n−1/n/n+1 de campos y el doble envío de una
  escritura solo se prueban con el controlador de prueba (`ErrorProbeController`) y con la base real en las pruebas del
  adaptador (choque de versión y claves anti-duplicados); no hay petición HTTP real a ellos.
- El CI de la rama no se ejecutó (no hay push).
