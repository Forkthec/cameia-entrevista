# cameia-entrevista

## Contexto del proyecto

CAMEIA ofrece práctica y simulación de entrevistas virtuales para preparar entrevistas de trabajo. Aunque puede incluir preguntas técnicas, el foco principal son las preguntas de comportamiento.

Este repositorio contiene el microservicio central de Entrevista dentro de una arquitectura de microservicios. Su responsabilidad es gestionar toda la lógica de las sesiones de práctica para vacantes de empleo: configuración, estado, preguntas y turnos. Soporta dos modos de sesión — **entreno** y **simulación** — y produce como reporte derivado el **perfil de debilidades**, que no es un tercer modo sino un análisis generado a partir de entrevistas ya realizadas (ver [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md), A-002).

**La máquina de estados de la sesión (estados, transiciones y reglas específicas de cada modo) aún no está definida a nivel técnico.** Se especificará en un plan técnico o spec futura antes de implementarse — ver A-003 en [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md). No inventar estados, transiciones ni reglas de modo sin esa spec aprobada.

## Responsabilidad del servicio

- Configurar e iniciar sesiones de entrevista (modo, tono, personalidad, forma e idioma) asociadas a un `firebaseUid`.
- Mantener el estado de cada sesión y validar sus transiciones según la máquina de estados que se defina en spec.
- Gestionar preguntas, respuestas y avance de turnos dentro de la sesión.
- Construir contexto conversacional (contexto profesional, vacante en texto libre) dentro de límites de cuota y privacidad aprobados.
- Integrarse con Perfil, Voz, Auditoría y proveedores LLM mediante contratos explícitos.
- Generar el perfil de debilidades como reporte derivado de entrevistas finalizadas, cuando esa capacidad entre en alcance vía spec.

No persiste cuentas, perfiles maestros ni archivos de audio/video de forma permanente. No implementa autenticación ni pagos.

## Estructura de carpetas (Estilo DDD)

Entrevista sigue el patrón de capas con Domain-Driven Design. Las carpetas están predefinidas para garantizar cohesión alta y acoplamiento bajo. Crear subcarpetas solo si existe evidencia de un motivo de cambio distinto.

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
│   ├── model               // sesión, pregunta, turno, objetos de valor, enums
│   ├── service              // servicios de dominio
│   ├── policy               // reglas de dominio por modo (entreno/simulación)
│   ├── port                 // interfaces que el dominio define y NO implementa
│   ├── event                // eventos de dominio
│   └── exception            // excepciones de negocio
└── infrastructure
    ├── persistence
    │   ├── entity           // modelo JPA — NO es el modelo de dominio
    │   ├── repository        // Spring Data + adaptadores de los puertos
    │   └── mapper             // dominio <-> entity
    ├── messaging
    │   ├── consumer           // @RabbitListener
    │   ├── publisher          // RabbitTemplate
    │   └── payload             // contratos de mensaje versionados
    ├── client                 // WebClient hacia Perfil, Voz y Auditoría
    ├── ia                     // adaptadores de proveedores LLM (Spring AI)
    └── config                  // configuración de Spring
