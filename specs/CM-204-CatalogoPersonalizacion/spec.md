# CM-204 · Catálogo de personalización de la entrevista (`GET /api/v1/interviews/config/catalog`)

| Dato | Valor |
|---|---|
| Tarea Jira | CM-204, subtarea de Backend de HU-4.3 (tarea padre CM-263; Frontend CM-205, pruebas CM-206). Fuente del reparto: `informacion/Contexto CAMEIA/05102026_v1_03-catalogo-epicas-e-historias.md` línea 449 y fila 23 de la hoja «Backlog» (clave CM-263) |
| Historia | HU-4.3 «Configurar la sesión: modalidad, tono, forma de respuesta e idioma» (backlog v8.3, hoja HE-04, 11 CA; 1 solo de Backend: CA-4.3.10) |
| Sprint | Jira: no se pudo leer en esta sesión (brecha, §19). Según `ESTADO.md` y `cronograma/cronograma.md` §5: entra al Sprint 3 «solo si sobra tiempo», si no al Sprint 4 (aviso enviado a Vela el 20-oct, `comunicaciones/enviadas/20102026_v1_ajustes-de-jira-sprint-3-para-vela.md`, punto 2) |
| Repositorio | `cameia-entrevista`, base `origin/develop` `bcc697d` (21-oct) **más CM-311 y CM-312 tratadas como implementadas** (§18) |
| Depende de | CM-311 (enumerados `Modality`, `Tone`, `AnswerMode`, `InterviewLanguage`, `FirebaseUid`, manejador de errores, Testcontainers, ArchUnit, colección Postman) y, por debajo, CM-312 (`ErrorCode`, `BusinessExceptionHandler`) |
| Desbloquea | CM-207 (crear la sesión valida contra este mismo catálogo, CA-4.3.10 y nota técnica de HU-4.3) y CM-205 de Frontend (deja de tener las opciones fijas) |
| Estimación | Jira: 1,1 h (según `cronograma/plan-sprint4_entrevista-voz-reporte.md` §1). Backend: **3 a 4 h** (dominio, endpoint, 4 clases de prueba, OpenAPI, Postman y documentación). Se informa a Vela; no se cambia en Jira |
| Estado | **LISTA PARA EJECUTAR** con CM-311 fusionada (ya lo está) (condición de entrada de la tarjeta T0). Aprobada por Paula el 22-oct-2026, con las decisiones por recomendación (§15) ratificadas |

## 1. Objetivo

Publicar en `cameia-entrevista` un endpoint de solo lectura que devuelve las opciones de personalización de una entrevista (modalidades, tonos, formas de respuesta e idiomas) y la disponibilidad de cada una, sacadas de **una sola fuente en el dominio** (`PersonalizationCatalog`), la misma con la que CM-207 validará la creación de la sesión. Así `cameia-web` deja de tener las opciones fijas (nota técnica de HU-4.3: «sin indicador propio en cameia-web»).

No guarda nada, no lee la base, no llama a otro servicio y no usa la IA.

## 2. Fuentes y vigencia

| Fuente | Qué aporta | Marca |
|---|---|---|
| Backlog v8.3, HU-4.3 (`extraer_hu.py`, volcado en `analisis/contexto/CM-204.md`) | 11 CA, nota técnica (ruta, valores, «desde constantes de configuración, sin tabla nueva», «HU-4.4 valida contra la misma fuente») | Hecho |
| Hoja «Cambios v8», filas 22 a 28 | Audio preseleccionado; Texto seleccionable; sin Video; catálogo único con `AUDIO` y `TEXTO` ambos disponibles; `es-CO` y `en` | Hecho |
| Spec aprobada de CM-311 (20-oct) | Enumerados con su valor del backlog (`Modality.SIMULATION("SIMULACION")`, …), `FirebaseUid.required`, manejador y catálogo de errores, `ProblemDetailTestSupport`, colección `postman/` | Hecho (tratado como implementado; §18) |
| `cameia-web` `origin/develop` `836d8e3` (solo lectura, 22-oct) | `src/features/interview-setup/model/interviewSetup.types.ts`: mismos códigos (`SIMULACION`, `ENTRENO`, `PROFESIONAL_NEUTRO`, `CALIDO`, `ESTRICTO`, `MOTIVADOR`, `AUDIO`, `TEXTO`, `es-CO`, `en`) y mismo orden; `src/i18n/README.md` regla 2: «Los catálogos guardan códigos, no etiquetas»; `docs/decisiones/19102026_v1_wizard-amanecer-vs-backlog.md` §4: espera «el contrato real del endpoint» | Hecho |
| Solicitudes de Frontend del 20-oct (`comunicaciones/recibidas/20102026_v1_…` 4.2 y 5.4; `20102026_v2_…` fila 2) | Piden el endpoint y su forma | Hecho |
| `cameia-gateway` `origin/develop` `84d7e02`, `application.yml` líneas 46 a 53 | La ruta `/api/v1/interviews/**` llega a Entrevista **sin reescribir el camino** y siempre autenticada (no está en `PUBLIC_ROUTES` salvo `/api/v1/interviews/health`) | Hecho |
| `cameia-perfil` `origin/develop` `1201943`, `ProfessionalRoleController` | Precedente de catálogo de solo lectura | Hecho |
| Jira (descripción, comentarios, padre, enlaces de CM-204) | **No leído:** el conector de Jira no respondió en esta sesión (§19, brecha B-1) | Brecha |

Ante una diferencia entre fuentes manda, en este orden: contrato publicado y seguridad; lo que hace `cameia-web`; los CA; el estándar; los atributos de calidad.

## 3. Criterios de aceptación y trazabilidad

HU-4.3 tiene 11 CA. Diez son solo de Frontend (columna «Técnico» = `F`, o `F y B` donde la parte de Backend es de HU-4.4). El único de Backend es CA-4.3.10. Los que cita la parte técnica del endpoint se trazan igual, porque su respuesta sale de este catálogo.

