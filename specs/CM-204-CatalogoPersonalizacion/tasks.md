# Tarjetas · CM-204 Catálogo de personalización

Cada tarjeta se ejecuta sola y en orden. Quien la ejecuta no lee la spec: todo lo que necesita está aquí.

**Reglas para todas las tarjetas**

- Identificadores, nombres de métodos de prueba y códigos en inglés. Javadoc, comentarios, `@DisplayName`, OpenAPI, mensajes y documentos en español. Sin `CM-204` ni rutas a otros archivos en comentarios.
- Javadoc en toda clase, `record` y método (`@param`, `@return`, `@throws`); `package-info.java` en cada paquete nuevo.
- Pruebas `metodo_shouldX_whenY` con `@DisplayName` en español; Arrange-Act-Assert; valores literales; sin `Thread.sleep`; aserciones concretas.
- Prohibido: dependencias nuevas, cambiar los enumerados o el manejador de CM-311, migraciones, editar `.github/`, registrar el valor de `X-User-Id` o cuerpos.
- **Detenerse y reportar a Paula** con la salida real si un archivo o nombre de CM-311 no está donde dice la tarjeta, una prueba existente se rompe sin causa clara o falta un dato.
- Docker Desktop encendido: sin Docker las pruebas de integración se omiten y eso **no** cuenta como verde.
- Al terminar cada tarjeta: `./mvnw.cmd -B -ntp test` y anotar el resultado real al final de la tarjeta.

---

## T0 · Preparación, contraste con CM-311 y línea base

1. `git fetch`; `git status` limpio.
2. Base: `git log origin/develop --oneline --grep "CM-311"`. Fusionada → rama `CM-204-catalogo-personalizacion` desde `origin/develop`. No fusionada pero con `origin/CM-311-*` → rama desde su punta (stack); anotar SHA y árbol (`git rev-parse <base>^{tree}`). Ninguna → **detenerse**: CM-204 no empieza sin CM-311.
3. Comprobar que existen, con estos nombres exactos (si algo difiere, detenerse y reportar):

| Qué | Dónde | Cómo |
|---|---|---|
| `Modality` con `SIMULATION("SIMULACION")`, `TRAINING("ENTRENO")` y `code()` | `src/main/java/tech/cameia/entrevista/domain/model/Modality.java` | `git show HEAD:<ruta>` |
| `Tone` con `PROFESSIONAL_NEUTRAL`, `WARM`, `STRICT`, `MOTIVATING` (en ese orden) | `…/domain/model/Tone.java` | ídem |
| `AnswerMode` con `AUDIO("AUDIO")`, `TEXT("TEXTO")` | `…/domain/model/AnswerMode.java` | ídem |
| `InterviewLanguage` con `SPANISH_COLOMBIA("es-CO")`, `ENGLISH("en")` | `…/domain/model/InterviewLanguage.java` | ídem |
| `FirebaseUid.required(String)` | `…/domain/model/FirebaseUid.java` | ídem |
| `ProblemDetailTestSupport.errorHandler()` | `src/test/java/tech/cameia/entrevista/presentation/advice/ProblemDetailTestSupport.java` | ídem (anotar el nombre real del método si difiere) |
| `ck_sesion_modalidad`, `ck_sesion_tono`, `ck_sesion_forma_respuesta`, `ck_sesion_idioma` | `src/main/resources/db/migration/V1__esquema_inicial_entrevista.sql` | `git grep -n "ck_sesion_" HEAD -- src/main/resources` |
| Colección y entorno de Postman | `postman/cameia-entrevista.postman_collection.json`, `postman/local.postman_environment.json` | ídem |
| Configuración de dominio previa | `git grep -n "@Configuration" HEAD -- src/main` | si ya existe un `DomainPolicyConfiguration`, se extiende en T2 en vez de crearlo |

4. Jira (solo lectura): leer descripción, comentarios, padre y enlaces de CM-204; anotar al final de esta tarjeta si cambian algo de la spec. Si cambian un valor, **detenerse**.
5. Línea base: Docker encendido, `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS` y ninguna clase de Testcontainers `Skipped`. Anotar el número de pruebas.
6. Mover CM-204 a «En curso» en Jira (si Paula es la responsable).

