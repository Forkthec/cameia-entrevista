# CM-312 · Cliente de Gemini con Spring AI y tiempo de espera configurable — cameia-entrevista

| Campo | Valor |
|---|---|
| Jira | CM-312 (Subtarea de CM-211, HT-02, Sprint 3) |
| Repositorio | cameia-entrevista |
| Rama | `CM-312-cliente-gemini` |
| Base | `origin/develop` @ `bcc697d` (20 de octubre de 2026) |
| Estado | Borrador: pendiente de aprobación del Product Owner y de la revisión de Backend |
| Documentos hermanos | [plan.md](plan.md) · [tasks.md](tasks.md) |

## 1. Contexto y alcance

CM-211 (HT-02) habilita el acceso al proveedor LLM por ambiente y bloquea HU-4.1, HU-4.5 y HU-5.2. Su subtarea de Backend, CM-312, deja en este servicio una pieza de infraestructura que llama a Gemini a través de Spring AI y que devuelve el texto o falla con un error propio, con un tiempo de espera por variable de entorno (30 s al inicio).

CM-312 es la primera tarea que escribe código de negocio en Entrevista y la primera que necesita responder un error propio (el modelo no respondió a tiempo). Por eso **siembra la base del manejo de errores**: la lista de códigos, la raíz de excepciones y el manejador, con la forma de `cameia-cuentas`. CM-311 (motor base, HT-01) la extiende sin reemplazar ni renombrar nada. El reparto entre las dos tareas lo fija el aviso de coordinación de Backend del 17 de octubre de 2026, y esta spec lo respeta.

**Criterios de aceptación** (Jira y backlog v8.2):

1. Una llamada de prueba real devuelve contenido no vacío.
2. Si el modelo supera el tiempo de espera, la respuesta del servicio es `503` con `code` `LLM_UNAVAILABLE`.
3. Sin la llave, el servicio lo detecta al arrancar y la llave no aparece en el log. No hay llaves en el repositorio.

Si el spike HU-5.14 elige Gemini para transcribir, sirve la misma llave: por eso la variable conserva el nombre `GOOGLE_GENAI_API_KEY`, que no es propio del chat.

### 1.1 Fuera de alcance

| No se hace aquí | Quién lo hace |
|---|---|
| Endpoint o ruta nueva, incluida una ruta temporal de prueba | Nadie (D-02) |
| Reintentos en el cliente | HU-5.2 y HU-5.6 (D-03) |
| `VALIDATION_FAILED`, la lista `errors`, el cuerpo ilegible, la ruta inexistente, el método o el tipo de contenido no admitido | CM-311 |
| `IDENTITY_REQUIRED` y la lectura de `X-User-Id` | CM-311 |
| `DATABASE_UNAVAILABLE`, los códigos de transición de estado y de conflicto por edición simultánea | CM-311 |
| Filtro del `requestId` en el `MDC` | CM-311 (P2-05 de CM-283) |
| Dependencias nuevas (Flyway, validación, Testcontainers, ArchUnit, mensajería) | CM-311 |
| Perfiles de configuración nuevos, carpetas, Postman, renombrar `GET /health` o la configuración de OpenAPI | CM-311 |
| Router con selección por caso de uso o costo y respaldo a Kimi | La tarea que integre Kimi (D-01) |
| Guardia de cuota, publicación del consumo de tokens y trazas en Langfuse | Ver hallazgos H-1 a H-3 |

## 2. Componentes

| Capa y paquete | Pieza | Qué hace |
|---|---|---|
| `domain.exception` | `ErrorCode` | Enumeración con **solo** `INTERNAL_ERROR`, `LLM_UNAVAILABLE` y `LLM_LIMIT_REACHED`, cada uno con Javadoc |
| `domain.exception` | `BusinessException` | Clase abstracta que guarda el `ErrorCode` y el mensaje, igual que la de Cuentas |
| `domain.exception` | `LlmUnavailableException`, `LlmLimitReachedException` | Una excepción por código propio del cliente. Conservan la causa técnica solo para el log |
| `domain.port` | `LanguageModelPort` | Interfaz que el resto del servicio usa para pedir texto al modelo, sin saber que detrás está Gemini |
| `domain.model` | `LanguageModelPrompt` | Objeto de valor (`record`) con el texto que se envía y el tope de tokens de salida |
| `infrastructure.ia` | `GeminiLanguageModelAdapter` | Implementa el puerto con el `ChatModel` de Spring AI. Valida la respuesta y traduce cada fallo a su excepción |
| `infrastructure.config.ia` | `LanguageModelProperties` | Configuración tipada y validada al arrancar (`@ConfigurationProperties` y `@Validated`) |
| `infrastructure.config.ia` | `LanguageModelConfiguration` | Crea el cliente de Gemini con la llave, el tiempo de espera y los reintentos apagados |
| `presentation.advice` | `BusinessExceptionHandler` | Tres manejadores (`LlmUnavailableException`, `LlmLimitReachedException` y `Exception`), el método común que arma la respuesta y el que resuelve el `requestId` |

