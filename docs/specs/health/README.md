# Spec: Endpoint de salud (`GET /health`)

- Identificador: CM-101
- Estado: Aprobada
- Responsable: Juan Vela
- Fecha: 2026-09-08

## Contexto

El microservicio de Entrevista se despliega en Cloud Run detrás del API Gateway. La
plataforma necesita una señal simple y barata para saber si la instancia está viva y
puede recibir tráfico, sin arrastrar la lógica de negocio ni el contexto de sesión.

Mientras no se incorpora Spring Boot Actuator, esta spec define un endpoint propio y
mínimo en la capa `presentation`, además de servir como primer caso que ejercita la
documentación OpenAPI derivada de Javadoc (ver "Documentación").

## Alcance

Incluye:

- Un endpoint HTTP `GET /health` que responde `200 OK` con un objeto JSON.
- Un DTO de respuesta `HealthResponse` en el contrato público.
- Metadatos generales del documento OpenAPI (`OpenAPIConfiguration`).
- Cableado de therapi-runtime-javadoc para que las descripciones de OpenAPI provengan
  del Javadoc.

No incluye:

- Comprobación de dependencias externas (base de datos, mensajería, proveedores LLM).
- Endpoints de Actuator ni métricas.
- Autenticación o autorización dentro de la aplicación (la garantiza la frontera de
  infraestructura descrita en A-001).

## Contrato

### Petición

- Método y ruta: `GET /health`.
- Sin cuerpo, sin cabeceras obligatorias, **sin parámetros de consulta**.

### Respuesta de éxito

- Código: `200 OK`.
- `Content-Type: application/json`.
- Cuerpo:

  ```json
  { "status": "UP" }
  ```

  - `status` (string): estado del servicio. Valor `"UP"` cuando la aplicación responde.

## Reglas

- R1: El endpoint no realiza comprobaciones de dependencias externas; solo confirma que
  la aplicación responde.
- R2: El endpoint no acepta parámetros de consulta; los parámetros desconocidos se
  ignoran y la respuesta sigue siendo `200 OK`.
- R3: La respuesta es un objeto JSON de nivel superior (nunca un valor plano) para
  permitir extensión futura sin romper el contrato.
- R4: El endpoint no registra datos de la petición ni información sensible.

## Casos de éxito

- CE1: `GET /health` sin parámetros devuelve `200 OK` con `{"status":"UP"}` y
  `Content-Type` compatible con `application/json`.
- CE2: `GET /health?cualquier=valor` devuelve `200 OK` con `{"status":"UP"}` (los
  parámetros extra no afectan la respuesta).

## Casos de error

- CN1: Un método distinto de `GET` sobre `/health` (por ejemplo `POST`) devuelve
  `405 Method Not Allowed` (comportamiento por defecto de Spring MVC).
- CN2: Una ruta distinta de `/health` devuelve `404 Not Found`.

## Documentación

- OpenAPI JSON: `http://localhost:8080/v3/api-docs` (generado por springdoc).
- Interfaz de referencia (Swagger UI): `http://localhost:8080/swagger-ui.html`, que
  consume el JSON anterior (`springdoc.swagger-ui.url=/v3/api-docs`).
- Las descripciones de la operación y del esquema `HealthResponse` se generan a partir
  del Javadoc en español de `HealthController` y `HealthResponse`. El puente lo aporta
  `therapi-runtime-javadoc` (dependencia en runtime) junto con
  `therapi-runtime-javadoc-scribe` (procesador de anotaciones en `maven-compiler-plugin`).

## Pruebas

- `HealthControllerTest` (`@WebMvcTest`): cubre CE1 y CE2.
