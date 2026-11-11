# Informe del spike de planificación y tonos (parte de Backend de HU-4.1)

Corridas del 7 y el 8-nov-2026 (hora de Colombia). Cada cifra sale de los archivos de
`fixtures/salidas/<CARPETA>/` (una salida cruda por corrida) o de `fixtures/reevaluaciones/` (las mismas salidas vueltas a evaluar
con la regla vigente, sin llamar al modelo). Nada de producción cambió: el banco vive en `src/test` y en `docs/spikes/`.


## Para CM-212 (planificar las 5 preguntas)

**Recomendación final: Gemini 3.5 Flash Lite (`gemini-3.5-flash-lite`) con esta configuración.**

| Insumo | Valor | Evidencia |
|---|---|---|
| Prompt | `prompts/planificacion.v3.txt` + `tono.PROFESIONAL_NEUTRO.v1.txt`, `tono.CALIDO.v2.txt` y `tono.ESTRICTO.v2.txt` | §2b |
| Nivel de pensamiento | `minimal` (es solo configuración: `cameia.llm.thinking-level`) | §2 |
| Temperatura | 1,0 (0,3 no mejoró) | §2 |
| **Cambio de código en el adaptador (`src/main`, de CM-212)** | Unir el texto de **todas** las partes de la respuesta de Gemini: `GeminiLanguageModelAdapter` entrega solo la primera y el plan llega cortado | §3 |
| Validación y reintento | Normalizar el tipo (mayúsculas) antes de validar; validar el plan al recibirlo y reintentar **una** vez | §3 |
| Plan válido a la primera / tras un reintento | Grupo A (neutro): 22 de 24 / 23 de 24. Tonos con la configuración final: **24 de 24 / 24 de 24** | §2 y §2b |
| Plantilla de competencias | 1 `APERTURA`, 2 `TECNICA`, 3 `CONDUCTUAL`, 4 `TECNICA`, 5 `SITUACIONAL` (`plantilla-competencias.md`); orden confirmado por Paula el 8-nov-2026 | |
| Esquema JSON | `schemas/plan-preguntas.v1.schema.json` | |
| Tiempo máximo por petición | **9,5 s** (máximo observado 6,35 s × 1,5). Un plan con un reintento son dos peticiones: **19 s** en el peor caso | §2b |
| Largo máximo de la pregunta principal | **500 caracteres** (máximo observado 362, con Qwen; 299 con Gemini; margen de 25 %, a la centena) | §2b |
| Columna de la pregunta | `pregunta.texto_original` es `VARCHAR(1000)` (CM-311). Con un límite validado de 500, el estándar pide que el esquema repita el límite: CM-212 baja la columna a 500 con una migración Flyway nueva | §2b |
| Límites de Gemini | Nivel gratuito del proyecto «Proyecto CAMEIA» (AI Studio, 8-nov-2026): `gemini-3.5-flash-lite` 15 peticiones por minuto, 250.000 tokens por minuto y 500 por día. Máximos en 28 días: 16, 12.430 y 217 | §5 |

El texto de instrucción que usa hoy el servicio es `planificacion.v6`, no la `v3` de la tabla: la `v4` encierra el texto de la persona entre marcas, la `v5` agrega la regla para quien no tiene experiencia laboral y la `v6` la prohibición de temas ajenos al desempeño. Su medición está en `CM-212-medicion-final/informe.md`; las cifras de este informe son las de la `v3`.

Respaldo: Qwen 3.8 27B en Groq con `reasoning_effort = none` y el mismo prompt v3. Con ese prompt, 19 de 24 planes de tonos salieron válidos a la
primera y 22 de 24 con un reintento (mediana 0,99 s); devuelve textos vacíos con más frecuencia que Gemini (§4). El bloque
`tono.ESTRICTO.v2.txt` no se midió con Qwen. Tiene un tope de 1.000 tokens de salida por minuto (unos 3 planes por minuto).

## 1. En palabras llanas

