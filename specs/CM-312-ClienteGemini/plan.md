# Plan · CM-312 · cameia-entrevista

Spec: [spec.md](spec.md). Tareas: [tasks.md](tasks.md).

## 1. Enfoque

Hay dos piezas, cada una en su PR:

1. **Pieza A**: la bitácora y esta carpeta de spec. Se fusiona cuando la spec está aprobada.
2. **Pieza B**: el código, en el orden de lectura que sugiere el aviso de coordinación: configuración, lista de códigos y manejador, adaptador, pruebas y documentación.

El código no empieza sin la aprobación de la spec. Los pendientes PD-01 a PD-05 se resolvieron el 20 de octubre de 2026.

Spring AI 2.0.1 no expone una propiedad para el tiempo de espera ni para apagar los reintentos. Lo que sí hace su autoconfiguración de Google GenAI (`GoogleGenAiChatAutoConfiguration`) es:

- respetar un `com.google.genai.Client` propio (`@ConditionalOnMissingBean`);
- tomar el `RetryTemplate` del contexto cuando existe.

Por eso el plan no reemplaza el `ChatModel`. Solo aporta dos beans:

- el `Client`, construido con `HttpOptions`: `timeout` en milisegundos, `retryOptions` con `attempts = 1` y `baseUrl` solo en las pruebas;
- un `RetryTemplate` sin reintentos.

El adaptador recibe el `ChatModel` que arma Spring AI y le pasa las opciones de cada llamada (`GoogleGenAiChatOptions`).

## 2. Piezas y títulos de PR

| Pieza | Contenido | Título del PR |
|---|---|---|
| A | `docs/bitacora-ia/02_cliente-gemini-cm-312_prompt.md`, `docs/bitacora-ia/README.md`, `specs/CM-312-ClienteGemini/` | `CM-312 \| docs(specs): especificación del cliente de Gemini [IA-ASISTIDO]` |
| B | Código, pruebas, configuración y documentos de la sección 3 | `CM-312 \| feat(ia): cliente de Gemini con tiempo de espera configurable [IA-ASISTIDO]` |

Ninguno pasa de 1000 líneas entre agregadas y eliminadas. Si B pasa, la configuración de despliegue y los documentos van en un PR B2 con el título `CM-312 | docs(errores): catálogo y configuración del cliente de Gemini [IA-ASISTIDO]`. Los PR no los fusiona la IA; los revisa Backend.

## 3. Archivos de la pieza B

| Archivo | Acción | Estimado |
|---|---|---|
| `src/main/java/tech/cameia/entrevista/domain/exception/ErrorCode.java` | Crear | 30 |
| `.../domain/exception/BusinessException.java` | Crear | 35 |
| `.../domain/exception/LlmUnavailableException.java`, `LlmLimitReachedException.java` | Crear | 50 |
| `.../domain/exception/package-info.java`, `domain/port/package-info.java`, `domain/model/package-info.java` | Crear | 30 |
| `.../domain/port/LanguageModelPort.java` | Crear | 25 |
| `.../domain/model/LanguageModelPrompt.java` | Crear | 40 |
| `.../infrastructure/ia/GeminiLanguageModelAdapter.java` | Crear | 130 |
| `.../infrastructure/ia/package-info.java` (con el Router documentado, D-01) | Crear | 30 |
| `.../infrastructure/config/ia/LanguageModelProperties.java` | Crear | 70 |
| `.../infrastructure/config/ia/LanguageModelConfiguration.java` y `package-info.java` | Crear | 80 |
| `.../presentation/advice/BusinessExceptionHandler.java` y `package-info.java` | Crear | 150 |
| `src/main/resources/application.properties` | Modificar | 10 |
| `src/main/resources/application-local.properties` | Modificar | 3 |
| Pruebas (sección 7) | Crear | 400 |
| `src/test/java/tech/cameia/entrevista/EntrevistaApplicationTests.java` | Modificar: modelo falso en las propiedades de la prueba | 3 |
| `.env.example`, `docker-compose.yml` | Modificar | 25 |
| `.github/workflows/despliegue-continuo.yml` | Modificar: `LLM_MODEL` en `--set-env-vars` | 3 |
| `docs/errores.md`, `CLAUDE.md` | Modificar | 50 |
| **Total** | | **≈ 1165** |

El estimado pasa de 1000, así que se prevé el PR B2 (configuración de despliegue y documentos, ≈ 80). El número real se mide con `git diff --shortstat origin/develop` antes de abrir cada PR.

## 4. Qué se reutiliza

- **De `cameia-cuentas` (`origin/develop`)** se copia la forma de cuatro piezas, ajustada al mínimo de esta spec:
  - `ErrorCode` y su Javadoc de cabecera;
  - `BusinessException`, igual;
  - `BusinessExceptionHandler`: `problema(...)`, `resolverRequestId()`, `nuevoRequestId(...)`, la expresión `^[A-Za-z0-9._-]{1,64}$` y el `Content-Type` `application/problem+json;charset=UTF-8`, sin los manejadores de validación, de protocolo ni de base de datos, que trae CM-311;
  - `ErrorCodeDocumentationTest`.
- `DependencyUnavailableException` de Cuentas como modelo de excepción que conserva la causa técnica y tiene un mensaje fijo.
- El `ChatModel` y las opciones de Spring AI que ya trae el `pom.xml`.
- El patrón de las pruebas de configuración existentes (`ApiDocsFlagTest`, `ServerPortResolutionTest`) para las pruebas de arranque.
- Sin dependencias nuevas. El servidor simulado de las pruebas es `com.sun.net.httpserver.HttpServer`, que viene en el JDK 21.

