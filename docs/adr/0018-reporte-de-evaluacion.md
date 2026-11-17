# ADR 0018 — Reporte de Evaluación: proveedor, escalas, fórmulas, muletillas y casos sin resultado

- **Estado:** Aceptado. Aprobado por Paula Andrea Muñoz Delgado, 16-nov-2026, con la spec de la tarea; confirmó el 17-nov-2026 el envío del cargo y la oferta a la IA y los límites conocidos.
- **Fecha:** 16-nov-2026.
- **Historias:** HU-6.1 (esta decisión), HU-6.2 y HU-6.3 (la usan). Spec: `specs/CM-380-ReporteEvaluacion/spec.md`.

## Contexto

Cuando una persona termina una Simulación, el servicio le entrega un Reporte de Evaluación con tres métricas de contenido
(Precisión y Claridad, Carisma Verbal, Lenguaje Inclusivo y Adecuado), cuatro de voz (Ritmo de Habla, Tiempo de Pausas,
Tasa de Vacilación y Entusiasmo) y el Método STAR de cada pregunta conductual. Hacen falta un proveedor, una escala, unas
fórmulas y unas reglas para lo que no se puede calcular, que no estaban escritas. El audio no se guarda, y Cloud Run solo
asegura CPU mientras atiende una petición.

## Decisión

### 1. Proveedor de IA