| CA | Dueño | Texto que fija el contrato (literal del backlog) | Requisito | Prueba (nombre exacto) | Postman |
|---|---|---|---|---|---|
| CA-4.3.10 | B | «GET /api/v1/interviews/config/catalog → 200 OK: tonos PROFESIONAL_NEUTRO, CALIDO, ESTRICTO, MOTIVADOR; idiomas es-CO, en; modalidades SIMULACION (disponible) y ENTRENO (no); formas AUDIO y TEXTO (ambas disponibles). Fuente: las mismas constantes con que valida HU-4.4.» | REQ-CAT-01 a REQ-CAT-05 | `PersonalizationCatalogTest.modes_shouldOfferSimulationAvailableAndTrainingUnavailable`, `…tones_shouldOfferFourTonesInOrder_whenRead`, `…answerFormats_shouldOfferAudioAndTextAvailable_whenRead`, `…languages_shouldOfferSpanishColombiaAndEnglishAvailable_whenRead`; `InterviewConfigControllerTest.catalog_shouldReturnExactBody_whenIdentityPresent`; `PersonalizationCatalogEndToEndTest.catalog_shouldReturn200WithCharset_whenRealServer`; `CatalogSchemaConsistencyTest.catalog_shouldMatchDatabaseCheck_forEveryColumn` | `200 catálogo completo` |
| CA-4.3.1 | F | «GET /api/v1/interviews/config/catalog → 200 OK (ENTRENO con disponible = false)» | REQ-CAT-02 | `…modes_shouldOfferSimulationAvailableAndTrainingUnavailable` | `200 catálogo completo` (prueba `ENTRENO no disponible`) |
| CA-4.3.3 | F | «GET /api/v1/interviews/config/catalog → 200 OK (AUDIO y TEXTO con disponible = true)» | REQ-CAT-02 | `…answerFormats_shouldOfferAudioAndTextAvailable_whenRead` | ídem (prueba `AUDIO y TEXTO disponibles`) |
| CA-4.3.9 | F y B | «idioma = en aceptado con el plan FREE» | REQ-CAT-06 | `InterviewConfigControllerTest.catalog_shouldBeIdenticalForEveryPlan_whenPlanHeaderDiffers` (el catálogo no depende del plan). El `POST …/sessions` → 201 con `en` y plan FREE es de CM-207 | `200 mismo catálogo con plan FREE` |
| CA-4.3.11 | F | «GET /api/v1/interviews/config/catalog → 500 Internal Server Error (aplica igual a cualquier 5xx)» | REQ-CAT-09 | `InterviewConfigControllerTest.catalog_shouldReturn500Generic_whenUnexpectedFailure` | No aplica: no se puede provocar un 500 real desde fuera (§12) |
| CA-4.3.2, 4.3.5, 4.3.6 | F | Tonos «Profesional y neutro», «Cálido», «Estricto», «Motivador» con descripción; preselección | REQ-CAT-03 (orden y códigos) | `…tones_shouldOfferFourTonesInOrder_whenRead` | `200 catálogo completo` |
| CA-4.3.4 | F | «idioma = en» | REQ-CAT-02 | `…languages_shouldOfferSpanishColombiaAndEnglishAvailable_whenRead` | ídem |
| CA-4.3.7 | F | Navegador sin grabación: lo decide `cameia-web` (MediaRecorder) | — (sin requisito de Backend: el catálogo dice que `AUDIO` está disponible en el sistema; la capacidad del navegador es del cliente) | — | — |
| CA-4.3.8 | F y B | «POST /api/v1/interviews/sessions con forma_respuesta = TEXTO → 201» | De CM-207 | De CM-207 | De CM-207 |

Requisitos sin CA directo, con su razón: REQ-CAT-07 (identidad) sale de RT-02 y RT-03-CA04 y del `CLAUDE.md` de Entrevista §3 («solo `GET /health` va sin identidad»); REQ-CAT-08 (parámetros ignorados) sale de RT-01-CA06; REQ-CAT-10 (`Content-Type`) de ASVS 4.1.1.

### 3.1 Comparación literal CA → spec

| Elemento | CA / nota técnica | Spec | Coincide |
|---|---|---|---|
| Método y ruta | `GET /api/v1/interviews/config/catalog` | igual | sí |
| Estado de éxito | 200 OK | 200 | sí |
| Tonos | `PROFESIONAL_NEUTRO, CALIDO, ESTRICTO, MOTIVADOR` | mismos códigos, mismo orden | sí |
| Idiomas | `es-CO, en` | mismos | sí |
| Modalidades | `SIMULACION` (disponible), `ENTRENO` (no) | `available: true` / `false` | sí |
| Formas de respuesta | `AUDIO` y `TEXTO`, ambas disponibles | `available: true` en las dos | sí |
| Nombre del indicador | el CA dice «disponible = false» (castellano, prosa) | `available` (inglés, `camelCase`, como exige el `CLAUDE.md` de Entrevista §4 y la decisión L-12 del 17-oct) | adaptado: es el nombre JSON, no un texto para la persona (D-CAT-02) |
| Fuente | «las mismas constantes con que valida HU-4.4», «sin tabla nueva» | `PersonalizationCatalog` del dominio, sin tabla | sí |
| 5xx | «→ 500 … aplica igual a cualquier 5xx» con «Ocurrió un error. Inténtalo de nuevo.» | 500 `INTERNAL_ERROR` con ese mensaje (lo da el manejador de CM-311) | sí |

## 4. Requisitos funcionales (EARS)

- **REQ-CAT-01** Cuando llegue `GET /api/v1/interviews/config/catalog` con identidad válida, el sistema debe responder `200` con un objeto JSON de exactamente cuatro miembros: `modes`, `tones`, `answerFormats` y `languages`; cada uno es una lista de objetos `{ "value": <texto>, "available": <booleano> }`.
- **REQ-CAT-02** El cuerpo debe ser exactamente este (orden de los miembros y de las listas incluido):

```json
{
  "modes":  [ { "value": "SIMULACION", "available": true }, { "value": "ENTRENO", "available": false } ],
  "tones":       [ { "value": "PROFESIONAL_NEUTRO", "available": true }, { "value": "CALIDO", "available": true },
                   { "value": "ESTRICTO", "available": true }, { "value": "MOTIVADOR", "available": true } ],
  "answerFormats": [ { "value": "AUDIO", "available": true }, { "value": "TEXTO", "available": true } ],
  "languages":   [ { "value": "es-CO", "available": true }, { "value": "en", "available": true } ]
}
```