Hoy ningún caso de uso llama al puerto: lo usarán HU-4.5 (planificar las preguntas) y HU-5.2 (responder).

## 3. Decisiones

Decisiones humanas tomadas el 20 de octubre de 2026 por el Product Owner, a partir del aviso de coordinación de Backend del 17 de octubre de 2026. Cada una indica por qué y qué se descartó.

| # | Decisión | Por qué | Alternativa descartada |
|---|---|---|---|
| D-01 | Puerto en `domain.port` y un solo adaptador, `GeminiLanguageModelAdapter`, en `infrastructure.ia`. El **Router LLM** del diagrama de componentes (Strategy que elige proveedor y modelo por caso de uso y costo, con respaldo entre Gemini y Kimi) queda **solo documentado**, en el `package-info.java` de `infrastructure.ia` y en esta spec, y se completa cuando entre Kimi. Ese día el puerto no cambia: el Router lo implementa y el adaptador de Gemini pasa a ser una de sus estrategias | El estándar (sección 4) exige dos variantes reales para un Strategy, y hoy hay una sola. El aviso de coordinación limita lo que CM-312 siembra | Clases de esqueleto del Router con una sola estrategia |
| D-02 | **Sin endpoint.** La llamada real se hace con una prueba manual que solo corre con `LLM_LIVE_TEST=true` y nunca en la integración continua. Gasta muy pocos tokens: un prompt fijo y corto, un tope de 16 tokens de salida y el pensamiento del modelo al mínimo (PD-04). Las pruebas automáticas usan un servidor simulado y una llave falsa | No se agrega una ruta sin un caso de uso que la justifique, y una ruta de prueba con costo por llamada es un riesgo de abuso | Una ruta `POST /api/v1/llm/probe` apagada por bandera fuera de producción |
| D-03 | **Sin reintentos en el cliente:** un solo intento por llamada, con el tiempo de espera de `LLM_TIMEOUT`. Se apagan las dos capas de reintento que traen las librerías por defecto: las `HttpRetryOptions` del SDK de Google GenAI (`attempts = 1`) y el `RetryTemplate` de Spring AI (cero reintentos). El peor caso es un tiempo de espera, 30 s al inicio | Los reintentos pertenecen a HU-5.2 y HU-5.6; si el cliente también reintenta, los intentos se multiplican y el usuario espera hasta tres veces más | 3 intentos con espera creciente, incluido el tiempo agotado (peor caso ≈ 93 s) |
| D-04 | La llave se **valida siempre** (`@NotBlank`), en todos los perfiles. `application-local.properties` trae una llave falsa por defecto y las pruebas fijan otra, así que cualquiera puede correr el servicio sin secretos reales. En `production` no hay valor por defecto: sin el secreto, el servicio no arranca | Cumple el criterio 3 y la recomendación de Backend (que solo falle en producción) sin código que dependa del perfil ni perfiles nuevos | Validación condicionada al perfil activo |
| D-05 | Clasificación de los fallos del modelo: el tiempo agotado, el error del proveedor (5xx, E/S cortada, 4xx distinto de 429) y la respuesta vacía o inutilizable dan `LLM_UNAVAILABLE`; la cuota agotada del proveedor (429 `RESOURCE_EXHAUSTED`) da `LLM_LIMIT_REACHED`. Los dos responden `503`, porque quien usa el sistema no superó ningún límite propio | Frontend puede mostrar un mensaje distinto, y ningún fallo previsible termina en `INTERNAL_ERROR`. `LIMIT_REACHED` es una causa del vocabulario cerrado del estándar | Un solo código para todo; `429` para la cuota del proveedor |
| D-06 | El modelo se elige con la variable `LLM_MODEL`, obligatoria y sin valor por defecto en el código. La temperatura y el nivel de pensamiento se fijan en `application.properties` con los valores de PD-02 y PD-04 | El estándar prohíbe convertir en constante un valor pendiente de decisión. El modelo cambia por ambiente y por costo, y la temperatura no | Un id de modelo escrito en el código |
| D-07 | En el log solo van el `requestId` (cuando hay petición), la duración en milisegundos y el resultado (`ok` o el `ErrorCode`). Nunca la llave, el texto enviado ni el texto recibido | SEG-04, sección 6 del `CLAUDE.md` y Ley 1581 | Registrar el prompt truncado |
| D-08 | La cobertura de lo nuevo es el mínimo del equipo: 70 % de líneas y de ramas, medido con JaCoCo, con el número real por clase tocada y la razón de cada línea o rama sin cubrir | Lo fija Backend para esta revisión | El 90 % de la sección 9 del estándar |

