# CM-312 · Cliente de Gemini con Spring AI y tiempo de espera configurable — cameia-entrevista

| Campo | Valor |
|---|---|
| Jira | CM-312 (Subtarea de CM-211, HT-02, Sprint 3) |
| Repositorio | cameia-entrevista |
| Rama | `CM-312-cliente-gemini` |
| Base | `origin/develop` @ `bcc697d` (20 de octubre de 2026) |
| Estado | Aprobada por el Product Owner el 21 de octubre de 2026; pendiente de la revisión de Backend en el PR |
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
| `domain.model` | `LanguageModelResponse` | Objeto de valor (`record`) con el texto de la respuesta, los tokens de entrada y de salida y el modelo que respondió (D-09) |
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
| D-02 | **Sin endpoint.** La llamada real se hace con una prueba manual que solo corre con `LLM_LIVE_TEST=true` y nunca en la integración continua. Gasta muy pocos tokens: un prompt fijo y corto, un tope de 64 tokens de salida (incluye el pensamiento; con 16 la respuesta llegaba vacía) y el nivel de pensamiento más bajo que admite el modelo (PD-04). Las pruebas automáticas usan un servidor simulado y una llave falsa | No se agrega una ruta sin un caso de uso que la justifique, y una ruta de prueba con costo por llamada es un riesgo de abuso | Una ruta `POST /api/v1/llm/probe` apagada por bandera fuera de producción |
| D-03 | **Sin reintentos en el cliente:** un solo intento por llamada, con el tiempo de espera de `LLM_TIMEOUT`. Se apagan las dos capas de reintento que traen las librerías por defecto: las `HttpRetryOptions` del SDK de Google GenAI (`attempts = 1`) y el `RetryTemplate` de Spring AI (cero reintentos). El peor caso es un tiempo de espera, 30 s al inicio | Los reintentos pertenecen a HU-5.2 y HU-5.6; si el cliente también reintenta, los intentos se multiplican y el usuario espera hasta tres veces más | 3 intentos con espera creciente, incluido el tiempo agotado (peor caso ≈ 93 s) |
| D-04 | La llave se **valida siempre** (`@NotBlank`), en todos los perfiles. `application-local.properties` trae una llave falsa por defecto y las pruebas fijan otra, así que cualquiera puede correr el servicio sin secretos reales. En `production` no hay valor por defecto: sin el secreto, el servicio no arranca | Cumple el criterio 3 y la recomendación de Backend (que solo falle en producción) sin código que dependa del perfil ni perfiles nuevos | Validación condicionada al perfil activo |
| D-05 | Clasificación de los fallos del modelo: el tiempo agotado, el error del proveedor (5xx, E/S cortada, 4xx distinto de 429) y la respuesta vacía o inutilizable dan `LLM_UNAVAILABLE`; la cuota agotada del proveedor (429 `RESOURCE_EXHAUSTED`) da `LLM_LIMIT_REACHED`. Los dos responden `503`, porque quien usa el sistema no superó ningún límite propio | Frontend puede mostrar un mensaje distinto, y ningún fallo previsible termina en `INTERNAL_ERROR`. `LIMIT_REACHED` es una causa del vocabulario cerrado del estándar | Un solo código para todo; `429` para la cuota del proveedor |
| D-06 | El modelo se elige con la variable `LLM_MODEL`, obligatoria y sin valor por defecto en el código. El valor de staging es `gemini-3.8-flash` (PD-01). La temperatura (`1.0`, PD-02) y el nivel de pensamiento (`low`, PD-04) se fijan en `application.properties` | El estándar prohíbe convertir en constante un valor pendiente de decisión. El modelo cambia por ambiente y por costo, y la temperatura no | Un id de modelo escrito en el código |
| D-07 | En el log solo van el `requestId` (cuando hay petición), la duración en milisegundos y el resultado (`ok` o el `ErrorCode`). Cuando la respuesta llega sin texto, la causa técnica lleva además el motivo de corte (`finishReason`), el modelo que respondió y los tokens de entrada, de salida y de pensamiento, para diagnosticar sin ver el texto (agregado el 22 de octubre de 2026). Nunca la llave, el texto enviado ni el texto recibido | SEG-04, sección 6 del `CLAUDE.md` y Ley 1581 | Registrar el prompt truncado |
| D-08 | La cobertura de lo nuevo es el mínimo del equipo: 70 % de líneas y de ramas, medido con JaCoCo, con el número real por clase tocada y la razón de cada línea o rama sin cubrir | Lo fija Backend para esta revisión | El 90 % de la sección 9 del estándar |
| D-09 | El puerto devuelve un `LanguageModelResponse` con el texto, los tokens de entrada y de salida que informa el proveedor y el modelo que respondió, no solo el texto. CM-312 no publica ni guarda el consumo: solo lo deja disponible para quien llama. Decisión del Product Owner del 21 de octubre de 2026. Si el proveedor no informa los tokens, el valor es `0`, que es lo que entrega Spring AI en ese caso (propuesta de la IA, pendiente de la revisión de Backend) | El consumo lo publica el caso de uso (H-2) y lo usa la Guardia de Cuota (H-1); si el puerto devolviera solo el texto, HU-4.5 y HU-5.2 tendrían que cambiar su firma | Devolver solo `String` |

