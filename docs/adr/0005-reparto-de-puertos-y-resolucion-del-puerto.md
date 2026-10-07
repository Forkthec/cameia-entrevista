# ADR 0005 · Reparto de puertos y resolución del puerto

## Estado

Aceptada. Decisión de Product Owner, 14 de septiembre de 2026.

## Contexto

Cloud Run inyecta la variable `PORT` y exige que el proceso escuche exactamente en ese puerto. Además, el Gateway enruta a Entrevista por el puerto 8083 mientras este repositorio se publicaba en el 8081, que ya usa Cuentas. DevOps pidió resolver ambos puntos (CM-132 y CM-133, puntos 1 y 2, 11 de septiembre de 2026).

## Decisión

`PORT` manda en despliegue y 8083 es el puerto de Entrevista en local.

- `server.port=${PORT:${SERVER_PORT:8080}}`: primero `PORT`; sin él, `SERVER_PORT`; sin ninguno de los dos, 8080. Es la misma forma que usa Perfil.
- Reparto de puertos en local: 8080 Gateway, 8081 Cuentas, 8082 Perfil y 8083 Entrevista. Se corrige este repositorio y el Gateway no se toca.
- El puerto interno del contenedor sigue siendo 8080: `SERVER_PORT` es el puerto interno dentro del compose y el del host en la publicación, y ambos usos son coherentes.

## Consecuencias

- Es solo configuración (`application.properties`, `.env.example`, `docker-compose.yml` y `Dockerfile`): ningún endpoint ni regla de negocio cambia.
- El reparto de puertos es un contrato de despliegue local entre repositorios, no una capacidad del dominio.
