# Medición del evaluador de respuestas

Mide, con llamadas reales, el evaluador de respuestas del servicio (prompt `evaluacion.v1`, su esquema y el lector `EvaluationOutputReader`) contra Gemini (por el puerto real del servicio) y Qwen en Groq. Los casos están en `src/test/resources/spike/evaluacion/casos.v1.json`: textos sintéticos, con la decisión esperada como propuesta pendiente de revisión de Paula.

## Cómo se corre

Variables del entorno del proceso (solo nombres; los valores nunca se escriben en archivos): `GOOGLE_GENAI_API_KEY`, `LLM_MODEL`, `GROQ_API_KEY`. Opcionales: `SPIKE_GRUPOS`, `SPIKE_PRESUPUESTO_GEMINI` (200) y `SPIKE_PRESUPUESTO_QWEN` (400).

```powershell
# Sin red: pruebas del informe y de los clientes
./mvnw.cmd -B -ntp "-Dtest=InformeEvaluacionTest,ModelosDePruebaTest" test
# Tanda 1 (180 llamadas a cada modelo)
$env:SPIKE_EVALUACION="true"; $env:SPIKE_GRUPOS="ES_SIN_DIFICULTAD,ES_CON_DIFICULTAD,EN,QA"; ./mvnw.cmd -B -ntp "-Dtest=MedicionEvaluacionRunner" test
# Tanda 2 (48 llamadas a cada modelo)
$env:SPIKE_EVALUACION="true"; $env:SPIKE_GRUPOS="RAFAGA_10,BORDES"; ./mvnw.cmd -B -ntp "-Dtest=MedicionEvaluacionRunner" test
```

Al terminar, el ejecutor reescribe `informe.md` y `resultados.csv` con todas las salidas de `salidas/<grupo>/<modelo>/<caso>-<corrida>.json`. Una llamada ya guardada se salta, así que una corrida interrumpida se reanuda.

Antes de guardar o copiar cualquier resultado se pasa el control de llaves:

```powershell
git grep --untracked -n -E "AIza[0-9A-Za-z_-]{20,}|gsk_[0-9A-Za-z]{20,}|[Bb]earer [0-9A-Za-z._-]{20,}" -- docs/mediciones/CM-216-evaluacion
```

Esperado: sin resultados.

## Qué día se corrió cada grupo

Las dos tandas corrieron el 12-nov-2026 (hora de Colombia) sobre el código del commit `1e6cef6`, con `gemini-3.5-flash-lite` (pensamiento `minimal`) y `qwen/qwen3.8-27b`: 228 llamadas a cada modelo (45 por cada uno de los cuatro grupos de la tanda 1, 30 de ráfaga y 18 de bordes), con las llamadas que fallaron en el proveedor incluidas.

Incidencias de la corrida:

- Un primer intento con `LLM_MODEL=gemini-3.8-flash` falló al instante en las 7 primeras llamadas: ese modelo rechaza el nivel de pensamiento `minimal` (`400: Thinking level MINIMAL is not supported for this model`). Se descartaron esas 7 salidas y se repitió todo con el modelo de la spec. Dato para quien despliegue: `application.properties` fija `minimal`, así que `LLM_MODEL` debe ser un modelo que lo admita.
- Gemini respondió 503 («high demand») en 15 llamadas, sin reintento; Qwen devolvió 429 por minuto con reintento (19 intentos) y 2 llamadas de la ráfaga quedaron sin respuesta por «request too large» al sumarse tokens.
- El informe no cambia por sí solo el prompt, el esquema ni los tiempos.