## 4. Requisitos (EARS)

### Configuración y arranque

- **REQ-01.** El servicio deberá leer su configuración del modelo con el prefijo `cameia.llm`, enlazada a `LanguageModelProperties` y validada al arrancar, según la tabla de la sección 6.
- **REQ-02.** Si `cameia.llm.api-key` falta, está vacía o tiene solo espacios, el servicio no deberá arrancar, y el mensaje de error deberá nombrar la propiedad `cameia.llm.api-key` y la variable `GOOGLE_GENAI_API_KEY`.
- **REQ-03.** Si `cameia.llm.model` falta, está vacía o tiene solo espacios, el servicio no deberá arrancar, y el mensaje deberá nombrar la propiedad `cameia.llm.model` y la variable `LLM_MODEL`.
- **REQ-04.** Si `cameia.llm.timeout` no es una duración, o no es mayor que cero, el servicio no deberá arrancar.
- **REQ-05.** El valor de la llave no deberá aparecer en ninguna línea del log ni en ningún mensaje de error, ni siquiera cuando el arranque falle por otra propiedad. La representación de texto (`toString`) de `LanguageModelProperties` deberá mostrar la llave como `****`.
- **REQ-06.** El cliente de Gemini deberá usar la llave y el tiempo de espera de `LanguageModelProperties`, y hará un solo intento por llamada (D-03).

### Llamada al modelo

- **REQ-07.** Cuando un caso de uso pida texto con un `LanguageModelPrompt`, el adaptador deberá enviar el texto al modelo de `cameia.llm.model`, con la temperatura y el nivel de pensamiento configurados y con `maxOutputTokens` igual al tope del prompt, y deberá devolver un `LanguageModelResponse` con el texto de la primera respuesta, los tokens de entrada y de salida que informe el proveedor (`0` si no los informa) y el modelo que respondió (D-09).
- **REQ-08.** `LanguageModelPrompt` deberá rechazar al construirse un texto nulo, vacío o con solo espacios, y un tope de tokens menor que 1, con `IllegalArgumentException` y un mensaje en español. Es un error de programación del que llama, no de la persona. `LanguageModelResponse` deberá rechazar del mismo modo un texto o un modelo nulo, vacío o con solo espacios, y un número de tokens negativo. La representación de texto de los dos `record` no deberá incluir el texto, que puede llevar datos de la persona.
- **REQ-09.** Si el modelo no responde dentro de `cameia.llm.timeout`, el adaptador deberá lanzar `LlmUnavailableException` con la causa técnica, sin reintentar.
- **REQ-10.** Si el proveedor responde 429, el adaptador deberá lanzar `LlmLimitReachedException`, sin reintentar.
- **REQ-11.** Si el proveedor responde con otro estado de error (4xx distinto de 429 o 5xx) o la conexión se corta, el adaptador deberá lanzar `LlmUnavailableException`, sin reintentar.
- **REQ-12.** Si la respuesta no trae candidatos, o el texto es nulo, vacío o con solo espacios, el adaptador deberá lanzar `LlmUnavailableException`: una respuesta vacía nunca es un valor válido.
- **REQ-13.** Por cada llamada, el adaptador deberá registrar en `INFO` una sola línea de medición, con la duración y el resultado (`ok`, `LLM_UNAVAILABLE` o `LLM_LIMIT_REACHED`), según D-07. Es una medición de la llamada y no el registro del error, que lo hace una sola vez el manejador, con traza (REQ-17).
- **REQ-14.** El adaptador no deberá guardar estado entre llamadas.

