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

PENDIENTE: se completa con las fechas y el SHA al guardar los resultados.