## 4. Requisitos (EARS)

### Configuración y arranque

- **REQ-01.** El servicio deberá leer su configuración del modelo con el prefijo `cameia.llm`, enlazada a `LanguageModelProperties` y validada al arrancar, según la tabla de la sección 6.
- **REQ-02.** Si `cameia.llm.api-key` falta, está vacía o tiene solo espacios, el servicio no deberá arrancar, y el mensaje de error deberá nombrar la propiedad `cameia.llm.api-key` y la variable `GOOGLE_GENAI_API_KEY`.
- **REQ-03.** Si `cameia.llm.model` falta, está vacía o tiene solo espacios, el servicio no deberá arrancar, y el mensaje deberá nombrar la propiedad `cameia.llm.model` y la variable `LLM_MODEL`.
- **REQ-04.** Si `cameia.llm.timeout` no es una duración, o no es mayor que cero, el servicio no deberá arrancar.
- **REQ-05.** El valor de la llave no deberá aparecer en ninguna línea del log ni en ningún mensaje de error, ni siquiera cuando el arranque falle por otra propiedad. La representación de texto (`toString`) de `LanguageModelProperties` deberá mostrar la llave como `****`.
- **REQ-06.** El cliente de Gemini deberá usar la llave y el tiempo de espera de `LanguageModelProperties`, y hará un solo intento por llamada (D-03).

### Llamada al modelo

- **REQ-07.** Cuando un caso de uso pida texto con un `LanguageModelPrompt`, el adaptador deberá enviar el texto al modelo de `cameia.llm.model`, con la temperatura y el nivel de pensamiento configurados y con `maxOutputTokens` igual al tope del prompt, y deberá devolver el texto de la primera respuesta.
- **REQ-08.** `LanguageModelPrompt` deberá rechazar al construirse un texto nulo, vacío o con solo espacios, y un tope de tokens menor que 1, con `IllegalArgumentException` y un mensaje en español. Es un error de programación del que llama, no de la persona.
- **REQ-09.** Si el modelo no responde dentro de `cameia.llm.timeout`, el adaptador deberá lanzar `LlmUnavailableException` con la causa técnica, sin reintentar.
- **REQ-10.** Si el proveedor responde 429, el adaptador deberá lanzar `LlmLimitReachedException`, sin reintentar.
- **REQ-11.** Si el proveedor responde con otro estado de error (4xx distinto de 429 o 5xx) o la conexión se corta, el adaptador deberá lanzar `LlmUnavailableException`, sin reintentar.
- **REQ-12.** Si la respuesta no trae candidatos, o el texto es nulo, vacío o con solo espacios, el adaptador deberá lanzar `LlmUnavailableException`: una respuesta vacía nunca es un valor válido.
- **REQ-13.** Por cada llamada, el adaptador deberá registrar en `INFO` una sola línea de medición, con la duración y el resultado (`ok`, `LLM_UNAVAILABLE` o `LLM_LIMIT_REACHED`), según D-07. Es una medición de la llamada y no el registro del error, que lo hace una sola vez el manejador, con traza (REQ-17).
- **REQ-14.** El adaptador no deberá guardar estado entre llamadas.

### Respuesta de error

