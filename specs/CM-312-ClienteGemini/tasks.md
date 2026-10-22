# Tareas · CM-312 · cameia-entrevista

Spec: [spec.md](spec.md). Plan: [plan.md](plan.md).

## Cómo se usan estas tarjetas

Se hace una tarjeta, se verifica con salida real y recién entonces se pasa a la siguiente. Cada tarjeta dura 30 minutos o menos. Si la realidad contradice la tarjeta, se detiene el trabajo y se reporta (cláusula «Detenerse si»). Cada tarjeta se marca `[x]` en el momento de terminarla.

**Reglas que valen para todas.**

- Rama `CM-312-cliente-gemini`, creada desde `origin/develop`.
- Commits con la forma `CM-312 | tipo(scope): resultado`, sin `[IA-ASISTIDO]` y con el trailer `Co-Authored-By` del modelo.
- Identificadores en inglés. Javadoc, comentarios, mensajes y `@DisplayName` en español.
- Pruebas nuevas con el nombre `metodo_shouldResultado_whenCondicion`.
- **Prohibido:**
  - agregar dependencias, perfiles o carpetas;
  - tocar `GET /health` o la configuración de OpenAPI;
  - agregar códigos fuera de los tres de la spec;
  - agregar un endpoint;
  - usar una llave real en las pruebas automáticas;
  - registrar la llave, el prompt o la respuesta del modelo;
  - hacer `push` a `develop` o `main`, o fusionar.
- Las rutas `.../` abrevian `src/main/java/tech/cameia/entrevista/` (o `src/test/java/...` en las pruebas).

## Pieza A · Bitácora y spec

### T-01 · Comprometer la bitácora y la spec

- [x] **Objetivo.** Dejar comprometidas en la rama la entrada 02 de la bitácora y `specs/CM-312-ClienteGemini/`.
- **Archivos.**
  - `docs/bitacora-ia/02_cliente-gemini-cm-312_prompt.md`
  - `docs/bitacora-ia/README.md`
  - `specs/CM-312-ClienteGemini/spec.md`, `plan.md` y `tasks.md`
- **Hacer.**
  1. Ejecutar `git status --short`: debe mostrar solo esos archivos, además de los no rastreados que no son de esta tarea, que no se agregan.
  2. Agregar los archivos con `git add` y comprobar con `git diff --cached --shortstat` que no pasan de 1000 líneas.
  3. Hacer el commit `CM-312 | docs(specs): especificación del cliente de Gemini`.
- **Terminado cuando** el commit existe y solo contiene esos archivos.

### T-02 · Resolver los pendientes y aprobar la spec

- [ ] **Objetivo.** Que la spec pase de «Borrador» a «Aprobada».
- **Hacer.**
  1. Escribir en la spec los valores de PD-01 a PD-04: id del modelo, temperatura, límite de uso del proveedor y nivel de pensamiento.
  2. Cambiar el campo «Estado» a «Aprobada por <rol> el <fecha>».
  3. Abrir el PR de la pieza A y responder PD-05 en el hilo del aviso de coordinación.
- **Detenerse si** Backend pide cambios de alcance en la revisión: se actualiza la spec antes de escribir código.
- **Terminado cuando** el PR de la pieza A está aprobado.

## Pieza B · Código

### T-03 · Lista de códigos y excepciones

- [x] **Cubre.** D-05 y la sección 5 de la spec.
- **Archivos.** Crear en `.../domain/exception/`: `ErrorCode.java`, `BusinessException.java`, `LlmUnavailableException.java`, `LlmLimitReachedException.java` y `package-info.java`.
- **Hacer.**
  1. Leer `git -C ../cameia-cuentas show origin/develop:src/main/java/tech/cameia/cuentas/domain/exception/ErrorCode.java`, `BusinessException.java` y `DependencyUnavailableException.java`.
  2. `ErrorCode`: solo `INTERNAL_ERROR`, `LLM_UNAVAILABLE` y `LLM_LIMIT_REACHED`, cada uno con su Javadoc.
  3. `BusinessException`: igual que la de Cuentas.
  4. Las dos excepciones: mensaje fijo «Ocurrió un error. Inténtalo de nuevo.» y constructor que recibe la causa técnica (`initCause`), como `DependencyUnavailableException`.