```

**Regla de dependencias:** `domain` no importa nada de `presentation`, `application` ni `infrastructure`. `application` depende de `domain` a través de puertos. `infrastructure` implementa los puertos que `domain` define. Esta regla se valida automáticamente con la prueba ArchUnit `LayeredArchitectureTest`; toda nueva clase debe pasarla.

## Límites de entrada y confianza

El servicio solo debe aceptar peticiones autenticadas del API Gateway. La estrategia tiene dos capas, ambas documentadas en [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md) (A-001):

### Capa 1: IAM + OIDC en Cloud Run (base obligatoria)
- El microservicio se despliega con `--no-allow-unauthenticated`.
- Solo la cuenta de servicio del API Gateway tiene rol de invocador (binding de IAM).
- El Gateway obtiene un token OIDC del metadata server de Cloud Run y lo agrega a cada petición saliente en el header `Authorization: Bearer <token>`.
- Cloud Run valida el token antes de enrutar la petición al microservicio.
- No implica cambios en la lógica de negocio: es configuración de infraestructura.

### Capa 2: VPC e ingress interno
- Se agrega encima de la Capa 1. Aplica a Entrevista porque el servicio procesa información sensible de la sesión de práctica: contexto profesional, prompts conversacionales y transcripciones.
- El microservicio queda con `ingress: internal`, sin ruta pública desde internet.
- El Gateway accede al microservicio por un conector privado dentro de la VPC.
- El Gateway sigue siendo público, como punto de entrada único para React.

**Contrato de entrada:**

Entrevista recibe del Gateway únicamente los datos necesarios para la autorización de negocio, nunca el JWT completo:

- **Obligatorios:** `firebase_uid`, `email`, `roles`, `request_id`.
- **Opcionales:** `display_name`, `correlation_id`, `issued_at`.

No agregar campos derivados del JWT sin justificar su necesidad y documentar su contrato. No confiar en headers enviados directamente por clientes externos.

## Reglas de seguridad

- No registrar JWT, secretos, contraseñas, tokens de Firebase, prompts completos sensibles, CV, audio ni transcripciones.
- Guardar secretos de Firebase y de proveedores LLM/Voz únicamente en el gestor de secretos o variables de entorno aprobadas; nunca en Git.
- La autenticidad de peticiones del Gateway se garantiza mediante IAM y tokens OIDC (Capa 1) y el ingress interno de la VPC (Capa 2). No es necesario firmar el payload en la aplicación.
- Aplicar autorización por endpoint probado y no asumir que `roles` equivale automáticamente a permisos de negocio.
- Exponer solo los endpoints de Actuator necesarios para salud.
- Cada integración externa (Perfil, Voz, Auditoría, proveedores LLM) requiere pruebas de autenticidad, reintentos, manejo de errores e idempotencia antes de considerarla completa.

## Constitución

Los principios no negociables del proyecto (stack, calidad, tests y límites) están consolidados en [constitution.md](constitution.md). Toda spec y todo PR los cumple; en caso de conflicto, esa lista prevalece sobre el resto de este documento.

## Metodología Spec-Driven Development

El repositorio sigue Spec-Driven Development clásico. Las especificaciones viven bajo `specs/`, organizadas por funcionalidad. Antes de implementar una capacidad nueva:

1. Crear o actualizar la spec con contexto, alcance, requisitos, reglas, casos de éxito y casos de error.
2. Registrar las decisiones y contratos relevantes.
3. Implementar únicamente lo respaldado por una spec aprobada.
4. Añadir pruebas que demuestren los escenarios de la spec.
5. Actualizar la documentación si cambian contratos, configuración, datos, eventos o comandos.

Esto aplica en particular a la máquina de estados de sesión y a las reglas detalladas de los modos entreno/simulación: no implementar ninguna de las dos sin una spec aprobada que las defina (ver A-003 en [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md)).

No crear carpetas o especificaciones ficticias para aparentar que una decisión está tomada.

## Restricción de ambigüedades

Si una petición contiene una ambigüedad que puede afectar seguridad, contrato, datos, permisos, arquitectura o comportamiento observable, el agente debe detenerse antes de editar. Debe formular preguntas concretas y resolverlas ahí mismo con máximo 6 preguntas.

Toda ambigüedad cuya resolución tenga impacto en la arquitectura, sea de alto impacto en seguridad, o comprometa una buena práctica (por ejemplo, omitir pruebas unitarias) debe quedar registrada en [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md) con la pregunta, la decisión adoptada, el impacto y la especificación relacionada. Ambigüedades menores, sin ese impacto, pueden resolverse en la conversación sin dejar constancia formal allí.

No asumir defaults silenciosos en decisiones críticas. Una tarea puede continuar solo si las partes ambiguas son irrelevantes para el cambio o si ya existe una decisión documentada y aprobada.

## Límite de tamaño de cambios

Se prohíben cambios cuyo diff total agregado y eliminado supere 1000 líneas por solicitud. Antes de editar, estimar el tamaño. Si se supera el umbral:

- Detener la implementación.
- Informar al usuario que debe revisar cada cambio.
- Recomendar dividir la petición en incrementos pequeños, por responsabilidad o por spec.
- Proponer un orden de modularización y esperar confirmación.

No usar este límite para ocultar cambios relacionados en commits separados: cada incremento debe ser revisable y funcional.

## Reglas de nombrado

- Clases e interfaces: `PascalCase`.
- Métodos, campos y variables: `camelCase`.
- Constantes: `UPPER_SNAKE_CASE`.
- Paquetes: `lowercase`, sin guiones bajos, sin plurales inventados.
- **Sin abreviaturas:** `sesion`, no `ses`; `pregunta`, no `preg`; `contextoProfesional`, no `ctxProf`. Los identificadores cortos de los diagramas son etiquetas del dibujo, no nombres de clase.

## Convenciones de idioma

**Regla fundamental:** El compilador lee código en inglés; las personas leen documentación en español.

| Elemento | Idioma | Ejemplo |
|---|---|---|
| Paquetes, clases, métodos, variables | **Inglés** | `Session`, `createSession()`, `firebaseUid` |
| Constantes | **Inglés** `UPPER_SNAKE` | `MAX_RETRY_ATTEMPTS`, `LLM_TIMEOUT_MS` |
| Nombres de tablas/columnas | **Español** `snake_case` | `sesion`, `fecha_creacion`, `id_firebase` |
| **Comentarios de código (Javadoc)** | **Español** | Ver sección "Documentación de código" |
| **Descripciones OpenAPI** | **Español** | Ver sección "Documentación de código" |
| Mensajes de log | **Español**, sin datos sensibles | `logger.info("Sesión de entrevista creada")` |
| Excepciones (mensaje) | **Español** | `throw new SessionNotFoundException("Sesión no encontrada")` |
| Commits y PRs | **Español** | `git commit -m "CM-25: Implementar planificación de preguntas"` |

**Justificación:** El código convive con compiladores, intérpretes y dependencias internacionales; el inglés es el estándar. La documentación la lee el equipo en un contexto donde el español es natural.

## Documentación de código

### Javadoc en español

Todo método público en `domain`, `application` y los adaptadores de `infrastructure` lleva Javadoc en español con descripción clara de qué hace, parámetros, retorno y excepciones.

### OpenAPI en español

Cada endpoint expone su contrato mediante OpenAPI 3.0.

**Acceso a documentación:**
- JSON OpenAPI: `http://localhost:8080/v3/api-docs`
- Interfaz Swagger UI: `http://localhost:8080/swagger-ui.html`

## Convenciones técnicas

**Stack base:**
- Java 21, Spring Boot 4.1.1 y Maven Wrapper.
- Spring AI para la integración con proveedores LLM.
- Sigue las indicaciones de [guidelines.md](guidelines.md).
- Usa Javadoc en español; código y método/clase en inglés (ver sección "Convenciones de idioma").
- Usa JUnit 5 para pruebas unitarias.

## Verificación de cambios de código

1. Pruebas Unitarias
```powershell
./mvnw.cmd test
```
2. Limpieza y construcción del proyecto
```powershell
./mvnw.cmd clean package
```

## Flujo de contribución

- Usar ramas `<tipo>/CM-<numero>-<descripcion-kebab-case>` (tipos: `feat`, `fix`, `test`, `docs`, `refactor`, `perf`, `build`, `ci`, `chore`).
- Todo cambio ordinario entra mediante PR y revisión de una persona distinta del autor.
- Mantener `main` estable y promover cambios desde `develop` mediante Merge commit.
- Integrar ramas de trabajo en `develop` mediante Squash.
- Actualizar la spec y este documento en el mismo PR cuando cambien reglas o contratos.