Un spike es un experimento con tope de horas. Se le pidió a dos modelos que prepararan las 5 preguntas de una entrevista con perfiles y
ofertas **inventados**, y se midió cuántas veces el plan sale bien a la primera, cuánto tarda y si los tres tonos (neutro, cálido y
estricto) se distinguen.

Lo más importante que se encontró:

- **Una causa de fallos era del adaptador, no del modelo.** Gemini a veces contesta en dos partes y el servicio se queda con la primera:
  el plan llega cortado. Con las partes unidas, los planes válidos a la primera pasaron de 19 a 22 de 24 con el mismo prompt.
- **El prompt v2 y el pensamiento mínimo remataron el resultado:** 24 de 24 y la mitad del tiempo.
- **Con un reintento, ambos modelos llegan a 22-24 de 24**, y los tres tonos de Gemini se distinguen con la configuración final (24 de 24 planes y 8 de 8 de cada tono).
- **Una segunda ronda** corrigió el saludo repetido del tono cálido y añadió la regla «un tema distinto por pregunta» (§3).

Cada plan se revisa con siete comprobaciones, de la **a** a la **g** (CA-4.1.1): a = forma correcta; b = tipos según la plantilla;
c = preguntas técnicas pertinentes al rol; d = idioma; e = sin repetidas; f = refleja la oferta; g = largo dentro del límite.
Las letras a, b y g se deciden con regla exacta; **c, d, e y f son aproximadas** y una persona debe confirmarlas: esa confirmación consta en el campo «Control humano» del PR, no en este informe.

## 2. Antes y después

Grupo A ampliado: 24 corridas (2 roles × 2 idiomas × con y sin oferta × 3), tono neutro. «Válidos» usa la regla vigente (§6).

| Configuración | Muestras | Válidos a la primera | Válidos tras 1 reintento | Fallos (letra: cuántos) | Mediana | p95 | Máximo | Largo máx. pregunta | Tokens de salida (mediana) |
|---|---|---|---|---|---|---|---|---|---|
| **Gemini, antes**: puerto tal cual, prompt v1, pensamiento `low`, T 1,0 | 24 | 19 | 24 | a: 5 (4 JSON cortado, 1 tipo fuera del catálogo) | 2,01 s | 3,77 s | 6,19 s | 235 | 212 |
| Gemini, partes unidas, prompt v1 | 24 | 22 | 24 | a: 2 (tipo fuera del catálogo) | 3,20 s | 4,17 s | 5,99 s | 260 | 212 |
| Gemini, partes unidas, prompt v2 | 24 | 24 | 24 | ninguno | 2,36 s | 3,61 s | 8,80 s | 189 | 210 |
| Gemini, partes unidas, prompt v2, T 0,3 | 24 | 23 | 24 | a: 1 (JSON inválido) | 1,70 s | 2,88 s | 3,19 s | 259 | 202 |
| **Gemini, partes unidas, prompt v2, pensamiento `minimal`** (base de la configuración final; ver §2b) | 24 | **24** | 24 | ninguno | **1,06 s** | 1,35 s | 2,44 s | 233 | 206 |
| Qwen, antes (prompt v1, por defecto; grupo A original) | 8 | 7 | — | a: 1 | 1,01 s | — | 1,51 s | 298 | 360 |
| Qwen, prompt v2, por defecto | 22 (+2 perdidas) | 17 | 21 | a: 5 (campos mal formados) | 0,92 s | 1,04 s | 3,92 s | 222 | 294 |
| **Qwen, prompt v2, sin razonamiento** (base del respaldo; ver §2b) | 24 | 21 | 24 | a: 2 · c: 1 | 0,95 s | 1,45 s | 1,69 s | 215 | 312 |

Lecturas:

- Los tiempos de Gemini tienen ruido del proveedor: la mediana de «partes unidas, v1» (3,2 s) es mayor que la del puerto tal cual
  (2,0 s) aunque unir partes no agrega tiempo. La diferencia clara es la de `minimal` (1,06 s, p95 1,35 s).
