# Medición de `planificacion.v5` con perfiles sin experiencia laboral (CM-212)

## Qué se mide y por qué

Un perfil sin experiencia laboral es válido: la experiencia es opcional y la formación académica es obligatoria. La plantilla `v4`
pide en la posición 3 «una experiencia pasada concreta» y no manda apoyarse en la formación. La `v5` es la `v4` más una regla: si la lista
`experiencias` está vacía o no aparece, la pregunta 3 no puede pedir un empleo, un cargo ni un proyecto laboral pasado, y al menos una
pregunta se apoya en la formación académica, sin afirmar experiencia que el perfil no trae.

El banco anterior (153 llamadas de la `v4` y las anteriores) usó solo dos perfiles ficticios, Backend con 3 años y QA con 2 años de
experiencia, en texto libre. Ninguno estaba sin experiencia, de modo que el caso no se había medido.

## Configuración

`gemini-3.5-flash-lite`, pensamiento `minimal`, temperatura 1,0, partes unidas, bloques de tono `v2` (`PROFESIONAL_NEUTRO` usa su `v1`),
tope de salida 4096 tokens, tono neutro, sin oferta laboral, sin reintento. Entradas: `fixtures/entradas/entradas-sin-experiencia.json`,
datos 100 % ficticios con la forma real del contexto que recibe el servicio (JSON con `resumen`, `experiencias`, `formaciones` y
`habilidades`). Cuatro perfiles sin experiencia (Backend, QA, Datos, Frontend) en español e inglés, y seis de control con experiencia
(Backend, QA, Datos, Frontend, DevOps, Diseño de producto), uno por idioma alternando.

Un primer intento con el modelo definido en el entorno de la máquina (`gemini-3.8-flash`) respondió 400 en las 20 llamadas («Thinking level
MINIMAL is not supported for this model»): no produjo ningún plan y no cuenta en las cifras de abajo. Las corridas válidas se hicieron con
`gemini-3.5-flash-lite`, el modelo de la medición de la `v4`.

## Resultados

| Grupo | Prompt | Perfiles | Planes | Válidos a la primera | Tiempo máximo | Tokens de salida (máx.) |
|---|---|---|---|---|---|---|
| Sin experiencia (S) | `v5` | 4 × 2 idiomas | 8 | **8** | 3,34 s | 222 |
| Control con experiencia (C) | `v5` | 6 | 6 | **6** | 2,20 s | 217 |
| Sin experiencia (S), referencia | `v4` | 4 × 2 idiomas | 8 | **8** | 6,16 s | 231 |

«Válido» es la lista (a) a (g) del banco: JSON con la forma exacta, 5 preguntas, tipos de la plantilla, idioma y tecnologías del rol.

### Que al menos una pregunta cite la formación sin inventar experiencia

La lectura la hizo una persona sobre el texto de las preguntas; una regla automática (término al inicio de palabra) dio el mismo resultado.

| Perfil | `v4` (referencia) | `v5` |
|---|---|---|
| S01 y S02, Backend | cita la formación (es y en) | cita la formación (es y en) |
| S03 y S04, QA | cita la formación (es y en) | cita la formación (es y en) |
| S05 y S06, Datos | S05 sí; **S06 no** | S05 sí; S06 sí |
| S07 y S08, Frontend | S07 sí; **S08 no** | S07 sí; S08 sí |
| **Total** | **6 de 8** | **8 de 8** |

- La pregunta 3 de la `v5` en los 8 planes se apoya en estudios, proyecto de grado, cursos o proyecto académico; ninguna pide un empleo.
- `v4`, S06 (inglés, Datos): la pregunta 3 pide «a specific time when you used Python to analyze a dataset» sin ligarla a los estudios, y la
  pregunta 1 habla de «the Data Analyst internship», una pasantía que el perfil no dice. `v4`, S08 (inglés, Frontend): la pregunta 3 pide
  «a time when you built a web page» y ninguna pregunta cita la formación. Son los dos casos que la `v5` corrige.
- Una limitación de la `v5`: S03 (QA, español) pregunta «cómo aplicó los conceptos de Selenium» durante sus estudios; el perfil lista
  Selenium con nivel básico y dice que practicó casos de prueba en sus cursos, pero no dice que lo haya usado en ellos. Es una suposición
  leve, y es el tipo de caso que la regla «usa SOLO lo que dice el contexto» busca evitar; no se corrigió el prompt por una sola aparición.
- Control con experiencia (6 planes `v5`): la pregunta 3 pide una situación del trabajo en 6 de 6 (C03 dice «en su experiencia laboral») y
  ninguna se apoya en la formación; no hay regresión en el caso con experiencia.

### Lo que esta medición no prueba

- Con 8 planes sin experiencia, la `v4` fallaba en 2: una muestra pequeña, sin repeticiones. No separa el efecto de la `v5` del azar del
  modelo; la `v5` cumplió en los 8, pero con la misma muestra la `v4` habría cumplido en 6 de 8 por azar con una probabilidad no despreciable.
- Solo se midió el tono neutro, sin oferta laboral, con un modelo (`gemini-3.5-flash-lite`).
- La regla automática de «cita la formación» es aproximada; la confirmación es la lectura de una persona.
  La lectura de «suposición de experiencia» la hizo quien redactó el informe (IA); `PENDIENTE (Paula)` confirmarla.

## Archivos

| Qué | Dónde |
|---|---|
| Resúmenes por grupo (este repositorio) | `GEMINI_LITE_v5-unido-minimal/` y `GEMINI_LITE_v4-sinexp-referencia/` |
| Respuestas crudas de cada llamada | `ejecucion/evidencia/CM-212/banco-medicion-v5/` (fuera del repositorio) |
| Entradas ficticias | `fixtures/entradas/entradas-sin-experiencia.json` |
| Prompt | `prompts/planificacion.v5.txt` |