- **REQ-15.** Cuando una `LlmUnavailableException` o una `LlmLimitReachedException` llegue al manejador, la respuesta deberá ser `503` con `Content-Type: application/problem+json; charset=UTF-8` y los miembros `type`, `title`, `status`, `detail`, `instance`, `code` y `requestId`, según la sección 5.
- **REQ-16.** Cuando cualquier otra excepción llegue al manejador, la respuesta deberá ser `500` con `code` `INTERNAL_ERROR` y el mensaje «Ocurrió un error. Inténtalo de nuevo.». Ninguna respuesta deberá llevar traza, nombre de clase ni el mensaje de una excepción de librería.
- **REQ-17.** El manejador deberá registrar cada error una sola vez, en `ERROR` con traza (todos los códigos de esta spec son 5xx), con `code` y `requestId` y sin textos del modelo.
- **REQ-18.** El `requestId` deberá ser el encabezado `X-Request-Id` recibido cuando cumpla `^[A-Za-z0-9._-]{1,64}$`; si falta o no lo cumple, deberá ser un UUID v4 nuevo. En los dos casos se deberá devolver en el encabezado `X-Request-Id` de la respuesta de error.

### Documentación y repositorio

- **REQ-19.** `docs/errores.md` deberá listar los tres códigos en la tabla «Códigos que el servicio emite», con las columnas de Cuentas: `Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba`. Una prueba deberá fallar si un miembro de `ErrorCode` no aparece como fila de esa tabla.
- **REQ-20.** `.env.example` deberá documentar `GOOGLE_GENAI_API_KEY`, `LLM_MODEL` y `LLM_TIMEOUT`, sin valores reales. El repositorio no deberá contener ninguna cadena con la forma de una llave de Google (`AIza` seguido de 35 caracteres `[0-9A-Za-z_-]`).
- **REQ-21.** El despliegue de staging deberá definir `LLM_MODEL`; sin ella, el servicio no arranca (REQ-03).

## 5. Errores que se publican

| Código | HTTP | `title` | `detail` (mensaje) | Cuándo | Prueba |
|---|---|---|---|---|---|
| `LLM_UNAVAILABLE` | 503 | Servicio no disponible | Ocurrió un error. Inténtalo de nuevo. | `LlmUnavailableException`: tiempo agotado, error del proveedor distinto de 429, conexión cortada, o respuesta sin candidatos o con texto vacío | `GeminiLanguageModelAdapterTest`, `BusinessExceptionHandlerTest` |
| `LLM_LIMIT_REACHED` | 503 | Servicio no disponible | Ocurrió un error. Inténtalo de nuevo. | `LlmLimitReachedException`: el proveedor respondió 429 (cuota o límite de uso del proveedor agotado) | `GeminiLanguageModelAdapterTest`, `BusinessExceptionHandlerTest` |
| `INTERNAL_ERROR` | 500 | Error interno | Ocurrió un error. Inténtalo de nuevo. | Cualquier fallo imprevisto | `BusinessExceptionHandlerTest` |

Los dos códigos del modelo usan el mismo mensaje, el que fija HU-4.5 para ese caso. Frontend distingue la causa por `code`. Hoy ningún endpoint los emite: la columna «Endpoints» de `docs/errores.md` dice «ninguno todavía (los emitirá el caso de uso que use el modelo)», y cada historia que llame al puerto agrega su ruta.

Ejemplo:

```json
{
  "type": "about:blank",
  "title": "Servicio no disponible",
  "status": 503,
  "detail": "Ocurrió un error. Inténtalo de nuevo.",
  "instance": "/api/v1/<ruta del caso de uso que llamó al modelo>",
  "code": "LLM_UNAVAILABLE",
  "requestId": "3c14c237-9fb8-4c25-86c6-cd81501d44f2"
}
```

## 6. Configuración

| Propiedad | Variable | Obligatoria | Valor por defecto | Regla | Si no cumple |
|---|---|---|---|---|---|
| `cameia.llm.api-key` | `GOOGLE_GENAI_API_KEY` | Sí | Vacío en la base; llave falsa en `local` y en las pruebas (D-04) | No vacía ni en blanco | No arranca (REQ-02) |
| `cameia.llm.model` | `LLM_MODEL` | Sí | Ninguno | No vacío ni en blanco; el id exacto de PD-01 | No arranca (REQ-03) |
| `cameia.llm.timeout` | `LLM_TIMEOUT` | No | `30s` | Duración de Spring mayor que cero (`30s`, `500ms`, `PT30S`) | No arranca (REQ-04) |
| `cameia.llm.temperature` | — | Sí | Valor de PD-02, fijo en `application.properties` | Entre `0.0` y `2.0` | No arranca |
| `cameia.llm.thinking-level` | — | Sí | Valor de PD-04, fijo en `application.properties` | Uno de los niveles que acepta `GoogleGenAiThinkingLevel` | No arranca |

