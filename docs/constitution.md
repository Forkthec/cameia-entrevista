# Constitución de cameia-entrevista

Principios no negociables. Toda spec y todo PR los cumple. Si dos documentos chocan, rige el orden de la [sección 1 del estándar](estandar-backend.md#1-alcance-y-precedencia). El detalle vive en el [CLAUDE.md](../CLAUDE.md) y en el [estándar](estandar-backend.md).

1. **Stack:** Java 21, Spring Boot 4.1.1 y Maven Wrapper, más PostgreSQL 16; subir una versión mayor exige spec aprobada. → `pom.xml`.
2. **Alcance y datos:** solo lo que es del servicio; base y rol propios y sin claves foráneas hacia otros servicios. → revisión del PR y del esquema.
3. **Capas y dependencias:** las reglas de la sección 3 del estándar. → prueba de arquitectura en verde.
4. **Entrada de confianza:** solo se acepta el tráfico del Gateway; la identidad llega en los encabezados `X-User-*` y nunca del cuerpo ni de la ruta, salvo en las rutas sin identidad que el `CLAUDE.md` declara con su spec. → configuración de despliegue y pruebas del controlador.
5. **Datos sensibles:** secretos solo por variable de entorno o gestor de secretos; nada sensible en logs, URL ni errores. → escaneo de secretos en CI y búsqueda de `System.out`.
6. **Errores:** formato común con `code` y `requestId`; cada error previsible con código, estado, mensaje y prueba. → `docs/errores.md` y pruebas del manejador.
7. **Base de datos:** solo por migración; el esquema repite lo que valida el código. → migración probada con Testcontainers.
8. **Desarrollo guiado por especificación:** nada se implementa sin spec aprobada. → revisión del PR contra la spec.
9. **Puerta de ambigüedad:** ante una ambigüedad de seguridad, contrato, datos o arquitectura se detiene el trabajo y se pregunta (hasta 6 preguntas por ronda). → decisión en la spec.
10. **Pruebas:** JUnit 5, un caso por camino y por rama, PostgreSQL real con Testcontainers y puerto aleatorio, cobertura de lo nuevo ≥ 90 %. → informe de JaCoCo.
11. **Verde antes del PR:** `./mvnw.cmd clean verify` pasa. → salida del comando.
12. **Tamaño del cambio:** un PR no pasa de 1000 líneas entre agregadas y eliminadas. → `git diff --shortstat`.
13. **Idioma y nombres:** identificadores en inglés y documentación en español. → revisión del PR.
14. **Documentación formal:** OpenAPI, Javadoc y comentarios completos. → revisión del PR y OpenAPI generado.
15. **Contribución:** rige `CONTRIBUTING.md`; la revisión la hace una persona distinta del autor y el merge siempre lo hace una persona. → reglas de rama y validadores de PR.
16. **Spec antes de la máquina de estados:** no se implementan estados, transiciones ni reglas de modo sin una spec aprobada. → revisión del PR contra la spec.
17. **Datos de la sesión:** el contexto profesional, los prompts y las transcripciones no se registran en logs ni se conservan fuera de la sesión. → revisión del PR y pruebas de logs.
