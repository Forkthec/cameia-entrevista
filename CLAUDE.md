# cameia-entrevista

## 1. Servicio

CAMEIA ofrece práctica y simulación de entrevistas virtuales para preparar entrevistas de trabajo, con foco en las preguntas de comportamiento. Este repositorio es el microservicio de Entrevista: gestiona las sesiones de práctica para vacantes de empleo, es decir, su configuración, su estado, sus preguntas y sus turnos.

- Soporta dos modos de sesión: **entreno** y **simulación**.
- Produce el **perfil de debilidades** como reporte derivado de entrevistas ya realizadas; no es un tercer modo de sesión ([ADR 0003](docs/adr/0003-perfil-de-debilidades-como-reporte-derivado.md)). Su mecánica (qué datos lo alimentan, cuándo se genera y cómo se expone) se define en una spec cuando entre en alcance, y no se crea lógica de generación sin ella.
- Configura e inicia sesiones (modo, tono, personalidad, forma e idioma) asociadas a un `firebaseUid`.
- Construye el contexto conversacional (contexto profesional y vacante en texto libre) dentro de los límites de cuota y privacidad aprobados.
- Gestiona preguntas, respuestas y avance de turnos dentro de la sesión.
- Se integra con Perfil, Voz, Auditoría y proveedores LLM mediante contratos explícitos.

No persiste cuentas, perfiles maestros ni audio o video de forma permanente. No implementa autenticación ni pagos, y no custodia contraseñas ni credenciales.

Las reglas comunes de Backend están en [docs/estandar-backend.md](docs/estandar-backend.md) y los principios no negociables, en [docs/constitution.md](docs/constitution.md).

## 2. Estructura y dependencias

Pila: Java 21, Spring Boot 4.1.1, Maven Wrapper y Spring AI para los proveedores LLM. El paquete base es `tech.cameia.entrevista` (`groupId` de Maven: `tech.cameia`).

Capas con Domain-Driven Design. Se crean subcarpetas solo con un motivo de cambio distinto.

```text
tech.cameia.entrevista
├── presentation
│   ├── controller          // adaptadores de entrada HTTP
│   ├── dto                 // request/response del contrato público
│   └── advice              // manejo de errores HTTP
├── application
│   ├── service             // casos de uso (orquestación, transacción)
│   └── command             // objetos de entrada de los casos de uso
├── domain
│   ├── model               // sesión, pregunta, turno, objetos de valor, enumerados
│   ├── service             // servicios de dominio
│   ├── policy              // reglas de dominio por modo (entreno y simulación)
│   ├── port                // interfaces que el dominio define y no implementa
│   ├── event               // eventos de dominio
│   └── exception           // excepciones de negocio
└── infrastructure
    ├── persistence
    │   ├── entity          // modelo JPA, distinto del modelo de dominio
    │   ├── repository      // Spring Data y adaptadores de los puertos
    │   └── mapper          // dominio <-> entidad
    ├── messaging
    │   ├── consumer        // @RabbitListener
    │   ├── publisher       // RabbitTemplate
    │   └── payload         // contratos de mensaje versionados
    ├── client              // WebClient hacia Perfil, Voz y Auditoría
    ├── ia                  // adaptadores de proveedores LLM (Spring AI)
    └── config              // configuración de Spring
```

Hoy el código tiene `presentation.controller` y `presentation.dto` (el endpoint de salud) y `infrastructure.config.documentation` (la configuración de OpenAPI); el resto del árbol es la estructura prevista.

**Regla de dependencias.** `domain` no importa nada de `presentation`, `application` ni `infrastructure`; `application` depende de `domain` solo por puertos; `infrastructure` implementa los puertos de `domain`. La prueba de arquitectura que debe vigilarla está pendiente (sección 10); mientras tanto la regla se comprueba en la revisión de cada PR.

## 3. Límites de confianza

El servicio solo debe aceptar peticiones autenticadas del API Gateway, con dos capas ([ADR 0002](docs/adr/0002-autenticacion-entre-gateway-y-entrevista.md)):

