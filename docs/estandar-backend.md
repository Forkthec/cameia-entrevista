# Estándar de Backend de CAMEIA

Reglas de calidad comunes a los microservicios de Backend (Cuentas, Perfil, Entrevista y Gateway). Este documento es idéntico en los cuatro repositorios; lo propio de cada servicio vive en su `CLAUDE.md`.

## 1. Alcance y precedencia

Aplica a todo código, prueba, migración y documento nuevo o modificado de un microservicio de Backend. Esta es la única lista de precedencia del repositorio; cuando dos fuentes chocan, manda la primera:

1. La seguridad y los contratos ya publicados que consume otro equipo. Un cambio de contrato es aditivo y se avisa a Frontend.
2. `docs/constitution.md`, en los principios no negociables.
3. Este estándar, en calidad de código.
4. `CONTRIBUTING.md`, en rama, commit, título de PR, revisión y merge.
5. `CLAUDE.md`, en lo propio del servicio (estructura, rutas, datos, seguridad).

Si dos documentos de esta lista se contradicen, el trabajo se detiene, se reporta qué choca y dónde, y se corrige el documento en un PR. Nunca se elige en silencio.

**Código existente.** Una diferencia entre el código que ya existe y este estándar no detiene el trabajo: se registra como hallazgo en la spec de la tarea que la encuentra, con su destino, y se corrige cuando una tarea modifica ese archivo. El código nuevo y el modificado cumplen el estándar completo.

## 2. Idioma y nombres

- **Identificadores en inglés** (paquetes, clases, métodos, variables, constantes). **Documentación en español:** Javadoc, OpenAPI, comentarios, logs, mensajes de error, `@DisplayName`, commits y PR.
- Tablas y columnas en `snake_case` español y en singular (`cuenta`, `experiencia_laboral`); las constantes, en `UPPER_SNAKE_CASE`.
- Sin abreviaturas, salvo siglas técnicas establecidas (`dto`, `id`, `uid`, `url`, `api`, `jwt`) y el sufijo `AppService`.
- Sin prefijo `I` en las interfaces ni sufijo `Impl` en las clases.

**Sufijo por capa.** Rige para las clases nuevas; una clase existente con otra forma conserva su nombre.

| Pieza | Forma | Ejemplo |
|---|---|---|
| Controlador | `<Concept>Controller` | `ProfileController` |
| DTO | `<UseCase>Request` / `<Concept>Response` | `CreateProfileRequest` |
| Manejador de errores | `<Scope>ExceptionHandler` | `BusinessExceptionHandler` |
| Servicio de aplicación | `<Concept>AppService`, que lo distingue de un servicio de `domain.service` | `ProfileAppService` |
| Comando | `<UseCase>Command` | `RegisterUserCommand` |
| Modelo de dominio | sin sufijo | `Account` |
| Política | `<Concept>Policy` | `PasswordPolicy` |
| Puerto | `<Aggregate>Repository` o el agente | `AccountRepository` |
| Evento de dominio | hecho en pasado | `AccountDeleted` |
| Excepción de negocio | `<Rule>Exception` | `EmailAlreadyRegisteredException` |
| Entidad JPA | `<Concept>Entity` | `AccountEntity` |
| Spring Data / adaptador | `<Entity>JpaRepository` / `<Port>Adapter` | `AccountJpaRepository` / `AccountRepositoryAdapter` |
| Consumidor de mensajes | `<Event>Listener` | `AccountDeletedListener` |
| Contrato de mensaje | `<Event>PayloadV<n>` | `AccountDeletedPayloadV1` |
| Configuración | `<Topic>Configuration` | `FirebaseConfiguration` |

**Verbos**

| Intención | Forma |
|---|---|
| Cambiar el estado del agregado | verbo imperativo: `finalize()`, `addSkill()` |
| Preguntar sin efectos | `can…`, `is…`, `has…` |
| Calcular o derivar | sustantivo del resultado: `missingFields()` |
| Fábrica estática | `create` u `of` |
| Guardia que lanza | `require…` |
| Decisión de una política | `allows…`, devuelve un enumerado, no un `boolean`, porque el `boolean` pierde el motivo |

**Pruebas nuevas:** `metodo_shouldResultado_whenCondicion` con `@DisplayName` en español. Las pruebas existentes con otro estilo se renombran solo cuando una tarea las modifica.

