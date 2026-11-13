# Medición del evaluador de respuestas (HU-5.2)

Corrida del 2026-11-12 (hora de Colombia) contra los modelos reales. Este documento se entiende solo: describe qué se midió, los resultados y lo que queda sin medir. Todos los datos de las entradas son ficticios. Se generó con `InformeEvaluacion` a partir de las salidas crudas de `salidas/`; los casos y las decisiones esperadas son una propuesta pendiente de revisión de Paula.

## 1. Cobertura de lo corrido

| Grupo | Modelo | Llamadas esperadas | Llamadas hechas | Estado |
|---|---|---|---|---|
| ES_SIN_DIFICULTAD | GEMINI_LITE | 45 | 45 | completo |
| ES_CON_DIFICULTAD | GEMINI_LITE | 45 | 45 | completo |
| EN | GEMINI_LITE | 45 | 45 | completo |
| QA | GEMINI_LITE | 45 | 45 | completo |
| RAFAGA_10 | GEMINI_LITE | 30 | 30 | completo |
| BORDES | GEMINI_LITE | 18 | 18 | completo |
| ES_SIN_DIFICULTAD | QWEN_GROQ | 45 | 45 | completo |
| ES_CON_DIFICULTAD | QWEN_GROQ | 45 | 45 | completo |
| EN | QWEN_GROQ | 45 | 45 | completo |
| QA | QWEN_GROQ | 45 | 45 | completo |
| RAFAGA_10 | QWEN_GROQ | 30 | 30 | completo |
| BORDES | QWEN_GROQ | 18 | 18 | completo |

Una llamada hecha incluye las que fallaron en el proveedor. Si un grupo no se corrió por cupo, proveedor caído o falta de autorización, esa parte queda sin verificar.

## 2. Decisión, forma y estabilidad por grupo y modelo

| Grupo | Modelo | JSON válido | Decisión correcta | No pidió seguimiento | Pidió de más | Grupo correcto | Clase exacta | Mismo resultado en las 3 corridas |
|---|---|---|---|---|---|---|---|---|
| ES_SIN_DIFICULTAD | GEMINI_LITE | 45 de 45 | 45 de 45 | 0 | 0 | 100.0 % | 80.0 % | 15 de 15 |
| ES_CON_DIFICULTAD | GEMINI_LITE | 45 de 45 | 45 de 45 | 0 | 0 | 100.0 % | 86.7 % | 15 de 15 |
| EN | GEMINI_LITE | 41 de 45 | 41 de 45 | 0 | 0 | 100.0 % | 87.8 % | 11 de 15 |
| QA | GEMINI_LITE | 44 de 45 | 44 de 45 | 0 | 0 | 100.0 % | 86.4 % | 14 de 15 |
| RAFAGA_10 | GEMINI_LITE | 23 de 30 | 23 de 30 | 0 | 0 | 100.0 % | 73.9 % | 6 de 10 |
| BORDES | GEMINI_LITE | 15 de 18 | 12 de 18 | 3 | 0 | 80.0 % | 80.0 % | 5 de 6 |
| ES_SIN_DIFICULTAD | QWEN_GROQ | 45 de 45 | 43 de 45 | 0 | 2 | 95.6 % | 82.2 % | 14 de 15 |
| ES_CON_DIFICULTAD | QWEN_GROQ | 45 de 45 | 43 de 45 | 0 | 2 | 95.6 % | 80.0 % | 12 de 15 |
| EN | QWEN_GROQ | 45 de 45 | 42 de 45 | 0 | 3 | 93.3 % | 80.0 % | 13 de 15 |
| QA | QWEN_GROQ | 45 de 45 | 42 de 45 | 0 | 3 | 93.3 % | 75.6 % | 11 de 15 |
| RAFAGA_10 | QWEN_GROQ | 28 de 30 | 28 de 30 | 0 | 0 | 100.0 % | 78.6 % | 8 de 10 |
| BORDES | QWEN_GROQ | 18 de 18 | 14 de 18 | 1 | 3 | 50.0 % | 50.0 % | 4 de 6 |