**Terminado:** rama creada, base anotada, nombres confirmados, línea base verde.

---

## T1 · Dominio: catálogo como única fuente (REQ-CAT-02 a 05)

**Archivos nuevos**

`src/main/java/tech/cameia/entrevista/domain/model/CatalogOption.java`:

```java
package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Una opción de personalización de la entrevista y si hoy se puede elegir.
 *
 * <p>Una opción no disponible se muestra en la interfaz deshabilitada con «Próximamente»; no se puede
 * usar al crear una sesión.</p>
 *
 * @param value     valor del enumerado que representa la opción
 * @param available {@code true} si hoy se puede elegir
 * @param <E>       tipo del enumerado (modalidad, tono, forma de respuesta o idioma)
 */
public record CatalogOption<E extends Enum<E>>(E value, boolean available) {

    /** Exige el valor: una opción sin valor no significa nada. */
    public CatalogOption {
        Objects.requireNonNull(value, "value");
    }
}
```

`src/main/java/tech/cameia/entrevista/domain/policy/PersonalizationCatalog.java`:

```java
package tech.cameia.entrevista.domain.policy;

/**
 * Catálogo de personalización de la entrevista: modalidades, tonos, formas de respuesta e idiomas, con la
 * disponibilidad de cada uno.
 *
 * <p>Es la única fuente de esas opciones. La pantalla las muestra a partir de este catálogo y la creación de
 * la sesión las valida contra él, de modo que lo que se ofrece y lo que se acepta no pueden diferir. Las
 * listas siguen el orden de declaración de cada enumerado, que es el orden en que se muestran.</p>
 *
 * <p>Hoy solo la modalidad Entreno no está disponible: se muestra como «Próximamente» hasta que existan la
 * repregunta y el reintento de respuesta. Habilitarla exige cambiar este conjunto y desplegar.</p>
 */
public final class PersonalizationCatalog {

    /** Opciones que se muestran pero todavía no se pueden elegir. */
    private static final Set<Enum<?>> UNAVAILABLE = Set.of(Modality.TRAINING);

    private final List<CatalogOption<Modality>> modes = optionsOf(Modality.values());
    private final List<CatalogOption<Tone>> tones = optionsOf(Tone.values());
    private final List<CatalogOption<AnswerMode>> answerFormats = optionsOf(AnswerMode.values());
    private final List<CatalogOption<InterviewLanguage>> languages = optionsOf(InterviewLanguage.values());

    /** @return las modalidades en orden, con su disponibilidad; lista inmodificable */
    public List<CatalogOption<Modality>> modes() { return modes; }

    /** @return los tonos en orden; lista inmodificable */
    public List<CatalogOption<Tone>> tones() { return tones; }

    /** @return las formas de respuesta en orden; lista inmodificable */
    public List<CatalogOption<AnswerMode>> answerFormats() { return answerFormats; }

    /** @return los idiomas de la sesión en orden; lista inmodificable */
    public List<CatalogOption<InterviewLanguage>> languages() { return languages; }

    /**
     * Indica si una opción se puede elegir hoy.
     *
     * @param option valor de cualquiera de los cuatro enumerados
     * @return {@code false} solo para las opciones marcadas como «Próximamente»
     * @throws NullPointerException si {@code option} es nulo
     */
    public boolean isAvailable(Enum<?> option) {
        Objects.requireNonNull(option, "option");
        return !UNAVAILABLE.contains(option);
    }

    // Arma la lista de un enumerado en su orden de declaración; List.copyOf la deja inmodificable.
    private <E extends Enum<E>> List<CatalogOption<E>> optionsOf(E[] values) {
        return List.copyOf(Arrays.stream(values).map(value -> new CatalogOption<>(value, isAvailable(value))).toList());
    }
}
```

**Trampa:** los campos de instancia se inicializan llamando a `isAvailable`, que lee la constante `static` `UNAVAILABLE`: está bien porque lo estático se inicializa antes que cualquier instancia. No conviertas `UNAVAILABLE` en campo de instancia declarado después de las listas: quedaría nulo al construir.