## 3. Arquitectura y dependencias

- Cuatro capas: `presentation`, `application`, `domain` e `infrastructure`. `domain` no importa Spring, JPA, Rabbit ni Google. `application` depende de `domain` solo por puertos. `infrastructure` implementa los puertos. `presentation` no accede a `infrastructure`.
- La prueba de arquitectura (ArchUnit) del repositorio debe quedar en verde. Una regla de esta sección que la prueba todavía no vigila se comprueba en la revisión del PR.
- **Persistencia en tres piezas:** el puerto en `domain.port` (sin una importación de Spring ni de JPA), la interfaz de Spring Data y el adaptador que implementa el puerto con ella. El servicio de aplicación inyecta el puerto, nunca el `JpaRepository` ni el adaptador.
- El mapeo entre dominio y entidad se escribe a mano; no se usan generadores de mapeo.
- `@Transactional` solo en `application.service`: `@Transactional(readOnly = true)` en lecturas y `@Transactional` en escrituras, con el alcance mínimo. Ninguna llamada externa (Firebase, LLM, RabbitMQ, otro servicio) dentro de una transacción, salvo con compensación explícita y probada.
- `spring.jpa.open-in-view=false`, `ddl-auto=validate`, `@Enumerated(EnumType.STRING)` y relaciones `LAZY`.
- **Prohibido:** clases `Utils`, Singleton manual, Service Locator, herencia de más de dos niveles, `Map<String,Object>` como cuerpo o payload, importar clases de otro microservicio, convertir un valor pendiente de decisión en constante y agregar dependencias sin aprobarlas en el plan.

## 4. Código limpio y mantenible

**Alcance y reutilización**

- Se escribe lo mínimo que cumple la spec: sin parámetros, clases, interfaces, banderas ni configuración «por si acaso». Una interfaz solo si es un puerto de dominio o hay dos implementaciones reales.
- Orden de decisión: reutilizar, extender, crear. Antes de crear una excepción, un validador, un objeto de valor o una utilidad de prueba se busca en el repositorio; si se crea, el plan dice por qué no servía lo existente. La abstracción se extrae a la tercera repetición.
- Un patrón (puerto y adaptador, comando, objeto de valor, política, compensación) solo si resuelve un problema presente y el repositorio ya lo usa. Strategy, Factory o Builder exigen dos o más variantes reales.
- Las reglas de negocio viven en `domain` y se prueban sin Spring; el controlador y el DTO solo validan sintaxis.
- Sin estado en memoria entre peticiones (el servicio escala por instancias), operaciones idempotentes y tiempo de espera en toda llamada externa.
- No se refactoriza ni se reformatea fuera del alcance del cambio.

**Límites que se revisan en el PR**

| Elemento | Límite |
|---|---|
| Método | ≤ 20 líneas (≤ 15 en agregados y objetos de valor) |
| Parámetros | ≤ 3; desde el cuarto, un objeto de valor o un comando |
| Anidamiento | ≤ 2 niveles; se sale temprano con guardias |
| Complejidad ciclomática | ≤ 8 |
| Clase | ≤ 200 líneas |
| Parámetro `boolean` | prohibido si cambia el comportamiento |

**Estilo**

- Inyección por constructor, con dependencias `private final` y sin `@Autowired`.
- Visibilidad de paquete por defecto en controladores y sus métodos, clases `@Configuration`, métodos `@Bean`, `JpaRepository` y adaptadores. Públicos el modelo de dominio, los puertos, los servicios de aplicación, los DTO y los comandos, porque otro paquete los usa a propósito (el controlador llama al servicio de aplicación).
- Objetos de valor inmutables (`record`); sin setters públicos en el dominio (el estado cambia con métodos de negocio); los agregados no exponen sus colecciones (copia o vista de solo lectura).
- Los controladores devuelven `ResponseEntity<T>` con el estado explícito (201 en una creación). La raíz de todo JSON es un objeto, nunca un arreglo. Toda colección sin cota se pagina.
- Sin `TODO` en el código: lo pendiente va a la spec o a Jira.
- Logs con SLF4J; los costosos de `DEBUG` y `TRACE` van con una guarda `isDebugEnabled()` o con un `Supplier`.
- Configuración propia con `@ConfigurationProperties` y `@Validated`, para que el servicio no arranque con una configuración inválida; sin `@Value` disperso.