«Grupo correcto» y «clase exacta» se calculan sobre las llamadas con JSON válido. «No pidió seguimiento» es una respuesta que esperaba `FOLLOW_UP` y recibió `AVANZAR`; «pidió de más» es lo contrario.

## 3. Latencia

Reloj del cliente (`System.nanoTime`), solo con las llamadas que devolvieron texto; el corte de la medición es 30 s (no el de 3 s de producción). El p99 solo se calcula con 100 llamadas o más.

| Grupo | Modelo | Con texto | p50 (ms) | p95 (ms) | p99 (ms) | Máximo (ms) | Cortadas (30 s) | Sobre 3 s | Máximo × 1,5 (ms) |
|---|---|---|---|---|---|---|---|---|---|
| ES_SIN_DIFICULTAD | GEMINI_LITE | 45 | 1138 | 1939 | — | 23471 | 0 | 1 | 35206 |
| ES_CON_DIFICULTAD | GEMINI_LITE | 45 | 1087 | 1811 | — | 1867 | 0 | 0 | 2800 |
| EN | GEMINI_LITE | 41 | 1163 | 2016 | — | 16447 | 0 | 2 | 24670 |
| QA | GEMINI_LITE | 44 | 1067 | 1564 | — | 2091 | 0 | 1 | 3136 |
| RAFAGA_10 | GEMINI_LITE | 23 | 2016 | 21363 | — | 23337 | 0 | 16 | 35005 |
| BORDES | GEMINI_LITE | 15 | 1938 | 19808 | — | 19808 | 0 | 8 | 29712 |
| TOTAL | GEMINI_LITE | 213 | 1165 | 4977 | 21363 | 23471 | 0 | 28 | 35206 |
| ES_SIN_DIFICULTAD | QWEN_GROQ | 45 | 925 | 2100 | — | 2715 | 0 | 0 | 4072 |
| ES_CON_DIFICULTAD | QWEN_GROQ | 45 | 823 | 1381 | — | 2229 | 0 | 0 | 3343 |
| EN | QWEN_GROQ | 45 | 793 | 1663 | — | 2200 | 0 | 0 | 3300 |
| QA | QWEN_GROQ | 45 | 724 | 1320 | — | 1717 | 0 | 0 | 2575 |
| RAFAGA_10 | QWEN_GROQ | 28 | 762 | 1058 | — | 1406 | 0 | 0 | 2109 |
| BORDES | QWEN_GROQ | 18 | 778 | 1848 | — | 1848 | 0 | 0 | 2772 |
| TOTAL | QWEN_GROQ | 226 | 797 | 1634 | 2229 | 2715 | 0 | 0 | 4072 |

## 4. Tokens y fallos del proveedor

| Modelo | Entrada: media | Entrada: máximo | Salida: media | Salida: máximo | SOBRECARGA | LIMITE_POR_MINUTO | LIMITE_POR_DIA | OTRO |
|---|---|---|---|---|---|---|---|---|
| GEMINI_LITE | 709 | 1533 | 109 | 179 | 15 | 0 | 0 | 0 |
| QWEN_GROQ | 713 | 1605 | 131 | 273 | 0 | 19 | 0 | 0 |

## 5. Lecturas específicas

**Idioma del `feedback` en inglés (grupo `EN`, heurística de palabras vacías).**

| Modelo | En inglés | En español | Mezclado |
|---|---|---|---|
| GEMINI_LITE | 0 | 41 | 0 |
| QWEN_GROQ | 2 | 41 | 2 |

**Ráfaga de 10 (grupo `RAFAGA_10`).**

| Modelo | Tiempo total de una ronda de 10 (s, media) | Llamadas rechazadas por límite |
|---|---|---|
| GEMINI_LITE | 54.7 | 0 |
| QWEN_GROQ | 7.2 | 11 |

**Bordes (grupo `BORDES`).**

| Modelo | La inyección cambió la decisión | La IA reveló el prompt | Respuestas de 4.000 con decisión correcta |
|---|---|---|---|
| GEMINI_LITE | 3 de 12 | 0 de 12 | 6 de 6 |
| QWEN_GROQ | 1 de 12 | 0 de 12 | 3 de 6 |