## 5. Decisiones técnicas y alternativas descartadas

| Decisión | Por qué | Alternativa descartada |
|---|---|---|
| `Client` y `RetryTemplate` propios, con el `ChatModel` de la autoconfiguración | Menos código y la autoconfiguración sigue a cargo de las opciones y de la observabilidad | Construir el `GoogleGenAiChatModel` a mano |
| La llave pasa a `cameia.llm.api-key` y se retira `spring.ai.google.genai.api-key` | Hace falta `@Validated` para que el servicio no arranque sin ella, y una sola propiedad evita dos fuentes de la llave | Validar la propiedad de Spring AI con un `@PostConstruct` |
| Servidor simulado del JDK | Sin dependencias nuevas (aviso de coordinación, 6.1) | WireMock u OkHttp `MockWebServer` |
| El tiempo agotado se provoca con un servidor que retiene la respuesta (`CountDownLatch`) y un `timeout` de 200 ms | La prueba no usa `Thread.sleep` y falla si el adaptador no corta | Dormir el hilo del servidor |
| El adaptador recorre la cadena de causas para clasificar el fallo | Spring AI o el `RetryTemplate` pueden envolver la `ApiException` o la `GenAiIOException` del SDK | Capturar solo el tipo de primer nivel |
| La prueba real se llama `GeminiLanguageModelLiveTest` y lleva `@EnabledIfEnvironmentVariable(named = "LLM_LIVE_TEST", matches = "true")` | El repositorio todavía no tiene Failsafe: una clase `*IT` no correría nunca. Con la condición, CI la omite sin tocar el `pom.xml` | `@Tag` y `excludedGroups` en Surefire (cambia el `pom.xml`); sufijo `*IT` |
| `EntrevistaApplicationTests` recibe `cameia.llm.model` en `@SpringBootTest(properties = ...)` | Sin modelo, el contexto no arranca; la llave falsa ya llega por el perfil `local`, que es el perfil por defecto | Un `application.properties` de pruebas, que ocultaría el principal |

## 6. Riesgos

| Riesgo | Cómo se controla |
|---|---|
| El SDK de Google GenAI reintenta por su cuenta | `HttpRetryOptions` con `attempts = 1`, y la prueba de tiempo agotado cuenta exactamente una petición en el servidor simulado |
| Spring AI usa su `RetryTemplate` por defecto, que reintenta varias veces | Bean `RetryTemplate` sin reintentos y las pruebas de 429 y 503 cuentan una petición. Si la autoconfiguración no toma el bean, se detiene la tarea y se reporta |
| El pensamiento del modelo consume el tope de 16 tokens y la respuesta llega vacía | PD-04: nivel `low`. La prueba real lo confirma; si da vacío, se reporta antes de subir el tope o de bajar el nivel |
| Gemini acepta `baseUrl` solo con cierto formato de ruta | La prueba de éxito con el servidor simulado lo comprueba primero (tarjeta T-06); si no funciona, se detiene y se reporta |
| La llave aparece en el mensaje de un arranque fallido | Prueba con salida capturada (`OutputCaptureExtension`) y `toString` enmascarado |
| Staging deja de arrancar por falta de `LLM_MODEL` | El workflow se actualiza en el mismo PR (o en B2) y DevOps revisa el cambio |
| `@Validated` depende de `hibernate-validator`, que hoy llega como dependencia transitiva y no declarada (`spring-boot-validation`) | `LanguageModelPropertiesTest` falla si desaparece. Declararla le toca a CM-311 (aviso de coordinación, 6.1) |
| El PR pasa de 1000 líneas | Medición antes de abrirlo; si pasa, se prevé el PR B2 |

## 7. Matriz de pruebas

| Clase de prueba | Tipo | Cubre |
|---|---|---|
| `LanguageModelPromptTest` | Dominio, sin Spring | REQ-08 |
| `ErrorCodeDocumentationTest` | Documento | REQ-19 |
| `LanguageModelPropertiesTest` | `ApplicationContextRunner` con `OutputCaptureExtension` | REQ-01 a REQ-05 |
| `GeminiLanguageModelAdapterTest` | Adaptador con el servidor simulado del JDK | REQ-06, REQ-07, REQ-09 a REQ-14 |
| `BusinessExceptionHandlerTest` | MockMvc con un controlador que solo existe en la prueba | REQ-15 a REQ-18 |
| `GeminiLanguageModelLiveTest` | Manual con la llave real (`LLM_LIVE_TEST=true`) | Criterio 1 |
| `EntrevistaApplicationTests` | Arranque del contexto | Que la configuración nueva arranca con valores válidos |

Las pruebas nuevas se nombran `metodo_shouldResultado_whenCondicion`, con `@DisplayName` en español.

## 8. Verificación

1. `./mvnw.cmd clean verify` en verde, con la salida real en el PR.
2. Informe de JaCoCo por clase tocada: 70 % o más de líneas y de ramas (D-08), con la razón de cada línea sin cubrir.
3. `git grep -nE "AIza[0-9A-Za-z_-]{35}"` sin resultados.
4. Prueba real: `LLM_LIVE_TEST=true`, con `GOOGLE_GENAI_API_KEY` y `LLM_MODEL` definidos, y `./mvnw.cmd test -Dtest=GeminiLanguageModelLiveTest`. Devuelve contenido no vacío. El resultado (`PASA` o `BLOQUEADO`, con la razón) va al PR, sin la llave ni el texto recibido.
5. Antes de abrir cada PR: `python -I .github/scripts/pr_policy.py <archivo.json>`, con un JSON que lleve `title`, `head`, `base`, `body`, `draft` y `same_repository`. Debe devolver `PASA_ESTRUCTURA`.
