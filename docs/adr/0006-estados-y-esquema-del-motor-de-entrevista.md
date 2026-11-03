# ADR 0006 · Estados y esquema del motor de la entrevista

## Estado

Aceptada. Decisión de Backend, 17 al 22 de octubre de 2026.

## Contexto

La entrevista necesita una sesión con un estado, preguntas y turnos que avanzan, y una forma de no duplicar solicitudes, antes de que existan los endpoints que las usan. Había que fijar de una vez las máquinas de estados, el esquema `microentrevista` con sus restricciones y cómo responde el servicio cuando algo de eso falla.

## Decisión

- **Estados de la sesión.** `CONFIGURADA → EN_CURSO`; `EN_CURSO → ABANDONADA` o `EVALUANDO`; `EVALUANDO → FINALIZADA`. Cualquier otro paso lanza `SESSION_TRANSITION_NOT_ALLOWED` antes de modificar nada. Los estados terminales son `ABANDONADA` y `FINALIZADA`.
- **Estados del turno.** `PENDING → PROCESSING`; `PROCESSING → COMPLETED` o `RETRYING`; `RETRYING → PROCESSING`. Son los de la historia técnica HT-01, que es más reciente que el diagrama; las historias de reintento y de seguimiento agregan lo suyo.
- **Borrar no es un estado.** Solo se borra una sesión `CONFIGURADA`, con una sentencia condicionada al estado (`DELETE … WHERE estado = 'CONFIGURADA'`): si otra transacción ya la inició, no se borra.
- **Nombres.** Los enumerados del código van en inglés (`SessionStatus.CONFIGURED`) y la base guarda el valor del backlog (`CONFIGURADA`), con un convertidor por enumerado. Tablas y columnas, en español.
- **Esquema.** Esquema `microentrevista`, Flyway `V1`, `ddl-auto=validate`. Cada regla del código está repetida como restricción con nombre (`pk_`, `fk_`, `uq_`, `ck_`, `ix_`): una sola sesión `CONFIGURADA` o `EN_CURSO` por persona (índice único parcial), una sola respuesta definitiva y una sola retroalimentación por turno, y claves anti-duplicados únicas por persona y operación. La base admite la modalidad `ENTRENO`; el código la rechaza hasta que la historia de configuración lo decida.
- **Control de versiones.** `@Version` en sesión y turno. Una escritura con una versión vencida lanza `SESSION_CONFLICT`; una violación de un índice único lanza su error de negocio, nunca un 500.
- **Retroalimentación en `jsonb`.** El estudio de la historia de evaluación todavía no fija su forma; guardarla como documento evita una migración por cada ajuste.
- **Pregunta de 1 a 1000 caracteres, provisional.** El límite queda en la base como `CHECK` y se puede ampliar con una migración si el estudio de la historia de preguntas lo pide.
- **Errores.** Sin identidad (`X-User-Id` ausente, en blanco, de más de 128 caracteres o con caracteres de control, de formato o separadores de Unicode) responde `401 IDENTITY_REQUIRED`. Un cuerpo ilegible responde `422 REQUEST_BODY_INVALID_FORMAT`, como Cuentas y Perfil. Un `multipart/form-data` ilegible responde `415 REQUEST_MULTIPART_INVALID_FORMAT`, uno de más de 10 MB responde `413 REQUEST_PAYLOAD_TOO_LARGE` y lo que el contenedor reporta por `/error` o por la válvula de Tomcat sale en la forma común: `400 REQUEST_PATH_INVALID_FORMAT` (ruta con codificación inválida, encabezado mal formado, petición que no se pudo interpretar) y `404 ROUTE_NOT_FOUND`. Los códigos están en [errores.md](../errores.md).

## Consecuencias

- Los endpoints de sesión y de turno no necesitan decidir estados ni choques: lanzan las excepciones del dominio y el manejador de errores las traduce.
- El esquema no se puede contradecir desde el código sin que falle una prueba (`ColumnLengthConsistencyTest`, `InterviewSchemaMigrationTest`).
- Cambiar un estado o un límite es una migración nueva, no una edición de `V1`.

## Alternativas descartadas

- Estados del turno del diagrama original: más antiguos que HT-01.
- Un estado `BORRADA`: borrar es una operación, y un estado más habría obligado a filtrarlo en cada consulta.
- Valores en inglés en la base: contradice el vocabulario del backlog que leen producto y pruebas.
- `400` para el cuerpo ilegible o para la identidad ausente: los demás servicios usan `422` y `401`.
- Columnas sueltas para la retroalimentación: obligan a migrar con cada cambio de su forma.
