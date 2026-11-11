# Spike de planificación y tonos (CM-347)

Banco de pruebas que pide a dos modelos planes de 5 preguntas de entrevista con datos ficticios y mide la validez, el tiempo y los
tonos. No cambia el servicio: el código vive en `src/test/java/tech/cameia/entrevista/spike/planificacion/`.

| Ruta | Qué contiene |
|---|---|
| `informe.md` | Resultados, comparación de modelos y recomendación |
| `plantilla-competencias.md` | Orden de los tipos de pregunta del plan |
| `prompts/` | `planificacion.v1.txt` a `planificacion.v6.txt`: la `v4` es la `v3` con el contexto y la oferta entre `<datos_de_la_persona>` y su cierre; la `v5` agrega la regla para quien no tiene experiencia laboral; la `v6` agrega la prohibición de preguntar por temas ajenos al desempeño. El servicio usa la `v6`. Un bloque por tono (`tono.<TONO>.v1.txt`); en la configuración final, `tono.PROFESIONAL_NEUTRO.v1.txt`, `tono.CALIDO.v4.txt` (arranques variados, sin fórmulas de deseo) y `tono.ESTRICTO.v2.txt` |
| `schemas/` | Esquema JSON del plan |
| `fixtures/entradas/` | Roles, contextos y ofertas ficticios |
| `fixtures/reevaluaciones/` | Las mismas salidas vueltas a evaluar con la regla vigente, sin llamar al modelo |
| `fixtures/salidas/<CARPETA>/` | Salida cruda de cada corrida (`A01`…`B12`) y `resumen-<CARPETA>-<grupo>.json` |

Carpetas de salidas:

| Carpeta | Contenido |
|---|---|
| `GEMINI_LITE` | Gemini 3.5 Flash Lite (`gemini-3.5-flash-lite`), grupos A y B |
| `QWEN_GROQ` | `qwen/qwen3.8-27b` en Groq con los valores por defecto, grupos A y B |
| `QWEN_GROQ_SIN_RAZONAMIENTO` | Corrida aparte del grupo A con `reasoning_effort = none` («ajuste propuesto») |
| `GEMINI_LITE_*`, `QWEN_GROQ_*` con sufijo `A24`/`B24` | Corridas de mejora (24 por grupo), rotuladas con prompt (`v1` a `v3`), tonos (`tono2`, `estricto2`), partes unidas, temperatura y pensamiento |

Los intentos con errores del proveedor (503 con `gemini-3.8-flash`, 401 por falta de llave en la primera tanda de Qwen) y dos corridas exploratorias de 8 planes (`GEMINI_LITE_v1-partes-unidas`, `GEMINI_LITE_v2-partes-unidas`, sustituidas por las de 24) no se publican; no cuentan como resultado.

## Cómo se corre (manual)

Variables: `SPIKE_CM347=true`, `SPIKE_MODELO` (`GEMINI_LITE` o `QWEN_GROQ`), `SPIKE_GRUPO` (`A`, `B` o `todo`), `SPIKE_PAUSA_S`,
`SPIKE_PRESUPUESTO` (máximo de llamadas), `SPIKE_MAX_TOKENS`, `SPIKE_TIMEOUT` `SPIKE_PROMPT` (`v1` a `v5`; con `v4` y `v5` el banco quita del texto de la persona las marcas de apertura y de cierre), `SPIKE_ENTRADAS` (nombre de un archivo de `fixtures/entradas/`; por defecto, `entradas-ficticias.json`), `SPIKE_TONO` (`v2` usa los bloques de tono `v2`; los que no tienen `v2` caen a `v1`), `SPIKE_TEMPERATURA`, `SPIKE_PENSAMIENTO` (Gemini: `minimal`, `low`…), `SPIKE_REINTENTO=true`, `SPIKE_UNIR_PARTES=true` (Gemini: une todas las partes de la respuesta), `SPIKE_ETIQUETA` (rotula la carpeta), los grupos `A24` y `B24`, `S`, `C` y `SC` (perfiles sin experiencia laboral, perfiles de control con experiencia y ambos; usan `fixtures/entradas/entradas-sin-experiencia.json`), `H` (un perfil hostil por tono, dos variantes) y `T` (contexto y oferta de 2.000, 8.000 y 32.000 caracteres) y, solo para Qwen, `SPIKE_RAZONAMIENTO=none`.
Las llaves viven en `GOOGLE_GENAI_API_KEY` (con `LLM_MODEL=gemini-3.5-flash-lite`) y `GROQ_API_KEY`; nunca se imprimen ni se guardan.

```text
./mvnw.cmd test -Dtest=PlanificacionSpikeRunner
```

Las pruebas sin conexión: `./mvnw.cmd test -Dtest='PlanCheckerTest,ModelosDePruebaTest,TonoCheckerTest'`. Para reevaluar lo guardado: `SPIKE_REEVALUAR=true` con `-Dtest=ReevaluadorDeSalidas`; para el diagnóstico de las partes de Gemini: `SPIKE_DIAGNOSTICO=true` con `-Dtest=GeminiDiagnostico`.
