# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores): `ProblemDetail` (RFC 9457) con
`Content-Type: application/problem+json;charset=UTF-8`, el código estable en `code` y el identificador de la petición en
`requestId` (el `X-Request-Id` recibido si cumple `^[A-Za-z0-9._-]{1,64}$`; si no, un UUID v4 nuevo, que también se devuelve en el
encabezado `X-Request-Id`). Los errores de validación agregan `errors`, con un elemento `field`, `code` y `message` por campo
rechazado; en ellos `code` es `VALIDATION_FAILED` y `detail` es siempre «Revisa los campos marcados.». La decisión está en el
[ADR 0001](adr/0001-codigo-de-error-y-request-id.md).

El miembro `type` no se envía: vale `about:blank`, y RFC 9457 (3.1.1) define que su ausencia equivale a ese valor. Es el mismo
comportamiento que el de Cuentas.

Todas las respuestas las produce `BusinessExceptionHandler`. Cada error se registra una sola vez: los 4xx en `WARN` sin traza, con
`status`, `code`, `requestId` y el `firebaseUid` (o `-`); los 5xx en `ERROR` con traza. Nunca se registran el valor de un campo, el
cuerpo de la petición, el mensaje de una excepción de deserialización o de base de datos, ni los textos enviados al modelo.

## Códigos que el servicio emite