**Prueba nueva** `src/test/java/tech/cameia/entrevista/domain/policy/PersonalizationCatalogTest.java` (sin Spring; `private final PersonalizationCatalog catalog = new PersonalizationCatalog();`):

| Prueba | Esperado |
|---|---|
| `modes_shouldOfferSimulationAvailableAndTrainingUnavailable` | `[(SIMULATION, true), (TRAINING, false)]` exacto (`containsExactly`) |
| `tones_shouldOfferFourTonesInOrder_whenRead` | `PROFESSIONAL_NEUTRAL, WARM, STRICT, MOTIVATING`, los 4 `available == true` |
| `answerFormats_shouldOfferAudioAndTextAvailable_whenRead` | `[(AUDIO, true), (TEXT, true)]` |
| `languages_shouldOfferSpanishColombiaAndEnglishAvailable_whenRead` | `[(SPANISH_COLOMBIA, true), (ENGLISH, true)]` |
| `catalog_shouldListEveryEnumValue_whenRead` | tamaños = `Modality.values().length` (2), `Tone` (4), `AnswerMode` (2), `InterviewLanguage` (2) |
| `isAvailable_shouldBeFalseOnlyForTraining` (`@ParameterizedTest @MethodSource` con los 10 valores de los 4 enumerados) | `TRAINING` → `false`; los otros 9 → `true` |
| `isAvailable_shouldThrow_whenNull` | `NullPointerException` |
| `catalog_shouldReturnUnmodifiableLists_whenRead` | `catalog.tones().add(...)` → `UnsupportedOperationException` |
| `catalogOption_shouldThrow_whenValueNull` | `new CatalogOption<Tone>(null, true)` → `NullPointerException` |

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=PersonalizationCatalogTest` → 10 pruebas (19 ejecuciones con la parametrizada) en verde.

**Commit:** `CM-204 | feat(dominio): catálogo de personalización como única fuente de opciones`.

---

## T2 · Endpoint, configuración y documentación OpenAPI (REQ-CAT-01, 06 a 12)

**Archivos nuevos**

`src/main/java/tech/cameia/entrevista/infrastructure/config/DomainPolicyConfiguration.java`:

```java
/**
 * Ensambla como beans las reglas de dominio, que se construyen con {@code new} y no conocen Spring.
 */
@Configuration
class DomainPolicyConfiguration {

    /** @return el catálogo de personalización, único para todo el servicio */
    @Bean
    PersonalizationCatalog personalizationCatalog() {
        return new PersonalizationCatalog();
    }
}
```

`src/main/java/tech/cameia/entrevista/application/service/GetPersonalizationCatalogService.java`:

```java
/**
 * Caso de uso: consultar el catálogo de personalización de la entrevista.
 *
 * <p>No depende del usuario ni de su plan: todos ven las mismas opciones. No toca la base ni otros
 * servicios, así que responde aunque la base no esté disponible.</p>
 */
@Service
public class GetPersonalizationCatalogService {

    private final PersonalizationCatalog catalog;

    /** @param catalog catálogo único del servicio */
    public GetPersonalizationCatalogService(PersonalizationCatalog catalog) { this.catalog = catalog; }

    /** @return el catálogo con sus opciones y su disponibilidad */
    public PersonalizationCatalog execute() { return catalog; }
}
```

(No `final`: la prueba web la simula con Mockito.)

`src/main/java/tech/cameia/entrevista/presentation/dto/CatalogOptionResponse.java`:

```java
/**
 * Una opción del catálogo tal como la recibe el cliente.
 *
 * @param value     valor que el cliente envía al crear la sesión y que usa para buscar su etiqueta
 * @param available si hoy se puede elegir; {@code false} se muestra deshabilitada con «Próximamente»
 */