1. **IAM con OIDC en Cloud Run (base obligatoria).** El servicio se despliega con `--no-allow-unauthenticated`; solo la cuenta de servicio del Gateway tiene rol de invocador. El Gateway obtiene un token OIDC del servidor de metadatos de Cloud Run y lo agrega a cada petición en `Authorization: Bearer`, y Cloud Run lo valida antes de enrutar. No cambia la lógica de negocio: es configuración de infraestructura.
2. **VPC con ingreso interno (contexto sensible).** Encima de la capa 1: Entrevista procesa información sensible de la práctica (contexto profesional, prompts conversacionales y transcripciones), así que queda con ingreso `internal`, sin ruta pública, y el Gateway llega por un conector privado de la VPC. El Gateway sigue siendo el único punto de entrada público.

**Identidad de entrada.** El servicio recibirá del Gateway solo lo necesario para la autorización de negocio, nunca el JWT completo, en estos encabezados. Todavía ningún endpoint los lee: el único endpoint es `GET /health`, que no lleva identidad.

| Encabezado | Obligatorio | Contenido |
|---|---|---|
| `X-User-Id` | Sí | `firebaseUid` del usuario |
| `X-User-Email` | Sí | Correo del usuario |
| `X-User-Roles` | Sí | Roles del usuario |
| `X-Request-Id` | Sí | Identificador de la petición |
| `X-User-Plan` | No | Plan del usuario; no respalda ningún derecho ni cuota hasta que exista la spec de planes |
| `X-User-Email-Verified` | No | Si el correo está verificado |

No se confía en un encabezado enviado directamente por un cliente externo, y no se agregan campos derivados del JWT sin justificar su necesidad y documentar el contrato.

**Ruta sin identidad.** Solo `GET /health`: no lee ni modifica datos de una sesión. Una ruta nueva no se suma sin una spec que lo justifique.

## 4. Contrato y errores

