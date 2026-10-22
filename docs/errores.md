# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores): `ProblemDetail` (RFC 9457) con
`Content-Type: application/problem+json; charset=UTF-8`, los miembros `title`, `status`, `detail` e `instance`, el código estable en
`code` y el identificador de la petición en `requestId`. El `requestId` es el `X-Request-Id` recibido si cumple
`^[A-Za-z0-9._-]{1,64}$`; si no, un UUID v4 nuevo. En los dos casos se devuelve también en el encabezado `X-Request-Id`. La decisión
está en el [ADR 0001](adr/0001-codigo-de-error-y-request-id.md).

El miembro `type` no se envía: vale `about:blank`, y RFC 9457 (3.1.1) define que su ausencia equivale a ese valor. Es el mismo
comportamiento que el de Cuentas.

Todas las respuestas con código las produce `BusinessExceptionHandler`. Cada error se registra una sola vez, en `ERROR` con traza y con
`code` y `requestId`, porque todos los códigos de hoy son fallos del servicio (5xx). Nunca se registran la llave del proveedor, el texto
enviado al modelo ni el texto recibido. Decisiones en la [spec de CM-312](../specs/CM-312-ClienteGemini/spec.md), secciones 3 a 5.

## Códigos que el servicio emite

Rutas: hoy ningún endpoint llama al modelo de lenguaje; los dos códigos del modelo los emitirá el caso de uso que lo use (HU-4.5 y
HU-5.2), y cada historia agrega aquí su ruta. **Cualquiera** es toda ruta del servicio. Las pruebas citadas están en
`src/test/java/tech/cameia/entrevista/`.

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `LLM_UNAVAILABLE` | 503 | Ninguno todavía | — | Ocurrió un error. Inténtalo de nuevo. | `LlmUnavailableException`: el modelo no respondió dentro de `LLM_TIMEOUT`, el proveedor respondió un error distinto de 429, la conexión se cortó, el cuerpo no se pudo leer, o la respuesta llegó sin candidatos, sin texto o sin metadatos. Un solo intento, sin reintentos | `GeminiLanguageModelAdapterTest`, `BusinessExceptionHandlerTest` |
| `LLM_LIMIT_REACHED` | 503 | Ninguno todavía | — | Ocurrió un error. Inténtalo de nuevo. | `LlmLimitReachedException`: el proveedor respondió 429 (cuota o límite de uso de la cuenta del servicio agotado). Es 503 y no 429 porque la persona no superó ningún límite propio | `GeminiLanguageModelAdapterTest`, `BusinessExceptionHandlerTest` |
| `INTERNAL_ERROR` | 500 | Cualquiera | — | Ocurrió un error. Inténtalo de nuevo. | Cualquier fallo imprevisto que ningún otro manejador cubre. El cuerpo no lleva traza, nombre de clase ni el mensaje de la excepción | `BusinessExceptionHandlerTest`, `BusinessExceptionHandlerDispatchTest` |

## Respuestas sin código

Las responde Spring Boot con su cuerpo por defecto (`timestamp`, `status`, `error`, `path`), sin `code` ni `requestId`: el manejador
las relanza sin registrarlas porque son rechazos de protocolo y no fallos del servicio.

| Respuesta | Cuándo | Destino |
|---|---|---|
| 404 | Ruta inexistente | CM-311: código propio y forma común (`ContainerErrorController`, como en Cuentas) |
| 405 | Método no admitido, por ejemplo `POST /health` | CM-311, igual que el 404 |
| 415 y 406 | Tipo de contenido o de respuesta no admitido | CM-311, igual que el 404 |
| 400 | Cuerpo ilegible o parámetro de otro tipo; hoy ningún endpoint lee cuerpo ni parámetros | CM-311, con `VALIDATION_FAILED` y sus códigos de campo |

Con el perfil `local` y Spring Boot DevTools, esas respuestas incluyen además la traza en el miembro `trace`. Es propio de DevTools,
que no entra en la imagen de despliegue.

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un código
publicado no se reutiliza ni se renombra. `ErrorCodeDocumentationTest` falla si un miembro de `ErrorCode` no aparece como fila de la
tabla «Códigos que el servicio emite».
