# Pruebas de API de Entrevista (Postman y Newman)

Esta carpeta trae la colección de Postman del microservicio de Entrevista y el entorno local con el que se corre.

| Archivo | Contenido |
|---|---|
| `cameia-entrevista.postman_collection.json` | Colección v2.1: carpeta `Salud` (1 petición) y carpeta `Errores del framework` (3 peticiones) |
| `local.postman_environment.json` | Entorno local: `baseUrl` (`http://localhost:8083`, el puerto de `SERVER_PORT`). No lleva secretos |

## Qué verifica

| # | Petición | Resultado esperado |
|---|---|---|
| 1 | `GET /health` con `X-Request-Id: postman-1` | 200, cuerpo `{"status":"UP"}` y el mismo `X-Request-Id: postman-1` en la respuesta |
| 2 | `GET /ruta-que-no-existe` | 404 `ROUTE_NOT_FOUND` |
| 3 | `POST /health` | 405 `METHOD_NOT_ALLOWED`, con `Allow` que incluye `GET` |
| 4 | `GET /health` con `Accept: application/xml` | 406 `MEDIA_TYPE_NOT_ACCEPTABLE` |

Cada error comprueba además el estado, el `code`, un `requestId` presente, un `Content-Type` con `application/problem+json` y `charset=UTF-8`, y un cuerpo sin las palabras `trace` ni `exception`.

## Cómo correrla

1. Levantar la base (necesita Docker y un `.env` local copiado de `.env.example`, con `DB_PASSWORD` definido):

   ```
   docker compose up -d entrevista-db
   ```

2. Empaquetar y arrancar el servicio contra esa base. Las cinco propiedades `cameia.llm.*` son obligatorias; para estas pruebas basta cualquier valor, porque ninguna petición de la colección llama al modelo:

   ```
   ./mvnw.cmd -B -ntp clean package -DskipTests
   java -jar target/entrevista-0.0.1-SNAPSHOT.jar
   ```

3. Correr la colección:

   ```
   npx newman run postman/cameia-entrevista.postman_collection.json -e postman/local.postman_environment.json
   ```

Para guardar el informe en formato JUnit, agregar `--reporters cli,junit --reporter-junit-export <ruta>/newman.xml`.