**Antipatrones que bloquean un PR:** dominio anémico; entidad JPA usada como modelo de dominio; agregado anotado con `@Entity`; lógica de negocio en el controlador; entidad o agregado devuelto por un controlador; clave foránea hacia otro contexto; `JpaRepository` inyectado desde `application`.

## 5. Validación y casos borde

**Dos capas.** En el borde, Bean Validation sobre el DTO (`@Valid`) comprueba sintaxis: presencia, tipo, formato, longitud y rango. En el dominio, objetos de valor y políticas imponen los invariantes de negocio. Los valores que se interpretan (fechas, enumerados) se tipan en el DTO o se validan con un mensaje propio; nunca hay un `parse` o un `valueOf` sin capturar. Un mensaje y un código por causa, nunca uno genérico. Los campos no esperados se ignoran. El texto se normaliza antes de validar (recorte de espacios, NFC, minúsculas en el correo).

**Casos borde.** Cada requisito se recorre con esta lista, campo por campo:

| Grupo | Casos |
|---|---|
| Presencia | ausente, `null`, vacío, solo espacios, solo tabuladores o saltos de línea |
| Texto | n−1, n y n+1 caracteres contados por puntos de código en NFC; tildes, ñ, emoji, caracteres combinantes; espacios internos y en los extremos; caracteres de control, `<script>`, comillas, `%` y `_` |
| Números | cero, negativo, mínimo y máximo exactos, uno fuera, decimales, desbordamiento, no numérico |
| Fechas | calendario inválido (31/02), año bisiesto y no bisiesto, hoy, ayer, mañana, cambio de mes y de año, todo en UTC, reloj fijo en las pruebas |
| Enumerados | valor válido, desconocido, en minúsculas, vacío |
| Duplicados | mismo valor con otra mayúscula, con tilde o con espacios; duplicado concurrente |
| Estado | transición inválida, operación repetida, recurso ya eliminado |
| Propiedad | recurso de otro usuario, sin identidad, identidad en el cuerpo distinta de la del encabezado |
| Colecciones | vacía, un elemento, máximo, máximo + 1, orden, paginación en los extremos |
| Concurrencia | doble envío, dos pestañas, dos hilos sobre la misma fila, actualización perdida |
| Falla parcial | falla tras el primer efecto externo, falla de la compensación, error de base de datos a mitad |
| Dependencias | tiempo agotado, 5xx, respuesta vacía o mal formada, reintento |
| Carga | cuerpo grande, campos extra, JSON mal formado, tipo de contenido erróneo |

Cada celda aplicable es una prueba con valores literales; las que no aplican se justifican por escrito en la spec.

## 6. Errores

Toda respuesta de error de un microservicio Spring usa `application/problem+json; charset=UTF-8` (RFC 9457):

```json
{
  "type": "about:blank",
  "title": "Datos no válidos",
  "status": 422,
  "detail": "La contraseña es demasiado común.",
  "instance": "/api/v1/users",
  "code": "VALIDATION_FAILED",
  "requestId": "3c14c237-9fb8-4c25-86c6-cd81501d44f2",
  "errors": [
    { "field": "password", "code": "PASSWORD_TOO_COMMON", "message": "Esta contraseña es demasiado común, elige otra." }
  ]
}
```

- `code`: la causa de la operación. `VALIDATION_FAILED` cuando hay `errors`; en los demás casos, el código específico (`EMAIL_ALREADY_REGISTERED` con 409).
- `requestId`: el `X-Request-Id` que pone el Gateway; si falta, el servicio genera uno y lo devuelve en el encabezado. Es el hilo para depurar entre el Gateway, el servicio y el log.
- `errors[]`: un elemento por campo rechazado, con `field` (nombre del contrato JSON), `code` y `message`.
- Un fallo técnico responde 500 con `INTERNAL_ERROR` y un mensaje genérico, sin detalle técnico, que cada servicio fija en su `docs/errores.md`; la causa y la traza van solo al log. Ninguna respuesta lleva traza, SQL, nombre de clase ni el mensaje de una excepción de librería.
- `code`, `requestId` y `errors[].code` son miembros de extensión que la norma admite; se agregan sin reemplazar nada de lo publicado y se avisan a Frontend. La decisión está en el [ADR 0001](adr/0001-codigo-de-error-y-request-id.md).