### Respuesta de error

- **REQ-15.** Cuando una `LlmUnavailableException` o una `LlmLimitReachedException` llegue al manejador, la respuesta deberá ser `503` con `Content-Type: application/problem+json; charset=UTF-8` y los miembros `title`, `status`, `detail`, `instance`, `code` y `requestId`, según la sección 5. El miembro `type` se omite, como en Cuentas: Spring 7 no lo escribe cuando vale `about:blank`, y RFC 9457 (3.1.1) define que su ausencia equivale a ese valor.
- **REQ-16.** Cuando cualquier otra excepción llegue al manejador, la respuesta deberá ser `500` con `code` `INTERNAL_ERROR` y el mensaje «Ocurrió un error. Inténtalo de nuevo.». Ninguna respuesta deberá llevar traza, nombre de clase ni el mensaje de una excepción de librería. Los rechazos de protocolo de Spring MVC (las excepciones que implementan `ErrorResponse`, `HttpMessageNotReadableException` y `TypeMismatchException`) no son fallos del servicio: el manejador los relanza sin registrar y Spring los responde con su estado 4xx, como antes de CM-312. Darles código propio le toca a CM-311.
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
| `cameia.llm.model` | `LLM_MODEL` | Sí | Ninguno | No vacío ni en blanco; en staging, `gemini-3.8-flash` (PD-01) | No arranca (REQ-03) |
| `cameia.llm.timeout` | `LLM_TIMEOUT` | No | `30s` | Duración de Spring mayor que cero (`30s`, `500ms`, `PT30S`) | No arranca (REQ-04) |
| `cameia.llm.temperature` | — | Sí | `1.0`, fijo en `application.properties` (PD-02). Es el valor por defecto del modelo, y la familia Gemini 3 está pensada para trabajar con él | Entre `0.0` y `2.0` | No arranca |
| `cameia.llm.thinking-level` | — | Sí | `low`, fijo en `application.properties` (PD-04) | Uno de los niveles que acepta `GoogleGenAiThinkingLevel` | No arranca |

Se retira `spring.ai.google.genai.api-key` de `application.properties`: el cliente de Gemini lo crea `LanguageModelConfiguration` a partir de `cameia.llm`, y la autoconfiguración de Spring AI lo respeta (`@ConditionalOnMissingBean`).

**Límite de uso del proveedor:** 10 peticiones, valor provisional fijado por el Product Owner el 20 de octubre de 2026 hasta que un spike futuro dé el valor definitivo (PD-07). Hoy el único que llama al modelo es la prueba manual (D-02), que hace una petición por ejecución. CM-312 no cuenta ni corta peticiones: hacerlo es trabajo de la Guardia de Cuota (H-1). Si el proveedor corta antes, responde 429 y el servicio da `LLM_LIMIT_REACHED` (D-05).

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
| Texto (respuesta) | Texto o modelo nulo, vacío, solo espacios | `null`, `""`, `"  "` | `IllegalArgumentException` |
| Números (tokens de la respuesta) | Negativo, cero, positivo | `-1`, `0`, `5` | Excepción, se construye, se construye |
| Dependencias | Respuesta sin conteos de tokens | 200 con texto `OK`, `usageMetadata` vacío y `modelVersion` vacío | Devuelve `OK` con `0` tokens de entrada, `0` de salida y el modelo configurado |
| Dependencias | Respuesta sin datos de uso o sin versión del modelo | 200 con texto `OK` y sin `usageMetadata` o sin `modelVersion` | `LlmUnavailableException`: Spring AI 2.0.1 no acepta la respuesta sin esos dos campos |
| Dependencias | Respuesta correcta | 200 con texto `OK` | Devuelve `OK` |
| Dependencias | Tiempo agotado | El servidor simulado no responde; `timeout=200ms` | `LlmUnavailableException`; el servidor recibió **una** petición |
| Dependencias | Cuota agotada | 429 `RESOURCE_EXHAUSTED` | `LlmLimitReachedException`; **una** petición |
| Dependencias | Error del proveedor | 500, 503 | `LlmUnavailableException`; **una** petición |
| Dependencias | Petición rechazada por el proveedor | 400 | `LlmUnavailableException` |
| Dependencias | Conexión cortada | El servidor cierra la conexión sin responder | `LlmUnavailableException` |
| Dependencias | Respuesta mal formada | 200 con cuerpo que no es JSON | `LlmUnavailableException` |
| Dependencias | Respuesta vacía | 200 sin candidatos; 200 con texto `""`; 200 con texto `"   "` | `LlmUnavailableException` |
| Dependencias | Respuesta cortada por el tope | 200 con texto `""`, `finishReason` `MAX_TOKENS` y 16 tokens de pensamiento | `LlmUnavailableException`; la causa nombra `finishReason=MAX_TOKENS`, el modelo y los tokens, sin texto |
| Petición | `X-Request-Id` válido | `abc-123` | `requestId` y encabezado de respuesta `abc-123` |
| Petición | `X-Request-Id` ausente | — | UUID v4 nuevo, igual en el cuerpo y en el encabezado |
| Petición | `X-Request-Id` largo | 65 caracteres `a` | UUID v4 nuevo |
| Petición | `X-Request-Id` con espacios o caracteres no admitidos | `abc 123`, `abc/123` | UUID v4 nuevo |
| Respuesta | Rechazo de protocolo | `POST` a una ruta `GET`; cuerpo `{no es json`; parámetro `int` con `abc` | 405, 400, 400, sin línea de «Fallo no controlado» en el log |
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

