# Plantilla de competencias de la entrevista mixta

Asigna un **tipo de pregunta** a cada una de las 5 posiciones de una entrevista. En el MVP es una sola plantilla, igual para
todos los roles (entrevista mixta). Será una constante de código; no vive en la base de datos.

| Posición | Tipo (código) | Qué busca | ¿Se evalúa con STAR? |
|---|---|---|---|
| 1 | `APERTURA` | Romper el hielo: presentación y motivación por el rol | No |
| 2 | `TECNICA` | Un conocimiento central del rol objetivo | No |
| 3 | `CONDUCTUAL` | Una experiencia pasada concreta (método STAR: situación, tarea, acción, resultado) | Sí |
| 4 | `TECNICA` | Un segundo conocimiento del rol, distinto del de la posición 2 | No |
| 5 | `SITUACIONAL` | Una situación hipotética del trabajo: cómo actuaría | No |

## Reglas

- El plan de la IA es válido solo si cada pregunta trae el tipo que esta plantilla asigna a su posición (CA-4.1.1 b y CA-4.5.3).
- Hay exactamente 2 preguntas técnicas, y no pueden ser la misma.
- Los códigos de tipo son los de la tabla `tipo_pregunta`: `APERTURA`, `TECNICA`, `CONDUCTUAL`, `SITUACIONAL`.
- La posición y el texto de una pregunta principal no cambian una vez iniciada la entrevista.

## Por qué este orden (confirmado por Paula el 8-nov-2026)

- Empieza suave (apertura) y termina con un caso hipotético, como una entrevista real.
- Las dos técnicas quedan separadas por la conductual, para no cansar con dos preguntas técnicas seguidas.
- Alternativa descartada: apertura, técnica, conductual, situacional, conductual (una sola técnica). Deja el criterio
  «las preguntas técnicas son pertinentes al rol» con una sola pregunta que revisar y le quita peso al conocimiento del rol.