Se retira `spring.ai.google.genai.api-key` de `application.properties`: el cliente de Gemini lo crea `LanguageModelConfiguration` a partir de `cameia.llm`, y la autoconfiguración de Spring AI lo respeta (`@ConditionalOnMissingBean`).

**Límite de uso del proveedor:** se escribe aquí con PD-03 antes de aprobar la spec (solicitud 4.2 del aviso de coordinación).

## 7. Casos borde

Cada fila aplicable es una prueba con estos valores literales.

| Grupo | Caso | Valor | Resultado esperado |
|---|---|---|---|
| Presencia (llave) | Ausente, vacía, solo espacios, solo tabulador | sin propiedad, `""`, `"   "`, `"\t"` | No arranca y nombra `cameia.llm.api-key` |
| Presencia (llave) | Válida | `clave-falsa-de-prueba` | Arranca |
| Presencia (modelo) | Ausente, vacío, solo espacios | sin propiedad, `""`, `"  "` | No arranca y nombra `cameia.llm.model` |
| Números (timeout) | Cero, negativo, no es una duración, válido | `0s`, `-1s`, `abc`, `30s` | No arranca, no arranca, no arranca, arranca |
| Secreto en el log | Arranque fallido por timeout `abc` con llave `clave-secreta-de-prueba` | — | La salida capturada no contiene `clave-secreta-de-prueba` |
| Secreto en el log | `toString()` de las propiedades | llave `clave-secreta-de-prueba` | Contiene `****` y no la llave |
| Texto (prompt) | Nulo, vacío, solo espacios | `null`, `""`, `"  "` | `IllegalArgumentException` |
| Números (tope) | Cero, negativo, mínimo válido | `0`, `-1`, `1` | Excepción, excepción, se construye |
| Dependencias | Respuesta correcta | 200 con texto `OK` | Devuelve `OK` |
| Dependencias | Tiempo agotado | El servidor simulado no responde; `timeout=200ms` | `LlmUnavailableException`; el servidor recibió **una** petición |
| Dependencias | Cuota agotada | 429 `RESOURCE_EXHAUSTED` | `LlmLimitReachedException`; **una** petición |
| Dependencias | Error del proveedor | 500, 503 | `LlmUnavailableException`; **una** petición |
| Dependencias | Petición rechazada por el proveedor | 400 | `LlmUnavailableException` |
| Dependencias | Conexión cortada | El servidor cierra la conexión sin responder | `LlmUnavailableException` |
| Dependencias | Respuesta mal formada | 200 con cuerpo que no es JSON | `LlmUnavailableException` |
| Dependencias | Respuesta vacía | 200 sin candidatos; 200 con texto `""`; 200 con texto `"   "` | `LlmUnavailableException` |
| Petición | `X-Request-Id` válido | `abc-123` | `requestId` y encabezado de respuesta `abc-123` |
| Petición | `X-Request-Id` ausente | — | UUID v4 nuevo, igual en el cuerpo y en el encabezado |
| Petición | `X-Request-Id` largo | 65 caracteres `a` | UUID v4 nuevo |
| Petición | `X-Request-Id` con espacios o caracteres no admitidos | `abc 123`, `abc/123` | UUID v4 nuevo |
| Respuesta | Fallo imprevisto con mensaje de librería | `IllegalStateException("detalle interno")` | 500 `INTERNAL_ERROR`; el cuerpo no contiene `detalle interno` ni `IllegalStateException` |

**No aplican**, con su razón:

| Grupo | Por qué no aplica |
|---|---|
| Fechas | El cliente no calcula ni guarda fechas; la duración se mide con un reloj monotónico (`System.nanoTime`), así que no hace falta un reloj inyectable |
| Enumerados, duplicados, estado, propiedad, colecciones | No hay entrada de la persona, ni recurso, ni estado, ni dueño: no hay endpoint (D-02) |
| Concurrencia | El adaptador no tiene estado (REQ-14), y el cliente de Gemini es seguro entre hilos |
| Falla parcial | No hay un efecto anterior a la llamada que compensar; el caso de uso que lo use guarda la respuesta de la persona antes de llamar (estándar, sección 11) |
| Carga | No hay cuerpo de entrada |

## 8. Atributos de calidad

