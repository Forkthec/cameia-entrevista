# Entrada 11 · Registrar la respuesta, contador de reintentos y misma clave en proceso

- **Fecha:** 2026-11-11 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-216](../../specs/CM-216-RegistrarRespuesta/spec.md), sección 4 (decisiones D-216-1 a D-216-3)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: la tarea de registrar la respuesta escrita guarda la respuesta, la evalúa con el modelo sin mostrar el
resultado y abre la pregunta siguiente. El botón «Reintentar» repite la evaluación cuando la IA falló, y Frontend puede
repetir la misma petición (doble clic, recarga). El criterio habla de un tope de reintentos pero no dice quién lo cuenta,
ni qué trae el estado, ni qué pasa con una repetición mientras la primera aún espera a la IA.

Tarea: proponer opciones con recomendación y consecuencias para: quién cuenta y limita los reintentos; qué agrega el
estado de la sesión para que la vista se recupere al recargar; y qué responde una petición con la misma clave mientras
la primera sigue evaluando.

Condiciones: ningún error previsible termina en el 500 genérico; sin transacción abierta durante la llamada al modelo; la
respuesta de la persona nunca se pierde; el log no cita texto de la persona; cada cambio con su prueba, OpenAPI y Postman.

Formato: tabla de decisiones en la spec, con las alternativas descartadas.
```

## Decisiones con peso humano

- Backend cuenta y limita los reintentos, en una columna del turno (0 a 4); el quinto se rechaza con 409 (Paula, 11-nov-2026).
- El estado agrega `status` y `retriesUsed` a la pregunta pendiente y un historial con cada respuesta guardada, también la que espera reintento (Paula, 11-nov-2026).
- La segunda petición con la misma clave espera sin ocupar la base y devuelve el mismo resultado; si no termina a tiempo, 409 `TURN_CONFLICT` (Paula, 11-nov-2026).