El reporte usa el mismo proveedor y el mismo modelo que la evaluación de respuestas (variable `LLM_MODEL`; Gemini) con
Groq como respaldo, y la misma política de 3 intentos del [ADR 0014](0014-reintentos-y-respaldo-de-la-ia.md): 2 con
Gemini y el tercero con Groq, esperas de 500 ms y 1 s, y un 429 no se repite con el mismo proveedor. Es **una sola llamada
por sesión**, con salida JSON validada por esquema (`prompts/esquema.reporte.v1.json`) y por el lector del servicio. Valen
las mismas condiciones de datos del ADR 0014: sin entrenamiento con los datos y retención cero en Groq. A la IA solo van el
idioma, el nombre del rol al que aspira la persona, la oferta laboral si la hay (para juzgar si la respuesta tiene que ver
con el rol), el texto de cada pregunta, su tipo y la respuesta. Nunca el CV, el nombre ni el correo. El prompt es
`reporte.v1` (`prompts/reporte.v1.txt`, tercera versión de trabajo). Defensas del prompt: el texto de las personas va
entre etiquetas y el armador reemplaza `<`, `>` y sus formas de ancho completo y los encabezados falsos «Pregunta N (»;
el lector exige las cinco preguntas, una sola vez cada una, y rechaza cualquier otra cosa. La temperatura queda en 1,0
como en todo el servicio: bajarla afectaba a los otros prompts.

Prueba en vivo del 16-nov-2026, con una sesión ficticia de 5 preguntas: tres llamadas válidas a `gemini-3.5-flash-lite` (1,3
a 2,6 s), una más que respondió 503 «modelo con mucha demanda» (el caso que cubre el reintento) y una válida a Groq
(`qwen/qwen3.8-27b`, 1,3 s, mismo esquema en modo estricto). Las cuatro salidas válidas pasaron el esquema y dieron notas
coherentes.

### 2. Escala y rúbrica de contenido

La IA califica cada respuesta **no conductual** con un entero de 1 a 5 por métrica:

| Métrica | 1 | 3 | 5 |
|---|---|---|---|
| Precisión y Claridad | No responde lo preguntado o es confusa | Responde lo preguntado con ideas comprensibles, pero generales o desordenadas | Responde de forma directa, ordenada y con ejemplos o datos concretos |
| Carisma Verbal | Tono plano, inseguro o negativo | Tono correcto, pero poco convincente | Tono seguro, cercano y convincente, que transmite interés |
| Lenguaje Inclusivo y Adecuado | Expresiones ofensivas, discriminatorias o vulgares | Registro informal o poco apropiado para una entrevista, sin ser ofensivo | Registro profesional, respetuoso e inclusivo |

2 y 4 son los intermedios. El valor de la sesión es el promedio de las respuestas calificadas, guardado con 2 decimales
(redondeo `HALF_UP`) y mostrado con 1. Las respuestas conductuales no se califican aquí: van al Método STAR.

### 3. Método STAR

En la misma llamada, la IA marca en cada respuesta **conductual** si están la Situación, la Tarea, la Acción y el
Resultado. El nivel lo calcula el servicio: cantidad de componentes presentes, de 0 a 4. Un 0 es un valor calculado, no
«sin resultado».

### 4. Métricas de voz

Al transcribir cada respuesta por voz, con la lista `words` que devuelve AssemblyAI (cada palabra con su inicio y su fin en
ms), el servicio calcula y guarda cuatro números, nunca la lista:

- **palabras:** cantidad de palabras con tiempos;
- **tramo de habla:** fin de la última palabra − inicio de la primera, en ms;
- **pausas:** suma de los silencios de **500 ms o más** entre palabras consecutivas, en ms;
- **muletillas:** cantidad de palabras de la lista del apartado 5.

Por sesión, con la suma de esos datos en sus respuestas por voz:

- **Ritmo de Habla** = palabras ÷ tramo de habla en minutos (entero, palabras por minuto);
- **Tiempo de Pausas** = pausas ÷ tramo de habla × 100 (%, 1 decimal);
- **Tasa de Vacilación** = muletillas ÷ palabras × 100 (por cada 100 palabras, 1 decimal).

Redondeo `HALF_UP`. No se usa openSMILE ni otro componente: los datos salen de la respuesta que ya llega al transcribir.

**Medición de los 500 ms (16-nov-2026):** en dos audios con dos pausas largas hechas a propósito, el umbral detectó las
cuatro (4.671 y 4.003 ms en español; 3.642 y 2.897 ms en inglés). En la lectura de corrido, solo dos silencios pasaron de
500 ms, y por poco (517 y 543 ms). Los silencios naturales entre frases también cuentan: una lectura pausada da entre 20 y
30 % de Tiempo de Pausas.

### 5. Muletillas

La transcripción se pide con la opción `disfluencies` de AssemblyAI. Se cuentan como palabra completa, sin distinguir
mayúsculas, tildes ni puntuación, las de esta lista cerrada:

- español: eh, ehh, ehm, em, emm, mm, mmm, ah;
- inglés: um, umm, uh, uhh, er, erm, hmm, mm.

**Medición (16-nov-2026):** 10 audios grabados por el equipo leyendo respuestas ficticias, sin datos de personas reales:
5 en español y 5 en inglés (uno de control sin muletillas, uno con 4 repartidas, uno con 6 y dos pausas largas, uno muy
corto con 1, y uno con 5 alargadas). Cada audio se transcribió con y sin la opción, con la misma petición del servicio.

| Idioma | Muletillas del guion | Detectadas sin la opción | Detectadas con la opción | ¿Las conserva? |
|---|---|---|---|---|
| Español | 16 | 0 | 17 (una real de más) | Sí, con la opción |
| Inglés | 16 | 5 | 9 (solo «um» y «uh») | En parte |

- En español, sin la opción, AssemblyAI borra las muletillas y a veces pone otra palabra en su lugar («Eh, soy» → «Y
  soy»). Con la opción las conserva y escribe las alargadas en su forma corta («ehh» → «eh»), que la lista ya incluye.
- En inglés, con la opción, solo conserva «um» y «uh»; nunca escribió «er», «erm» ni «hmm».
- **Decisión de Paula Andrea Muñoz Delgado (16-nov-2026):** en inglés la Tasa de Vacilación **se calcula igual**. Queda
  dicho que en inglés cuenta en la práctica solo «um» y «uh» y que el valor queda por debajo del real (en la sesión de
  prueba, 4,5 frente a unas 7,9 con las 16 del guion). Alternativa descartada: «sin resultado» en inglés.
- Activar la opción no empeora la transcripción que se evalúa: agrega las muletillas, conserva las repeticiones que la
  persona dijo, corrige palabras inventadas y cambia algo de puntuación; el sentido de las 10 respuestas no cambió.
- **Decisión de Paula Andrea Muñoz Delgado (16-nov-2026):** el texto transcrito se guarda y se evalúa tal cual, con
  muletillas y repeticiones, y así se ve en el historial de la sesión. Alternativa descartada: quitarlas del texto.

### 6. Entusiasmo (F0)

Medirlo exige analizar el audio, que en el MVP no se guarda ni se analiza. El Entusiasmo queda **«sin resultado» en todas
las sesiones**, nunca 0. Su cálculo queda para después del MVP.

### 7. Cómo y cuándo se genera

- El reporte se genera la **primera vez que se consulta** una sesión `FINALIZADA`, dentro de esa consulta: hasta 3
  intentos de 20 s (`LLM_TIMEOUT`), con un peor caso ≈ 61,5 s, por debajo de los 120 s del Gateway. No hay proceso en
  segundo plano ni evento.
- Una **marca de generación** (fila `GENERAR_REPORTE`/`EN_CURSO` de `ejecucion_ia`, una sola por sesión) impide dos
  generaciones a la vez: la otra consulta responde 202 `REPORT_GENERATING`. La marca vence a los **2 minutos**
  (`REPORT_GENERATION_CLAIM_TTL`), por ejemplo si la instancia se reinició, y entonces otra consulta la retoma.
- Los datos de voz se calculan **al transcribir** cada respuesta, antes de descartar el audio.
- El reporte emitido no cambia: uno por sesión, sin modificaciones (restricción y disparadores en la base).
- En el MVP todos los planes ven el mismo reporte.

### 8. Casos sin resultado

«Sin resultado» se guarda y se devuelve como nulo, nunca como 0.

| Caso | Efecto |
|---|---|
| Entrevista solo por texto | Las 4 métricas de voz «sin resultado» |
| Respuesta por voz sin datos de voz (registrada antes de existir el cálculo, sin palabras o con tramo de habla 0) | No cuenta para las métricas de voz; si ninguna tiene datos, las de voz «sin resultado» |
| Ninguna respuesta no conductual | Las 3 de contenido «sin resultado» |
| La IA falla los 3 intentos | Las 3 de contenido y el Método STAR «sin resultado»; las de voz se conservan; el reporte se emite igual |
| Entusiasmo (F0) | Siempre «sin resultado» |

## Límites conocidos

- **Sin instrucción de sistema.** El puerto de modelo de lenguaje que comparten los demás casos de uso envía un solo
  mensaje; las reglas del prompt y el texto de la persona viajan juntos y solo las separan las etiquetas y el
  neutralizado del armador. Separar la instrucción de sistema exige cambiar el puerto y sus adaptadores, y queda fuera
  de esta tarea.
- **Variación de ±1** en las notas situacionales entre llamadas con el mismo texto (medida con el prompt final); el
  respaldo en Groq es más ruidoso que Gemini.
- **Inglés sin verificar con voz real:** el prompt no se comprobó con transcripciones reales de respuestas habladas en inglés.

## Consecuencias

- Una sola llamada a la IA por sesión, para siempre: el costo del reporte es fijo y pequeño (unos 1.000 tokens de entrada
  y 250 de salida en la prueba).
- La primera consulta puede tardar hasta un minuto; Frontend muestra «Tu reporte se está generando.» mientras tanto.
- La Tasa de Vacilación en inglés subestima la vacilación real.
- El texto de las respuestas por voz muestra las muletillas.
- La voz reconocida que exige el mínimo de 1,5 s también suma lo que duran las muletillas.

## Alternativas descartadas

- Spike con 20 audios, comparación de proveedores y openSMILE: el alcance del MVP solo necesitaba la medición de
  muletillas.
- Generar el reporte al cerrar la sesión o con un evento: Cloud Run no asegura CPU fuera de una petición.
- Una columna «marca» en la tabla del reporte: la fila de `ejecucion_ia` ya registra cada intento.