- **Prompt v2 frente a v1 (ambos con partes unidas):** 24 contra 22 de 24. La diferencia (2) es chica con 24 muestras; v2 se queda
  porque no empeora nada. El tipo mal escrito (`TEcnICA`) aparece una vez con v2 en 56 corridas de la configuración base
  (A05 de la corrida de tonos); el reintento lo cubre.
- **Qwen por defecto** perdió 2 corridas por el tope de 1.000 tokens de salida por minuto del proveedor (no se cuentan como
  inválidas). Sin razonamiento no perdió ninguna.
- **Tiempo máximo:** con el prompt v2 el máximo de Gemini fue 2,44 s. El valor que se recomienda para CM-212 (9,5 s por petición) sale
  de la segunda ronda, que tuvo un máximo de 6,35 s (§2b).

### Estabilidad de los tonos con el prompt v2 y los bloques de tono `v1` (Gemini; 24 corridas, 4 por tono e idioma)

Los 3 tonos × 2 idiomas × 4 repeticiones dieron **24 de 24 planes válidos**. Regla de lectura del tono (`TonoChecker`, aproximada y
reproducible): acogida (frases de bienvenida en alguna pregunta) y exigencia (pedir cifras, métricas o precisión en 3 o más preguntas).

| Tono pedido | Leído igual | Leído distinto |
|---|---|---|
| PROFESIONAL_NEUTRO | 8 de 8 | — |
| ESTRICTO | 8 de 8 | — |
| CALIDO | 7 de 8 | 1 leído como neutro (sin frase de acogida) |

La regla se afinó con las salidas de la primera tanda; estas 24 salidas no se usaron para afinarla. **Ningún tono es inestable**; el
catálogo de tonos no se cambia (CA-4.1.8). El bloque cálido `v1` saludaba en las preguntas 2 a 4 y se reemplazó (§2b).

## 2b. Segunda ronda: tono cálido, temas y tono estricto

Esta ronda corrió 192 planes más (48 por modelo y prompt, más dos controles y la corrida final de Gemini), con cero fallos de proveedor.
Compara el prompt v2 (control) con el v3, que añade «cada una de las preguntas 2, 3, 4 y 5 trata un tema distinto del rol». Se midió
un criterio extra, **h**, que no es de un CA: un tema (por ejemplo «base de datos») no aparece en dos de las preguntas 2 a 5.

**Gemini, pensamiento `minimal`, partes unidas**

| Configuración | Muestras | Válidos a la 1.ª | Tras reintento | Mediana | Máximo | Largo máx. | Sin tema repetido (h) |
|---|---|---|---|---|---|---|---|
| A, prompt v2 | 24 | 24 | 24 | 1,06 s | 2,44 s | 233 | 4 de 24 |
| A, prompt v3 | 24 | 22 | 23 | 1,33 s | 4,81 s | 211 | 7 de 24 |
| Tonos, prompt v2 + cálido v2 (control) | 24 | 23 | 24 | 1,46 s | 2,56 s | 287 | 6 de 24 |
| Tonos, prompt v3 + cálido v2 | 24 | 23 | 24 | 1,46 s | 6,35 s | 299 | 10 de 24 |
| **Tonos, prompt v3 + cálido v2 + estricto v2 (final)** | 24 | **24** | 24 | 1,52 s | 4,22 s | 296 | **11 de 24** |

**Qwen 3.8 27B en Groq, sin razonamiento**

| Configuración | Muestras | Válidos a la 1.ª | Tras reintento | Mediana | Máximo | Largo máx. | Sin tema repetido (h) |
|---|---|---|---|---|---|---|---|
| A, prompt v2 | 24 | 21 | 24 | 0,95 s | 1,69 s | 215 | 3 de 23 |
| A, prompt v3 | 24 | 21 | 23 | 1,01 s | 3,50 s | 250 | 7 de 22 |
| Tonos, prompt v2 + cálido v2 (control) | 24 | 21 | 24 | 1,13 s | 2,20 s | 362 | 3 de 23 |
| Tonos, prompt v3 + cálido v2 | 24 | 19 | 22 | 0,99 s | 1,65 s | 319 | 10 de 19 |

