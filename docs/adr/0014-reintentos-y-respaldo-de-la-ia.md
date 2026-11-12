# ADR 0014 · Reintentos y respaldo de la IA

## Estado

Aceptada. Decisiones de Backend ratificadas por Paula Andrea Muñoz Delgado, 11 de noviembre de 2026.

## Contexto

Planificar las cinco preguntas y evaluar cada respuesta dependen de un modelo de lenguaje externo que a veces tarda, falla, devuelve una salida que no cumple el esquema o responde 429 (límite de uso). Un solo intento deja a la persona con un error que otro intento habría evitado. Además, la evaluación de una respuesta debe llegar en pocos segundos, y la planificación tiene un plazo mayor porque genera más texto.

## Decisión

- **Ciclo de hasta tres intentos** por operación (planificar o evaluar): dos con el proveedor principal (Gemini) y el tercero con el de respaldo (Qwen 3.8 27B en Groq). Cada intento queda como una fila de ejecución de IA con su número, proveedor, modelo, estado y código de error; hay a lo sumo una fila en curso por operación y el cambio de intento (cerrar la fallida y abrir la siguiente) ocurre en una sola transacción.
- **Elección del proveedor** (`AiRetryPolicy`, en el dominio):

| Historia del ciclo | Respaldo con llave | Intento siguiente |
|---|---|---|
| (vacía) | — | 1 · principal |
| 1 principal falla (no 429) | — | 2 · principal |
| 1 principal 429 | sí | 2 · respaldo |
| 1 principal 429 | no | fin (`LLM_LIMIT_REACHED`) |
| 1 y 2 principal fallan (no 429) | sí | 3 · respaldo |
| 1 y 2 principal fallan (no 429) | no | 3 · principal |
| 1 principal falla, 2 principal 429 | sí | 3 · respaldo |
| 1 principal falla, 2 principal 429 | no | fin (`LLM_LIMIT_REACHED`) |
| 1 principal 429, 2 respaldo falla (no 429) | sí | 3 · respaldo |
| 1 principal 429, 2 respaldo 429 | sí | fin (`LLM_LIMIT_REACHED`) |
| 3 intentos hechos | — | fin (causa del 3.º) |

- **D-224-1 · Cliente de Groq.** Starter `spring-ai-starter-model-openai`; el `ChatModel` de Groq se arma a mano, sin la autoconfiguración de OpenAI, para no chocar con el de Gemini. URL `https://api.groq.com/openai`, modelo `qwen/qwen3.8-27b`, `reasoning_effort: none` y respuesta con `json_schema` estricto. El respaldo recibe el mismo texto de prompt y el mismo tope de salida que el principal, más el esquema JSON de la operación.
- **D-224-2 · Esperas de 500 ms y 1 s** antes del segundo y del tercer intento, configurables. Peor caso: evaluar ≈ 10,5 s (3 × 3 s + 1,5 s); planificar ≈ 61,5 s (3 × 20 s + 1,5 s), dentro de los 120 s del Gateway.
- **D-224-3 · Sin llave de Groq el servicio arranca**, registra un aviso una sola vez y el tercer intento usa Gemini. Probar el respaldo real queda pendiente hasta que DevOps cargue la llave.
- **D-224-4 · Prueba literal de los 19,9 s.** Un servidor local responde a los 19.900 ms con corte de 20.000 ms, además de la versión escalada.
- **D-224-5 · Datos que recibe el respaldo y condiciones de uso de Groq (Paula, Product Owner, 12-nov-2026).** El respaldo recibe el mismo texto de prompt que el proveedor principal (contexto profesional, vacante y respuesta de la persona); se aprueba su uso en pruebas locales y en el despliegue con estas condiciones:
  - **Rol:** Groq actúa como encargado del tratamiento bajo su addendum de procesamiento de datos (`https://console.groq.com/docs/legal/customer-data-processing-addendum`, vigente desde el 15-oct-2025), que forma parte de su acuerdo de servicios.
  - **Retención:** se activa la retención cero (Zero Data Retention) en los controles de datos de la cuenta que emite la llave de despliegue. Sin ella, Groq puede registrar entradas y salidas hasta 30 días para depurar fallos o investigar abuso; con ella no retiene entradas ni salidas.
  - **Entrenamiento:** el acuerdo de servicios de Groq (§4.2) prohíbe usar entradas y salidas para entrenar o ajustar modelos sin permiso explícito del cliente; no se concede ese permiso.
  - **Ubicación:** Groq puede procesar en Estados Unidos y otros países; no se puede elegir región. La política de privacidad de CAMEIA informa que existe un segundo proveedor de IA fuera de Colombia.
  - **Subencargados:** se revisa la lista de `trust.groq.com/subprocessors` al cargar la llave y cuando Groq avise cambios (aviso de 15 días y derecho a objetar).
  - **Activación:** la llave de despliegue se carga en Secret Manager solo cuando la retención cero está activa y el addendum aceptado, y ambos quedan confirmados por escrito en la tarea de despliegue (CM-225). Sin esas dos confirmaciones la llave no se carga y el tercer intento sigue usando Gemini.
  - **Llaves:** la de pruebas locales y la de despliegue son distintas y de proyectos distintos de Groq; ninguna se escribe en Git, registros ni respuestas.
  - **Pendiente de verificación humana:** la vigencia de Estados Unidos en la lista de países con nivel adecuado de la Superintendencia de Industria y Comercio y el texto operativo de su Circular Externa 003 de 2025; lo confirma quien responde por la protección de datos del equipo antes de producción.
