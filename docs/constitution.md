# Constitución de cameia-entrevista

Principios no negociables. Toda spec y todo PR los cumple; en conflicto, esta lista prevalece. El detalle vive en [CLAUDE.md](CLAUDE.md) y [guidelines.md](guidelines.md).

1. **Stack:** Java 21 + Spring Boot 4.1.1 + Maven Wrapper + PostgreSQL 16; subir una versión mayor exige spec aprobada. → `pom.xml`.
2. **Alcance y datos:** solo configuración, estado, preguntas, turnos y contexto conversacional de la sesión de entrevista por `firebaseUid`/`sessionId`; base y rol propios, sin FKs externas; nunca contraseñas ni datos de pago. → revisión de PR y de esquema.
3. **Capas DDD:** `domain` sin dependencias de otras capas y `application` → `domain` solo por puertos. → `LayeredArchitectureTest` en verde.
4. **Entrada de confianza:** solo el API Gateway (IAM+OIDC, VPC interna); contrato mínimo `firebase_uid, email, roles, request_id`; sin campos nuevos del JWT sin contrato documentado. → config de despliegue + DTO de entrada.
5. **Datos sensibles:** secretos de Firebase y de proveedores LLM/Voz solo en gestor de secretos o variables de entorno, nunca en Git; logs SLF4J en español sin JWT, tokens, contraseñas, prompts sensibles, CV, audio ni transcripciones. → escaneo de secretos en CI + `grep` de `System.out`.
6. **Spec-Driven:** toda capacidad nace de una spec aprobada en `specs/`; prohibido crear implementación si no está en una spec. Aplica en particular a la máquina de estados de sesión y a las reglas de los modos entreno/simulación, aún no definidas. → revisión de PR contra la spec.
7. **Puerta de ambigüedad:** detenerse ante ambigüedad de seguridad, contrato, datos, permisos o arquitectura; máx. 6 preguntas; registrar en [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md) si el impacto es alto. → entrada en el registro.
8. **Tests:** JUnit 5; cada spec con casos de éxito y de error; integraciones externas con pruebas de autenticidad, reintentos, errores e idempotencia; integración con Testcontainers y puerto aleatorio. → `./mvnw.cmd test`.
9. **Verde antes de PR:** `./mvnw.cmd test` y `./mvnw.cmd clean package` pasan localmente. → ejecución de ambos comandos.
10. **Tamaño de cambio:** diff agregado + eliminado ≤ 1000 líneas por solicitud; si se supera, dividir en incrementos revisables y esperar confirmación. → `git diff --stat`.
11. **Idioma y nombres:** identificadores en inglés; documentación, Javadoc, OpenAPI, logs, excepciones, commits y PRs en español; tablas y columnas en `snake_case` español; sin abreviaturas. → revisión de PR.
12. **Contribución:** ramas `<tipo>/CM-<numero>-<descripcion-kebab-case>`; PR revisado por otra persona; `develop` por Squash y `main` por Merge commit; spec y documentación actualizadas en el mismo PR. → reglas de rama + checklist de PR.