(El «de N» de h cuenta solo planes con forma válida.)

Cuántos de 8 planes de cada tono se leen con el tono pedido (`TonoChecker`, aproximado):

| Tono | Gemini v2 (control) | Gemini v3 | Gemini final | Qwen v2 (control) | Qwen v3 |
|---|---|---|---|---|---|
| Neutro | 8 | 8 | 8 | 7 | 6 |
| Cálido | 7 | 8 | 8 | 8 | 3 (5 válidos) |
| Estricto | 6 | 2 | **8** | 6 | 8 |

Lecturas:

- **Tono cálido:** el bloque `tono.CALIDO.v2.txt` (bienvenida solo en la pregunta 1, cierre breve solo en la 5, nada de saludos ni de dar por hecha una respuesta previa en la 2 a 4) elimina el saludo repetido en las cuatro configuraciones medidas. El bloque `v1` saludaba en las preguntas 2 a 4 en 4 de 8 planes de Gemini y suponía una respuesta previa en 2.
- **Temas:** la regla del prompt v3 mejora h (Gemini 6 → 10 de 24, Qwen 3 → 10 de 19) pero no la resuelve: casi la mitad de los planes aún repite algún tema entre las preguntas 2 a 5. No es un criterio de aceptación.
- **Tono estricto:** con el prompt v3 el bloque `v1` se aflojó en Gemini (2 de 8 leídos estrictos). El bloque `v2`, que pide en cada pregunta un dato verificable y el trato de «usted», lo recuperó (8 de 8) sin perder validez.
- **Tiempo:** el máximo de Gemini con v3 llegó a 6,35 s en una petición. Con los valores de la configuración final el máximo fue 4,22 s. El valor de 9,5 s deja margen para ese ruido.

## 3. Fallos, causas y ajustes

| Hallazgo | Causa vista | Ajuste y resultado |
|---|---|---|
| Gemini: JSON «cortado» (4 de 24 en la fila «antes») | `GeminiDiagnostico` (6 llamadas): en 2 de 6 la respuesta trae **2 partes** (cada una con motivo de fin `STOP`) y el puerto entrega solo la primera | Unir las partes: 19 → 22 de 24 con el mismo prompt. Cambio en `src/main` para CM-212, **no hecho aquí** |
| Gemini: `tipo` mal escrito (`TEcnICA`, `TEcnica`) | Fallo del propio modelo al escribir la palabra; con prompt v1 en 2 de 24, con v2 en 1 de 56 | Validar y reintentar una vez (todos se recuperaron). Alternativa no medida: aceptar mayúsculas y minúsculas al leer el `tipo` |
| Qwen: claves mal formadas, saltos de línea sin escapar | Observado en la primera tanda | El prompt v2 pide claves exactas y una línea por texto; bajó pero no desapareció (a: 2 de 24 sin razonamiento) |
| Qwen: idioma equivocado (1 caso, primera tanda) | — | Línea de idioma al final del prompt v2; no se repitió en las 46 corridas nuevas del grupo A. El idioma con tonos no se probó con Qwen |
| Gemini: `tipo` mal escrito con el prompt v3 (`TEcnICA`, 3 de 48 planes) | Mismo fallo del modelo; dos se recuperaron con el reintento y uno no | Normalizar el tipo (mayúsculas) antes de validar y reintentar una vez |
| Qwen: textos de pregunta vacíos (7 de 48 planes con el prompt v3) y un tipo mal escrito (`CONVENTUAL`) | Fallo del modelo en la salida estructurada | El reintento corrigió 5 de 7; Qwen queda solo como respaldo |
| Qwen: redacción rota que ninguna regla ve («las locators», preguntas sin signos de interrogación) | Calidad del modelo | Se anota como límite del respaldo; la lectura de una persona sigue siendo necesaria |
| Ambos: pregunta técnica 4 «sin términos del rol» | La regla c era injusta: no contaba RabbitMQ ni mensajes asíncronos | Regla ajustada (§6) |