@Schema(description = "Opción de personalización con su disponibilidad")
public record CatalogOptionResponse(
        @Schema(description = "Valor de la opción; el cliente lo envía al crear la sesión", example = "SIMULACION",
                requiredMode = Schema.RequiredMode.REQUIRED) String value,
        @Schema(description = "Si hoy se puede elegir; false = «Próximamente»", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean available) {

    /**
     * @param option opción del dominio
     * @param value  valor del enumerado (su {@code code()})
     * @return la opción para el contrato
     */
    static CatalogOptionResponse of(CatalogOption<?> option, String value) {
        return new CatalogOptionResponse(value, option.available());
    }
}
```

`src/main/java/tech/cameia/entrevista/presentation/dto/PersonalizationCatalogResponse.java`:

```java
/**
 * Respuesta de {@code GET /api/v1/interviews/config/catalog}: las cuatro listas de opciones de la entrevista.
 * Las etiquetas visibles no viajan: el cliente las toma de su catálogo de textos con el {@code code}.
 */
@Schema(description = "Catálogo de personalización de la entrevista")
public record PersonalizationCatalogResponse(
        @ArraySchema(arraySchema = @Schema(description = "Modalidades en orden. Valores: SIMULACION, ENTRENO (hoy no disponible)"))
        List<CatalogOptionResponse> modes,
        @ArraySchema(arraySchema = @Schema(description = "Tonos del entrevistador en orden. Valores: PROFESIONAL_NEUTRO, CALIDO, ESTRICTO, MOTIVADOR"))
        List<CatalogOptionResponse> tones,
        @ArraySchema(arraySchema = @Schema(description = "Formas de respuesta en orden. Valores: AUDIO, TEXTO"))
        List<CatalogOptionResponse> answerFormats,
        @ArraySchema(arraySchema = @Schema(description = "Idiomas de la sesión en orden. Valores: es-CO, en"))
        List<CatalogOptionResponse> languages) {

    /**
     * Traduce el catálogo del dominio al contrato, conservando el orden de cada lista.
     *
     * @param catalog catálogo del dominio
     * @return la respuesta para el cliente
     */
    public static PersonalizationCatalogResponse from(PersonalizationCatalog catalog) {
        return new PersonalizationCatalogResponse(
                catalog.modes().stream().map(o -> CatalogOptionResponse.of(o, o.value().code())).toList(),
                catalog.tones().stream().map(o -> CatalogOptionResponse.of(o, o.value().code())).toList(),
                catalog.answerFormats().stream().map(o -> CatalogOptionResponse.of(o, o.value().code())).toList(),
                catalog.languages().stream().map(o -> CatalogOptionResponse.of(o, o.value().code())).toList());
    }
}
```

`src/main/java/tech/cameia/entrevista/presentation/controller/InterviewConfigController.java`:

```java
/**
 * Adaptador de entrada HTTP de la configuración de la entrevista.
 *
 * <p>La identidad llega solo en {@code X-User-Id}, que pone el Gateway; sin ella se responde 401. El
 * catálogo es el mismo para todos los usuarios y planes.</p>
 */
@Tag(name = "Configuración de la entrevista", description = "Opciones con que se personaliza una entrevista")
@RestController
@RequestMapping("/api/v1/interviews/config")
class InterviewConfigController {

    private final GetPersonalizationCatalogService getCatalog;

    InterviewConfigController(GetPersonalizationCatalogService getCatalog) { this.getCatalog = getCatalog; }

    /**
     * Devuelve las modalidades, tonos, formas de respuesta e idiomas con su disponibilidad.
     *
     * @param userId identidad que pone el Gateway; obligatoria
     * @return 200 con el catálogo
     * @throws IdentityRequiredException si falta la identidad o no es válida
     */
    @Operation(summary = "Consultar el catálogo de personalización",
            description = "Opciones de la entrevista y su disponibilidad. Las mismas con que se valida la creación de la sesión.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = PersonalizationCatalogResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin identidad (code IDENTITY_REQUIRED)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":401,\"code\":\"IDENTITY_REQUIRED\",\"detail\":\"Tu sesión expiró. Inicia sesión de nuevo.\"}"))),
            @ApiResponse(responseCode = "405", description = "Método no admitido (code METHOD_NOT_ALLOWED)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":405,\"code\":\"METHOD_NOT_ALLOWED\",\"detail\":\"Método no permitido.\"}"))),
            @ApiResponse(responseCode = "406", description = "Tipo de respuesta no admitido (code MEDIA_TYPE_NOT_ACCEPTABLE)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":406,\"code\":\"MEDIA_TYPE_NOT_ACCEPTABLE\",\"detail\":\"Tipo de respuesta no admitido.\"}"))),
            @ApiResponse(responseCode = "500", description = "Error imprevisto (code INTERNAL_ERROR)", content = @Content(
                    mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":500,\"code\":\"INTERNAL_ERROR\",\"detail\":\"Ocurrió un error. Inténtalo de nuevo.\"}")))
    })
    @GetMapping(path = "/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PersonalizationCatalogResponse> catalog(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userId) {
        FirebaseUid.required(userId); // solo exige que la petición venga del Gateway con un usuario
        return ResponseEntity.ok(PersonalizationCatalogResponse.from(getCatalog.execute()));
    }
}
```

**Trampa 1:** si `cameia-web` o el Gateway agregan `X-User-Plan`, el controlador no lo lee: el catálogo no depende del plan (CA-4.3.9).
**Trampa 2:** `produces = application/json` hace que `Accept: application/xml` dé 406 por el manejador de CM-311; no agregues un convertidor XML.
**Trampa 3:** el `charset=UTF-8` del 200 lo pone `spring.servlet.encoding.force-response=true` (CM-311 T1); en MockMvc `standaloneSetup` no aparece, por eso se comprueba en la prueba de servidor real.

**Pruebas nuevas**

`src/test/java/tech/cameia/entrevista/application/service/GetPersonalizationCatalogServiceTest.java`: `execute_shouldReturnInjectedCatalog` (`assertThat(service.execute()).isSameAs(catalog)`).

`src/test/java/tech/cameia/entrevista/presentation/controller/InterviewConfigControllerTest.java` (MockMvc `standaloneSetup(new InterviewConfigController(new GetPersonalizationCatalogService(new PersonalizationCatalog()))).setControllerAdvice(ProblemDetailTestSupport.errorHandler())`; constante `EXPECTED_BODY` con el JSON exacto de abajo):

```json
{"modes":[{"value":"SIMULACION","available":true},{"value":"ENTRENO","available":false}],"tones":[{"value":"PROFESIONAL_NEUTRO","available":true},{"value":"CALIDO","available":true},{"value":"ESTRICTO","available":true},{"value":"MOTIVADOR","available":true}],"answerFormats":[{"value":"AUDIO","available":true},{"value":"TEXTO","available":true}],"languages":[{"value":"es-CO","available":true},{"value":"en","available":true}]}
```

| Prueba | Petición | Esperado |
|---|---|---|
| `catalog_shouldReturnExactBody_whenIdentityPresent` | `GET /api/v1/interviews/config/catalog`, `X-User-Id: uid-ñandú` | 200; `content().json(EXPECTED_BODY, JsonCompareMode.STRICT)` (orden y miembros exactos) |
| `catalog_shouldReturn200_whenIdentityHas128Chars` | `X-User-Id: "u".repeat(128)` | 200 |
| `catalog_shouldReturn401_whenIdentityInvalid` (`@ParameterizedTest`: sin encabezado, `""`, `"   "`, `"\t"`, `"u".repeat(129)`, `"uid\u0007"`) | — | 401; `$.code` = `IDENTITY_REQUIRED`; `$.detail` = «Tu sesión expiró. Inicia sesión de nuevo.»; `$.modes` no existe |
| `catalog_shouldBeIdenticalForEveryPlan_whenPlanHeaderDiffers` | tres peticiones: `uid-a` + `X-User-Plan: FREE`; `uid-a` + `PREMIUM`; `uid-b` sin plan | los tres cuerpos `String` iguales entre sí e iguales a `EXPECTED_BODY` |
| `catalog_shouldIgnoreQueryParameters_whenUnexpected` | `?plan=PREMIUM&modality=ENTRENO&lang=en&firebaseUid=otro` y, en otra petición, `?q=%3Cscript%3E&r=%25` | 200; cuerpo igual a `EXPECTED_BODY`; el segundo no contiene `script` |
| `catalog_shouldIgnoreBody_whenSent` | `GET` con `Content-Type: application/json` y cuerpo `{"modes":[]}` | 200; cuerpo igual a `EXPECTED_BODY` |
| `catalog_shouldReturn500Generic_whenUnexpectedFailure` | controlador con `mock(GetPersonalizationCatalogService.class)` y `when(service.execute()).thenThrow(new IllegalStateException("detalle interno"))` | 500; `$.code` = `INTERNAL_ERROR`; `$.detail` = «Ocurrió un error. Inténtalo de nuevo.»; el cuerpo no contiene `detalle interno` ni `IllegalStateException` |

`src/test/java/tech/cameia/entrevista/presentation/PersonalizationCatalogEndToEndTest.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "cameia.llm.model=modelo-de-prueba")`, `@Testcontainers(disabledWithoutDocker = true)`, contenedor `postgres:16-alpine` con `@ServiceConnection`, igual que las de CM-311; cliente `TestRestTemplate` o `RestClient` con el puerto):

| Prueba | Petición | Esperado |
|---|---|---|
| `catalog_shouldReturn200WithCharset_whenRealServer` | `GET` con `X-User-Id: uid-e2e`, `X-Request-Id: cat-e2e-1` | 200; `Content-Type` contiene `application/json` y `charset=UTF-8` (sin distinguir mayúsculas); encabezado `X-Request-Id` = `cat-e2e-1`; cuerpo JSON igual a `EXPECTED_BODY` (copiar la constante) |
| `catalog_shouldReturn401WithCharset_whenIdentityMissing_onRealServer` | sin `X-User-Id` | 401; `Content-Type` contiene `application/problem+json` y `charset=UTF-8`; `code` = `IDENTITY_REQUIRED`; `requestId` no vacío |
| `catalog_shouldReturn405WithAllow_whenMethodNotGet` (`@ParameterizedTest`: `POST`, `PUT`, `DELETE`, `PATCH`) | con `X-User-Id: uid-e2e` | 405; `code` = `METHOD_NOT_ALLOWED`; `Allow` contiene `GET` |
| `catalog_shouldReturn406_whenAcceptXml` | `Accept: application/xml` | 406; `code` = `MEDIA_TYPE_NOT_ACCEPTABLE` |
| `catalog_shouldMeetLatencyBudget_when50Requests` | 5 peticiones de calentamiento y 50 medidas con `System.nanoTime()` | todos los cuerpos iguales al primero; p95 (valor en la posición 47 de las 50 ordenadas, índice base 0) ≤ 2000 ms; imprimir p50 y p95 con `System.out` **no**: usar `assertThat(...).as("p95 = %d ms", p95)` |
| `openApi_shouldDocumentCatalog_withEveryResponse` | `GET /v3/api-docs` (perfil por defecto `local`, documentación encendida) | `paths['/api/v1/interviews/config/catalog'].get.responses` tiene las llaves `200`, `401`, `405`, `406`, `500`; el esquema `PersonalizationCatalogResponse` tiene `modes`, `tones`, `answerFormats`, `languages`; ningún parámetro `X-User-Id` visible |

`src/test/java/tech/cameia/entrevista/infrastructure/persistence/CatalogSchemaConsistencyTest.java` (mismo arranque; `JdbcTemplate`):

| Prueba | Qué hace | Esperado |
|---|---|---|
| `catalog_shouldMatchDatabaseCheck_forEveryColumn` (`@ParameterizedTest @CsvSource`: `ck_sesion_modalidad,modalidad`, `ck_sesion_tono,tono`, `ck_sesion_forma_respuesta,forma_respuesta`, `ck_sesion_idioma,idioma`) | `select pg_get_constraintdef(c.oid) from pg_constraint c join pg_namespace n on n.oid = c.connamespace where n.nspname = 'microentrevista' and c.conname = ?`; extraer con `Pattern.compile("'([^']+)'")` los literales | conjunto de literales = conjunto de `code()` de la lista del catálogo correspondiente |
| `catalog_shouldFitColumnLength_forEveryCode` (mismo `@CsvSource`) | `select character_maximum_length from information_schema.columns where table_schema = 'microentrevista' and table_name = 'sesion' and column_name = ?` | cada `code().length()` ≤ ese largo |

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="GetPersonalizationCatalogServiceTest,InterviewConfigControllerTest,PersonalizationCatalogEndToEndTest,CatalogSchemaConsistencyTest,LayeredArchitectureTest"` → todas ejecutadas (no `Skipped`) y en verde.