- **REQ-CAT-03** El orden de cada lista debe ser el orden de declaración del enumerado de CM-311 (`Modality`, `Tone`, `AnswerMode`, `InterviewLanguage`), que coincide con el orden de los CA y con el de `cameia-web`. Cada `value` es el valor de `code()` del enumerado, el mismo que guarda la base (`ck_sesion_*`).
- **REQ-CAT-04** La disponibilidad debe salir solo de `PersonalizationCatalog.isAvailable(...)`: hoy, todo valor está disponible salvo `Modality.TRAINING` (`ENTRENO`). CM-207 debe usar este mismo método para rechazar una opción no disponible (fuera de esta CM).
- **REQ-CAT-05** Todo valor de los cuatro enumerados debe aparecer en el catálogo: si alguien agrega un valor a un enumerado, aparece en la lista sin tocar el controlador. Una prueba lo vigila (`catalog_shouldListEveryEnumValue_whenRead`).
- **REQ-CAT-06** El catálogo no debe depender del usuario ni de su plan: dos peticiones con `X-User-Plan: FREE` y `X-User-Plan: PREMIUM`, o con dos `X-User-Id` distintos, deben recibir el mismo cuerpo byte a byte (CA-4.3.9: el inglés está disponible en todos los planes).
- **REQ-CAT-07** Si `X-User-Id` falta, viene vacío, solo con espacios, con más de 128 caracteres o con un carácter de control, el sistema debe responder `401` `IDENTITY_REQUIRED` con «Tu sesión expiró. Inicia sesión de nuevo.» (regla de CM-311, `FirebaseUid.required`), sin cuerpo de catálogo. Con el servidor real, un encabezado con un byte de control o de más de 8 KB lo rechaza Tomcat antes de llegar a la aplicación y responde `400` `REQUEST_PATH_INVALID_FORMAT` con «La petición no es válida.» (forma común de CM-311, también sin catálogo); el `401` es el de la aplicación y el `400`, el del contenedor (`catalog_shouldReturn400_whenIdentityRejectedByContainer_onRealServer`).
- **REQ-CAT-08** El sistema debe ignorar los parámetros de consulta (`?plan=PREMIUM`, `?modality=ENTRENO`, `?lang=en`) y responder el mismo catálogo. No hay parámetros admitidos.
- **REQ-CAT-09** Cuando la lectura del catálogo falle por una causa imprevista, el sistema debe responder `500` `INTERNAL_ERROR` con «Ocurrió un error. Inténtalo de nuevo.», sin traza, nombre de clase ni mensaje interno (manejador de CM-311/CM-312).
- **REQ-CAT-10** Toda respuesta del endpoint debe declarar `charset=UTF-8` en el `Content-Type` (`application/json;charset=UTF-8` en 200; `application/problem+json;charset=UTF-8` en error) y llevar el encabezado `X-Request-Id` (filtro de CM-311).
- **REQ-CAT-11** Un método distinto de `GET` (y `HEAD`, que Spring atiende sin cuerpo) debe responder `405` `METHOD_NOT_ALLOWED` con un encabezado `Allow` que contiene `GET` (Spring también puede listar `HEAD` y `OPTIONS`; la prueba solo exige `GET`); un `Accept` que no admite JSON (`application/xml`) debe responder `406` `MEDIA_TYPE_NOT_ACCEPTABLE` (manejador de CM-311).
- **REQ-CAT-12** El endpoint debe quedar documentado en OpenAPI con `@Tag`, `@Operation`, el encabezado `X-User-Id` oculto (lo pone el Gateway) y `@ApiResponse` para 200, 401, 405, 406 y 500, cada error con el esquema `ProblemDetail` y un ejemplo con su `code`; los DTO con `@Schema` en cada campo (descripción, ejemplo, valores permitidos).

## 5. Contrato

| Elemento | Valor |
|---|---|
| Método y ruta en Entrevista | `GET /api/v1/interviews/config/catalog` (el Gateway reenvía la misma ruta sin reescribirla) |
| Encabezados de entrada | `X-User-Id` (obligatorio, lo pone el Gateway); `X-Request-Id` (opcional); `X-User-Plan` y demás se ignoran |
| Cuerpo de entrada | Ninguno; un cuerpo enviado se ignora |
| Parámetros | Ninguno; los que lleguen se ignoran |
| 200 | Cuerpo de REQ-CAT-02; `Content-Type: application/json;charset=UTF-8` |
| 401 | `IDENTITY_REQUIRED` |
| 405 | `METHOD_NOT_ALLOWED`, `Allow` contiene `GET` |
| 406 | `MEDIA_TYPE_NOT_ACCEPTABLE` |
| 500 | `INTERNAL_ERROR` |
| Caché | Sin encabezado `Cache-Control` propio (D-CAT-05) |
| Consumidor | `cameia-web`, CM-205 (pasos «Modo y tono» e «Idioma y forma de respuesta»). Cambio **aditivo**: el endpoint es nuevo |

**Lo que el cliente hace con el catálogo** (CA de Frontend, para que el contrato baste): las etiquetas y descripciones visibles («Profesional y neutro», «Formal y directo…») las pone `cameia-web` desde su i18n con el `value` (`interview:tono.<value>`); una opción con `available: false` se muestra deshabilitada con «Próximamente» (RT-04-CA01); la preselección (Simulación, Profesional y neutro, Audio, Español) es del cliente (CA-4.3.1, 4.3.2, 4.3.3, 4.3.4). El catálogo no envía etiquetas, descripciones ni preselección (D-CAT-03, D-CAT-04).

## 6. Diseño

| Capa | Pieza | Qué hace |
|---|---|---|
| `domain.model` | `CatalogOption<E extends Enum<E>>` (`record`: `E value`, `boolean available`) | Una opción con su disponibilidad |
| `domain.policy` | `PersonalizationCatalog` (clase `final`, sin Spring) | La única fuente: listas en orden de enumerado y `isAvailable(Enum<?>)`; hoy solo `Modality.TRAINING` no está disponible |
| `application.service` | `GetPersonalizationCatalogService` (`@Service`) | Caso de uso de lectura: devuelve el catálogo. Existe para que el controlador hable con la aplicación como el resto del servicio y para poder simular el fallo de CA-4.3.11 en la prueba web |
| `infrastructure.config` | `DomainPolicyConfiguration` (`@Configuration`) | Crea el bean `PersonalizationCatalog` con `new`, como `DomainPolicyConfiguration` de Cuentas. CM-207 agrega aquí el `Clock` |
| `presentation.controller` | `InterviewConfigController` (clase de paquete, `@RestController`, `@RequestMapping("/api/v1/interviews/config")`) | `GET /catalog`; valida la identidad con `FirebaseUid.required` y mapea a los DTO |
| `presentation.dto` | `PersonalizationCatalogResponse` (`record` con 4 listas), `CatalogOptionResponse` (`record String value, boolean available`) | Contrato de salida |

Sin cambios en: base de datos, `ErrorCode`, `BusinessExceptionHandler`, enumerados de CM-311, `pom.xml`.

## 7. Matriz de validación por entrada (R2)

El endpoint no tiene cuerpo ni parámetros. La única entrada que se valida es `X-User-Id`; las demás se ignoran o las resuelve el marco.