**Nombre de los códigos.** `UPPER_SNAKE_CASE` en inglés con la forma `<SUJETO>_<CAUSA>`. El sujeto es el campo o el recurso (`EMAIL`, `PASSWORD`, `BIRTH_DATE`, `PROFILE`, `SKILL`, `ACCOUNT`). La causa sale de un vocabulario cerrado: `REQUIRED`, `TOO_SHORT`, `TOO_LONG`, `INVALID_FORMAT`, `INVALID_CHARACTERS`, `INVALID_VALUE`, `OUT_OF_RANGE`, `IN_THE_FUTURE`, `UNDERAGE`, `NOT_FOUND`, `ALREADY_EXISTS` (o `ALREADY_REGISTERED`), `LIMIT_REACHED`, `NOT_ALLOWED`, `NOT_VERIFIED`, `DISABLED`, `CONFLICT`, `UNAVAILABLE` y `TIMEOUT`. Una causa nueva se agrega con su spec.

**Reglas del catálogo**

1. Un código corresponde a un estado HTTP, un mensaje, una excepción de negocio y al menos una prueba. Un código publicado no se reutiliza ni se renombra.
2. Cada servicio documenta su catálogo en `docs/errores.md`.
3. El manejador de errores no inventa códigos: los toma de la excepción. Una violación de restricción de base de datos se traduce a su código específico, nunca a `INTERNAL_ERROR`.
4. Mapa base de estados para lo nuevo (un estado ya publicado que difiere se conserva y se anota en `docs/errores.md`): 400 cuerpo ilegible o encabezado faltante · 401 sin identidad · 403 sin permiso o recurso ajeno donde el servicio lo defina · 404 inexistente · 409 duplicado o conflicto de estado · 422 validación · 429 límite de uso · 502, 503 y 504 dependencia · 500 genérico.
5. Sin `catch` vacío ni excepciones tragadas. `catch (Exception)` solo en el manejador y en los consumidores de mensajes; `catch (RuntimeException)` solo para compensar y relanzar. Si la respuesta es un error, el estado final queda coherente.
6. El Gateway conserva su formato `{"code","message"}` con un catálogo cerrado. `EMAIL_NOT_VERIFIED`, `PLAN_LIMIT` y `LLM_UNAVAILABLE` son códigos reservados por el contrato del proyecto y todavía ningún servicio los emite; se publican cuando el código los emita.

**Registro.** Cada error se registra una vez, en español, con `code`, `requestId` y el identificador interno (`firebaseUid`), sin datos personales: los 4xx de negocio en `WARN` sin traza; los 5xx y las compensaciones fallidas en `ERROR` con traza. El `requestId` entra al `MDC` desde un filtro.

## 7. Base de datos

1. **Solo por migración.** Flyway `VNNN__descripcion.sql` nueva y probada contra una base con datos. Una migración aplicada no se edita.
2. **El esquema repite el código.** `NOT NULL` por defecto (un `NULL` se justifica); `CHECK` para enumerados y rangos; `UNIQUE`, sin distinguir mayúsculas donde el dominio no las distingue (índice sobre `lower(...)`); `VARCHAR(n)` con el mismo `n` que el límite validado.
3. **Tipos.** Identificadores `uuid`; instantes `timestamptz` en UTC; fechas sin hora `date`; dinero `numeric`, nunca `double`; booleanos con valor por defecto.
4. **Integridad.** Claves foráneas solo dentro del servicio, con `ON DELETE` explícito, y ninguna hacia otro servicio. Una base y un rol por servicio, sin acceso entre bases.
5. **Rendimiento.** Índice en cada clave foránea y en cada filtro u orden frecuente (parcial si el filtro es fijo); sin consultas `N+1`; colecciones con paginación y tope.
6. **Concurrencia.** `@Version` en los agregados que se actualizan a la vez; bloqueo pesimista del padre cuando una regla cruza varias filas (mínimos y máximos). La perdedora se traduce a `CONFLICT` o se relee de forma idempotente.
7. **Nombres con prefijo:** `pk_`, `fk_`, `uq_`, `ck_` e `ix_`, seguidos de la tabla y las columnas o la regla (`uq_cuenta_correo`).
8. **Datos sensibles.** Nunca contraseñas ni binarios; datos personales solo con la finalidad mínima y sin copias en logs.
9. **Pruebas.** Cada restricción tiene una prueba que la viola y comprueba el código de error resultante; la migración se prueba con PostgreSQL real en Testcontainers.