- **Verificación.** `./mvnw.cmd -q compile`.
- **Terminado cuando** compila y el commit es `CM-312 | feat(errores): códigos y excepciones del cliente del modelo`.

### T-04 · Manejador de errores

- [x] **Cubre.** REQ-15 a REQ-18.
- **Archivos.** Crear `.../presentation/advice/BusinessExceptionHandler.java` y `package-info.java`. Borrar `.../presentation/advice/.gitkeep`.
- **Hacer.**
  1. Leer `BusinessExceptionHandler.java` de Cuentas en `origin/develop`.
  2. Copiar solo `problema(...)`, `resolverRequestId()`, `nuevoRequestId(...)`, `rutaOriginal()`, `primerRegistro()` y las constantes `REQUEST_ID_HEADER`, `REQUEST_ID_ATTRIBUTE`, `LOGGED_ATTRIBUTE`, `PROBLEM_JSON_UTF8`, `REQUEST_ID_VALIDO` y `DETALLE_INTERNO`.
  3. Escribir tres manejadores:
     - `LlmUnavailableException` → 503;
     - `LlmLimitReachedException` → 503;
     - `Exception` → 500 `INTERNAL_ERROR`.
     Los tres usan el título de la sección 5 y registran en `ERROR` con traza, con `code` y `requestId`.
  4. No copiar los manejadores de validación, de protocolo, de base de datos ni de multipart.
- **Prueba.** `.../presentation/advice/BusinessExceptionHandlerTest.java` con `MockMvcBuilders.standaloneSetup(new FailingController()).setControllerAdvice(new BusinessExceptionHandler())`. `FailingController` es una clase anidada de la prueba con tres rutas que lanzan cada excepción. Casos, con los valores literales de la sección 7 de la spec:
  - `X-Request-Id` `abc-123`;
  - sin encabezado;
  - 65 caracteres `a`;
  - `abc 123`;
  - `IllegalStateException("detalle interno")`.
  Cada caso comprueba el estado, el `Content-Type`, `code`, `requestId` y el encabezado de respuesta.
- **Detenerse si** `ProblemDetail` no sale con `charset=UTF-8`: se reporta antes de forzarlo.
- **Terminado cuando** la prueba pasa y el commit es `CM-312 | feat(errores): manejador de errores del cliente del modelo`.

### T-05 · Puerto, prompt y propiedades

- [ ] **Cubre.** REQ-01 a REQ-05 y REQ-08.
- **Archivos.**
  - En `.../domain/port/`: `LanguageModelPort.java` y `package-info.java`.
  - En `.../domain/model/`: `LanguageModelPrompt.java` y `package-info.java`.
  - En `.../infrastructure/config/ia/`: `LanguageModelProperties.java` y `package-info.java`.
  - Borrar los `.gitkeep` de `domain/port` y `domain/model`.
- **Hacer.**
  1. `LanguageModelPort`: `String generate(LanguageModelPrompt prompt)`, con Javadoc que nombre las dos excepciones que puede lanzar.
  2. `LanguageModelPrompt`: `record(String text, int maxOutputTokens)` con las validaciones de REQ-08.
  3. `LanguageModelProperties`: `record` con `@ConfigurationProperties("cameia.llm")` y `@Validated`. Campos y reglas:
     - `apiKey`, `@NotBlank`;
     - `model`, `@NotBlank`;
     - `timeout`, `@NotNull`, mayor que cero;
     - `temperature`, `@NotNull`, entre `0.0` y `2.0`;
     - `thinkingLevel`, `@NotNull`.
     El `toString` enmascara `apiKey` con `****`. Los mensajes de validación nombran la variable de entorno.
- **Prueba.**
  - `LanguageModelPromptTest`: los casos de prompt y de tope de la sección 7.
  - `LanguageModelPropertiesTest`: con `ApplicationContextRunner`, una configuración mínima que habilita las propiedades y `OutputCaptureExtension`, los casos de llave, modelo, timeout, secreto en el log y `toString` de la sección 7.
