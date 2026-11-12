# Entrada 13 · Reintentos de la IA

- **Fecha:** 2026-11-11 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-224](../../specs/CM-224-ReintentosIA/spec.md), sección de decisiones (D-224-1 a D-224-4)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: planificar las preguntas y evaluar cada respuesta llaman a Gemini con un solo intento. El criterio pide hasta
tres intentos con espera creciente y un proveedor de respaldo, sin decir cómo se llama a Groq, cuánto se espera entre
intentos, qué pasa si falta la llave de Groq en un entorno ni cómo probar «resultado válido a los 19,9 s».

Tarea: proponer opciones con recomendación y consecuencias para: el cliente de Groq; las esperas entre intentos; el
comportamiento sin llave de Groq; y la prueba del tiempo máximo de planificación.

Condiciones: ningún error previsible termina en el 500 genérico; sin transacción abierta mientras se espera; ningún
registro con prompts, salidas, llaves ni texto de la persona; cada cambio con su prueba, OpenAPI y Postman.

Formato: tabla de decisiones en la spec, con las alternativas descartadas.
```

## Decisiones con peso humano

- El cliente de Groq usa el starter de OpenAI de Spring AI con el `ChatModel` armado a mano, no un cliente HTTP propio (Paula, 11-nov-2026).
- Las esperas entre intentos son de 500 ms y 1 s (Paula, 11-nov-2026).
- Sin llave de Groq el servicio arranca con un aviso y el tercer intento usa Gemini; el respaldo real queda pendiente de probar hasta que DevOps cargue la llave (Paula, 11-nov-2026).
- La prueba del tiempo de planificación es literal (19.900 ms de 20.000 ms) además de la escalada (Paula, 11-nov-2026).
