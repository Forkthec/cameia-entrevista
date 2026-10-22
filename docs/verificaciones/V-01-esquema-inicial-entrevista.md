# V-01 · Esquema inicial, máquinas de estados y manejador de errores de Entrevista

## Qué se quería saber

Si el esquema `microentrevista` (migración V1), las máquinas de estados de la sesión y del turno, y el manejador de errores con
`requestId` e identidad del Gateway cumplen los siete criterios del habilitador HT-01 contra un PostgreSQL 16 real, y si el
servicio empaquetado responde con el formato de error común.

## Conclusión

Cumple. Las 405 pruebas pasan (0 fallos, 0 omitidas), las clases tocadas superan 90 % de líneas y de ramas, Newman pasa 4 de 4
peticiones (19 aserciones) contra el jar empaquetado con una base real, y ninguna de las pruebas con petición real produjo un
`500` ni una línea `ERROR` en el log de la aplicación.

## Entorno

| Elemento | Valor |
|---|---|
| Commit verificado | `cb82c0f` (rama `CM-311-motor-base-entrevista`) |
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
| `InterviewSchemaMigrationTest` | 85 |
| `SessionStatusTest` | 34 |
| `BusinessExceptionHandlerTest` | 28 |
| `TurnStatusTest` | 22 |
| `ErrorCodeTest` | 17 |
| `ErrorCodeDocumentationTest` | 17 |
| `InterviewSessionTest` | 16 |
| `InterviewSessionRepositoryAdapterTest` | 16 |
| `FirebaseUidTest` | 14 |
| `RequestIdFilterTest` | 13 |
| `HandlerBranchesTest` | 9 |
| `InterviewTurnTest` | 9 |
| `IdentityHeaderTest` | 8 |
| `ColumnLengthConsistencyTest` | 7 |
| `BusinessExceptionHandlerDispatchTest` | 6 |
| `FrameworkErrorsTest` | 5 |
| `InterviewTurnRepositoryAdapterTest` | 4 |
| `LayeredArchitectureTest`, `RequestIdTest` | 3 cada una |
| Total del módulo | 405 |

Cobertura de las clases nuevas o modificadas (JaCoCo): todas con 100 % de líneas; ramas 100 % salvo `BusinessExceptionHandler`
(96,3 %: 79 de 82). Las 3 ramas sin cubrir son guardas defensivas del manejador: una restricción de validación que no es de
Hibernate (línea 480), una `ConstraintViolationException` sin nombre de restricción (línea 492) y una `SQLException` sin SQLState
(línea 503); ninguna es alcanzable desde una petición hoy. `EntrevistaApplication.main` (33 %) y `LanguageModelProperties`
(75 % de ramas) no las toca esta tarea.

Newman: 4 peticiones, 0 fallos, 19 aserciones, 0 fallos.

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
| `Accept: application/xml` | 406 `MEDIA_TYPE_NOT_ACCEPTABLE` |
| 60 peticiones en paralelo | 60 respuestas 404 con 60 `requestId` distintos |

## Lo que no se pudo ejecutar o queda abierto

- No existe ninguna ruta de negocio, así que el cuerpo con validación, los límites n−1/n/n+1 de campos y el doble envío de una
  escritura solo se prueban con el controlador de prueba (`ErrorProbeController`) y con la base real en las pruebas del
  adaptador (choque de versión y claves anti-duplicados); no hay petición HTTP real a ellos.
- Tres respuestas salen del formato del servicio y quedan documentadas en `docs/errores.md` como pendientes de decisión: una
  ruta con codificación porcentual inválida (página HTML de Tomcat), `GET /error` directo y un `multipart/form-data` sin
  límite (`500 INTERNAL_ERROR`).
- El CI de la rama no se ejecutó (no hay push).