**Conductuales (pregunta 3 de `ES`, `EN` y `QA`): `feedback` que nombra situación, tarea, acción y resultado** (conteo por palabras; STAR completo sigue fuera de alcance, HE-06).

| Modelo | Con las cuatro partes |
|---|---|
| GEMINI_LITE | 0 de 25 |
| QWEN_GROQ | 0 de 27 |

## 6. Comparación con el spike CM-261

Los casos no son los mismos, así que la comparación es orientativa. Cifras del spike: decisión correcta en 35 de 36 (Gemini, temperatura 1,0) y 33 de 36 (Qwen). Esta medición, grupo `ES_CON_DIFICULTAD` (el que se parece al spike):

| Modelo | Esta medición | Spike |
|---|---|---|
| GEMINI_LITE | 45 de 45 | 35 de 36 |
| QWEN_GROQ | 43 de 45 | 33 de 36 |

## 7. Hallazgos con destino

Reglas de destino de la spec (§8b). Ninguna cambia el prompt, el esquema ni los tiempos sin decisión de Paula.

| Regla | Dato | Destino |
|---|---|---|
| 1. Latencia (GEMINI_LITE) | p95 4977 ms; máximo × 1,5 = 35206 ms | **Hallazgo:** pasa de 3 s; va a CM-224 (tiempo de evaluación de este proveedor) y a decisión de Paula |
| 2. Decisión ES_SIN_DIFICULTAD frente a `ES_CON_DIFICULTAD` (GEMINI_LITE) | 45 de 45 frente a 45 de 45 | Sin diferencia a la baja |
| 2. Decisión EN frente a `ES_CON_DIFICULTAD` (GEMINI_LITE) | 41 de 45 frente a 45 de 45 | **Diferencia:** decisión de Paula; prompt `evaluacion.v2` si decide corregirlo ahora |
| 2. Decisión QA frente a `ES_CON_DIFICULTAD` (GEMINI_LITE) | 44 de 45 frente a 45 de 45 | **Diferencia:** decisión de Paula; prompt `evaluacion.v2` si decide corregirlo ahora |
| 3. JSON no válido (GEMINI_LITE) | 0 | Sin hallazgo |
| 5. Ráfaga (GEMINI_LITE) | 0 rechazadas por límite | Sin hallazgo |
| 6. Inyección (GEMINI_LITE) | 3 casos | **Hallazgo:** corregir el prompt (`evaluacion.v2`) y repetir `BORDES`; decisión de Paula |
| 1. Latencia (QWEN_GROQ) | p95 1634 ms; máximo × 1,5 = 4072 ms | **Hallazgo:** pasa de 3 s; va a CM-224 (tiempo de evaluación de este proveedor) y a decisión de Paula |
| 2. Decisión ES_SIN_DIFICULTAD frente a `ES_CON_DIFICULTAD` (QWEN_GROQ) | 43 de 45 frente a 43 de 45 | Sin diferencia a la baja |
| 2. Decisión EN frente a `ES_CON_DIFICULTAD` (QWEN_GROQ) | 42 de 45 frente a 43 de 45 | **Diferencia:** decisión de Paula; prompt `evaluacion.v2` si decide corregirlo ahora |
| 2. Decisión QA frente a `ES_CON_DIFICULTAD` (QWEN_GROQ) | 42 de 45 frente a 43 de 45 | **Diferencia:** decisión de Paula; prompt `evaluacion.v2` si decide corregirlo ahora |
| 3. JSON no válido (QWEN_GROQ) | 0 | Sin hallazgo |
| 5. Ráfaga (QWEN_GROQ) | 11 rechazadas por límite | **Hallazgo:** dato para CM-224 (un rechazo por límite pasa al respaldo) y para la decisión de producto sobre el nivel de pago |
| 6. Inyección (QWEN_GROQ) | 1 casos | **Hallazgo:** corregir el prompt (`evaluacion.v2`) y repetir `BORDES`; decisión de Paula |
| 4. Idioma del `feedback` (sección 5) | Ver conteo por modelo | Se suma a H-12; destino HE-06 |
| 7. Conductuales (sección 5) | Ver conteo por modelo | HE-06 |
| 8. Grupo no corrido | 0 combinaciones grupo-modelo incompletas | Sin hallazgo |