- La versión va en la ruta: `/api/v1/...`. La ruta de salud, `GET /health`, es la excepción: no es contrato de negocio.
- Los nombres JSON van en `camelCase`.
- Los errores son `ProblemDetail` (RFC 9457) con `Content-Type: application/problem+json`. El formato y las reglas están en la [sección 6 del estándar](docs/estandar-backend.md#6-errores) y lo que el servicio emite hoy, en [docs/errores.md](docs/errores.md): todavía no tiene manejador de errores.
- Un fallo técnico se registra completo en el log y al cliente solo le llega un texto genérico.

**Documentación de la API.** OpenAPI en `http://localhost:8083/v3/api-docs` y Swagger UI en `http://localhost:8083/swagger-ui.html`. Las dos rutas se publican y se retiran juntas, porque el JSON ya expone el contrato completo. Solo se publican en desarrollo: `application.properties` las deja apagadas, el perfil `local` las enciende y el perfil `production` las apaga, de modo que en despliegue responden `404`. La variable `API_DOCS_ENABLED` invierte ese valor por defecto sin reconstruir la imagen. Decisión en el [ADR 0004](docs/adr/0004-exposicion-de-la-documentacion-de-la-api.md).

## 5. Datos

PostgreSQL 16 con JPA, con base y rol propios y `spring.jpa.open-in-view=false`. El servicio todavía no tiene entidades ni migraciones: la primera migración será Flyway, según la [sección 7 del estándar](docs/estandar-backend.md#7-base-de-datos).

## 6. Seguridad

- No registrar JWT, secretos, contraseñas, tokens de Firebase, prompts completos sensibles, CV, audio ni transcripciones.
- Los secretos de Firebase y de los proveedores LLM y de Voz viven solo en el gestor de secretos o en variables de entorno aprobadas, nunca en Git.
- La autenticidad de las peticiones del Gateway la garantizan IAM y el token OIDC (capa 1) y el ingreso interno de la VPC (capa 2); no se firma el payload en la aplicación.
- La autorización se aplica por endpoint; un rol no equivale a un permiso de negocio.
- Se exponen solo los endpoints de operación necesarios para la salud.
- Cada integración externa (Perfil, Voz, Auditoría y proveedores LLM) requiere pruebas de autenticidad, reintentos, manejo de errores e idempotencia antes de darla por completa.

## 7. Pruebas

- JUnit 5, con las pruebas bajo `src/test/java/tech/cameia/entrevista`: el arranque del contexto, la prueba del controlador de salud y las de configuración (interruptor de la documentación de la API y resolución del puerto).
- Las pruebas de persistencia y de integración usan PostgreSQL real en Testcontainers y puerto aleatorio. Testcontainers todavía no está declarado; está pendiente (sección 10).
- Las clases `*Test` las ejecuta Surefire. Las reglas de pruebas y de cobertura están en la [sección 9 del estándar](docs/estandar-backend.md#9-pruebas-y-cobertura).

## 8. Verificación

Verificación completa: `./mvnw.cmd clean verify`. Genera el informe de cobertura en `target/site/jacoco/index.html`.

- Pruebas solas: `./mvnw.cmd test`.
- Con Docker: `docker compose up --build -d` levanta Entrevista y PostgreSQL 16.
- Con la aplicación en `http://localhost:8083`: salud en `/health`, OpenAPI en `/v3/api-docs` y Swagger UI en `/swagger-ui.html` (con la documentación publicada, ver sección 4).
- Puertos locales: Gateway 8080, Cuentas 8081, Perfil 8082 y Entrevista 8083. En Cloud Run, `PORT` tiene prioridad sobre `SERVER_PORT` (`server.port=${PORT:${SERVER_PORT:8080}}`); decisión en el [ADR 0005](docs/adr/0005-reparto-de-puertos-y-resolucion-del-puerto.md).

## 9. Contribución

Rama, commit, tipos, título de PR, revisión y merge: rige [CONTRIBUTING.md](CONTRIBUTING.md). Lo que este repositorio añade:

- El título del PR lleva `[IA-ASISTIDO]` al final cuando hubo IA; el commit no lo lleva. Un commit asistido por IA lleva el trailer `Co-Authored-By` con el modelo.
- Una IA puede abrir un PR; nunca lo fusiona. El control humano lo marca la persona que revisa.
- Un PR cubre una pieza reconocible y no pasa de 1000 líneas entre agregadas y eliminadas.
- Antes del código hay una spec aprobada en `specs/CM-NNN-Descripcion/` (`spec.md`, `plan.md` y `tasks.md`); el flujo está en la sección 12 de [docs/estandar-backend.md](docs/estandar-backend.md).
- Las decisiones con peso humano se registran en [docs/bitacora-ia/](docs/bitacora-ia/README.md).

Las specs nuevas viven en `specs/CM-NNN-Descripcion/` con `Descripcion` en PascalCase; las carpetas de spec existentes con otro nombre no se renombran.

## 10. Pendientes

| Pendiente | Responsable | Qué bloquea |
|---|---|---|
| Máquina de estados de la sesión (estados, transiciones y reglas por modo) | Product Owner | Todo estado, transición o regla de modo: el servicio no los implementa sin una spec aprobada |
| Prueba de arquitectura (`LayeredArchitectureTest` con ArchUnit) | Backend (CM-283) | Vigilar la regla de dependencias de la sección 2 |
| Testcontainers para las pruebas con base de datos | Backend (CM-283) | Pruebas de persistencia y de integración |
| Manejador de errores con `code` y `requestId` | Backend (CM-283) | El catálogo de [docs/errores.md](docs/errores.md) |
| Perfil `prod`, variable `API_DOCUMENTATION_ENABLED` y puerto único 8083 (hoy: perfil `production`, `API_DOCS_ENABLED` y puerto interno 8080) | Backend (CM-283) y DevOps | Retirar el perfil `production` |