| Entrada | Caso | Valor literal | Resultado | `code` | Mensaje | Prueba |
|---|---|---|---|---|---|---|
| `X-User-Id` | ausente | (sin encabezado) | 401 | `IDENTITY_REQUIRED` | «Tu sesión expiró. Inicia sesión de nuevo.» | `InterviewConfigControllerTest.catalog_shouldReturn401_whenIdentityInvalid` (fila 1) |
| | vacío | `""` | 401 | ídem | ídem | ídem (fila 2) |
| | solo espacios | `"   "` | 401 | ídem | ídem | ídem (fila 3) |
| | solo tabulador | `"\t"` | 401 | ídem | ídem | ídem (fila 4) |
| | 129 caracteres | `"u".repeat(129)` | 401 | ídem | ídem | ídem (fila 5) |
| | carácter de control | `"uid\u0007"` | 401 desde la aplicación (MockMvc); 400 si el contenedor lo rechaza antes (servidor real) | `IDENTITY_REQUIRED`; con servidor real, `REQUEST_PATH_INVALID_FORMAT` | «Tu sesión expiró. Inicia sesión de nuevo.»; con servidor real, «La petición no es válida.» | ídem (fila 6); servidor real: `PersonalizationCatalogEndToEndTest.catalog_shouldReturn400_whenIdentityRejectedByContainer_onRealServer` |
| | más de 8 KB (9000 caracteres) | `"u".repeat(9000)` | 400 (lo rechaza el contenedor; la aplicación no llega a leerlo) | `REQUEST_PATH_INVALID_FORMAT` | «La petición no es válida.» | `PersonalizationCatalogEndToEndTest.catalog_shouldReturn400_whenIdentityRejectedByContainer_onRealServer` |
| | 128 caracteres | `"u".repeat(128)` | 200 | — | — | `…catalog_shouldReturn200_whenIdentityHas128Chars` |
| | válido con tilde y eñe | `"uid-ñandú"` | 200 | — | — | `…catalog_shouldReturnExactBody_whenIdentityPresent` (usa este valor) |
| `X-User-Plan` | `FREE` / `PREMIUM` / ausente | literal | 200, mismo cuerpo | — | — | `…catalog_shouldBeIdenticalForEveryPlan_whenPlanHeaderDiffers` |
| Parámetros | inesperados | `?plan=PREMIUM&modality=ENTRENO&lang=en` | 200, mismo cuerpo | — | — | `…catalog_shouldIgnoreQueryParameters_whenUnexpected` |
| Parámetro hostil | `<script>` y `%` | `?q=%3Cscript%3E&r=%25` | 200, mismo cuerpo; el cuerpo no contiene `script` | — | — | ídem (segunda petición) |
| Cuerpo | inesperado en un GET | `{"modes":[]}` con `Content-Type: application/json` | 200, mismo cuerpo (no se lee) | — | — | `…catalog_shouldIgnoreBody_whenSent` |
| `Accept` | `application/xml` | — | 406 | `MEDIA_TYPE_NOT_ACCEPTABLE` | «Tipo de respuesta no admitido.» | `PersonalizationCatalogEndToEndTest.catalog_shouldReturn406_whenAcceptXml` |
| Método | `POST`, `PUT`, `DELETE`, `PATCH` | — | 405 + `Allow` | `METHOD_NOT_ALLOWED` | «Método no permitido.» | `…catalog_shouldReturn405WithAllow_whenMethodNotGet` (parametrizada, 4) |

## 8. Base de datos (R4)

Sin migración: la nota técnica dice «desde constantes de configuración (sin tabla nueva)». Lo que sí se exige es que el catálogo y la base digan lo mismo, porque CM-207 guardará en `sesion` lo que el catálogo ofrece:

| Lista | Columna de CM-311 | Restricción | Prueba |
|---|---|---|---|
| `modes` | `sesion.modalidad varchar(20)` | `ck_sesion_modalidad` IN (`SIMULACION`, `ENTRENO`) | `CatalogSchemaConsistencyTest.catalog_shouldMatchDatabaseCheck_forEveryColumn` (fila `ck_sesion_modalidad`) |
| `tones` | `sesion.tono varchar(20)` | `ck_sesion_tono` | ídem |
| `answerFormats` | `sesion.forma_respuesta varchar(10)` | `ck_sesion_forma_respuesta` | ídem |
| `languages` | `sesion.idioma varchar(5)` | `ck_sesion_idioma` | ídem |

La prueba lee `pg_get_constraintdef` de cada restricción, extrae los literales entre comillas simples y los compara como conjunto con los `code()` del enumerado; también comprueba que ningún valor supera el `VARCHAR(n)` de su columna (`information_schema.columns.character_maximum_length`). Así, un valor nuevo en un enumerado sin su migración rompe la prueba y no un `INSERT` en producción.

**Siguiente migración libre del repo** (integración de la ola, §17): CM-204 no usa ninguna. `V2` queda para CM-209, `V3` para CM-207 y `V4` para CM-216 si la necesita.

## 9. Casos borde (R5)

| Grupo | Caso | Datos | Resultado | Prueba |
|---|---|---|---|---|
| Presencia | identidad ausente, vacía, espacios, tabulador | §7 | 401 | `…_whenIdentityInvalid` |
| Texto | identidad de 128 / 129; con eñe y tilde; con control | §7 | 200 / 401 / 200 / 401 | §7 |
| Números | — | No aplica: no hay números de entrada | — | — |
| Fechas | — | No aplica: no hay fechas | — | — |
| Enumerados | cada valor de los 4 enumerados aparece una vez y en orden; `ENTRENO` es el único no disponible | — | — | `PersonalizationCatalogTest` (5 pruebas) |
| Enumerados | `isAvailable` de los 10 valores | parametrizada | 9 `true`, 1 `false` | `PersonalizationCatalogTest.isAvailable_shouldBeFalseOnlyForTraining` |
| Enumerados | valor del enumerado sin su `CHECK` en la base | — | prueba roja | `CatalogSchemaConsistencyTest` |
| Duplicados | — | No aplica: lectura sin efecto | — | — |
| Estado | — | No aplica: el catálogo no tiene estado | — | — |
| Idempotencia | 50 peticiones seguidas | mismo cuerpo | iguales | `PersonalizationCatalogEndToEndTest.catalog_shouldMeetLatencyBudget_when50Requests` compara cada cuerpo con el primero |
| Propiedad | usuario A y usuario B | `uid-a`, `uid-b` | mismo cuerpo (no hay recurso propio) | `…catalog_shouldBeIdenticalForEveryPlan_whenPlanHeaderDiffers` (incluye dos usuarios) |
| Propiedad | identidad en la consulta | `?firebaseUid=otro` | ignorada | `…catalog_shouldIgnoreQueryParameters_whenUnexpected` |
| Colecciones | ninguna lista vacía; tamaños 2, 4, 2, 2 | — | — | `PersonalizationCatalogTest` |
| Concurrencia | dos hilos leen a la vez | — | No aplica: objeto inmutable sin estado (listas `List.copyOf`); se justifica por diseño y lo vigila la prueba de inmutabilidad `catalog_shouldReturnUnmodifiableLists_whenRead` | `PersonalizationCatalogTest` |
| Falla parcial | fallo imprevisto | `IllegalStateException("detalle interno")` | 500 sin el texto | `…catalog_shouldReturn500Generic_whenUnexpectedFailure` |
| Dependencias | base caída | — | **El catálogo sigue respondiendo 200**: no toca la base. Prueba: ninguna (no hay ruta a la base); se deja dicho como propiedad de fiabilidad (§14, FIA-01) | — |
| Carga | cuerpo inesperado; `Accept` XML; método no admitido | §7 | 200 / 406 / 405 | §7 |
| Rutas | `HEAD`, `OPTIONS`, barra final (`/catalog/`), mayúsculas (`/Catalog`) | petición real al jar | `HEAD` 200 sin cuerpo y `OPTIONS` 200 con `Allow` (comportamiento por defecto de Spring: no entregan datos); barra final y mayúsculas 404 `ROUTE_NOT_FOUND` | Medido en la exploración adversarial; el 404 lo cubre `FrameworkErrorsTest` |