- **Detenerse si** `jakarta.validation` no está en el classpath. Hoy llega como dependencia transitiva (`spring-boot-validation` → `hibernate-validator`). Si falta, no se agrega: se reporta, porque declarar la dependencia le toca a CM-311.
- **Terminado cuando** las pruebas pasan y el commit es `CM-312 | feat(ia): puerto del modelo y configuración validada`.

### T-06 · Cliente de Gemini sin reintentos

- [ ] **Cubre.** REQ-06 y D-03.
- **Archivos.** Crear `.../infrastructure/config/ia/LanguageModelConfiguration.java`.
- **Hacer.**
  1. Bean `com.google.genai.Client` construido con `Client.builder().apiKey(...).httpOptions(...)`:
     - `HttpOptions.timeout` con `timeout.toMillis()` convertido a `int`;
     - `retryOptions(HttpRetryOptions.builder().attempts(1))`;
     - `baseUrl` solo si la propiedad `cameia.llm.base-url` (solo para pruebas, no documentada en `.env.example`) tiene valor.
  2. Bean `org.springframework.core.retry.RetryTemplate` sin reintentos.
  3. `@EnableConfigurationProperties(LanguageModelProperties.class)`.
  4. Retirar `spring.ai.google.genai.api-key` de `application.properties` y agregar las propiedades de la sección 6 de la spec.
  5. En `application-local.properties`: `cameia.llm.api-key=${GOOGLE_GENAI_API_KEY:local-fake-key}`.
- **Verificación.**
  - `EntrevistaApplicationTests` con `@SpringBootTest(properties = "cameia.llm.model=modelo-de-prueba")` arranca.
  - Con `--debug`, el informe de condiciones muestra que la autoconfiguración no creó su propio `Client`.
- **Detenerse si** `GoogleGenAiChatAutoConfiguration` no toma el `RetryTemplate` del contexto o crea un segundo `Client`.
- **Terminado cuando** arranca y el commit es `CM-312 | feat(ia): cliente de Gemini con tiempo de espera y sin reintentos`.

### T-07 · Adaptador: camino feliz

- [ ] **Cubre.** REQ-07, REQ-13 y REQ-14.
- **Archivos.** Crear `.../infrastructure/ia/GeminiLanguageModelAdapter.java` y `package-info.java` (con el Router documentado, D-01). Borrar `.../infrastructure/ia/.gitkeep`.
- **Hacer.**
  1. Implementar `LanguageModelPort` con el `ChatModel` inyectado, pasando por llamada `GoogleGenAiChatOptions.builder().model(...).temperature(...).maxOutputTokens(...).thinkingLevel(...)`.
  2. Devolver el texto del primer resultado.
  3. Registrar en `INFO` la línea `Llamada al modelo [resultado={}, duracionMs={}]`, sin el texto.
- **Prueba.** `GeminiLanguageModelAdapterTest`, con un `HttpServer` del JDK en el puerto 0 que responde 200 con un cuerpo `generateContent` mínimo que trae el texto `OK`. Comprueba que el resultado es `OK` y que el servidor recibió una petición con el modelo configurado en la ruta.
- **Detenerse si** el SDK no envía la petición al `baseUrl` del servidor simulado: se reporta qué ruta pide.
- **Terminado cuando** la prueba pasa y el commit es `CM-312 | feat(ia): adaptador de Gemini`.

### T-08 · Adaptador: fallos

- [ ] **Cubre.** REQ-09 a REQ-12 y D-05.
- **Archivos.** `GeminiLanguageModelAdapter.java` y `GeminiLanguageModelAdapterTest.java`.
- **Hacer.** Capturar `RuntimeException` alrededor de la llamada, recorrer la cadena de causas y clasificar el fallo:
  - `ApiException` con `code() == 429` → `LlmLimitReachedException`;
  - otra `ApiException`, `GenAiIOException`, `IOException`, respuesta sin candidatos o texto en blanco → `LlmUnavailableException`.
  En los dos casos se conserva la causa y se relanza: no es un `catch` que trague la excepción.