| Atributo | Métrica | Qué se demuestra | Prueba |
|---|---|---|---|
| Fiabilidad | FIA-01 | Si el modelo falla, el adaptador lanza su excepción y no deja nada a medias (no tiene estado) | Pruebas de fallo del adaptador |
| Seguridad | SEG-04 | Cero llaves, prompts o respuestas en el log y en los errores | Pruebas de arranque con salida capturada; revisión del adaptador |
| Seguridad | SEG-01 | Configuración validada al arrancar, secreto solo por variable de entorno, errores sin detalle técnico, log sin datos sensibles | Pruebas de arranque y del manejador; `git grep` de llaves |
| Mantenibilidad | MAN-02 | El adaptador prueba éxito, formato inválido, tiempo agotado y cuota agotada | `GeminiLanguageModelAdapterTest` |
| Interoperabilidad | IOP-02 | La integración con Gemini pasa por un adaptador detrás de un puerto de dominio | Revisión de dependencias del PR |
| Capacidad | REST-0002 | Tiempo de espera en la llamada; sin estado en memoria entre peticiones | Prueba de tiempo agotado |
| Adecuación de la IA | IA-01 | El puerto devuelve texto crudo, que cada caso de uso valida antes de cambiar un estado de la entrevista | Queda para HU-4.5 y HU-5.2 |

## 9. Hallazgos del diagrama de componentes C3

El diagrama C3 del microservicio de Entrevista es un modelo propuesto. Al revisarlo para esta tarea aparecieron estos puntos, cada uno con su destino:

| ID | Hallazgo | Destino |
|---|---|---|
| H-1 | La **Guardia de Cuota** recibe llamadas de los casos de uso de Sesión y de Evaluación, pero no tiene de dónde leer la cuota (la réplica del plan y la cuota que llega por la Cola 3 y guarda el Repository Entrevista), y nada le reporta el consumo de cada llamada. Al ser una política de dominio no debe llamar a infraestructura: el caso de uso debería cargar la cuota por un puerto, pasársela a la política y registrar el consumo después de la llamada | Spec de cuota (Product Owner y Backend) |
| H-2 | El **Router LLM llama al Publicador de Eventos**, lo que acopla dos piezas de infraestructura. El consumo de tokens lo debería publicar el caso de uso, con Outbox (estándar, sección 11, Eventos) | Spec del registro de consumo |
| H-3 | El **Router LLM envía trazas a Langfuse**. Mandar prompts y respuestas a una herramienta de trazas choca con SEG-04 y con la sección 6 del `CLAUDE.md`, aunque sea autohospedada | Decisión de Backend antes de integrarlo |
| H-4 | Los casos de uso llaman **directo al Router LLM**, que es infraestructura: viola la regla de dependencias | Se resuelve en esta spec con `LanguageModelPort` (D-01) |
| H-5 | El Router describe un **respaldo entre Gemini y Kimi** | La tarea que integre Kimi (D-01) |

## 10. Pendientes

| # | Pendiente | Responsable | Para cuándo | Bloquea |
|---|---|---|---|---|
| PD-01 | Id exacto en la API del modelo «Gemini 3.7 Flash», confirmado en Google AI Studio | Product Owner | Antes de aprobar la spec | El valor de `LLM_MODEL` en `.env.example` y en el despliegue de staging (REQ-21) |
| PD-02 | Temperatura del modelo | Product Owner | Antes de aprobar la spec | `cameia.llm.temperature` |
| PD-03 | Límite de uso del plan gratuito del proveedor para ese modelo (peticiones por minuto y por día, tokens por minuto) | Product Owner | Antes de aprobar la spec | La sección 6 |
| PD-04 | Nivel de pensamiento por defecto. Se recomienda el más bajo que el modelo acepte: con un tope de salida bajo, el pensamiento puede consumir los tokens y dejar la respuesta vacía | Product Owner | Antes de aprobar la spec | `cameia.llm.thinking-level` y la prueba real con pocos tokens |
| PD-05 | Nueva fecha de fusión: el aviso de coordinación pedía el 19 de octubre de 2026 | Product Owner, en el hilo del aviso | 20 de octubre de 2026 | El inicio del código de CM-311 |
| PD-06 | Confirmar con la primera llamada real que la versión 1 del secreto `google-genai-api-key-staging` es una llave real, sin leerla ni registrarla | Product Owner con DevOps | Con la prueba real | El criterio 1 en staging |