- **Tiempos por operación.** La evaluación espera como máximo 3 s por intento (`LLM_EVALUATION_TIMEOUT`); la planificación, 20 s (`LLM_TIMEOUT`).
- **Error de la respuesta.** Si los tres intentos fallan, el `code` es el del último (`LLM_UNAVAILABLE`, `LLM_LIMIT_REACHED` o `LLM_INVALID_OUTPUT`), con el mismo mensaje de siempre. Una evaluación fallida deja el turno en `RETRYING`; una planificación fallida deja la sesión `CONFIGURADA`.
- **Reintentar corre un ciclo nuevo** desde el intento 1; el contador de reintentos manuales del turno sube en uno por ciclo, no por intento.
- **La caducidad de la evaluación en curso** y la espera de la misma clave idempotente usan el presupuesto del ciclo completo (3 × tiempo de evaluación + esperas, más 15 s de margen en la espera), para no declarar abandonado un ciclo que sigue vivo.
- **Sin migración.** El esquema ya guardaba número de intento, proveedor y modelo.

## Consecuencias

- Frontend no cambia su contrato: los mismos códigos 503, ahora tras hasta tres intentos; la petición puede tardar más (hasta ≈ 10,5 s al evaluar).
- DevOps debe cargar `GROQ_API_KEY` y, si se desea, las variables de tiempo (tarea de CM-225). Sin la llave todo funciona con Gemini en el tercer intento.
- Cada intento queda trazable en la base sin guardar texto de la persona ni de la IA.

## Hallazgos de la implementación

- La tarjeta suponía `OpenAiApi`; la versión de Spring AI usada trae el cliente `openai-java`.
- Ese cliente agrega `/chat/completions` a la base sin `/v1`, por lo que el servicio agrega `/v1` a la URL configurada; la propiedad conserva `https://api.groq.com/openai`. La llamada real a Groq no se probó (sin llave).
- Se fija `spring.ai.model.*=none` para que ninguna autoconfiguración de Spring AI cree modelos que el servicio arma a mano.
- El nombre del esquema dentro de `response_format.json_schema` no se verificó contra la API real.
- Las pruebas de punta a punta usan esperas de 0 ms.

## Alternativas descartadas

- Cliente HTTP propio para Groq: más código que mantener.
- Esperas de 1 s y 2 s: más espera sin beneficio medido.
- No arrancar sin la llave de Groq: bloquearía el despliegue hasta que DevOps la cargue.
- Probar solo la versión escalada del tiempo: no cumple el criterio al pie de la letra.