- **Prueba.** Los casos de dependencias de la sección 7, cada uno comprobando el tipo de excepción y que el servidor recibió **una** petición. El tiempo agotado se provoca con `cameia.llm.timeout=200ms` y un manejador que espera un `CountDownLatch` que solo se libera al cerrar la prueba.
- **Detenerse si** el servidor recibe más de una petición en un caso de fallo: algún reintento sigue activo.
- **Terminado cuando** las pruebas pasan y el commit es `CM-312 | test(ia): fallos del adaptador de Gemini`.

### T-09 · Prueba real y catálogo de errores

- [ ] **Cubre.** El criterio 1 y REQ-19.
- **Archivos.**
  - Crear `GeminiLanguageModelLiveTest.java`.
  - Crear `.../domain/exception/ErrorCodeDocumentationTest.java`.
  - Modificar `docs/errores.md`.
- **Hacer.**
  1. `GeminiLanguageModelLiveTest`:
     - `@EnabledIfEnvironmentVariable(named = "LLM_LIVE_TEST", matches = "true")`;
     - arranca el contexto con las variables reales;
     - envía el prompt «Responde OK y luego una frase tuya de máximo seis palabras.» con un tope de 16 tokens;
     - comprueba que el texto no está en blanco, sin imprimirlo.
  2. `ErrorCodeDocumentationTest`: copiar la de Cuentas.
  3. `docs/errores.md`:
     - el apartado «Formato», con la forma y el `requestId`;
     - la tabla de tres filas de la sección 5 de la spec;
     - «Respuestas sin código»: «Ninguna».
- **Verificación.** `./mvnw.cmd test` en verde, con la prueba real omitida. Después, una ejecución manual con `LLM_LIVE_TEST=true`, cuyo resultado se anota para el PR sin la llave ni el texto.
- **Detenerse si** la prueba real devuelve el texto vacío: PD-04, se reporta antes de subir el tope.
- **Terminado cuando** las dos pruebas existen y el commit es `CM-312 | docs(errores): catálogo de errores del cliente del modelo`.

### T-10 · Configuración de uso y despliegue

- [ ] **Cubre.** REQ-20 y REQ-21.
- **Archivos.** `.env.example`, `docker-compose.yml`, `.github/workflows/despliegue-continuo.yml` y `CLAUDE.md`.
- **Hacer.**
  1. `.env.example`: `LLM_MODEL=gemini-3.7-flash` (PD-01) y `LLM_TIMEOUT=30s`, cada una con su comentario. La nota de `GOOGLE_GENAI_API_KEY` dice que sin llave el servicio no arranca, salvo en `local`, que trae una llave falsa.
  2. `docker-compose.yml`: pasar `LLM_MODEL` (obligatoria, con `:?`) y `LLM_TIMEOUT`.
  3. Workflow: agregar `LLM_MODEL=gemini-3.7-flash` a `--set-env-vars`.
  4. `CLAUDE.md`:
     - sección 2: los paquetes que ya existen;
     - sección 4: los códigos emitidos, con un enlace a `docs/errores.md`;
     - sección 10: Router y Kimi, H-1 a H-3.
- **Verificación.**
  - `docker compose config` valida.
  - `git grep -nE "AIza[0-9A-Za-z_-]{35}"` no devuelve nada.
- **Terminado cuando** el commit es `CM-312 | ci(despliegue): variables del modelo en staging y documentación`.

### T-11 · Verificación final y PR

- [ ] **Hacer.**
  1. `./mvnw.cmd clean verify`.
  2. Leer el informe JaCoCo de cada clase tocada (70 % o más, D-08).
  3. Medir el tamaño con `git diff --shortstat origin/develop`. Si pasa de 1000, separar T-10 y el catálogo en el PR B2.
  4. Validar con `pr_policy.py`.
  5. Abrir el PR con la plantilla completa, la salida real, la cobertura por clase y la tabla de la sección 8 de la spec con resultados. «Control humano» queda en pendiente.
- **Terminado cuando** el PR está abierto y la revisión queda a cargo de Backend.
