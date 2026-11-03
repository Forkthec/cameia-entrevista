# V-02 · Catálogo de personalización de la entrevista

## Qué se quería saber

Si `GET /api/v1/interviews/config/catalog` entrega exactamente las modalidades, tonos, formas de respuesta e idiomas del backlog con su disponibilidad, si rechaza lo que no debe (sin identidad, método o tipo de respuesta no admitidos) con el formato de error común, si se mantiene dentro del presupuesto de desempeño y si ninguna petición termina en el error genérico `500` salvo un fallo imprevisto.

## Conclusión

Cumple. Las 518 pruebas pasan (0 fallos; 1 omitida, la llamada real al modelo, que solo corre con `LLM_LIVE_TEST=true`), las siete clases nuevas tienen 100 % de líneas y de ramas, y Newman pasa 14 de 14 peticiones (71 aserciones) contra el jar empaquetado con una base real. Ninguna petición real produjo un `500` ni una línea `ERROR` en el log de la aplicación.

Un hallazgo de comportamiento que la verificación dejó escrito: con el servidor real, un `X-User-Id` con un byte de control o de más de 8 KB no llega a la aplicación; Tomcat lo rechaza y responde `400 REQUEST_PATH_INVALID_FORMAT` («La petición no es válida.»), no `401`. El `401 IDENTITY_REQUIRED` es el de la aplicación (sin encabezado, vacío, en blanco, de más de 128 caracteres). Una prueba con servidor real fija el `400`.

## Entorno

| Elemento | Valor |
|---|---|
| Commit verificado | `51547ee` (rama `CM-204-catalogo-personalizacion`, sobre `develop` en `e79d46a`); el commit que agrega este documento solo suma este archivo |
| Java | OpenJDK 21.0.12.1 |
| Maven | Maven Wrapper, `./mvnw.cmd -B -ntp clean verify` |
| Base de datos | `postgres:16-alpine` en Docker 29.7.2 (Testcontainers en las pruebas; un contenedor desechable para Newman) |
| Newman | `npx newman run` con la colección y el entorno `local` de `postman/` |
| Sistema | Windows 11 |

Las credenciales y la clave del modelo usadas en la comprobación son valores inventados y desechables; no se reproducen aquí.

## Números reales

Pruebas del catálogo (`clean verify`):

| Clase | Pruebas |
|---|---|
| `PersonalizationCatalogTest` | 19 |
| `InterviewConfigControllerTest` | 12 |
| `PersonalizationCatalogEndToEndTest` | 11 |
| `CatalogSchemaConsistencyTest` | 8 |
| `GetPersonalizationCatalogServiceTest` | 1 |
| Total del catálogo | 51 |
| Total del módulo | 518 (467 antes de la tarea) |

Cobertura de las clases nuevas (JaCoCo):

| Clase | Líneas | Ramas |
|---|---|---|
| `PersonalizationCatalog` | 18/18 | 4/4 |
| `PersonalizationCatalogResponse` | 6/6 | — |
| `InterviewConfigController` | 5/5 | — |
| `GetPersonalizationCatalogService` | 4/4 | — |
| `CatalogOption` | 3/3 | — |
| `DomainPolicyConfiguration` | 2/2 | — |
| `CatalogOptionResponse` | 2/2 | — |
| Total del módulo | 743/746 (99,6 %) | 255/265 (96,2 %) |

Lo que queda sin cubrir en el módulo (3 líneas y 10 ramas) no lo toca esta tarea: `EntrevistaApplication.main`, `LanguageModelProperties`, el adaptador de Gemini y guardas defensivas del manejador de errores.

Peticiones reales contra el jar empaquetado:

| Comprobación | Resultado |
|---|---|
| Newman, carpeta «Catálogo de personalización» | 7 peticiones, 37 aserciones, 0 fallos |
| Newman, colección completa | 14 peticiones, 71 aserciones, 0 fallos; respuesta media 27 ms, máxima 91 ms |
| `GET` con identidad | `200`, `Content-Type: application/json;charset=UTF-8`, `X-Request-Id` devuelto |
| Líneas `ERROR` en el log de la aplicación | 0 |

Desempeño (DES-02, p95 ≤ 2 s): la prueba `catalog_shouldMeetLatencyBudget_when50Requests` pasa. Con 50 peticiones `curl` seguidas al jar se midió p50 = 3,2 ms, p95 = 4,9 ms y máximo 27,9 ms, antes de las correcciones de la revisión; ese camino de la petición no cambió después.

Exploración con 43 casos adversariales contra el jar (identidad de 128, 129 y 9000 caracteres, tabulador, byte de control, doble encabezado, parámetros hostiles, métodos, `HEAD`, `OPTIONS`, barra final y mayúsculas en la ruta, 200 peticiones en 20 hilos): ninguno respondió `500`; el cuerpo fue idéntico en las 200 peticiones concurrentes.

## Lo que no se ejecutó

- Prueba con la base de datos caída: el endpoint no la usa, así que no hay ruta que probar (fiabilidad por diseño).
- Un `500` con una petición real: no se puede provocar desde fuera; la prueba usa el manejador real con un servicio que falla.
- La llamada real al modelo de lenguaje (`GeminiLanguageModelLiveTest`): es manual y no pertenece a esta tarea.
- Los repasos automáticos de seguridad y de calidad de código como herramientas aparte: la seguridad se revisó leyendo el diff completo.