## 8. Seguridad

Cada endpoint y cada cambio se revisan contra el nivel 1 de ASVS (matriz de seguridad del proyecto) y contra OWASP API Security Top 10:

| Riesgo | Qué se comprueba |
|---|---|
| API1 Autorización por objeto | Cada lectura o escritura de un recurso propio comprueba el dueño con la identidad del Gateway; recurso ajeno → 403 o 404 según el servicio |
| API2 Autenticación rota | Solo se acepta la identidad que pone el Gateway; ningún dato de identidad sale del cuerpo ni de la ruta, salvo en las rutas sin identidad del `CLAUDE.md` |
| API3 Propiedades del objeto | DTO explícitos de entrada y salida; sin enlazar entidades; `id`, `estado` y `plan` no se aceptan del cliente |
| API4 Consumo de recursos | Límites de longitud, de cuerpo, de elementos y de página; tiempo de espera en toda llamada externa |
| API5 Autorización por función | Cada operación declara quién la ejecuta; un rol no equivale a un permiso de negocio |
| API6 Flujos sensibles | Registro, verificación y cambios de estado con idempotencia y límite de intentos |
| API7 SSRF | Ninguna URL de destino sale de la entrada del usuario |
| API8 Configuración | Perfil `prod` sin documentación abierta ni emulador; variables validadas al arrancar; sin trazas en respuestas |
| API9 Inventario | Rutas solo bajo `/api/v1/...`; ningún endpoint sin documentar |
| API10 Consumo inseguro | Se valida lo que responde Firebase, otro servicio o el LLM antes de usarlo |

- La identidad del usuario llega solo en los encabezados `X-User-*` que pone el Gateway; nunca en el cuerpo ni en la ruta. La única excepción son las rutas sin identidad que el `CLAUDE.md` del servicio declara con su spec, como el registro, que crea la identidad en vez de afirmarla.
- Consultas parametrizadas; ningún SQL concatenado; sin deserializar tipos arbitrarios; respuestas siempre por DTO.
- `Content-Type` con `charset` en toda respuesta.
- Actuator público solo en `health` e `info`; el resto exige autenticación.
- Secretos solo por variable de entorno o gestor de secretos; `.env.example` sin valores; nunca en Git.
- Ni contraseñas, tokens, correos, nombres, contenido del CV, audio ni transcripciones en logs, URL o errores. El `firebaseUid` y el `X-Request-Id` sí se registran.

## 9. Pruebas y cobertura

| Capa | Prueba |
|---|---|
| Dominio | JUnit 5 y AssertJ, sin Spring; los límites en tablas `@ParameterizedTest` |
| Aplicación | Mockito o dobles en memoria; se verifican los efectos y que no quede estado a medias |
| Web | MockMvc o WebTestClient: estado, `Content-Type`, `detail` y `errors[].field` |
| Persistencia e integración | PostgreSQL real con Testcontainers y `@SpringBootTest(webEnvironment = RANDOM_PORT)`; un flujo completo por caso de uso |
| Arquitectura | ArchUnit del repositorio en verde |

- Un caso por camino y por rama: feliz, cada validación con sus límites, cada excepción y su código, duplicado, inexistente, recurso ajeno, sin identidad, dependencia caída o lenta, compensación, vacíos y concurrencia.
- H2 está prohibido. Una prueba nunca depende del orden ni de `Thread.sleep`; los datos son sintéticos y literales; el reloj se inyecta.
- `*Test` los ejecuta Surefire y `*IT` los ejecuta Failsafe; una clase `*IT` no se crea sin comprobar que corre.
- Cobertura de lo nuevo o modificado **≥ 90 % de líneas y de ramas**, medida con JaCoCo; cada línea o rama sin cubrir se reporta con su razón. La cobertura global del repositorio no baja.
- «Terminado» significa `./mvnw.cmd clean verify` ejecutado con salida real. Si algo no se pudo ejecutar, se dice y por qué.