El diagrama C3 del microservicio de Entrevista (`docs/29082026_1_Componentes(C3)-C3 - Microservicio de Entrevista.drawio.svg`) es un modelo propuesto. Al revisarlo para esta tarea aparecieron estos puntos. La columna «Cambio en el diagrama» dice si el diagrama debe corregirse y cómo; ninguno de estos cambios lo hace CM-312.

| ID | Hallazgo | Cambio en el diagrama | Destino |
|---|---|---|---|
| H-1 | La **Guardia de Cuota** solo recibe flechas (de Aplicación Sesión de Entrevista y de Aplicación Evaluación). Eso es correcto: es una política de dominio y no debe llamar a nadie; recibe la cuota y el consumo como datos. Lo que falta es de dónde los saca el caso de uso: la cuota del ciclo llega por la Cola 3 y la guarda el Repository Entrevista, y Aplicación Evaluación no tiene flecha hacia ese repositorio | **Sí.** Agregar la flecha Aplicación Evaluación → Repository Entrevista («lee la cuota del ciclo»). Las flechas hacia la Guardia de Cuota no cambian | Spec de cuota (Product Owner y Backend) |
| H-2 | El **Router LLM llama al Publicador de Eventos** para publicar el consumo de tokens. El Router no conoce al usuario, la sesión ni el caso de uso que el evento necesita, no maneja una transacción para escribir en Outbox y queda desacoplado de la Guardia de Cuota, que vive en el caso de uso (estándar, sección 11, Eventos) | **Sí.** Quitar la flecha Router LLM → Publicador de Eventos. Agregar Aplicación Sesión de Entrevista → Publicador de Eventos («consumo de tokens»), y sumar el consumo de tokens a la flecha Aplicación Evaluación → Publicador de Eventos, que hoy solo lleva la evaluación finalizada | Spec del registro de consumo |
| H-3 | El **Router LLM envía trazas a Langfuse**. La flecha está bien puesta: la observabilidad es infraestructura y el Router es el único punto por donde pasan todas las llamadas. Lo pendiente es el contenido: una traza de Langfuse guarda por defecto el prompt y la respuesta completos, lo que choca con SEG-04 («ni en trazas»), con la sección 6 del `CLAUDE.md` y con la Ley 1581. Opciones: solo metadatos (modelo, tokens, latencia, resultado y `requestId`); enmascarado; texto completo solo fuera de producción con datos sintéticos; texto completo en producción con consentimiento y borrado | **No.** La flecha se conserva | Decisión de Backend y del Product Owner antes de integrarlo |
| H-4 | Los casos de uso llaman al **Router LLM**, que es infraestructura. El diagrama muestra llamadas en tiempo de ejecución y no dependencias de código, igual que las flechas de los casos de uso hacia los Repository y hacia el Cliente Voz. La regla de dependencias (sección 2 del `CLAUDE.md`) solo se viola si el caso de uso importa la clase del Router; con `LanguageModelPort` la llamada llega al Router y el código del caso de uso depende solo del puerto | **No.** La regla se cumple en el código con `LanguageModelPort` (D-01) | Esta spec (D-01) |
| H-5 | El Router describe un **respaldo entre Gemini y Kimi** | **No** | La tarea que integre Kimi (D-01) |
| H-6 | La descripción del **Router LLM** dice «con tiempo límite, **reintentos** y respaldo entre Gemini y Kimi». Los reintentos pertenecen a los casos de uso (HU-5.2 y HU-5.6), y el cliente no reintenta (D-03) | **Sí.** Quitar «reintentos» de la descripción del Router LLM: «Selecciona proveedor y modelo según el caso de uso y el costo, con tiempo límite y respaldo entre Gemini y Kimi.» | Quien mantenga el diagrama |