**Commit:** `CM-204 | feat(api): GET /api/v1/interviews/config/catalog con su documentación`.

---

## T3 · Postman (R11)

**Archivo:** `postman/cameia-entrevista.postman_collection.json` (v2.1, UTF-8). Carpeta nueva «Catálogo de personalización» con estas 7 peticiones, en este orden, contra `{{baseUrl}}/api/v1/interviews/config/catalog`:

| Nombre | Método y encabezados | `pm.test` |
|---|---|---|
| `200 catálogo completo` | GET; `X-User-Id: uid-postman-a`; `X-Request-Id: postman-cat-1` | estado 200; `Content-Type` incluye `application/json` y `charset=utf-8` (en minúsculas tras `toLowerCase()`); cuerpo igual a `EXPECTED_BODY` de T2 (`pm.expect(pm.response.json()).to.eql(...)`); `ENTRENO no disponible` (`json.modes[1].available === false`); `AUDIO y TEXTO disponibles`; `X-Request-Id` = `postman-cat-1`; `responseTime < 200`. Guardar el cuerpo en `pm.collectionVariables.set("catalogBody", pm.response.text())` |
| `200 mismo catálogo con plan FREE` | GET; `X-User-Id: uid-postman-b`; `X-User-Plan: FREE` | 200; `pm.response.text() === pm.collectionVariables.get("catalogBody")` |
| `200 parámetros inesperados ignorados` | GET `…/catalog?plan=PREMIUM&modality=ENTRENO`; `X-User-Id: uid-postman-a` | 200; mismo texto |
| `401 sin identidad` | GET sin `X-User-Id` | 401; `code` `IDENTITY_REQUIRED`; `detail` «Tu sesión expiró. Inicia sesión de nuevo.»; `problem+json` con charset; `requestId` presente; el texto no coincide con `/Exception\|trace\|org\.|tech\.cameia/` |
| `401 identidad en blanco` | GET; `X-User-Id: "   "` | ídem |
| `405 POST no admitido` | POST; `X-User-Id: uid-postman-a` | 405; `code` `METHOD_NOT_ALLOWED`; encabezado `Allow` incluye `GET` |
| `406 Accept XML` | GET; `X-User-Id: uid-postman-a`; `Accept: application/xml` | 406; `code` `MEDIA_TYPE_NOT_ACCEPTABLE` |