## 10. Documentación

- **OpenAPI completo.** Cada controlador con `@Tag`; cada endpoint con `@Operation`, `@Parameter` y un `@ApiResponse` por cada estado posible (éxito, 400, 401, 403, 404, 409, 422, 503 según aplique) con el esquema de error y un ejemplo con `code`; cada encabezado relevante (`Idempotency-Key`, `X-Request-Id`); cada campo de cada DTO con `@Schema` (descripción, ejemplo, obligatoriedad, longitud, patrón y valores permitidos), coherente con la validación y con la columna. Antes de cada PR se revisa el OpenAPI generado contra la spec.
- **Javadoc** en toda clase, interfaz, `record`, enumerado y método (públicos, protegidos y de paquete; los privados cuando la lógica no es evidente), con el porqué, `@param`, `@return` y `@throws`; y un `package-info.java` por paquete.
- **Comentarios de bloque** sobre la lógica importante (regla de negocio, compensación, concurrencia, decisión de seguridad): explican la intención y el porqué. **Comentarios de línea** cortos y solo para lo no obvio; ningún código comentado.
- Los comentarios son autosuficientes: no llevan `CM-NNN` ni remiten a otros archivos o repositorios.
- Una decisión de contrato o de arquitectura se registra en un ADR en `docs/adr/`. El catálogo de errores está en `docs/errores.md`, y el `README.md` y el `.env.example` se mantienen al día.

## 11. Reglas del proyecto

| Tema | Regla |
|---|---|
| Idempotencia | Registro y verificación detectan el duplicado por correo y `firebaseUid`; las operaciones que crean un recurso a partir de una acción repetible aceptan el encabezado `Idempotency-Key`; la respuesta del usuario se guarda antes de llamar al LLM |
| LLM y APIs externas | Salida JSON validada contra esquema; tiempo de espera configurable por ambiente; hasta 3 intentos con espera creciente; 429 o 5xx del proveedor → 503 `LLM_UNAVAILABLE`; nada generado por IA se confirma sin validación; cada adaptador prueba éxito, formato inválido, tiempo agotado y cuota; ni prompts, ni transcripciones ni secretos en logs |
| Eventos | Contrato versionado aprobado antes de publicar; Outbox en el productor e Inbox en el consumidor; `ack` tras el commit; cola de errores; cada mensaje lleva id, tipo, versión, instante, productor, `correlationId` y `causationId`, sin secretos, audio ni datos personales innecesarios |
| Datos entre servicios | Una base y un rol por servicio; sin acceso ni claves foráneas entre bases; lo emitido es inmutable (reporte, pago aprobado) |
| Propiedad | El dueño sale del encabezado de identidad. Perfil responde 403 ante un recurso ajeno y 404 ante uno inexistente; Entrevista responde 404 en ambos casos y traduce un 403 de Perfil a 404 |
| Fechas | Todo se calcula en UTC con un reloj inyectable; experiencia y formación se comparan por mes y año; la hora de Colombia es de presentación y la pone el cliente |
| Texto libre | Se guarda y se devuelve como texto, nunca como HTML |
| Rendimiento | p95 ≤ 2 s en operaciones JSON propias sin IA ni archivos; 5xx propios < 1 % |
| Privacidad | Ley 1581: datos mínimos; sin datos personales en logs, tampoco en procesos de purga; audio solo temporal; staging con datos de prueba |
| Servicio desplegable | `Dockerfile` de dos etapas con usuario sin privilegios y `HEALTHCHECK`; `.env.example` sin secretos; ingreso interno |
| Evidencia del PR | Cada comprobación con resultado `PASA`, `FALLA`, `BLOQUEADO` o `PENDIENTE` sobre un SHA identificado; todo «No aplica» con su justificación; nunca se afirma como implementada una capacidad pendiente |

## 12. Desarrollo guiado por especificación

