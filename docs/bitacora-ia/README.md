# Bitácora de IA

Esta carpeta guarda la **entrada** de cada decisión en la que una persona cambió el rumbo de una propuesta de la IA (alcance, arquitectura, seguridad, costo, calidad o proceso): el prompt armado con rol, contexto, tarea, condiciones y formato. La **salida** es la spec del repositorio (`specs/CM-NNN-*/spec.md`, sección de decisiones, con la decisión humana, el porqué y las alternativas descartadas); la bitácora la enlaza y no la copia.

Cada entrada es un archivo `NN_tema_prompt.md`, con `NN` consecutivo.

| Entrada | Decisión | Salida |
|---|---|---|
| [01_alinear-estandar-backend_prompt.md](01_alinear-estandar-backend_prompt.md) | Alinear los documentos y el estándar de los cuatro repositorios de Backend | [Spec de CM-283](../../specs/CM-283-AlinearEstandar/spec.md) |
| [02_cliente-gemini-cm-312_prompt.md](02_cliente-gemini-cm-312_prompt.md) | Construir el cliente de Gemini con Spring AI, el timeout configurable y los errores sembrados, ajustando el diagrama C3 propuesto y la coordinación con Backend | [Spec de CM-312](../../specs/CM-312-ClienteGemini/spec.md) |
| [03_motor-base-entrevista_prompt.md](03_motor-base-entrevista_prompt.md) | Esquema inicial, estados de la sesión y del turno, manejador de errores completo e identidad del Gateway; la base admite `ENTRENO` aunque se recomendó solo `SIMULACION` | [Spec de CM-311](../../specs/CM-311-MotorBaseEntrevista/spec.md) |
| [04_dueno-en-puertos-y-413_prompt.md](04_dueno-en-puertos-y-413_prompt.md) | El dueño entra a los puertos de sesión ahora, no en cada endpoint; el multipart de más de 10 MB responde 413 con código propio; el 400 del contenedor conserva su código de ruta | [Spec de CM-311](../../specs/CM-311-MotorBaseEntrevista/spec.md), decisiones P25 y P26 |
| [05_catalogo-personalizacion_prompt.md](05_catalogo-personalizacion_prompt.md) | Forma del contrato del catálogo de personalización (cuatro listas de `{value, available}`), identidad obligatoria, sin etiquetas ni preselección ni caché, y la disponibilidad como constante del dominio | [Spec de CM-204](../../specs/CM-204-CatalogoPersonalizacion/spec.md), decisiones D-CAT-01 a D-CAT-07 |
| [06_replica-del-perfil_prompt.md](06_replica-del-perfil_prompt.md) | Copia local del perfil profesional alimentada por el evento de Perfil: sin `embedding`, estado tal como llega, dos `jsonb`, pruebas con RabbitMQ real y rechazo del cambio de dueño | [Spec de CM-209](../../specs/CM-209-ReplicaPerfilProfesional/spec.md), decisiones D-RPL-01 a D-RPL-07 |