## 4. Lectura de la IA sobre lo aproximado

Lectura hecha por la IA sobre una muestra de las salidas guardadas (`fixtures/salidas/`). No es una revisión humana; la confirmación consta en el campo «Control humano» del PR.

| Aspecto | Lectura |
|---|---|
| Tonos | Cálido saluda y tutea («Qué gusto saludarte…», «Welcome to the team!»); estricto exige métricas y ejemplos; neutro es directo |
| Pertinencia al rol (c) | Los fallos de c de la primera tanda eran preguntas sobre RabbitMQ o mensajería; con la regla corregida pasan. En las corridas nuevas quedó 1 fallo (Qwen sin razonamiento, A05) |
| Cálido (segunda ronda) | Bienvenida en la 1 y cierre en la 5; las preguntas 2 a 4 tutean sin saludos. La pregunta 1 de algunos planes resulta efusiva |
| Estricto (segunda ronda) | Con `v2` pide cifras, herramientas y resultados medibles y trata de «usted»; con Qwen en español usó «tú» en algún plan |
| Temas repetidos | Varios hallazgos de h son ángulos distintos del mismo tema (por ejemplo, consultas de la base de datos en la 2 y en la 5); otros repiten el escenario (defecto crítico antes del lanzamiento en la 3 y en la 5) |
| Suposiciones sobre la persona | En la primera tanda, una primera pregunta de Qwen decía «su estancamiento». El prompt v2 lo prohíbe; no se revisaron a mano todas las salidas nuevas |
| Refleja la oferta (f) | Sí en la muestra leída |

## 5. Límites y cuotas

| | Gemini 3.5 Flash Lite | Qwen 3.8 27B (Groq) |
|---|---|---|
| Límites del proyecto (nivel gratuito, AI Studio, 28 días) | `gemini-3.5-flash-lite`: **15 peticiones por minuto, 250.000 tokens por minuto, 500 peticiones por día**. Máximos vistos: 16 por minuto, 12.430 tokens por minuto, 217 por día. `gemini-3.8-flash`: 5 por minuto, 250.000 tokens por minuto, 20 por día; máximos vistos: 6 y 21 por día (límites superados) | 8.000 tokens por minuto (entrada y salida), 1.000 de salida por minuto y 1.000 peticiones por día |
| Cómo se definen | Por proyecto de Google (no por llave), según el nivel de uso; el límite diario se reinicia a la medianoche del Pacífico. Se leen en AI Studio (`aistudio.google.com/rate-limit`) | Por organización de Groq |
| Lo observado | El pico de 16 por minuto supera el límite de 15: coincide con las corridas del banco, que esperan 3 s entre llamadas (hasta 20 por minuto); en las salidas guardadas no hay ningún 429. `gemini-3.8-flash` (20 por día) explica por qué no sirvió para el spike | 2 de 24 corridas con razonamiento por defecto fueron rechazadas por el tope de salida; sin razonamiento, ninguna |
| Implicación | Un plan son 1 o 2 peticiones (con reintento): caben unos 7 planes por minuto y unos 250 al día. Más allá hay que subir el nivel (facturación). CM-212 debe tratar un 429 del proveedor como un error de negocio con código estable y mensaje claro. **El nivel gratuito puede usar los contenidos para mejorar los productos de Google (condición del proveedor, por verificar en sus términos): con datos de personas reales (contexto profesional, vacante) conviene el nivel de pago** | A unos 300 a 350 tokens por plan caben **unos 3 planes por minuto**; con varias sesiones a la vez hace falta espera y reintento |

## 6. El verificador `PlanChecker`

Cambio de regla (se volvieron a evaluar las salidas ya guardadas, sin llamar al modelo; resultados en `fixtures/reevaluaciones/`):

- **Letra c:** a la lista de términos del rol Backend se agregaron `mensaj`, `message`, `rabbitmq`, `asíncron`, `asynchron`,
  `idempoten`, `consumidor`, `consumer` y `postgres`. Efecto en la primera tanda: Gemini 15 → 17 de 20 válidos; Qwen 12 → 14 de 20.