1. **Fases con aprobación entre ellas:** análisis, spec, plan y tareas. Sin spec aprobada no hay código.
2. Cada tarea vive en `specs/CM-NNN-Descripcion/` (`Descripcion` en PascalCase) con tres archivos:
   - `spec.md`: contexto, requisitos numerados en notación EARS, cada campo con su regla, límites, mensaje, código y estado HTTP, cada columna y restricción de base de datos, casos borde con datos literales y lo que queda fuera.
   - `plan.md`: enfoque, archivos, qué se reutiliza, decisiones con su porqué y las alternativas descartadas, riesgos y matriz de pruebas.
   - `tasks.md`: tarjetas autosuficientes de 30 minutos o menos, con archivos exactos, pruebas con valores literales y qué hacer si algo no cuadra; cada una se marca `[x]` al terminarla.
3. Toda decisión de arquitectura, seguridad, datos, costo o contrato queda en la spec con la decisión humana, su porqué y las alternativas descartadas.
4. **Lo que no se sabe se pregunta**, en rondas de hasta 6 preguntas, y lo que siga sin respuesta queda en la spec como pendiente con su responsable. No se asume nada en silencio.
5. Un PR cubre una pieza reconocible y verificada y no pasa de 1000 líneas entre agregadas y eliminadas, tampoco cuando solo cambia documentos: el límite existe para que cada línea se lea de verdad. Un cambio mayor se parte en piezas.

## 13. Contribución y entrega

- Rama, commit, tipos, título de PR, revisión y merge: rige `CONTRIBUTING.md`. Los tipos son `feat`, `fix`, `test`, `docs`, `refactor`, `build`, `ci` y `chore`.
- `[IA-ASISTIDO]` va solo al final del título del PR cuando hubo IA.
- Una IA puede abrir un PR y nunca lo fusiona; el control humano lo marca la persona que revisa.
- La descripción del PR llena los ocho campos de la plantilla del repositorio y declara los atributos de calidad que toca (sección 15).
- La bitácora de IA guarda solo la entrada de las decisiones con peso humano, en `docs/bitacora-ia/`.

## 14. Guía de Spring Boot: qué se adopta

| # | Práctica | Estado |
|---|---|---|
| 1 | Inyección por constructor | Se adopta, con dependencias `final` y sin `@Autowired` |
| 2 | Visibilidad de paquete en componentes de Spring | Se adopta (sección 4) |
| 3 | Propiedades tipadas y validadas | Se adopta; el servicio no arranca con una configuración inválida |
| 4 | Fronteras de transacción | Se adopta (sección 3) |
| 5 | `open-in-view=false` | Se adopta |
| 6 | Separar la capa web de la de persistencia | Se adopta: DTO `record`, nunca entidades |
| 7 | Principios REST | Se adopta, salvo el versionado nativo: la versión va en la ruta `/api/v1/...` porque es el contrato que consumen el Gateway, la web y Entrevista |
| 8 | Comandos para las operaciones de negocio | Se adopta (`application.command`) |
| 9 | Manejo centralizado de excepciones | Se adopta, con `ProblemDetail` (sección 6) |
| 10 | Actuator mínimo | Se adopta: público solo `health` e `info` |
| 11 | Internacionalización con `ResourceBundles` | No se adopta: el producto es monolingüe en español y no hay un requisito que la pida |
| 12 | Testcontainers | Se adopta (sección 9) |
| 13 | Puerto aleatorio en las pruebas de integración | Se adopta |
| 14 | Logging con SLF4J y guardas | Se adopta (secciones 4 y 8) |

Sobre configuración por variables de entorno frente a perfiles: las diferencias entre ambientes se pasan por variables de entorno; el perfil `prod` fija lo que no puede encenderse desde fuera, como la documentación de la API y el emulador de Firebase.

## 15. Atributos de calidad

Cada PR declara en su descripción qué atributos toca y con qué evidencia:

| Atributo | Evidencia típica |
|---|---|
| Seguridad | Prueba de ataque (IDOR, entrada hostil) y la línea de la matriz ASVS que cubre |
| Fiabilidad | Pruebas de idempotencia, de compensación y de concurrencia |
| Rendimiento | Índice presente, consulta sin `N+1`, tiempo de espera configurado |
| Mantenibilidad | Sin duplicación, métodos cortos, prueba de arquitectura en verde |
| Testabilidad | Reglas en `domain` probadas sin Spring; reloj inyectable |
| Observabilidad | `code` y `requestId` en el log del error, sin datos personales |
| Compatibilidad de contrato | Cambio aditivo, OpenAPI actualizado y aviso a Frontend |