## 10. Errores (R3)

CM-204 no crea códigos. Usa los del catálogo de CM-311/CM-312 y agrega la ruta a su columna «Endpoints» de `docs/errores.md`:

| Código | HTTP | Cuándo en este endpoint | Mensaje | Prueba |
|---|---|---|---|---|
| `IDENTITY_REQUIRED` | 401 | `X-User-Id` inválido (§7) | «Tu sesión expiró. Inicia sesión de nuevo.» | `…catalog_shouldReturn401_whenIdentityInvalid` |
| `METHOD_NOT_ALLOWED` | 405 | método distinto de GET/HEAD | «Método no permitido.» | `…catalog_shouldReturn405WithAllow_whenMethodNotGet` |
| `MEDIA_TYPE_NOT_ACCEPTABLE` | 406 | `Accept: application/xml` | «Tipo de respuesta no admitido.» | `…catalog_shouldReturn406_whenAcceptXml` |
| `INTERNAL_ERROR` | 500 | fallo imprevisto | «Ocurrió un error. Inténtalo de nuevo.» | `…catalog_shouldReturn500Generic_whenUnexpectedFailure` |

**Rutas al genérico revisadas:** la única forma de llegar al 500 es un fallo de programación (el catálogo no hace E/S). No hay ninguna causa previsible sin código propio. `DATABASE_UNAVAILABLE` no aplica porque no hay acceso a la base. ADR: no hace falta uno nuevo (no hay decisión de contrato de errores); las decisiones del contrato del catálogo van a la bitácora de IA (§15).

## 11. Seguridad (R6)

### 11.1 ASVS 5.0.0 nivel 1 (matriz de `cameia-infra`, solo lectura)

| ID | Estado en la matriz | Qué hace CM-204 | Prueba |
|---|---|---|---|
| 1.2.3 | CUMPLE | Respuesta por DTO serializado con Jackson, sin JSON a mano | `…catalog_shouldReturnExactBody_whenIdentityPresent` |
| 2.1.1 | CUMPLE | Esta spec documenta la única entrada validada | — |
| 2.2.1 / 2.2.2 | CUMPLE | Identidad validada en el servidor; el catálogo es la lista blanca que usará CM-207 | §7 |
| 4.1.1 | NO_CUMPLE (por el Gateway) | 200 y errores con `charset=UTF-8` | `…catalog_shouldReturn200WithCharset_whenRealServer`, `…_whenIdentityMissing_onRealServer` |
| 8.1.1 / 8.2.1 | CUMPLE | Ruta privada: el Gateway exige token (no está en `PUBLIC_ROUTES`) y Entrevista exige `X-User-Id` | `…catalog_shouldReturn401_whenIdentityInvalid` |
| 8.2.2 | CUMPLE | No aplica a este recurso: el catálogo no es de nadie; se demuestra que dos usuarios ven lo mismo y que la identidad en la consulta se ignora | `…_whenPlanHeaderDiffers`, `…_whenUnexpected` |
| 14.2.1 | CUMPLE | Ningún dato sensible en la URL | — |
| 15.3.1 | CUMPLE | DTO explícito: solo `value` y `available` | `…catalog_shouldReturnExactBody_whenIdentityPresent` (igualdad exacta) |

No hay hallazgo nuevo para la matriz.

### 11.2 OWASP API Security Top 10

| Riesgo | Aplica | Cómo |
|---|---|---|
| API1 BOLA | No | Sin recurso propio; mismo cuerpo para todos (prueba de dos usuarios) |
| API2 Autenticación | Sí | Solo `X-User-Id` del Gateway, 401 si falta (§7) |
| API3 Propiedades | Sí | DTO explícito; ni entidades ni campos internos |
| API4 Recursos | Sí | Respuesta fija de ~500 bytes; sin parámetros que la agranden |
| API5 Función | Sí | Cualquier usuario autenticado puede leerlo; no hay función administrativa |
| API6 Flujos sensibles | No | Lectura sin efecto |
| API7 SSRF | No | Sin URL de salida |
| API8 Configuración | Sí | OpenAPI solo en `local` (ADR 0004 de Entrevista); errores sin traza |
| API9 Inventario | Sí | Ruta bajo `/api/v1/interviews/**`, documentada en OpenAPI; prueba `openApi_shouldDocumentCatalog_withEveryResponse` |
| API10 Consumo inseguro | No | No consume otro servicio |

Pruebas negativas de SEG-02: sin identidad (6 casos), identidad en la consulta ignorada, dos usuarios con el mismo resultado.

## 12. Postman y Newman (R11)

En la colección que crea CM-311 (`postman/cameia-entrevista.postman_collection.json`), carpeta nueva «Catálogo de personalización», contra `{{baseUrl}}` = `http://localhost:8083` (directo al servicio, con `X-User-Id` a mano):

| Petición | Encabezados | Espera (`pm.test`) |
|---|---|---|
| `200 catálogo completo` | `X-User-Id: uid-postman-a`, `X-Request-Id: postman-cat-1` | 200; `Content-Type` contiene `application/json` y `charset=UTF-8`; cuerpo igual a REQ-CAT-02; prueba `ENTRENO no disponible`; prueba `AUDIO y TEXTO disponibles`; `X-Request-Id` = `postman-cat-1`; tiempo < 200 ms |
| `200 mismo catálogo con plan FREE` | `X-User-Id: uid-postman-b`, `X-User-Plan: FREE` | 200; cuerpo igual al anterior (se guarda el primero en una variable de colección) |
| `200 parámetros inesperados ignorados` | `X-User-Id: uid-postman-a`; URL `…/catalog?plan=PREMIUM&modality=ENTRENO` | 200; cuerpo igual |
| `401 sin identidad` | ninguno | 401; `code = IDENTITY_REQUIRED`; `detail` = «Tu sesión expiró. Inicia sesión de nuevo.»; `problem+json` con charset; `requestId` presente; sin `trace` ni `exception` |
| `401 identidad en blanco` | `X-User-Id: "   "` | ídem |
| `405 POST no admitido` | `X-User-Id: uid-postman-a` | 405; `code = METHOD_NOT_ALLOWED`; `Allow` contiene `GET` |
| `406 Accept XML` | `X-User-Id: uid-postman-a`, `Accept: application/xml` | 406; `code = MEDIA_TYPE_NOT_ACCEPTABLE` |

CA-4.3.11 (500) no tiene petición: no hay forma de provocar un fallo imprevisto desde fuera sin alterar el servicio; lo cubre la prueba web.