- Las letras d (idioma), e (repetidas) y f (oferta) no se cambiaron: no dieron falsos fallos en las salidas leídas.
- Sigue siendo aproximado: c, d, e y f.
- Se agregó `TonoChecker` (lectura del tono) con su prueba; `ReevaluadorDeSalidas` vuelve a evaluar lo guardado.

## 7. Criterios no cubiertos y lo que no se hizo

| Criterio o idea | Estado |
|---|---|
| CA-4.1.1, 4.1.3, 4.1.5, 4.1.6, 4.1.7, 4.1.8 | Medidos (§2). Objetivo de 7 de 8 cumplido por Gemini en la configuración recomendada y por Qwen sin razonamiento con reintento |
| Parte de turno y evaluación (CA-4.1.2 y lo de evaluación de 4.1.4) | De Vela (CM-200); no se midió |
| CA-4.1.9 | Se cumplió: ver §9 |
| Salida estructurada (`responseSchema` de Gemini, modo JSON de Groq) | **No se probó.** Con las partes unidas y 24 de 24 no hizo falta; el puerto del servicio no lo expone. Sería el siguiente intento si el reintento resultara caro |
| Otros modelos (por ejemplo `gemini-3.5-flash`) | **No se corrió**: sin costo aceptado por Paula. Solo valdría la pena si Gemini Lite baja de 22 de 24 en uso real |
| Tonos de Qwen con 4 por celda | 8 por tono con el prompt v3 y el control v2 (§2b); el bloque estricto `v2` no se midió con Qwen |
| Criterio h (tema distinto por pregunta) | Medido; no es de un CA y no bloquea el plan (§2b) |
| Temperatura 0,7 | No se corrió (0,3 no mejoró y 1,0 ya da 24 de 24) |
| Límites de Gemini | Registrados en §5 |

## 8. Qué se descartó y por qué

- **Temperatura 0,3:** 23 de 24, sin ganancia frente a 1,0.
- **Prompt v2 sin unir partes:** 5 de 8; los cortes de JSON no se arreglan con el prompt.
- **Unir partes sin cambiar el prompt:** 22 de 24; mejora, pero deja el tipo mal escrito.
- **Qwen con razonamiento por defecto:** más tokens por plan y 2 corridas rechazadas por el tope por minuto.
- **Prompt v3 con el bloque estricto `v1`:** el tono estricto de Gemini se afloja (2 de 8); se reemplazó por `tono.ESTRICTO.v2.txt`.
- **Saludos en todas las preguntas del tono cálido (`v1`):** se cambió por una bienvenida solo en la 1 y un cierre solo en la 5.
- **Cambiar el catálogo de tonos:** prohibido por CA-4.1.8 y sin motivo.

## 9. Horas

- Tiempo de máquina medido por las marcas de los archivos: primera tanda ≈ 15 min (7-nov, 10:36 a 10:50); segunda ronda ≈ 40 min (8-nov, 15:49 a 16:26, y 9-nov, 01:29 a 01:31), casi todo espera por el tope de tokens por minuto de Groq y las pausas entre llamadas.
- Las horas de trabajo se registran en el registro de trabajo de la tarea en Jira, por quien las trabajó; este informe no las estima. Tope del spike: 8 h, y 4 h para la parte de Backend.

## 10. Siguiente paso

1. Una persona confirma c, d, e y f leyendo las salidas y lo deja en el campo «Control humano» del PR.
2. CM-212: unir las partes en el adaptador, prompt v3 con los bloques de tono `v2`, pensamiento `minimal`, normalizar el tipo, validar y reintentar una vez, largo máximo de 500 y tiempo máximo de 9,5 s por petición.
3. Registrar en la spec de CM-212 el error de negocio para un 429 del proveedor.
4. Para el equipo: plantilla, esquema, prompt y cuotas. Para Frontend: tipos de pregunta y largos observados.
