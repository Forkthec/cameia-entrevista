\# cameia-entrevista



Microservicio central de Entrevista de CAMEIA. Gestiona la configuración, estado, preguntas y turnos de las sesiones de práctica.



> \*\*Estado:\*\* repositorio creado para el Sprint 1. La base técnica corresponde a \[CM-101](https://f0rktech.atlassian.net/browse/CM-101); las evaluaciones, voz y proveedores externos se incorporan solo mediante historias y evidencia verificable.



\## Alcance del Sprint 1



\- Investigación de personalización conversacional: \[CM-21](https://f0rktech.atlassian.net/browse/CM-21).

\- Contexto profesional de sesión: \[CM-22](https://f0rktech.atlassian.net/browse/CM-22).

\- Modo, tono, personalidad, forma e idioma: \[CM-23](https://f0rktech.atlassian.net/browse/CM-23).

\- Inicialización en estado `CONFIGURADA`: \[CM-24](https://f0rktech.atlassian.net/browse/CM-24).

\- Planificación de preguntas y transición a `EN\_CURSO`: \[CM-25](https://f0rktech.atlassian.net/browse/CM-25).

\- Spike de contexto conversacional: \[CM-26](https://f0rktech.atlassian.net/browse/CM-26).

\- Envío de respuesta y avance de turno: \[CM-27](https://f0rktech.atlassian.net/browse/CM-27).



Empleo, video y el flujo completo de reporte permanecen fuera del alcance salvo cambio aprobado en Jira.



\## Responsabilidades



\- Configurar e iniciar sesiones de entrevista.

\- Mantener la máquina de estados y las invariantes de cada modo.

\- Gestionar preguntas, respuestas y avance de turnos.

\- Construir contexto conversacional dentro de límites aprobados.

\- Integrarse con Perfil, Voz, Auditoría y proveedores LLM mediante contratos explícitos.



No persiste cuentas, perfiles maestros ni archivos de audio/video de forma permanente.



\## Contexto arquitectónico



```mermaid

flowchart LR

&#x20;   G\[cameia-gateway] --> E\[cameia-entrevista]

&#x20;   E --> DB\[(PostgreSQL Entrevista)]

&#x20;   E -. contexto/cuota .-> R\[RabbitMQ]

&#x20;   E -. voz prevista .-> V\[cameia-voz]

&#x20;   E -. conversación prevista .-> L\[Proveedores LLM]

&#x20;   E -. consumo previsto .-> A\[cameia-auditoria]

```



\## Tecnología prevista



| Elemento | Línea base |

|---|---|

| Lenguaje | Java 21 |

| Framework | Spring Boot 4.1.1 |

| Build | Maven; wrapper pendiente de confirmar |

| Persistencia | PostgreSQL 16, base/rol propios |

| Mensajería | RabbitMQ para réplicas y consumo cuando sea aprobado |

| Ejecución objetivo | Servicio HTTP y consumidor en el mismo repositorio/imagen |



\## Reglas de dominio relevantes



\- Estados base: `CONFIGURADA`, `EN\_CURSO`, `PAUSADA`, `FINALIZADA` y `ABANDONADA`, sujetos a validación en código.

\- La configuración no debe cambiar silenciosamente después de iniciar la sesión.

\- El contexto de vacante del MVP es texto libre; no depende del servicio Post-MVP de Empleo.

\- Las llamadas externas deben respetar timeout, cuota, privacidad y trazabilidad.



\## Ejecución local



```text

Instalación: pendiente de confirmar en CM-101

Pruebas: pendiente de confirmar en CM-101

Build: pendiente de confirmar en CM-101

Inicio: pendiente de confirmar en CM-101

Health check: pendiente de confirmar en CM-101

```



\## Configuración, seguridad y calidad



\- No guardar prompts completos sensibles, CV, audio, transcripciones, tokens ni `.env` en Git.

\- Probar transiciones válidas e inválidas de la sesión.

\- Probar reintentos, timeouts y fallos de proveedores cuando esas integraciones existan.

\- Conservar evidencia de consumo y trazabilidad sin datos personales.

\- Activar CI únicamente con comandos reales.



\## Contribución



\- `main` es estable y solo recibe promociones `develop → main` mediante Merge commit.

\- `develop` integra ramas `CM-<numero>-<descripcion-kebab-case>` mediante Squash.

\- Todo cambio ordinario entra mediante PR y revisión distinta del autor; la rama `CM-\*` se elimina después.



\## Cuándo actualizar este README



Actualizarlo en el mismo PR que cambie propósito, stack, comandos, variables, endpoints, estados, contratos, proveedores, eventos, persistencia, pruebas, despliegue o responsables. Si no aplica, justificarlo en el PR.