## 10. Pendientes

| # | Pendiente | Responsable | Para cuándo | Bloquea |
|---|---|---|---|---|
| PD-01 | Id exacto en la API del modelo «Gemini 3.7 Flash», confirmado en Google AI Studio. **Resuelto el 20 de octubre de 2026:** `gemini-3.7-flash`. **Cambiado el 22 de octubre de 2026:** `gemini-3.8-flash`; la prueba real mostró en Google AI Studio que `gemini-3.7-flash` quedó obsoleto y Google redirige la petición a 3.8 | Product Owner | Antes de aprobar la spec | El valor de `LLM_MODEL` en `.env.example` y en el despliegue de staging (REQ-21) |
| PD-02 | Temperatura del modelo. **Resuelto el 20 de octubre de 2026:** `1.0`, el valor por defecto | Product Owner | Antes de aprobar la spec | `cameia.llm.temperature` |
| PD-03 | Límite de uso del plan gratuito del proveedor para ese modelo. **Resuelto el 20 de octubre de 2026:** 10 peticiones, provisional (sección 6 y PD-07) | Product Owner | Antes de aprobar la spec | La sección 6 |
| PD-04 | Nivel de pensamiento por defecto. Con un tope de salida bajo, el pensamiento puede consumir los tokens y dejar la respuesta vacía. **Resuelto el 20 de octubre de 2026:** `low`; la prueba real confirma que alcanza con 16 tokens. El 22 de octubre de 2026 se probó `minimal`, pero `gemini-3.8-flash` lo rechaza con 400 («Thinking level MINIMAL is not supported for this model»), así que queda `low`, el nivel más bajo que admite. Con un tope de 16 tokens el pensamiento se gastó todo el tope (`MAX_TOKENS`, 12 tokens de pensamiento y texto vacío), por eso la prueba real usa 64 | Product Owner | Antes de aprobar la spec | `cameia.llm.thinking-level` y la prueba real con pocos tokens |
| PD-05 | Nueva fecha de fusión: el aviso de coordinación pedía el 19 de octubre de 2026. **Resuelto el 20 de octubre de 2026:** se fusiona el mismo 20 de octubre; falta avisarlo en el hilo | Product Owner, en el hilo del aviso | 20 de octubre de 2026 | El inicio del código de CM-311 |
| PD-06 | Confirmar con la primera llamada real que la versión 1 del secreto `google-genai-api-key-staging` es una llave real, sin leerla ni registrarla | Product Owner con DevOps | Con la prueba real | El criterio 1 en staging |
| PD-07 | Spike del límite de uso: valor definitivo, ventana (por minuto o por día) y si se cuenta en peticiones o en tokens | Product Owner | Antes de que un caso de uso llame al modelo (HU-4.5 o HU-5.2) | El valor de la sección 6 y la Guardia de Cuota (H-1) |
| PD-08 | Tiempo de espera frente a la latencia real de `gemini-3.8-flash`. El 22 de octubre de 2026, con un prompt de 15 tokens, el modelo respondió 503 «high demand» varias veces y, cuando respondió, tardó 59 s y 75 s; con `LLM_TIMEOUT=30s` casi toda llamada termina en `LLM_UNAVAILABLE`. La prueba real pasó con `LLM_TIMEOUT=90s` | Product Owner | Antes de que un caso de uso llame al modelo (HU-4.5 o HU-5.2) | El valor por defecto de `LLM_TIMEOUT` y la elección del modelo |