Comando (en la raíz del repo, con el jar arrancado y la base de compose): `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --folder "Catálogo de personalización" --reporters cli,junit --reporter-junit-export ../../ejecucion/evidencia/CM-204/newman.xml` → 7 peticiones, 0 fallos. Al cerrar la CM se corre además la colección completa (las 4 de CM-311 + 7).

## 13. Pruebas, cobertura y comandos (R9, R10)

| Capa | Clase | Herramienta |
|---|---|---|
| Dominio | `PersonalizationCatalogTest` (`src/test/java/tech/cameia/entrevista/domain/policy/`) | JUnit 5 + AssertJ, sin Spring |
| Aplicación | `GetPersonalizationCatalogServiceTest` | JUnit 5, sin Spring (objeto real) |
| Web | `InterviewConfigControllerTest` (`presentation/controller/`) | MockMvc `standaloneSetup` con `ProblemDetailTestSupport.errorHandler()` de CM-311 |
| Integración | `PersonalizationCatalogEndToEndTest` (`presentation/`) | `@SpringBootTest(RANDOM_PORT)` + Testcontainers + `cameia.llm.model=modelo-de-prueba` |
| Esquema | `CatalogSchemaConsistencyTest` (`infrastructure/persistence/`) | `@SpringBootTest` + Testcontainers + `JdbcTemplate` |
| Arquitectura | `LayeredArchitectureTest` (de CM-311, sin cambios) | ArchUnit |

Clases a medir (≥ 90 % de líneas y de ramas cada una): `PersonalizationCatalog`, `CatalogOption`, `GetPersonalizationCatalogService`, `DomainPolicyConfiguration`, `InterviewConfigController`, `PersonalizationCatalogResponse`, `CatalogOptionResponse`. La cobertura global del repo no baja.

Comandos (PowerShell, en el worktree de la rama):

```
docker version
./mvnw.cmd -B -ntp test -Dtest="PersonalizationCatalogTest,GetPersonalizationCatalogServiceTest,InterviewConfigControllerTest"
./mvnw.cmd -B -ntp test -Dtest="PersonalizationCatalogEndToEndTest,CatalogSchemaConsistencyTest,LayeredArchitectureTest,ErrorCodeDocumentationTest"
./mvnw.cmd -B -ntp clean verify
Select-String -Path target/site/jacoco/jacoco.csv -Pattern "PersonalizationCatalog|CatalogOption|GetPersonalizationCatalogService|DomainPolicyConfiguration|InterviewConfigController"
docker compose up -d entrevista-db
./mvnw.cmd -B -ntp -DskipTests package
java -jar target/entrevista-0.0.1-SNAPSHOT.jar      # SERVER_PORT=8083, perfil local
npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --reporters cli,junit
```

## 14. Atributos de calidad (R13)

HU-4.3 es de la épica HE-04; su anexo declara seguridad, desempeño, fiabilidad e interacción. Se declaran además los transversales que el cambio toca.

| Atributo | Métrica | Límite | Cómo lo cumple CM-204 | Prueba o comando | Resultado real |
|---|---|---|---|---|---|
| AC-0002 desempeño | DES-02 | p95 ≤ 2 s en operación JSON propia | Respuesta en memoria, sin base ni red | `PersonalizationCatalogEndToEndTest.catalog_shouldMeetLatencyBudget_when50Requests` (5 de calentamiento + 50 medidas; p95 ≤ 2000 ms) y Newman (< 200 ms por petición) | p50 = 3,2 ms, p95 = 4,9 ms, máx. 27,9 ms con el jar (50 peticiones); la prueba pasa (p95 ≤ 2000 ms); Newman: media 23 ms, ninguna ≥ 200 ms. CUMPLE |
| AC-0002 desempeño | DES-04 | 5xx propios < 1 % | Ninguna causa previsible llega al 500 | §10 | 0 respuestas 500 en 43 casos adversariales, 200 peticiones concurrentes y 14 de Newman. CUMPLE |
| AC-0003 seguridad | SEG-01 | ASVS N1 aplicable 100 % | §11.1 | ídem | Todas las filas aplicables de §11.1 con su prueba en verde. CUMPLE |
| AC-0003 seguridad | SEG-02 | 100 % de accesos sin identidad rechazados | 6 casos de §7 | `…_whenIdentityInvalid` | 6 casos de §7 en la prueba web (401) y, con servidor real, 401 sin encabezado, vacío, en blanco, tabulador y 129 caracteres, y 400 del contenedor con byte de control o de 9000 caracteres; ninguno devolvió el catálogo. CUMPLE |
| AC-0003 seguridad | SEG-04 | Cero datos sensibles en logs | El endpoint no registra nada; los 401 los registra el manejador sin el valor | revisión + `logs_shouldNotContainBodyOrValues` de CM-311 | 0 apariciones de `script` y de `Set-Cookie` y 0 líneas `ERROR` en el log tras el barrido; solo `firebaseUid` y `requestId`. CUMPLE |
| AC-0004 fiabilidad | FIA-01 | Conserva el último estado | No cambia estado; responde aunque la base esté caída (no la usa) | Por diseño (§9) | Cumple por diseño: el endpoint no usa la base ni otro servicio, por eso no hay prueba con la base caída |
| AC-0004 fiabilidad | FIA-02 | Cero efectos al repetir | Lectura pura; 50 respuestas idénticas | `…when50Requests` | 50 respuestas idénticas con servidor real y 200 peticiones en 20 hilos con un solo hash de cuerpo. CUMPLE |
| AC-0006 interacción | INT (coherencia del cliente) | Errores con `code` y mensaje; opciones de una sola fuente | El cliente deja de tener las opciones fijas | `…catalog_shouldReturnExactBody_whenIdentityPresent` | Cuerpo exacto probado en dominio, web, servidor real y Newman; `CatalogSchemaConsistencyTest` iguala el catálogo a los 4 `CHECK`. CUMPLE |
| Transversal | MAN-01 | ≥ 90 % líneas y ramas en lo nuevo | §13 | `jacoco.csv` | 100 % de líneas y ramas en las 7 clases nuevas (JaCoCo). CUMPLE |
| Transversal | IOP-01 | API documentada | OpenAPI completo | `…openApi_shouldDocumentCatalog_withEveryResponse` | Respuestas 200, 401, 405, 406 y 500 con ejemplo, esquema con los cuatro miembros y `X-User-Id` oculto. CUMPLE |
| Restricción | REST-0002 | Sin estado en memoria entre peticiones | Objeto inmutable, sin campos mutables | `…catalog_shouldReturnUnmodifiableLists_whenRead` | Objeto inmutable; las listas no se pueden modificar. CUMPLE |
| Restricción | REST-0003 | Datos personales mínimos | No lee ni devuelve datos personales | revisión | No lee ni devuelve datos personales (revisión). CUMPLE |

## 15. Decisiones