**Cualquiera** es toda ruta del servicio. Los códigos de dominio (sesión, turno e idempotencia) los lanzan los casos de uso de las
historias que agregan las rutas de sesión; hasta entonces el servicio no los emite por ninguna ruta. Las pruebas citadas están en
`src/test/java/tech/cameia/entrevista/`.

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `INTERNAL_ERROR` | 500 | cualquiera, incluido `GET /api/v1/interviews/config/catalog` | — | Ocurrió un error. Inténtalo de nuevo. | Cualquier fallo imprevisto que ningún otro manejador cubre. El cuerpo no lleva traza, nombre de clase ni el mensaje de la excepción | `presentation/advice/BusinessExceptionHandlerTest` |
| `LLM_UNAVAILABLE` | 503 | cualquiera | — | Ocurrió un error. Inténtalo de nuevo. | `LlmUnavailableException`: el modelo no respondió dentro de `LLM_TIMEOUT`, el proveedor respondió un error distinto de 429, la conexión se cortó, el cuerpo no se pudo leer, o la respuesta llegó sin candidatos, sin texto o sin metadatos. Un solo intento, sin reintentos | `presentation/advice/BusinessExceptionHandlerTest`, `infrastructure/ia/GeminiLanguageModelAdapterTest` |
| `LLM_LIMIT_REACHED` | 503 | cualquiera | — | Ocurrió un error. Inténtalo de nuevo. | `LlmLimitReachedException`: el proveedor respondió 429 (cuota o límite de uso de la cuenta del servicio agotado). Es 503 y no 429 porque la persona no superó ningún límite propio | `presentation/advice/BusinessExceptionHandlerTest` |
| `VALIDATION_FAILED` | 422 | cualquiera con cuerpo | uno por campo | Revisa los campos marcados. | `MethodArgumentNotValidException` | `presentation/advice/BusinessExceptionHandlerTest` |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | cualquiera con cuerpo | — | Revisa el formato de los datos enviados. | `HttpMessageNotReadableException` | `presentation/advice/BusinessExceptionHandlerTest` |
| `REQUEST_INVALID_VALUE` | 422 | cualquiera | — | Revisa los datos enviados. | Encabezado o parámetro obligatorio ausente, o valor de la ruta con otro tipo | `presentation/advice/BusinessExceptionHandlerTest` |
| `IDENTITY_REQUIRED` | 401 | cualquiera con identidad, incluido `GET /api/v1/interviews/config/catalog` | — | Tu sesión expiró. Inicia sesión de nuevo. | `IdentityRequiredException`; encabezado `X-User-Id` ausente, vacío, en blanco, de más de 128 caracteres o con caracteres de control, de formato o separadores de línea | `presentation/advice/IdentityHeaderTest` |
| `ROUTE_NOT_FOUND` | 404 | cualquiera | — | No existe la ruta solicitada. | `NoResourceFoundException` | `presentation/advice/FrameworkErrorsTest` |
| `METHOD_NOT_ALLOWED` | 405 | cualquiera, incluido `GET /api/v1/interviews/config/catalog` | — | Método no permitido. | `HttpRequestMethodNotSupportedException` | `presentation/advice/FrameworkErrorsTest` |
| `MEDIA_TYPE_NOT_ALLOWED` | 415 | cualquiera con cuerpo | — | Tipo de contenido no admitido. | `HttpMediaTypeNotSupportedException` | `presentation/advice/BusinessExceptionHandlerTest` |
| `MEDIA_TYPE_NOT_ACCEPTABLE` | 406 | cualquiera, incluido `GET /api/v1/interviews/config/catalog` | — | Tipo de respuesta no admitido. | `HttpMediaTypeNotAcceptableException` | `presentation/advice/FrameworkErrorsTest` |
| `REQUEST_MULTIPART_INVALID_FORMAT` | 415 | cualquiera con cuerpo | — | Revisa el formato de los datos enviados. | `MultipartException` (por ejemplo, `multipart/form-data` sin `boundary`) | `presentation/advice/BusinessExceptionHandlerTest`, `presentation/advice/FrameworkErrorsTest` |
| `REQUEST_PATH_INVALID_FORMAT` | 400 | cualquiera | — | La petición no es válida. | Estado 400 del contenedor: ruta con codificación porcentual inválida (`/%zz`) o petición que no se pudo interpretar | `presentation/advice/ContainerErrorControllerTest`, `presentation/advice/ContainerErrorReportValveTest`, `presentation/advice/FrameworkErrorsTest` |
| `REQUEST_PAYLOAD_TOO_LARGE` | 413 | cualquiera con cuerpo | — | Los datos enviados superan el tamaño permitido. | `MaxUploadSizeExceededException` (multipart de más de 10 MB) o estado 413 del contenedor | `presentation/advice/BusinessExceptionHandlerTest`, `presentation/advice/ContainerErrorControllerTest`, `presentation/advice/FrameworkErrorsTest` |
| `DATABASE_UNAVAILABLE` | 503 | cualquiera | — | Ocurrió un error. Inténtalo de nuevo. | La base no responde o rechaza la conexión | `presentation/advice/BusinessExceptionHandlerTest` |
| `SESSION_TRANSITION_NOT_ALLOWED` | 409 | rutas de sesión | — | Esta entrevista ya no admite esa acción. | `SessionTransitionNotAllowedException` | `presentation/advice/BusinessExceptionHandlerTest`, `domain/model/InterviewSessionTest` |
| `TURN_TRANSITION_NOT_ALLOWED` | 409 | rutas de turno | — | Esta pregunta ya no admite esa acción. | `TurnTransitionNotAllowedException` | `presentation/advice/BusinessExceptionHandlerTest`, `domain/model/InterviewTurnTest` |
| `SESSION_CONFLICT` | 409 | rutas de sesión | — | La entrevista cambió mientras se procesaba tu solicitud. Inténtalo de nuevo. | `SessionConflictException` | `presentation/advice/BusinessExceptionHandlerTest` |
| `SESSION_ALREADY_EXISTS` | 409 | creación e inicio de sesión | — | Ya tienes una entrevista en preparación o en curso. Inténtalo de nuevo. | `SessionAlreadyExistsException` | `presentation/advice/BusinessExceptionHandlerTest` |
| `IDEMPOTENCY_KEY_ALREADY_EXISTS` | 409 | rutas con clave de idempotencia | — | Esta solicitud ya se registró. | `IdempotencyKeyAlreadyExistsException` | `presentation/advice/BusinessExceptionHandlerTest` |
| `PROFILE_NOT_FOUND` | 404 | `POST /api/v1/interviews/sessions` | profileId | No encontramos lo que buscabas. | `ProfileNotFoundException`: el perfil elegido es de otra persona; no se distingue de uno inexistente | `application/service/CreateSessionServiceTest`, `presentation/controller/CreateSessionControllerTest` |
| `PROFILE_SYNC_PENDING` | 409 | `POST /api/v1/interviews/sessions` | — | Tu perfil se está actualizando. Inténtalo en unos segundos. | `ProfileSyncPendingException`: la copia local del perfil aún no llegó, está archivada o su contexto está vacío | `application/service/CreateSessionServiceTest`, `presentation/controller/CreateSessionControllerTest` |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | `POST /api/v1/interviews/sessions` | Idempotency-Key | Esta solicitud no coincide con la que enviaste antes. Inténtalo de nuevo. | `IdempotencyKeyConflictException`: la misma clave llegó con otra configuración | `application/service/CreateSessionServiceTest`, `presentation/controller/CreateSessionControllerTest` |
| `PROFILE_ID_REQUIRED` | 422 | `POST /api/v1/interviews/sessions` | profileId | Elige un Perfil Profesional. | Ausente, nulo, vacío o en blanco | `domain/policy/SessionConfigurationPolicyTest` |
| `PROFILE_ID_INVALID_FORMAT` | 422 | `POST /api/v1/interviews/sessions` | profileId | El Perfil Profesional elegido no es válido. | No es un UUID con guiones | `domain/policy/SessionConfigurationPolicyTest` |
| `TARGET_ROLE_ID_REQUIRED` | 422 | `POST /api/v1/interviews/sessions` | targetRoleId | Elige un rol objetivo. | Ausente, nulo, vacío o en blanco | `domain/policy/SessionConfigurationPolicyTest` |
| `TARGET_ROLE_ID_INVALID_FORMAT` | 422 | `POST /api/v1/interviews/sessions` | targetRoleId | El rol objetivo elegido no es válido. | No es un UUID con guiones | `domain/policy/SessionConfigurationPolicyTest` |
| `TARGET_ROLE_ID_INVALID_VALUE` | 422 | `POST /api/v1/interviews/sessions` | targetRoleId | Elige un rol objetivo del catálogo. | El UUID no está en la copia local del catálogo de roles o su rol está inactivo | `domain/policy/SessionConfigurationPolicyTest` |
| `JOB_OFFER_TOO_LONG` | 422 | `POST /api/v1/interviews/sessions` | jobOffer | La oferta laboral no puede superar los 3000 caracteres. | Más de 3000 puntos de código Unicode tras normalizar a NFC y recortar | `domain/policy/SessionConfigurationPolicyTest` |
| `JOB_OFFER_INVALID_CHARACTERS` | 422 | `POST /api/v1/interviews/sessions` | jobOffer | La oferta laboral contiene un carácter no permitido. | Contiene el carácter nulo (U+0000), que la base de datos no guarda | `domain/policy/SessionConfigurationPolicyTest`, `presentation/CreateSessionEndToEndTest` |
| `MODE_REQUIRED` | 422 | `POST /api/v1/interviews/sessions` | mode | Elige la modalidad. | Ausente, nulo, vacío o en blanco | `domain/policy/SessionConfigurationPolicyTest` |
| `MODE_INVALID_VALUE` | 422 | `POST /api/v1/interviews/sessions` | mode | Elige una modalidad de la lista. | Valor distinto de `SIMULACION` y `ENTRENO`, o con otras mayúsculas | `domain/policy/SessionConfigurationPolicyTest` |
| `MODE_NOT_ALLOWED` | 422 | `POST /api/v1/interviews/sessions` | mode | Esta modalidad estará disponible próximamente. | `ENTRENO`, que el catálogo marca como no disponible | `domain/policy/SessionConfigurationPolicyTest` |
| `TONE_REQUIRED` | 422 | `POST /api/v1/interviews/sessions` | tone | Elige el tono del entrevistador. | Ausente, nulo, vacío o en blanco | `domain/policy/SessionConfigurationPolicyTest` |
| `TONE_INVALID_VALUE` | 422 | `POST /api/v1/interviews/sessions` | tone | Elige un tono de la lista. | Valor que no está en el catálogo de tonos | `domain/policy/SessionConfigurationPolicyTest` |
| `ANSWER_FORMAT_REQUIRED` | 422 | `POST /api/v1/interviews/sessions` | answerFormat | Elige la forma de respuesta. | Ausente, nulo, vacío o en blanco | `domain/policy/SessionConfigurationPolicyTest` |
| `ANSWER_FORMAT_INVALID_VALUE` | 422 | `POST /api/v1/interviews/sessions` | answerFormat | Elige una forma de respuesta de la lista. | Valor que no está en el catálogo de formas de respuesta | `domain/policy/SessionConfigurationPolicyTest` |
| `SESSION_LANGUAGE_REQUIRED` | 422 | `POST /api/v1/interviews/sessions` | sessionLanguage | Elige el idioma de la entrevista. | Ausente, nulo, vacío o en blanco | `domain/policy/SessionConfigurationPolicyTest` |
| `SESSION_LANGUAGE_INVALID_VALUE` | 422 | `POST /api/v1/interviews/sessions` | sessionLanguage | Elige un idioma de la lista. | Valor que no está en el catálogo de idiomas | `domain/policy/SessionConfigurationPolicyTest` |
| `IDEMPOTENCY_KEY_INVALID_FORMAT` | 422 | `POST /api/v1/interviews/sessions` | Idempotency-Key | Ocurrió un error. Inténtalo de nuevo. | Encabezado vacío, de más de 64 caracteres o con caracteres fuera de `A-Za-z0-9._-` | `domain/policy/SessionConfigurationPolicyTest` |

## Respuestas que salen del contenedor

Tomcat resuelve por su cuenta algunas peticiones antes de que existan una ruta o un controlador. Todas salen en el formato común:

| Petición | Respuesta | Quién la produce |
|---|---|---|
| Ruta con codificación porcentual inválida (`/%zz`), encabezado con caracteres de control o demasiado grande, línea de petición mal formada | 400 `REQUEST_PATH_INVALID_FORMAT`, con un `requestId` nuevo (el `X-Request-Id` recibido no es confiable) | `ContainerErrorReportValve` |
| `GET /error` pedido directamente | 404 `ROUTE_NOT_FOUND` | `ContainerErrorController` |
| Estado del contenedor sin código propio (por ejemplo 503) | 500 `INTERNAL_ERROR`, registrado en `ERROR` con el estado | `ContainerErrorController` |

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un
código publicado no se reutiliza ni se renombra. La prueba `ErrorCodeDocumentationTest` falla si un valor de `ErrorCode` no tiene su
fila.