**Comando** (base de compose arriba; jar con `SERVER_PORT=8083`): `npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json --folder "Catálogo de personalización" --reporters cli,junit --reporter-junit-export ../../ejecucion/evidencia/CM-204/newman.xml` → 7 peticiones, 0 fallos. Guardar la salida en `ejecucion/evidencia/CM-204/newman.txt` (carpeta de la raíz `cameia`).

**Trampa:** si alguna falla en Newman y no en las pruebas automáticas, falta una prueba automática: agrégala antes de corregir.

**Commit:** `CM-204 | test(postman): peticiones del catálogo de personalización`.

---

## T4 · Documentación (R8)

1. `docs/errores.md`: en las filas de `IDENTITY_REQUIRED`, `METHOD_NOT_ALLOWED`, `MEDIA_TYPE_NOT_ACCEPTABLE` e `INTERNAL_ERROR`, agregar `GET /api/v1/interviews/config/catalog` a la columna «Endpoints». No se crean filas ni códigos.
2. `CLAUDE.md` del repo: sección 3, frase «Todavía ningún endpoint los lee» → «Los lee cada endpoint de `/api/v1/interviews/**`; hoy, `GET /api/v1/interviews/config/catalog`.»; sección 4, una línea: «El catálogo de personalización (`GET /api/v1/interviews/config/catalog`) es la única fuente de modalidades, tonos, formas de respuesta e idiomas; la creación de la sesión valida contra él.»
3. `docs/bitacora-ia/04_catalogo-personalizacion_prompt.md` (la entrada: rol, contexto, tarea, condiciones y formato con que se pidió decidir la forma del contrato y si el catálogo exige identidad) y su fila en `docs/bitacora-ia/README.md` (la salida es esta spec, §15). Numerar con el siguiente número libre si CM-311 o CM-312 ya usaron el 04.
4. `package-info.java` en `domain/policy` y `application/service` si no existen (Javadoc de una línea con el propósito del paquete).

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=ErrorCodeDocumentationTest` → verde.

**Commit:** `CM-204 | docs(entrevista): catálogo en errores, CLAUDE.md y bitácora`.

---

## T5 · Verificación única sobre la punta

1. `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`; en `target/surefire-reports`, ninguna clase de Testcontainers `Skipped`.
2. `jacoco.csv`: ≥ 90 % de líneas y de ramas en `PersonalizationCatalog`, `CatalogOption`, `GetPersonalizationCatalogService`, `DomainPolicyConfiguration`, `InterviewConfigController`, `PersonalizationCatalogResponse`, `CatalogOptionResponse`; cada línea o rama sin cubrir con su razón.
3. Newman: carpeta (7) y colección completa (7 de CM-311 + 7).
4. Recorrer la lista de verificación de la spec §20 fila por fila y llenar la columna «Resultado real» de la tabla de atributos (§14) en `ejecucion/evidencia/CM-204/resumen.md`, con la tabla R1 a R14 → evidencia.
5. Si las reglas de push de `CLAUDE.md` raíz §2 se cumplen (spec y plan aprobados en `ESTADO.md`): push de la rama y PR en Draft con `[IA-ASISTIDO]`, validando antes el cuerpo con `.github/scripts/pr_policy.py`. Explicar a Paula en lenguaje llano y detenerse.

## Resultados

| Tarjeta | Resultado real | Fecha |
|---|---|---|
| T0 | Rama desde `origin/develop` `e79d46a` (CM-311 fusionada, PR #35). Nombres de CM-311 confirmados; difieren dos: el método de apoyo es `ProblemDetailTestSupport.errorHandler()` y la bitácora `04` ya existía (se usó la `05`). Jira sin cambios de valores (CM-204 ya «En curso»). Línea base: `BUILD SUCCESS`, 467 pruebas, 0 fallos, 1 omitida (la prueba manual contra Gemini) | 22-oct-2026 |
| T1 | `PersonalizationCatalogTest`: 18 ejecuciones (8 sueltas + 10 de la parametrizada; la tarjeta decía 17), 0 fallos. Commit `ed2f6a5` | 22-oct-2026 |
| T2 | 51 ejecuciones de las 6 clases del comando, 0 fallos tras corregir la consulta de `parameters` de la prueba de OpenAPI (con el encabezado oculto, OpenAPI no trae esa lista). Commit `13a3ac8` | 22-oct-2026 |
| T3 | Newman de la carpeta: 7 peticiones, 37 comprobaciones, 0 fallos. Commit `2bd6176` (incluye el README de `postman/`) | 22-oct-2026 |
| T4 | `ErrorCodeDocumentationTest`: 20 pruebas, 0 fallos. Commit `9100407` | 22-oct-2026 |
| T5 | `clean verify`: `BUILD SUCCESS`, 515 pruebas, 0 fallos, 1 omitida (la manual de Gemini). Cobertura de las 7 clases: 100 % de líneas y ramas. Newman de la colección completa: 14 peticiones, 71 comprobaciones, 0 fallos. Exploración adversarial: 43 casos y 200 peticiones concurrentes sin ningún 500. Detalle en `ejecucion/evidencia/CM-204/resumen.md` | 22-oct-2026 |