| # | Decisión | Opciones consideradas | Por qué | Quién |
|---|---|---|---|---|
| D-CAT-01 | El catálogo exige `X-User-Id` (401 sin ella) | A) exigir identidad; B) ruta sin identidad, como el catálogo de roles de Perfil | El Gateway ya exige token en `/api/v1/interviews/**` (solo `/health` es pública), así que el cliente siempre la manda; el `CLAUDE.md` de Entrevista §3 dice que solo `GET /health` va sin identidad y que una ruta nueva sin identidad necesita justificación. B no da nada al usuario y abre una excepción | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula).** Si elige B: se quita `FirebaseUid.required` del controlador y las 6 pruebas de 401 pasan a ser 200 |
| D-CAT-02 | Forma: objeto con cuatro listas `modes`, `tones`, `answerFormats`, `languages`, cada elemento `{value, available}`; nombres JSON en inglés y `camelCase` | A) esa; B) `modalities`/`answerModes`/`code` (los nombres de los enumerados de CM-311); C) nombres del backlog en español (`modalidades`, `disponible`) | A son exactamente los nombres que ya usa `cameia-web` (`mode`, `answer_format`, `session_language`, ya en `camelCase`) y los que trae el borrador de respuesta a Frontend del 20-oct (`comunicaciones/20102026_v1_respuesta-nueva-entrevista-para-frontend.md` §6.2, sin enviar), así que Frontend no cambia nada; el `CLAUDE.md` de Entrevista §4 y la decisión L-12 piden inglés y `camelCase`. Los **valores** (`SIMULACION`, `es-CO`) se quedan como en el backlog, la base y `cameia-web`. El nombre de los enumerados del dominio no tiene que coincidir con el del contrato | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula).** Si elige B o C, cambia solo el DTO y las pruebas de cuerpo exacto, y se corrige el borrador a Frontend antes de enviarlo |
| D-CAT-03 | Sin etiquetas ni descripciones en la respuesta | A) solo códigos; B) etiquetas en español e inglés | La regla 2 de `cameia-web/src/i18n/README.md`: «Los catálogos guardan códigos, no etiquetas»; los textos ya están en su i18n en los dos idiomas | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-CAT-04 | Sin preselección (`default`) en la respuesta | A) sin ella; B) `"preselected": true` en la opción por defecto | Los CA de preselección son de Frontend (`F`) y la nota técnica solo pide la disponibilidad; agregarla sería un campo «por si acaso» (R7). Si el PO quiere moverla al servidor, es un miembro nuevo, aditivo | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-CAT-05 | Sin `Cache-Control` propio | A) sin encabezado; B) `private, max-age=300`; C) `no-store` | El catálogo no es sensible (no aplica `no-store`) y cambia solo con un despliegue; TanStack Query ya lo guarda en el cliente. B ahorra poco y complica ver un cambio de disponibilidad | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-CAT-06 | La disponibilidad vive en `PersonalizationCatalog` del dominio (constante), no en `application.properties` | A) constante de dominio; B) propiedad configurable por ambiente | «Desde constantes de configuración (sin tabla nueva)» admite las dos. A se prueba sin Spring, no se puede desalinear entre ambientes y la usa CM-207 igual; habilitar Entreno exige además lógica nueva (HU-5.3, HU-5.4), o sea un despliegue en cualquier caso | **DECIDIDA POR RECOMENDACIÓN (ratifica Paula)** |
| D-CAT-07 | Un caso de uso de aplicación aunque solo devuelva el objeto | A) controlador → servicio de aplicación; B) controlador → bean de dominio | Mantiene la regla de capas de todo el servicio y deja simular el fallo de CA-4.3.11 en la prueba web | Inferencia de Backend (R7) |

## 16. Fuera de alcance

Validar la creación de la sesión contra el catálogo y el 422 por `ENTRENO` (CM-207, CA-4.4.3); la preselección y las etiquetas (Frontend, CM-205); detectar si el navegador graba (CA-4.3.7, Frontend); el cupo por plan (HU-4.6, CM-259); el idioma `es`/`en` hacia el catálogo de roles y Voz (CM-207 y voz).

## 17. Integración con las otras CM de la ola en `cameia-entrevista`

| Recurso compartido | CM-311 | CM-204 | CM-209 | CM-207 | CM-216 |
|---|---|---|---|---|---|
| Migración Flyway | `V1` | ninguna | `V2` | `V3` | `V4` (si hace falta) |
| `ErrorCode` | +14 | ninguno | los suyos al final | los suyos al final | los suyos al final |
| `docs/errores.md` | crea las filas | solo columna «Endpoints» de 4 filas | filas nuevas | filas nuevas | filas nuevas |
| `infrastructure/config/DomainPolicyConfiguration` | — | **la crea** con `PersonalizationCatalog` | — | agrega el `Clock` | usa |
| `postman/` | crea | carpeta «Catálogo de personalización» | — (sin HTTP) | carpeta «Crear sesión» | carpeta «Responder» |
| `PersonalizationCatalog` | — | crea | — | usa `isAvailable` | — |

Rama: `CM-204-catalogo-personalizacion` desde `origin/develop` con CM-311 fusionada. Si CM-311 aún no está fusionada, desde la punta de `CM-311-motor-base-entrevista` (stack; solo el PR de abajo apunta a `develop`) y se rebasa con `--onto` al fusionarse. Un PR, cuatro commits (§ plan). Diff estimado: unas 650 líneas (≈ 220 de código, ≈ 330 de pruebas, ≈ 100 de documentación y Postman).

## 18. Revalidar cuando CM-311 y CM-312 estén implementadas

| Lo que esta spec toma prestado | Debe existir en | Cómo se confirma |
|---|---|---|
| `Modality` (`SIMULATION("SIMULACION")`, `TRAINING("ENTRENO")`), `Tone` (`PROFESSIONAL_NEUTRAL`, `WARM`, `STRICT`, `MOTIVATING`), `AnswerMode` (`AUDIO`, `TEXT("TEXTO")`), `InterviewLanguage` (`SPANISH_COLOMBIA("es-CO")`, `ENGLISH("en")`), cada uno con `code()` y en ese orden | `src/main/java/tech/cameia/entrevista/domain/model/` | `git show origin/develop:<ruta>`; `PersonalizationCatalogTest` |
| `FirebaseUid.required(String)` que lanza `IdentityRequiredException` | `domain/model/FirebaseUid.java` | `FirebaseUidTest` de CM-311 |
| Manejador con 401 `IDENTITY_REQUIRED`, 405, 406 y 500 y los mensajes de §10 | `presentation/advice/BusinessExceptionHandler.java` | `BusinessExceptionHandlerTest`, `FrameworkErrorsTest` |
| `ProblemDetailTestSupport.errorHandler()` | `src/test/java/tech/cameia/entrevista/presentation/advice/ProblemDetailTestSupport.java` | compilar `InterviewConfigControllerTest` |
| `RequestIdFilter` (eco de `X-Request-Id` en 200) | `presentation/filter/RequestIdFilter.java` | `…_whenRealServer` |
| Restricciones `ck_sesion_modalidad`, `ck_sesion_tono`, `ck_sesion_forma_respuesta`, `ck_sesion_idioma` en el esquema `microentrevista` | `db/migration/V1__esquema_inicial_entrevista.sql` | `CatalogSchemaConsistencyTest` |
| Arranque de pruebas con `cameia.llm.model=modelo-de-prueba` y Testcontainers `postgres:16-alpine` | CM-312 / CM-311 | `EntrevistaApplicationTests` |
| Colección `postman/cameia-entrevista.postman_collection.json` y entorno `local` | CM-311 T7 | abrir el archivo |
| Columnas de `docs/errores.md` `Código \| HTTP \| Endpoints \| Campo \| Mensaje \| Origen \| Prueba` | CM-312 | `ErrorCodeDocumentationTest` |
| `spring.servlet.encoding.force-response=true` (charset en 200) | `application.properties` (CM-311 T1) | `…_whenRealServer` |

Si un nombre difiere, se actualiza esta spec y las tarjetas antes de ejecutar (tarjeta T0).

## 19. Hallazgos con destino

| # | Hallazgo | Tipo | Destino |
|---|---|---|---|
| B-1 | Jira no respondió en esta sesión: no se leyeron descripción, comentarios, padre ni enlaces de CM-204 | Verificación previa | Tarjeta T0: leerlos antes de empezar y anotar si cambian algo |
| H-1 | `cameia-web` ya envía `camelCase` (`answerFormat`, `sessionLanguage`) desde el PR #100 | Cerrado | Los nombres del cuerpo de crear la sesión los fija CM-207 con esos mismos nombres |
| H-1b | Web no consume todavía este endpoint: sus opciones son fijas (`interviewSetup.types.ts`) y se conectará cuando exista (respuesta de Frontend del 22-oct, sección 3, fila 6.2) | Sin consumidor | Avisar a Frontend al publicar la OpenAPI |
| H-2 | `GET /api/v1/interviews/health` está en `DEV_PUBLIC_ROUTES` del Gateway (pública solo con el perfil local) y el Gateway la reenvía con ese camino, pero Entrevista solo atiende `GET /health`: en local, por el Gateway, respondería 404 | Corrección de código (Gateway o Entrevista, ambos de Backend), baja prioridad | Fuera de CM-204 (no es su ruta). Destino: tarea nueva vía Vela o pieza de CM-296 si Paula la acepta; anotado en la spec de CM-296 §fuera de alcance |
| H-3 | La hoja «Backlog» asocia HU-4.3 a CM-263 y la tarea de Backend es CM-204 | Hecho, sin acción | — |
| H-4 | Jira: 1,1 h; Backend estima 3 a 4 h | Acción para Vela | Aviso en el resumen de la ola |

## 20. Lista de verificación de la CM

| # | Qué | Comando o prueba | Esperado |
|---|---|---|---|
| V-01 | CA-4.3.10 | `-Dtest=PersonalizationCatalogTest,InterviewConfigControllerTest` y Newman `200 catálogo completo` | cuerpo igual a REQ-CAT-02 |
| V-02 | CA-4.3.1 / 4.3.3 / 4.3.4 | mismas pruebas | `ENTRENO` `false`; `AUDIO`, `TEXTO`, `en` `true` |
| V-03 | CA-4.3.9 | `…_whenPlanHeaderDiffers`; Newman `plan FREE` | cuerpos idénticos |
| V-04 | CA-4.3.11 | `…_whenUnexpectedFailure` | 500 `INTERNAL_ERROR` sin «detalle interno» |
| V-05 | R1 | `git diff` de `src/main`: identificadores en inglés, Javadoc en español | cumple |
| V-06 | R2 | §7 completo | 6 × 401, 128 → 200 |
| V-07 | R3 | §10; petición real 401/405/406 en Newman | 0 fallos |
| V-08 | R4 | `CatalogSchemaConsistencyTest` | 4 restricciones iguales a los enumerados |
| V-09 | R5 | §9 | cada fila aplicable con su prueba en verde |
| V-10 | R6 | §11; `git grep -n "log\." src/main/java/tech/cameia/entrevista/presentation/controller/InterviewConfigController.java` | sin registros |
| V-11 | R7 | revisión: ≤ 20 líneas por método, sin estado, sin dependencias nuevas | cumple |
| V-12 | R8 | `openApi_shouldDocumentCatalog_withEveryResponse`; Javadoc y `package-info`; `docs/errores.md` | verde |
| V-13 | R9 | `./mvnw.cmd -B -ntp clean verify`; ninguna clase de Testcontainers `Skipped` | `BUILD SUCCESS` |
| V-14 | R10 | `jacoco.csv` | ≥ 90 % líneas y ramas en las 7 clases |
| V-15 | R11 | Newman carpeta y colección completa | 7 y 14 peticiones, 0 fallos |
| V-16 | R12 | tabla §3 sin filas vacías | cumple |
| V-17 | R13 | tabla §14 con el resultado real | cada fila con número |
| V-18 | R14 | sin push, PR ni Jira fuera de lo permitido | cumple |
| V-19 | Adversarial: identidad hostil (`"uid\u0007"`, 129), consulta hostil (`%3Cscript%3E`), dos usuarios, 50 repeticiones, `Accept` XML, `POST` | §7 y §9 | rechazado o ignorado según la tabla |

## 21. Hechos, inferencias y decisiones

- **Hechos** (con fuente): todo §2, §3 y §5 salvo lo marcado; ruta sin reescritura del Gateway; códigos y orden de `cameia-web`.
- **Inferencias justificadas:** D-CAT-07; que el endpoint no necesita base (nota técnica «sin tabla nueva»); estimación de 3 a 4 h.
- **Decisiones por recomendación (ratificadas por Paula el 22-oct, opción A):** D-CAT-01 a D-CAT-06.
- **PENDIENTE de persona:** ninguno bloqueante. Jira (B-1) es una verificación de la tarjeta T0.

## 22. Puerta de listo

| Condición | Estado |
|---|---|
| Tabla CA → requisito → prueba → Postman y tabla de atributos | Hecha (§3, §14) |
| Matriz por entrada, casos borde, ruta al 500 cerrada, plan de seguridad | Hecho (§7, §9, §10, §11) |
| Contraste con `origin/develop` y con las specs de CM-311 y CM-312 | Hecho (§2, §18): los archivos de CM-204 no existen y se crean; lo prestado existe en las specs aprobadas |
| Integración con la ola | Hecha (§17) |
| Línea base verde | Tarjeta T0 (la de CM-311 tras fusionarla) |
| Lista de verificación | Hecha (§20) |

**Veredicto: LISTA PARA EJECUTAR** (con CM-311 fusionada como condición de entrada). Aprobada por Paula el 22-oct-2026.
